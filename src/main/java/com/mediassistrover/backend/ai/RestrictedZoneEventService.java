package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.ai.PatrolFallEventService.PatientLocation;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.entity.DetectionEvent;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** One shared EC2 inference supplies both floor suspicion and registered ground anchors. */
@Service @RequiredArgsConstructor
public class RestrictedZoneEventService {
    public record Request(@NotNull UUID eventId,@NotNull UUID jobId,@NotNull UUID jobStepId,
                          @NotNull Instant detectedAt,@NotBlank @Size(max=100) String algorithm,
                          @NotNull @DecimalMin("0.5") @DecimalMax("1") BigDecimal confidence,
                          @NotNull UUID zoneId,@NotNull @PositiveOrZero Long trackId,
                          @NotNull @Valid PatientLocation personLocation) { }
    public record Result(UUID detectionEventId,UUID alertId,boolean duplicate) { }
    private final DeviceResourceScopePolicy scope;
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final ZoneRepository zones;
    private final RobotMqttSessionRepository links;
    private final RobotNavigationSessionRepository sessions;
    private final MqttSessionRegistry registry;
    private final NavigationSessionService navigation;
    private final PatrolFallEventService locationEvidence;
    private final DetectionEventRepository detections;
    private final RestrictedIntrusionAlertService alerts;
    private final MonitoringReceiptService receipts;
    private final RobotCommandOutboxRepository commands;
    private final MqttWire wire;
    private final Clock clock;

