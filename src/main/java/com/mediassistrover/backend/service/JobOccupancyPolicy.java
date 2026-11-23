package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.JobStatus;
import java.util.Set;

/** A logical suspended job retains its robot association without owning its motors. */
public final class JobOccupancyPolicy {
    private JobOccupancyPolicy() { }
    public static final Set<JobStatus> MOTION_OWNERS = Set.of(JobStatus.QUEUED, JobStatus.ACCEPTED,
            JobStatus.RUNNING, JobStatus.SUSPENDING, JobStatus.RECOVERY_REQUIRED);
    public static final Set<JobStatus> EXECUTING = Set.of(JobStatus.ACCEPTED, JobStatus.RUNNING,
            JobStatus.SUSPENDING, JobStatus.RECOVERY_REQUIRED);
    public static final Set<JobStatus> NON_TERMINAL = Set.of(JobStatus.QUEUED, JobStatus.ACCEPTED,
            JobStatus.RUNNING, JobStatus.SUSPENDING, JobStatus.SUSPENDED, JobStatus.RECOVERY_REQUIRED);
    public static boolean patrol(Job job) {
        return job != null && job.isBeOrchestrated() && job.getSchemaVersion() == 3
                && job.getWorkflow() != null && "PATROL".equalsIgnoreCase(job.getWorkflow().getServiceType());
    }
    /** A future patrol has no physical ownership until admission selects its first step. */
    public static boolean unstartedPatrolIntent(Job job) {
        return patrol(job) && job.getStatus() == JobStatus.QUEUED && job.getAcceptedAt() == null
                && job.getStartedAt() == null && job.getSelectedJobStep() == null && !job.stopOutstanding();
    }
    public static boolean yieldable(Job job, int incomingPriority) {
        return patrol(job) && job.getCancelRequestedAt() == null && incomingPriority > job.getPriority()
                && Set.of(JobStatus.QUEUED, JobStatus.ACCEPTED, JobStatus.RUNNING,
                    JobStatus.SUSPENDING, JobStatus.SUSPENDED).contains(job.getStatus());
    }
}
