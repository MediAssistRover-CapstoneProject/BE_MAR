package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.media.*;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotTaskArbitrationTest {
    final JobRepository jobs=mock(JobRepository.class);
    final JobSuspensionHoldRepository holds=mock(JobSuspensionHoldRepository.class);
    final JobSuspensionService suspension=mock(JobSuspensionService.class);
    final VoiceInteractionRepository voices=mock(VoiceInteractionRepository.class);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final MediaSessionRepository media=mock(MediaSessionRepository.class);
    final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final NavigationSessionService navigation=mock(NavigationSessionService.class);
    final RobotTaskArbitrationService arbitration=new RobotTaskArbitrationService(jobs,holds,suspension,voices,incidents,media,wakeups,robots,navigation,mock(com.mediassistrover.backend.repository.AlertRepository.class));
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.IDLE).build();

    RobotTaskArbitrationTest(){
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
    }
    Job job(String type,JobStatus state,int priority){
        return Job.builder().jobId(UUID.randomUUID()).robot(robot).status(state).priority(priority)
                .workflow(Workflow.builder().serviceType(type).build()).beOrchestrated(true).schemaVersion(3).queuedAt(Instant.now()).build();
    }
    void owners(Job... jobs){when(this.jobs.findByRobotRobotIdAndStatusIn(eq(robot.getRobotId()),eq(JobOccupancyPolicy.MOTION_OWNERS))).thenReturn(List.of(jobs));}

    @Test void databaseIdleDoesNotOverridePendingPatrolStop(){
        Job patrol=job("PATROL",JobStatus.SUSPENDING,1),sos=job("EMERGENCY",JobStatus.QUEUED,100);
        owners(patrol,sos);
        assertFalse(arbitration.canStart(sos));
        assertTrue(JobOccupancyPolicy.EXECUTING.contains(patrol.getStatus()),"Heartbeat must keep the robot held until suspension settles");
    }
    @Test void queuePriorityIsRecheckedUnderRobotLock(){
        Job staff=job("NAVIGATION",JobStatus.QUEUED,50),sos=job("EMERGENCY",JobStatus.QUEUED,100);
        owners(staff,sos);
        assertFalse(arbitration.canStart(staff));assertTrue(arbitration.canStart(sos));
        verify(robots,times(2)).findByIdForUpdate(robot.getRobotId());
    }
    @Test void voiceHoldsOrdinaryWorkButEmergencyMayProceed(){
        Job staff=job("NAVIGATION",JobStatus.QUEUED,50),sos=job("EMERGENCY",JobStatus.QUEUED,100);
        when(voices.findByRobotRobotIdAndStatus(robot.getRobotId(),VoiceInteractionStatus.ACTIVE)).thenReturn(List.of(new VoiceInteraction()));
        owners(staff);assertFalse(arbitration.canStart(staff));
        owners(sos);assertTrue(arbitration.canStart(sos));
    }
    @Test void assistMediaDoesNotPermitMovementEvenForEmergency(){
        var sos=job("EMERGENCY",JobStatus.QUEUED,100);owners(sos);
        var session=new MediaSession();session.setMode(MediaSession.Mode.ASSIST);
        when(media.findByActiveRobotId(robot.getRobotId())).thenReturn(Optional.of(session));
        assertFalse(arbitration.canStart(sos));
    }
    @Test void pinnedNavigationSessionIsRevalidatedWhenJobActuallyStarts(){
        var staff=job("NAVIGATION",JobStatus.QUEUED,50);owners(staff);var sessionId=UUID.randomUUID();
        var map=FacilityMap.builder().mapId(UUID.randomUUID()).build();staff.setMap(map);
        staff.setInputJson(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("navigation_session_id",sessionId.toString()));
        doThrow(new com.mediassistrover.backend.exception.BusinessRuleException("expired"))
                .when(navigation).requireReadyContext(robot.getRobotId(),sessionId,map.getMapId());
        assertFalse(arbitration.canStart(staff));
        verify(navigation).requireReadyContext(robot.getRobotId(),sessionId,map.getMapId());
    }
    @Test void aSuspendedPatrolIsNotAMotorOwnerAndMultipleForegroundJobsCanWait(){
        Job patrol=job("PATROL",JobStatus.SUSPENDED,1),staff=job("NAVIGATION",JobStatus.QUEUED,50);
        when(jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),JobOccupancyPolicy.NON_TERMINAL)).thenReturn(List.of(patrol,staff));
        assertTrue(arbitration.yieldingPatrol(robot,60));
        assertFalse(JobOccupancyPolicy.MOTION_OWNERS.contains(JobStatus.SUSPENDED));
        owners(staff);assertTrue(arbitration.canStart(staff));
        staff.setStatus(JobStatus.RUNNING);assertFalse(arbitration.yieldingPatrol(robot,100));
    }
    @Test void reconcileRetainsJobHoldUntilItsJobIsTerminalAndNeverReleasesAnIncident(){
        Job patrol=job("PATROL",JobStatus.SUSPENDED,1),staff=job("NAVIGATION",JobStatus.RUNNING,50);
        var jobHold=JobSuspensionHold.builder().job(patrol).source("JOB").sourceId(staff.getJobId()).build();
        var incident=JobSuspensionHold.builder().job(patrol).source("INCIDENT").sourceId(UUID.randomUUID()).build();
        when(jobs.lockRobotJobs(robot.getRobotId(),JobOccupancyPolicy.NON_TERMINAL)).thenReturn(List.of(patrol,staff));
        when(holds.findByJobJobIdOrderByCreatedAt(patrol.getJobId())).thenReturn(List.of(jobHold,incident));
        when(jobs.findById(staff.getJobId())).thenReturn(Optional.of(staff));
        arbitration.reconcileRobot(robot.getRobotId());
        verify(suspension).request(patrol,"JOB",staff.getJobId());
        verify(suspension,never()).release(any(),any(),any());
        staff.setStatus(JobStatus.SUCCEEDED);
        when(jobs.lockRobotJobs(robot.getRobotId(),JobOccupancyPolicy.NON_TERMINAL)).thenReturn(List.of(patrol));
        arbitration.reconcileRobot(robot.getRobotId());
        verify(suspension).release(patrol,"JOB",staff.getJobId());
        verify(suspension,never()).release(eq(patrol),eq("INCIDENT"),any());
    }
}
