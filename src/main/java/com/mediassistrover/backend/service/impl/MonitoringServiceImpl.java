package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.monitoring.request.MonitoringRequests.*;
import com.mediassistrover.backend.dto.monitoring.response.MonitoringResponses.*;
import com.mediassistrover.backend.dto.pi.request.PiEnvelope;
import com.mediassistrover.backend.dto.pi.response.PiResponses.AckResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapper.MonitoringMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MonitoringServiceImpl implements MonitoringService {
    private final DetectionEventRepository detectionRepository;
    private final CrowdDensityRecordRepository crowdRepository;
    private final HeatmapRecordRepository heatmapRepository;
    private final RobotRepository robotRepository;
    private final FacilityMapRepository mapRepository;
    private final ZoneRepository zoneRepository;
    private final EndpointRepository endpointRepository;
    private final JobStepRepository jobStepRepository;
    private final AccountRepository accountRepository;
    private final MonitoringMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    @Override @Transactional
    public AckResponse recordDetection(PiEnvelope<DetectionPayload> e,DevicePrincipal principal){
        validateEnvelope(e,principal,"DETECTION_EVENT"); Robot robot=robot(principal.robotId());
        if(detectionRepository.existsById(e.messageId()))return ack(e,robot,true);
        DetectionPayload p=e.payload(); FacilityMap map=assignedMap(robot,p.mapId()); Zone zone=zone(p.zoneId(),map);
        validateCoordinates(p.xM(),p.yM()); validateJsonObject(p.eventPayload(),"eventPayload"); validateEvidence(p.evidenceRef());
        detectionRepository.save(DetectionEvent.builder().detectionEventId(e.messageId()).robot(robot).map(map).zone(zone)
                .eventType(p.eventType()).confidenceScore(p.confidenceScore()).xM(p.xM()).yM(p.yM())
                .locationBasis(p.locationBasis()).modelVersion(p.modelVersion()).eventPayloadJson(p.eventPayload())
                .evidenceRef(p.evidenceRef()).detectedAt(p.detectedAt()).receivedAt(Instant.now(clock)).build());
        return ack(e,robot,false);
    }

    @Override @Transactional
    public AckResponse recordCrowdDensity(PiEnvelope<CrowdDensityPayload> e,DevicePrincipal principal){
        validateEnvelope(e,principal,"CROWD_DENSITY_RECORD"); Robot robot=robot(principal.robotId());
        if(crowdRepository.existsById(e.messageId()))return ack(e,robot,true);
        CrowdDensityPayload p=e.payload(); FacilityMap map=robot.getCurrentMap();
        if(map==null)throw new BusinessRuleException("Robot has no assigned map");
        Zone zone=zone(p.zoneId(),map); Endpoint checkpoint=endpoint(p.checkpointId(),map);
        JobStep jobStep=jobStep(e.jobStepId(),robot); validateJsonObject(p.metrics(),"metrics"); validateEvidence(p.evidenceRef());
        crowdRepository.save(CrowdDensityRecord.builder().crowdDensityRecordId(e.messageId()).robot(robot).zone(zone)
                .checkpoint(checkpoint).jobStep(jobStep).personCount(p.personCount()).densityLevel(p.densityLevel())
                .confidenceScore(p.confidenceScore()).modelVersion(p.modelVersion()).metricsJson(p.metrics())
                .evidenceRef(p.evidenceRef()).capturedAt(p.capturedAt()).recordedAt(Instant.now(clock)).build());
        return ack(e,robot,false);
    }

    @Override public DetectionResponse getDetection(UUID id){return mapper.toResponse(detectionRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("DetectionEvent not found: "+id)));}
    @Override public Page<DetectionResponse> listDetections(UUID robotId,DetectionEventType type,Instant from,Instant to,Pageable p){validateWindow(from,to);return detectionRepository.search(robotId,type,from,to,p).map(mapper::toResponse);}
    @Override public CrowdDensityResponse getCrowdDensity(UUID id){return mapper.toResponse(crowdRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("CrowdDensityRecord not found: "+id)));}
    @Override public Page<CrowdDensityResponse> listCrowdDensity(UUID zoneId,CrowdDensityLevel level,Instant from,Instant to,Pageable p){validateWindow(from,to);return crowdRepository.search(zoneId,level,from,to,p).map(mapper::toResponse);}

    @Override @Transactional
    public HeatmapResponse createHeatmap(HeatmapCreate r,UUID actorId,RequestMetadata metadata){
        if(!r.periodStart().isBefore(r.periodEnd()))throw new BusinessRuleException("periodStart must be before periodEnd");
        validateJsonObject(r.gridData(),"gridData"); FacilityMap map=mapRepository.findById(r.mapId()).orElseThrow(()->new ResourceNotFoundException("Map not found: "+r.mapId()));
        String type=r.heatmapType().trim().toUpperCase(Locale.ROOT);
        if(heatmapRepository.existsByMapMapIdAndHeatmapTypeIgnoreCaseAndPeriodStartAndPeriodEndAndCellSizeM(r.mapId(),type,r.periodStart(),r.periodEnd(),r.cellSizeM()))throw new ConflictException("Heatmap window already exists");
        HeatmapRecord h=heatmapRepository.save(HeatmapRecord.builder().map(map).heatmapType(type).periodStart(r.periodStart())
                .periodEnd(r.periodEnd()).cellSizeM(r.cellSizeM()).gridDataJson(r.gridData()).imageUri(r.imageUri())
                .maxValue(r.maxValue()).sourceRecordCount(r.sourceRecordCount()).generatedAt(Instant.now(clock)).build());
        Account actor=accountRepository.findById(actorId).orElseThrow(()->new ResourceNotFoundException("Account not found: "+actorId));
        auditService.record(actor,"HEATMAP_CREATED","HeatmapRecord",h.getHeatmapRecordId(),null,
                Map.of("mapId",r.mapId(),"heatmapType",type,"sourceRecordCount",r.sourceRecordCount()),metadata);
        return mapper.toResponse(h);
    }
    @Override public HeatmapResponse getHeatmap(UUID id){return mapper.toResponse(heatmapRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("HeatmapRecord not found: "+id)));}
    @Override public Page<HeatmapResponse> listHeatmaps(UUID mapId,String type,Pageable p){return heatmapRepository.search(mapId,type==null||type.isBlank()?null:type.trim(),p).map(mapper::toResponse);}

    private void validateEnvelope(PiEnvelope<?> e,DevicePrincipal p,String type){if(!"1.0".equals(e.protocolVersion()))throw new BusinessRuleException("Unsupported protocolVersion");if(!p.robotId().equals(e.robotId()))throw new BusinessRuleException("Envelope robotId does not match device token");if(!type.equals(e.messageType()))throw new BusinessRuleException("Unexpected messageType; expected "+type);if(e.occurredAt().isAfter(Instant.now(clock).plusSeconds(300)))throw new BusinessRuleException("occurredAt is too far in the future");}
    private Robot robot(UUID id){return robotRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+id));}
    private FacilityMap assignedMap(Robot r,UUID id){FacilityMap map=mapRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Map not found: "+id));if(r.getCurrentMap()==null||!r.getCurrentMap().getMapId().equals(id))throw new BusinessRuleException("Event map does not match robot current map");return map;}
    private Zone zone(UUID id,FacilityMap map){if(id==null)return null;Zone z=zoneRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Zone not found: "+id));if(!z.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Zone does not belong to event map");return z;}
    private Endpoint endpoint(UUID id,FacilityMap map){if(id==null)return null;Endpoint ep=endpointRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Endpoint not found: "+id));if(!ep.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Checkpoint and Zone must belong to the same Map");return ep;}
    private JobStep jobStep(UUID id,Robot robot){if(id==null)return null;JobStep s=jobStepRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("JobStep not found: "+id));Robot assigned=s.getJobTask().getJob().getRobot();if(assigned==null||!assigned.getRobotId().equals(robot.getRobotId()))throw new BusinessRuleException("JobStep is not assigned to this robot");return s;}
    private void validateCoordinates(Object x,Object y){if((x==null)!=(y==null))throw new BusinessRuleException("xM and yM must both be present or both be null");}
    private void validateJsonObject(com.fasterxml.jackson.databind.JsonNode n,String name){if(!n.isObject())throw new BusinessRuleException(name+" must be a JSON object");}
    private void validateEvidence(String value){if(value!=null&&value.regionMatches(true,0,"data:",0,5))throw new BusinessRuleException("evidenceRef must be a reference, not embedded base64 data");}
    private void validateWindow(Instant from,Instant to){if(from!=null&&to!=null&&!from.isBefore(to))throw new BusinessRuleException("from must be before to");}
    private AckResponse ack(PiEnvelope<?> e,Robot r,boolean duplicate){return new AckResponse(e.messageId(),e.operationKey(),e.correlationId(),true,duplicate,Instant.now(clock),r.getStatus(),false,false,null);}
}
