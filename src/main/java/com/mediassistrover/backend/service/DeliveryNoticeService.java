package com.mediassistrover.backend.service;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;
@Service @RequiredArgsConstructor
public class DeliveryNoticeService {
 @org.springframework.beans.factory.annotation.Value("${app.operations.scheduler-enabled:true}") private boolean schedulingEnabled=true;
 public record Created(Job job) {}
 public record Arrived(JobStep step) {}
 @EventListener public void completed(Arrived event){arrived(event.step());}
 private final AlertService alerts;
 private final DeliveryRequestRepository deliveries;
 private final JobRepository jobs;
 private final NavigationGuidanceService guidance;
 private final ObjectMapper json;
 private final DeliveryEmailService email;
 @EventListener public void created(Created e){notice(e.job(),null,"CREATED","Có đơn giao hàng mới dành cho bạn.");}
 public void arrived(JobStep s){if(receiverMove(s)&&s.getResultJson()!=null&&s.getResultJson().path("reached").asBoolean())notice(s.getJobTask().getJob(),s,"ARRIVED","Robot đã tới. Mời bạn ra nhận hàng.");}
 private boolean receiverMove(JobStep s){return Set.of("MOVE_RECEIVER","TRANSIT").contains(s.getStepCodeSnapshot())&&"MOVE".equals(s.getStepTypeSnapshot());}
 @Scheduled(fixedDelay=500) @Transactional public void approaching(){
  if(!schedulingEnabled)return;
  for(UUID id:jobs.findRunningDeliveryIds()){
   Job job=jobs.lockById(id).orElse(null);if(job==null)continue;var s=job.getSelectedJobStep();
   if(job.getStatus()!=JobStatus.RUNNING||job.getCancelRequestedAt()!=null||job.getRobot()==null||s==null||!receiverMove(s)||s.getStatus()!=JobStepStatus.RUNNING)continue;
   var v=guidance.view(job.getRobot().getRobotId());
   if("TRACKING".equals(v.path("state").asText())&&s.getJobStepId().toString().equals(v.path("plan").path("jobStepId").asText())&&v.has("remainingCmExact")&&v.path("remainingCmExact").asDouble()<=5&&v.has("goalDistanceCmExact")&&v.path("goalDistanceCmExact").asDouble()<=5)
    notice(job,s,"APPROACHING","Robot còn khoảng 5 cm. Mời bạn ra nhận hàng.");
  }
 }
 private void notice(Job job,JobStep step,String type,String message){
  if(job.getServiceRequest()==null||job.getServiceRequest().getRequestType()!=ServiceRequestType.DELIVERY)return;
  var d=deliveries.findById(job.getServiceRequest().getServiceRequestId()).orElse(null);
  if(d==null||d.getExpectedReceiverAccount()==null)return;
  var a=d.getExpectedReceiverAccount();if(a.getStatus()!=AccountStatus.ACTIVE)return;
  var data=json.createObjectNode().put("jobId",job.getJobId().toString()).put("url","/delivery-qr.html?jobId="+job.getJobId());
  var alert=alerts.createSystemAlert("DELIVERY:"+job.getJobId()+":"+type,"DELIVERY_"+type,AlertSeverity.INFO,message,data,null,job.getServiceRequest(),step,null,false,AlertAckPolicy.ANY,null,json.createArrayNode().add(a.getAccountId().toString()));
  email.enqueue(alert,a);
 }
}
