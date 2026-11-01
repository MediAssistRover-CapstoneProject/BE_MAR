package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mapping.MappingControlService;
import com.mediassistrover.backend.service.NavigationProfileService;
import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttHeartbeatRetryTest {
    final UUID robot=UUID.randomUUID();
    final MqttWire wire=new MqttWire();
    final MqttProperties properties=mock(MqttProperties.class);
    final MqttSessionRegistry sessions=mock(MqttSessionRegistry.class);
    final MqttInboxStore store=mock(MqttInboxStore.class);
    final MqttDurablePublisher publisher=mock(MqttDurablePublisher.class);
    final MqttInboundRouter router=new MqttInboundRouter(properties,wire,mock(MqttContractValidator.class),sessions,
            store,mock(MqttInboxProcessor.class),publisher,mock(NavigationProfileService.class),mock(MappingControlService.class));
    void receive() {
        when(properties.manages(robot)).thenReturn(true);
        var message=wire.object().put("robotId",robot.toString()).put("messageType","HEARTBEAT")
                .put("messageId",UUID.randomUUID().toString()).put("sequenceNo",42);
        router.route(MqttTopics.topic(robot,"heartbeat"),wire.bytes(message),0,false);
    }
    @Test void transientLockConflictRetriesTheSameHeartbeatWithoutAnyPhysicalPublication() {
        doThrow(new CannotAcquireLockException("deadlock")).doNothing().when(sessions).heartbeat(any());
        receive();
        var envelopes=org.mockito.ArgumentCaptor.forClass(MqttWire.Envelope.class);
        verify(sessions,times(2)).heartbeat(envelopes.capture());
        assertThat(envelopes.getAllValues().get(0)).isSameAs(envelopes.getAllValues().get(1));
        verifyNoInteractions(store,publisher);
    }
    @Test void persistentConflictIsBoundedAndNotReportedAsAccepted() {
        doThrow(new CannotAcquireLockException("deadlock")).when(sessions).heartbeat(any());
        assertThatThrownBy(this::receive).isInstanceOf(CannotAcquireLockException.class);
        verify(sessions,times(3)).heartbeat(any());verifyNoInteractions(store,publisher);
    }
    @Test void businessRuleFailureNeverRetriesOrBypassesValidation() {
        doThrow(new BusinessRuleException("stale session")).when(sessions).heartbeat(any());
        assertThatThrownBy(this::receive).isInstanceOf(BusinessRuleException.class);
        verify(sessions).heartbeat(any());verifyNoInteractions(store,publisher);
    }
}
