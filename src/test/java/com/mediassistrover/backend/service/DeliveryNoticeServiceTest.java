package com.mediassistrover.backend.service;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import java.util.*;
import static org.mockito.Mockito.*;
class DeliveryNoticeServiceTest {
 final AlertService alerts=mock(AlertService.class);final DeliveryRequestRepository deliveries=mock(DeliveryRequestRepository.class);final JobRepository jobs=mock(JobRepository.class);
 final NavigationGuidanceService guidance=mock(NavigationGuidanceService.class);final DeliveryEmailService email=mock(DeliveryEmailService.class);final ObjectMapper json=new ObjectMapper();
 final DeliveryNoticeService service=new DeliveryNoticeService(alerts,deliveries,jobs,guidance,json,email);
 Job job;JobStep step;
 @BeforeEach void setup(){var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.DELIVERY).build();
 job=Job.builder().jobId(UUID.randomUUID()).serviceRequest(request).status(JobStatus.RUNNING).robot(Robot.builder().robotId(UUID.randomUUID()).build()).build();
 step=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(JobTask.builder().job(job).build()).stepCodeSnapshot("MOVE_RECEIVER").stepTypeSnapshot("MOVE").status(JobStepStatus.RUNNING).build();job.setSelectedJobStep(step);
 when(deliveries.findById(request.getServiceRequestId())).thenReturn(Optional.of(DeliveryRequest.builder().expectedReceiverAccount(Account.builder().accountId(UUID.randomUUID()).status(AccountStatus.ACTIVE).build()).build()));
 when(jobs.findRunningDeliveryIds()).thenReturn(List.of(job.getJobId()));when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));}
 @Test void createsReceiverAlertAndEnqueuesEmail(){service.created(new DeliveryNoticeService.Created(job));verify(alerts).createSystemAlert(eq("DELIVERY:"+job.getJobId()+":CREATED"),eq("DELIVERY_CREATED"),any(),anyString(),any(),isNull(),eq(job.getServiceRequest()),isNull(),isNull(),eq(false),any(),isNull(),argThat(n->n.isArray()&&n.size()==1));verify(email).enqueue(any(),any());}
 @Test void ignoresPickupAndUnconfirmedArrival(){step.setStepCodeSnapshot("MOVE_SENDER");step.setResultJson(json.createObjectNode().put("reached",true));service.arrived(step);step.setStepCodeSnapshot("MOVE_RECEIVER");step.setResultJson(json.createObjectNode().put("reached",false));service.arrived(step);verifyNoInteractions(alerts);}
 @Test void reportsArrival(){step.setResultJson(json.createObjectNode().put("reached",true));service.arrived(step);verify(email).enqueue(any(),any());}
 @Test void usesExactFiveCentimeterThresholdAndFreshTracking(){var state=json.createObjectNode().put("state","TRACKING").put("remainingCmExact",5.1).put("goalDistanceCmExact",5);state.putObject("plan").put("jobStepId",step.getJobStepId().toString());when(guidance.view(any())).thenReturn(state);
 service.approaching();verifyNoInteractions(alerts);state.put("remainingCmExact",5);service.approaching();verify(email).enqueue(any(),any());reset(alerts,email);state.put("state","WAITING_FOR_POSE");service.approaching();verifyNoInteractions(alerts,email);}
 @Test void doesNotSendApproachAfterArrivalOrCancellation(){step.setStatus(JobStepStatus.SUCCEEDED);service.approaching();step.setStatus(JobStepStatus.RUNNING);job.setCancelRequestedAt(java.time.Instant.now());service.approaching();verifyNoInteractions(alerts,guidance);}
}
