package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MqttStepDispatchService {

    private final MqttProperties properties;
    private final MqttSessionRegistry registry;
    private final RobotMqttSessionRepository sessions;
    private final RobotCommandOutboxRepository commands;
    private final RobotRepository robots;
    private final StepInputContractValidator inputs;
    private final StepInputScopeValidator scope;
    private final MqttContractValidator contracts;
    private final MqttWire wire;
    private final Clock clock;

    private final com.mediassistrover.backend.service.OperationAuthorizationService
            operationAuthorization;

    private final com.mediassistrover.backend.service.NavigationProfileService
            navigationProfiles;

    private final com.mediassistrover.backend.mapping.MappingLifecycleService
            mapping;

    private final com.mediassistrover.backend.ai.AiSensingService ai;

    private final MqttRolloutService rollout;
    private final StepExecutionPolicy executionPolicy;

    public void requireActionSupported(Step step) {

        if (!executionPolicy.requiresMqtt(step)) {
            throw new BusinessRuleException(
                    "Step " + step.getStepCode() + " is backend-owned and cannot use MQTT dispatch"
            );
        }

        if (!properties.isEnabled()
                || !properties.isDispatchEnabled()
                || properties.getRobotIds().isEmpty()
                || !properties.dispatches(step.getStepType())) {

            throw new BusinessRuleException(
                    "Step "
                            + step.getStepCode()
                            + " awaits configured MQTT dispatch; keep workflow DRAFT"
            );
        }

        ObjectNode params =
                ((ObjectNode) step.getParamsJson()).deepCopy();

        params.setAll(
                (ObjectNode) step.getInputMappingJson()
        );

        /*
         * CONTROL_HARDWARE remains restricted here until
         * UNLOCK is fully backed by the authorization contract.
         *
         * This restriction does NOT prevent CONTROL_HARDWARE
         * itself from being a Pi/MQTT StepType.
         */
        if (step.getStepType() == StepType.CONTROL_HARDWARE
                && !Set.of(
                "LOCK_COMPARTMENT",
                "LOCK"
        ).contains(
                params.path("action").asText()
        )) {

            throw new BusinessRuleException(
                    "Official UNLOCK awaits Pi STAFF_VERIFIED enforcement "
                            + "and a confirmed HELLO/output contract; "
                            + "keep workflow DRAFT"
            );
        }

        if (step.getStepType() == StepType.EXECUTE_SENSING
                && "MAPPING".equals(
                params.path("sensing_type").asText()
        )) {

            mapping.requireSupported(step);
        }

        if (step.getStepType() == StepType.EXECUTE_SENSING
                && com.mediassistrover.backend.ai.AiSensingService.TYPES
                .contains(
                        params.path("sensing_type").asText()
                )) {

            ai.requireEnabled();
        }

        if (step.getStepType() == StepType.EXECUTE_SENSING
                && !Set.of(
                "ROBOT_HEALTH",
                "SIDE_CLEARANCE",
                "MAPPING",
                "CROWD_DENSITY",
                "RESTRICTED_ZONE"
        ).contains(
                params.path("sensing_type").asText()
        )) {

            throw new BusinessRuleException(
                    "Sensing action is outside the integrated Pi contract; "
                            + "keep workflow DRAFT"
            );
        }
    }

    /**
     * Safety gate while a mapping session is physically open.
     *
     * We must NOT allow arbitrary Pi commands during SLAM/mapping.
     *
     * Allowed:
     *
     * 1. EXECUTE_SENSING / MAPPING
     *    START, SAVE, STOP
     *
     * Everything else remains blocked while the mapping
     * session is potentially open.
     */
    private boolean allowedDuringOpenMapping(JobStep step) {

        if (step == null) {
            return false;
        }

        /*
         * START/SAVE/STOP physical mapping operation.
         */
        if (com.mediassistrover.backend.mapping.MappingLifecycleService
                .mapping(step)) {

            return true;
        }

        /*
         * Mapping workflow:
         *
         * START
         *   ↓
         * WAIT(EVENT: MAPPING_SAVE_REQUESTED)
         *   ↓
         * SAVE
         */
        // WAIT(EVENT) is backend-owned and can never enter MQTT dispatch.

        /*
         * Mapping workflow:
         *
         * SAVE
         *   ↓
         * COMMIT_MAP_ARTIFACT
         *   ↓
         * STOP
         *
         * DATA_SERVICE remains backend-owned while the physical mapping
         * session is open.
         */
        // DATA_SERVICE is backend-owned and can never enter MQTT dispatch.

        return false;
    }

    @Transactional
    public void dispatch(JobStep step) {

        /*
         * Mapping cleanup STOP is allowed even when rollout
         * dispatch has been halted.
         */
        if (!step.isMappingCleanup()
                && !rollout.dispatchAllowed()) {

            return;
        }

        if (!properties.isEnabled()
                || (
                !properties.isDispatchEnabled()
                        && !step.isMappingCleanup()
        )) {

            return;
        }

        Job job =
                step.getJobTask()
                        .getJob();

        /*
         * Every selected device-executed StepType must reach
         * this dispatcher.
         */
        if (!job.isBeOrchestrated()
                || job.getStatus() != JobStatus.RUNNING
                || job.getSelectedJobStep() == null
                || !job.getSelectedJobStep()
                .getJobStepId()
                .equals(step.getJobStepId())
                || step.getStatus() != JobStepStatus.PENDING
                || executionPolicy.isServer(
                step,
                step.getResolvedInputsJson()
        )) {

            throw new BusinessRuleException(
                    "Dispatch requires selected PENDING MQTT step"
            );
        }

        if (step.getInterruptionReason() != null
                || (
                job.getCancelRequestedAt() != null
                        && !step.isMappingCleanup()
        )) {

            return;
        }

        /*
         * Configuration/catalog changes after commit must not
         * reinterpret an existing operation.
         *
         * Exactly one EXECUTE_STEP operation is created
         * per JobStep.
         */
        if (commands.findByJobStepId(
                step.getJobStepId()
        ).isPresent()) {

            return;
        }

        /*
         * IMPORTANT:
         *
         * Keep the mapping safety boundary narrow: backend-owned steps do
         * not pass through this dispatcher, and only mapping robot actions
         * may execute while the mapping session is open.
         */
        if (mapping.open(job.getJobId())
                && !allowedDuringOpenMapping(step)) {

            throw new BusinessRuleException(
                    "Open mapping permits only MAPPING START/SAVE/STOP Pi actions"
            );
        }

        boolean unlock =
                "CONTROL_HARDWARE".equals(
                        step.getStepTypeSnapshot()
                )
                        && "UNLOCK_COMPARTMENT".equals(
                        step.getResolvedInputsJson()
                                .path("action")
                                .asText()
                );

        if (unlock) {

            operationAuthorization.requireBound(
                    step,
                    step.getResolvedInputsJson()
                            .path("authorization")
            );
        }

        if (!step.isMappingCleanup()) {
            requireActionSupported(
                    step.getStep()
            );
        }

        if (com.mediassistrover.backend.mapping.MappingLifecycleService
                .mapping(step)) {

            mapping.requireBound(step);
        }

        /*
         * Validate business scope using trusted BE-side data.
         */
        ObjectNode business =
                ((ObjectNode) step.getResolvedInputsJson())
                        .deepCopy();

        if (unlock) {
            business.remove("authorization");
        }

        scope.validate(
                step.getStep(),
                business,
                job
        );

        if (job.getRobot() == null
                || !properties.manages(
                job.getRobot()
                        .getRobotId()
        )) {

            throw new BusinessRuleException(
                    "Robot is not enrolled for MQTT"
            );
        }

        robots.findByIdForUpdate(
                job.getRobot()
                        .getRobotId()
        ).orElseThrow();

        RobotMqttSession session =
                sessions.lock(
                        job.getRobot()
                                .getRobotId()
                ).orElse(null);

        /*
         * No fresh HELLO + heartbeat => do not issue a command.
         * Orchestrator can retry later.
         */
        if (!registry.fresh(session)) {
            return;
        }

        if (com.mediassistrover.backend.mapping.MappingLifecycleService
                .mapping(step)
                && !session.getHelloJson()
                .path("mappingEnabled")
                .asBoolean()) {

            throw new BusinessRuleException(
                    "Pi does not enable mapping"
            );
        }

        if (session.getHelloJson()
                .path("simulation")
                .asBoolean()
                && !properties.isAllowSimulation()) {

            throw new BusinessRuleException(
                    "Simulation dispatch is disabled"
            );
        }

        /*
         * HELLO capability negotiation.
         *
         * Pi must explicitly advertise every StepType that
         * BE may dispatch.
         */
        if (!registry.advertises(session, step.getStepTypeSnapshot())) {

            throw new BusinessRuleException(
                    "Pi does not enable selected StepType"
            );
        }

        /*
         * MOVE has additional map identity protection.
         */
        if ("MOVE".equals(
                step.getStepTypeSnapshot()
        )
                && (
                job.getMap() == null
                        || job.getRobot()
                        .getCurrentMap() == null
                        || !job.getMap()
                        .getMapId()
                        .equals(
                                job.getRobot()
                                        .getCurrentMap()
                                        .getMapId()
                        )
                        || job.getMap()
                        .getAssetHash() == null
                        || !job.getMap()
                        .getAssetHash()
                        .equalsIgnoreCase(
                                Objects.toString(
                                        job.getRobot()
                                                .getMapAssetHash(),
                                        ""
                                )
                        )
        )) {

            throw new BusinessRuleException(
                    "MOVE requires matching map UUID and asset hash"
            );
        }

        String profileHash = null;

        JsonNode aiProfile =
                com.mediassistrover.backend.ai.AiSensingService
                        .ai(step)
                        ? ai.bind(step)
                        : null;

        if ("MOVE".equals(
                step.getStepTypeSnapshot()
        )) {

            profileHash =
                    navigationProfiles.ready(
                            job.getRobot(),
                            session,
                            step.getResolvedInputsJson()
                    );

            if (profileHash == null) {
                return;
            }
        }

        /*
         * Only one unresolved physical/device operation may
         * exist on a robot at a time.
         */
        if (!commands.findByRobotIdAndStateIn(
                session.getRobotId(),
                List.of(
                        "PENDING",
                        "PUBLISHING",
                        "PUBLISHED",
                        "ACKNOWLEDGED",
                        "STARTED",
                        "RECOVERY_REQUIRED"
                )
        ).isEmpty()) {

            throw new BusinessRuleException(
                    "Robot already has an unresolved Pi command"
            );
        }

        ObjectNode params =
                ((ObjectNode) step.getResolvedInputsJson())
                        .deepCopy();

        /*
         * Metadata permitted in BE templates is never sent as
         * unrelated/untrusted hardware business metadata.
         */
        if ("CONTROL_HARDWARE".equals(
                step.getStepTypeSnapshot()
        )) {

            params.remove(
                    List.of(
                            "maxAttempts",
                            "service_request_id"
                    )
            );

            if ("LOCK".equals(
                    params.path("action").asText()
            )) {

                params.put(
                        "action",
                        "LOCK_COMPARTMENT"
                );
            }
        }

        // WAIT(EVENT) is completed only by the durable backend event mechanism.
        inputs.validateMqttParams(
                step.getStep(),
                params
        );

        UUID commandId =
                UUID.randomUUID();

        Instant now =
                Instant.now(clock)
                        .truncatedTo(
                                java.time.temporal.ChronoUnit.MICROS
                        );

        ObjectNode command =
                wire.object()
                        .put(
                                "protocolVersion",
                                "1.0"
                        )
                        .put(
                                "contract",
                                "MAR-STEP-2"
                        )
                        .put(
                                "messageType",
                                "COMMAND"
                        )
                        .put(
                                "commandId",
                                commandId.toString()
                        )
                        .put(
                                "correlationId",
                                commandId.toString()
                        )
                        .put(
                                "robotId",
                                session.getRobotId()
                                        .toString()
                        )
                        .put(
                                "transportSessionId",
                                session.getTransportSessionId()
                                        .toString()
                        )
                        .put(
                                "commandType",
                                "EXECUTE_STEP"
                        )
                        .put(
                                "expiresAt",
                                now.plusSeconds(
                                                properties.getCommandTtlSec()
                                        )
                                        .toString()
                        );

        ObjectNode action =
                command.putObject("step")
                        .put(
                                "jobId",
                                job.getJobId()
                                        .toString()
                        )
                        .put(
                                "jobStepId",
                                step.getJobStepId()
                                        .toString()
                        )
                        .put(
                                "stepId",
                                step.getStep()
                                        .getStepId()
                                        .toString()
                        )
                        .put(
                                "stepCode",
                                step.getStepCodeSnapshot()
                        )
                        .put(
                                "stepType",
                                step.getStepTypeSnapshot()
                        )
                        .put(
                                "visitNo",
                                step.getVisitNo()
                        )
                        .put(
                                "operationKey",
                                step.getOperationKey()
                        )
                        .put(
                                "timeoutSec",
                                step.getTimeoutSecSnapshot()
                        );

        action.set(
                "params",
                params
        );

        /*
         * Validate the actual outbound wire payload.
         */
        contracts.command(command);

        commands.save(
                RobotCommandOutbox.builder()
                        .commandId(commandId)
                        .commandType("EXECUTE_STEP")
                        .robotId(
                                session.getRobotId()
                        )
                        .jobId(
                                job.getJobId()
                        )
                        .jobStepId(
                                step.getJobStepId()
                        )
                        .operationKey(
                                step.getOperationKey()
                        )
                        .navigationProfileHash(
                                profileHash
                        )
                        .aiProfileSnapshotJson(
                                aiProfile
                        )
                        .payloadJson(
                                command
                        )
                        .expiresAt(
                                now.plusSeconds(
                                        properties.getCommandTtlSec()
                                )
                        )
                        .state("PENDING")
                        .createdAt(now)
                        .build()
        );
    }
}
