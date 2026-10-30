package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.enums.TerminalStatus;
import java.util.Set;

/** Expose the terminal step's code without copying arbitrary runtime payloads. */
final class JobFailureSummary {
    private JobFailureSummary() { }
    static void apply(Job job,TerminalStatus terminal) {
        var step=job.getSelectedJobStep();
        if(terminal!=TerminalStatus.FAILED || step==null || !Set.of(JobStepStatus.FAILED,JobStepStatus.TIMED_OUT).contains(step.getStatus()))return;
        if(job.getErrorCode()==null || job.getErrorCode().isBlank()) {
            var code=step.getErrorCode();
            job.setErrorCode(code==null || code.isBlank()?(step.getStatus()==JobStepStatus.TIMED_OUT?"STEP_TIMEOUT":"STEP_FAILED"):code);
        }
        if(job.getFailureReason()==null || job.getFailureReason().isBlank())
            job.setFailureReason("Step "+step.getStepCodeSnapshot()+" failed: "+job.getErrorCode());
    }
}
