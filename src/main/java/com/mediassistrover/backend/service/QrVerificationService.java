package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.verification.request.QrVerificationRequests.Issue;
import com.mediassistrover.backend.dto.verification.request.QrVerificationRequests.CameraVerify;
import com.mediassistrover.backend.dto.verification.response.*;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.RobotMqttSession;
import com.mediassistrover.backend.enums.AuthVerificationPurpose;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.security.DevicePrincipal;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
public interface QrVerificationService {
    String STAFF_SCAN_CAPABILITY = "staff-qr-scan-v1";
    record StaffCode(UUID authJobStepId, AuthVerificationPurpose purpose, Instant expiresAt, String proof) { }
    record ScanWindow(UUID jobId, UUID authJobStepId, Instant expiresAt) { }

    QrChallengeResponse ensureForStep(JobStep step);
    QrChallengeResponse activeForAdmin(UUID jobId,Issue request,AccountPrincipal actor);
    Optional<QrChallengeResponse> activeForDevice(UUID jobId,DevicePrincipal actor);
    AuthVerificationResponse verify(UUID jobId,String proof,AccountPrincipal actor,RequestMetadata metadata);
    StaffCode issueForStaffPhone(UUID jobId, AccountPrincipal actor);
    Optional<ScanWindow> staffScanWindow(RobotMqttSession session);
    AuthVerificationResponse verifyFromCamera(UUID jobId, CameraVerify request, DevicePrincipal actor, RequestMetadata metadata);
}
