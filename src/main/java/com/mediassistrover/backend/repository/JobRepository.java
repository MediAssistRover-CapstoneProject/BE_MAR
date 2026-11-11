package com.mediassistrover.backend.repository;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface JobRepository extends JpaRepository<Job,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.jobId=:id")
    Optional<Job> lockById(@Param("id") UUID id);
    interface StatusCount { JobStatus getStatus(); long getTotal(); }
    boolean existsByWorkflowWorkflowId(UUID workflowId);
    Page<Job> findByStatus(JobStatus status,Pageable pageable);
    @Query("select j.jobId from Job j where j.status=com.mediassistrover.backend.enums.JobStatus.RUNNING and j.serviceRequest.requestType=com.mediassistrover.backend.enums.ServiceRequestType.DELIVERY order by j.jobId")
    List<UUID> findRunningDeliveryIds();
    Page<Job> findByBeOrchestratedTrueAndStatus(JobStatus status,Pageable pageable);
    interface DeadlineCandidate {
        UUID getJobId(); Instant getStartedAt(); Integer getMaxDurationSec(); Instant getCancelRequestedAt();
        com.mediassistrover.backend.enums.JobStepStatus getStepStatus(); String getStepType();
        Instant getStepStartedAt(); Integer getStepTimeoutSec();
        String getCommandState(); Instant getCommandExpiresAt();
        Instant getInterruptionRequestedAt();
    }
    @Query("select j.jobId as jobId,j.startedAt as startedAt,w.maxDurationSec as maxDurationSec," +
           "j.cancelRequestedAt as cancelRequestedAt,s.status as stepStatus,s.stepTypeSnapshot as stepType," +
           "s.startedAt as stepStartedAt,s.timeoutSecSnapshot as stepTimeoutSec," +
           "c.state as commandState,c.expiresAt as commandExpiresAt,s.interruptionRequestedAt as interruptionRequestedAt " +
           "from Job j join j.workflow w left join j.selectedJobStep s " +
           "left join RobotCommandOutbox c on c.jobStepId=s.jobStepId and c.commandType='EXECUTE_STEP' " +
           "where j.beOrchestrated=true and j.status='RUNNING' order by j.jobId")
    List<DeadlineCandidate> findBeDeadlineCandidates(Pageable pageable);
    @Query("select j.jobId from Job j where j.beOrchestrated=true and j.status='QUEUED' and j.robot.status='IDLE' order by j.queuedAt")
    List<UUID> findReadyBeJobIds(Pageable page);
    Page<Job> findByServiceRequestRequestedByAccountAccountId(UUID accountId,Pageable pageable);
    List<Job> findByServiceRequestServiceRequestId(UUID requestId);
    boolean existsByServiceRequestServiceRequestIdAndAssignedStaffAccountAccountId(UUID requestId, UUID accountId);
    Page<Job> findByAssignedStaffAccountAccountIdOrServiceRequestRequestedByAccountAccountId(UUID assigned,UUID requester,Pageable pageable);
    @Query("select j from Job j where (:status is null or j.status=:status) " +
            "and (:workflowId is null or j.workflow.workflowId=:workflowId) " +
            "and (:requestId is null or j.serviceRequest.serviceRequestId=:requestId) " +
            "and (:robotId is null or j.robot.robotId=:robotId) " +
            "and (:from is null or j.queuedAt>=:from) and (:to is null or j.queuedAt<:to)")
    Page<Job> search(@Param("status") JobStatus status,@Param("workflowId") UUID workflowId,
                     @Param("requestId") UUID requestId,@Param("robotId") UUID robotId,
                     @Param("from") Instant from,@Param("to") Instant to,Pageable pageable);
    @Query("select j from Job j where j.archivedAt is null and (:status is null or j.status=:status) and (:workflowId is null or j.workflow.workflowId=:workflowId) and (:requestId is null or j.serviceRequest.serviceRequestId=:requestId) and (:robotId is null or j.robot.robotId=:robotId) and (:from is null or j.queuedAt>=:from) and (:to is null or j.queuedAt<:to)")
    Page<Job> searchActive(@Param("status") JobStatus status,@Param("workflowId") UUID workflowId,@Param("requestId") UUID requestId,@Param("robotId") UUID robotId,@Param("from") Instant from,@Param("to") Instant to,Pageable pageable);
    List<Job> findByRobotRobotIdAndStatusIn(UUID robotId,Collection<JobStatus> statuses);
    @Query("select j.jobId from Job j where j.status='QUEUED' and j.robot is null order by j.priority desc,j.queuedAt,j.jobId")
    List<UUID> findUnassignedQueuedJobIds(Pageable pageable);
    @Query("select count(j)>0 from Job j left join j.map m where j.status='QUEUED' and j.robot is null " +
           "and j.cancelRequestedAt is null and j.priority>:priority and (m is null or m.mapId=:mapId)")
    boolean existsHigherPriorityQueuedForMap(@Param("mapId") UUID mapId, @Param("priority") int priority);
    Optional<Job> findFirstByRobotRobotIdAndStatusOrderByQueuedAtAsc(UUID robotId,JobStatus status);
    boolean existsByScheduleScheduleIdAndScheduleFireAtAndScheduleItemKey(UUID scheduleId,Instant fireAt,String itemKey);
    @Query("select j.jobId from Job j where j.robot.robotId=:robotId and j.status in :statuses order by j.jobId")
    List<UUID> findRobotJobIds(@Param("robotId") UUID robotId,@Param("statuses") Collection<JobStatus> statuses);
    /** Lock by PRIMARY, like the orchestrator. Locking through ix_job_robot_status
     * first deadlocks with an owner changing status while holding the primary row.
     * Recheck after waiting: a candidate may have become terminal in the meantime. */
    default List<Job> lockRobotJobs(UUID robotId,Collection<JobStatus> statuses) {
        List<Job> held=new ArrayList<>();
        for(UUID id:findRobotJobIds(robotId,statuses)) {
            Job job=lockById(id).orElse(null);
            if(job!=null && job.getRobot()!=null && robotId.equals(job.getRobot().getRobotId()) && statuses.contains(job.getStatus()))held.add(job);
        }
        return held;
    }
    // Legacy dispatch expiry must not lock the QUEUED status index: its scan can
    // lock a modern MOVE before filtering dispatch_expires_at/be_orchestrated.
    @Query("select j.jobId from Job j where j.beOrchestrated=false and j.status='QUEUED' and j.dispatchExpiresAt is not null and j.dispatchExpiresAt<=:now order by j.jobId")
    List<UUID> findOverdueLegacyIds(@Param("now") Instant now);
    @Query("select j.status as status, count(j) as total from Job j group by j.status")
    List<StatusCount> countGroupedByStatus();
}
