package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mqtt.MqttRolloutService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.RobotTaskArbitrationService;
import com.mediassistrover.backend.validation.WorkflowExecutionReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolRequestAdmissionTest {
    @Test void anEmergencyForBusyRobotNeverFallsBackToAnUnrelatedIdleRobot(){
        var service=mock(JobExecutionServiceImpl.class,CALLS_REAL_METHODS);
        var rollout=mock(MqttRolloutService.class);
        var workflows=mock(WorkflowRepository.class);
        var robots=mock(RobotRepository.class);
        var readiness=mock(WorkflowExecutionReadiness.class);
        var arbitration=mock(RobotTaskArbitrationService.class);
        Map.of("rollout",rollout,"workflowRepository",workflows,"robotRepository",robots,
                "readiness",readiness,"arbitration",arbitration).forEach((field,value)->ReflectionTestUtils.setField(service,field,value));
        when(rollout.lockForRuntimeCreation()).thenReturn(new MqttRolloutService.State(MqttRolloutService.Mode.LEGACY_COMPATIBLE,false,1,Instant.now(),"test"));
        var workflow=Workflow.builder().workflowId(UUID.randomUUID()).serviceType("EMERGENCY").schemaVersion(3).build();
        when(workflows.findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc("EMERGENCY",WorkflowStatus.PUBLISHED))
                .thenReturn(Optional.of(workflow));
        var robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        when(readiness.acceptsRobot(workflow,robot)).thenReturn(true);
        when(arbitration.yieldingPatrol(robot,100)).thenReturn(false);
        var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestType(ServiceRequestType.EMERGENCY_SOS)
                .requestedRobot(robot).priority(100).requestDataJson(new ObjectMapper().createObjectNode()).build();
        assertThrows(BusinessRuleException.class,()->service.createForRequest(request));
        verify(robots,never()).findByStatusOrderByRobotCodeAsc(any());
        verify(robots,never()).findByStatusAndCurrentMapMapIdOrderByRobotCodeAsc(any(),any());
    }
}
