package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttCommittedAckTest {
    final MqttPublicationPlans plans=mock(MqttPublicationPlans.class);
    final PahoMqttTransport transport=mock(PahoMqttTransport.class);
    final MqttWire wire=new MqttWire();
    final MqttDurablePublisher publisher=new MqttDurablePublisher(plans,transport,wire,mock(MqttContractValidator.class));
    MqttPublicationPlans.Publication plan(){return new MqttPublicationPlans.Publication(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),wire.object());}
    @Test void committedAckDoesNotRereadTheDatabaseOrBlockTheBrokerCallbackOnJdbc() throws Exception {
        var p=plan();var broker=new CompletableFuture<Void>();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(transport.connected()).thenReturn(true);when(transport.publish(anyString(),any(),eq(1))).thenReturn(broker);
        doAnswer(call->{entered.countDown();release.await(5,TimeUnit.SECONDS);return null;}).when(plans).acknowledged(p.id(),p.sessionId());
        var callback=Executors.newSingleThreadExecutor();
        try {
            publisher.committedAcknowledgement(p);
            verify(plans,never()).acknowledgement(any(),anyBoolean());
            callback.submit(()->broker.complete(null)).get(1,TimeUnit.SECONDS);
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
        } finally {release.countDown();publisher.destroy();callback.shutdownNow();}
    }
    @Test void outerTransactionMustCommitBeforeTheAckCanAuthorizePhysicalExecution() {
        var p=plan();when(transport.connected()).thenReturn(true);
        when(transport.publish(anyString(),any(),eq(1))).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publisher.committedAcknowledgement(p);verifyNoInteractions(transport);
            for(var synchronization:TransactionSynchronizationManager.getSynchronizations())synchronization.afterCommit();
            verify(transport).publish(anyString(),any(),eq(1));
        } finally {TransactionSynchronizationManager.clear();publisher.destroy();}
    }
    @Test void preparedInitialCommandUsesCommittedSnapshotWithoutRepeatingCloudQueries() throws Exception {
        var p=plan();((com.fasterxml.jackson.databind.node.ObjectNode)p.payload()).put("expiresAt",java.time.Instant.now().plusSeconds(30).toString());
        when(transport.connected()).thenReturn(true);when(transport.publish(anyString(),any(),eq(1))).thenReturn(new CompletableFuture<>());
        try {
            publisher.queued(new MqttDurablePublisher.CommandQueued(p.id(),p));
            verify(transport,timeout(1000)).publish(anyString(),any(),eq(1));
            verify(plans,never()).command(any());
        } finally {publisher.destroy();}
    }

    @Test void rollbackCannotPublishAnAck() {
        TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        try {publisher.committedAcknowledgement(plan());}
        finally {TransactionSynchronizationManager.clear();publisher.destroy();}
        verifyNoInteractions(transport,plans);
    }
}
