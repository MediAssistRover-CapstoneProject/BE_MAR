package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JobFailureSummaryTest {
    Job job(){return Job.builder().selectedJobStep(JobStep.builder().stepCodeSnapshot("MOVE_TO_ENDPOINT")
        .status(JobStepStatus.FAILED).errorCode("PATH_UNAVAILABLE").build()).build();}
    @Test void surfacesMoveFailureCodeAndStepName(){
        var job=job();JobFailureSummary.apply(job,TerminalStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("PATH_UNAVAILABLE");
        assertThat(job.getFailureReason()).isEqualTo("Step MOVE_TO_ENDPOINT failed: PATH_UNAVAILABLE");
    }
    @Test void preservesWorkflowLevelFailure(){
        var job=job();job.setErrorCode("WORKFLOW_DURATION_EXCEEDED");job.setFailureReason("Workflow timed out");
        JobFailureSummary.apply(job,TerminalStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("WORKFLOW_DURATION_EXCEEDED");assertThat(job.getFailureReason()).isEqualTo("Workflow timed out");
    }
    @Test void cancellationAndHandledFailuresDoNotBecomeJobFailures(){
        for(var status:new TerminalStatus[]{TerminalStatus.CANCELLED,TerminalStatus.SUCCEEDED,TerminalStatus.RECOVERY_REQUIRED}){
            var job=job();JobFailureSummary.apply(job,status);assertThat(job.getErrorCode()).isNull();
        }
    }
    @Test void intentionalFailedTerminalAfterSuccessDoesNotInventAnError(){
        var job=job();job.getSelectedJobStep().setStatus(JobStepStatus.SUCCEEDED);JobFailureSummary.apply(job,TerminalStatus.FAILED);
        assertThat(job.getErrorCode()).isNull();
    }
}
