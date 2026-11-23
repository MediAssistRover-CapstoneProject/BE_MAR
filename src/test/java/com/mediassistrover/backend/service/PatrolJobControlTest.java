package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolJobControlTest {
    final JobRepository jobs=mock(JobRepository.class);
    final JobService views=mock(JobService.class);
    final JobSuspensionService suspension=mock(JobSuspensionService.class);
    final StaffOperationalScopePolicy scope=mock(StaffOperationalScopePolicy.class);
    final AccountRepository accounts=mock(AccountRepository.class);
    final AuditService audit=mock(AuditService.class);
    final PatrolJobControlService control=new PatrolJobControlService(jobs,views,suspension,scope,accounts,audit);
    final Account account=Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).build();
    final AccountPrincipal actor=AccountPrincipal.fromAccount(account);

    Job job(JobStatus status){
        var job=Job.builder().jobId(UUID.randomUUID()).status(status).build();
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(suspension.isPatrol(job)).thenReturn(true);
        when(accounts.findById(account.getAccountId())).thenReturn(Optional.of(account));
        return job;
    }
    @Test void suspendUsesManualHoldAndReturnsAuthoritativeJobView(){
        var job=job(JobStatus.RUNNING);
        var view=new JobDetailResponse(null,List.of(),List.of());
        when(views.get(job.getJobId(),false,actor)).thenReturn(view);
        assertSame(view,control.suspend(job.getJobId(),"Pause",actor,null));
        verify(suspension).request(job,"MANUAL",job.getJobId());
        assertEquals(JobStatus.RUNNING,job.getStatus(),"API must not invent a physical stop");
    }
    @Test void resumeOnlyReleasesManualHoldAndNeverDirectlyAcquiresRobot(){
        var job=job(JobStatus.SUSPENDED);
        control.resume(job.getJobId(),"Continue",actor,null);
        verify(suspension).release(job,"MANUAL",job.getJobId());
        verify(suspension,never()).tryResume(any());
        assertTrue(job.isResumeRequested());assertEquals(JobStatus.SUSPENDED,job.getStatus());
    }
    @Test void cancelledIntentCannotBeResumed(){
        var job=job(JobStatus.SUSPENDED);job.setCancelRequestedAt(java.time.Instant.now());
        assertThrows(BusinessRuleException.class,()->control.resume(job.getJobId(),"Continue",actor,null));
        verify(suspension,never()).release(any(),any(),any());
    }
    @Test void resumeCanReleaseManualHoldOnAnUnstartedQueuedPatrol(){
        var job=job(JobStatus.QUEUED);job.setSchemaVersion(3);job.setBeOrchestrated(true);
        job.setWorkflow(Workflow.builder().serviceType("PATROL").build());
        control.resume(job.getJobId(),"Continue waiting",actor,null);
        verify(suspension).release(job,"MANUAL",job.getJobId());
        assertEquals(JobStatus.QUEUED,job.getStatus());verify(suspension,never()).tryResume(any());
    }
    @Test void callerMustHaveAccessToThisJob(){
        var job=job(JobStatus.RUNNING);
        doThrow(new AccessDeniedException("denied")).when(scope).requireJobAccess(job,actor);
        assertThrows(AccessDeniedException.class,()->control.suspend(job.getJobId(),"Pause",actor,null));
        verify(suspension,never()).request(any(),any(),any());
    }
    @Test void observingPatrolRetainsItsReachedCheckpointInTheJobView(){
        var job=job(JobStatus.RUNNING);job.setPatrolRound(2);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var endpoint=UUID.randomUUID();
        var sensing=JobStep.builder().jobStepId(UUID.randomUUID()).stepTypeSnapshot("EXECUTE_SENSING")
                .executionSequenceNo(8).resolvedInputsJson(json.createObjectNode().put("sensing_type","PATROL_FALL")).build();
        job.setSelectedJobStep(sensing);
        var reached=JobStep.builder().stepTypeSnapshot("MOVE").status(JobStepStatus.SUCCEEDED)
                .resolvedInputsJson(json.createObjectNode().put("endpoint_id",endpoint.toString())).build();
        var history=mock(JobStepRepository.class);var holds=mock(JobSuspensionHoldRepository.class);
        when(history.findReachedPatrolCheckpoints(eq(job.getJobId()),eq(2),eq(8L),any())).thenReturn(List.of(reached));
        var state=new PatrolJobStateService(suspension,holds,history).snapshot(job);
        assertEquals(endpoint,state.targetEndpointId());assertEquals("EXECUTE_SENSING",state.phase());
        assertEquals(2,state.round());assertEquals(sensing.getJobStepId(),state.selectedStepId());
    }
}
