package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.robot.response.RobotResponses.BatteryResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Measured charge, episode policy, and physical return are deliberately separate durable facts. */
@Service @RequiredArgsConstructor
public class RobotBatteryPolicyService {
    private final RobotBatteryPolicyRepository policies;
    private final RobotRepository robots;
    private final RobotBatteryService batteries;
    private final JobRepository jobs;
    private final EndpointRepository endpoints;
    private final VoiceInteractionRepository voices;
    private final MappingSessionRepository mapping;
    private final NavigationSessionService navigation;
    private final ObjectProvider<RobotTaskArbitrationService> arbitration;
    private final ObjectProvider<JobExecutionService> execution;
    private final JobCancellationService cancellations;
    private final DeliveryReleasePolicy delivery;
    private final BatteryReturnWorkflowService workflow;
    private final BatteryAlertService alerts;
    private final PatrolInterruptionEvents interruptions;
    private final PlatformTransactionManager transactions;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;
    private final com.mediassistrover.backend.mapping.MapBundleService bundles;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private RobotCommandOutboxRepository warningCommands;
    @Value("${app.battery.low-percent:10}") private int lowThreshold=10;
    @Value("${app.battery.critical-percent:5}") private int criticalThreshold=5;
    @Value("${app.battery.rearm-percent:15}") private int rearmThreshold=15;
    @Value("${app.battery.confirmation-sec:5}") private int confirmationSec=5;

