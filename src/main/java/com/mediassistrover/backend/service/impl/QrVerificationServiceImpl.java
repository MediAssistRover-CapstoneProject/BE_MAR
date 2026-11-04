package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.config.QrChallengeProperties;
import com.mediassistrover.backend.dto.verification.request.QrVerificationRequests.Issue;
import com.mediassistrover.backend.dto.verification.response.AuthVerificationResponse;
import com.mediassistrover.backend.dto.verification.response.QrChallengeResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import com.mediassistrover.backend.validation.RuntimeAuthorizationPolicy;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QrVerificationServiceImpl implements QrVerificationService {
    private static final String PREFIX = "MARQR1";

    private final QrChallengeRepository challengeRepository;
    private final AuthVerificationRepository verificationRepository;
    private final JobRepository jobRepository;
    private final JobStepRepository stepRepository;
    private final DeliveryRequestRepository deliveryRepository;
    private final RuntimeStepCompletionService completionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final StaffOperationalScopePolicy staffScope;
    private final DeviceResourceScopePolicy deviceScope;
    private final QrChallengeProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public QrChallengeResponse ensureForStep(JobStep supplied) {
        JobStep step = stepRepository.lockById(supplied.getJobStepId()).orElseThrow();
        Job job = step.getJobTask().getJob();
        requireLiveAuthentication(job, step);
        QrChallenge existing = challengeRepository.findByAuthJobStepJobStepId(step.getJobStepId()).orElse(null);
        if (existing != null) return active(existing);

        DeliveryRequest delivery = delivery(job);
        AuthVerificationPurpose purpose = RuntimeAuthorizationPolicy.purpose(step);
        Account expected = purpose == AuthVerificationPurpose.DELIVERY_SENDER
                ? delivery.getExpectedSenderAccount() : delivery.getExpectedReceiverAccount();
        if (expected == null)
            throw new BusinessRuleException("QR delivery requires the expected staff account for " + purpose);
        requireEligibleExpectedStaff(expected);

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant stepDeadline = step.getStartedAt().plusSeconds(step.getTimeoutSecSnapshot());
        Instant workflowDeadline = job.getStartedAt().plusSeconds(job.getWorkflow().getMaxDurationSec());
        Instant expiresAt = Collections.min(List.of(now.plusSeconds(properties.ttlSeconds()), stepDeadline, workflowDeadline))
                .truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(now)) throw new BusinessRuleException("QR authentication window has expired");

        byte[] provisional = new byte[32];
        random.nextBytes(provisional);
        QrChallenge challenge = challengeRepository.saveAndFlush(QrChallenge.builder()
                .job(job).authJobStep(step).expectedAccount(expected).purpose(purpose)
                .tokenHash(hash(Base64.getUrlEncoder().withoutPadding().encodeToString(provisional)))
                .issuedAt(now).expiresAt(expiresAt).build());
        challenge.setTokenHash(hash(payload(challenge)));
        challengeRepository.flush();
        auditService.record(null, "QR_CHALLENGE_ISSUED", "QrChallenge", challenge.getChallengeId(), null,
                Map.of("jobId", job.getJobId(), "purpose", purpose.name(), "expectedAccountId", expected.getAccountId()), null);
        return response(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public QrChallengeResponse activeForAdmin(UUID jobId, Issue request, AccountPrincipal actor) {
        Account account = staffScope.requireCurrentStaffOrAdmin(actor);
        if (account.getRole() != Role.ADMIN) throw new AccessDeniedException("ADMIN is required to inspect a QR challenge");
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        JobStep step = stepRepository.findById(request.authJobStepId())
                .orElseThrow(() -> new ResourceNotFoundException("JobStep not found: " + request.authJobStepId()));
        if (!step.getJobTask().getJob().getJobId().equals(jobId)
                || RuntimeAuthorizationPolicy.purpose(step) != request.purpose())
            throw new BusinessRuleException("QR challenge does not match the requested Job step and purpose");
        requireLiveAuthentication(job, step);
        QrChallenge challenge = challengeRepository.findByAuthJobStepJobStepId(step.getJobStepId())
                .orElseThrow(() -> new ResourceNotFoundException("Active QR challenge not found"));
        return active(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrChallengeResponse> activeForDevice(UUID jobId, DevicePrincipal actor) {
        Robot robot = deviceScope.requireCurrentDevice(actor);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (job.getRobot() == null || !job.getRobot().getRobotId().equals(robot.getRobotId()))
            throw new AccessDeniedException("Job is outside this Device scope");
        JobStep step = job.getSelectedJobStep();
        if (job.getStatus() != JobStatus.RUNNING || job.getCancelRequestedAt() != null
                || step == null || step.getStatus() != JobStepStatus.RUNNING
                || step.getStep().getStepType() != StepType.AUTHENTICATE) return Optional.empty();
        QrChallenge challenge = challengeRepository.findByAuthJobStepJobStepId(step.getJobStepId()).orElse(null);
        if (challenge == null || challenge.getUsedAt() != null
                || !challenge.getExpiresAt().isAfter(Instant.now(clock))) return Optional.empty();
        return Optional.of(response(challenge));
    }

    @Override
    @Transactional
    public AuthVerificationResponse verify(UUID jobId, String proof, AccountPrincipal actor, RequestMetadata metadata) {
        Parsed parsed = parseAndVerify(proof);
        if (!parsed.jobId().equals(jobId)) throw new BusinessRuleException("QR proof is outside this Job");
        Job job = jobRepository.lockById(jobId)
                .orElseThrow(() -> new BusinessRuleException("QR proof is outside this Job"));
        staffScope.requireJobAccess(job, actor);
        Account account = requireCurrentStaff(actor);
        QrChallenge challenge = challengeRepository.lockByTokenHash(hash(proof))
                .orElseThrow(() -> new BusinessRuleException("QR proof is invalid"));
        requireClaimsMatch(parsed, challenge);

        if (challenge.getUsedAt() != null) {
            AuthVerification existing = verificationRepository.findByAuthJobStepJobStepId(
                            challenge.getAuthJobStep().getJobStepId())
                    .orElseThrow(() -> new ConflictException("QR proof has already been used"));
            if (!existing.getActorAccount().getAccountId().equals(account.getAccountId()))
                throw new ConflictException("QR proof has already been used by another account");
            return verificationResponse(existing);
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!challenge.getExpiresAt().isAfter(now)) throw new BusinessRuleException("QR proof has expired");
        JobStep step = challenge.getAuthJobStep();
        requireLiveAuthentication(job, step);
        Account expected = challenge.getExpectedAccount();
        if (expected == null) throw new BusinessRuleException("QR challenge has no expected staff account");
        requireEligibleExpectedStaff(expected);
        if (!expected.getAccountId().equals(account.getAccountId()))
            throw new AccessDeniedException("QR challenge belongs to another Staff account");

        challenge.setUsedAt(now);
        challenge.setUsedByAccount(account);
        AuthVerification verification = verificationRepository.save(AuthVerification.builder()
                .job(job).authJobStep(step).actorAccount(account).actorTokenVersion(account.getTokenVersion())
                .method(AuthVerificationMethod.QR_CODE).purpose(challenge.getPurpose())
                .requiredPermission(permission(challenge.getPurpose())).issuedAt(now)
                .validUntil(challenge.getExpiresAt()).build());
        var result = objectMapper.createObjectNode().put("authenticated", true).put("verified", true)
                .put("verification_id", verification.getVerificationId().toString())
                .put("actor_user_id", account.getAccountId().toString())
                .put("valid_until", verification.getValidUntil().toString())
                .put("purpose", verification.getPurpose().name());
        if (job.getSchemaVersion() == 3) result = objectMapper.createObjectNode().put("verified", true)
                .put("verification_id", verification.getVerificationId().toString())
                .put("actor_account_id", account.getAccountId().toString())
                .put("valid_until", verification.getValidUntil().toString())
                .put("purpose", verification.getPurpose().name())
                .put("service_request_id", job.getServiceRequest().getServiceRequestId().toString())
                .put("verified_at", now.toString());
        completionService.complete(step, result, account, JobEventProducerType.STAFF,
                "QR_VERIFICATION_SUCCEEDED", null);
        auditService.record(account, "QR_PROOF_VERIFIED", "AuthVerification", verification.getVerificationId(), null,
                Map.of("jobId", jobId, "challengeId", challenge.getChallengeId()), metadata);
        return verificationResponse(verification);
    }

    private QrChallengeResponse active(QrChallenge challenge) {
        if (challenge.getUsedAt() != null || !challenge.getExpiresAt().isAfter(Instant.now(clock)))
            throw new BusinessRuleException("QR challenge is no longer active");
        return response(challenge);
    }

    private QrChallengeResponse response(QrChallenge challenge) {
        String value = payload(challenge);
        if (!hash(value).equals(challenge.getTokenHash()))
            throw new BusinessRuleException("QR challenge integrity check failed");
        return new QrChallengeResponse(challenge.getChallengeId(), challenge.getJob().getJobId(),
                challenge.getAuthJobStep().getJobStepId(), challenge.getPurpose(), value, challenge.getExpiresAt());
    }

    private String payload(QrChallenge challenge) {
        String claims = String.join("|", challenge.getChallengeId().toString(), challenge.getJob().getJobId().toString(),
                challenge.getAuthJobStep().getJobStepId().toString(), challenge.getPurpose().name(),
                Long.toString(challenge.getExpiresAt().getEpochSecond()));
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String signed = PREFIX + "." + encoded;
        return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signed));
    }

    private Parsed parseAndVerify(String proof) {
        if (proof == null || proof.isBlank() || proof.length() > 1024)
            throw new BusinessRuleException("QR proof is required");
        String[] sections = proof.split("\\.", -1);
        if (sections.length != 3 || !PREFIX.equals(sections[0]))
            throw new BusinessRuleException("QR proof is invalid");
        byte[] supplied;
        String claims;
        try {
            supplied = Base64.getUrlDecoder().decode(sections[2]);
            claims = new String(Base64.getUrlDecoder().decode(sections[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("QR proof is invalid");
        }
        if (!MessageDigest.isEqual(supplied, hmac(sections[0] + "." + sections[1])))
            throw new BusinessRuleException("QR proof is invalid");
        String[] values = claims.split("\\|", -1);
        try {
            if (values.length != 5) throw new IllegalArgumentException();
            return new Parsed(UUID.fromString(values[0]), UUID.fromString(values[1]), UUID.fromString(values[2]),
                    AuthVerificationPurpose.valueOf(values[3]), Long.parseLong(values[4]));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("QR proof is invalid");
        }
    }

    private void requireClaimsMatch(Parsed parsed, QrChallenge challenge) {
        if (!parsed.challengeId().equals(challenge.getChallengeId())
                || !parsed.jobId().equals(challenge.getJob().getJobId())
                || !parsed.authJobStepId().equals(challenge.getAuthJobStep().getJobStepId())
                || parsed.purpose() != challenge.getPurpose()
                || parsed.expiresAtEpochSecond() != challenge.getExpiresAt().getEpochSecond())
            throw new BusinessRuleException("QR proof claims do not match the challenge");
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign QR challenge", ex);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DeliveryRequest delivery(Job job) {
        if (job.getServiceRequest() == null
                || job.getServiceRequest().getRequestType() != ServiceRequestType.DELIVERY)
            throw new BusinessRuleException("QR authentication is restricted to delivery Jobs");
        return deliveryRepository.findById(job.getServiceRequest().getServiceRequestId())
                .orElseThrow(() -> new BusinessRuleException("Delivery runtime requires its DeliveryRequest"));
    }

    private Account requireCurrentStaff(AccountPrincipal actor) {
        Account account = staffScope.requireCurrentStaffOrAdmin(actor);
        if (account.getRole() != Role.STAFF)
            throw new AccessDeniedException("A STAFF account is required to verify delivery QR");
        return account;
    }

    private void requireEligibleExpectedStaff(Account account) {
        if (account.getRole() != Role.STAFF || account.getStatus() != AccountStatus.ACTIVE
                || account.isMustChangePassword())
            throw new AccessDeniedException(
                    "Expected delivery account is no longer an active, unrestricted STAFF account");
    }

    private void requireLiveAuthentication(Job job, JobStep step) {
        if (step.getStep().getStepType() != StepType.AUTHENTICATE
                || step.getStep().getParamsJson().has("method")
                && !"QR_CODE".equals(step.getStep().getParamsJson().path("method").asText()))
            throw new BusinessRuleException("Authentication method does not match QR delivery");
        Instant now = Instant.now(clock);
        if (!job.isBeOrchestrated() || job.getStatus() != JobStatus.RUNNING || job.getCancelRequestedAt() != null
                || job.getSelectedJobStep() == null
                || !job.getSelectedJobStep().getJobStepId().equals(step.getJobStepId())
                || step.getStatus() != JobStepStatus.RUNNING || step.getStartedAt() == null
                || !step.getStartedAt().plusSeconds(step.getTimeoutSecSnapshot()).isAfter(now)
                || job.getStartedAt() == null
                || !job.getStartedAt().plusSeconds(job.getWorkflow().getMaxDurationSec()).isAfter(now))
            throw new BusinessRuleException("QR requires the selected, unexpired AUTHENTICATE step");
        delivery(job);
    }

    private String permission(AuthVerificationPurpose purpose) {
        return purpose == AuthVerificationPurpose.DELIVERY_SENDER ? "DELIVERY_SEND" : "DELIVERY_RECEIVE";
    }

    private AuthVerificationResponse verificationResponse(AuthVerification value) {
        return new AuthVerificationResponse(value.getVerificationId(), value.getJob().getJobId(),
                value.getAuthJobStep().getJobStepId(), value.getActorAccount().getAccountId(),
                value.getUsedByJobStep() == null ? null : value.getUsedByJobStep().getJobStepId(),
                value.getMethod(), value.getPurpose(), value.getRequiredPermission(), value.getIssuedAt(),
                value.getValidUntil(), value.getRevokedAt());
    }

    private record Parsed(UUID challengeId, UUID jobId, UUID authJobStepId,
                          AuthVerificationPurpose purpose, long expiresAtEpochSecond) { }
}
