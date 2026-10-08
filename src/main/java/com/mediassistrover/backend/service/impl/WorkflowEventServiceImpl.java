package com.mediassistrover.backend.service.impl;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.job.request.WorkflowEventRequest;
import com.mediassistrover.backend.dto.job.response.WorkflowEventResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.RuntimeAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkflowEventServiceImpl implements WorkflowEventService {
    private final JobRepository jobRepository; private final JobEventRepository eventRepository; private final JobStepRepository stepRepository;
    private final AccountRepository accountRepository; private final RuntimeStepCompletionService completionService; private final AuditService auditService;
    private final ObjectMapper objectMapper; private final Clock clock;
    @Override @Transactional public WorkflowEventResponse publish(UUID jobId,WorkflowEventRequest r,AccountPrincipal actor,RequestMetadata md){
        Job job=jobRepository.lockById(jobId).orElseThrow(()->new ResourceNotFoundException("Job not found: "+jobId));RuntimeAuthorizationPolicy.requireJobScope(job,actor);
        JobEvent duplicate=eventRepository.findById(r.eventId()).orElse(null);if(duplicate!=null){if(!duplicate.getJob().getJobId().equals(jobId))throw new ConflictException("eventId belongs to another Job");return response(duplicate,true);}RuntimeAuthorizationPolicy.requireRunning(job);
        Instant now=Instant.now(clock);if(r.occurredAt().isAfter(now.plusSeconds(300)))throw new BusinessRuleException("occurredAt is too far in the future");if(job.getStartedAt()!=null&&r.occurredAt().isBefore(job.getStartedAt()))throw new BusinessRuleException("Event predates this Job run");Account account=accountRepository.findById(actor.accountId()).orElseThrow();
        JobEvent event=eventRepository.saveAndFlush(JobEvent.builder().jobEventId(r.eventId()).job(job).robot(job.getRobot()).actorAccount(account).producerType(actor.role()==Role.ADMIN?JobEventProducerType.ADMIN:JobEventProducerType.STAFF).producerSessionId(r.eventId()).sequenceNo(1).eventType(r.eventType()).correlationKey(r.correlationKey()).statusScope("WORKFLOW_EVENT").eventDataJson(r.data()==null?objectMapper.createObjectNode():r.data().deepCopy()).occurredAt(r.occurredAt()).receivedAt(now).persistedAt(now).build());
        for(JobStep wait:stepRepository.lockRunningWaits(jobId))if(consumeAvailable(wait))break;
        auditService.record(account,"WORKFLOW_EVENT_PUBLISHED","Job",jobId,null,Map.of("eventId",r.eventId(),"eventType",r.eventType()),md);return response(event,false);
    }
    @Override public boolean consumeAvailable(JobStep wait){
        JsonNode input=wait.getResolvedInputsJson();if(input==null||!"EVENT".equals(input.path("wait_type").asText()))return false;String type=input.path("event_type").asText();String correlation=input.path("correlation_key").asText(wait.getOperationKey());Job job=wait.getJobTask().getJob();Instant notBefore=job.getStartedAt()==null?job.getQueuedAt():job.getStartedAt();List<JobEvent> matches=eventRepository.lockUnconsumed(job.getJobId(),type,correlation,notBefore);if(matches.isEmpty())return false;JobEvent event=matches.getFirst();Instant now=Instant.now(clock);event.setConsumedByJobStep(wait);event.setConsumedAt(now);
        ObjectNode eventJson=objectMapper.createObjectNode().put("event_id",event.getJobEventId().toString()).put("type",event.getEventType()).put("correlation_key",event.getCorrelationKey()).put("job_id",job.getJobId().toString()).put("actor_user_id",event.getActorAccount().getAccountId().toString()).put("occurred_at",event.getOccurredAt().toString());ObjectNode result=objectMapper.createObjectNode().put("completed",true).put("wake_reason","EVENT").put("elapsed_sec",Math.max(0,Duration.between(wait.getStartedAt(),now).toMillis()/1000.0));result.set("event",eventJson);
        return completionService.complete(wait,result,event.getActorAccount(),event.getProducerType(),"WAIT_EVENT_CONSUMED",null);
    }
    private WorkflowEventResponse response(JobEvent e,boolean duplicate){return new WorkflowEventResponse(e.getJobEventId(),e.getJob().getJobId(),e.getEventType(),e.getCorrelationKey(),duplicate,e.getConsumedByJobStep()==null?null:e.getConsumedByJobStep().getJobStepId(),e.getConsumedAt());}
}
