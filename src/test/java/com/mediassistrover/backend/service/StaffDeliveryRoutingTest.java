package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.delivery.request.DeliveryRequests;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.impl.*;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffDeliveryRoutingTest {
 @Mock ServiceRequestRepository requests;
 @Mock DeliveryRequestRepository deliveries;
 @Mock DeliveryItemRepository items;
 @Mock FacilityMapRepository maps;
 @Mock RoomRepository rooms;
 @Mock EndpointRepository endpoints;
 @Mock EndpointGroupRepository groups;
 @Mock AccountRepository accounts;
 @Mock RequestJobMapper mapper;
 @Mock AuditService audit;
 @Mock StaffOperationalScopePolicy scope;
 @Mock StaffDutyService duty;
 @Mock FaceAuthService faceAuth;
 @Mock DeliveryAuthenticationPolicy deliveryAuthenticationPolicy;
 @Spy ObjectMapper json=new ObjectMapper();
 @Spy Clock clock=Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"),ZoneOffset.UTC);
 @InjectMocks DeliveryServiceImpl service;
 @InjectMocks JobExecutionServiceImpl execution;
 AutoCloseable mocks;
 Account sender,receiver;Shift shift;FacilityMap map;Endpoint pickup,target;Room room;AccountPrincipal actor;
 @BeforeEach void setup(){mocks=MockitoAnnotations.openMocks(this);
 sender=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build();
 receiver=Account.builder().accountId(UUID.randomUUID()).fullName("Receiver").role(Role.STAFF).status(AccountStatus.ACTIVE).build();actor=AccountPrincipal.fromAccount(sender);
 var floor=Floor.builder().floorId(UUID.randomUUID()).active(true).build();map=FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).status(MapStatus.ACTIVE).build();room=Room.builder().roomId(UUID.randomUUID()).roomCode("102").floor(floor).active(true).build();
 pickup=Endpoint.builder().endpointId(UUID.randomUUID()).map(map).active(true).accessible(true).build();target=Endpoint.builder().endpointId(UUID.randomUUID()).map(map).room(room).active(true).accessible(true).build();
 room.getEndpoints().add(target);
 shift=Shift.builder().shiftId(UUID.randomUUID()).room(room).staffProfile(StaffProfile.builder().account(receiver).build()).build();
 when(duty.require(shift.getShiftId(),receiver.getAccountId())).thenReturn(shift);when(duty.target(shift,map.getMapId())).thenReturn(target);
 when(maps.findById(map.getMapId())).thenReturn(Optional.of(map));when(rooms.findById(room.getRoomId())).thenReturn(Optional.of(room));
 when(endpoints.findById(pickup.getEndpointId())).thenReturn(Optional.of(pickup));when(endpoints.findById(target.getEndpointId())).thenReturn(Optional.of(target));
 when(accounts.findById(sender.getAccountId())).thenReturn(Optional.of(sender));when(accounts.findById(receiver.getAccountId())).thenReturn(Optional.of(receiver));
 when(requests.saveAndFlush(any())).thenAnswer(i->{ServiceRequest r=i.getArgument(0);r.setServiceRequestId(UUID.randomUUID());return r;});
 when(deliveries.save(any())).thenAnswer(i->{DeliveryRequest d=i.getArgument(0);d.setServiceRequestId(d.getServiceRequest().getServiceRequestId());return d;});
 when(items.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(any())).thenReturn(List.of());
 }
 @AfterEach void close() throws Exception{mocks.close();}
 DeliveryRequests.Create input(){return new DeliveryRequests.Create(map.getMapId(),null,UUID.randomUUID(),pickup.getEndpointId(),UUID.randomUUID(),null,sender.getAccountId(),receiver.getAccountId(),TargetMode.ENDPOINT,"Forged name",null,true,null,null,null,List.of(new DeliveryRequests.Item("X","Parcel","GENERAL",1,"item",false,null,null)),50,null,json.createObjectNode().put("receiver_shift_id",UUID.randomUUID().toString()),shift.getShiftId());}
 @Test void serverOverridesForgedDestinationWithCheckedInRoom(){service.create(input(),"test-key",actor,null);var captured=ArgumentCaptor.forClass(DeliveryRequest.class);verify(deliveries).save(captured.capture());
 var d=captured.getValue();assertSame(room,d.getDeliveryRoom());assertSame(target,d.getDeliveryEndpoint());assertSame(shift,d.getReceiverShift());assertEquals("Receiver",d.getRecipientName());assertEquals(shift.getShiftId().toString(),d.getServiceRequest().getRequestDataJson().path("receiver_shift_id").asText());}
 @Test void checkedOutReceiverCannotCreateRequest(){when(duty.require(any(),any())).thenThrow(new BusinessRuleException("Receiver checked out"));assertThrows(BusinessRuleException.class,()->service.create(input(),"key",actor,null));verify(requests,never()).saveAndFlush(any());}
 @Test void submittingDraftRevalidatesAttendanceBeforeCreatingJob(){var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.DELIVERY).build();
 var d=DeliveryRequest.builder().serviceRequest(request).receiverShift(shift).expectedReceiverAccount(receiver).map(map).deliveryRoom(room).deliveryEndpoint(target).build();when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(d));when(duty.require(any(),any())).thenThrow(new BusinessRuleException("Shift ended"));
 assertThrows(BusinessRuleException.class,()->execution.createForRequest(request));}
 @Test void submittingDraftRejectsChangedEndpoint(){var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.DELIVERY).build();
 var d=DeliveryRequest.builder().serviceRequest(request).receiverShift(shift).expectedReceiverAccount(receiver).map(map).deliveryRoom(room).deliveryEndpoint(Endpoint.builder().endpointId(UUID.randomUUID()).build()).build();when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(d));
 assertThrows(BusinessRuleException.class,()->execution.createForRequest(request));}
 DeliveryRequests.Create importantInput(DeliveryAuthenticationMethod method){var r=input();return new DeliveryRequests.Create(r.mapId(),r.pickupRoomId(),r.deliveryRoomId(),r.pickupEndpointId(),r.deliveryEndpointId(),r.deliveryGroupId(),r.expectedSenderAccountId(),r.expectedReceiverAccountId(),r.targetMode(),r.recipientName(),r.recipientContact(),r.requiresConfirmation(),r.temperatureRequirement(),r.deliveryDeadline(),"Giay to quan trong",List.of(new DeliveryRequests.Item("X","Parcel","GENERAL",1,"item",true,null,null)),r.priority(),r.note(),r.data(),r.receiverShiftId(),method);}
 @Test void importantGoodsDefaultToFaceAndPersistPolicy(){var r=importantInput(null);assertEquals(DeliveryAuthenticationMethod.FACE_VERIFICATION,r.authenticationMethod());service.create(r,"important",actor,null);
  var saved=ArgumentCaptor.forClass(DeliveryRequest.class);verify(deliveries).save(saved.capture());assertEquals(DeliveryAuthenticationMethod.FACE_VERIFICATION,saved.getValue().getAuthenticationMethod());verify(faceAuth).requireDelivery(DeliveryAuthenticationMethod.FACE_VERIFICATION,sender.getAccountId(),receiver.getAccountId());}
 @Test void directApiCannotDowngradeImportantGoodsToQr(){assertThrows(BusinessRuleException.class,()->service.create(importantInput(DeliveryAuthenticationMethod.QR_CODE),"downgrade",actor,null));verify(requests,never()).saveAndFlush(any());verifyNoInteractions(faceAuth);}
 @Test void unavailableFaceDoesNotCreateAnImportantQrFallback(){doThrow(new BusinessRuleException("FACE_DISABLED")).when(faceAuth).requireDelivery(DeliveryAuthenticationMethod.FACE_VERIFICATION,sender.getAccountId(),receiver.getAccountId());assertThrows(BusinessRuleException.class,()->service.create(importantInput(null),"unavailable",actor,null));verify(requests,never()).saveAndFlush(any());}
 @Test void persistedSensitiveItemCannotUseAnOldQrPolicy(){var d=DeliveryRequest.builder().serviceRequestId(UUID.randomUUID()).authenticationMethod(DeliveryAuthenticationMethod.QR_CODE).build();when(items.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(d.getServiceRequestId())).thenReturn(List.of(DeliveryItem.builder().sensitive(true).build()));var policy=new DeliveryAuthenticationPolicy(items);assertThrows(BusinessRuleException.class,()->policy.require(d));d.setAuthenticationMethod(DeliveryAuthenticationMethod.FACE_VERIFICATION);assertEquals(DeliveryAuthenticationMethod.FACE_VERIFICATION,policy.require(d));assertEquals(DeliveryAuthenticationMethod.QR_CODE,policy.methodFor(d,AuthVerificationPurpose.DELIVERY_SENDER));assertEquals(DeliveryAuthenticationMethod.FACE_VERIFICATION,policy.methodFor(d,AuthVerificationPurpose.DELIVERY_RECEIVER));}
 @Test void importantWorkflowUsesQrAtPickupAndFaceAtDropoff(){var graph=new StaffQrDeliveryWorkflowFactory(json).build(DeliveryAuthenticationMethod.FACE_VERIFICATION).graph();var auth=graph.steps().stream().filter(s->s.stepType()==StepType.AUTHENTICATE).toList();assertEquals(2,auth.size());assertEquals("QR_CODE",auth.get(0).paramsJson().path("method").asText());assertEquals("DELIVERY_SENDER",auth.get(0).paramsJson().path("purpose").asText());assertEquals("FACE_VERIFICATION",auth.get(1).paramsJson().path("method").asText());assertEquals("DELIVERY_RECEIVER",auth.get(1).paramsJson().path("purpose").asText());}
 @Test void arrivalAudioCannotReplayMoveOrBypassStaffVerification(){
  for(var method:DeliveryAuthenticationMethod.values()){
   var definition=new StaffQrDeliveryWorkflowFactory(json).build(method);var graph=definition.graph();
   assertTrue(definition.workflow().defaultInputsJson().path("pickup_arrival_text").asText().contains("QR"));
   assertEquals(method==DeliveryAuthenticationMethod.QR_CODE,
       definition.workflow().defaultInputsJson().path("delivery_arrival_text").asText().contains("QR"));
   for(String stage:List.of("SENDER","RECEIVER")){
    var moved=graph.transitions().stream().filter(t->t.fromStepCode().equals("MOVE_"+stage)&&t.outcome()==TransitionOutcome.SUCCESS).findFirst().orElseThrow();
    assertEquals("ARRIVAL_"+stage,moved.toStepCode());
    var arrival=graph.steps().stream().filter(s->s.stepCode().equals("ARRIVAL_"+stage)).findFirst().orElseThrow();
    assertEquals("SPEAK",arrival.paramsJson().path("action").asText());
    assertEquals("/inputs/"+(stage.equals("SENDER")?"pickup_arrival_text":"delivery_arrival_text"),arrival.inputMappingJson().at("/data/text/$ref").asText());
    var routes=graph.transitions().stream().filter(t->t.fromStepCode().equals("ARRIVAL_"+stage)).toList();
    assertEquals(3,routes.size());assertTrue(routes.stream().allMatch(t->("AUTH_"+stage).equals(t.toStepCode())&&t.terminalStatus()==null));
   }
   assertEquals("ANNOUNCE_RECEIVER",graph.transitions().stream().filter(t->t.fromStepCode().equals("VERIFY_RECEIVER")&&!t.defaultTransition()).findFirst().orElseThrow().toStepCode());
   assertTrue(graph.transitions().stream().filter(t->t.fromStepCode().equals("ANNOUNCE_RECEIVER")).allMatch(t->t.toStepCode()==null&&t.terminalStatus()==TerminalStatus.SUCCEEDED));
  }
 }

 private final class CargoFixture {
  final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
  final RobotRepository robots=mock(RobotRepository.class);
  final RobotMqttSessionRepository sessions=mock(RobotMqttSessionRepository.class);
  final com.mediassistrover.backend.mqtt.MqttConnectionState connection=new com.mediassistrover.backend.mqtt.MqttConnectionState();
  final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
  final ServiceRequest request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.DELIVERY)
          .status(ServiceRequestStatus.IN_PROGRESS).build();
  final Job job=Job.builder().jobId(UUID.randomUUID()).serviceRequest(request).robot(robot).beOrchestrated(true).schemaVersion(3)
          .status(JobStatus.RUNNING).contextJson(json.createObjectNode()).build();
  final DeliveryRequest delivery=DeliveryRequest.builder().serviceRequestId(request.getServiceRequestId()).serviceRequest(request)
          .pickupPayloadConfirmedAt(clock.instant()).build();
  final RobotMqttSession session=new RobotMqttSession();
  final DeliveryReleasePolicy policy=new DeliveryReleasePolicy(deliveries,commands,robots,sessions,connection,audit,json,clock);
  CargoFixture(){
   when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(delivery));
   when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
   connection.connectedNow();session.setOwnerInstanceId(connection.instanceId());session.setConnectionId(connection.connectionId());
   session.setHandshakeAccepted(true);session.setHandshakeAcked(true);session.setTransportSessionId(UUID.randomUUID());
   session.setHelloReceivedAt(clock.instant().minusSeconds(2));session.setLastHeartbeatAt(clock.instant());
   var hello=json.createObjectNode();hello.putArray("states");session.setHelloJson(hello);
   var heartbeat=json.createObjectNode().put("status","IDLE").putNull("activeJobStepId");
   heartbeat.putObject("telemetry").putObject("odom_speed").put("value",0).put("age_sec",0.1);session.setHeartbeatJson(heartbeat);
   when(sessions.lock(robot.getRobotId())).thenReturn(Optional.of(session));
  }
 }

 @Test void loadedOrOpenedDeliveryRetainsRobotUntilTheReceiverHandoverIsComplete(){
  var f=new CargoFixture();assertTrue(f.policy.requiresReconciliation(f.job));
  f.delivery.setDeliveredAt(clock.instant());assertTrue(f.policy.requiresReconciliation(f.job));
  f.delivery.setDeliveryPayloadConfirmedAt(clock.instant());f.delivery.setDeliveryCompartmentConfirmedAt(clock.instant());
  assertFalse(f.policy.requiresReconciliation(f.job));
  f.delivery.setDeliveredAt(null);f.delivery.setDeliveryPayloadConfirmedAt(null);f.delivery.setDeliveryCompartmentConfirmedAt(null);
  f.delivery.setPickupPayloadConfirmedAt(null);
  var unlock=json.createObjectNode();unlock.putObject("step").put("stepType","CONTROL_HARDWARE").putObject("params").put("action","UNLOCK_COMPARTMENT");
  var command=RobotCommandOutbox.builder().commandType("EXECUTE_STEP").sendAttempts(1).state("COMPLETED")
          .physicalStopConfirmed(true).payloadJson(unlock).build();
  when(f.commands.findByJobIdOrderByCreatedAt(f.job.getJobId())).thenReturn(List.of(command));
  assertTrue(f.policy.requiresReconciliation(f.job),"A stopped UNLOCK does not prove the cart remained empty");
  command.setSendAttempts(0);assertFalse(f.policy.requiresReconciliation(f.job));
 }

 @Test void cargoRecoveryRequiresExplicitConfirmationAndCommittedStopThenKeepsNormalMilestones(){
  var f=new CargoFixture();
  var yes=new com.mediassistrover.backend.dto.job.request.JobRequests.DeliveryRecoveryConfirmation(true,true,true);
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,null,sender,null));
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,
          new com.mediassistrover.backend.dto.job.request.JobRequests.DeliveryRecoveryConfirmation(false,true,true),sender,null));
  var command=RobotCommandOutbox.builder().commandType("EXECUTE_STEP").sendAttempts(1).state("STARTED").physicalStopConfirmed(false).build();
  when(f.commands.findByJobIdOrderByCreatedAt(f.job.getJobId())).thenReturn(List.of(command));
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,yes,sender,null));
  command.setState("COMPLETED");command.setPhysicalStopConfirmed(true);
  f.policy.confirmCleared(f.job,yes,sender,null);f.job.setReconciledByAccount(sender);f.job.setReconciledAt(clock.instant());
  assertFalse(f.policy.requiresReconciliation(f.job));assertNull(f.delivery.getDeliveredAt());assertNull(f.delivery.getDeliveryPayloadConfirmedAt());
  assertTrue(f.job.getContextJson().path("delivery_recovery").path("cargo_removed").asBoolean());
  verify(audit).record(eq(sender),eq("DELIVERY_CARGO_RECOVERY_CONFIRMED"),eq("Job"),eq(f.job.getJobId()),isNull(),anyMap(),isNull());
 }

 @Test void staleMovingOrForeignPiCannotClearCargoWithAStaffFlag(){
  var f=new CargoFixture();var yes=new com.mediassistrover.backend.dto.job.request.JobRequests.DeliveryRecoveryConfirmation(true,true,true);
  f.session.setLastHeartbeatAt(clock.instant().minusSeconds(20));
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,yes,sender,null));
  f.session.setLastHeartbeatAt(clock.instant());
  ((com.fasterxml.jackson.databind.node.ObjectNode)f.session.getHeartbeatJson().at("/telemetry/odom_speed")).put("value",0.1);
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,yes,sender,null));
  ((com.fasterxml.jackson.databind.node.ObjectNode)f.session.getHeartbeatJson().at("/telemetry/odom_speed")).put("value",0);
  f.session.setConnectionId(UUID.randomUUID());
  assertThrows(BusinessRuleException.class,()->f.policy.confirmCleared(f.job,yes,sender,null));
  assertFalse(f.job.getContextJson().has("delivery_recovery"));
 }

 @Test void settledRecoveryCannotAutomaticallyFailAndReleaseLoadedDelivery(){
  var f=new CargoFixture();f.job.setStatus(JobStatus.RECOVERY_REQUIRED);
  var safety=mock(com.mediassistrover.backend.mqtt.MqttJobSafety.class,CALLS_REAL_METHODS);
  org.springframework.test.util.ReflectionTestUtils.setField(safety,"deliveryRelease",f.policy);
  safety.failSettledRecovery(f.job);
  assertEquals(JobStatus.RECOVERY_REQUIRED,f.job.getStatus());assertEquals(RobotStatus.ERROR,f.robot.getStatus());
  assertNull(f.job.getReconciledAt());assertEquals(ServiceRequestStatus.RECOVERY_REQUIRED,f.request.getStatus());
 }

 @Test void failedLoadedDeliveryBecomesRecoveryInsteadOfReturningRobotToTheQueue(){
  var f=new CargoFixture();var orchestrator=mock(WorkflowOrchestratorService.class,CALLS_REAL_METHODS);
  Map.ofEntries(Map.entry("deliveryRelease",f.policy),Map.entry("mapping",mock(com.mediassistrover.backend.mapping.MappingLifecycleService.class)),
          Map.entry("steps",mock(JobStepRepository.class)),Map.entry("tasks",mock(JobTaskRepository.class)),
          Map.entry("events",mock(JobEventRepository.class)),Map.entry("updates",mock(org.springframework.context.ApplicationEventPublisher.class)),
          Map.entry("json",json),Map.entry("clock",clock))
          .forEach((key,value)->org.springframework.test.util.ReflectionTestUtils.setField(orchestrator,key,value));
  org.springframework.test.util.ReflectionTestUtils.invokeMethod(orchestrator,"finish",f.job,TerminalStatus.FAILED);
  assertEquals(JobStatus.RECOVERY_REQUIRED,f.job.getStatus());assertEquals(RobotStatus.ERROR,f.robot.getStatus());assertNull(f.job.getEndedAt());
 }

 @Test void workflowCannotManufactureTheStaffCargoRecoveryRecord(){
  var f=new CargoFixture();var step=JobStep.builder().jobTask(JobTask.builder().job(f.job).build()).build();
  var inputs=json.createObjectNode();inputs.putArray("operations").addObject().put("path","/vars/delivery_recovery")
          .put("operation","SET").putObject("value").put("cargo_removed",true);
  assertThrows(BusinessRuleException.class,()->new SetContextStepHandler(json).execute(step,inputs));
  assertTrue(f.policy.requiresReconciliation(f.job));
 }

 @Test void jobRecoveryRejectsNoteAloneAndReleasesCargoOnlyWithFreshStructuredEvidence(){
  var f=new CargoFixture();f.job.setStatus(JobStatus.RECOVERY_REQUIRED);f.robot.setStatus(RobotStatus.ERROR);
  var jobs=mock(JobRepository.class);var service=mock(JobServiceImpl.class,CALLS_REAL_METHODS);
  when(jobs.lockById(f.job.getJobId())).thenReturn(Optional.of(f.job));
  Map.ofEntries(Map.entry("jobRepository",jobs),Map.entry("deliveryRelease",f.policy),Map.entry("mqttCommands",f.commands),
          Map.entry("robotRepository",f.robots),Map.entry("robotSessions",f.sessions),Map.entry("staffScope",scope),
          Map.entry("accountRepository",accounts),Map.entry("mapping",mock(com.mediassistrover.backend.mapping.MappingLifecycleService.class)),
          Map.entry("eventRepository",mock(JobEventRepository.class)),Map.entry("mapper",mock(RequestJobMapper.class)),
          Map.entry("auditService",audit),Map.entry("objectMapper",json),Map.entry("clock",clock))
          .forEach((key,value)->org.springframework.test.util.ReflectionTestUtils.setField(service,key,value));
  var outcome=com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileOutcome.RESUME_AS_FAILED;
  assertThrows(BusinessRuleException.class,()->service.reconcile(f.job.getJobId(),
          new com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileJobRequest("handled",outcome),actor,null));
  assertEquals(JobStatus.RECOVERY_REQUIRED,f.job.getStatus());assertEquals(RobotStatus.ERROR,f.robot.getStatus());
  var confirmation=new com.mediassistrover.backend.dto.job.request.JobRequests.DeliveryRecoveryConfirmation(true,true,true);
  service.reconcile(f.job.getJobId(),new com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileJobRequest("goods removed and cart secured",outcome,confirmation),actor,null);
  assertEquals(JobStatus.FAILED,f.job.getStatus());assertEquals(RobotStatus.STARTING,f.robot.getStatus());assertTrue(f.session.isClosed());
  assertEquals(ServiceRequestStatus.FAILED,f.request.getStatus());assertFalse(f.policy.requiresReconciliation(f.job));
  assertNull(f.delivery.getDeliveredAt());
 }
}
