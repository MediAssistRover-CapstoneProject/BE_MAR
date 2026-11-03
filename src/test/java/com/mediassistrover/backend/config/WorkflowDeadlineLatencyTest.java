package com.mediassistrover.backend.config;

import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.repository.JobRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowDeadlineLatencyTest {
    final Instant now=Instant.parse("2026-11-02T18:00:00Z");
    JobRepository.DeadlineCandidate moving(){
        var row=mock(JobRepository.DeadlineCandidate.class);
        when(row.getStartedAt()).thenReturn(now.minusSeconds(20));when(row.getMaxDurationSec()).thenReturn(600);
        when(row.getStepStatus()).thenReturn(JobStepStatus.RUNNING);when(row.getStepType()).thenReturn("MOVE");
        when(row.getStepStartedAt()).thenReturn(now.minusSeconds(10));when(row.getStepTimeoutSec()).thenReturn(180);
        return row;
    }
    @Test void ordinaryMoveDoesNotAcquireGraphLocksEverySecondButItsExactTimeoutStillDoes(){
        var row=moving();assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isFalse();
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now.plusSeconds(170))).isTrue();
    }
    @Test void cancellationCompletionAndServerWaitAreNeverSkipped(){
        var row=moving();when(row.getCancelRequestedAt()).thenReturn(now);
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isTrue();
        when(row.getCancelRequestedAt()).thenReturn(null);when(row.getStepStatus()).thenReturn(JobStepStatus.SUCCEEDED);
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isTrue();
        when(row.getStepStatus()).thenReturn(JobStepStatus.RUNNING);when(row.getStepType()).thenReturn("WAIT");
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isTrue();
    }
    @Test void workflowDeadlineStillWinsEvenWhenTheIndividualMoveHasTimeLeft(){
        var row=moving();when(row.getMaxDurationSec()).thenReturn(20);
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isTrue();
    }
    @Test void anAlreadyPublishedCommandDoesNotWakeTheGraphWhilePiCommitsItsStartButExpiryStillDoes(){
        var row=moving();when(row.getStepStatus()).thenReturn(JobStepStatus.PENDING);
        when(row.getCommandState()).thenReturn("ACKNOWLEDGED");when(row.getCommandExpiresAt()).thenReturn(now.plusSeconds(5));
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now)).isFalse();
        assertThat(WorkflowOrchestrationScheduler.needsDeadlineWork(row,now.plusSeconds(5))).isTrue();
    }
}
