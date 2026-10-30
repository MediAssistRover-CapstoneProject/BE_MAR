package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class NavigationZoneProfileTest {
    @Test void activeRulesAndAbsoluteSpeedTravelInTheHashedValidatedProfile() throws Exception {
        var robots=mock(RobotRepository.class);var endpoints=mock(EndpointRepository.class);var zones=mock(ZoneRepository.class);
        var wire=new MqttWire();
        var service=new NavigationProfileService(robots,mock(RobotNavigationProfileRepository.class),mock(RobotMqttSessionRepository.class),
            endpoints,zones,mock(JobRepository.class),mock(RobotCommandOutboxRepository.class),mock(MqttSessionRegistry.class),
            mock(MqttProperties.class),wire,mock(OrchestrationWorkService.class),Clock.systemUTC(),mock(NavigationSessionService.class));
        var robotId=UUID.randomUUID();var mapId=UUID.randomUUID();
        var map=FacilityMap.builder().mapId(mapId).status(MapStatus.ACTIVE).versionNo(1).frameId("map").assetHash("a".repeat(64)).build();
        var robot=Robot.builder().robotId(robotId).currentMap(map).build();when(robots.findById(robotId)).thenReturn(Optional.of(robot));
        when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId)).thenReturn(List.of(Endpoint.builder().endpointId(UUID.randomUUID())
            .map(map).active(true).accessible(true).xM(BigDecimal.ONE).yM(BigDecimal.ZERO).yawRad(BigDecimal.ZERO).build()));
        var polygon=new ObjectMapper().readTree("{\"points\":[[0,0],[1,0],[1,1]]}");
        var restricted=Zone.builder().zoneId(UUID.randomUUID()).map(map).active(true).zoneType(ZoneType.KEEP_OUT).polygonJson(polygon).build();
        var speed=Zone.builder().zoneId(UUID.randomUUID()).map(map).active(true).zoneType(ZoneType.SPEED).polygonJson(polygon).speedLimitMps(new BigDecimal("0.150")).build();
        when(zones.findByMapMapIdOrderByZoneCodeAsc(mapId)).thenReturn(List.of(restricted,speed));
        var first=service.snapshot(robot);
        assertThat(first.profile().path("zones").size()).isEqualTo(2);
        assertThat(first.profile().at("/zones/1/speed_limit_mps").asDouble()).isEqualTo(.15);
        var message=wire.object().put("messageType","NAV_PROFILE").put("robotId",robotId.toString())
            .put("transportSessionId",UUID.randomUUID().toString()).put("profileVersion",first.profileVersion()).put("profileHash",first.profileHash());
        message.set("profile",first.profile());
        assertThatCode(()->new MqttContractValidator().response(message)).doesNotThrowAnyException();
        var status=service.zoneStatus(robotId);
        assertThat(status.path("restrictedZoneCount").asInt()).isEqualTo(1);assertThat(status.path("speedZoneCount").asInt()).isEqualTo(1);
        speed.setSpeedLimitMps(new BigDecimal("0.220"));assertThat(service.snapshot(robot).profileHash()).isNotEqualTo(first.profileHash());
        restricted.setActive(false);assertThat(service.snapshot(robot).profile().path("zones").size()).isEqualTo(1);
        assertThat(service.zoneStatus(robotId).path("profileAcknowledged").asBoolean()).isFalse();
    }
}
