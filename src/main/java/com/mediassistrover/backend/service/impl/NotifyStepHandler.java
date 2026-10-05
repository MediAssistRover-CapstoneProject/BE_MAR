package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.EmergencyIncidentRepository;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;

@Component @RequiredArgsConstructor
public class NotifyStepHandler implements ServerStepHandler {
    private final AlertService alertService; private final EmergencyIncidentRepository incidentRepository;
    private final ObjectMapper objectMapper; private final Clock clock;
    @Override public boolean supports(JobStep step){return StepType.NOTIFY.name().equals(step.getStepTypeSnapshot());}
    @Override public ServerStepExecution execute(JobStep step,JsonNode inputs){Job job=step.getJobTask().getJob();JsonNode params=step.getStep().getParamsJson();String type=text(params,"notification_type",step.getStepCodeSnapshot());AlertSeverity severity=type.contains("SOS")||type.contains("EMERGENCY")?AlertSeverity.CRITICAL:type.contains("FAILED")?AlertSeverity.WARNING:AlertSeverity.INFO;String message=text(params,"message",type.replace('_',' '));EmergencyIncident incident=null;if(job.getServiceRequest()!=null&&job.getServiceRequest().getRelatedIncidentId()!=null)incident=incidentRepository.findById(job.getServiceRequest().getRelatedIncidentId()).orElse(null);Alert alert=alertService.createSystemAlert("NOTIFY:"+step.getJobStepId(),type,severity,message,inputs,incident,job.getServiceRequest(),step,null,severity!=AlertSeverity.INFO,AlertAckPolicy.ANY,Instant.now(clock).plusSeconds(step.getTimeoutSecSnapshot()));ObjectNode result=objectMapper.createObjectNode().put("alert_id",alert.getAlertId().toString()).put("notification_type",type).put("persisted",true);return new ServerStepExecution(result,true);}
    private String text(JsonNode node,String field,String fallback){return node!=null&&node.path(field).isTextual()?node.path(field).asText():fallback;}
}
