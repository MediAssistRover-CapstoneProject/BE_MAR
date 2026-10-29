package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.job.request.BulkRecoveryRequest;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.UUID;

/** Intentionally no enclosing transaction: each recovery commits or rolls back independently. */
@Service @RequiredArgsConstructor
public class BulkRecoveryService {
    private final MqttOperationRecoveryService recovery;
    private final ObjectMapper json;

    public JsonNode close(BulkRecoveryRequest request, AccountPrincipal actor, RequestMetadata metadata) {
        var ids=new HashSet<UUID>();
        for(var item:request.operations())
            if(!ids.add(item.jobId()))throw new BusinessRuleException("Duplicate jobId in batch");
        var result=json.createObjectNode().put("dryRun",request.dryRun());
        var rows=result.putArray("results");int closed=0,blocked=0;
        for(var item:request.operations()) {
            var row=rows.addObject().put("jobId",item.jobId().toString()).put("jobStepId",item.jobStepId().toString());
            try {
                if(request.dryRun()) {
                    row.put("result","EVIDENCE_ONLY");
                    row.set("evidence",recovery.evidence(item.jobId(),actor));
                } else {
                    row.set("receipt",recovery.reconcile(item.jobId(),item.jobStepId(),item.idempotencyKey(),item.recovery(),actor,metadata));
                    row.put("result","CLOSED");closed++;
                }
            } catch(AccessDeniedException e) {
                row.put("result","BLOCKED").put("reason","ACCESS_DENIED");blocked++;
            } catch(BusinessRuleException | ConflictException | ResourceNotFoundException e) {
                row.put("result","BLOCKED").put("reason",e.getMessage());blocked++;
            }
        }
        return result.put("closed",closed).put("blocked",blocked)
                .put("note",request.dryRun()?"Evidence preview only; eligibility is rechecked under lock when applying.":"Blocked jobs remain unchanged. No motion command sent.");
    }
}
