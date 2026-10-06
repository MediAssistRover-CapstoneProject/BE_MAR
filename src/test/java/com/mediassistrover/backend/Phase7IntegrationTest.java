package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.delivery.request.DeliveryRequests.*;
import com.mediassistrover.backend.dto.delivery.response.DeliveryResponses.DeliveryResponse;
import com.mediassistrover.backend.dto.emergency.request.EmergencyRequests.*;
import com.mediassistrover.backend.dto.emergency.response.EmergencyResponses.SosResponse;
import com.mediassistrover.backend.dto.pi.request.PiEnvelope;
import com.mediassistrover.backend.dto.verification.request.AuthVerificationRequests;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class Phase7IntegrationTest extends MySqlIntegrationTest {
    @Autowired ObjectMapper objectMapper; @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accountRepository; @Autowired WorkflowRepository workflowRepository;
    @Autowired HospitalInformationRepository hospitalRepository; @Autowired FloorRepository floorRepository;
    @Autowired FacilityMapRepository mapRepository; @Autowired EndpointRepository endpointRepository;
    @Autowired RobotRepository robotRepository; @Autowired ServiceRequestRepository requestRepository;
    @Autowired JobRepository jobRepository; @Autowired JobStepRepository jobStepRepository;
    @Autowired DeliveryRequestRepository deliveryRepository; @Autowired DeliveryItemRepository itemRepository;
    @Autowired EmergencyIncidentRepository incidentRepository; @Autowired AlertRepository alertRepository;
    @Autowired AlertRecipientRepository recipientRepository; @Autowired AuthVerificationRepository verificationRepository;
    @Autowired DeliveryService deliveryService; @Autowired ServiceRequestService requestService;
    @Autowired AuthVerificationService verificationService; @Autowired EmergencyService emergencyService;
    private Account staff; private AccountPrincipal staffPrincipal;

    @BeforeEach void setup(){clean();staff=accountRepository.save(Account.builder().accountCode("STAFF-P7").username("phase7.staff").fullName("Phase 7 Staff").email("phase7.staff@example.test").role(Role.STAFF).authProvider(AuthProvider.LOCAL).passwordHash("unused").status(AccountStatus.ACTIVE).passwordChangedAt(Instant.now()).build());staffPrincipal=AccountPrincipal.fromAccount(staff);}
    @AfterEach void cleanup(){clean();}

    @Test void secureDeliveryCreatesItemsJobAndRequiresPurposeSpecificVerifications(){
        publish("DELIVERY");FacilityMap map=facility();Endpoint pickup=endpoint(map,"P7-PICKUP");Endpoint destination=endpoint(map,"P7-DEST");robot(map,"P7-DELIVERY");
        Create body=new Create(map.getMapId(),null,null,pickup.getEndpointId(),destination.getEndpointId(),null,TargetMode.ENDPOINT,"Lab Receiver","100",true,null,Instant.now().plusSeconds(3600),"Handle carefully",List.of(new Item("DOC-1","Medical document","DOCUMENT",1,"FILE",true,"Keep sealed",null)),90,"Secure delivery",objectMapper.createObjectNode());
        DeliveryResponse created=deliveryService.create(body,"delivery-p7-1",staffPrincipal,metadata());DeliveryResponse duplicate=deliveryService.create(body,"delivery-p7-1",staffPrincipal,metadata());
        assertThat(duplicate.request().serviceRequestId()).isEqualTo(created.request().serviceRequestId());assertThat(created.items()).hasSize(1);assertThat(itemRepository.count()).isEqualTo(1);
        requestService.submit(created.request().serviceRequestId(),staffPrincipal,metadata());Job job=jobRepository.findByServiceRequestServiceRequestId(created.request().serviceRequestId()).getFirst();ServiceRequest parent=requestRepository.findById(created.request().serviceRequestId()).orElseThrow();parent.setStatus(ServiceRequestStatus.IN_PROGRESS);requestRepository.saveAndFlush(parent);job.setStatus(JobStatus.RUNNING);jobRepository.saveAndFlush(job);
        JobStep sender=authStep(job,"AUTH_SENDER");sender.setStatus(JobStepStatus.RUNNING);sender.setStartedAt(Instant.now());jobStepRepository.saveAndFlush(sender);var senderGrant=verificationService.create(job.getJobId(),new AuthVerificationRequests.Create(sender.getJobStepId(),AuthVerificationMethod.STAFF_CONFIRMATION,AuthVerificationPurpose.DELIVERY_SENDER,300),staffPrincipal,metadata());
        DeliveryResponse picked=deliveryService.confirmPickup(parent.getServiceRequestId(),new Confirm(senderGrant.verificationId(),"Loaded"),staffPrincipal,metadata());assertThat(picked.pickedUpAt()).isNotNull();assertThat(picked.senderAccountId()).isEqualTo(staff.getAccountId());
        JobStep receiver=authStep(job,"AUTH_RECEIVER");receiver.setStatus(JobStepStatus.RUNNING);receiver.setStartedAt(Instant.now());jobStepRepository.saveAndFlush(receiver);var receiverGrant=verificationService.create(job.getJobId(),new AuthVerificationRequests.Create(receiver.getJobStepId(),AuthVerificationMethod.STAFF_CONFIRMATION,AuthVerificationPurpose.DELIVERY_RECEIVER,300),staffPrincipal,metadata());
        DeliveryResponse delivered=deliveryService.confirmDelivery(parent.getServiceRequestId(),new Confirm(receiverGrant.verificationId(),"Received"),staffPrincipal,metadata());assertThat(delivered.deliveredAt()).isNotNull();assertThat(delivered.receiverAccountId()).isEqualTo(staff.getAccountId());assertThat(verificationRepository.count()).isEqualTo(2);
    }

    @Test void deviceSosIsIdempotentAndIncidentAlertFollowControlledStateMachines(){
        publish("EMERGENCY");FacilityMap map=facility();Endpoint endpoint=endpoint(map,"P7-SOS");Robot robot=robot(map,"P7-SOS-ROBOT");DevicePrincipal device=new DevicePrincipal(robot.getRobotId(),robot.getRobotCode(),0,"device",Instant.now().plusSeconds(3600));
        SosPayload payload=new SosPayload(map.getMapId(),endpoint.getEndpointId(),EmergencyIncidentSeverity.CRITICAL,"Patient requests urgent assistance",BigDecimal.ONE,BigDecimal.TWO);PiEnvelope<SosPayload> first=envelope(robot,"sos-operation-1",payload,UUID.randomUUID());
        SosResponse created=emergencyService.createSos(first,device,metadata());SosResponse duplicate=emergencyService.createSos(envelope(robot,"sos-operation-1",payload,UUID.randomUUID()),device,metadata());
        assertThat(duplicate.incident().incidentId()).isEqualTo(created.incident().incidentId());assertThat(incidentRepository.count()).isEqualTo(1);assertThat(alertRepository.count()).isEqualTo(1);assertThat(jobRepository.count()).isEqualTo(1);assertThat(recipientRepository.count()).isEqualTo(1);assertThat(requestRepository.findById(created.serviceRequestId()).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.QUEUED);
        assertThat(emergencyService.acknowledge(created.incident().incidentId(),staffPrincipal,metadata()).status()).isEqualTo(EmergencyIncidentStatus.ACKNOWLEDGED);assertThat(alertRepository.findById(created.alertId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(emergencyService.start(created.incident().incidentId(),staffPrincipal,metadata()).status()).isEqualTo(EmergencyIncidentStatus.IN_PROGRESS);assertThat(emergencyService.resolve(created.incident().incidentId(),new Resolution("Staff arrived"),staffPrincipal,metadata()).status()).isEqualTo(EmergencyIncidentStatus.RESOLVED);assertThat(emergencyService.close(created.incident().incidentId(),staffPrincipal,metadata()).status()).isEqualTo(EmergencyIncidentStatus.CLOSED);assertThat(alertRepository.findById(created.alertId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.CLOSED);
    }

    private JobStep authStep(Job job,String code){return jobStepRepository.findByJobId(job.getJobId()).stream().filter(s->s.getStepCodeSnapshot().equals(code)).findFirst().orElseThrow();}
    private PiEnvelope<SosPayload> envelope(Robot robot,String operation,SosPayload payload,UUID messageId){return new PiEnvelope<>("1.0",messageId,operation,UUID.randomUUID(),robot.getRobotId(),null,null,"EMERGENCY_SOS",Instant.now(),UUID.randomUUID(),1,payload);}
    private Workflow publish(String serviceType){Workflow w=workflowRepository.findAll().stream().filter(x->x.getServiceType().equals(serviceType)).findFirst().orElseThrow();w.setStatus(WorkflowStatus.PUBLISHED);w.setAcceptsNewJobs(true);w.setDefinitionHash("c".repeat(64));w.setCompiledDefinitionJson(objectMapper.createObjectNode().put("workflow",w.getWorkflowCode()));w.setPublishedAt(Instant.now());return workflowRepository.saveAndFlush(w);}
    private Robot robot(FacilityMap map,String code){return robotRepository.saveAndFlush(Robot.builder().currentMap(map).robotCode(code).robotName(code).model("Test").protocolVersion(1).capabilitiesJson(objectMapper.createArrayNode()).status(RobotStatus.IDLE).batteryUnit(BatteryUnit.UNKNOWN).deviceTokenVersion(0).build());}
    private FacilityMap facility(){HospitalInformation h=hospitalRepository.save(HospitalInformation.builder().updatedBy(staff).infoCategory("PROFILE").title("Hospital").content("Info").languageCode("vi-VN").keywordsJson(objectMapper.createArrayNode()).status(HospitalInformationStatus.PUBLISHED).publicVisible(true).build());Floor f=floorRepository.save(Floor.builder().hospitalInformation(h).floorCode("P7-F1").floorName("Floor 1").displayOrder(1).active(true).build());return mapRepository.save(FacilityMap.builder().floor(f).mapCode("P7-MAP").versionNo(1).mapName("Map").widthM(BigDecimal.TEN).heightM(BigDecimal.TEN).resolutionMPerPixel(new BigDecimal("0.05")).originXM(BigDecimal.ZERO).originYM(BigDecimal.ZERO).originYawRad(BigDecimal.ZERO).frameId("map").mapFileUri("https://example.test/map").assetHash("d".repeat(64)).status(MapStatus.ACTIVE).build());}
    private Endpoint endpoint(FacilityMap map,String code){return endpointRepository.save(Endpoint.builder().map(map).endpointCode(code).endpointName(code).endpointType(EndpointType.ROOM_STOP).xM(BigDecimal.ONE).yM(BigDecimal.ONE).yawRad(BigDecimal.ZERO).active(true).accessible(true).publicVisible(true).build());}
    private RequestMetadata metadata(){return new RequestMetadata("127.0.0.1","test","phase7-test");}
    private void clean(){jdbc.execute("SET FOREIGN_KEY_CHECKS=0");for(String t:new String[]{"AlertRecipient","Alert","AuthVerification","DeliveryItem","DeliveryRequest","EmergencyIncident","JobEvent","JobStep","JobTask","Job","Schedule","NavigationRequest","EscortRequest","ServiceRequest","RobotLocationHistory","RobotStatusHistory","Robot","EndpointGroupMember","Endpoint","Room","EndpointGroup","Zone","Map","Floor","HospitalInformation","AuditLog","RevokedToken","RefreshToken","StaffProfile","Account"})jdbc.execute("TRUNCATE TABLE `"+t+"`");jdbc.update("UPDATE `Workflow` SET status='DRAFT',accepts_new_jobs=FALSE,compiled_definition_json=NULL,definition_hash=NULL,published_at=NULL,published_by_account_id=NULL,row_version=0");jdbc.execute("SET FOREIGN_KEY_CHECKS=1");}
}
