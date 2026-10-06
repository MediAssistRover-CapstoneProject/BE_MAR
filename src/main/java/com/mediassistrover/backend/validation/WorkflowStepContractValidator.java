package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.service.impl.DataServiceStepHandler;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Publish-time semantic checks shared by seeded and admin-authored workflow graphs. */
@Component
public class WorkflowStepContractValidator {
    private final WorkflowJsonValidator jsonValidator;

    public WorkflowStepContractValidator(WorkflowJsonValidator jsonValidator){this.jsonValidator=jsonValidator;}

    public void validate(Workflow workflow,java.util.List<Step> steps){
        Set<String> stepCodes=new HashSet<>();steps.forEach(s->stepCodes.add(s.getStepCode().toUpperCase(Locale.ROOT)));
        Set<String> declaredInputs=declaredInputs(workflow);
        for(Step step:steps){
            JsonNode params=step.getParamsJson();JsonNode mapping=step.getInputMappingJson();
            switch(step.getStepType()){
                case DATA_SERVICE -> {
                    String key=text(params,"service_key");
                    if(key==null&&"QUERY".equals(step.getStepCode()))key="QUERY_HOSPITAL_INFORMATION";
                    if(key==null||!DataServiceStepHandler.SUPPORTED_SERVICE_KEYS.contains(key))
                        throw invalid(step,"unsupported DATA_SERVICE service_key: "+key);
                }
                case INTERACT -> requireText(step,params,"prompt_key");
                case NOTIFY -> requireText(step,params,"notification_type");
                case EXECUTE_SENSING -> requireText(step,params,"sensing_type");
                case WAIT -> {
                    String type=requireText(step,params,"wait_type");
                    if("DURATION".equals(type)&&params.path("duration_sec").asInt(0)<1)throw invalid(step,"DURATION requires duration_sec >= 1");
                    if("EVENT".equals(type))requireText(step,params,"event_type");
                    if(!Set.of("DURATION","EVENT").contains(type))throw invalid(step,"unsupported wait_type: "+type);
                }
                case CHECK_CONDITION -> {
                    JsonNode expression=params.path("expression");
                    try{jsonValidator.condition(expression);}catch(BusinessRuleException ex){throw invalid(step,ex.getMessage());}
                }
                case SET_CONTEXT -> {
                    if(!params.path("operations").isArray()||params.path("operations").isEmpty())throw invalid(step,"operations must be a non-empty array");
                }
                case CONTROL_HARDWARE -> {
                    String action=requireText(step,params,"action");
                    if(!Set.of("LOCK_COMPARTMENT","UNLOCK_COMPARTMENT").contains(action))throw invalid(step,"unsupported hardware action: "+action);
                }
                default -> { }
            }
            validateReferences(step,params,stepCodes,declaredInputs);
            validateReferences(step,mapping,stepCodes,declaredInputs);
        }
    }

    private Set<String> declaredInputs(Workflow workflow){
        Set<String> names=new HashSet<>();JsonNode properties=workflow.getInputSchemaJson().path("properties");
        if(properties.isObject())properties.fieldNames().forEachRemaining(names::add);
        JsonNode defaults=workflow.getDefaultInputsJson();if(defaults!=null&&defaults.isObject())defaults.fieldNames().forEachRemaining(names::add);
        return names;
    }
    private void validateReferences(Step step,JsonNode node,Set<String> stepCodes,Set<String> inputs){
        if(node==null)return;
        if(node.isObject()&&node.size()==1&&node.has("$ref")){
            String ref=node.path("$ref").asText();String[] parts=ref.split("/",4);
            if(parts.length>=3&&"inputs".equals(parts[1])&&!inputs.contains(unescape(parts[2])))throw invalid(step,"undeclared workflow input reference: "+ref);
            if(parts.length>=3&&"outputs".equals(parts[1])&&!stepCodes.contains(unescape(parts[2]).toUpperCase(Locale.ROOT)))throw invalid(step,"unknown step output reference: "+ref);
        }
        if(node.isContainerNode()){Iterator<JsonNode> it=node.elements();while(it.hasNext())validateReferences(step,it.next(),stepCodes,inputs);}
    }
    private String requireText(Step step,JsonNode node,String field){String value=text(node,field);if(value==null)throw invalid(step,field+" is required");return value;}
    private String text(JsonNode node,String field){return node!=null&&node.path(field).isTextual()&&!node.path(field).asText().isBlank()?node.path(field).asText():null;}
    private String unescape(String value){return value.replace("~1","/").replace("~0","~");}
    private BusinessRuleException invalid(Step step,String reason){return new BusinessRuleException("Invalid contract for step "+step.getStepCode()+": "+reason);}
}
