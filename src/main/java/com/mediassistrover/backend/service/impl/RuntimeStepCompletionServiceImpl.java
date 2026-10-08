package com.mediassistrover.backend.service.impl;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.RuntimeStepCompletionService;
import com.mediassistrover.backend.validation.StepResultContractValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class RuntimeStepCompletionServiceImpl implements RuntimeStepCompletionService {
    private final StepResultContractValidator validator; private final JobStepRepository stepRepository;
    private final JobEventRepository eventRepository; private final Clock clock;
    @Override public boolean complete(JobStep supplied,JsonNode result,Account actor,JobEventProducerType producer,String eventType,UUID eventId){
        JobStep step=stepRepository.lockById(supplied.getJobStepId()).orElseThrow();
        if(step.getStatus()==JobStepStatus.SUCCEEDED)return false;
        if(step.getStatus()!=JobStepStatus.RUNNING)return false;
        validator.validate(step,result);Instant now=Instant.now(clock);
        step.setResultJson(result.deepCopy());step.setStatus(JobStepStatus.SUCCEEDED);step.setEndedAt(now);
        JobTask task=step.getJobTask();List<JobStep> taskSteps=stepRepository.findByJobId(task.getJob().getJobId()).stream().filter(s->s.getJobTask().getJobTaskId().equals(task.getJobTaskId())).toList();
        if(taskSteps.stream().allMatch(s->s.getJobStepId().equals(step.getJobStepId())||s.getStatus()==JobStepStatus.SUCCEEDED)){task.setStatus(JobTaskStatus.SUCCEEDED);task.setEndedAt(now);}
        eventRepository.save(JobEvent.builder().jobEventId(eventId==null?UUID.randomUUID():eventId).job(task.getJob()).robot(task.getJob().getRobot()).jobTask(task).jobStep(step).actorAccount(actor).producerType(producer).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(eventType).previousStatus(JobStepStatus.RUNNING.name()).newStatus(JobStepStatus.SUCCEEDED.name()).statusScope("JOB_STEP").eventDataJson(result.deepCopy()).occurredAt(now).receivedAt(now).persistedAt(now).build());
        return true;
    }
}
