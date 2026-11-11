package com.mediassistrover.backend.media;

import com.mediassistrover.backend.entity.EmergencyIncident;
import com.mediassistrover.backend.enums.EmergencyIncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;

public interface MediaSessionRepository extends JpaRepository<MediaSession,UUID> {
    Optional<MediaSession> findByActiveRobotId(UUID robotId);
    List<MediaSession> findTop100ByActiveRobotIdIsNotNullOrderByCreatedAtDesc();
    List<MediaSession> findTop20ByCleanupPendingTrueOrderByEndedAtAsc();
    boolean existsByIncidentIncidentId(UUID incidentId);
    @Query("select i from EmergencyIncident i where i.robot.robotId=:robotId and i.incidentType='SOS' " +
            "and i.archivedAt is null and i.sosResponder is not null and i.status in :states " +
            "and i.occurredAt>=:since order by i.occurredAt desc")
    List<EmergencyIncident> recentSosForMedia(@Param("robotId") UUID robotId,@Param("since") Instant since,
            @Param("states") Collection<EmergencyIncidentStatus> states,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MediaSession s where s.sessionId=:id")
    Optional<MediaSession> lockById(@Param("id") UUID id);
}
