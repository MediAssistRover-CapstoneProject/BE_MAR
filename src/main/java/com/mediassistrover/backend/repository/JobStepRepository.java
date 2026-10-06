package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.JobStepStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface JobStepRepository extends JpaRepository<JobStep,UUID> {
    @Query("select s from JobStep s join fetch s.step where s.jobTask.job.jobId=:jobId order by s.executionSequenceNo")
    List<JobStep> findByJobId(@Param("jobId") UUID jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JobStep s where s.status='RUNNING' and s.startedAt is not null and s.startedAt <= :threshold")
    List<JobStep> lockPotentialTimeouts(@Param("threshold") Instant threshold);
    Optional<JobStep> findByOperationKey(String operationKey);
    long countByJobTaskJobJobId(UUID jobId);
    long countByJobTaskJobJobIdAndStepStepId(UUID jobId,UUID stepId);
}
