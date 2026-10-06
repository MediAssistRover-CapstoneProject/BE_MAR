package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.setting.request.SystemSettingRequests;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase8IntegrationTest extends MySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired RobotRepository robotRepository;
    @Autowired ServiceRequestRepository requestRepository;
    @Autowired JobRepository jobRepository;
    @Autowired WorkflowRepository workflowRepository;
    @Autowired SystemSettingService settingService;
    @Autowired RuntimeSettingService runtimeSettingService;
    @Autowired AuditQueryService auditQueryService;
    @Autowired ReportService reportService;
    @Autowired RobotService robotService;

    private Account admin;

    @BeforeEach
    void setup() {
        cleanRuntimeData();
        admin = accountRepository.save(Account.builder().accountCode("ADMIN-P8").username("phase8.admin")
                .fullName("Phase 8 Admin").email("phase8.admin@example.test").role(Role.ADMIN)
                .authProvider(AuthProvider.LOCAL).passwordHash("unused").status(AccountStatus.ACTIVE)
                .passwordChangedAt(Instant.now()).build());
    }

    @AfterEach void cleanup() { cleanRuntimeData(); }

    @Test
    void systemSettingOverridesPropertyAndEveryMutationIsAudited() {
        var created = settingService.create(new SystemSettingRequests.Create(
                        "operations.robot_offline_threshold_seconds", Map.of("value", 45),
                        SystemSettingDataType.NUMBER, "operations", "Runtime threshold", false),
                admin.getAccountId(), metadata());
        assertThat(runtimeSettingService.getLong(created.settingKey(), 30, 10, 3600)).isEqualTo(45);

        settingService.update(created.systemSettingId(), new SystemSettingRequests.Update(
                        Map.of("value", 60), SystemSettingDataType.NUMBER, "OPERATIONS", "Updated", false),
                admin.getAccountId(), metadata());
        assertThat(runtimeSettingService.getLong(created.settingKey(), 30, 10, 3600)).isEqualTo(60);
        assertThat(auditQueryService.search(null, "SystemSetting", admin.getAccountId(), null, null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);

        settingService.delete(created.systemSettingId(), admin.getAccountId(), metadata());
        assertThat(runtimeSettingService.getLong(created.settingKey(), 30, 10, 3600)).isEqualTo(30);
        assertThat(auditQueryService.search("SYSTEM_SETTING_DELETED", null, null, null, null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void overviewAggregatesStatusesAndUsesTerminalJobsAsCompletionRateDenominator() {
        Robot robot = robotRepository.save(Robot.builder().robotCode("P8-R1").robotName("Robot 1").model("Test")
                .protocolVersion(1).capabilitiesJson(objectMapper.createArrayNode()).status(RobotStatus.IDLE)
                .batteryUnit(BatteryUnit.UNKNOWN).deviceTokenVersion(0).build());
        ServiceRequest request = requestRepository.save(ServiceRequest.builder().requestCode("P8-REQ")
                .requestType(ServiceRequestType.INFORMATION).sourceType(RequestSourceType.SYSTEM).priority(10)
                .status(ServiceRequestStatus.COMPLETED).requestDataJson(objectMapper.createObjectNode())
                .idempotencyKey("phase8-request").requestedAt(Instant.now()).build());
        Workflow workflow = workflowRepository.findAll().getFirst();
        jobRepository.save(Job.builder().workflow(workflow).serviceRequest(request).robot(robot).jobCode("P8-JOB")
                .status(JobStatus.SUCCEEDED).priority(10).inputJson(objectMapper.createObjectNode())
                .contextJson(objectMapper.createObjectNode()).workflowHash("a".repeat(64)).protocolVersion(1)
                .schemaVersion(1).graphVersion(1).queuedAt(Instant.now()).endedAt(Instant.now()).build());

        var overview = reportService.getOperationsOverview();
        assertThat(overview.jobsByStatus().get(JobStatus.SUCCEEDED)).isEqualTo(1);
        assertThat(overview.robotsByStatus().get(RobotStatus.IDLE)).isEqualTo(1);
        assertThat(overview.requestsByType().get(ServiceRequestType.INFORMATION)).isEqualTo(1);
        assertThat(overview.jobCompletionRatePercent()).isEqualTo(100.0);
        assertThat(overview.jobsByStatus()).containsKeys(JobStatus.QUEUED, JobStatus.RECOVERY_REQUIRED);
    }

    @Test
    void robotOfflineSchedulerBusinessRuleUsesRuntimeSettingBeforePropertyFallback() {
        var setting = settingService.create(new SystemSettingRequests.Create(
                        "operations.robot_offline_threshold_seconds", Map.of("value", 60),
                        SystemSettingDataType.NUMBER, "OPERATIONS", "Runtime threshold", false),
                admin.getAccountId(), metadata());
        Robot robot = robotRepository.saveAndFlush(Robot.builder().robotCode("P8-OFFLINE").robotName("Offline test")
                .model("Test").protocolVersion(1).capabilitiesJson(objectMapper.createArrayNode())
                .status(RobotStatus.IDLE).batteryUnit(BatteryUnit.UNKNOWN).deviceTokenVersion(0)
                .lastHeartbeatAt(Instant.now().minusSeconds(45)).build());

        assertThat(robotService.markHeartbeatTimeoutsOffline()).isZero();
        settingService.update(setting.systemSettingId(), new SystemSettingRequests.Update(
                        Map.of("value", 20), SystemSettingDataType.NUMBER, "OPERATIONS", "Runtime threshold", false),
                admin.getAccountId(), metadata());
        assertThat(robotService.markHeartbeatTimeoutsOffline()).isEqualTo(1);
        assertThat(robotRepository.findById(robot.getRobotId()).orElseThrow().getStatus()).isEqualTo(RobotStatus.OFFLINE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobsByStatus.QUEUED").exists());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotReadAdminOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/overview"))
                .andExpect(status().isForbidden());
    }

    private RequestMetadata metadata() { return new RequestMetadata("127.0.0.1", "test", "phase8-test"); }

    private void cleanRuntimeData() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : new String[]{"AlertRecipient", "Alert", "AuthVerification", "DeliveryItem",
                "DeliveryRequest", "EmergencyIncident", "JobEvent", "JobStep", "JobTask", "Job",
                "Schedule", "NavigationRequest", "EscortRequest", "ServiceRequest", "RobotLocationHistory",
                "RobotStatusHistory", "Robot", "AuditLog", "RevokedToken", "RefreshToken", "StaffProfile",
                "SystemSetting", "Account"}) jdbc.execute("TRUNCATE TABLE `" + table + "`");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }
}
