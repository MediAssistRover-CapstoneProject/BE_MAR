package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.EndpointGroupMemberRepository;
import com.mediassistrover.backend.repository.EndpointRepository;
import com.mediassistrover.backend.repository.HospitalInformationRepository;
import com.mediassistrover.backend.service.ServerStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataServiceStepHandler implements ServerStepHandler {
    private final HospitalInformationRepository hospitalRepository;
    private final EndpointRepository endpointRepository;
    private final EndpointGroupMemberRepository groupMemberRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override public boolean supports(JobStep step){return step.getStep().getStepType()==StepType.DATA_SERVICE;}

    @Override public ServerStepExecution execute(JobStep step,JsonNode inputs){
        String key=inputs.path("service_key").asText(step.getStepCodeSnapshot().equals("QUERY")?"QUERY_HOSPITAL_INFORMATION":step.getStepCodeSnapshot());
        ObjectNode result=objectMapper.createObjectNode();ObjectNode data=result.putObject("data");
        switch(key){
            case "QUERY_HOSPITAL_INFORMATION" -> {
                ArrayNode items=data.putArray("items");
                hospitalRepository.findPublicEffective(HospitalInformationStatus.PUBLISHED,Instant.now(clock),PageRequest.of(0,20)).forEach(info->items.addObject().put("id",info.getHospitalInformationId().toString()).put("category",info.getInfoCategory()).put("title",info.getTitle()).put("content",info.getContent()).put("languageCode",info.getLanguageCode()));
                data.put("count",items.size());
            }
            case "RESOLVE_TARGETS" -> {
                UUID groupId=uuid(inputs,"endpoint_group_id");ArrayNode targets=data.putArray("targets");
                groupMemberRepository.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(groupId).stream().filter(m->m.isActive()&&m.getEndpoint().isActive()).forEach(m->targets.add(endpoint(m.getEndpoint())));
            }
            case "SELECT_TARGET" -> {
                JsonNode targets=inputs.path("targets");int index=inputs.path("index").asInt(0);boolean valid=targets.isArray()&&index>=0&&index<targets.size();data.put("has_target",valid);if(valid)data.set("target",targets.get(index).deepCopy());if(valid&&targets.get(index).has("endpoint_id"))data.set("endpoint_id",targets.get(index).get("endpoint_id"));
            }
            case "CHECK_TARGET" -> {
                UUID endpointId=uuid(inputs,"endpoint_id");Endpoint endpoint=endpointRepository.findById(endpointId).orElse(null);boolean valid=endpoint!=null&&endpoint.isActive()&&endpoint.isAccessible();data.put("is_valid",valid);if(valid)data.set("endpoint",endpoint(endpoint));
            }
            default -> throw new BusinessRuleException("Unsupported DATA_SERVICE service_key: "+key);
        }
        return new ServerStepExecution(result,true);
    }

    private UUID uuid(JsonNode inputs,String field){try{return UUID.fromString(inputs.path(field).asText());}catch(Exception ex){throw new BusinessRuleException(field+" must be a UUID");}}
    private ObjectNode endpoint(Endpoint e){return objectMapper.createObjectNode().put("endpoint_id",e.getEndpointId().toString()).put("endpoint_code",e.getEndpointCode()).put("endpoint_name",e.getEndpointName()).put("x_m",e.getXM()).put("y_m",e.getYM()).put("yaw_rad",e.getYawRad());}
}
