package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.delivery.request.DeliveryRequests.*;
import com.mediassistrover.backend.dto.delivery.response.DeliveryResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class DeliveryServiceImpl implements DeliveryService {
    private final ServiceRequestRepository requestRepository; private final DeliveryRequestRepository deliveryRepository;
    private final DeliveryItemRepository itemRepository; private final FacilityMapRepository mapRepository;
    private final RoomRepository roomRepository; private final EndpointRepository endpointRepository;
    private final EndpointGroupRepository groupRepository; private final AccountRepository accountRepository;
    private final AuthVerificationService verificationService; private final RequestJobMapper mapper;
    private final AuditService auditService; private final ObjectMapper objectMapper; private final Clock clock;

    @Override @Transactional public DeliveryResponse create(Create r,String key,AccountPrincipal actor,RequestMetadata md){
        validateKey(key);ServiceRequest existing=requestRepository.findByIdempotencyKey(key).orElse(null);
        if(existing!=null){checkOwner(existing,actor);if(existing.getRequestType()!=ServiceRequestType.DELIVERY)throw new ConflictException("Idempotency-Key was used for another request type");return response(delivery(existing.getServiceRequestId()));}
        FacilityMap map=map(r.mapId());Endpoint pickup=endpoint(r.pickupEndpointId());Endpoint destination=endpoint(r.deliveryEndpointId());EndpointGroup group=group(r.deliveryGroupId());
        validateMap(map,pickup,"Pickup endpoint");validateTarget(map,r.targetMode(),destination,group);
        Room pickupRoom=room(r.pickupRoomId());Room deliveryRoom=room(r.deliveryRoomId());validateRoomMap(map,pickupRoom);validateRoomMap(map,deliveryRoom);
        ObjectNode data=r.data()!=null&&r.data().isObject()?((ObjectNode)r.data().deepCopy()):objectMapper.createObjectNode();
        data.put("pickup_endpoint_id",pickup.getEndpointId().toString());data.put("target_mode",r.targetMode().name());
        if(destination!=null)data.put("delivery_endpoint_id",destination.getEndpointId().toString());if(group!=null)data.put("delivery_group_id",group.getEndpointGroupId().toString());
        Instant now=Instant.now(clock);Account account=account(actor.accountId());
        ServiceRequest parent=requestRepository.saveAndFlush(ServiceRequest.builder().requestedByAccount(account).requestCode(code("REQ")).requestType(ServiceRequestType.DELIVERY).sourceType(RequestSourceType.STAFF_UI).priority(r.priority()).status(ServiceRequestStatus.DRAFT).requestNote(r.note()).requestDataJson(data).idempotencyKey(key).requestedAt(now).build());
        DeliveryRequest delivery=deliveryRepository.save(DeliveryRequest.builder().serviceRequest(parent).map(map).pickupRoom(pickupRoom).deliveryRoom(deliveryRoom).pickupEndpoint(pickup).deliveryEndpoint(destination).deliveryGroup(group).targetMode(r.targetMode()).recipientName(r.recipientName()).recipientContact(r.recipientContact()).requiresConfirmation(true).temperatureRequirement(r.temperatureRequirement()).deliveryDeadline(r.deliveryDeadline()).deliveryNote(r.deliveryNote()).build());
        Set<String> codes=new HashSet<>();for(Item item:r.items()){String normalized=item.itemCode().trim().toUpperCase(Locale.ROOT);if(!codes.add(normalized))throw new BusinessRuleException("Duplicate delivery itemCode: "+normalized);itemRepository.save(DeliveryItem.builder().deliveryRequest(delivery).itemCode(normalized).itemName(item.itemName().trim()).itemCategory(item.itemCategory().trim().toUpperCase(Locale.ROOT)).quantity(item.quantity()).unit(item.unit().trim()).sensitive(item.sensitive()).handlingInstruction(item.handlingInstruction()).itemNote(item.itemNote()).build());}
        auditService.record(account,"DELIVERY_REQUEST_CREATED","DeliveryRequest",parent.getServiceRequestId(),null,Map.of("itemCount",r.items().size()),md);return response(delivery);
    }
    @Override public DeliveryResponse get(UUID id,AccountPrincipal actor){DeliveryRequest d=delivery(id);checkOwner(d.getServiceRequest(),actor);return response(d);}
    @Override @Transactional public DeliveryResponse confirmPickup(UUID id,Confirm r,AccountPrincipal actor,RequestMetadata md){DeliveryRequest d=delivery(id);checkOwnerOrAdmin(d.getServiceRequest(),actor);if(d.getPickedUpAt()!=null)return response(d);if(d.getServiceRequest().getStatus()!=ServiceRequestStatus.IN_PROGRESS)throw new BusinessRuleException("Pickup requires IN_PROGRESS request");verificationService.requireUsable(r.verificationId(),id,AuthVerificationPurpose.DELIVERY_SENDER,actor);Instant now=Instant.now(clock);d.setSenderAccount(account(actor.accountId()));d.setPickedUpAt(now);auditService.record(d.getSenderAccount(),"DELIVERY_PICKUP_CONFIRMED","DeliveryRequest",id,null,Map.of("verificationId",r.verificationId()),md);return response(d);}
    @Override @Transactional public DeliveryResponse confirmDelivery(UUID id,Confirm r,AccountPrincipal actor,RequestMetadata md){DeliveryRequest d=delivery(id);checkOwnerOrAdmin(d.getServiceRequest(),actor);if(d.getDeliveredAt()!=null)return response(d);if(d.getPickedUpAt()==null)throw new BusinessRuleException("Pickup must be confirmed before delivery");verificationService.requireUsable(r.verificationId(),id,AuthVerificationPurpose.DELIVERY_RECEIVER,actor);Instant now=Instant.now(clock);d.setReceiverAccount(account(actor.accountId()));d.setDeliveredAt(now);auditService.record(d.getReceiverAccount(),"DELIVERY_HANDOFF_CONFIRMED","DeliveryRequest",id,null,Map.of("verificationId",r.verificationId()),md);return response(d);}
    private DeliveryResponse response(DeliveryRequest d){List<ItemResponse> items=itemRepository.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(d.getServiceRequestId()).stream().map(i->new ItemResponse(i.getDeliveryItemId(),i.getItemCode(),i.getItemName(),i.getItemCategory(),i.getQuantity(),i.getUnit(),i.isSensitive(),i.getHandlingInstruction(),i.getItemNote())).toList();return new DeliveryResponse(mapper.toResponse(d.getServiceRequest()),id(d.getMap()),id(d.getPickupRoom()),id(d.getDeliveryRoom()),id(d.getPickupEndpoint()),id(d.getDeliveryEndpoint()),gid(d.getDeliveryGroup()),id(d.getResolvedDeliveryEndpoint()),aid(d.getSenderAccount()),aid(d.getReceiverAccount()),d.getTargetMode(),d.getRecipientName(),d.getRecipientContact(),d.isRequiresConfirmation(),d.getTemperatureRequirement(),d.getDeliveryDeadline(),d.getPickedUpAt(),d.getDeliveredAt(),d.getDeliveryNote(),d.getRowVersion(),items);}
    private void validateTarget(FacilityMap map,TargetMode mode,Endpoint endpoint,EndpointGroup group){if(mode==TargetMode.ENDPOINT&&(endpoint==null||group!=null)||mode==TargetMode.ENDPOINT_GROUP&&(group==null||endpoint!=null))throw new BusinessRuleException("Target mode requires exactly one matching delivery target");if(endpoint!=null){validateMap(map,endpoint,"Delivery endpoint");if(!endpoint.isActive())throw new BusinessRuleException("Delivery endpoint is inactive");}if(group!=null&&(!group.getMap().getMapId().equals(map.getMapId())||!group.isActive()))throw new BusinessRuleException("Delivery group must be active and belong to request map");}
    private void validateMap(FacilityMap map,Endpoint endpoint,String name){if(!endpoint.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException(name+" must belong to request map");if(!endpoint.isActive())throw new BusinessRuleException(name+" is inactive");}
    private void validateRoomMap(FacilityMap map,Room room){if(room!=null&&!room.getFloor().getFloorId().equals(map.getFloor().getFloorId()))throw new BusinessRuleException("Room must belong to request floor");}
    private void checkOwner(ServiceRequest request,AccountPrincipal actor){if(actor.role()!=Role.ADMIN&&!Objects.equals(request.getRequestedByAccount().getAccountId(),actor.accountId()))throw new org.springframework.security.access.AccessDeniedException("Delivery request is outside your scope");}
    private void checkOwnerOrAdmin(ServiceRequest request,AccountPrincipal actor){checkOwner(request,actor);}
    private void validateKey(String key){if(key==null||key.isBlank()||key.length()>150)throw new BusinessRuleException("Idempotency-Key header is required and must be at most 150 characters");}
    private String code(String prefix){return prefix+"-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);}
    private DeliveryRequest delivery(UUID id){return deliveryRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("DeliveryRequest not found: "+id));}
    private FacilityMap map(UUID id){return mapRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Map not found: "+id));}
    private Room room(UUID id){return id==null?null:roomRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Room not found: "+id));}
    private Endpoint endpoint(UUID id){return id==null?null:endpointRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Endpoint not found: "+id));}
    private EndpointGroup group(UUID id){return id==null?null:groupRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("EndpointGroup not found: "+id));}
    private Account account(UUID id){return accountRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Account not found: "+id));}
    private UUID id(FacilityMap x){return x==null?null:x.getMapId();}private UUID id(Room x){return x==null?null:x.getRoomId();}private UUID id(Endpoint x){return x==null?null:x.getEndpointId();}private UUID gid(EndpointGroup x){return x==null?null:x.getEndpointGroupId();}private UUID aid(Account x){return x==null?null:x.getAccountId();}
}
