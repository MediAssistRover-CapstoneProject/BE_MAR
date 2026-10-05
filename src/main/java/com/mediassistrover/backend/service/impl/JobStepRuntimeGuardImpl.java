package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.StepTransition;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.enums.TerminalStatus;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.JobStepRepository;
import com.mediassistrover.backend.service.JobStepRuntimeGuard;
import com.mediassistrover.backend.service.WorkflowRuntimeTransitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobStepRuntimeGuardImpl implements JobStepRuntimeGuard {
    private static final EnumSet<JobStepStatus> COMPLETED=EnumSet.of(JobStepStatus.SUCCEEDED,JobStepStatus.FAILED,JobStepStatus.TIMED_OUT);
    private final JobStepRepository stepRepository;
    private final WorkflowRuntimeTransitionService transitionService;

    @Override public void requireStartAllowed(JobStep candidate){
        Job job=candidate.getJobTask().getJob();List<JobStep> completed=completed(job);
        if(completed.isEmpty()){
            if(job.getWorkflow().getStartStep()==null||!job.getWorkflow().getStartStep().getStepId().equals(candidate.getStep().getStepId()))throw new BusinessRuleException("Only the Workflow start Step may run first");
            return;
        }
        JobStep previous=completed.getLast();StepTransition route=transitionService.resolve(previous);
        if(route.getToStep()==null)throw new BusinessRuleException("Workflow already reached terminal status "+route.getTerminalStatus());
        if(!route.getToStep().getStepId().equals(candidate.getStep().getStepId()))throw new BusinessRuleException("Step is not the selected transition target");
    }

    @Override public void requireSuccessfulTerminal(Job job){
        List<JobStep> all=stepRepository.findByJobId(job.getJobId());
        if(all.stream().anyMatch(s->s.getStatus()==JobStepStatus.RUNNING||s.getStatus()==JobStepStatus.RETRYING))throw new BusinessRuleException("Job has an unfinished active Step");
        List<JobStep> completed=all.stream().filter(s->COMPLETED.contains(s.getStatus())).sorted(order()).toList();
        if(completed.isEmpty())throw new BusinessRuleException("Job has no completed Step");
        StepTransition route=transitionService.resolve(completed.getLast());
        if(route.getToStep()!=null||route.getTerminalStatus()!=TerminalStatus.SUCCEEDED)throw new BusinessRuleException("Job cannot succeed before Workflow reaches terminal SUCCEEDED");
    }

    private List<JobStep> completed(Job job){return stepRepository.findByJobId(job.getJobId()).stream().filter(s->COMPLETED.contains(s.getStatus())).sorted(order()).toList();}
    private Comparator<JobStep> order(){return Comparator.comparing(JobStep::getEndedAt,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(JobStep::getExecutionSequenceNo);}
}
