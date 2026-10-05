package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class JobExecutionServiceImpl implements JobExecutionService {
    private static final Set<JobStatus> ACTIVE=EnumSet.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED);
    private final WorkflowRepository workflowRepository; private final TaskRepository taskRepository;
    private final StepRepository stepRepository; private final JobRepository jobRepository;
    private final JobTaskRepository jobTaskRepository; private final JobStepRepository jobStepRepository;
    private final JobEventRepository eventRepository; private final RobotRepository robotRepository;
    private final NavigationRequestRepository navigationRepository; private final EscortRequestRepository escortRepository;
    private final DeliveryRequestRepository deliveryRepository;
    private final ObjectMapper objectMapper; private final Clock clock;

    @Override public Job createForRequest(ServiceRequest request){Workflow workflow=workflowFor(request.getRequestType());FacilityMap map=mapFor(request);Robot robot=selectRobot(request.getRequestedRobot(),map);JsonNode input=merge(workflow.getDefaultInputsJson(),request.getRequestDataJson());return create(workflow,request,null,robot,map,request.getPriority(),input,null,null);}
    @Override public Job createForSchedule(Schedule schedule,Instant fireAt,JsonNode input){if(jobRepository.existsByScheduleScheduleIdAndScheduleFireAtAndScheduleItemKey(schedule.getScheduleId(),fireAt,"DEFAULT"))return jobRepository.findAll().stream().filter(j->j.getSchedule()!=null&&j.getSchedule().getScheduleId().equals(schedule.getScheduleId())&&fireAt.equals(j.getScheduleFireAt())).findFirst().orElseThrow();Workflow w=schedule.getWorkflow();if(w.getStatus()!=WorkflowStatus.PUBLISHED||!w.isAcceptsNewJobs())throw new BusinessRuleException("Schedule workflow does not accept new jobs");Robot robot=selectRobot(schedule.getRobot(),null);return create(w,null,schedule,robot,robot==null?null:robot.getCurrentMap(),schedule.getPriority(),merge(w.getDefaultInputsJson(),input),fireAt,"DEFAULT");}

    private Job create(Workflow workflow,ServiceRequest request,Schedule schedule,Robot robot,FacilityMap map,int priority,JsonNode input,Instant fireAt,String itemKey){
        Instant now=Instant.now(clock);Job job=jobRepository.saveAndFlush(Job.builder().workflow(workflow).serviceRequest(request).schedule(schedule).robot(robot).map(map).assignedStaffAccount(request!=null&&request.getRequestedByAccount()!=null&&request.getRequestedByAccount().getRole()==Role.STAFF?request.getRequestedByAccount():null).jobCode("JOB-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT)).status(JobStatus.QUEUED).priority(priority).inputJson(input).contextJson(objectMapper.createObjectNode()).workflowHash(workflow.getDefinitionHash()).protocolVersion(1).schemaVersion(workflow.getSchemaVersion()).graphVersion(workflow.getGraphVersion()).scheduleFireAt(fireAt).scheduleItemKey(itemKey).dispatchCommandId(robot==null?null:UUID.randomUUID()).dispatchExpiresAt(robot==null?null:now.plusSeconds(60)).queuedAt(now).scheduledAt(fireAt).build());
        Map<UUID,JobTask> runtimeTasks=new HashMap<>();for(Task task:taskRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())){JobTask jt=jobTaskRepository.save(JobTask.builder().job(job).task(task).sequenceNo(task.getSequenceNo()).taskNameSnapshot(task.getTaskName()).configSnapshotJson(copy(task.getConfigJson())).status(JobTaskStatus.PENDING).build());runtimeTasks.put(task.getTaskId(),jt);}jobTaskRepository.flush();
        long seq=0;for(Step step:stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())){if(!step.isEnabled())continue;jobStepRepository.save(JobStep.builder().jobTask(runtimeTasks.get(step.getTask().getTaskId())).step(step).visitNo(1).executionSequenceNo(++seq).stepCodeSnapshot(step.getStepCode()).stepTypeSnapshot(step.getStepType().getStepTypeCode()).resolvedInputsJson(objectMapper.createObjectNode()).status(JobStepStatus.PENDING).attemptNo(1).operationKey(job.getJobId()+":"+step.getStepCode()+":1").timeoutSecSnapshot(step.getTimeoutSec()).build());}
        event(job,"JOB_QUEUED",null,JobStatus.QUEUED.name(),objectMapper.createObjectNode());return job;
    }
    private Workflow workflowFor(ServiceRequestType type){String serviceType=switch(type){case INFORMATION->"INFORMATION";case NAVIGATION->"NAVIGATION";case ESCORT->"ESCORT";case DELIVERY->"DELIVERY";case EMERGENCY_SOS->"EMERGENCY";};return workflowRepository.findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(serviceType,WorkflowStatus.PUBLISHED).orElseThrow(()->new BusinessRuleException("No published workflow accepts request type "+type));}
    private Robot selectRobot(Robot preferred,FacilityMap map){if(preferred!=null&&preferred.getStatus()==RobotStatus.IDLE&&jobRepository.findByRobotRobotIdAndStatusIn(preferred.getRobotId(),ACTIVE).isEmpty())return preferred;List<Robot> candidates=map==null?robotRepository.findByStatusOrderByRobotCodeAsc(RobotStatus.IDLE):robotRepository.findByStatusAndCurrentMapMapIdOrderByRobotCodeAsc(RobotStatus.IDLE,map.getMapId());return candidates.stream().filter(r->jobRepository.findByRobotRobotIdAndStatusIn(r.getRobotId(),ACTIVE).isEmpty()).findFirst().orElse(null);}
    private void ensureNoActive(Robot robot){if(!jobRepository.findByRobotRobotIdAndStatusIn(robot.getRobotId(),ACTIVE).isEmpty())throw new ConflictException("Robot already has an active or unreconciled Job");}
    private FacilityMap mapFor(ServiceRequest r){return switch(r.getRequestType()){case NAVIGATION->navigationRepository.findById(r.getServiceRequestId()).map(NavigationRequest::getMap).orElse(null);case ESCORT->escortRepository.findById(r.getServiceRequestId()).map(EscortRequest::getMap).orElse(null);case DELIVERY->deliveryRepository.findById(r.getServiceRequestId()).map(DeliveryRequest::getMap).orElse(null);case EMERGENCY_SOS->null;case INFORMATION->null;};}
    private JsonNode merge(JsonNode defaults,JsonNode runtime){ObjectNode result=objectMapper.createObjectNode();if(defaults!=null&&defaults.isObject())result.setAll((ObjectNode)defaults.deepCopy());if(runtime!=null&&runtime.isObject())result.setAll((ObjectNode)runtime);return result;}
    private JsonNode copy(JsonNode n){return n==null?objectMapper.createObjectNode():n.deepCopy();}
    private void event(Job job,String type,String previous,String next,JsonNode data){Instant now=Instant.now(clock);eventRepository.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).robot(job.getRobot()).producerType(JobEventProducerType.SERVER).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(type).previousStatus(previous).newStatus(next).statusScope("JOB").eventDataJson(data).occurredAt(now).receivedAt(now).persistedAt(now).build());}
}
