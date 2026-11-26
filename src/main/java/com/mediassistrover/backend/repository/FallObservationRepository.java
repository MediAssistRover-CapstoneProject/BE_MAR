package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.FallObservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface FallObservationRepository extends JpaRepository<FallObservation,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from FallObservation o where o.observationId=:id")
    Optional<FallObservation> lock(@Param("id") UUID id);
    Optional<FallObservation> findFirstByRobotIdOrderByCreatedAtDesc(UUID robotId);
    Optional<FallObservation> findFirstByRobotIdAndMonitoringSessionIdAndTrackingRevisionAndTrackIdOrderByCreatedAtDesc(UUID robotId,UUID sessionId,UUID trackingRevision,int trackId);
    List<FallObservation> findByRobotIdAndStateIn(UUID robotId,Collection<FallObservation.State> states);
    List<FallObservation> findByJobIdAndStateIn(UUID jobId,Collection<FallObservation.State> states);
    boolean existsByJobIdAndStateInAndExpiresAtAfter(UUID jobId,Collection<FallObservation.State> states,Instant now);
    @Query("select o.observationId from FallObservation o where o.state in :states order by o.createdAt")
    List<UUID> activeIds(@Param("states") Collection<FallObservation.State> states,Pageable page);
}
