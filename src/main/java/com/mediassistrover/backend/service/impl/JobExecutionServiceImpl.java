package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.JobExecutionService;
import com.mediassistrover.backend.service.PatrolWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional @lombok.extern.slf4j.Slf4j
public class JobExecutionServiceImpl implements JobExecutionService {
    private static final Set<JobStatus> ACTIVE=com.mediassistrover.backend.service.JobOccupancyPolicy.MOTION_OWNERS;
    private static final Set<ServiceRequestType> ROBOT_PINNED=EnumSet.of(ServiceRequestType.CROWD_MONITORING,
            ServiceRequestType.FALL_DETECTION_RESPONSE,ServiceRequestType.RESTRICTED_AREA_MONITORING,
            ServiceRequestType.AUTO_CHARGING,ServiceRequestType.NAVIGATION_RECOVERY,ServiceRequestType.ROBOT_STARTUP_STANDBY);
    private final WorkflowRepository workflowRepository; private final TaskRepository taskRepository;
    private final StepRepository stepRepository; private final JobRepository jobRepository;
    private final JobTaskRepository jobTaskRepository; private final JobStepRepository jobStepRepository;
    private final JobEventRepository eventRepository; private final RobotRepository robotRepository;
    private final NavigationRequestRepository navigationRepository; private final EscortRequestRepository escortRepository;
    private final DeliveryRequestRepository deliveryRepository;
    private final FacilityMapRepository mapRepository;
    private final ObjectMapper objectMapper; private final Clock clock;
    private final com.mediassistrover.backend.mqtt.MqttProperties mqttProperties;
    private final RequestJobInputSnapshotFactory inputSnapshots;
    private final com.mediassistrover.backend.validation.WorkflowExecutionReadiness readiness;
    private final com.mediassistrover.backend.service.OrchestrationWorkService wakeups;
    private final FloorRepository floors;
    private final MappingSessionRepository mappingSessions;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;
    private final com.mediassistrover.backend.service.NavigationSessionService navigationSessions;
    private final PatrolWorkflowService patrol;
    private final com.mediassistrover.backend.service.RobotTaskArbitrationService arbitration;
    private final com.mediassistrover.backend.validation.WorkflowInputValidator workflowInputs;

