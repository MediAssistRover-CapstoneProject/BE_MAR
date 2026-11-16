package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.JobSuspensionHold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JobSuspensionHoldRepository extends JpaRepository<JobSuspensionHold,UUID> {
    Optional<JobSuspensionHold> findByJobJobIdAndSourceAndSourceId(UUID jobId,String source,UUID sourceId);
    boolean existsByJobJobIdAndReleasedAtIsNull(UUID jobId);
    List<JobSuspensionHold> findByJobJobIdOrderByCreatedAt(UUID jobId);
}