    public record Charger(UUID endpointId,String endpointCode,String endpointName,UUID mapId) { }
    public record ReturnRequest(UUID endpointId) { }
    public record TestRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percent,Boolean dryRun,
                              UUID endpointId,@Min(15) @Max(300) Integer ttlSec) { }
    public record Status(UUID robotId,String state,UUID episodeId,BigDecimal measuredPercent,boolean measuredFresh,
                         int lowThreshold,int criticalThreshold,int rearmThreshold,boolean foregroundBlocked,
                         UUID returnJobId,UUID chargerEndpointId,String blockedReason,Instant lastEvaluatedAt,
                         boolean testMode,Instant testExpiresAt,boolean dryRun,BigDecimal requestedPercent,
                         boolean wouldAlert,boolean wouldReturn,List<Charger> eligibleChargers,UUID warningJobId) { }
    private static final RequestMetadata SYSTEM=new RequestMetadata(null,"battery-policy",null);
    private static final Set<JobStatus> TERMINAL=Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED);

    @Transactional(readOnly=true) public Status current(UUID robotId) {
        Robot robot=required(robotId);return view(robot,policies.findById(robotId).orElse(null),false,null,null);
    }
    @Transactional(readOnly=true) public List<Charger> chargers(UUID robotId) {return chargerRows(required(robotId)).stream().map(this::charger).toList();}

    @Transactional(readOnly=true) public boolean foregroundBlocked(UUID robotId) {
        var row=policies.findById(robotId).orElse(null);
        if(row!=null && (row.isCriticalLatched() || row.isReturnRequested()))return true;
        // Close the admission/publication race before the scheduled evaluator commits its episode.
        BatteryResponse sample=batteries.current(robotId);
        return usable(sample) && sample.batteryPercent().compareTo(BigDecimal.valueOf(critical()))<=0;
    }
    @Transactional(readOnly=true) public boolean publicationBlocked(Job job) {
        if(job==null || job.getRobot()==null)return false;
        if(isReturn(job)) {
            RobotBatteryPolicy policy=policies.findById(job.getRobot().getRobotId()).orElse(null);
            if(policy==null || !job.getJobId().equals(policy.getReturnJobId()) && !job.getJobId().equals(policy.getWarningJobId()))return true;
            if("TEST_EXPIRED".equals(policy.getReturnSource()) || "TEST".equals(policy.getReturnSource()) && !testActive(policy))return true;
            // Incident, cargo, mapping and assisted media facts can change after admission.
            if(arbitration.getObject().robotEventHeld(job.getRobot().getRobotId()))return true;
            if(!mapping.findByRobotIdAndPotentiallyOpenTrue(job.getRobot().getRobotId()).isEmpty())return true;
            if(jobs.findByRobotRobotIdAndStatusIn(job.getRobot().getRobotId(),JobOccupancyPolicy.NON_TERMINAL).stream()
                    .filter(other->!job.getJobId().equals(other.getJobId()))
                    .anyMatch(other->other.stopOutstanding() || other.getStatus()==JobStatus.RECOVERY_REQUIRED || delivery.requiresReconciliation(other)))return true;
            if(BatteryReturnWorkflowService.WARNING_CODE.equalsIgnoreCase(job.getWorkflow().getWorkflowCode()))return false;
            if("AUTOMATIC".equals(policy.getReturnSource()) && !usable(batteries.current(job.getRobot().getRobotId())))return true;
            Endpoint target=policy.getChargerEndpointId()==null?null:endpoints.findById(policy.getChargerEndpointId()).orElse(null);
            return target==null || target.getEndpointType()!=EndpointType.CHARGER || !target.isActive() || !target.isAccessible()
                    || target.getMap().getStatus()!=MapStatus.ACTIVE || job.getMap()==null
                    || !job.getMap().getMapId().equals(target.getMap().getMapId());
        }
        return foregroundBlocked(job.getRobot().getRobotId());
    }
    /** The existing MQTT expiresAt fence also bounds delayed delivery of an explicit test command. */
    @Transactional(readOnly=true) public Instant commandDeadline(Job job) {
        if(job==null || job.getRobot()==null || !isReturn(job))return null;
        var row=policies.findById(job.getRobot().getRobotId()).orElse(null);
        return row!=null && "TEST".equals(row.getReturnSource()) && testActive(row)
                && (job.getJobId().equals(row.getWarningJobId()) || job.getJobId().equals(row.getReturnJobId()))
                ?row.getTestExpiresAt():null;
    }
    public static boolean isReturn(Job job) {
        return job!=null && job.getServiceRequest()!=null && job.getServiceRequest().getRequestType()==ServiceRequestType.AUTO_CHARGING
                && job.getWorkflow()!=null && Set.of(BatteryReturnWorkflowService.CODE,BatteryReturnWorkflowService.WARNING_CODE)
                    .contains(job.getWorkflow().getWorkflowCode());
    }

    /** Distinct capture timestamps, not heartbeat count, debounce critical and rearm decisions. */
    @Transactional public void observe(UUID robotId) {
        Robot robot=robots.findByIdForUpdate(robotId).orElse(null);if(robot==null || robot.getStatus()==RobotStatus.RETIRED)return;
        RobotBatteryPolicy row=row(robotId);Instant now=clock.instant();BatteryResponse sample=batteries.current(robotId);
        row.setLastEvaluatedAt(now);
        if(row.getTestExpiresAt()!=null && !row.getTestExpiresAt().isAfter(now)) {
            row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);
            // An expired test may not start another physical step. Its existing operations settle via CANCEL_STEP.
            row.setCriticalLatched(false);
            if(returnActive(row)){row.setReturnSource("TEST_EXPIRED");row.setReturnRequested(true);row.setState("WAITING_FOR_STOP");}
            else reset(row);
        }
        if(!usable(sample) || sample.capturedAt()==null) {
            // A sensor gap breaks confirmation; absence never becomes zero percent or recovery.
            row.setCriticalSince(null);row.setRearmSince(null);return;
        }
        if(row.getLastSampleAt()!=null && !sample.capturedAt().isAfter(row.getLastSampleAt()))return;
        row.setLastSampleAt(sample.capturedAt());BigDecimal percent=sample.batteryPercent();
        if(percent.compareTo(BigDecimal.valueOf(low()))<=0) {
            episode(row);if(!row.isLowNotified()) {
                alerts.raise(robot,row.getEpisodeId(),"LOW",percent,"Estimated battery at or below warning threshold");row.setLowNotified(true);
            }
            if(!row.isReturnRequested() && !row.isCriticalLatched())row.setState("LOW");
        }
        if(percent.compareTo(BigDecimal.valueOf(critical()))<=0) {
            row.setRearmSince(null);
            if(row.getCriticalSince()==null)row.setCriticalSince(sample.capturedAt());
            else if(!sample.capturedAt().isBefore(row.getCriticalSince().plusSeconds(confirmSeconds()))) {
                episode(row);row.setCriticalLatched(true);row.setReturnRequested(true);
                row.setReturnSource("AUTOMATIC");
                row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);
                if(row.getReturnJobId()==null && !"FAILED".equals(row.getState()))row.setState("RETURN_REQUESTED");
                if(!row.isCriticalNotified()) {
                    alerts.raise(robot,row.getEpisodeId(),"CRITICAL",percent,"Critical battery confirmed; stop existing work before return to charger");row.setCriticalNotified(true);
                }
            }
        } else row.setCriticalSince(null);
        if(!testActive(row) && percent.compareTo(BigDecimal.valueOf(rearm()))>=0) {
            if(row.getRearmSince()==null)row.setRearmSince(sample.capturedAt());
            else if(!sample.capturedAt().isBefore(row.getRearmSince().plusSeconds(confirmSeconds())) && !returnActive(row)
                    && (row.isCriticalLatched() || !row.isReturnRequested()))reset(row);
        } else row.setRearmSince(null);
    }

    @Transactional public void requestReturn(UUID robotId,ReturnRequest request,UUID actorId,RequestMetadata metadata) {
        // Match cancellation/orchestration lock order: every Job before Robot.
        jobs.lockRobotJobs(robotId,JobOccupancyPolicy.NON_TERMINAL);
        Robot robot=robots.findByIdForUpdate(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        RobotBatteryPolicy row=row(robotId);
        if(returnActive(row) || "ARRIVED".equals(row.getState()) && row.isReturnRequested())return;
        if(request!=null && request.endpointId()!=null)requireCharger(robot,request.endpointId());
        if(row.getEpisodeId()==null || "FAILED".equals(row.getState()))newEpisode(row);
        row.setChargerEndpointId(request==null?null:request.endpointId());row.setReturnJobId(null);
        row.setReturnRequested(true);row.setState("RETURN_REQUESTED");row.setBlockedReason(null);row.setLastEvaluatedAt(clock.instant());
        row.setReturnSource("MANUAL");row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);
        audit.record(account(actorId),"BATTERY_RETURN_REQUESTED","Robot",robotId,null,
                Map.of("episodeId",row.getEpisodeId(),"selection",request!=null && request.endpointId()!=null?request.endpointId().toString():"AUTO"),metadata);
    }

    @Transactional public Status test(UUID robotId,TestRequest request,UUID actorId,RequestMetadata metadata) {
        Robot robot=required(robotId);BigDecimal percent=request.percent();
        if(request.dryRun()==null || request.dryRun())return view(robot,policies.findById(robotId).orElse(null),true,percent,previewReason(robot,request.endpointId()));
        // Execution is explicit. The real Robot and MQTT battery fields remain untouched.
        jobs.lockRobotJobs(robotId,JobOccupancyPolicy.NON_TERMINAL);
        robot=robots.findByIdForUpdate(robotId).orElseThrow();RobotBatteryPolicy row=row(robotId);
        if(returnActive(row) || row.isCriticalLatched())throw new ConflictException("An active battery return/critical episode already exists");
        if(request.endpointId()!=null)requireCharger(robot,request.endpointId());
        newEpisode(row);row.setTestPercent(percent);row.setTestActorId(actorId);
        row.setReturnSource("TEST");
        row.setTestExpiresAt(clock.instant().plusSeconds(request.ttlSec()==null?120:Math.max(15,Math.min(300,request.ttlSec()))));
        row.setChargerEndpointId(request.endpointId());row.setLastEvaluatedAt(clock.instant());
        if(percent.compareTo(BigDecimal.valueOf(low()))<=0) {
            row.setState("LOW");alerts.raise(robot,row.getEpisodeId(),"LOW",percent,"Explicit temporary battery policy test; measured telemetry is unchanged");row.setLowNotified(true);
        }
        if(percent.compareTo(BigDecimal.valueOf(critical()))<=0) {
            row.setCriticalLatched(true);row.setReturnRequested(true);row.setState("RETURN_REQUESTED");
            alerts.raise(robot,row.getEpisodeId(),"CRITICAL",percent,"Explicit battery critical test; stop receipts and all normal navigation safety gates still apply");row.setCriticalNotified(true);
        }
        audit.record(account(actorId),"BATTERY_POLICY_TEST_EXECUTED","Robot",robotId,null,
                Map.of("testPercent",percent,"expiresAt",row.getTestExpiresAt(),"episodeId",row.getEpisodeId()),metadata);
        return view(robot,row,false,percent,null);
    }

    /** End synthetic authority only; a real critical episode always keeps its safety hold. */
    @Transactional public void endTest(UUID robotId,UUID actorId,RequestMetadata metadata) {
        jobs.lockRobotJobs(robotId,JobOccupancyPolicy.NON_TERMINAL);
        Robot robot=robots.findByIdForUpdate(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        var row=policies.findById(robotId).orElse(null);
        if(row==null || !Set.of("TEST","TEST_EXPIRED").contains(row.getReturnSource()))return;
        UUID episode=row.getEpisodeId();row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);row.setCriticalLatched(false);
        if(returnActive(row)){row.setReturnSource("TEST_EXPIRED");row.setReturnRequested(true);row.setState("WAITING_FOR_STOP");}
        else reset(row);
        audit.record(account(actorId),"BATTERY_POLICY_TEST_ENDED","Robot",robot.getRobotId(),null,
                Map.of("episodeId",episode,"awaitingStop",row.isReturnRequested()),metadata);
    }

    /** Retry entry point. Intent/alerts commit separately from optional graph preparation and motor work. */
    public void advance(UUID robotId) {
        var initial=policies.findById(robotId).orElse(null);if(initial==null || !initial.isReturnRequested())return;
        UUID workflowId=null;
        if(initial.getReturnJobId()==null && !"FAILED".equals(initial.getState())) {
            try {workflowId=initial.getWarningJobId()==null?workflow.ensureWarning():workflow.ensure();}
            catch(RuntimeException unavailable) {
                var tx=new TransactionTemplate(transactions);tx.executeWithoutResult(status->{
                    // Missing workflow/capability must never prevent the critical stop itself.
                    advanceLocked(robotId,null);
                });return;
            }
        }
        UUID prepared=workflowId;var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->advanceLocked(robotId,prepared));
    }

    private void advanceLocked(UUID robotId,UUID workflowId) {
        rollout.lockForRuntimeCreation();
        List<Job> owned=jobs.lockRobotJobs(robotId,JobOccupancyPolicy.NON_TERMINAL);
        Robot robot=robots.findByIdForUpdate(robotId).orElse(null);if(robot==null)return;
        RobotBatteryPolicy row=policies.findById(robotId).orElse(null);if(row==null || !row.isReturnRequested())return;
        row.setLastEvaluatedAt(clock.instant());
        // Also enforce the expiry here: an HTTP execution request can advance between scheduler ticks.
        if("TEST".equals(row.getReturnSource()) && !testActive(row)) {
            row.setReturnSource("TEST_EXPIRED");row.setCriticalLatched(false);
            row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);
        }
        if("TEST_EXPIRED".equals(row.getReturnSource())) {
            for(Job job:owned)if(job.getJobId().equals(row.getWarningJobId()) || job.getJobId().equals(row.getReturnJobId())) {
                if(job.getCancelRequestedAt()==null && !TERMINAL.contains(job.getStatus()))
                    cancellations.cancel(job,null,"Battery policy execution test expired",SYSTEM);
            }
            if(returnActive(row)){block(robot,row,"TEST_EXPIRED");row.setState("WAITING_FOR_STOP");}
            else reset(row);
            return;
        }
        // Finish active conversations. Their lease heartbeat returns active=false, stopping Pi voice capture.
        for(var candidate:voices.findByRobotRobotIdAndStatus(robotId,VoiceInteractionStatus.ACTIVE)) {
            var voice=voices.lockById(candidate.getVoiceInteractionId()).orElseThrow();
            if(voice.getStatus()==VoiceInteractionStatus.ACTIVE) {
                voice.setStatus(VoiceInteractionStatus.CANCELLED);voice.setEndedAt(clock.instant());voice.setLeaseExpiresAt(clock.instant());
                interruptions.release(robotId,"VOICE",voice.getVoiceInteractionId());
            }
        }
        if(row.getReturnJobId()!=null) {
            Job returning=jobs.findById(row.getReturnJobId()).orElse(null);
            if(returning==null){block(robot,row,"RETURN_JOB_MISSING");return;}
            if(returning.getStatus()==JobStatus.SUCCEEDED) {
                row.setState("ARRIVED");row.setBlockedReason(null);
                // No contact/current sensor exists: never claim CHARGING from navigation arrival.
                if(!row.isCriticalLatched())row.setReturnRequested(false);
                return;
            }
            if(TERMINAL.contains(returning.getStatus()) || returning.getStatus()==JobStatus.RECOVERY_REQUIRED) {
                row.setState("FAILED");block(robot,row,"RETURN_JOB_FAILED");row.setState("FAILED");return;
            }
            String held=holdReason(robot,owned,row.getReturnJobId());
            if(held!=null){block(robot,row,held);return;}
            row.setState(returning.getStatus()==JobStatus.QUEUED?"RETURN_REQUESTED":"RETURNING");row.setBlockedReason(null);return;
        }
        if("FAILED".equals(row.getState()))return; // A failed Pi operation needs explicit review/retry, never blind retries.
        if(row.getWarningJobId()!=null) {
            Job warning=jobs.findById(row.getWarningJobId()).orElse(null);
            boolean failedButStopped=warning!=null && failedWarningSettled(warning,robot);
            if(warning==null || warning.getStatus()==JobStatus.RECOVERY_REQUIRED || TERMINAL.contains(warning.getStatus())
                    && warning.getStatus()!=JobStatus.SUCCEEDED && !failedButStopped) {
                block(robot,row,"WARNING_JOB_FAILED");row.setState("FAILED");return;
            }
            if(warning.getStatus()!=JobStatus.SUCCEEDED && !failedButStopped) {
                String reason=holdReason(robot,owned,warning.getJobId());
                if(reason!=null)block(robot,row,reason);else {row.setState("RETURN_REQUESTED");row.setBlockedReason(null);}
                return;
            }
        }
        for(Job job:owned) {
            if(job.getCancelRequestedAt()==null && job.getStatus()!=JobStatus.RECOVERY_REQUIRED && !TERMINAL.contains(job.getStatus()))
                cancellations.cancel(job,null,"Critical battery / return to charger",SYSTEM);
        }
        String held=holdReason(robot,owned,null);
        if(held!=null) {
            block(robot,row,held);if("WAITING_FOR_PHYSICAL_STOP".equals(held))row.setState("WAITING_FOR_STOP");return;
        }
        if(workflowId==null){block(robot,row,"RETURN_WORKFLOW_UNAVAILABLE");return;}
        if(row.getWarningJobId()==null) {
            var input=json.createObjectNode().put("battery_episode_id",row.getEpisodeId().toString())
                    .put("announcement_text","MANUAL".equals(row.getReturnSource())?
                        "Tôi xin phép dừng công việc và trở về điểm sạc.":
                        "Pin của tôi sắp hết. Tôi xin phép dừng công việc và trở về điểm sạc.");
            Job warning=execution.getObject().createForBatteryWarning(workflowId,robot,input,row.getEpisodeId());
            row.setWarningJobId(warning.getJobId());row.setState("RETURN_REQUESTED");row.setBlockedReason(null);return;
        }
        if("AUTOMATIC".equals(row.getReturnSource()) && !usable(batteries.current(robotId))) {
            block(robot,row,"BATTERY_TELEMETRY_UNAVAILABLE");return;
        }
        if(activeMap(robot)==null || activeMap(robot).getStatus()!=MapStatus.ACTIVE){block(robot,row,"NO_ACTIVE_MAP");return;}
        List<Endpoint> chargers=chargerRows(robot);
        if(chargers.isEmpty()){block(robot,row,"MISSING_CHARGER");return;}
        Endpoint target=row.getChargerEndpointId()==null?chargers.getFirst():chargers.stream()
                .filter(e->e.getEndpointId().equals(row.getChargerEndpointId())).findFirst().orElse(null);
        if(target==null){block(robot,row,"CHARGER_UNAVAILABLE");return;}
        var session=navigation.current(robotId);
        if(session==null){block(robot,row,"NAVIGATION_NOT_READY");return;}
        try {
            navigation.requireReadyContext(robotId,session.getSessionId(),target.getMap().getMapId());
            bundles.requireFreePoint(target.getMap().getMapId(),target.getXM().doubleValue(),target.getYM().doubleValue());
        } catch(BusinessRuleException | ResourceNotFoundException unavailable) {block(robot,row,"NAVIGATION_NOT_READY");return;}
        row.setChargerEndpointId(target.getEndpointId());
        var input=json.createObjectNode().put("battery_episode_id",row.getEpisodeId().toString())
                .put("map_id",target.getMap().getMapId().toString()).put("endpoint_id",target.getEndpointId().toString())
                .put("navigation_session_id",session.getSessionId().toString()).put("target_mode","ENDPOINT");
        Job created=execution.getObject().createForBatteryReturn(workflowId,robot,target.getMap(),input,row.getEpisodeId());
        row.setReturnJobId(created.getJobId());row.setState("RETURN_REQUESTED");row.setBlockedReason(null);
    }

    private String holdReason(Robot robot,List<Job> owned,UUID exclude) {
        if(owned.stream().filter(j->!j.getJobId().equals(exclude)).anyMatch(delivery::requiresReconciliation))return "DELIVERY_RECOVERY_REQUIRED";
        if(owned.stream().filter(j->!j.getJobId().equals(exclude)).anyMatch(j->j.stopOutstanding() || j.getStatus()==JobStatus.RECOVERY_REQUIRED))return "WAITING_FOR_PHYSICAL_STOP";
        if(!mapping.findByRobotIdAndPotentiallyOpenTrue(robot.getRobotId()).isEmpty())return "MAPPING_ACTIVE";
        if(arbitration.getObject().robotEventHeld(robot.getRobotId()))return "INCIDENT_OR_MEDIA_HOLD";
        if(!Set.of(RobotStatus.IDLE,RobotStatus.BUSY).contains(robot.getStatus()))return "ROBOT_UNAVAILABLE";
        if(exclude==null && robot.getStatus()!=RobotStatus.IDLE)return "WAITING_FOR_PHYSICAL_STOP";
        return null;
    }
    /** A completed failed TTS call owns no motors. Unknown, interrupted or cancelled speech still requires recovery. */
    private boolean failedWarningSettled(Job warning,Robot robot) {
        if(warning.getStatus()!=JobStatus.FAILED || warning.getCancelRequestedAt()!=null || warning.stopOutstanding()
                || robot.getStatus()!=RobotStatus.IDLE || warningCommands==null)return false;
        var commands=warningCommands.findByJobIdOrderByCreatedAt(warning.getJobId());
        return commands.size()==1 && commands.stream().allMatch(command->"EXECUTE_STEP".equals(command.getCommandType())
                && command.getSendAttempts()>0 && "COMPLETED".equals(command.getState()) && command.getInterruptionReason()==null
                && command.getPayloadJson()!=null && "INTERACT".equals(command.getPayloadJson().path("step").path("stepType").asText())
                && "SPEAK".equals(command.getPayloadJson().path("step").path("params").path("action").asText())
                && com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(command,null));
    }
    private String previewReason(Robot robot,UUID endpointId) {
        List<Job> owned=jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),JobOccupancyPolicy.NON_TERMINAL);
        if(owned.stream().anyMatch(delivery::requiresReconciliation))return "DELIVERY_RECOVERY_REQUIRED";
        if(!mapping.findByRobotIdAndPotentiallyOpenTrue(robot.getRobotId()).isEmpty())return "MAPPING_ACTIVE";
        if(arbitration.getObject().robotSafetyHeld(robot.getRobotId()))return "INCIDENT_OR_MEDIA_HOLD";
        if(activeMap(robot)==null || activeMap(robot).getStatus()!=MapStatus.ACTIVE)return "NO_ACTIVE_MAP";
        if(!Set.of(RobotStatus.IDLE,RobotStatus.BUSY).contains(robot.getStatus()))return "ROBOT_UNAVAILABLE";
        var choices=chargerRows(robot);if(choices.isEmpty())return "MISSING_CHARGER";
        if(endpointId!=null && choices.stream().noneMatch(e->endpointId.equals(e.getEndpointId())))return "CHARGER_UNAVAILABLE";
        var session=navigation.current(robot.getRobotId());if(session==null)return "NAVIGATION_NOT_READY";
        try {navigation.requireReadyContext(robot.getRobotId(),session.getSessionId(),activeMap(robot).getMapId());}
        catch(BusinessRuleException unavailable){return "NAVIGATION_NOT_READY";}
        return owned.isEmpty()?null:"WAITING_FOR_PHYSICAL_STOP";
    }
    private void block(Robot robot,RobotBatteryPolicy row,String reason) {
        if(!"WAITING_FOR_PHYSICAL_STOP".equals(reason) && !reason.equals(row.getBlockedReason()))
            alerts.raise(robot,row.getEpisodeId(),"BLOCKED",effectivePercent(row,batteries.current(robot.getRobotId())),reason);
        row.setBlockedReason(reason);row.setState("BLOCKED");
    }
    private Status view(Robot robot,RobotBatteryPolicy row,boolean dryRun,BigDecimal requested,String previewReason) {
        BatteryResponse measured=batteries.current(robot.getRobotId());boolean fresh=usable(measured);
        BigDecimal test=requested!=null?requested:row!=null && testActive(row)?row.getTestPercent():null;
        return new Status(robot.getRobotId(),row==null?"NORMAL":row.getState(),row==null?null:row.getEpisodeId(),
                fresh?measured.batteryPercent():null,fresh,low(),critical(),rearm(),
                row!=null && (row.isCriticalLatched() || row.isReturnRequested()) || fresh && measured.batteryPercent().compareTo(BigDecimal.valueOf(critical()))<=0,
                row==null?null:row.getReturnJobId(),row==null?null:row.getChargerEndpointId(),dryRun?previewReason:row==null?null:row.getBlockedReason(),
                row==null?null:row.getLastEvaluatedAt(),row!=null && testActive(row),row==null?null:row.getTestExpiresAt(),dryRun,test,
                test!=null && test.compareTo(BigDecimal.valueOf(low()))<=0,test!=null && test.compareTo(BigDecimal.valueOf(critical()))<=0,
                chargerRows(robot).stream().map(this::charger).toList(),row==null?null:row.getWarningJobId());
    }
    private Robot required(UUID id){return robots.findById(id).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+id));}
    private RobotBatteryPolicy row(UUID id){return policies.findById(id).orElseGet(()->{var value=new RobotBatteryPolicy();value.setRobotId(id);return policies.save(value);});}
    private Account account(UUID id){return accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Account not found"));}
    private FacilityMap activeMap(Robot robot){var session=navigation.current(robot.getRobotId());return session==null?robot.getCurrentMap():session.getMap();}
    private List<Endpoint> chargerRows(Robot robot) {
        FacilityMap map=activeMap(robot);if(map==null || map.getStatus()!=MapStatus.ACTIVE)return List.of();
        return endpoints.findByMapMapIdOrderByEndpointCodeAsc(map.getMapId()).stream()
                .filter(e->e.getEndpointType()==EndpointType.CHARGER && e.isActive() && e.isAccessible()).toList();
    }
    private Endpoint requireCharger(Robot robot,UUID id){return chargerRows(robot).stream().filter(e->id.equals(e.getEndpointId())).findFirst()
            .orElseThrow(()->new BusinessRuleException("CHARGER_UNAVAILABLE: choose an active accessible CHARGER endpoint on the current ACTIVE map"));}
    private Charger charger(Endpoint e){return new Charger(e.getEndpointId(),e.getEndpointCode(),e.getEndpointName(),e.getMap().getMapId());}
    private boolean usable(BatteryResponse sample){return sample!=null && sample.fresh() && "ESTIMATED".equals(sample.estimateStatus()) && sample.batteryPercent()!=null;}
    private boolean testActive(RobotBatteryPolicy row){return row.getTestPercent()!=null && row.getTestExpiresAt()!=null && row.getTestExpiresAt().isAfter(clock.instant());}
    private BigDecimal effectivePercent(RobotBatteryPolicy row,BatteryResponse measured){return testActive(row)?row.getTestPercent():usable(measured)?measured.batteryPercent():null;}
    private boolean returnActive(RobotBatteryPolicy row){return active(row.getReturnJobId()) || active(row.getWarningJobId());}
    private boolean active(UUID jobId){return jobId!=null && jobs.findById(jobId).map(j->!TERMINAL.contains(j.getStatus()) || j.stopOutstanding()).orElse(false);}
    private void episode(RobotBatteryPolicy row){if(row.getEpisodeId()==null)newEpisode(row);}
    private void newEpisode(RobotBatteryPolicy row){reset(row);row.setEpisodeId(UUID.randomUUID());}
    private void reset(RobotBatteryPolicy row){row.setState("NORMAL");row.setEpisodeId(null);row.setLowNotified(false);row.setCriticalNotified(false);row.setCriticalLatched(false);
        row.setReturnRequested(false);row.setCriticalSince(null);row.setRearmSince(null);row.setReturnJobId(null);row.setWarningJobId(null);row.setReturnSource("AUTOMATIC");
        row.setTestPercent(null);row.setTestExpiresAt(null);row.setTestActorId(null);row.setChargerEndpointId(null);row.setBlockedReason(null);}
    private int critical(){return Math.max(1,Math.min(criticalThreshold,98));}
    private int low(){return Math.max(critical()+1,Math.min(lowThreshold,99));}
    private int rearm(){return Math.max(low()+1,Math.min(rearmThreshold,100));}
    private int confirmSeconds(){return Math.max(1,Math.min(confirmationSec,30));}
}
