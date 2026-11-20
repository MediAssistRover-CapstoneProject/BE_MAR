package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.VoiceInteraction;
import com.mediassistrover.backend.enums.VoiceInteractionStatus;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.repository.VoiceInteractionRepository;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Expiring conversation ownership must never cancel an already submitted navigation job. */
@Service @RequiredArgsConstructor
public class VoiceInteractionLeaseService {
    private final VoiceInteractionRepository interactions;
    private final DeviceResourceScopePolicy scope;
    private final PatrolInterruptionEvents interruptions;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    @Value("${app.voice.interaction-lease-sec:45}") private int leaseSeconds=45;
    @Value("${app.operations.scheduler-enabled:true}") private boolean schedulerEnabled=true;

    public record Lease(boolean active, int leaseSeconds, String reason) { }
    public int leaseSeconds() { return Math.max(15, Math.min(300, leaseSeconds)); }

    @Transactional
    public Lease heartbeat(UUID id, DevicePrincipal device) {
        scope.requireCurrentDevice(device);
        var row=interactions.lockById(id).orElseThrow(()->new ResourceNotFoundException("Voice interaction not found"));
        if(row.getRobot()==null || !device.robotId().equals(row.getRobot().getRobotId()))
            throw new AccessDeniedException("Voice interaction is outside this Device scope");
        Instant now=clock.instant();
        if(row.getStatus()!=VoiceInteractionStatus.ACTIVE)return new Lease(false,leaseSeconds(),"VOICE_SESSION_ENDED");
        if(row.getLeaseExpiresAt()==null)return new Lease(false,leaseSeconds(),"VOICE_LEASE_NOT_NEGOTIATED");
        if(!row.getLeaseExpiresAt().isAfter(now)) {
            expire(row,now);
            return new Lease(false,leaseSeconds(),"VOICE_SESSION_EXPIRED");
        }
        row.setLeaseExpiresAt(now.plusSeconds(leaseSeconds()));
        return new Lease(true,leaseSeconds(),null);
    }

    @Scheduled(fixedDelayString="${app.voice.lease-reconcile-ms:5000}")
    public void reconcile() {
        if(!schedulerEnabled)return;
        for(UUID id:interactions.findExpiredLeaseIds(clock.instant(),PageRequest.of(0,100))) {
            var transaction=new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status->interactions.lockById(id).ifPresent(row->{
                Instant now=clock.instant();
                if(row.getStatus()==VoiceInteractionStatus.ACTIVE && row.getLeaseExpiresAt()!=null
                        && !row.getLeaseExpiresAt().isAfter(now))expire(row,now);
            }));
        }
    }

    private void expire(VoiceInteraction row, Instant now) {
        row.setStatus(VoiceInteractionStatus.FAILED);
        row.setEndedAt(now);
        interruptions.release(row.getRobot().getRobotId(),"VOICE",row.getVoiceInteractionId());
    }
}
