package com.mediassistrover.backend;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.MapBundleService;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.NavigationSessionService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationAuthorizationTest {
    final MqttWire wire=new MqttWire();
    final UUID robot=UUID.randomUUID(),id=UUID.randomUUID(),mapId=UUID.randomUUID(),producer=UUID.randomUUID();
    final Instant now=Instant.parse("2026-11-03T07:00:00Z");
    final RobotNavigationSessionRepository rows=mock(RobotNavigationSessionRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final RobotMqttSessionRepository links=mock(RobotMqttSessionRepository.class);
    final JobRepository jobs=mock(JobRepository.class);
    final EndpointRepository endpoints=mock(EndpointRepository.class);
    final NavigationSessionService service=new NavigationSessionService(rows,robots,mock(FacilityMapRepository.class),links,
        mock(RobotNavigationProfileRepository.class),jobs,endpoints,mock(MapBundleService.class),wire,Clock.fixed(now,ZoneOffset.UTC),
        mock(jakarta.persistence.EntityManager.class),mock(org.springframework.context.ApplicationEventPublisher.class));

    RobotNavigationSession row(){
        var map=new FacilityMap();map.setMapId(mapId);map.setFrameId("map");
        var r=new RobotNavigationSession();r.setRobotId(robot);r.setSessionId(id);r.setMap(map);r.setProducerSessionId(producer);
        r.setMapAssetHash("a".repeat(64));r.setExpiresAt(now.plusSeconds(900));r.setInitializationMode("SEED");r.setInitializationRevision(2);
        r.setInitialPoseJson(wire.object().put("x_m",1.).put("y_m",2.).put("yaw_rad",.3));
        when(rows.findById(robot)).thenReturn(Optional.of(r));return r;
    }
    RobotMqttSession link(){
        var l=new RobotMqttSession();l.setRobotId(robot);l.setProducerSessionId(producer);
        l.setHelloJson(wire.object().set("navigationSession",wire.object().put("session_id",id.toString())
            .put("map_id",mapId.toString()).put("map_asset_hash","a".repeat(64))));
        when(links.findById(robot)).thenReturn(Optional.of(l));return l;
    }
    @Test void heartbeatAuthorizationNeedsOneReadAndDoesNotRequireANewPose(){
        row();var grant=service.mqttAuthorization(link());
        assertThat(grant.path("state").asText()).isEqualTo("OPEN");
        assertThat(grant.path("initialization").path("revision").asLong()).isEqualTo(2);
        assertThat(grant.path("observedAt").asText()).isEqualTo(now.toString());
        verify(rows,times(1)).findById(robot);verifyNoInteractions(robots,links,jobs);
    }
    @Test void closedExpiredAndForeignProcessesRevokeInsteadOfRetainingOldAuthorization(){
        var r=row();var l=link();r.setClosedAt(now);
        assertThat(service.mqttAuthorization(l).path("state").asText()).isEqualTo("CLOSED");
        r.setClosedAt(null);r.setExpiresAt(now.minusSeconds(1));
        assertThat(service.mqttAuthorization(l).path("state").asText()).isEqualTo("EXPIRED");
        r.setExpiresAt(now.plusSeconds(900));l.setProducerSessionId(UUID.randomUUID());
        assertThat(service.mqttAuthorization(l).path("state").asText()).isEqualTo("CLOSED");
    }
    @Test void ordinaryMappingDoesNotQueryNavigationAuthorization(){
        var l=new RobotMqttSession();l.setHelloJson(wire.object());
        assertThat(service.mqttAuthorization(l)).isNull();verifyNoInteractions(rows);
    }
    @Test void planIsObservationButStillRequiresTheExactActiveStepAndEndpoint(){
        var r=row();link();var deviceRobot=new Robot();deviceRobot.setRobotId(robot);deviceRobot.setRobotCode("R1");deviceRobot.setDeviceTokenVersion(1);
        when(robots.findById(robot)).thenReturn(Optional.of(deviceRobot));
        var step=new JobStep();step.setJobStepId(UUID.randomUUID());step.setStepTypeSnapshot("MOVE");step.setStatus(JobStepStatus.RUNNING);
        var endpoint=new Endpoint();endpoint.setEndpointId(UUID.randomUUID());endpoint.setMap(r.getMap());
        step.setResolvedInputsJson(wire.object().put("endpoint_id",endpoint.getEndpointId().toString()));
        var job=new Job();job.setJobId(UUID.randomUUID());job.setRobot(deviceRobot);job.setMap(r.getMap());job.setSelectedJobStep(step);job.setStatus(JobStatus.RUNNING);
        when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));when(endpoints.findById(endpoint.getEndpointId())).thenReturn(Optional.of(endpoint));
        var request=new NavigationSessionService.PlannedPath(id,mapId,job.getJobId(),step.getJobStepId(),endpoint.getEndpointId(),"a".repeat(64),2,
            List.of(new NavigationSessionService.PlanPoint(0,0),new NavigationSessionService.PlanPoint(1,1)));
        var device=new DevicePrincipal(robot,"R1",1,"test",now.plusSeconds(60));
        assertThat(service.publishPlan(device,request).path("accepted").asBoolean()).isTrue();
        step.setStatus(JobStepStatus.SUCCEEDED);
        assertThatThrownBy(()->service.publishPlan(device,request)).hasMessageContaining("active MOVE step");
    }
}
