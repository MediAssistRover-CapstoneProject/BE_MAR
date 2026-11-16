package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.exception.BusinessRuleException;
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
}
