package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationLivePoseTest {
    final Instant now=Instant.parse("2026-11-02T15:00:00Z");
    final UUID robot=UUID.randomUUID(),transport=UUID.randomUUID(),producer=UUID.randomUUID(),session=UUID.randomUUID(),map=UUID.randomUUID();
    final MqttConnectionState connection=new MqttConnectionState();
    final ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
    final NavigationLivePoseService service=new NavigationLivePoseService(Clock.fixed(now,ZoneOffset.UTC),connection,events);
    final MqttWire wire=new MqttWire();
    NavigationLivePoseService.Authorization authorization(Instant expiry){return new NavigationLivePoseService.Authorization(robot,transport,producer,connection.connectionId(),session,map,"a".repeat(64),1,expiry);}
    com.fasterxml.jackson.databind.node.ObjectNode pose(){
        var value=wire.object().put("messageType","NAVIGATION_POSE").put("robotId",robot.toString())
            .put("transportSessionId",transport.toString()).put("producerSessionId",producer.toString())
            .put("navigationSessionId",session.toString()).put("mapId",map.toString()).put("mapAssetHash","a".repeat(64))
            .put("initializationRevision",1).put("sequenceNo",1).put("capturedAt",now.toString());
        value.putObject("pose").put("xM",.1).put("yM",.2).put("yawRad",.3);return value;
    }
    @Test void onlyTheCurrentCommittedScopeCanProduceObservationsWithoutAnyDatabaseDependency(){
        connection.connectedNow();service.accept(pose());verifyNoInteractions(events);
        service.authorized(authorization(now.plusSeconds(15)));new MqttContractValidator().live(pose());
        service.accept(pose());service.accept(pose());verify(events,times(1)).publishEvent(any(NavigationLivePoseService.Pose.class));
        service.authorized(authorization(now.plusSeconds(15)));service.accept(pose()); // heartbeat refresh keeps sequence
        verify(events,times(1)).publishEvent(any(NavigationLivePoseService.Pose.class));
    }
    @Test void oldRevisionStalePoseExpiredHeartbeatAndReconnectedBrokerCannotReviveObservations(){
        connection.connectedNow();service.authorized(authorization(now.plusSeconds(15)));
        service.accept(pose().put("initializationRevision",2));service.accept(pose().put("capturedAt",now.minusSeconds(3).toString()));
        service.authorized(authorization(now.minusSeconds(1)));service.accept(pose());
        service.authorized(authorization(now.plusSeconds(15)));connection.connectedNow();service.accept(pose());verifyNoInteractions(events);
    }
    @Test void closingTheSessionDropsObservationsAndInvalidNumbersFailTheWireContract(){
        connection.connectedNow();service.authorized(authorization(now.plusSeconds(15)));service.closed(new NavigationLivePoseService.Closed(robot));
        service.accept(pose());verifyNoInteractions(events);
        var invalid=pose();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.path("pose")).put("yawRad",7.);
        assertThatThrownBy(()->new MqttContractValidator().live(invalid)).isInstanceOf(RuntimeException.class);
    }
}
