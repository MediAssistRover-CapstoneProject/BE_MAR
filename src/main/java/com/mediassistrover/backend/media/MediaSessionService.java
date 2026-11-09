package com.mediassistrover.backend.media;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import java.util.*;

import static com.mediassistrover.backend.media.MediaSession.State.*;

@Service @RequiredArgsConstructor @Transactional
public class MediaSessionService {
    private final MediaProperties properties;
    private final LiveKitGateway livekit;
    private final MediaSessionRepository sessions;
    private final RobotRepository robots;
    private final EmergencyIncidentRepository incidents;
    private final StaffOperationalScopePolicy staffScope;
    private final DeviceResourceScopePolicy deviceScope;
    private final JobRepository jobs;
    private final com.mediassistrover.backend.service.OrchestrationWorkService jobWakeups;
    private final MediaEvents events;
    private final Clock clock;
    public record View(UUID sessionId,UUID robotId,UUID incidentId,UUID staffAccountId,
                       MediaSession.Mode mode,MediaSession.State state,Instant createdAt,
                       Instant expiresAt,Instant deviceSeenAt,Instant endedAt,String endReason) { }
    public record Event(boolean enabled,View session) { }
    public record Credentials(String url,String room,String identity,String token,Instant expiresAt,
                              View session,int width,int height,int fps,int bitrate) { }

