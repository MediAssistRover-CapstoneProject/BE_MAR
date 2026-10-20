package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.DeflaterOutputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappingLiveTest {
    final UUID robot=UUID.fromString("11111111-1111-4111-8111-111111111111"),sid=UUID.fromString("22222222-2222-4222-8222-222222222222"),
            jobId=UUID.fromString("33333333-3333-4333-8333-333333333333"),tid=UUID.fromString("44444444-4444-4444-8444-444444444444");
    final Instant now=Instant.parse("2026-10-14T10:00:00Z");final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    final MqttWire wire=new MqttWire();final MqttContractValidator contracts=new MqttContractValidator();
    final JobRepository jobs=mock(JobRepository.class);final MappingSessionRepository sessions=mock(MappingSessionRepository.class);
    final RobotMqttSessionRepository links=mock(RobotMqttSessionRepository.class);final MqttSessionRegistry registry=mock(MqttSessionRegistry.class);
    final MqttProperties mqtt=new MqttProperties();final MappingProperties properties=new MappingProperties();
    final PahoMqttTransport transport=mock(PahoMqttTransport.class);
    final MappingControlService controls=new MappingControlService(jobs,sessions,links,registry,mqtt,properties,transport,wire,contracts,clock);
    final MappingPreviewService previews=new MappingPreviewService(sessions,clock,mock(ApplicationEventPublisher.class));
    MappingSession session;Job job;RobotMqttSession link;ObjectNode published;
    @BeforeEach void setup() {
        properties.setEnabled(true);mqtt.setEnabled(true);mqtt.setDispatchEnabled(true);mqtt.setRobotIds(Set.of(robot));
        session=MappingSession.builder().sessionId(sid).jobId(jobId).robotId(robot).state("MAPPING").potentiallyOpen(true).build();
        job=Job.builder().jobId(jobId).status(JobStatus.RUNNING).selectedJobStep(JobStep.builder().status(JobStepStatus.RUNNING).stepTypeSnapshot("WAIT")
                .resolvedInputsJson(wire.object().put("event_type","MAPPING_SAVE_REQUESTED")).build()).build();
        link=new RobotMqttSession();link.setTransportSessionId(tid);link.setProducerSessionId(UUID.randomUUID());link.setHelloJson(wire.object().put("mappingEnabled",true));
        when(jobs.lockById(jobId)).thenReturn(Optional.of(job));when(sessions.findByJobId(jobId)).thenReturn(Optional.of(session));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));when(links.findById(robot)).thenReturn(Optional.of(link));
        when(registry.fresh(link)).thenReturn(true);when(transport.connected()).thenReturn(true);
        when(transport.publish(anyString(),any(),eq(0))).thenAnswer(i->{published=wire.parse(i.getArgument(1));return CompletableFuture.completedFuture(null);});
    }
    @Test void publishesBoundedControlAndCorrelatesOnlyCurrentPiAck() throws Exception {
        var sent=controls.send(jobId,new MappingControlService.Request(sid,.05,0.,400));
        verify(transport).publish(eq("mar/robots/"+robot+"/mapping/control"),any(),eq(0));contracts.live(published);
        assertThat(controls.status(jobId).status()).isEqualTo("MQTT_PUBLISHED");
        Files.createDirectories(Path.of("target/mapping-contract"));Files.writeString(Path.of("target/mapping-contract/be-control.json"),wire.json(published));
        ObjectNode ack=published.deepCopy();ack.remove(List.of("issuedAt","expiresAt","linearX","angularZ","durationMs"));
        ack.put("messageType","MAPPING_CONTROL_ACK").put("producerSessionId",link.getProducerSessionId().toString()).put("accepted",true).putNull("errorCode").put("receivedAt",now.toString());contracts.live(ack);
        ack.put("transportSessionId",UUID.randomUUID().toString());controls.acknowledge(robot,ack);assertThat(controls.status(jobId).status()).isEqualTo("MQTT_PUBLISHED");
        ack.put("transportSessionId",tid.toString());controls.acknowledge(robot,ack);assertThat(controls.status(jobId).status()).isEqualTo("PI_ACCEPTED");
        assertThat(sent.expiresAt()).isEqualTo(now.plusMillis(750));
    }
    @Test void rejectsMoveOutsideWaitAndStopRemainsAvailableDuringSave() {
        session.setState("SAVING");job.getSelectedJobStep().setStepTypeSnapshot("EXECUTE_SENSING");
        assertThatThrownBy(()->controls.send(jobId,new MappingControlService.Request(sid,.05,0.,400))).hasMessageContaining("Move only");
        assertThatCode(()->controls.send(jobId,new MappingControlService.Request(sid,0.,0.,400))).doesNotThrowAnyException();
    }
    @Test void rejectsDisconnectedForeignSessionAndOutOfRangeVelocity() {
        assertThatThrownBy(()->controls.send(jobId,new MappingControlService.Request(UUID.randomUUID(),.05,0.,400))).hasMessageContaining("does not belong");
        for(double speed:new double[]{-.01,.06,Double.NaN,Double.POSITIVE_INFINITY})
            assertThatThrownBy(()->controls.send(jobId,new MappingControlService.Request(sid,speed,0.,400))).hasMessageContaining("Invalid");
        when(registry.fresh(link)).thenReturn(false);
        assertThatThrownBy(()->controls.send(jobId,new MappingControlService.Request(sid,.05,0.,400))).hasMessageContaining("not ready");
        verify(transport,never()).publish(anyString(),any(),anyInt());
    }
    @Test void limitsRepeatedMoveButAllowsImmediateStop() {
        controls.send(jobId,new MappingControlService.Request(sid,.05,0.,400));
        assertThatThrownBy(()->controls.send(jobId,new MappingControlService.Request(sid,.05,0.,400))).hasMessageContaining("250 ms");
        controls.send(jobId,new MappingControlService.Request(sid,0.,0.,400));assertThat(published.path("linearX").asDouble()).isZero();
    }
    ObjectNode map(byte[] grid) throws Exception {
        var bytes=new ByteArrayOutputStream();try(var zip=new DeflaterOutputStream(bytes)){zip.write(grid);}
        return wire.object().put("mapping_session_id",sid.toString()).put("captured_at",now.toString()).put("revision",1)
                .put("width",2).put("height",2).put("data",Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
    @Test void mapPngFlipsRosRowsAndRejectsCorruptCellsAndStaleFrames() throws Exception {
        var frame=map(new byte[]{0,100,-1,50});previews.accept(robot,frame);
        var image=ImageIO.read(new ByteArrayInputStream(previews.mapImage(sid).bytes()));
        assertThat(image.getRaster().getSample(0,1,0)).isEqualTo(254);assertThat(image.getRaster().getSample(1,1,0)).isZero();
        assertThat(image.getRaster().getSample(0,0,0)).isEqualTo(205);
        var invalid=map(new byte[]{0,101,0,0}).put("revision",2).put("captured_at",now.plusMillis(1).toString());
        assertThatThrownBy(()->previews.accept(robot,invalid)).hasMessageContaining("Invalid occupancy");
        session.setPotentiallyOpen(false);assertThat(previews.mapImage(sid)).isNull();
    }
    @Test void cameraJpegRequiresMatchingDimensionsAndSession() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,3,BufferedImage.TYPE_INT_RGB),"jpeg",bytes);
        var payload=wire.object().put("mapping_session_id",sid.toString()).put("captured_at",now.toString()).put("revision",1)
                .put("width",4).put("height",3).put("encoding","jpeg-base64").put("data",Base64.getEncoder().encodeToString(bytes.toByteArray()));
        var envelope=wire.object().put("messageType","CAMERA_PREVIEW").put("robotId",robot.toString()).put("transportSessionId",tid.toString());envelope.set("payload",payload);contracts.live(envelope);
        previews.acceptCamera(robot,payload);assertThat(previews.cameraImage(sid).bytes()).isEqualTo(bytes.toByteArray());
        payload.put("width",5);assertThatThrownBy(()->previews.acceptCamera(robot,payload)).hasMessageContaining("dimensions");
        payload.put("width",4).put("captured_at",now.minusSeconds(20).toString()).put("revision",2);previews.acceptCamera(robot,payload);
        assertThat(previews.cameraImage(sid).revision()).isEqualTo(1);
    }
}
