package com.mediassistrover.backend;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NavigationRouteTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void invalidHttpInputIs400AndDoesNotCreateWork() throws Exception {
        var routes=mock(NavigationRouteService.class);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new com.mediassistrover.backend.controller.request.NavigationRouteController(routes))
            .setControllerAdvice(new com.mediassistrover.backend.exception.GlobalExceptionHandler(java.time.Clock.systemUTC())).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/navigation-routes")
            .contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/navigation-routes")
            .header("Idempotency-Key","empty-route").contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(routes);
    }
    @ParameterizedTest @ValueSource(ints={1,4,20}) void graphUsesRequestedStopCountAndOnlySuccessAdvances(int n) {
        var d=new NavigationRouteWorkflowFactory(json).build(n,.6);
        assertThat(d.graph().steps()).hasSize(n);assertThat(d.workflow().maxTotalVisits()).isEqualTo(n);
        for(int i=0;i<n;i++) {
            var step=d.graph().steps().get(i);String code=step.stepCode();
            assertThat(step.paramsJson().path("max_speed_mps").asDouble()).isEqualTo(.6);
            assertThat(step.inputMappingJson().path("endpoint_id").path("$ref").asText())
                .isEqualTo(i==n-1?"/inputs/endpoint_id":"/inputs/route_waypoint_ids/"+i);
            var success=d.graph().transitions().stream().filter(t->t.fromStepCode().equals(code)&&t.outcome()==TransitionOutcome.SUCCESS).findFirst().orElseThrow();
            if(i<n-1)assertThat(success.toStepCode()).isEqualTo(d.graph().steps().get(i+1).stepCode());
            else assertThat(success.terminalStatus()).isEqualTo(TerminalStatus.SUCCEEDED);
            assertThat(d.graph().transitions().stream().filter(t->t.fromStepCode().equals(code)&&t.outcome()!=TransitionOutcome.SUCCESS))
                .allSatisfy(t->assertThat(t.toStepCode()).isNull());
        }
    }
    static class Fixture {
        ObjectMapper json=new ObjectMapper();UUID robot=UUID.randomUUID(),map=UUID.randomUUID(),session=UUID.randomUUID(),wf=UUID.randomUUID(),requestId=UUID.randomUUID();
        List<UUID> ids=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        WorkflowService workflows=mock(WorkflowService.class,RETURNS_DEEP_STUBS);
        ServiceRequestService requests=mock(ServiceRequestService.class);ServiceRequestRepository rows=mock(ServiceRequestRepository.class);
        EndpointRepository endpoints=mock(EndpointRepository.class);RobotRepository robots=mock(RobotRepository.class);
        NavigationSessionService sessions=mock(NavigationSessionService.class);
        Account account=Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build();
        AccountPrincipal actor=AccountPrincipal.fromAccount(account);
        ServiceRequestResponse response=mock(ServiceRequestResponse.class);
        NavigationResponse navigation=mock(NavigationResponse.class);
        NavigationRouteService service=new NavigationRouteService(new NavigationRouteWorkflowFactory(json),workflows,requests,rows,endpoints,robots,sessions,json,mock(com.mediassistrover.backend.mqtt.MqttRolloutService.class));
        Fixture() {
            when(robots.findByIdForUpdate(robot)).thenReturn(Optional.of(mock(Robot.class)));
            when(workflows.createGraph(any(),eq(actor.accountId()),isNull()).workflow().workflowId()).thenReturn(wf);
            for(UUID id:ids)when(endpoints.findById(id)).thenReturn(Optional.of(Endpoint.builder().endpointId(id)
                .map(FacilityMap.builder().mapId(map).build()).active(true).accessible(true).build()));
            when(navigation.request()).thenReturn(response);when(response.serviceRequestId()).thenReturn(requestId);
            when(response.status()).thenReturn(ServiceRequestStatus.DRAFT);
            when(requests.createNavigation(any(),eq("route-key"),eq(actor),isNull())).thenReturn(navigation);
            when(requests.submit(requestId,actor,null)).thenReturn(response);
            clearInvocations(workflows);
        }
        NavigationRouteService.Create body(Boolean submit){return new NavigationRouteService.Create(robot,map,session,ids,.6,submit,null);}
    }
    @Test void oneCallCreatesPublishesAndSubmitsAWholeRouteInOrder() {
        var f=new Fixture();var r=f.service.create(f.body(true),"route-key",f.actor,null);
        assertThat(r.workflowId()).isEqualTo(f.wf);assertThat(r.endpointIds()).isEqualTo(f.ids);
        verify(f.workflows).validate(f.wf,f.actor.accountId(),null);verify(f.workflows).publish(f.wf,f.actor.accountId(),null);
        verify(f.requests).submit(f.requestId,f.actor,null);
        var input=org.mockito.ArgumentCaptor.forClass(com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.NavigationCreateRequest.class);
        verify(f.requests).createNavigation(input.capture(),eq("route-key"),eq(f.actor),isNull());
        assertThat(input.getValue().destinationEndpointId()).isEqualTo(f.ids.getLast());
        assertThat(input.getValue().data().path("route_waypoint_ids")).hasSize(2);
        assertThat(input.getValue().robotId()).isEqualTo(f.robot);assertThat(input.getValue().navigationSessionId()).isEqualTo(f.session);
    }
    @Test void draftDoesNotDispatchAndReplayingCompletedRouteDoesNotCreateAnotherWorkflow() {
        var f=new Fixture();f.service.create(f.body(false),"route-key",f.actor,null);verify(f.requests,never()).submit(any(),any(),any());
        clearInvocations(f.workflows,f.sessions,f.endpoints);
        var data=f.json.createObjectNode().put("workflow_id",f.wf.toString());data.set("route_endpoint_ids",f.json.valueToTree(f.ids));
        when(f.rows.findByIdempotencyKey("route-key")).thenReturn(Optional.of(ServiceRequest.builder().requestedByAccount(f.account)
            .requestType(ServiceRequestType.NAVIGATION).requestDataJson(data).build()));
        when(f.response.status()).thenReturn(ServiceRequestStatus.COMPLETED);
        assertThat(f.service.create(f.body(false),"route-key",f.actor,null).workflowId()).isEqualTo(f.wf);
        verify(f.workflows,never()).createGraph(any(),any(),any());verifyNoInteractions(f.sessions,f.endpoints);
    }
    @Test void foreignMapEndpointIsRejectedBeforePublishingWorkflow() {
        var f=new Fixture();when(f.endpoints.findById(f.ids.getFirst())).thenReturn(Optional.of(Endpoint.builder()
            .map(FacilityMap.builder().mapId(UUID.randomUUID()).build()).active(true).accessible(true).build()));
        assertThatThrownBy(()->f.service.create(f.body(true),"route-key",f.actor,null)).hasMessageContaining("selected map");
        verify(f.workflows,never()).createGraph(any(),any(),any());verifyNoInteractions(f.requests);
    }
}
