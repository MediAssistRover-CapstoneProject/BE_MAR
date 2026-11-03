package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.MappingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface MappingSessionRepository extends JpaRepository<MappingSession,UUID> {
    Optional<MappingSession> findByJobId(UUID jobId);
    List<MappingSession> findByRobotIdAndPotentiallyOpenTrue(UUID robotId);
    @Modifying(flushAutomatically=true)
    @Query("update MappingSession s set s.potentiallyOpen=true, s.state='STARTING' "
            + "where s.sessionId=:sessionId and s.jobId=:jobId and s.state='ALLOCATED' and s.potentiallyOpen=false")
    int markStarting(@Param("sessionId") UUID sessionId,@Param("jobId") UUID jobId);
}
