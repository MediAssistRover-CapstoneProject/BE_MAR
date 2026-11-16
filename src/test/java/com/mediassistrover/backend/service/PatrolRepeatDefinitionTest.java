package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mqtt.MqttProperties;
import com.mediassistrover.backend.mqtt.MqttStepDispatchService;
import com.mediassistrover.backend.validation.StepExecutionPolicy;
import com.mediassistrover.backend.validation.StepInputContractValidator;
import com.mediassistrover.backend.validation.WorkflowJsonValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolRepeatDefinitionTest {
    final PatrolWorkflowService service=mock(PatrolWorkflowService.class,CALLS_REAL_METHODS);
    final UUID mapId=UUID.randomUUID();
    PatrolRepeatDefinitionTest(){ReflectionTestUtils.setField(service,"json",new ObjectMapper());}
    PatrolWorkflowService.Create request(Integer repeat){
        return new PatrolWorkflowService.Create("Route",mapId,List.of(new PatrolWorkflowService.Checkpoint(UUID.randomUUID(),UUID.randomUUID(),"CROWD_DENSITY")),.25,60,12,false,repeat);
    }
    @Test void defaultAndFiniteRepeatAreExplicitWorkflowDefaults(){
        assertEquals(1,service.graph(request(null),"WF_TEST").workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        var graph=service.graph(request(3),"WF_TEST");
        assertEquals(3,graph.workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        assertEquals(mapId.toString(),graph.workflow().defaultInputsJson().path("map_id").asText());
    }
    @Test void continuousRepeatsRetainABoundedSingleRoundGraph(){
        var graph=service.graph(request(0),"WF_TEST");
        assertEquals(0,graph.workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        assertTrue(graph.graph().steps().stream().allMatch(step->step.maxVisits()==1));
        assertTrue(graph.workflow().maxDurationSec()>0);
    }
    @Test void unboundedConfigurationValuesAreRejected(){
        assertThrows(BusinessRuleException.class,()->service.graph(request(-1),"WF_TEST"));
        assertThrows(BusinessRuleException.class,()->service.graph(request(1001),"WF_TEST"));
    }
    @Test void fallOnlyCheckpointPassesTheIntegratedPiDispatchContract(){
        var request=new PatrolWorkflowService.Create("Fall route",mapId,
                List.of(new PatrolWorkflowService.Checkpoint(UUID.randomUUID(),null,"PATROL_FALL")),.25,60,3,true,1);
        var sensing=service.graph(request,"WF_FALL").graph().steps().stream()
                .filter(step->step.stepType()==StepType.EXECUTE_SENSING).findFirst().orElseThrow();
        var step=Step.builder().workflow(Workflow.builder().schemaVersion(3).build())
                .stepCode(sensing.stepCode()).stepType(sensing.stepType())
                .timeoutSec(sensing.timeoutSec())
                .paramsJson(sensing.paramsJson()).inputMappingJson(sensing.inputMappingJson()).build();
        var inputs=new StepInputContractValidator(new WorkflowJsonValidator());
        assertDoesNotThrow(()->inputs.validatePiParams(step,step.getParamsJson()));
        var config=new MqttProperties();config.setEnabled(true);config.setDispatchEnabled(true);
        config.setRobotIds(Set.of(UUID.randomUUID()));config.setEnabledStepTypes(Set.of("EXECUTE_SENSING"));
        var dispatch=mock(MqttStepDispatchService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(dispatch,"properties",config);
        ReflectionTestUtils.setField(dispatch,"executionPolicy",new StepExecutionPolicy(config));
        assertDoesNotThrow(()->dispatch.requireActionSupported(step));
        var withoutOptIn=step.getParamsJson().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)withoutOptIn).put("fall_detection",false);
        assertThrows(BusinessRuleException.class,()->inputs.validatePiParams(step,withoutOptIn));
    }
}
