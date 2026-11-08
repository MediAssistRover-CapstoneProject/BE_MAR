package com.mediassistrover.backend.media;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MediaSessionRepository extends JpaRepository<MediaSession,UUID> {
    Optional<MediaSession> findByActiveRobotId(UUID robotId);
    List<MediaSession> findTop100ByActiveRobotIdIsNotNullOrderByCreatedAtDesc();
    List<MediaSession> findTop20ByCleanupPendingTrueOrderByEndedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MediaSession s where s.sessionId=:id")
    Optional<MediaSession> lockById(@Param("id") UUID id);
}