    @Override public Job createForPublished(UUID workflowId,UUID robotId,UUID mapId,UUID sessionId,JsonNode inputs,Integer priority) {
        var policy=rollout.lockForRuntimeCreation();
        Workflow workflow=workflowRepository.findById(workflowId).orElseThrow(()->new ResourceNotFoundException("Workflow not found"));
        rollout.requireSchema(policy,workflow.getSchemaVersion());
        if(workflow.getSchemaVersion()!=3 || workflow.getStatus()!=WorkflowStatus.PUBLISHED
                || !workflow.isAcceptsNewJobs() || workflow.getDefinitionHash()==null)
            throw new BusinessRuleException("Select a published schema 3 workflow that accepts new jobs");
        if(!Set.of("NAVIGATION","ROBOT_TEST","SPEECH","MAPPING","PATROL","CUSTOM").contains(workflow.getServiceType()))
            throw new BusinessRuleException("This workflow requires its service request API; delivery must bind staff, items and QR/face verification");
        var actions=stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflowId).stream().filter(Step::isEnabled).toList();
        if(actions.isEmpty())throw new BusinessRuleException("Workflow has no enabled steps");
        if(actions.stream().anyMatch(s->Set.of(StepType.AUTHENTICATE,StepType.HANDLE_PAYLOAD).contains(s.getStepType())))
            throw new BusinessRuleException("Verification and payload steps require a delivery service request");
        ObjectNode input=inputSnapshots.forWorkflow(workflow,inputs);
        input.remove(Set.of("requested_by_account_id","service_request_id","test_request_key","test_request_fingerprint","test_sequence","test_confirm_keys"));
        boolean patrolRun=PatrolWorkflowService.isPatrol(workflow);
        if("PATROL".equals(workflow.getServiceType()) && !patrolRun)
            throw new BusinessRuleException("Use a patrol definition created with the patrol workflow API");
        int rank=priority==null?(patrolRun?5:"MAPPING".equals(workflow.getServiceType())?1:50):priority;
        if(rank<0 || rank>100 || patrolRun && rank>10)throw new BusinessRuleException("Priority must be 0..100; patrol priority must be 0..10");
        boolean move=actions.stream().anyMatch(s->s.getStepType()==StepType.MOVE);
        if(patrolRun && !move)throw new BusinessRuleException("Patrol requires MOVE and sensing checkpoint pairs");
        if(mapId!=null) {
            if(input.hasNonNull("map_id") && !mapId.toString().equals(input.path("map_id").asText()))
                throw new BusinessRuleException("Selected map differs from workflow inputs");
            if(move)input.put("map_id",mapId.toString());
        }
        workflowInputs.validate(workflow.getInputSchemaJson(),input);
        if("MAPPING".equals(workflow.getServiceType())) {
            UUID floorId=inputUuid(input,"floor_id");
            if(!floors.findById(floorId).map(Floor::isActive).orElse(false))
                throw new BusinessRuleException("Mapping requires an active floor");
            Robot robot=selectPinnedRobotId(robotId);
            if(!mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty())
                throw new BusinessRuleException("Robot holds an open mapping session");
            return create(workflow,null,null,robot,null,rank,input,null,null);
        }
        if("ROBOT_TEST".equals(workflow.getServiceType()) && actions.stream().anyMatch(s->!testStepSupported(s)))
            throw new BusinessRuleException("Robot test workflow contains unsupported test steps");
        if("ROBOT_TEST".equals(workflow.getServiceType()))for(Step step:actions)
            if(step.getStepType()==StepType.WAIT && "EVENT".equals(step.getParamsJson().path("wait_type").asText())
                    && "TEST_OPERATOR_CONFIRMED".equals(step.getParamsJson().path("event_type").asText()))
                input.withObject("test_confirm_keys").put(step.getStepCode(),UUID.randomUUID().toString());
        if("SPEECH".equals(workflow.getServiceType()) && actions.stream().anyMatch(s->s.getStepType()!=StepType.INTERACT
                || !"SPEAK".equals(s.getParamsJson().path("action").asText())))
            throw new BusinessRuleException("Speech workflows accept INTERACT/SPEAK only");
        if(actions.stream().anyMatch(s->s.getStepType()==StepType.CONTROL_HARDWARE
                && (s.getInputMappingJson().has("action") || "UNLOCK_COMPARTMENT".equals(s.getParamsJson().path("action").asText())))
                && !mqttProperties.isAllowCommissioningUnlock())
            throw new BusinessRuleException("Commissioning unlock is disabled; delivery uses verified service requests");
        Robot robot=robotRepository.findByIdForUpdate(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        if(!Set.of(RobotStatus.IDLE,RobotStatus.BUSY).contains(robot.getStatus()))
            throw new BusinessRuleException("Selected robot must be online and operational before queuing a job");
        if(!mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty())
            throw new BusinessRuleException("Finish the current mapping session before running another workflow");
        FacilityMap map=null;
        if(move) {
            UUID selectedMap=inputUuid(input,"map_id");
            if(patrolRun && !selectedMap.toString().equals(workflow.getDefaultInputsJson().path("map_id").asText()))
                throw new BusinessRuleException("Patrol checkpoints belong to the workflow map; create a patrol for the other map");
            map=mapRepository.findById(selectedMap).orElseThrow(()->new ResourceNotFoundException("Map not found"));
            if(map.getStatus()!=MapStatus.ACTIVE)throw new BusinessRuleException("MOVE requires an ACTIVE map");
            if(sessionId==null)throw new BusinessRuleException("MOVE requires the current navigationSessionId");
            if(input.hasNonNull("navigation_session_id") && !sessionId.toString().equals(input.path("navigation_session_id").asText()))
                throw new BusinessRuleException("Navigation session differs from workflow inputs");
            navigationSessions.pinReadyContextForLockedRoute(robotId,sessionId,selectedMap);
            if(patrolRun) {
                patrol.validateSchedule(workflow,robot,rank,null,objectMapper.createObjectNode());
                if(patrol.readyMap(workflow,robot,rank)==null)
                    throw new BusinessRuleException("PATROL_NOT_READY: robot must be idle with matching map, fresh sensors and no pending incident or job");
            }
            input.put("navigation_session_id",sessionId.toString());
        }
        return create(workflow,null,null,robot,map,rank,input,null,null);
    }
    private UUID inputUuid(JsonNode input,String key) {
        try{return UUID.fromString(input.path(key).asText());}
        catch(IllegalArgumentException e){throw new BusinessRuleException(key+" must be a UUID");}
    }
    @Override public Job createForTest(UUID workflowId,UUID robotId,UUID mapId,UUID sessionId,JsonNode input) {
        var policy=rollout.lockForRuntimeCreation();
        Workflow workflow=workflowRepository.findById(workflowId).orElseThrow(()->new ResourceNotFoundException("Workflow not found"));
        rollout.requireSchema(policy,workflow.getSchemaVersion());
        if(workflow.getSchemaVersion()!=3 || !"ROBOT_TEST".equals(workflow.getServiceType())
                || workflow.getStatus()!=WorkflowStatus.PUBLISHED || !workflow.isAcceptsNewJobs() || workflow.getDefinitionHash()==null)
            throw new BusinessRuleException("Test job requires a published schema 3 ROBOT_TEST workflow");
        var actions=stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflowId).stream().filter(Step::isEnabled).toList();
        if(actions.isEmpty() || actions.stream().anyMatch(s->!testStepSupported(s)))
            throw new BusinessRuleException("Test job accepts MOVE, WAIT, CONTROL_HARDWARE, fixed VOICE_CAPTURE/AUDIO_LOOPBACK and INTERACT/SPEAK only");
        boolean move=actions.stream().anyMatch(s->s.getStepType()==StepType.MOVE);
        if(move){
            if(mapId==null||sessionId==null)throw new BusinessRuleException("Test MOVE requires its map/session");
        }
        Robot robot=selectPinnedRobotId(robotId);
        if(!mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty())throw new BusinessRuleException("Finish mapping before a test workflow");
        if(move)navigationSessions.pinReadyContextForLockedRoute(robotId,sessionId,mapId);
        FacilityMap map=move?mapRepository.findById(mapId).orElseThrow(()->new ResourceNotFoundException("Map not found")):null;
        return create(workflow,null,null,robot,map,50,inputSnapshots.forWorkflow(workflow,input),null,null);
    }
    static boolean testStepSupported(Step step){
        if(Set.of(StepType.MOVE,StepType.WAIT,StepType.CONTROL_HARDWARE).contains(step.getStepType()))return true;
        if(step.getStepType()==StepType.EXECUTE_SENSING)
            return Set.of("VOICE_CAPTURE","AUDIO_LOOPBACK").contains(step.getParamsJson().path("sensing_type").asText())
                &&(step.getInputMappingJson()==null||!step.getInputMappingJson().has("sensing_type"));
        return step.getStepType()==StepType.INTERACT&&step.getParamsJson()!=null
                &&"SPEAK".equals(step.getParamsJson().path("action").asText())
                &&(step.getInputMappingJson()==null||!step.getInputMappingJson().has("action"));
    }
    @Override public Job createForSpeech(UUID workflowId,UUID robotId,String text,String languageCode) {
        var policy=rollout.lockForRuntimeCreation();
        Workflow workflow=workflowRepository.findById(workflowId).orElseThrow(()->new ResourceNotFoundException("Workflow not found"));
        rollout.requireSchema(policy,workflow.getSchemaVersion());
        if(workflow.getSchemaVersion()!=3 || !"SPEECH".equals(workflow.getServiceType()) || workflow.getStatus()!=WorkflowStatus.PUBLISHED
                || !workflow.isAcceptsNewJobs() || workflow.getDefinitionHash()==null)throw new BusinessRuleException("Speech requires a published schema 3 SPEECH workflow");
        var actions=stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflowId).stream().filter(Step::isEnabled).toList();
        if(actions.isEmpty() || actions.stream().noneMatch(s->s.getStepType()==StepType.INTERACT)
                || actions.stream().anyMatch(s->s.getStepType()!=StepType.INTERACT || !"SPEAK".equals(s.getParamsJson().path("action").asText())))
            throw new BusinessRuleException("Speech jobs accept only INTERACT/SPEAK steps");
        if(text==null || text.isBlank() || text.length()>500 || !Set.of("vi","en").contains(Objects.toString(languageCode,"")))
            throw new BusinessRuleException("Speech requires 1-500 characters and languageCode vi or en");
        Robot robot=selectPinnedRobotId(robotId);
        if(!mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty())throw new BusinessRuleException("Robot holds an open mapping session");
        var input=inputSnapshots.forWorkflow(workflow,objectMapper.createObjectNode().put("speech_text",text).put("language_code",languageCode));
        return create(workflow,null,null,robot,null,1,input,null,null);
    }

    @Override public Job createForMapping(UUID workflowId,UUID robotId,UUID floorId,String mapName) {
        rollout.lockForRuntimeCreation();
        Workflow workflow=workflowRepository.findById(workflowId).orElseThrow(()->new ResourceNotFoundException("Workflow not found"));
        if(workflow.getSchemaVersion()!=3 || !"MAPPING".equals(workflow.getServiceType()) || workflow.getStatus()!=WorkflowStatus.PUBLISHED
                || !workflow.isAcceptsNewJobs() || workflow.getDefinitionHash()==null)throw new BusinessRuleException("Mapping requires a published schema 3 MAPPING workflow");
        if(mapName==null || mapName.isBlank() || mapName.length()>160 || floorId==null)throw new BusinessRuleException("Mapping requires floor_id and map_name (1-160 characters)");
        if(!floors.findById(floorId).map(Floor::isActive).orElse(false))throw new BusinessRuleException("Mapping requires an active floor");
        Robot robot=selectPinnedRobotId(robotId);
        if(!mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty())throw new BusinessRuleException("Robot holds an open mapping session");
        return create(workflow,null,null,robot,null,1,objectMapper.createObjectNode().put("floor_id",floorId.toString()).put("map_name",mapName),null,null);
    }

    private final com.mediassistrover.backend.service.StaffDutyService staffDuty;
    private final org.springframework.context.ApplicationEventPublisher deliveryEvents;
    @Override public Job createForRequest(ServiceRequest request){
        if(request.getRequestType()==ServiceRequestType.DELIVERY){
            var delivery=deliveryRepository.findById(request.getServiceRequestId()).orElseThrow();
            if(delivery.getReceiverShift()!=null){
            var shift=staffDuty.require(delivery.getReceiverShift().getShiftId(),delivery.getExpectedReceiverAccount().getAccountId());
            var target=staffDuty.target(shift,delivery.getMap().getMapId());
            if(!target.getEndpointId().equals(delivery.getDeliveryEndpoint().getEndpointId())||!shift.getRoom().getRoomId().equals(delivery.getDeliveryRoom().getRoomId()))
                throw new BusinessRuleException("Receiver room changed; create a new delivery request");
            }
        }
        var policy=rollout.lockForRuntimeCreation();Workflow workflow=workflowFor(request,policy);rollout.requireSchema(policy,workflow.getSchemaVersion());FacilityMap map=mapFor(request);
        boolean sessionPinned=request.getRequestDataJson().hasNonNull("navigation_session_id");
        if(sessionPinned){if(request.getRequestedRobot()==null||map==null)throw new BusinessRuleException("Navigation session requires its selected robot/map");
            navigationSessions.requireReadyContext(request.getRequestedRobot().getRobotId(),UUID.fromString(request.getRequestDataJson().path("navigation_session_id").asText()),map.getMapId());}
        Robot robot=selectRequestRobot(request,map,workflow,ROBOT_PINNED.contains(request.getRequestType())||sessionPinned
                || request.getRequestType()==ServiceRequestType.EMERGENCY_SOS);
        JsonNode input=inputSnapshots.forRequest(workflow,request);Job job=create(workflow,request,null,robot,map,request.getPriority(),input,null,null);job.setRecoveryOfJob(recoverySource(request));return job;}
    @Override public Job createForSchedule(Schedule schedule, Instant fireAt, JsonNode input) {
        var policy = rollout.lockForRuntimeCreation();
        var existing = jobRepository.findByScheduleScheduleIdAndScheduleFireAtAndScheduleItemKey(
                schedule.getScheduleId(), fireAt, "DEFAULT");
        if (existing.isPresent()) return existing.get();
        Workflow workflow = schedule.getWorkflow();
        rollout.requireSchema(policy, workflow.getSchemaVersion());
        if ("PATROL".equalsIgnoreCase(workflow.getServiceType()) && !PatrolWorkflowService.isPatrol(workflow)) {
            throw new BusinessRuleException("Use a patrol definition created with the patrol workflow API");
        }
        if (PatrolWorkflowService.isPatrol(workflow)) {
            if (policy.dispatchHalted() || workflow.getStatus() != WorkflowStatus.PUBLISHED
                    || !workflow.isAcceptsNewJobs() || schedule.getRobot() == null) return null;
            Robot robot = robotRepository.findByIdForUpdate(schedule.getRobot().getRobotId()).orElse(null);
            FacilityMap map;
            try {
                map = patrol.queuedMap(workflow, robot, schedule.getPriority(), schedule.getIntervalSec(), input);
            } catch (BusinessRuleException | ResourceNotFoundException error) {
                log.warn("Patrol schedule {} unavailable: {}", schedule.getScheduleId(), error.getMessage());
                return null;
            }
            // Serialize on the robot: recurring schedules coalesce into its existing patrol intent.
            if (jobRepository.existsNonterminalPatrol(robot.getRobotId())) return null;
            Job job = create(workflow, null, schedule, robot, map, schedule.getPriority(),
                    inputSnapshots.forWorkflow(workflow, input), fireAt, "DEFAULT");
            ((ObjectNode) job.getContextJson()).put("patrol_map_asset_hash", map.getAssetHash())
                    .put("patrol_map_frame_id", map.getFrameId());
            return job;
        }
        if (workflow.getStatus() != WorkflowStatus.PUBLISHED || !workflow.isAcceptsNewJobs()) {
            throw new BusinessRuleException("Schedule workflow does not accept new jobs");
        }
        Robot robot = selectRobot(schedule.getRobot(), null, workflow, schedule.getPriority());
        return create(workflow, null, schedule, robot, robot == null ? null : robot.getCurrentMap(),
                schedule.getPriority(), inputSnapshots.forWorkflow(workflow, input), fireAt, "DEFAULT");
    }

    private Job create(Workflow workflow,ServiceRequest request,Schedule schedule,Robot robot,FacilityMap map,int priority,JsonNode input,Instant fireAt,String itemKey){
        if(robot!=null && !readiness.acceptsRobot(workflow,robot))throw new BusinessRuleException("Selected robot does not support this workflow transport; enrolled MQTT robots require schema 3");
        readiness.requireReady(workflow);if(robot!=null && workflow.getSchemaVersion()==2 && mqttProperties.manages(robot.getRobotId()))throw new BusinessRuleException("Enrolled MQTT robots require a schema 3 workflow; do not switch active Job transport");Instant now=Instant.now(clock);Job job=jobRepository.saveAndFlush(Job.builder().workflow(workflow).serviceRequest(request).schedule(schedule).robot(robot).map(map).assignedStaffAccount(request!=null&&request.getRequestedByAccount()!=null&&request.getRequestedByAccount().getRole()==Role.STAFF?request.getRequestedByAccount():null).jobCode("JOB-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT)).status(JobStatus.QUEUED).priority(priority).inputJson(input).contextJson(objectMapper.createObjectNode()).workflowHash(workflow.getDefinitionHash()).protocolVersion(1).schemaVersion(workflow.getSchemaVersion()).graphVersion(workflow.getGraphVersion()).beOrchestrated(workflow.getSchemaVersion()==3).scheduleFireAt(fireAt).scheduleItemKey(itemKey).dispatchCommandId(robot==null||workflow.getSchemaVersion()==3?null:UUID.randomUUID()).dispatchExpiresAt(robot==null||workflow.getSchemaVersion()==3?null:now.plusSeconds(60)).queuedAt(now).scheduledAt(fireAt).build());
        Map<UUID,JobTask> runtimeTasks=new HashMap<>();for(Task task:taskRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())){JobTask jt=jobTaskRepository.save(JobTask.builder().job(job).task(task).sequenceNo(task.getSequenceNo()).taskNameSnapshot(task.getTaskName()).configSnapshotJson(copy(task.getConfigJson())).status(JobTaskStatus.PENDING).build());runtimeTasks.put(task.getTaskId(),jt);}jobTaskRepository.flush();
        long seq=0;for(Step step:stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())){if(!step.isEnabled())continue;jobStepRepository.save(JobStep.builder().jobTask(runtimeTasks.get(step.getTask().getTaskId())).step(step).visitNo(1).executionSequenceNo(++seq).stepCodeSnapshot(step.getStepCode()).stepTypeSnapshot(step.getStepType().getStepTypeCode()).resolvedInputsJson(objectMapper.createObjectNode()).status(JobStepStatus.PENDING).attemptNo(1).operationKey(job.getJobId()+":"+step.getStepCode()+":1").timeoutSecSnapshot(step.getTimeoutSec()).build());}
        if(com.mediassistrover.backend.service.JobOccupancyPolicy.patrol(job)) {
            job.setPatrolRepeatCount(input.path("patrol_repeat_count").asInt(1));job.setPatrolRound(1);
        }
        event(job,"JOB_QUEUED",null,JobStatus.QUEUED.name(),objectMapper.createObjectNode());deliveryEvents.publishEvent(new com.mediassistrover.backend.service.DeliveryNoticeService.Created(job));wakeups.request(job);return job;
    }
    private Robot selectRequestRobot(ServiceRequest request, FacilityMap map, Workflow workflow, boolean pinned) {
        Robot preferred=request.getRequestedRobot();
        if(preferred!=null) {
            Robot locked=robotRepository.findByIdForUpdate(preferred.getRobotId()).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
            if(!readiness.acceptsRobot(workflow,locked))throw new BusinessRuleException("Selected robot does not support this workflow transport");
            boolean sameMap=map==null || locked.getCurrentMap()!=null && map.getMapId().equals(locked.getCurrentMap().getMapId());
            if(!sameMap && pinned)throw new BusinessRuleException("Requested robot is not on the requested map");
            if(sameMap && (locked.getStatus()==RobotStatus.IDLE && jobRepository.findByRobotRobotIdAndStatusIn(locked.getRobotId(),ACTIVE).isEmpty()
                    || workflow.getSchemaVersion()==3 && arbitration.yieldingPatrol(locked,request.getPriority())))return locked;
            if(pinned)throw new BusinessRuleException("Requested robot is busy with a non-interruptible task");
        }
        if(pinned)throw new BusinessRuleException("Operational request requires a robot");
        return selectRobot(preferred,map,workflow);
    }
    private Workflow deliveryWorkflow(ServiceRequest request, com.mediassistrover.backend.mqtt.MqttRolloutService.State policy) {
        var d=deliveryRepository.findById(request.getServiceRequestId()).orElseThrow();
        deliveryAuthenticationPolicy.require(d);
        face.getObject().requireDelivery(d.getAuthenticationMethod(),d.getExpectedSenderAccount().getAccountId(),d.getExpectedReceiverAccount().getAccountId());
        return workflowRepository.findByStatusAndAcceptsNewJobsTrueOrderByWorkflowCodeAscVersionNoDesc(WorkflowStatus.PUBLISHED).stream()
            .filter(w->"DELIVERY".equalsIgnoreCase(w.getServiceType()))
            .filter(w->w.getSchemaVersion()==3 || d.getAuthenticationMethod()==DeliveryAuthenticationMethod.QR_CODE && policy.mode()==com.mediassistrover.backend.mqtt.MqttRolloutService.Mode.LEGACY_COMPATIBLE)
            .filter(w->{var auth=stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(w.getWorkflowId()).stream().filter(Step::isEnabled).filter(s->s.getStepType()==StepType.AUTHENTICATE).toList();
                return auth.stream().anyMatch(s->"DELIVERY_SENDER".equals(s.getParamsJson().path("purpose").asText()))
                    && auth.stream().anyMatch(s->"DELIVERY_RECEIVER".equals(s.getParamsJson().path("purpose").asText()))
                    && auth.stream().allMatch(s->{var params=s.getParamsJson();String purpose=params.path("purpose").asText();
                        if(!Set.of("DELIVERY_SENDER","DELIVERY_RECEIVER").contains(purpose))return false;
                        String expected="DELIVERY_SENDER".equals(purpose)?DeliveryAuthenticationMethod.QR_CODE.name():d.getAuthenticationMethod().name();
                        return expected.equals(params.path("method").asText()) && !s.getInputMappingJson().has("method") && !s.getInputMappingJson().has("purpose");});})
            .max(java.util.Comparator.comparingInt(Workflow::getVersionNo)).orElseThrow(()->new BusinessRuleException("No published delivery workflow for "+d.getAuthenticationMethod()));
    }
    private final org.springframework.beans.factory.ObjectProvider<com.mediassistrover.backend.service.FaceAuthService> face;
    private final com.mediassistrover.backend.service.DeliveryAuthenticationPolicy deliveryAuthenticationPolicy;
    private Workflow workflowFor(ServiceRequest request,com.mediassistrover.backend.mqtt.MqttRolloutService.State policy){if(request.getRequestType()==ServiceRequestType.DELIVERY)return deliveryWorkflow(request,policy);ServiceRequestType type=request.getRequestType();String serviceType=switch(type){case INFORMATION->"INFORMATION";case NAVIGATION->"NAVIGATION";case ESCORT->"ESCORT";case DELIVERY->"DELIVERY";case EMERGENCY_SOS->"EMERGENCY";case CROWD_MONITORING->"CROWD_MONITORING";case FALL_DETECTION_RESPONSE->"FALL_DETECTION_RESPONSE";case RESTRICTED_AREA_MONITORING->"RESTRICTED_AREA_MONITORING";case AUTO_CHARGING->"AUTO_CHARGING";case NAVIGATION_RECOVERY->"NAVIGATION_RECOVERY";case ROBOT_STARTUP_STANDBY->"ROBOT_STARTUP_STANDBY";};Workflow selected=type==ServiceRequestType.NAVIGATION?navigationRepository.findById(request.getServiceRequestId()).map(NavigationRequest::getWorkflow).orElse(null):null;if(selected!=null){if(selected.getStatus()!=WorkflowStatus.PUBLISHED||!selected.isAcceptsNewJobs()||!serviceType.equalsIgnoreCase(selected.getServiceType()))throw new BusinessRuleException("Selected workflow is not compatible with request type "+type);return selected;}return (policy.mode()==com.mediassistrover.backend.mqtt.MqttRolloutService.Mode.LEGACY_COMPATIBLE?workflowRepository.findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(serviceType,WorkflowStatus.PUBLISHED):workflowRepository.findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueAndSchemaVersionOrderByVersionNoDesc(serviceType,WorkflowStatus.PUBLISHED,3)).orElseThrow(()->new BusinessRuleException("No published workflow accepts request type "+type));}
    private Robot selectRobot(Robot preferred,FacilityMap map,Workflow workflow){
        return selectRobot(preferred,map,workflow,-1);
    }
    private Robot selectRobot(Robot preferred,FacilityMap map,Workflow workflow,int priority){
        if(preferred!=null){
            Robot locked=robotRepository.findByIdForUpdate(preferred.getRobotId()).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+preferred.getRobotId()));
            if(!readiness.acceptsRobot(workflow,locked))throw new BusinessRuleException("Selected robot does not support this workflow transport");
            if(locked.getStatus()==RobotStatus.IDLE && (map==null || locked.getCurrentMap()!=null && map.getMapId().equals(locked.getCurrentMap().getMapId())) && jobRepository.findByRobotRobotIdAndStatusIn(locked.getRobotId(),ACTIVE).isEmpty())return locked;
            if(workflow.getSchemaVersion()==3 && priority>=0 && (map==null || locked.getCurrentMap()!=null && map.getMapId().equals(locked.getCurrentMap().getMapId()))
                    && arbitration.yieldingPatrol(locked,priority))return locked;
        }
        List<Robot> candidates=map==null?robotRepository.findByStatusOrderByRobotCodeAsc(RobotStatus.IDLE):robotRepository.findByStatusAndCurrentMapMapIdOrderByRobotCodeAsc(RobotStatus.IDLE,map.getMapId());
        return candidates.stream().filter(r->readiness.acceptsRobot(workflow,r))
                .map(r->robotRepository.findByIdForUpdate(r.getRobotId()).orElseThrow())
                .filter(r->r.getStatus()==RobotStatus.IDLE && jobRepository.findByRobotRobotIdAndStatusIn(r.getRobotId(),ACTIVE).isEmpty()).findFirst().orElse(null);
    }
    private Robot selectPinnedRobot(Robot requested){
        if(requested==null)throw new BusinessRuleException("Operational request requires a robot");
        return selectPinnedRobotId(requested.getRobotId());
    }
    private Robot selectPinnedRobotId(UUID robotId){
        // First load must hold the write lock: a heartbeat can increment @Version
        // between an unlocked findById and upgrading that same managed entity.
        Robot locked=robotRepository.findByIdForUpdate(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+robotId));
        if(locked.getStatus()!=RobotStatus.IDLE)throw new BusinessRuleException("Operational request requires the selected robot to be IDLE");
        ensureNoActive(locked);return locked;
    }
    private void ensureNoActive(Robot robot){if(!jobRepository.findByRobotRobotIdAndStatusIn(robot.getRobotId(),ACTIVE).isEmpty())throw new ConflictException("Robot already has an active or unreconciled Job");}
    private FacilityMap mapFor(ServiceRequest r){return switch(r.getRequestType()){case NAVIGATION->navigationRepository.findById(r.getServiceRequestId()).map(NavigationRequest::getMap).orElse(null);case ESCORT->escortRepository.findById(r.getServiceRequestId()).map(EscortRequest::getMap).orElse(null);case DELIVERY->deliveryRepository.findById(r.getServiceRequestId()).map(DeliveryRequest::getMap).orElse(null);case CROWD_MONITORING,RESTRICTED_AREA_MONITORING,NAVIGATION_RECOVERY->mapFromData(r.getRequestDataJson());case EMERGENCY_SOS,INFORMATION,FALL_DETECTION_RESPONSE,AUTO_CHARGING,ROBOT_STARTUP_STANDBY->null;};}
    private FacilityMap mapFromData(JsonNode data){
        if(data==null||!data.path("map_id").isTextual())return null;
        try{return mapRepository.findById(UUID.fromString(data.path("map_id").asText())).orElseThrow(()->new ResourceNotFoundException("Map not found"));}
        catch(IllegalArgumentException ex){throw new BusinessRuleException("map_id must be a UUID");}
    }
    private Job recoverySource(ServiceRequest request){
        if(request.getRequestType()!=ServiceRequestType.NAVIGATION_RECOVERY)return null;
        JsonNode value=request.getRequestDataJson()==null?null:request.getRequestDataJson().get("recovery_of_job_id");
        if(value==null||!value.isTextual())throw new BusinessRuleException("recovery_of_job_id is required");
        try{return jobRepository.findById(UUID.fromString(value.asText())).orElseThrow(()->new ResourceNotFoundException("Recovery source Job not found"));}
        catch(IllegalArgumentException ex){throw new BusinessRuleException("recovery_of_job_id must be a UUID");}
    }
    private JsonNode copy(JsonNode n){return n==null?objectMapper.createObjectNode():n.deepCopy();}
    private void event(Job job,String type,String previous,String next,JsonNode data){Instant now=Instant.now(clock);eventRepository.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).robot(job.getRobot()).producerType(JobEventProducerType.SERVER).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(type).previousStatus(previous).newStatus(next).statusScope("JOB").eventDataJson(data).occurredAt(now).receivedAt(now).persistedAt(now).build());}
}
