package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.storage.S3ObjectStorageService;
import com.mediassistrover.backend.validation.*;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;

/** Network work is always between transactions; completion rechecks all authority. */
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class FaceAuthService {
    public static final String CAPABILITY="staff-face-verify-v1";
    private final FaceAiClient ai;
    private final StaffProfileRepository profiles;
    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final DeliveryRequestRepository deliveries;
    private final DeliveryAuthenticationPolicy deliveryAuthentication;
    private final FaceAuthSessionRepository sessions;
    private final AuthVerificationRepository verifications;
    private final RobotMqttSessionRepository transports;
    private final MqttSessionRegistry mqtt;
    private final DeviceResourceScopePolicy deviceScope;
    private final org.springframework.beans.factory.ObjectProvider<RuntimeStepCompletionService> completion;
    private final S3ObjectStorageService storage;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;

    public record Status(UUID accountId,UUID avatarVersion,String status,String avatarUrl,boolean enabled,String reason) {}
    public record Window(UUID jobId,UUID authJobStepId,UUID sessionId,Instant expiresAt) {}
    public record Scan(UUID sessionId,UUID authJobStepId,UUID transportSessionId,String image) {}
    private record Sample(Window window,UUID account,UUID version,String model,String template) {}
    private record Avatar(UUID account,UUID version,String url,long revision) {}

    public Status status(UUID account) {
        return tx.execute(s -> status(profile(account,false)));
    }
    private Status status(StaffProfile p){return status(p,null);}
    private Status status(StaffProfile p,String reason){return new Status(p.getAccount().getAccountId(),p.getFaceAvatarVersion(),
        p.getFaceStatus()==null?(p.getUrl()==null?"MISSING":"PENDING"):p.getFaceStatus(),p.getUrl(),ai.enabled(),reason);}
    private StaffProfile profile(UUID account,boolean lock){return (lock?profiles.lockFace(account):profiles.findByAccountAccountId(account))
        .orElseThrow(()->new BusinessRuleException("FACE_PROFILE_MISSING"));}
    private StaffProfile ready(UUID account,boolean lock){
        StaffProfile p=profile(account,lock);Account a=p.getAccount();
        if(!p.isActive() || a.getRole()!=Role.STAFF || a.getStatus()!=AccountStatus.ACTIVE || a.isMustChangePassword()
                || !"READY".equals(p.getFaceStatus()) || p.getFaceTemplate()==null || p.getUrl()==null
                || p.getFaceAvatarVersion()==null || p.getFaceModelVersion()==null) fail("FACE_AVATAR_NOT_READY");
        return p;
    }
    public void requireDelivery(DeliveryAuthenticationMethod method,UUID sender,UUID receiver){
        if(method!=DeliveryAuthenticationMethod.FACE_VERIFICATION)return;
        ai.requireEnabled();ready(receiver,false);
    }
    public Status review(UUID account,UUID version,boolean approve,AccountPrincipal actor){
        if(actor.role()!=Role.ADMIN)throw new AccessDeniedException("ADMIN required");
        Avatar snapshot=tx.execute(s->{
            StaffProfile p=profile(account,true);
            if(version==null||!version.equals(p.getFaceAvatarVersion())||p.getUrl()==null)fail("FACE_AVATAR_CHANGED");
            return new Avatar(account,version,p.getUrl(),p.getFaceRowVersion());
        });
        JsonNode result=null;
        if(approve){
            String key=storage.keyFromPublicUrl(snapshot.url(),Set.of("images"));
            if(storage.head(key,true).sizeBytes()>2L*1024*1024)fail("FACE_AVATAR_TOO_LARGE: avatar must be at most 2 MiB; upload a smaller image");
            byte[] image=storage.get(key,2*1024*1024);
            result=ai.call("enroll",json.createObjectNode().put("image",Base64.getEncoder().encodeToString(image)));
        }
        JsonNode enrolled=result;
        return tx.execute(s->{
            StaffProfile p=profile(account,true);
            if(!snapshot.version().equals(p.getFaceAvatarVersion()) || snapshot.revision()!=p.getFaceRowVersion()
                    || !snapshot.url().equals(p.getUrl()))fail("FACE_AVATAR_CHANGED");
            p.setFaceTemplate(null);p.setFaceModelVersion(null);
            boolean valid=approve && enrolled.path("accepted").asBoolean() && validTemplate(enrolled.path("template"))
                && enrolled.path("modelVersion").asText().matches("[a-f0-9]{64}");
            p.setFaceStatus(valid?"READY":approve?"INVALID":"REJECTED");
            if(valid){p.setFaceTemplate(enrolled.path("template").toString());p.setFaceModelVersion(enrolled.path("modelVersion").asText());}
            p.setFaceReviewedAt(clock.instant());p.setFaceReviewedBy(actor.accountId());
            // A new approval/rejection also invalidates any in-flight authorization.
            p.setFaceAvatarVersion(UUID.randomUUID());
            return status(p,approve&&!valid?enrollmentReason(enrolled):null);
        });
    }
    private String enrollmentReason(JsonNode answer){
        String issue=answer.path("quality").path("issue").asText();
        if(Set.of("FACE_TOO_SMALL","FACE_TOO_BLURRY","INVALID_FACE_FEATURE").contains(issue))return issue;
        String reason=answer.path("reason").asText();
        return Set.of("NO_FACE","MULTIPLE_FACES","LOW_QUALITY").contains(reason)?reason:"FACE_INVALID_ENROLLMENT_RESULT";
    }
    private boolean validTemplate(JsonNode value){
        if(!value.isArray()||value.size()!=128)return false;
        double norm=0;for(JsonNode n:value){if(!n.isNumber()||!Double.isFinite(n.asDouble()))return false;norm+=n.asDouble()*n.asDouble();}
        return norm>0 && Double.isFinite(norm);
    }
    public boolean supports(RobotMqttSession t){
        if(t==null||!mqtt.fresh(t)||t.getHelloJson()==null)return false;
        var h=t.getHelloJson();Set<String> caps=new HashSet<>();h.path("capabilities").forEach(n->caps.add(n.asText()));
        return "STAFF_VERIFIED".equals(h.path("unlockPolicy").asText()) && caps.containsAll(Set.of(CAPABILITY,"secure-unlock-v1"));
    }
    public boolean supportsRobot(UUID robot){return supports(transports.findById(robot).orElse(null));}
    public Window ensure(JobStep supplied){return tx.execute(s->{
        Job job=jobs.lockById(supplied.getJobTask().getJob().getJobId()).orElseThrow();
        JobStep step=selected(job);Account account=expected(job,step);StaffProfile p=ready(account.getAccountId(),true);
        if(!step.getJobStepId().equals(supplied.getJobStepId()))fail("FACE_WRONG_STEP");
        var t=transports.findById(job.getRobot().getRobotId()).orElseThrow();
        if(!supports(t))fail("FACE_ROBOT_NOT_READY");
        var old=sessions.findByAuthJobStepId(step.getJobStepId()).orElse(null);
        if(old!=null)return window(currentSession(old,job,step,p,t));
        var v=new FaceAuthSession();v.setSessionId(UUID.randomUUID());v.setJobId(job.getJobId());v.setAuthJobStepId(step.getJobStepId());
        v.setRobotId(job.getRobot().getRobotId());v.setTransportSessionId(t.getTransportSessionId());v.setAccountId(account.getAccountId());v.setAvatarVersion(p.getFaceAvatarVersion());
        v.setExpiresAt(Collections.min(List.of(clock.instant().plusSeconds(120),step.getStartedAt().plusSeconds(step.getTimeoutSecSnapshot()),job.getStartedAt().plusSeconds(job.getWorkflow().getMaxDurationSec()))));
        sessions.saveAndFlush(v);return window(v);
    });}
    public Optional<Window> scanWindow(RobotMqttSession transport){
        if(!ai.enabled()||!supports(transport))return Optional.empty();
        return tx.execute(s->{
            var active=jobs.findByRobotRobotIdAndStatusIn(transport.getRobotId(),List.of(JobStatus.RUNNING));
            if(active.size()!=1)return Optional.empty();
            try {Job job=jobs.lockById(active.getFirst().getJobId()).orElseThrow();JobStep step=selected(job);var p=ready(expected(job,step).getAccountId(),true);
                var v=sessions.findByAuthJobStepId(step.getJobStepId()).orElse(null);
                if(v==null)return Optional.empty();
                var current=transports.findById(job.getRobot().getRobotId()).orElse(null);
                if(!supports(current)||!Objects.equals(current.getTransportSessionId(),transport.getTransportSessionId()))return Optional.empty();
                return Optional.of(window(currentSession(v,job,step,p,current)));
            }catch(BusinessRuleException ex){return Optional.empty();}
        });
    }
    public Optional<Window> active(UUID jobId,DevicePrincipal device){return tx.execute(s->{
        Job job=owned(jobId,device,false);
        var t=transports.findById(job.getRobot().getRobotId()).orElse(null);
        return t==null?Optional.empty():scanWindow(t).filter(w->w.jobId().equals(jobId));
    });}
    public JsonNode verify(UUID jobId,Scan scan,DevicePrincipal device){
        if(scan==null||scan.sessionId()==null||scan.authJobStepId()==null||scan.transportSessionId()==null
                ||scan.image()==null||scan.image().length()>2800000)fail("FACE_INVALID_SCAN");
        try{byte[] bytes=Base64.getDecoder().decode(scan.image());if(bytes.length>2*1024*1024||bytes.length<3
            ||bytes[0]!=(byte)0xff||bytes[1]!=(byte)0xd8||bytes[2]!=(byte)0xff)fail("FACE_INVALID_JPEG");}
        catch(IllegalArgumentException e){fail("FACE_INVALID_JPEG");}
        Sample sample=tx.execute(s->{Job job=owned(jobId,device,true);JobStep step=selected(job);
            StaffProfile p=ready(expected(job,step).getAccountId(),true);FaceAuthSession v=requireScan(job,step,p,scan);
            return new Sample(window(v),v.getAccountId(),v.getAvatarVersion(),p.getFaceModelVersion(),p.getFaceTemplate());});
        JsonNode template;try{template=json.readTree(sample.template());}catch(Exception e){throw new BusinessRuleException("FACE_INVALID_TEMPLATE");}
        var input=json.createObjectNode().put("image",scan.image()).put("modelVersion",sample.model());input.set("template",template);
        long start=System.nanoTime();
        JsonNode answer;
        try {answer=ai.call("verify",input);} catch(RuntimeException ex) {
            String reason=ex.getMessage()!=null&&Set.of("FACE_AI_UNAVAILABLE","FACE_AI_BUSY","FACE_MODEL_CHANGED","FACE_DISABLED").contains(ex.getMessage())?ex.getMessage():"FACE_AI_UNAVAILABLE";
            log.info("FACE_VERIFY job={} step={} reason={} elapsedMs={}",jobId,scan.authJobStepId(),reason,(System.nanoTime()-start)/1000000);throw ex;
        }
        log.info("FACE_COMPARE job={} step={} reason={} elapsedMs={}",jobId,scan.authJobStepId(),answer.path("matched").asBoolean()?"MATCH":safeReason(answer.path("reason").asText()),(System.nanoTime()-start)/1000000);
        if(!sample.model().equals(answer.path("modelVersion").asText()))fail("FACE_MODEL_CHANGED");
        if(!answer.path("matched").asBoolean())return json.createObjectNode().put("verified",false).put("reason",safeReason(answer.path("reason").asText()));
        if(!answer.path("score").isNumber()||!Double.isFinite(answer.path("score").asDouble()))fail("FACE_INVALID_RESULT");
        return tx.execute(s->{
            Job job=owned(jobId,device,true);JobStep step=selected(job);Account account=expected(job,step);
            StaffProfile p=ready(account.getAccountId(),true);FaceAuthSession v=requireScan(job,step,p,scan);
            if(!sample.version().equals(p.getFaceAvatarVersion())||!sample.model().equals(p.getFaceModelVersion()))fail("FACE_AVATAR_CHANGED");
            Instant now=clock.instant();var purpose=RuntimeAuthorizationPolicy.purpose(step);
            var proof=verifications.saveAndFlush(AuthVerification.builder().job(job).authJobStep(step).actorAccount(account)
                .actorTokenVersion(account.getTokenVersion()).faceAvatarVersion(p.getFaceAvatarVersion()).method(AuthVerificationMethod.FACE_VERIFICATION)
                .purpose(purpose).requiredPermission(purpose==AuthVerificationPurpose.DELIVERY_SENDER?"DELIVERY_SEND":"DELIVERY_RECEIVE")
                .issuedAt(now).validUntil(v.getExpiresAt()).build());
            var output=json.createObjectNode().put("verified",true).put("verification_id",proof.getVerificationId().toString())
                .put("actor_account_id",account.getAccountId().toString()).put("valid_until",v.getExpiresAt().toString())
                .put("purpose",purpose.name()).put("service_request_id",job.getServiceRequest().getServiceRequestId().toString()).put("verified_at",now.toString());
            v.setUsedAt(now);
            if(!completion.getObject().complete(step,output,account,JobEventProducerType.STAFF,"FACE_VERIFICATION_SUCCEEDED",null))fail("FACE_STEP_ALREADY_COMPLETED");
            return json.createObjectNode().put("verified",true).put("reason","MATCH");
        });
    }
    private String safeReason(String r){return Set.of("NO_FACE","MULTIPLE_FACES","LOW_QUALITY","NO_MATCH").contains(r)?r:"FACE_AI_UNAVAILABLE";}
    private FaceAuthSession requireScan(Job job,JobStep step,StaffProfile p,Scan scan){
        if(!step.getJobStepId().equals(scan.authJobStepId()))fail("FACE_WRONG_STEP");
        var v=sessions.findById(scan.sessionId()).orElseThrow(()->new BusinessRuleException("FACE_SESSION_MISSING"));
        checkSession(v,job,step,p,scan.transportSessionId());return v;
    }
    private void checkSession(FaceAuthSession v,Job job,JobStep step,StaffProfile p,UUID transport){
        var t=transports.findById(job.getRobot().getRobotId()).orElse(null);
        if(!supports(t)||!Objects.equals(t.getTransportSessionId(),transport)||!Objects.equals(v.getTransportSessionId(),transport)
            ||!v.getJobId().equals(job.getJobId())||!v.getAuthJobStepId().equals(step.getJobStepId())||!v.getRobotId().equals(job.getRobot().getRobotId())
            ||!v.getAccountId().equals(p.getAccount().getAccountId())||!v.getAvatarVersion().equals(p.getFaceAvatarVersion())
            ||v.getUsedAt()!=null||!v.getExpiresAt().isAfter(clock.instant()))fail("FACE_SESSION_EXPIRED_OR_CHANGED");
    }
    /** A reconnect replaces only an unused nonce; it never restarts the authentication deadline. Caller holds the Job lock. */
    private FaceAuthSession currentSession(FaceAuthSession old,Job job,JobStep step,StaffProfile profile,RobotMqttSession transport){
        if(!old.getJobId().equals(job.getJobId())||!old.getAuthJobStepId().equals(step.getJobStepId())
            ||!old.getRobotId().equals(job.getRobot().getRobotId())||!old.getAccountId().equals(profile.getAccount().getAccountId())
            ||!old.getAvatarVersion().equals(profile.getFaceAvatarVersion())||old.getUsedAt()!=null
            ||!old.getExpiresAt().isAfter(clock.instant())
            ||verifications.findByAuthJobStepJobStepId(step.getJobStepId()).isPresent())fail("FACE_SESSION_EXPIRED_OR_CHANGED");
        if(Objects.equals(old.getTransportSessionId(),transport.getTransportSessionId()))return old;
        var replacement=new FaceAuthSession();replacement.setSessionId(UUID.randomUUID());replacement.setJobId(old.getJobId());
        replacement.setAuthJobStepId(old.getAuthJobStepId());replacement.setRobotId(old.getRobotId());
        replacement.setTransportSessionId(transport.getTransportSessionId());replacement.setAccountId(old.getAccountId());
        replacement.setAvatarVersion(old.getAvatarVersion());replacement.setExpiresAt(old.getExpiresAt());
        // Delete/flush before inserting because auth_job_step_id is unique. Old scans cannot resolve the old nonce afterwards.
        sessions.delete(old);sessions.flush();sessions.saveAndFlush(replacement);
        log.info("FACE_SESSION_ROTATED job={} step={}",job.getJobId(),step.getJobStepId());
        return replacement;
    }
    private Job owned(UUID jobId,DevicePrincipal device,boolean lock){
        Robot robot=deviceScope.requireCurrentDevice(device);Job job=(lock?jobs.lockById(jobId):jobs.findById(jobId)).orElseThrow();
        if(job.getRobot()==null||!job.getRobot().getRobotId().equals(robot.getRobotId()))throw new AccessDeniedException("Job outside Device scope");return job;
    }
    private JobStep selected(Job job){
        ai.requireEnabled();JobStep step=job.getSelectedJobStep();Instant now=clock.instant();
        if(!job.isBeOrchestrated()||job.getSchemaVersion()!=3||job.getStatus()!=JobStatus.RUNNING||job.getCancelRequestedAt()!=null
            ||job.getRobot()==null||step==null||step.getStatus()!=JobStepStatus.RUNNING||step.getStep().getStepType()!=StepType.AUTHENTICATE
            ||!"FACE_VERIFICATION".equals(step.getStep().getParamsJson().path("method").asText())
            ||step.getStartedAt()==null||!step.getStartedAt().plusSeconds(step.getTimeoutSecSnapshot()).isAfter(now)
            ||job.getStartedAt()==null||!job.getStartedAt().plusSeconds(job.getWorkflow().getMaxDurationSec()).isAfter(now))fail("FACE_NO_ACTIVE_STEP");
        return step;
    }
    private Account expected(Job job,JobStep step){
        if(job.getServiceRequest()==null)throw new BusinessRuleException("FACE_DELIVERY_REQUIRED");
        var d=deliveries.findById(job.getServiceRequest().getServiceRequestId()).orElseThrow();
        var purpose=RuntimeAuthorizationPolicy.purpose(step);
        if(deliveryAuthentication.methodFor(d,purpose)!=DeliveryAuthenticationMethod.FACE_VERIFICATION)fail("FACE_METHOD_MISMATCH");
        if(purpose!=AuthVerificationPurpose.DELIVERY_RECEIVER||d.getPickedUpAt()==null||d.getDeliveredAt()!=null)fail("FACE_WRONG_STAGE");
        return d.getExpectedReceiverAccount();
    }
    public void requireGrant(AuthVerification grant){
        if(grant.getMethod()!=AuthVerificationMethod.FACE_VERIFICATION)return;
        ai.requireEnabled();StaffProfile p=ready(grant.getActorAccount().getAccountId(),false);
        if(!Objects.equals(grant.getFaceAvatarVersion(),p.getFaceAvatarVersion()))fail("FACE_AVATAR_CHANGED");
    }
    private Window window(FaceAuthSession s){return new Window(s.getJobId(),s.getAuthJobStepId(),s.getSessionId(),s.getExpiresAt());}
    private static void fail(String code){throw new BusinessRuleException(code);}
}
