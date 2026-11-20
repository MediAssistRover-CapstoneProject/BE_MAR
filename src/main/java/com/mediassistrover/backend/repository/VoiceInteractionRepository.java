package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.VoiceInteraction;
import com.mediassistrover.backend.enums.VoiceInteractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface VoiceInteractionRepository extends JpaRepository<VoiceInteraction, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoiceInteraction v where v.voiceInteractionId=:id")
    java.util.Optional<VoiceInteraction> lockById(@Param("id") UUID id);

    @Query("select v.voiceInteractionId from VoiceInteraction v where v.status='ACTIVE' " +
            "and v.leaseExpiresAt is not null and v.leaseExpiresAt<=:now order by v.leaseExpiresAt")
    java.util.List<UUID> findExpiredLeaseIds(@Param("now") Instant now, Pageable page);
    java.util.List<VoiceInteraction> findByRobotRobotIdAndStatus(UUID robotId, VoiceInteractionStatus status);
    @Query("select v from VoiceInteraction v where (:robotId is null or v.robot.robotId=:robotId) " +
            "and (:status is null or v.status=:status) and (:from is null or v.startedAt>=:from) " +
            "and (:to is null or v.startedAt<:to)")
    Page<VoiceInteraction> search(@Param("robotId") UUID robotId,
                                  @Param("status") VoiceInteractionStatus status,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
