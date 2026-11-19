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
    @Mock DeliveryAuthenticationPolicy deliveryAuthentication;
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
        var definition=Step.builder().stepType(StepType.AUTHENTICATE).paramsJson(json.readTree("{\"method\":\"FACE_VERIFICATION\",\"purpose\":\"DELIVERY_RECEIVER\"}")).build();
        step=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(JobTask.builder().job(job).build()).step(definition)
            .status(JobStepStatus.RUNNING).startedAt(clock.instant()).timeoutSecSnapshot(120).build();job.setSelectedJobStep(step);
        delivery=DeliveryRequest.builder().serviceRequest(request).expectedSenderAccount(sender).expectedReceiverAccount(receiver)
            .authenticationMethod(DeliveryAuthenticationMethod.FACE_VERIFICATION).pickedUpAt(clock.instant()).build();
        var feature=json.createArrayNode();for(int i=0;i<128;i++)feature.add(i==0?1:0);
        profile=StaffProfile.builder().account(receiver).active(true).url("https://bucket/avatar.jpg").faceStatus("READY")
            .faceAvatarVersion(UUID.randomUUID()).faceModelVersion("a".repeat(64)).faceTemplate(feature.toString()).build();
        transport=new RobotMqttSession();transport.setRobotId(robot.getRobotId());transport.setTransportSessionId(UUID.randomUUID());
        transport.setHelloJson(json.readTree("{\"unlockPolicy\":\"STAFF_VERIFIED\",\"capabilities\":[\"staff-face-verify-v1\",\"secure-unlock-v1\"]}"));
        session=new FaceAuthSession();session.setSessionId(UUID.randomUUID());session.setJobId(job.getJobId());session.setAuthJobStepId(step.getJobStepId());
        session.setAccountId(receiver.getAccountId());session.setRobotId(robot.getRobotId());session.setAvatarVersion(profile.getFaceAvatarVersion());
        session.setTransportSessionId(transport.getTransportSessionId());session.setExpiresAt(clock.instant().plusSeconds(120));
        scan=new FaceAuthService.Scan(session.getSessionId(),step.getJobStepId(),transport.getTransportSessionId(),"/9j/");
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));when(deviceScope.requireCurrentDevice(device)).thenReturn(robot);
        when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(delivery));
        when(deliveryAuthentication.methodFor(any(),any())).thenAnswer(i->i.getArgument(1)==AuthVerificationPurpose.DELIVERY_SENDER
                ?DeliveryAuthenticationMethod.QR_CODE:((DeliveryRequest)i.getArgument(0)).getAuthenticationMethod());
        when(profiles.lockFace(receiver.getAccountId())).thenReturn(Optional.of(profile));when(profiles.findByAccountAccountId(receiver.getAccountId())).thenReturn(Optional.of(profile));
        when(transports.findById(robot.getRobotId())).thenReturn(Optional.of(transport));when(mqtt.fresh(transport)).thenReturn(true);
        when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(ai.call(eq("verify"),any())).thenAnswer(i->json.createObjectNode().put("matched",true).put("modelVersion",profile.getFaceModelVersion()).put("score",.8));
        when(completion.getObject()).thenReturn(completer);when(completer.complete(any(),any(),any(),any(),any(),any())).thenReturn(true);
        when(verifications.saveAndFlush(any())).thenAnswer(i->{AuthVerification v=i.getArgument(0);v.setVerificationId(UUID.randomUUID());return v;});
        when(storage.head(anyString(),eq(true))).thenReturn(new S3ObjectStorageService.ObjectInfo("images/a.jpg","https://bucket/avatar.jpg","image/jpeg",1,"hash"));
    }
    @AfterEach void cleanup() throws Exception {mocks.close();}
    @Test void correctReceiverCompletesWithScopedGrant(){
        assertTrue(face.verify(job.getJobId(),scan,device).path("verified").asBoolean());
        var captured=ArgumentCaptor.forClass(AuthVerification.class);verify(verifications).saveAndFlush(captured.capture());
        var grant=captured.getValue();assertEquals(receiver,grant.getActorAccount());assertEquals(profile.getFaceAvatarVersion(),grant.getFaceAvatarVersion());
        assertEquals(AuthVerificationMethod.FACE_VERIFICATION,grant.getMethod());assertEquals("DELIVERY_RECEIVE",grant.getRequiredPermission());assertNotNull(session.getUsedAt());
    }
    @Test void senderCannotUseFaceAuthentication(){
        step.getStep().setParamsJson(json.createObjectNode().put("method","FACE_VERIFICATION").put("purpose","DELIVERY_SENDER"));
        delivery.setPickedUpAt(null);rejectBeforeAi();
    }
    @Test void importantDeliveryRequiresOnlyReceiverFaceEnrollment(){
        face.requireDelivery(DeliveryAuthenticationMethod.FACE_VERIFICATION,sender.getAccountId(),receiver.getAccountId());
        verify(profiles).findByAccountAccountId(receiver.getAccountId());
        verify(profiles,never()).findByAccountAccountId(sender.getAccountId());
    }
    @Test void wrongPersonDoesNotGrant(){
        when(ai.call(any(),any())).thenAnswer(i->json.createObjectNode().put("matched",false).put("modelVersion",profile.getFaceModelVersion()).put("reason","NO_MATCH"));
        assertFalse(face.verify(job.getJobId(),scan,device).path("verified").asBoolean());verifyNoInteractions(verifications,completer);assertNull(session.getUsedAt());
    }
    @Test void expiredSessionNeverCallsAi(){session.setExpiresAt(clock.instant());rejectBeforeAi();}
    @Test void cancelledJobNeverCallsAi(){job.setCancelRequestedAt(clock.instant());rejectBeforeAi();}
    @Test void unapprovedAvatarNeverCallsAi(){profile.setFaceStatus("PENDING");rejectBeforeAi();}
    @Test void wrongExpectedAccountNeverCallsAi(){session.setAccountId(sender.getAccountId());rejectBeforeAi();}
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
    @Test void oldGrantCannotUnlockAfterAvatarChanges(){var grant=AuthVerification.builder().method(AuthVerificationMethod.FACE_VERIFICATION).actorAccount(receiver).faceAvatarVersion(profile.getFaceAvatarVersion()).build();profile.setUrl("changed");assertThrows(BusinessRuleException.class,()->face.requireGrant(grant));}
    @Test void avatarReplacementClearsTemplate(){var version=profile.getFaceAvatarVersion();profile.setUrl("new");assertNotEquals(version,profile.getFaceAvatarVersion());assertNull(profile.getFaceTemplate());assertEquals("PENDING",profile.getFaceStatus());profile.setUrl(null);assertEquals("MISSING",profile.getFaceStatus());}
    @Test void lateApprovalCannotRestoreOldAvatar(){
        when(storage.keyFromPublicUrl(any(),any())).thenReturn("images/a.jpg");when(storage.get(any(),anyLong())).thenReturn(new byte[]{1});
        when(ai.call(eq("enroll"),any())).thenAnswer(i->{profile.setUrl("replacement");return json.createObjectNode().put("accepted",true);});
        var admin=AccountPrincipal.fromAccount(Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build());
        assertThrows(BusinessRuleException.class,()->face.review(receiver.getAccountId(),profile.getFaceAvatarVersion(),true,admin));assertNull(profile.getFaceTemplate());
    }
    @Test void enrollmentQualityIsReturnedWithoutCreatingAnApprovedTemplate(){
        when(storage.keyFromPublicUrl(any(),any())).thenReturn("images/a.jpg");when(storage.get(any(),anyLong())).thenReturn(new byte[]{1});
        var answer=json.createObjectNode().put("accepted",false).put("reason","LOW_QUALITY")
                .set("quality",json.createObjectNode().put("issue","FACE_TOO_BLURRY"));
        when(ai.call(eq("enroll"),any())).thenReturn(answer);
        var admin=AccountPrincipal.fromAccount(Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build());
        var result=face.review(receiver.getAccountId(),profile.getFaceAvatarVersion(),true,admin);
        assertEquals("INVALID",result.status());assertEquals("FACE_TOO_BLURRY",result.reason());assertNull(profile.getFaceTemplate());
        when(storage.head(anyString(),eq(true))).thenReturn(new S3ObjectStorageService.ObjectInfo("images/a.jpg","url","image/jpeg",2L*1024*1024+1,"hash"));
        clearInvocations(ai);var error=assertThrows(BusinessRuleException.class,()->face.review(receiver.getAccountId(),profile.getFaceAvatarVersion(),true,admin));
        assertTrue(error.getMessage().startsWith("FACE_AVATAR_TOO_LARGE"));verifyNoInteractions(ai);
    }
    @Test void aiClientPreservesOnlySafeActionableProviderErrors() throws Exception {
        var client=new FaceAiClient(json,true,"http://private-face:8095","s".repeat(32));
        var http=mock(java.net.http.HttpClient.class);
        org.springframework.test.util.ReflectionTestUtils.setField(client,"http",http);
        @SuppressWarnings("unchecked") java.net.http.HttpResponse<String> response=mock(java.net.http.HttpResponse.class);
        when(http.send(any(java.net.http.HttpRequest.class),ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(409);when(response.body()).thenReturn("{\"detail\":\"MODEL_VERSION_CHANGED\"}");
        assertEquals("FACE_MODEL_CHANGED",assertThrows(BusinessRuleException.class,()->client.call("verify",json.createObjectNode())).getMessage());
        when(response.statusCode()).thenReturn(429);when(response.body()).thenReturn("{\"detail\":\"FACE_BUSY\"}");
        assertEquals("FACE_AI_BUSY",assertThrows(BusinessRuleException.class,()->client.call("verify",json.createObjectNode())).getMessage());
        when(response.statusCode()).thenReturn(500);when(response.body()).thenReturn("provider internal detail must not reach callers");
        assertEquals("FACE_AI_UNAVAILABLE",assertThrows(BusinessRuleException.class,()->client.call("verify",json.createObjectNode())).getMessage());
    }
    @Test void reconnectRotatesUnusedNonceWithoutExtendingDeadline(){
        var oldId=session.getSessionId();var deadline=session.getExpiresAt();
        transport.setTransportSessionId(UUID.randomUUID());when(ai.enabled()).thenReturn(true);
        when(jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),List.of(JobStatus.RUNNING))).thenReturn(List.of(job));
        when(sessions.findByAuthJobStepId(step.getJobStepId())).thenReturn(Optional.of(session));
        var result=face.scanWindow(transport).orElseThrow();
        var captured=ArgumentCaptor.forClass(FaceAuthSession.class);verify(sessions).saveAndFlush(captured.capture());
        var replacement=captured.getValue();
        assertNotEquals(oldId,result.sessionId());assertEquals(deadline,result.expiresAt());
        assertEquals(transport.getTransportSessionId(),replacement.getTransportSessionId());
        assertEquals(session.getAccountId(),replacement.getAccountId());assertEquals(session.getAvatarVersion(),replacement.getAvatarVersion());
        var ordered=inOrder(sessions);ordered.verify(sessions).delete(session);ordered.verify(sessions).flush();ordered.verify(sessions).saveAndFlush(replacement);
        // A request already in flight on the disconnected transport cannot use its old nonce.
        clearInvocations(verifications);
        rejectBeforeAi();
    }
    @Test void reconnectNeverRenewsExpiredOrUsedNonce(){
        transport.setTransportSessionId(UUID.randomUUID());when(ai.enabled()).thenReturn(true);
        when(jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),List.of(JobStatus.RUNNING))).thenReturn(List.of(job));
        when(sessions.findByAuthJobStepId(step.getJobStepId())).thenReturn(Optional.of(session));
        session.setExpiresAt(clock.instant());assertTrue(face.scanWindow(transport).isEmpty());
        session.setExpiresAt(clock.instant().plusSeconds(100));session.setUsedAt(clock.instant());
        assertTrue(face.scanWindow(transport).isEmpty());
        verify(sessions,never()).delete(any());verify(sessions,never()).saveAndFlush(any());
    }
    @Test void reconnectCannotReplaceAnAlreadyGrantedNonce(){
        transport.setTransportSessionId(UUID.randomUUID());when(ai.enabled()).thenReturn(true);
        when(jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),List.of(JobStatus.RUNNING))).thenReturn(List.of(job));
        when(sessions.findByAuthJobStepId(step.getJobStepId())).thenReturn(Optional.of(session));
        when(verifications.findByAuthJobStepJobStepId(step.getJobStepId())).thenReturn(Optional.of(AuthVerification.builder().build()));
        assertTrue(face.scanWindow(transport).isEmpty());verify(sessions,never()).saveAndFlush(any());
    }
    @Test void unlockChecksExactDeliveryMethodAndAssignedStaffAtBindAndDispatch() throws Exception {
        var bindings=mock(RobotOperationAuthorizationRepository.class);
        var authorization=new OperationAuthorizationService(jobs,steps,verifications,bindings,deliveries,deliveryAuthentication,json,clock,faceProvider());
        var unlock=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(step.getJobTask()).status(JobStepStatus.PENDING)
                .step(Step.builder().stepType(StepType.CONTROL_HARDWARE).paramsJson(json.readTree("{\"action\":\"UNLOCK_COMPARTMENT\"}"))
                    .inputMappingJson(json.readTree("{\"authorization\":{\"purpose\":\"DELIVERY_RECEIVER\"}}")).build())
                .operationKey("unlock-once").executionSequenceNo(2).build();
        step.setStatus(JobStepStatus.SUCCEEDED);step.setSelectedAt(clock.instant());step.setExecutionSequenceNo(1);
        var grant=AuthVerification.builder().verificationId(UUID.randomUUID()).job(job).authJobStep(step).actorAccount(receiver)
                .actorTokenVersion(receiver.getTokenVersion()).faceAvatarVersion(profile.getFaceAvatarVersion()).method(AuthVerificationMethod.FACE_VERIFICATION)
                .purpose(AuthVerificationPurpose.DELIVERY_RECEIVER).requiredPermission("DELIVERY_RECEIVE").validUntil(clock.instant().plusSeconds(60)).build();
        step.setResultJson(json.createObjectNode().put("verified",true).put("verification_id",grant.getVerificationId().toString()).put("purpose","DELIVERY_RECEIVER"));
        job.setSelectedJobStep(unlock);when(verifications.findById(grant.getVerificationId())).thenReturn(Optional.of(grant));
        var claimed=json.createObjectNode().put("mode","STAFF_VERIFIED").put("verification_id",grant.getVerificationId().toString())
                .put("actor_account_id",receiver.getAccountId().toString()).put("purpose","DELIVERY_RECEIVER").put("valid_until",grant.getValidUntil().toString());
        grant.setMethod(AuthVerificationMethod.QR_CODE);
        assertThrows(BusinessRuleException.class,()->authorization.bind(unlock,claimed));
        grant.setMethod(AuthVerificationMethod.FACE_VERIFICATION);delivery.setExpectedReceiverAccount(sender);
        assertThrows(BusinessRuleException.class,()->authorization.bind(unlock,claimed));
        delivery.setExpectedReceiverAccount(receiver);when(steps.findByJobId(job.getJobId())).thenReturn(List.of(step,unlock));
        assertEquals(claimed,authorization.bind(unlock,claimed));
        var saved=ArgumentCaptor.forClass(RobotOperationAuthorization.class);verify(bindings).saveAndFlush(saved.capture());
        when(bindings.findByJobStepJobStepId(unlock.getJobStepId())).thenReturn(Optional.of(saved.getValue()));
        authorization.requireBound(unlock,claimed);
        delivery.setAuthenticationMethod(DeliveryAuthenticationMethod.QR_CODE);
        assertThrows(BusinessRuleException.class,()->authorization.requireBound(unlock,claimed));
        delivery.setAuthenticationMethod(DeliveryAuthenticationMethod.FACE_VERIFICATION);delivery.setExpectedReceiverAccount(sender);
        assertThrows(BusinessRuleException.class,()->authorization.requireBound(unlock,claimed));
    }
    @Test void persistedSensitiveItemCannotUseQrGrantInACustomQrWorkflow() throws Exception {
        var items=mock(DeliveryItemRepository.class);var bindings=mock(RobotOperationAuthorizationRepository.class);
        var policy=new DeliveryAuthenticationPolicy(items);
        delivery.setAuthenticationMethod(DeliveryAuthenticationMethod.QR_CODE);
        when(items.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(delivery.getServiceRequestId()))
                .thenReturn(List.of(DeliveryItem.builder().sensitive(true).build()));
        var authorization=new OperationAuthorizationService(jobs,steps,verifications,bindings,deliveries,policy,json,clock,faceProvider());
        var unlock=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(step.getJobTask()).status(JobStepStatus.PENDING)
                .step(Step.builder().stepType(StepType.CONTROL_HARDWARE).paramsJson(json.readTree("{\"action\":\"UNLOCK_COMPARTMENT\"}"))
                    .inputMappingJson(json.readTree("{\"authorization\":{\"purpose\":\"DELIVERY_RECEIVER\"}}")).build()).build();
        step.getStep().setParamsJson(json.createObjectNode().put("method","QR_CODE").put("purpose","DELIVERY_RECEIVER"));
        var grant=AuthVerification.builder().verificationId(UUID.randomUUID()).job(job).authJobStep(step).actorAccount(receiver)
                .method(AuthVerificationMethod.QR_CODE).purpose(AuthVerificationPurpose.DELIVERY_RECEIVER).build();
        when(verifications.findById(grant.getVerificationId())).thenReturn(Optional.of(grant));job.setSelectedJobStep(unlock);
        var input=json.createObjectNode().put("verification_id",grant.getVerificationId().toString());
        var error=assertThrows(BusinessRuleException.class,()->authorization.bind(unlock,input));
        assertTrue(error.getMessage().contains("DELIVERY_AUTHENTICATION_POLICY_MISMATCH"));
        verify(bindings,never()).saveAndFlush(any());
    }
    @Test void sensitiveDeliverySenderUsesQrWithoutSenderFaceEnrollment() throws Exception {
        var items=mock(DeliveryItemRepository.class);var bindings=mock(RobotOperationAuthorizationRepository.class);
        when(items.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(delivery.getServiceRequestId()))
                .thenReturn(List.of(DeliveryItem.builder().sensitive(true).build()));
        var authorization=new OperationAuthorizationService(jobs,steps,verifications,bindings,deliveries,new DeliveryAuthenticationPolicy(items),json,clock,faceProvider());
        delivery.setPickedUpAt(null);
        step.getStep().setParamsJson(json.createObjectNode().put("method","QR_CODE").put("purpose","DELIVERY_SENDER"));
        step.setStatus(JobStepStatus.SUCCEEDED);step.setSelectedAt(clock.instant());step.setExecutionSequenceNo(1);
        var unlock=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(step.getJobTask()).status(JobStepStatus.PENDING)
                .step(Step.builder().stepType(StepType.CONTROL_HARDWARE).paramsJson(json.readTree("{\"action\":\"UNLOCK_COMPARTMENT\"}"))
                    .inputMappingJson(json.readTree("{\"authorization\":{\"purpose\":\"DELIVERY_SENDER\"}}")).build())
                .operationKey("sender-unlock").executionSequenceNo(2).build();
        var grant=AuthVerification.builder().verificationId(UUID.randomUUID()).job(job).authJobStep(step).actorAccount(sender)
                .actorTokenVersion(sender.getTokenVersion()).method(AuthVerificationMethod.QR_CODE).purpose(AuthVerificationPurpose.DELIVERY_SENDER)
                .requiredPermission("DELIVERY_SEND").validUntil(clock.instant().plusSeconds(60)).build();
        step.setResultJson(json.createObjectNode().put("verified",true).put("verification_id",grant.getVerificationId().toString()).put("purpose","DELIVERY_SENDER"));
        var claimed=json.createObjectNode().put("mode","STAFF_VERIFIED").put("verification_id",grant.getVerificationId().toString())
                .put("actor_account_id",sender.getAccountId().toString()).put("purpose","DELIVERY_SENDER").put("valid_until",grant.getValidUntil().toString());
        job.setSelectedJobStep(unlock);when(verifications.findById(grant.getVerificationId())).thenReturn(Optional.of(grant));
        when(steps.findByJobId(job.getJobId())).thenReturn(List.of(step,unlock));
        assertEquals(claimed,authorization.bind(unlock,claimed));
        verify(profiles,never()).findByAccountAccountId(sender.getAccountId());
        verify(profiles,never()).lockFace(sender.getAccountId());
    }
    private ObjectProvider<FaceAuthService> faceProvider(){
        @SuppressWarnings("unchecked") ObjectProvider<FaceAuthService> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(face);return provider;
    }
    private void rejectBeforeAi(){assertThrows(BusinessRuleException.class,()->face.verify(job.getJobId(),scan,device));verify(ai,never()).call(any(),any());verifyNoInteractions(verifications,completer);}
}
