package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttRolloutService;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** One idempotent run of an existing definition; never creates another workflow. */
@Service @RequiredArgsConstructor
public class PublishedWorkflowJobService {
    public record Create(@NotNull UUID robotId,UUID mapId,UUID navigationSessionId,
                         @NotNull JsonNode inputs,@Min(0) @Max(100) Integer priority) { }
    public record Result(UUID jobId,UUID workflowId,String status,boolean replayed) { }
    private final JobExecutionService execution;
    private final RobotTestSubmissionRepository submissions;
    private final JobRepository jobs;
    private final AccountRepository accounts;
    private final MqttRolloutService rollout;
    private final MqttWire wire;
    private final Clock clock;
    private final AuditService audit;

    @Transactional
    public Result run(UUID workflowId,Create request,String key,AccountPrincipal actor,RequestMetadata metadata) {
        requireAdmin(actor);
        if(request.inputs()==null || !request.inputs().isObject() || wire.bytes(request.inputs()).length>65536)
            throw new BusinessRuleException("Workflow inputs must be a JSON object of at most 64 KiB");
        rollout.lockForRuntimeCreation();
        String requestKey=requestKey(key,actor);
        ObjectNode content=wire.object().put("workflowId",workflowId.toString());
        content.set("request",wire.tree(request));
        String fingerprint=wire.canonicalHash(content);
        submissions.reserve(requestKey,actor.accountId(),fingerprint,clock.instant());
        var receipt=submissions.lockByKey(requestKey).orElseThrow();
        if(!actor.accountId().equals(receipt.getActorAccountId()) || !fingerprint.equals(receipt.getRequestFingerprint()))
            throw new ConflictException("Idempotency-Key was used for a different workflow run");
        if(receipt.getJobId()!=null) {
            Job previous=jobs.findById(receipt.getJobId()).orElseThrow(()->new ResourceNotFoundException(
                    "Original job was removed; use a new key only for a new requested run"));
            return result(previous,true);
        }
        Job job=execution.createForPublished(workflowId,request.robotId(),request.mapId(),
                request.navigationSessionId(),request.inputs(),request.priority());
        ObjectNode input=job.getInputJson().deepCopy();
        input.put("requested_by_account_id",actor.accountId().toString());
        // Only the validated ROBOT_TEST path receives mixed commissioning proof.
        if("ROBOT_TEST".equals(job.getWorkflow().getServiceType())) {
            input.put("test_request_key",requestKey).put("test_request_fingerprint",fingerprint);
        }
        job.setInputJson(input);
        receipt.setJobId(job.getJobId());
        audit.record(accounts.findById(actor.accountId()).orElseThrow(),"PUBLISHED_WORKFLOW_JOB_CREATED","Job",
                job.getJobId(),null,Map.of("workflowId",workflowId,"robotId",request.robotId()),metadata);
        return result(job,false);
    }

    @Transactional(readOnly=true)
    public Result lookup(UUID workflowId,String key,AccountPrincipal actor) {
        requireAdmin(actor);
        var receipt=submissions.findById(requestKey(key,actor)).orElse(null);
        var job=receipt==null || receipt.getJobId()==null?null:jobs.findById(receipt.getJobId()).orElse(null);
        if(job==null)return null;
        if(!workflowId.equals(job.getWorkflow().getWorkflowId()))
            throw new ConflictException("Idempotency-Key belongs to another workflow");
        return result(job,true);
    }
    private String requestKey(String key,AccountPrincipal actor) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{1,150}"))
            throw new BusinessRuleException("Idempotency-Key is required (1..150 safe characters)");
        return wire.canonicalHash(wire.object().put("scope","published-workflow-run")
                .put("actor",actor.accountId().toString()).put("key",key));
    }
    private void requireAdmin(AccountPrincipal actor) {
        if(actor==null || actor.role()!=Role.ADMIN)throw new AccessDeniedException("ADMIN is required to run workflow definitions directly");
    }
    private Result result(Job job,boolean replayed) {
        return new Result(job.getJobId(),job.getWorkflow().getWorkflowId(),job.getStatus().name(),replayed);
    }
}