    public Map<String,Object> configuration(AccountPrincipal actor) {
        staffScope.requireCurrentStaffOrAdmin(actor);
        return Map.of("enabled",properties.isEnabled(),"url",properties.getUrl(),"videoWidth",640,
                "videoHeight",480,"videoFps",15,"recordingEnabled",false,"internalTls",false);
    }
    public List<View> list(AccountPrincipal actor) {
        staffScope.requireCurrentStaffOrAdmin(actor);
        return sessions.findTop100ByActiveRobotIdIsNotNullOrderByCreatedAtDesc().stream()
                .filter(s->canRead(s,actor)).map(this::view).toList();
    }
    public View get(UUID id,AccountPrincipal actor) {var s=require(id);requireRead(s,actor);return view(s);}
    public View create(UUID robotId,UUID incidentId,MediaSession.Mode mode,AccountPrincipal actor) {
        livekit.ready();var staff=staffScope.requireCurrentStaffOrAdmin(actor);
        var robot=robots.findByIdForUpdate(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        EmergencyIncident incident=null;
        if(incidentId!=null) {
            incident=incidents.findById(incidentId).orElseThrow(()->new ResourceNotFoundException("Incident not found"));
            staffScope.requireIncidentAccess(incident,actor);incidentOpen(incident);
            requireSosResponder(incident, actor);
            if(incident.getRobot()==null||!robotId.equals(incident.getRobot().getRobotId()))
                throw new BusinessRuleException("MEDIA_INCIDENT_ROBOT_MISMATCH");
        } else if(actor.role()!=Role.ADMIN) throw new AccessDeniedException("Staff media sessions require an accessible incident");
        var old=sessions.findByActiveRobotId(robotId);
        if(old.isPresent()) {
            var s=old.get();
            if(s.getStaff()!=null && s.getStaff().getAccountId().equals(staff.getAccountId()) && s.getMode()==mode
                    && Objects.equals(incidentId,s.getIncident()==null?null:s.getIncident().getIncidentId()))return view(s);
            throw new ConflictException("ROBOT_MEDIA_SESSION_ALREADY_OPEN");
        }
        requireDeviceListener(robotId);
        var s=newSession(robot,incident,mode);s.setStaff(staff);s.setStaffTokenVersion(staff.getTokenVersion());s.setAcceptedAt(now());s.setState(CONNECTING);
        sessions.saveAndFlush(s);stopForAssist(s);notifyAfterCommit(s);return view(s);
    }
    /** Shares the existing SOS transaction; never contacts the media server here. */
    public UUID openSos(EmergencyIncident incident) {
        if(!properties.isEnabled()||incident.getRobot()==null)return null;
        if(incident.isRoutedSos()&&incident.getSosResponder()==null)return null;
        var robot=incident.getRobot();
        if(!events.isListening(robot.getRobotId()))return null;
        var current=sessions.findByActiveRobotId(robot.getRobotId());
        if(current.isPresent()) {
            if(current.get().getMode()==MediaSession.Mode.ASSIST) {
                var existing=current.get();
                return existing.getIncident()!=null
                        && Objects.equals(existing.getIncident().getIncidentId(),incident.getIncidentId())
                        ? existing.getSessionId() : null;
            }
            finish(current.get(),ENDED,"SOS_PREEMPTED_MONITOR");sessions.flush();
        }
        var s=newSession(robot,incident,MediaSession.Mode.ASSIST);
        sessions.saveAndFlush(s);notifyAfterCommit(s);return s.getSessionId();
    }
    public View accept(UUID id,AccountPrincipal actor) {
        livekit.ready();var staff=staffScope.requireCurrentStaffOrAdmin(actor);
        var s=locked(id);requireRead(s,actor);requireOpen(s);
        requireSosResponder(s.getIncident(),actor);
        if(s.getStaff()!=null&&!s.getStaff().getAccountId().equals(staff.getAccountId()))
            throw new ConflictException("MEDIA_ALREADY_ACCEPTED_BY_ANOTHER_STAFF");
        if(s.getState()==RINGING) {
            requireDeviceListener(s.getRobot().getRobotId());
            s.setStaff(staff);s.setStaffTokenVersion(staff.getTokenVersion());s.setAcceptedAt(now());s.setState(CONNECTING);stopForAssist(s);notifyAfterCommit(s);
        }
        return view(s);
    }
    public Credentials staffToken(UUID id,AccountPrincipal actor) {
        var s=locked(id);requireRead(s,actor);requireOpen(s);
        requireSosResponder(s.getIncident(),actor);
        if(s.getStaff()==null||!s.getStaff().getAccountId().equals(actor.accountId()))
            throw new AccessDeniedException("Accept this session before joining");
        return credentials(s,"staff-"+actor.accountId(),false);
    }
    public Credentials deviceToken(UUID id,DevicePrincipal actor) {
        var robot=deviceScope.requireCurrentDevice(actor);var s=locked(id);
        requireOwner(s,robot.getRobotId());requireOpen(s);
        if(s.getStaff()==null)throw new BusinessRuleException("MEDIA_WAITING_FOR_STAFF");
        s.setDeviceSeenAt(now());return credentials(s,"robot-"+robot.getRobotId(),true);
    }
    public View deviceState(UUID id,String state,String reason,DevicePrincipal actor) {
        var robot=deviceScope.requireCurrentDevice(actor);var s=locked(id);requireOwner(s,robot.getRobotId());
        if(!s.open())return view(s);
        if(!s.getExpiresAt().isAfter(now())){finish(s,EXPIRED,"SESSION_EXPIRED");return view(s);}
        if(s.getStaff()==null)throw new BusinessRuleException("MEDIA_WAITING_FOR_STAFF");
        s.setDeviceSeenAt(now());
        switch(state) {
            case "CONNECTED" -> {s.setState(ACTIVE);s.setReconnectStartedAt(null);}
            case "RECONNECTING" -> {if(s.getReconnectStartedAt()==null)s.setReconnectStartedAt(now());s.setState(RECONNECTING);}
            case "FAILED" -> finish(s,FAILED,reason==null?"DEVICE_MEDIA_FAILED":reason);
            default -> throw new BusinessRuleException("INVALID_MEDIA_DEVICE_STATE");
        }
        return view(s);
    }
    public SseEmitter deviceEvents(DevicePrincipal actor) {
        var robot=deviceScope.requireCurrentDevice(actor);
        View current=sessions.findByActiveRobotId(robot.getRobotId()).filter(s->s.getExpiresAt().isAfter(now())).map(this::view).orElse(null);
        return events.subscribe(robot.getRobotId(),Duration.between(now(),actor.expiresAt()).toMillis(),new Event(properties.isEnabled(),current));
    }
    public Event deviceCurrent(DevicePrincipal actor) {
        var robot=deviceScope.requireCurrentDevice(actor);
        return new Event(properties.isEnabled(),sessions.findByActiveRobotId(robot.getRobotId())
                .filter(s->s.getExpiresAt().isAfter(now())).map(this::view).orElse(null));
    }
    public View end(UUID id,String reason,AccountPrincipal actor) {
        var s=locked(id);requireRead(s,actor);
        if(actor.role()!=Role.ADMIN&&(s.getStaff()==null||!s.getStaff().getAccountId().equals(actor.accountId())))
            throw new AccessDeniedException("Only the assigned staff or Admin can end this call");
        if(s.open())finish(s,ENDED,reason);return view(s);
    }
    /** Separate scheduler thread; media cleanup cannot delay job/dispatch schedulers. */
    public void maintain() {
        if(!properties.isEnabled())return;
        for(var s:sessions.findTop100ByActiveRobotIdIsNotNullOrderByCreatedAtDesc()) {
            if(s.getStaff()!=null&&(s.getStaff().getStatus()!=AccountStatus.ACTIVE||s.getStaff().isMustChangePassword()
                    ||!Set.of(Role.STAFF,Role.ADMIN).contains(s.getStaff().getRole())
                    ||!Objects.equals(s.getStaffTokenVersion(),s.getStaff().getTokenVersion())))finish(s,ENDED,"STAFF_ACCESS_REVOKED");
            else if(s.getIncident()!=null&&(s.getIncident().getArchivedAt()!=null||Set.of(EmergencyIncidentStatus.CLOSED,
                    EmergencyIncidentStatus.RESOLVED,EmergencyIncidentStatus.FALSE_ALARM).contains(s.getIncident().getStatus())))
                finish(s,ENDED,"INCIDENT_CLOSED");
            else if(!s.getExpiresAt().isAfter(now()))finish(s,EXPIRED,"SESSION_EXPIRED");
            else if(s.getState()==RINGING && s.getCreatedAt().plusSeconds(properties.getRingTimeoutSec()).isBefore(now()))
                finish(s,EXPIRED,"STAFF_NOT_ANSWERED");
            else if(s.getAcceptedAt()!=null && Optional.ofNullable(s.getDeviceSeenAt()).orElse(s.getAcceptedAt())
                    .plusSeconds(properties.getDeviceTimeoutSec()).isBefore(now()))finish(s,FAILED,"DEVICE_MEDIA_TIMEOUT");
            else if(s.getReconnectStartedAt()!=null&&s.getReconnectStartedAt().plusSeconds(30).isBefore(now()))
                finish(s,FAILED,"MEDIA_RECONNECT_TIMEOUT");
        }
        for(var s:sessions.findTop20ByCleanupPendingTrueOrderByEndedAtAsc()) {
            try {livekit.deleteRoom(s);s.setCleanupPending(false);}
            catch(RuntimeException ignored) {break;} // Retry on the next pass; no token or patient data in logs.
        }
        events.heartbeat();
    }
    private Credentials credentials(MediaSession s,String identity,boolean device) {
        livekit.createRoom(s); // auto_create=false prevents a closed room being recreated with an old JWT.
        return new Credentials(properties.getUrl(),s.room(),identity,livekit.join(s,identity,device),
                s.getExpiresAt(),view(s),640,480,15,700000);
    }
    private MediaSession newSession(Robot robot,EmergencyIncident incident,MediaSession.Mode mode) {
        var s=new MediaSession();s.setRobot(robot);s.setIncident(incident);s.setActiveRobotId(robot.getRobotId());
        s.setMode(mode);s.setState(RINGING);s.setCreatedAt(now());s.setExpiresAt(now().plusSeconds(properties.getSessionMaxSec()));return s;
    }
    private void stopForAssist(MediaSession s) {
        if(s.getMode()!=MediaSession.Mode.ASSIST)return;
        // Use the normal durable cancellation pipeline; never rewrite a live Job to terminal.
        var cancellable=List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING);
        for(var candidate:jobs.findByRobotRobotIdAndStatusIn(s.getRobot().getRobotId(),
                cancellable)) {
            var job=jobs.lockById(candidate.getJobId()).orElseThrow();
            if(!cancellable.contains(job.getStatus()))continue;
            if(job.getCancelCommandId()==null) {
                job.setCancelCommandId(UUID.randomUUID());job.setCancelRequestedAt(now());job.setCancelRequestedByAccount(s.getStaff());
            }
            jobWakeups.request(job);
        }
    }
    private void requireDeviceListener(UUID robotId) {
        if(!events.isListening(robotId))throw new BusinessRuleException("MEDIA_DEVICE_NOT_CONNECTED");
    }
    private void incidentOpen(EmergencyIncident i) {
        if(i.getArchivedAt()!=null||Set.of(EmergencyIncidentStatus.RESOLVED,EmergencyIncidentStatus.CLOSED,EmergencyIncidentStatus.FALSE_ALARM).contains(i.getStatus()))
            throw new BusinessRuleException("MEDIA_INCIDENT_ALREADY_CLOSED");
    }
    private void requireOpen(MediaSession s) {
        if(!s.open()||!s.getExpiresAt().isAfter(now()))throw new BusinessRuleException("MEDIA_SESSION_CLOSED");
        if(s.getIncident()!=null)incidentOpen(s.getIncident());
    }
    private boolean canRead(MediaSession s,AccountPrincipal actor) {
        if(actor.role()==Role.ADMIN)return true;
        if(s.getIncident()!=null&&s.getIncident().isRoutedSos())
            return s.getIncident().isSosResponder(actor.accountId());
        return s.getStaff()!=null&&s.getStaff().getAccountId().equals(actor.accountId())
                || s.getIncident()!=null&&staffScope.canAccessIncident(s.getIncident(),actor);
    }
    private void requireSosResponder(EmergencyIncident incident, AccountPrincipal actor) {
        if (incident == null || !incident.isRoutedSos()) return;
        boolean allowed = incident.getSosResponder() == null ? actor.role() == Role.ADMIN
                : incident.isSosResponder(actor.accountId());
        if (!allowed) throw new AccessDeniedException("Only the designated SOS responder can take this call");
    }
    private void requireRead(MediaSession s,AccountPrincipal actor) {
        staffScope.requireCurrentStaffOrAdmin(actor);
        if(!canRead(s,actor))throw new AccessDeniedException("Media session is outside this Staff scope");
    }
    private void requireOwner(MediaSession s,UUID robot) {
        if(!s.getRobot().getRobotId().equals(robot))throw new AccessDeniedException("Media session belongs to another robot");
    }
    private MediaSession require(UUID id) {return sessions.findById(id).orElseThrow(()->new ResourceNotFoundException("Media session not found"));}
    private MediaSession locked(UUID id) {return sessions.lockById(id).orElseThrow(()->new ResourceNotFoundException("Media session not found"));}
    private Instant now(){return Instant.now(clock);}
    private View view(MediaSession s) {return new View(s.getSessionId(),s.getRobot().getRobotId(),s.getIncident()==null?null:s.getIncident().getIncidentId(),
            s.getStaff()==null?null:s.getStaff().getAccountId(),s.getMode(),s.getState(),s.getCreatedAt(),s.getExpiresAt(),s.getDeviceSeenAt(),s.getEndedAt(),s.getEndReason());}
    private void finish(MediaSession s,MediaSession.State state,String reason) {
        s.setState(state);s.setActiveRobotId(null);s.setEndedAt(now());s.setEndReason(reason);s.setCleanupPending(true);notifyAfterCommit(s);
    }
    private void notifyAfterCommit(MediaSession s) {
        UUID robot=s.getRobot().getRobotId();var event=new Event(properties.isEnabled(),view(s));
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){events.publish(robot,event);}
        });else events.publish(robot,event);
    }
}
