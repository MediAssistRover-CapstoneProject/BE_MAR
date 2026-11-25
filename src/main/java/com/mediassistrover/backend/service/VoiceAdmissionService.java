package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.media.MediaSession;
import com.mediassistrover.backend.media.MediaSessionRepository;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

/** Wake admission never transfers motor ownership; a conversation waits for the confirmed patrol stop. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class VoiceAdmissionService {
    private final DeviceResourceScopePolicy scope;
    private final JobRepository jobs;
    private final VoiceInteractionRepository voices;
    private final EmergencyIncidentRepository incidents;
    private final AlertRepository alerts;
    private final MediaSessionRepository media;
    private final RobotMqttSessionRepository links;
    private final RobotCommandOutboxRepository commands;
    private final MqttSessionRegistry registry;
    private final VoiceInteractionLeaseService leases;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<RobotBatteryPolicyService> batteryPolicy;

    public record Availability(boolean wakeAllowed, boolean conversationReady, UUID patrolJobId,
                               String state, String reason, int leaseSeconds) { }

    public Availability current(DevicePrincipal device, UUID interactionId) {
        Robot robot=scope.requireCurrentDevice(device);
        var owned=jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),JobOccupancyPolicy.NON_TERMINAL);
        var patrols=owned.stream().filter(JobOccupancyPolicy::patrol).toList();
        UUID patrol=patrols.isEmpty()?null:patrols.getFirst().getJobId();
        String blocked=null;
        var link=links.findById(robot.getRobotId()).orElse(null);
        var battery=batteryPolicy==null?null:batteryPolicy.getIfAvailable();
        if(battery!=null && battery.foregroundBlocked(robot.getRobotId()))blocked="BATTERY_RETURN_REQUIRED";
        else if(link==null || !registry.fresh(link) || link.getHeartbeatJson()==null
                || !Set.of(RobotStatus.IDLE,RobotStatus.BUSY).contains(robot.getStatus()))blocked="ROBOT_NOT_CONNECTED";
        else if(!incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(robot.getRobotId(),
                List.of(EmergencyIncidentStatus.OPEN,EmergencyIncidentStatus.ACKNOWLEDGED,EmergencyIncidentStatus.IN_PROGRESS)).isEmpty())blocked="INCIDENT_ACTIVE";
        else if(alerts.existsActiveRestrictedByRobotId(robot.getRobotId()))blocked="RESTRICTED_REVIEW_ACTIVE";
        else if(media.findByActiveRobotId(robot.getRobotId()).filter(s->s.getMode()==MediaSession.Mode.ASSIST).isPresent())blocked="MEDIA_ASSIST_ACTIVE";
        else if(owned.stream().anyMatch(j->j.stopOutstanding() || j.getStatus()==JobStatus.RECOVERY_REQUIRED))blocked="UNRESOLVED_PI_OPERATION";
        else if(owned.stream().anyMatch(j->!JobOccupancyPolicy.patrol(j)))blocked="FOREGROUND_JOB_PENDING";
        var active=voices.findByRobotRobotIdAndStatus(robot.getRobotId(),VoiceInteractionStatus.ACTIVE);
        if(blocked==null && active.stream().anyMatch(v->interactionId!=null && !interactionId.equals(v.getVoiceInteractionId())))blocked="OTHER_VOICE_ACTIVE";
        if(blocked==null && interactionId==null && active.size()>1)blocked="OTHER_VOICE_ACTIVE";
        if(blocked==null && interactionId!=null && active.stream().noneMatch(v->interactionId.equals(v.getVoiceInteractionId())
                && (v.getLeaseExpiresAt()==null || v.getLeaseExpiresAt().isAfter(clock.instant()))))blocked="VOICE_SESSION_ENDED";
        if(blocked!=null)return new Availability(false,false,patrol,"BLOCKED",blocked,leases.leaseSeconds());
        boolean stopped=robot.getStatus()==RobotStatus.IDLE && "IDLE".equals(link.getHeartbeatJson().path("status").asText())
                && link.getHeartbeatJson().path("activeJobStepId").isNull()
                && patrols.stream().allMatch(j->j.getStatus()==JobStatus.SUSPENDED)
                && commands.findByRobotIdAndStateIn(robot.getRobotId(),List.of("PENDING","PUBLISHING","PUBLISHED","ACKNOWLEDGED","STARTED","RECOVERY_REQUIRED")).isEmpty();
        boolean ready=stopped && (interactionId==null || active.stream().anyMatch(v->interactionId.equals(v.getVoiceInteractionId())));
        boolean wake=ready || patrols.stream().anyMatch(j->JobOccupancyPolicy.yieldable(j,50));
        return new Availability(wake,ready,patrol,ready?"READY":wake?"WAITING_FOR_STOP":"BLOCKED",
                ready?null:wake?"PATROL_STOP_PENDING":"ROBOT_NOT_IDLE",leases.leaseSeconds());
    }
}
