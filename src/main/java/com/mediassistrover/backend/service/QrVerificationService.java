package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.verification.request.QrVerificationRequests.Issue;
import com.mediassistrover.backend.dto.verification.response.*;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.security.DevicePrincipal;
import java.util.Optional;
import java.util.UUID;
public interface QrVerificationService {
    QrChallengeResponse ensureForStep(JobStep step);
    QrChallengeResponse activeForAdmin(UUID jobId,Issue request,AccountPrincipal actor);
    Optional<QrChallengeResponse> activeForDevice(UUID jobId,DevicePrincipal actor);
    AuthVerificationResponse verify(UUID jobId,String proof,AccountPrincipal actor,RequestMetadata metadata);
}