    @Transactional(readOnly=true)
    public JsonNode profile(UUID zoneId,DevicePrincipal device) {
        scope.requireCurrentDevice(device);
        var job=jobs.findByRobotRobotIdAndStatusIn(device.robotId(),List.of(JobStatus.RUNNING)).stream()
                .filter(j->JobOccupancyPolicy.patrol(j) && j.getStatus()==JobStatus.RUNNING).findFirst()
                .orElseThrow(()->new BusinessRuleException("No running patrol"));
        var step=job.getSelectedJobStep();
        if(step==null || !"EXECUTE_SENSING".equals(step.getStepTypeSnapshot())
                || !zoneId.toString().equals(step.getResolvedInputsJson().path("zone_id").asText()))
            throw new BusinessRuleException("Zone differs from the current sensing checkpoint");
        var command=commands.findByJobStepId(step.getJobStepId()).orElseThrow();
        var frozen=command.getAiProfileSnapshotJson();
        if(frozen==null || !"RESTRICTED_ZONE".equals(frozen.path("sensing_type").asText()))
            throw new BusinessRuleException("Restricted checkpoint has no commissioned producer profile");
        return frozen.deepCopy();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result record(Request request,DevicePrincipal device) {
        var authenticated=scope.requireCurrentDevice(device);
        var job=jobs.lockById(request.jobId()).orElseThrow(()->new ResourceNotFoundException("Patrol job not found"));
        if(job.getRobot()==null || !device.robotId().equals(job.getRobot().getRobotId()))
            throw new org.springframework.security.access.AccessDeniedException("Patrol is outside this Device scope");
        var robot=robots.findByIdForUpdate(authenticated.getRobotId()).orElseThrow();
        var payload=wire.object().put("event_id",request.eventId().toString()).put("job_id",request.jobId().toString())
                .put("job_step_id",request.jobStepId().toString()).put("zone_id",request.zoneId().toString())
                .put("track_id",request.trackId()).put("detected_at",request.detectedAt().toString())
                .put("algorithm",request.algorithm()).put("confidence",request.confidence());
        payload.set("person_location",PatrolFallEventService.patientPayload(request.personLocation(),wire));
        var replay=receipts.duplicateRestrictedZone(request.eventId(),robot,payload);
        if(replay.isPresent())return new Result(UUID.fromString(replay.get().path("detection_event_id").asText()),
                UUID.fromString(replay.get().path("alert_id").asText()),true);
        Instant now=clock.instant();
        var step=job.getSelectedJobStep();
        if(!JobOccupancyPolicy.patrol(job) || job.getStatus()!=JobStatus.RUNNING || job.getCancelRequestedAt()!=null
                || step==null || !request.jobStepId().equals(step.getJobStepId())
                || !Set.of("MOVE","EXECUTE_SENSING").contains(step.getStepTypeSnapshot())
                || step.getStatus()!=JobStepStatus.RUNNING)
            throw new BusinessRuleException("Restricted observation requires the current running patrol step");
        if(request.detectedAt().isBefore(now.minusSeconds(15)) || request.detectedAt().isAfter(now.plusSeconds(2)))
            throw new BusinessRuleException("Restricted observation timestamp must be fresh");
        var link=links.findById(robot.getRobotId()).orElse(null);
        if(link==null || !registry.observationFresh(link) || link.getHelloJson()==null
                || link.getHelloJson().path("simulation").asBoolean()
                || !hasCapability(link.getHelloJson().path("capabilities"),"continuous-restricted-v1"))
            throw new BusinessRuleException("Restricted observation requires a live opted-in Pi");
        var map=job.getMap();
        var zone=zones.lockById(request.zoneId()).orElseThrow(()->new ResourceNotFoundException("Zone not found"));
        if(map==null || robot.getCurrentMap()==null || !map.getMapId().equals(robot.getCurrentMap().getMapId())
                || !zone.isActive() || zone.getZoneType()!=ZoneType.KEEP_OUT || !map.getMapId().equals(zone.getMap().getMapId()))
            throw new BusinessRuleException("Restricted zone must be active in the current patrol map");
        var session=sessions.findById(robot.getRobotId()).orElseThrow(()->new BusinessRuleException("Navigation session required"));
        navigation.requireReadyContext(robot.getRobotId(),session.getSessionId(),map.getMapId());
        var person=PatrolFallEventService.verifiedPatientLocation(request.detectedAt(),request.personLocation(),session,map,
                locationEvidence.viewpoint(session,request.detectedAt()));
        if(person==null || person.floorHeightM()==null || person.floorHeightM().abs().compareTo(new BigDecimal("0.30"))>0
                || !inside(zone.getPolygonJson(),person.xM().doubleValue(),person.yM().doubleValue()))
            throw new BusinessRuleException("Restricted person requires verified ground position inside the zone");
        if(detections.existsById(request.eventId()))throw new ConflictException("Detection event ID already exists");
        var data=payload.deepCopy().put("source","SHARED_POSE_RGBD").put("map_id",map.getMapId().toString());
        var detection=detections.saveAndFlush(DetectionEvent.builder().detectionEventId(request.eventId()).robot(robot)
                .map(map).zone(zone).eventType(DetectionEventType.RESTRICTED_INTRUSION).locationBasis(DetectionLocationBasis.OBJECT)
                .xM(person.xM()).yM(person.yM()).confidenceScore(request.confidence().setScale(4,RoundingMode.HALF_UP))
                .modelVersion(request.algorithm()).eventPayloadJson(data).detectedAt(request.detectedAt()).receivedAt(now).build());
        var alert=alerts.recordTracked(detection,step);
        receipts.recordRestrictedZone(request.eventId(),robot,payload,wire.object()
                .put("detection_event_id",detection.getDetectionEventId().toString()).put("alert_id",alert.getAlertId().toString()));
        return new Result(detection.getDetectionEventId(),alert.getAlertId(),false);
    }
    private static boolean hasCapability(JsonNode values,String value) {
        for(var entry:values)if(value.equals(entry.asText()))return true;
        return false;
    }
    static boolean inside(JsonNode polygon,double x,double y) {
        var points=polygon.isObject()?polygon.path("points"):polygon;
        if(!points.isArray() || points.size()<3)return false;
        boolean inside=false;
        for(int i=0,j=points.size()-1;i<points.size();j=i++) {
            var a=points.get(i);var b=points.get(j);
            if(!a.isArray() || a.size()!=2 || !b.isArray() || b.size()!=2)return false;
            double ax=a.get(0).asDouble(),ay=a.get(1).asDouble(),bx=b.get(0).asDouble(),by=b.get(1).asDouble();
            if((ay>y)!=(by>y) && x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside;
    }
}
