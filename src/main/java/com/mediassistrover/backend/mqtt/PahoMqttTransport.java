package com.mediassistrover.backend.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.*;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.util.concurrent.*;

@Component @RequiredArgsConstructor @Slf4j
public class PahoMqttTransport implements SmartLifecycle {
    private final com.mediassistrover.backend.mqtt.MqttProperties properties;
    private final MqttConnectionState state;
    private final ObjectProvider<MqttInboundRouter> router;
    private volatile MqttAsyncClient client;
    private volatile boolean running;
    private final ThreadPoolExecutor inbound=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),
            task->{Thread thread=new Thread(task,"mar-mqtt-inbound");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());

    public boolean connected() { return client!=null && client.isConnected() && state.connected(); }
    public static SSLSocketFactory tls(com.mediassistrover.backend.mqtt.MqttProperties properties) {
        try {
            TrustManager[] trust=null;KeyManager[] keys=null;
            if (properties.getTrustStore()!=null) {
                KeyStore store=KeyStore.getInstance("PKCS12");try(var input=java.nio.file.Files.newInputStream(properties.getTrustStore())) {store.load(input,chars(properties.getTrustStorePassword()));}
                var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);trust=factory.getTrustManagers();
            }
            if (properties.getKeyStore()!=null) {
                KeyStore store=KeyStore.getInstance("PKCS12");try(var input=java.nio.file.Files.newInputStream(properties.getKeyStore())) {store.load(input,chars(properties.getKeyStorePassword()));}
                var factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(store,chars(properties.getKeyStorePassword()));keys=factory.getKeyManagers();
            }
            SSLContext context=SSLContext.getInstance("TLS");context.init(keys,trust,null);return context.getSocketFactory();
        } catch (Exception ex) {throw new IllegalArgumentException("Cannot initialize MQTT TLS stores",ex);}
    }
    private static char[] chars(String value) {return value==null?null:value.toCharArray();}
    @Override public synchronized void start() {
        if (running || !properties.isEnabled()) return;
        properties.validateConnection();
        try {
            client=new MqttAsyncClient(properties.getBrokerUri(),properties.getClientId(),new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                public void disconnected(MqttDisconnectResponse response) {state.disconnected();}
                public void mqttErrorOccurred(MqttException error) {log.warn("MQTT client error: reason {}",error.getReasonCode());}
                public void deliveryComplete(IMqttToken token) { }
                public void authPacketArrived(int reason,MqttProperties packet) { }
                public void connectComplete(boolean reconnect,String uri) {
                    try {
                        client.subscribe(new MqttSubscription[]{new MqttSubscription("mar/robots/+/events",1),new MqttSubscription("mar/robots/+/heartbeat",0),
                                new MqttSubscription("mar/robots/+/presence",1),new MqttSubscription("mar/robots/+/map-preview",0)},null,new MqttActionListener() {
                            public void onSuccess(IMqttToken token) {
                                int[] reasons=token.getReasonCodes();
                                if (reasons==null || reasons.length!=4 || java.util.Arrays.stream(reasons).anyMatch(code->code>=128)) {
                                    state.disconnected();log.warn("MQTT SUBACK denied a required topic; session remains unavailable");
                                } else state.connectedNow();
                            }
                            public void onFailure(IMqttToken token,Throwable error) {state.disconnected();log.warn("MQTT subscriptions denied; session remains unavailable");}
                        },new MqttProperties());
                    } catch (MqttException ex) {state.disconnected();log.warn("MQTT subscriptions failed: reason {}",ex.getReasonCode());}
                }
                public void messageArrived(String topic,MqttMessage message) {
                    byte[] bytes=message.getPayload().clone();int qos=message.getQos();boolean retained=message.isRetained();
                    try {inbound.execute(()->router.getObject().receive(topic,bytes,qos,retained));}
                    catch (RejectedExecutionException ex) {log.warn("MQTT inbound queue full; durable event receives no application ACK");}
                }
            });
            MqttConnectionOptions options=new MqttConnectionOptions();options.setUserName(properties.getUsername());options.setPassword(properties.getPassword().getBytes(StandardCharsets.UTF_8));
            options.setCleanStart(true);options.setSessionExpiryInterval(0L);options.setKeepAliveInterval(10);options.setConnectionTimeout(10);
            options.setAutomaticReconnect(true);options.setAutomaticReconnectDelay(1000,5000);options.setMaximumPacketSize(1_048_576L);
            options.setSocketFactory(tls(properties));options.setHttpsHostnameVerificationEnabled(true);
            running=true;
            try {
                client.connect(options,null,new MqttActionListener() {
                    public void onSuccess(IMqttToken token) { }
                    public void onFailure(IMqttToken token,Throwable error) {state.disconnected();log.warn("MQTT initial connection failed; scheduler will retry");}
                });
            } catch (MqttException ex) {
                state.disconnected();
                log.warn("MQTT initial connection could not be started; REST backend remains available and scheduler will retry: reason {}",ex.getReasonCode());
            }
        } catch (MqttException ex) {running=false;throw new IllegalStateException("Cannot initialize MQTT client",ex);}
    }
    public CompletableFuture<Void> publish(String topic,byte[] bytes,int qos) {
        var result=new CompletableFuture<Void>();
        if (!connected()) {result.completeExceptionally(new IllegalStateException("MQTT is disconnected"));return result;}
        try {client.publish(topic,bytes,qos,false,null,new MqttActionListener() {
            public void onSuccess(IMqttToken token) {result.complete(null);}
            public void onFailure(IMqttToken token,Throwable error) {result.completeExceptionally(error);}
        });} catch (MqttException ex) {result.completeExceptionally(ex);}
        return result;
    }
    public synchronized void retryInitialConnection() {
        if (properties.isEnabled() && !connected() && client!=null && !client.isConnected()) {
            try {client.reconnect();} catch (MqttException ex) {log.debug("MQTT connection retry pending: reason {}",ex.getReasonCode());}
        }
    }
    @Override public synchronized void stop() {
        state.disconnected();running=false;
        if (client!=null) {try {client.disconnectForcibly(1000L,1000L,true);client.close(true);} catch (MqttException ex) {log.debug("MQTT close: reason {}",ex.getReasonCode());}client=null;}
    }
    @Override public boolean isRunning() {return running;}
    @jakarta.annotation.PreDestroy public void destroy() {stop();inbound.shutdownNow();}
    @Override public boolean isAutoStartup() {return properties.isEnabled();}
    @Override public int getPhase() {return Integer.MAX_VALUE-100;}
}
