package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.enums.RobotStatus;
import com.mediassistrover.backend.repository.JobRepository;
import com.mediassistrover.backend.repository.RobotRepository;
import com.mediassistrover.backend.service.JobDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobDispatchServiceImpl implements JobDispatchService {
    private static final java.util.Set<JobStatus> OCCUPYING = com.mediassistrover.backend.service.JobOccupancyPolicy.MOTION_OWNERS;
    private final JobRepository jobRepository;
    private final RobotRepository robotRepository;
    private final Clock clock;
    private final com.mediassistrover.backend.service.OrchestrationWorkService wakeups;
    private final com.mediassistrover.backend.validation.WorkflowExecutionReadiness readiness;
    private final com.mediassistrover.backend.service.RobotTaskArbitrationService arbitration;

    @Override
    @Transactional
    public int dispatchQueuedJobs(int limit) {
        if (limit < 1) return 0;
        // Discover queue IDs without locking the shared status index. Lock only
        // each selected primary row, in dispatch priority order, then recheck.
        List<UUID> queued = jobRepository.findUnassignedQueuedJobIds(PageRequest.of(0, limit));
        int assigned = 0;
        for (UUID id : queued) {
            Job job=jobRepository.lockById(id).orElse(null);
            if (job==null || job.getStatus()!=JobStatus.QUEUED || job.getRobot()!=null) continue;
            Robot robot = chooseRobot(job);
            if (robot == null) continue;
            job.setRobot(robot);
            if (!job.isBeOrchestrated()) {job.setDispatchCommandId(UUID.randomUUID());job.setDispatchExpiresAt(Instant.now(clock).plusSeconds(60));}
            wakeups.request(job);
            assigned++;
        }
        return assigned;
    }

    private Robot chooseRobot(Job job) {
        FacilityMap map = job.getMap();
        List<Robot> candidates = map == null
                ? robotRepository.findByStatusOrderByRobotCodeAsc(RobotStatus.IDLE)
                : robotRepository.findByStatusAndCurrentMapMapIdOrderByRobotCodeAsc(RobotStatus.IDLE, map.getMapId());
        Robot idle = candidates.stream()
                .map(candidate -> robotRepository.findByIdForUpdate(candidate.getRobotId()).orElseThrow())
                .filter(robot -> robot.getStatus()==RobotStatus.IDLE)
                .filter(robot -> readiness.acceptsRobot(job.getWorkflow(),robot))
                .filter(robot -> jobRepository.findByRobotRobotIdAndStatusIn(robot.getRobotId(), OCCUPYING).isEmpty())
                .findFirst().orElse(null);
        if (idle != null || !job.isBeOrchestrated()) return idle;
        // A background patrol may yield only after the foreground job is durably queued for this robot.
        return jobRepository.findYieldablePatrolCandidates(PageRequest.of(0, 100)).stream()
                .filter(patrol -> patrol.getPriority() < job.getPriority())
                .map(Job::getRobot).filter(java.util.Objects::nonNull)
                .filter(robot -> map == null || robot.getCurrentMap() != null && map.getMapId().equals(robot.getCurrentMap().getMapId()))
                .map(robot -> robotRepository.findByIdForUpdate(robot.getRobotId()).orElseThrow())
                .filter(robot -> readiness.acceptsRobot(job.getWorkflow(), robot))
                .filter(robot -> arbitration.yieldingPatrol(robot, job.getPriority()))
                .findFirst().orElse(null);
    }
}
