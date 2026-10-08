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
                case MOVE -> {
                    optionalEnum(step,params,"mode",Set.of("NORMAL","ESCORT","PATROL"));
                    optionalNumberRange(step,params,"max_speed_mps",0.000001,0.3);
                }
                case DATA_SERVICE -> {
                    String key=text(params,"service_key");
                    if(key==null&&"QUERY".equals(step.getStepCode()))key="QUERY_HOSPITAL_INFORMATION";
                    if(key==null||!DataServiceStepHandler.SUPPORTED_SERVICE_KEYS.contains(key))
                        throw invalid(step,"unsupported DATA_SERVICE service_key: "+key);
                    rejectDangerousServiceFields(step,params);rejectDangerousServiceFields(step,mapping);
                    optionalEnum(step,params,"operation",Set.of("QUERY","CREATE","UPDATE"));
                }
                case INTERACT -> {requireText(step,params,"prompt_key");optionalEnum(step,params,"action",Set.of("SPEAK","DISPLAY","CAPTURE_INPUT","CONFIRM"));}
                case AUTHENTICATE -> {optionalEnum(step,params,"method",Set.of("QR_CODE","STAFF_CONFIRMATION"));requireText(step,params,"purpose");}
                case CONTROL_HARDWARE -> {
                    String action=requireText(step,params,"action");
                    if(!Set.of("LOCK_COMPARTMENT","UNLOCK_COMPARTMENT","LOCK","UNLOCK").contains(action))throw invalid(step,"unsupported hardware action: "+action);
                    if(step.getMaxAttempts()!=1)throw invalid(step,"hardware commands cannot be retried automatically");
                }
                case HANDLE_PAYLOAD -> optionalEnum(step,params,"action",Set.of("LOAD","UNLOAD","VERIFY"));
                case NOTIFY -> {
                    requireText(step,params,"notification_type");
                    if(!params.path("recipients").isArray())throw invalid(step,"recipients must be an array");
                    optionalEnum(step,params,"severity",Set.of("INFO","WARNING","CRITICAL"));
                    if(params.has("require_ack")&&!params.path("require_ack").isBoolean())throw invalid(step,"require_ack must be boolean");
                }
                case EXECUTE_SENSING -> {
                    String sensing=requireText(step,params,"sensing_type");
                    if(!Set.of("ROBOT_HEALTH","SIDE_CLEARANCE","CROWD_DENSITY","RESTRICTED_AREA_INTRUSION","RESTRICTED_ZONE").contains(sensing))throw invalid(step,"unsupported sensing_type: "+sensing);
                    optionalNumberRange(step,params,"window_sec",0,30);
                }
                case WAIT -> {
                    String type=requireText(step,params,"wait_type");
                    if("DURATION".equals(type)){
                        if(!params.path("duration_sec").isNumber()||params.path("duration_sec").asDouble()<0||params.path("duration_sec").asDouble()>3600)throw invalid(step,"DURATION requires duration_sec between 0 and 3600");
                        if(step.getTimeoutSec()<=params.path("duration_sec").asDouble())throw invalid(step,"timeoutSec must be greater than duration_sec");
                    }
                    if("EVENT".equals(type))requireText(step,params,"event_type");
                    if(!Set.of("DURATION","EVENT").contains(type))throw invalid(step,"unsupported wait_type: "+type);
                }
                case CHECK_CONDITION -> {
                    JsonNode expression=params.path("expression");
                    try{jsonValidator.condition(expression);}catch(BusinessRuleException ex){throw invalid(step,ex.getMessage());}
                }
                case SET_CONTEXT -> {
                    if(!params.path("operations").isArray()||params.path("operations").isEmpty())throw invalid(step,"operations must be a non-empty array");
                    if(params.path("operations").size()>128)throw invalid(step,"operations exceed 128 entries");
                    for(JsonNode op:params.path("operations")){
                        if(!op.isObject()||!op.path("path").isTextual()||!op.path("path").asText().matches("^/vars/[A-Za-z][A-Za-z0-9_]*$"))throw invalid(step,"operation path must target /vars/<identifier>");
                        if(op.has("operation")&&!Set.of("SET","INCREMENT").contains(op.path("operation").asText()))throw invalid(step,"unsupported context operation");
                        if(!op.has("value"))throw invalid(step,"context operation value is required");
                    }
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
    private void optionalEnum(Step step,JsonNode node,String field,Set<String> values){if(node!=null&&node.has(field)&&(!node.path(field).isTextual()||!values.contains(node.path(field).asText())))throw invalid(step,field+" has an unsupported value");}
    private void optionalNumberRange(Step step,JsonNode node,String field,double min,double max){if(node!=null&&node.has(field)&&(!node.path(field).isNumber()||node.path(field).asDouble()<min||node.path(field).asDouble()>max))throw invalid(step,field+" is outside the supported range");}
    private void rejectDangerousServiceFields(Step step,JsonNode node){if(node==null)return;if(node.isObject()){for(String field:java.util.List.of("url","sql","script","code"))if(node.has(field))throw invalid(step,"DATA_SERVICE cannot configure "+field);node.forEach(child->rejectDangerousServiceFields(step,child));}else if(node.isArray())node.forEach(child->rejectDangerousServiceFields(step,child));}
    private String text(JsonNode node,String field){return node!=null&&node.path(field).isTextual()&&!node.path(field).asText().isBlank()?node.path(field).asText():null;}
    private String unescape(String value){return value.replace("~1","/").replace("~0","~");}
    private BusinessRuleException invalid(Step step,String reason){return new BusinessRuleException("Invalid contract for step "+step.getStepCode()+": "+reason);}
}
