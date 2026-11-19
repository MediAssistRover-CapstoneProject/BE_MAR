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
 var floor=Floor.builder().floorId(UUID.randomUUID()).build();map=FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).build();room=Room.builder().roomId(UUID.randomUUID()).roomCode("102").floor(floor).active(true).build();
 pickup=Endpoint.builder().endpointId(UUID.randomUUID()).map(map).active(true).accessible(true).build();target=Endpoint.builder().endpointId(UUID.randomUUID()).map(map).room(room).active(true).accessible(true).build();
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
}
