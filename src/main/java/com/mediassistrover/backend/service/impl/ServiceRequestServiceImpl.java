package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.*;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ServiceRequestServiceImpl implements ServiceRequestService {
    private static final Set<ServiceRequestType> OPERATIONAL_TYPES=EnumSet.of(
            ServiceRequestType.CROWD_MONITORING,ServiceRequestType.FALL_DETECTION_RESPONSE,
            ServiceRequestType.RESTRICTED_AREA_MONITORING,ServiceRequestType.AUTO_CHARGING,
            ServiceRequestType.NAVIGATION_RECOVERY,ServiceRequestType.ROBOT_STARTUP_STANDBY);
    private final ServiceRequestRepository requestRepository; private final NavigationRequestRepository navigationRepository;
    private final EscortRequestRepository escortRepository; private final FacilityMapRepository mapRepository;
    private final DeliveryRequestRepository deliveryRepository;
    private final OrchestrationWorkService wakeups;
    private final RoomRepository roomRepository; private final EndpointRepository endpointRepository;
    private final ZoneRepository zoneRepository; private final DetectionEventRepository detectionEventRepository;
    private final EndpointGroupRepository groupRepository; private final AccountRepository accountRepository;
    private final JobExecutionService jobExecutionService; private final RequestJobMapper mapper;
    private final JobRepository jobRepository;
    private final RobotRepository robotRepository;
    private final ObjectMapper objectMapper; private final AuditService auditService; private final Clock clock;

    @Override @Transactional public ServiceRequestResponse createInformation(InformationCreateRequest r,String key,AccountPrincipal actor,RequestMetadata md){return createInformation(r,key,new RequestActor(actor.accountId(),null),md);}
    private ServiceRequestResponse createInformation(InformationCreateRequest r,String key,RequestActor actor,RequestMetadata md){
        ServiceRequest existing=existing(key,actor);if(existing!=null){if(existing.getRequestType()!=ServiceRequestType.INFORMATION)throw new ConflictException("Idempotency-Key was used for another request type");return mapper.toResponse(existing);}
        ObjectNode data=data(r.data());if(!data.path("question").isTextual()||data.path("question").asText().isBlank())data.put("question",r.note());
        ServiceRequest request=saveBase(ServiceRequestType.INFORMATION,r.priority(),r.note(),data,key,actor);
        audit(actor,"SERVICE_REQUEST_CREATED",request,md);return mapper.toResponse(request);
    }
    @Override @Transactional public NavigationResponse createNavigation(NavigationCreateRequest r,String key,AccountPrincipal actor,RequestMetadata md){return createNavigation(r,key,new RequestActor(actor.accountId(),null),md);}
    private NavigationResponse createNavigation(NavigationCreateRequest r,String key,RequestActor actor,RequestMetadata md){
        ServiceRequest found=existing(key,actor);if(found!=null){if(found.getRequestType()!=ServiceRequestType.NAVIGATION)throw new ConflictException("Idempotency-Key was used for another request type");return navigation(found);}
        FacilityMap map=map(r.mapId());Endpoint endpoint=endpoint(r.destinationEndpointId());EndpointGroup group=group(r.destinationGroupId());validateTarget(map,r.targetMode(),endpoint,group);
        ObjectNode data=data(r.data());data.put("map_id",map.getMapId().toString());data.put("target_mode",r.targetMode().name());
        if(endpoint!=null)data.put("endpoint_id",endpoint.getEndpointId().toString());if(group!=null)data.put("endpoint_group_id",group.getEndpointGroupId().toString());
        ServiceRequest parent=saveBase(ServiceRequestType.NAVIGATION,r.priority(),r.note(),data,key,actor);
        NavigationRequest child=navigationRepository.save(NavigationRequest.builder().serviceRequest(parent).map(map).room(room(r.roomId())).destinationEndpoint(endpoint).destinationGroup(group).targetMode(r.targetMode()).navigationMode(r.navigationMode()).accessibilityNeed(r.accessibilityNeed()).passengerCount(r.passengerCount()).arrivalInstruction(r.arrivalInstruction()).build());
        audit(actor,"NAVIGATION_REQUEST_CREATED",parent,md);return navigation(child);
    }
    @Override @Transactional public EscortResponse createEscort(EscortCreateRequest r,String key,AccountPrincipal actor,RequestMetadata md){return createEscort(r,key,new RequestActor(actor.accountId(),null),md);}
    private EscortResponse createEscort(EscortCreateRequest r,String key,RequestActor actor,RequestMetadata md){
        ServiceRequest found=existing(key,actor);if(found!=null){if(found.getRequestType()!=ServiceRequestType.ESCORT)throw new ConflictException("Idempotency-Key was used for another request type");return escort(found);}
        FacilityMap map=map(r.mapId());Endpoint destination=endpoint(r.destinationEndpointId());EndpointGroup group=group(r.destinationGroupId());validateTarget(map,r.targetMode(),destination,group);Endpoint origin=endpoint(r.originEndpointId());if(origin!=null&& !origin.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Origin endpoint must belong to request map");
        ObjectNode data=data(r.data());data.put("map_id",map.getMapId().toString());data.put("target_mode",r.targetMode().name());
        if(destination!=null)data.put("endpoint_id",destination.getEndpointId().toString());if(group!=null)data.put("endpoint_group_id",group.getEndpointGroupId().toString());
        data.put("patient_reference",r.patientReference());
        ServiceRequest parent=saveBase(ServiceRequestType.ESCORT,r.priority(),r.note(),data,key,actor);
        EscortRequest child=escortRepository.save(EscortRequest.builder().serviceRequest(parent).map(map).originRoom(room(r.originRoomId())).destinationRoom(room(r.destinationRoomId())).originEndpoint(origin).destinationEndpoint(destination).destinationGroup(group).targetMode(r.targetMode()).patientReference(r.patientReference()).escortMode(r.escortMode()).walkingSpeedMps(r.walkingSpeedMps()).confirmationTimeoutSec(r.confirmationTimeoutSec()).escortNote(r.escortNote()).build());
        audit(actor,"ESCORT_REQUEST_CREATED",parent,md);return escort(child);
    }
    @Override @Transactional public ServiceRequestResponse createOperational(OperationalCreateRequest r,String key,AccountPrincipal actor,RequestMetadata md){
        if(!OPERATIONAL_TYPES.contains(r.requestType()))throw new BusinessRuleException("requestType is not an operational workflow entry point");
        if(!r.data().isObject())throw new BusinessRuleException("Operational request data must be a JSON object");
        if(r.requestType()==ServiceRequestType.ROBOT_STARTUP_STANDBY&&actor.role()!=Role.ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Only ADMIN can request startup readiness checks");
        ServiceRequest found=existing(key,new RequestActor(actor.accountId(),r.robotId()));
        if(found!=null){if(found.getRequestType()!=r.requestType())throw new ConflictException("Idempotency-Key was used for another request type");return mapper.toResponse(found);}
        Robot requestedRobot=robot(r.robotId());ObjectNode payload=data(r.data());validateOperational(r.requestType(),requestedRobot,payload);
        ServiceRequest request=saveBase(r.requestType(),r.priority(),r.note(),payload,key,new RequestActor(actor.accountId(),requestedRobot.getRobotId()));
        // Operational requests are already fully specified and enter the queue atomically.
        request.setStatus(ServiceRequestStatus.SUBMITTED);jobExecutionService.createForRequest(request);request.setStatus(ServiceRequestStatus.QUEUED);
        audit(new RequestActor(actor.accountId(),null),"OPERATIONAL_REQUEST_QUEUED",request,md);return mapper.toResponse(request);
    }
    @Override public ServiceRequestResponse get(UUID id,AccountPrincipal actor){ServiceRequest r=request(id);checkRead(r,actor);return mapper.toResponse(r);}
    @Override public Page<ServiceRequestResponse> list(ServiceRequestStatus status,AccountPrincipal actor,Pageable p){Page<ServiceRequest> page;if(actor.role()==Role.ADMIN)page=status==null?requestRepository.findAll(p):requestRepository.findByStatus(status,p);else page=status==null?requestRepository.findByRequestedByAccountAccountId(actor.accountId(),p):requestRepository.findByRequestedByAccountAccountIdAndStatus(actor.accountId(),status,p);return page.map(mapper::toResponse);}
    @Override @Transactional public ServiceRequestResponse submit(UUID id,AccountPrincipal actor,RequestMetadata md){ServiceRequest r=request(id);checkOwner(r,actor);if(r.getStatus()!=ServiceRequestStatus.DRAFT)throw new BusinessRuleException("Only DRAFT request can be submitted");validateSubtype(r);r.setStatus(ServiceRequestStatus.SUBMITTED);jobExecutionService.createForRequest(r);r.setStatus(ServiceRequestStatus.QUEUED);audit(new RequestActor(actor.accountId(),null),"SERVICE_REQUEST_SUBMITTED",r,md);return mapper.toResponse(r);}
    @Override @Transactional public ServiceRequestResponse cancel(UUID id,String reason,AccountPrincipal actor,RequestMetadata md){ServiceRequest r=request(id);checkOwner(r,actor);if(Set.of(ServiceRequestStatus.COMPLETED,ServiceRequestStatus.FAILED,ServiceRequestStatus.CANCELLED).contains(r.getStatus()))throw new BusinessRuleException("Request is already terminal");Instant now=Instant.now(clock);DeliveryRequest delivery=r.getRequestType()==ServiceRequestType.DELIVERY?deliveryRepository.findById(id).orElse(null):null;boolean physical=delivery!=null&&(delivery.getPickedUpAt()!=null||delivery.getPickupCompartmentConfirmedAt()!=null);for(Job candidate:jobRepository.findByServiceRequestServiceRequestId(id)){Job job=jobRepository.lockById(candidate.getJobId()).orElseThrow();if(physical&&!Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED).contains(job.getStatus())){job.setStatus(JobStatus.RECOVERY_REQUIRED);job.setErrorCode("DELIVERY_CANCEL_AFTER_PHYSICAL_ACTION");job.setFailureReason(reason);if(job.getRobot()!=null)job.getRobot().setStatus(RobotStatus.ERROR);}else if(!Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED,JobStatus.RECOVERY_REQUIRED).contains(job.getStatus())&&job.getCancelCommandId()==null){job.setCancelCommandId(UUID.randomUUID());job.setCancelRequestedAt(now);job.setCancelRequestedByAccount(account(actor.accountId()));}wakeups.request(job);}r.setStatus(physical?ServiceRequestStatus.RECOVERY_REQUIRED:ServiceRequestStatus.CANCELLED);if(!physical)r.setCancelledAt(now);auditService.record(account(actor.accountId()),physical?"DELIVERY_RECOVERY_REQUIRED":"SERVICE_REQUEST_CANCELLED","ServiceRequest",id,null,Map.of("reason",reason),md);return mapper.toResponse(r);}

    private ServiceRequest saveBase(ServiceRequestType type,int priority,String note,JsonNode data,String key,RequestActor actor){if(key==null||key.isBlank()||key.length()>150)throw new BusinessRuleException("Idempotency-Key header is required and must be at most 150 characters");Instant now=Instant.now(clock);return requestRepository.save(ServiceRequest.builder().requestedByAccount(actor.accountId()==null?null:account(actor.accountId())).requestedRobot(actor.robotId()==null?null:robot(actor.robotId())).requestCode("REQ-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT)).requestType(type).sourceType(actor.accountId()!=null?RequestSourceType.STAFF_UI:RequestSourceType.ROBOT_UI).priority(priority).status(ServiceRequestStatus.DRAFT).requestNote(note).requestDataJson(data==null?objectMapper.createObjectNode():data).idempotencyKey(key).requestedAt(now).build());}
    private ObjectNode data(JsonNode source){return source!=null&&source.isObject()?((ObjectNode)source.deepCopy()):objectMapper.createObjectNode();}
    private ServiceRequest existing(String key,RequestActor actor){if(key==null||key.isBlank())return null;ServiceRequest r=requestRepository.findByIdempotencyKey(key).orElse(null);if(r!=null&&(!Objects.equals(r.getRequestedByAccount()==null?null:r.getRequestedByAccount().getAccountId(),actor.accountId()) || !Objects.equals(r.getRequestedRobot()==null?null:r.getRequestedRobot().getRobotId(),actor.robotId())))throw new ConflictException("Idempotency-Key belongs to another account");return r;}
    private void validateSubtype(ServiceRequest r){boolean exists=switch(r.getRequestType()){case NAVIGATION->navigationRepository.existsById(r.getServiceRequestId());case ESCORT->escortRepository.existsById(r.getServiceRequestId());case DELIVERY->deliveryRepository.existsById(r.getServiceRequestId());case INFORMATION->true;case EMERGENCY_SOS->r.getRelatedIncidentId()!=null;case CROWD_MONITORING,FALL_DETECTION_RESPONSE,RESTRICTED_AREA_MONITORING,AUTO_CHARGING,NAVIGATION_RECOVERY,ROBOT_STARTUP_STANDBY->r.getRequestedRobot()!=null;};if(!exists)throw new BusinessRuleException("Request subtype is missing");}
    private void validateOperational(ServiceRequestType type,Robot robot,ObjectNode data){
        if(data.size()>64)throw new BusinessRuleException("Operational request data exceeds 64 fields");
        switch(type){
            case CROWD_MONITORING,RESTRICTED_AREA_MONITORING -> {
                FacilityMap map=requiredMap(data);
                Zone zone=zoneRepository.findById(requiredUuid(data,"zone_id")).orElseThrow(()->new ResourceNotFoundException("Zone not found"));
                Endpoint checkpoint=endpoint(requiredUuid(data,"checkpoint_id"));
                if(!zone.getMap().getMapId().equals(map.getMapId())||!checkpoint.getMap().getMapId().equals(map.getMapId()))
                    throw new BusinessRuleException("Zone and checkpoint must belong to the request map");
                if(!zone.isActive()||!checkpoint.isActive())throw new BusinessRuleException("Zone and checkpoint must be active");
                if(robot.getCurrentMap()==null||!robot.getCurrentMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Robot is not assigned to the requested map");
            }
            case FALL_DETECTION_RESPONSE -> {
                DetectionEvent event=detectionEventRepository.findById(requiredUuid(data,"detection_event_id"))
                        .orElseThrow(()->new ResourceNotFoundException("DetectionEvent not found"));
                if(event.getEventType()!=DetectionEventType.SUSPECTED_FALL)throw new BusinessRuleException("Detection event is not a suspected fall");
                if(!event.getRobot().getRobotId().equals(robot.getRobotId()))throw new BusinessRuleException("Detection event belongs to another robot");
                if(event.getConfidenceScore()==null)throw new BusinessRuleException("Detection event has no confidence score");
                data.put("confidence",event.getConfidenceScore());
            }
            case AUTO_CHARGING -> {data.put("robot_id",robot.getRobotCode());if(!data.hasNonNull("charge_request_id"))data.put("charge_request_id",UUID.randomUUID().toString());}
            case NAVIGATION_RECOVERY -> {
                FacilityMap map=requiredMap(data);UUID endpointId=requiredUuid(data,"destination_endpoint_id");Endpoint destination=endpoint(endpointId);
                if(!destination.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Destination must belong to request map");
                Job original=jobRepository.findById(requiredUuid(data,"recovery_of_job_id"))
                        .orElseThrow(()->new ResourceNotFoundException("Recovery source Job not found"));
                if(original.getRobot()==null||!original.getRobot().getRobotId().equals(robot.getRobotId()))throw new BusinessRuleException("Recovery source Job belongs to another robot");
                if(original.getReconciledAt()==null||!Set.of(JobStatus.FAILED,JobStatus.CANCELLED).contains(original.getStatus()))
                    throw new BusinessRuleException("Navigation recovery requires a reconciled terminal Job");
                if(robot.getStatus()!=RobotStatus.IDLE)throw new BusinessRuleException("Navigation recovery requires an IDLE reconciled robot");
                if(!data.hasNonNull("recovery_request_id"))data.put("recovery_request_id",UUID.randomUUID().toString());
            }
            case ROBOT_STARTUP_STANDBY -> {if(!data.isEmpty())throw new BusinessRuleException("Startup readiness input must be an empty object");}
            default -> throw new BusinessRuleException("Unsupported operational request type");
        }
    }
    private FacilityMap requiredMap(ObjectNode data){return map(requiredUuid(data,"map_id"));}
    private UUID requiredUuid(ObjectNode data,String field){String value=requiredText(data,field);try{return UUID.fromString(value);}catch(IllegalArgumentException ex){throw new BusinessRuleException(field+" must be a UUID");}}
    private String requiredText(ObjectNode data,String field){if(!data.path(field).isTextual()||data.path(field).asText().isBlank())throw new BusinessRuleException(field+" is required");return data.path(field).asText();}
    private void validateTarget(FacilityMap map,TargetMode mode,Endpoint endpoint,EndpointGroup group){if(mode==TargetMode.ENDPOINT&&(endpoint==null||group!=null)||mode==TargetMode.ENDPOINT_GROUP&&(group==null||endpoint!=null))throw new BusinessRuleException("Target mode requires exactly one matching destination");UUID targetMap=endpoint!=null?endpoint.getMap().getMapId():group.getMap().getMapId();if(!targetMap.equals(map.getMapId()))throw new BusinessRuleException("Destination must belong to request map");if(endpoint!=null&&!endpoint.isActive()||group!=null&&!group.isActive())throw new BusinessRuleException("Destination is inactive");}
    private NavigationResponse navigation(ServiceRequest r){return navigation(navigationRepository.findById(r.getServiceRequestId()).orElseThrow(()->new ConflictException("Navigation subtype is missing")));}
    private NavigationResponse navigation(NavigationRequest n){return new NavigationResponse(mapper.toResponse(n.getServiceRequest()),n.getMap().getMapId(),id(n.getRoom()),id(n.getDestinationEndpoint()),gid(n.getDestinationGroup()),id(n.getResolvedEndpoint()),n.getTargetMode(),n.getNavigationMode(),n.getAccessibilityNeed(),n.getPassengerCount(),n.getArrivalInstruction());}
    private EscortResponse escort(ServiceRequest r){return escort(escortRepository.findById(r.getServiceRequestId()).orElseThrow(()->new ConflictException("Escort subtype is missing")));}
    private EscortResponse escort(EscortRequest e){return new EscortResponse(mapper.toResponse(e.getServiceRequest()),e.getMap().getMapId(),id(e.getOriginRoom()),id(e.getDestinationRoom()),id(e.getOriginEndpoint()),id(e.getDestinationEndpoint()),gid(e.getDestinationGroup()),id(e.getResolvedEndpoint()),e.getTargetMode(),e.getPatientReference(),e.getEscortMode(),e.getWalkingSpeedMps(),e.getConfirmationTimeoutSec(),e.getEscortNote());}
    private UUID id(Room x){return x==null?null:x.getRoomId();} private UUID id(Endpoint x){return x==null?null:x.getEndpointId();} private UUID gid(EndpointGroup x){return x==null?null:x.getEndpointGroupId();}
    private void checkRead(ServiceRequest r,AccountPrincipal a){if(a.role()!=Role.ADMIN&&!Objects.equals(r.getRequestedByAccount()==null?null:r.getRequestedByAccount().getAccountId(),a.accountId()))throw new org.springframework.security.access.AccessDeniedException("Request is outside your scope");}
    private void checkOwner(ServiceRequest r,AccountPrincipal a){checkRead(r,a);}
    private RequestSourceType source(Role role){return RequestSourceType.STAFF_UI;}
    private ServiceRequest request(UUID id){return requestRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("ServiceRequest not found: "+id));}
    private Account account(UUID id){return accountRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Account not found: "+id));}
    private FacilityMap map(UUID id){return mapRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Map not found: "+id));}
    private Room room(UUID id){return id==null?null:roomRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Room not found: "+id));}
    private Endpoint endpoint(UUID id){return id==null?null:endpointRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Endpoint not found: "+id));}
    private EndpointGroup group(UUID id){return id==null?null:groupRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("EndpointGroup not found: "+id));}
    private void audit(RequestActor actor,String action,ServiceRequest request,RequestMetadata md){auditService.record(actor.accountId()==null?null:account(actor.accountId()),action,"ServiceRequest",request.getServiceRequestId(),null,Map.of("status",request.getStatus().name()),md);}

    @Override @Transactional public ServiceRequestResponse createDeviceInformation(InformationCreateRequest r,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata md){
        ServiceRequestResponse response=createInformation(r,key,new RequestActor(null,actor.robotId()),md);
        submitDevice(response.serviceRequestId());return mapper.toResponse(request(response.serviceRequestId()));
    }
    @Override @Transactional public NavigationResponse createDeviceNavigation(NavigationCreateRequest r,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata md){
        NavigationResponse response=createNavigation(r,key,new RequestActor(null,actor.robotId()),md);
        ServiceRequest existing=existing(key,new RequestActor(null,actor.robotId()));submitDevice(existing.getServiceRequestId());return navigation(existing);
    }
    @Override @Transactional public EscortResponse createDeviceEscort(EscortCreateRequest r,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata md){
        EscortResponse response=createEscort(r,key,new RequestActor(null,actor.robotId()),md);
        ServiceRequest existing=existing(key,new RequestActor(null,actor.robotId()));submitDevice(existing.getServiceRequestId());return escort(existing);
    }
    private void submitDevice(UUID id){
        ServiceRequest request=request(id);
        if(request.getStatus()!=ServiceRequestStatus.DRAFT)return;
        validateSubtype(request);request.setStatus(ServiceRequestStatus.SUBMITTED);
        jobExecutionService.createForRequest(request);request.setStatus(ServiceRequestStatus.QUEUED);
    }
    private Robot robot(UUID id){return robotRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Robot not found"));}
    private record RequestActor(UUID accountId,UUID robotId){}
}
