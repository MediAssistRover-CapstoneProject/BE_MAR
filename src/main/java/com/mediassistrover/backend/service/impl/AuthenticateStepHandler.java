package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.service.ServerStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AuthenticateStepHandler implements ServerStepHandler {
    private final ObjectMapper objectMapper;
    private final com.mediassistrover.backend.service.QrVerificationService qrVerificationService;
    @Override public boolean supports(JobStep step){return StepType.AUTHENTICATE.name().equals(step.getStepTypeSnapshot());}
    @Override public ServerStepExecution execute(JobStep step,JsonNode inputs){var challenge=qrVerificationService.ensureForStep(step);ObjectNode result=objectMapper.createObjectNode().put("action","AWAIT_STAFF_VERIFICATION").put("job_step_id",step.getJobStepId().toString()).put("challenge_id",challenge.challengeId().toString()).put("purpose",challenge.purpose().name()).put("expires_at",challenge.expiresAt().toString());return new ServerStepExecution(result,false);}
}
