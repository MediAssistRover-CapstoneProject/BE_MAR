package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.JobStepStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface JobStepRepository extends JpaRepository<JobStep,UUID> {
    interface HelloOperation {
        UUID getJobStepId();
        String getOperationKey();
        UUID getJobId();
        UUID getRobotId();
    }
    // HELLO includes historical receipts. Read their identities in one query,
    // without loading every Step -> JobTask -> Job -> Robot lazy association.
    @Query("select s.jobStepId as jobStepId,s.operationKey as operationKey,j.jobId as jobId,r.robotId as robotId "
            +"from JobStep s join s.jobTask t join t.job j left join j.robot r where s.jobStepId in :ids")
    List<HelloOperation> findHelloOperations(@Param("ids") Collection<UUID> ids);
    @Query("select s.jobTask.job.jobId from JobStep s where s.jobStepId=:id")
    Optional<UUID> findJobId(@Param("id") UUID id);
    @Query("select s from JobStep s join fetch s.step where s.jobTask.job.jobId=:jobId order by s.executionSequenceNo")
    List<JobStep> findByJobId(@Param("jobId") UUID jobId);
    // Discover IDs without range/gap locks: the joined locking scan can lock
    // unrelated schema-3 MOVE steps before applying the legacy-job predicate.
    @Query("select s.jobStepId from JobStep s where s.status='RUNNING' and s.jobTask.job.beOrchestrated=false and s.startedAt is not null and s.startedAt <= :threshold")
    List<UUID> findPotentialTimeoutIds(@Param("threshold") Instant threshold);
    Optional<JobStep> findByOperationKey(String operationKey);
    long countByJobTaskJobJobId(UUID jobId);
    long countByJobTaskJobJobIdAndStepStepId(UUID jobId,UUID stepId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JobStep s where s.jobStepId=:id")
    Optional<JobStep> lockById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JobStep s where s.jobTask.job.jobId=:jobId and s.stepTypeSnapshot='WAIT' and s.status='RUNNING' order by s.executionSequenceNo")
    List<JobStep> lockRunningWaits(@Param("jobId") UUID jobId);
}
