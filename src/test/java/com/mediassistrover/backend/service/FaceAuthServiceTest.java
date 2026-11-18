package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.storage.S3ObjectStorageService;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaceAuthServiceTest {
    @Mock FaceAiClient ai;
    @Mock StaffProfileRepository profiles;
    @Mock JobRepository jobs;
    @Mock JobStepRepository steps;
    @Mock DeliveryRequestRepository deliveries;
    @Mock FaceAuthSessionRepository sessions;
    @Mock AuthVerificationRepository verifications;
    @Mock RobotMqttSessionRepository transports;
    @Mock MqttSessionRegistry mqtt;
    @Mock DeviceResourceScopePolicy deviceScope;
    @Mock ObjectProvider<RuntimeStepCompletionService> completion;
    @Mock RuntimeStepCompletionService completer;
    @Mock S3ObjectStorageService storage;
    @Mock TransactionTemplate tx;
    @Spy ObjectMapper json=new ObjectMapper();
    @Spy Clock clock=Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"),ZoneOffset.UTC);
    @InjectMocks FaceAuthService face;
    AutoCloseable mocks;
    Job job; JobStep step; Account sender,receiver; StaffProfile profile; DeliveryRequest delivery;
    Robot robot; RobotMqttSession transport; FaceAuthSession session; FaceAuthService.Scan scan; DevicePrincipal device;
    @BeforeEach void setup() throws Exception {
        mocks=MockitoAnnotations.openMocks(this);
        when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        sender=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build();
        receiver=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build();
        robot=Robot.builder().robotId(UUID.randomUUID()).build();
        device=new DevicePrincipal(robot.getRobotId(),"R1",0,"jti",clock.instant().plusSeconds(300));
        var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.DELIVERY).build();
        var workflow=Workflow.builder().workflowId(UUID.randomUUID()).maxDurationSec(2400).build();
        job=Job.builder().jobId(UUID.randomUUID()).robot(robot).serviceRequest(request).workflow(workflow).status(JobStatus.RUNNING)
            .schemaVersion(3).beOrchestrated(true).startedAt(clock.instant()).build();
        var definition=Step.builder().stepType(StepType.AUTHENTICATE).paramsJson(json.readTree("{\"method\":\"FACE_VERIFICATION\",\"purpose\":\"DELIVERY_SENDER\"}")).build();
        step=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(JobTask.builder().job(job).build()).step(definition)
            .status(JobStepStatus.RUNNING).startedAt(clock.instant()).timeoutSecSnapshot(120).build();job.setSelectedJobStep(step);
        delivery=DeliveryRequest.builder().serviceRequest(request).expectedSenderAccount(sender).expectedReceiverAccount(receiver)
            .authenticationMethod(DeliveryAuthenticationMethod.FACE_VERIFICATION).build();
        var feature=json.createArrayNode();for(int i=0;i<128;i++)feature.add(i==0?1:0);
        profile=StaffProfile.builder().account(sender).active(true).url("https://bucket/avatar.jpg").faceStatus("READY")
            .faceAvatarVersion(UUID.randomUUID()).faceModelVersion("a".repeat(64)).faceTemplate(feature.toString()).build();
        transport=new RobotMqttSession();transport.setRobotId(robot.getRobotId());transport.setTransportSessionId(UUID.randomUUID());
        transport.setHelloJson(json.readTree("{\"unlockPolicy\":\"STAFF_VERIFIED\",\"capabilities\":[\"staff-face-verify-v1\",\"secure-unlock-v1\"]}"));
        session=new FaceAuthSession();session.setSessionId(UUID.randomUUID());session.setJobId(job.getJobId());session.setAuthJobStepId(step.getJobStepId());
        session.setAccountId(sender.getAccountId());session.setRobotId(robot.getRobotId());session.setAvatarVersion(profile.getFaceAvatarVersion());
        session.setTransportSessionId(transport.getTransportSessionId());session.setExpiresAt(clock.instant().plusSeconds(120));
        scan=new FaceAuthService.Scan(session.getSessionId(),step.getJobStepId(),transport.getTransportSessionId(),"/9j/");
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));when(deviceScope.requireCurrentDevice(device)).thenReturn(robot);
        when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(delivery));
        when(profiles.lockFace(sender.getAccountId())).thenReturn(Optional.of(profile));when(profiles.findByAccountAccountId(sender.getAccountId())).thenReturn(Optional.of(profile));
        when(transports.findById(robot.getRobotId())).thenReturn(Optional.of(transport));when(mqtt.fresh(transport)).thenReturn(true);
        when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(ai.call(eq("verify"),any())).thenAnswer(i->json.createObjectNode().put("matched",true).put("modelVersion",profile.getFaceModelVersion()).put("score",.8));
        when(completion.getObject()).thenReturn(completer);when(completer.complete(any(),any(),any(),any(),any(),any())).thenReturn(true);
        when(verifications.saveAndFlush(any())).thenAnswer(i->{AuthVerification v=i.getArgument(0);v.setVerificationId(UUID.randomUUID());return v;});
    }
    @AfterEach void cleanup() throws Exception {mocks.close();}
    @Test void correctSenderCompletesWithScopedGrant(){
        assertTrue(face.verify(job.getJobId(),scan,device).path("verified").asBoolean());
        var captured=ArgumentCaptor.forClass(AuthVerification.class);verify(verifications).saveAndFlush(captured.capture());
        var grant=captured.getValue();assertEquals(sender,grant.getActorAccount());assertEquals(profile.getFaceAvatarVersion(),grant.getFaceAvatarVersion());
        assertEquals(AuthVerificationMethod.FACE_VERIFICATION,grant.getMethod());assertEquals("DELIVERY_SEND",grant.getRequiredPermission());assertNotNull(session.getUsedAt());
    }
    @Test void correctReceiverUsesReceiverTemplate(){
        step.getStep().setParamsJson(json.createObjectNode().put("method","FACE_VERIFICATION").put("purpose","DELIVERY_RECEIVER"));
        delivery.setPickedUpAt(clock.instant());profile.setAccount(receiver);session.setAccountId(receiver.getAccountId());
        when(profiles.lockFace(receiver.getAccountId())).thenReturn(Optional.of(profile));
        assertTrue(face.verify(job.getJobId(),scan,device).path("verified").asBoolean());
        verify(completer).complete(eq(step),any(),eq(receiver),any(),any(),any());
    }
    @Test void wrongPersonDoesNotGrant(){
        when(ai.call(any(),any())).thenAnswer(i->json.createObjectNode().put("matched",false).put("modelVersion",profile.getFaceModelVersion()).put("reason","NO_MATCH"));
        assertFalse(face.verify(job.getJobId(),scan,device).path("verified").asBoolean());verifyNoInteractions(verifications,completer);assertNull(session.getUsedAt());
    }
    @Test void expiredSessionNeverCallsAi(){session.setExpiresAt(clock.instant());rejectBeforeAi();}
    @Test void cancelledJobNeverCallsAi(){job.setCancelRequestedAt(clock.instant());rejectBeforeAi();}
    @Test void unapprovedAvatarNeverCallsAi(){profile.setFaceStatus("PENDING");rejectBeforeAi();}
    @Test void wrongRoleAtPickupNeverCallsAi(){session.setAccountId(receiver.getAccountId());rejectBeforeAi();}
    @Test void usedSessionNeverCallsAi(){session.setUsedAt(clock.instant());rejectBeforeAi();}
    @Test void changedTransportNeverCallsAi(){transport.setTransportSessionId(UUID.randomUUID());rejectBeforeAi();}
    @Test void unsupportedRobotNeverCallsAi(){transport.setHelloJson(json.createObjectNode());rejectBeforeAi();}
    @Test void wrongStepNeverCallsAi(){scan=new FaceAuthService.Scan(scan.sessionId(),UUID.randomUUID(),scan.transportSessionId(),scan.image());rejectBeforeAi();}
    @Test void wrongRobotIsDenied(){when(deviceScope.requireCurrentDevice(device)).thenReturn(Robot.builder().robotId(UUID.randomUUID()).build());assertThrows(AccessDeniedException.class,()->face.verify(job.getJobId(),scan,device));verifyNoInteractions(ai,verifications);}
    @Test void avatarChangeWhileAiRunsCannotGrant(){
        when(ai.call(any(),any())).thenAnswer(i->{profile.setUrl("https://bucket/new.jpg");return json.createObjectNode().put("matched",true).put("modelVersion","a".repeat(64)).put("score",.8);});
        assertThrows(BusinessRuleException.class,()->face.verify(job.getJobId(),scan,device));verifyNoInteractions(verifications,completer);
    }
    @Test void cancellationWhileAiRunsCannotGrant(){
        when(ai.call(any(),any())).thenAnswer(i->{job.setCancelRequestedAt(clock.instant());return json.createObjectNode().put("matched",true).put("modelVersion","a".repeat(64)).put("score",.8);});
        assertThrows(BusinessRuleException.class,()->face.verify(job.getJobId(),scan,device));verifyNoInteractions(verifications,completer);
    }
    @Test void aiFailureDoesNotGrant(){when(ai.call(any(),any())).thenThrow(new BusinessRuleException("FACE_AI_UNAVAILABLE"));assertThrows(BusinessRuleException.class,()->face.verify(job.getJobId(),scan,device));verifyNoInteractions(verifications,completer);}
    @Test void oldGrantCannotUnlockAfterAvatarChanges(){var grant=AuthVerification.builder().method(AuthVerificationMethod.FACE_VERIFICATION).actorAccount(sender).faceAvatarVersion(profile.getFaceAvatarVersion()).build();profile.setUrl("changed");assertThrows(BusinessRuleException.class,()->face.requireGrant(grant));}
    @Test void avatarReplacementClearsTemplate(){var version=profile.getFaceAvatarVersion();profile.setUrl("new");assertNotEquals(version,profile.getFaceAvatarVersion());assertNull(profile.getFaceTemplate());assertEquals("PENDING",profile.getFaceStatus());profile.setUrl(null);assertEquals("MISSING",profile.getFaceStatus());}
    @Test void lateApprovalCannotRestoreOldAvatar(){
        when(storage.keyFromPublicUrl(any(),any())).thenReturn("images/a.jpg");when(storage.get(any(),anyLong())).thenReturn(new byte[]{1});
        when(ai.call(eq("enroll"),any())).thenAnswer(i->{profile.setUrl("replacement");return json.createObjectNode().put("accepted",true);});
        var admin=AccountPrincipal.fromAccount(Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build());
        assertThrows(BusinessRuleException.class,()->face.review(sender.getAccountId(),profile.getFaceAvatarVersion(),true,admin));assertNull(profile.getFaceTemplate());
    }
    private void rejectBeforeAi(){assertThrows(BusinessRuleException.class,()->face.verify(job.getJobId(),scan,device));verify(ai,never()).call(any(),any());verifyNoInteractions(verifications,completer);}
}
