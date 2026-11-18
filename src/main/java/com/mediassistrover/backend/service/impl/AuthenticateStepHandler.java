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
    private final com.mediassistrover.backend.service.FaceAuthService face;
    @Override public boolean supports(JobStep step){return StepType.AUTHENTICATE.name().equals(step.getStepTypeSnapshot());}
    @Override public ServerStepExecution execute(JobStep step,JsonNode inputs){if("FACE_VERIFICATION".equals(step.getStep().getParamsJson().path("method").asText())){var w=face.ensure(step);return new ServerStepExecution(objectMapper.createObjectNode().put("action","AWAIT_FACE_VERIFICATION").put("job_step_id",w.authJobStepId().toString()).put("expires_at",w.expiresAt().toString()),false);}var challenge=qrVerificationService.ensureForStep(step);ObjectNode result=objectMapper.createObjectNode().put("action","AWAIT_STAFF_VERIFICATION").put("job_step_id",step.getJobStepId().toString()).put("challenge_id",challenge.challengeId().toString()).put("purpose",challenge.purpose().name()).put("expires_at",challenge.expiresAt().toString());return new ServerStepExecution(result,false);}
}
