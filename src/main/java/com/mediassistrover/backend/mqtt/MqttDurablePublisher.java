package com.mediassistrover.backend.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.mediassistrover.backend.validation.MqttContractValidator;
import java.util.UUID;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor @Slf4j
public class MqttDurablePublisher {
    public record CommandQueued(UUID commandId,MqttPublicationPlans.Publication initial) {
        public CommandQueued(UUID commandId){this(commandId,null);}
    }
    private final MqttPublicationPlans plans;
    private final PahoMqttTransport transport;
    private final MqttWire wire;
    private final MqttContractValidator contracts;
    private final ExecutorService receipts=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
            task->{var thread=new Thread(task,"mar-mqtt-receipt");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    public void command(UUID id) { afterCommit(()->publishCommand(id)); }
    @org.springframework.transaction.event.TransactionalEventListener(phase=org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void queued(CommandQueued event){record(()->{
        if(event.initial()==null)publishCommand(event.commandId());else publishPreparedCommand(event.initial());
    });}
    private void publishCommand(UUID id) {
        if (!transport.connected()) return;
        var plan=plans.command(id);if (plan==null) return;publishPreparedCommand(plan);
    }
    private void publishPreparedCommand(MqttPublicationPlans.Publication plan) {
        if(!transport.connected() || !java.time.Instant.parse(plan.payload().path("expiresAt").asText()).isAfter(java.time.Instant.now()))return;
        contracts.command(plan.payload());
        UUID id=plan.id();
        transport.publish(MqttTopics.topic(plan.robotId(),"commands"),wire.bytes(plan.payload()),1)
                .thenRun(()->record(()->plans.commandPublished(id,plan.sessionId()))).exceptionally(error->{log.warn("MQTT command {} not confirmed published; durable outbox remains",id);return null;});
    }
    public void acknowledgement(UUID id,boolean force) { afterCommit(()->publishAcknowledgement(id,force)); }
    public void rejection(UUID robot,com.fasterxml.jackson.databind.JsonNode response) {
        contracts.response(response);var snapshot=response.deepCopy();
        afterCommit(()->{if(transport.connected()) transport.publish(MqttTopics.topic(robot,"event-acks"),wire.bytes(snapshot),1);});
    }
    private void publishAcknowledgement(UUID id,boolean force) {
        if (!transport.connected()) return;
        var plan=plans.acknowledgement(id,force);if (plan==null) return;contracts.response(plan.payload());
        committedAcknowledgement(plan);
    }
    public void committedAcknowledgement(MqttPublicationPlans.Publication plan) {
        afterCommit(()->publishAcknowledgement(plan));
    }
    private void publishAcknowledgement(MqttPublicationPlans.Publication plan) {
        if(!transport.connected())return;
        contracts.response(plan.payload());
        transport.publish(MqttTopics.topic(plan.robotId(),"event-acks"),wire.bytes(plan.payload()),1)
                .thenRun(()->record(()->{
                    if("EVENT_ACK".equals(plan.payload().path("messageType").asText()))plans.eventAcknowledged(plan.id(),plan.sessionId());
                    else plans.acknowledged(plan.id(),plan.sessionId());
                })).exceptionally(error->{log.warn("MQTT ACK for {} will be retried",plan.id());return null;});
    }
    // Paho completes publish futures on its callback thread. Never run JDBC there:
    // a slow receipt write would also delay inbound ACKs, commands and keepalives.
    private void record(Runnable action) {
        try {receipts.execute(()->{try{action.run();}catch(RuntimeException ex){log.warn("MQTT receipt persistence will retry through durable work",ex);}});}
        catch(RejectedExecutionException ex){log.warn("MQTT receipt queue full; durable work will retry");}
    }
    @jakarta.annotation.PreDestroy public void destroy(){receipts.shutdownNow();}
    private void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {action.run();}
            });
        } else action.run();
    }
}
