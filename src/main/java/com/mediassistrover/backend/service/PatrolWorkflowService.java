package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.ai.AiSensingService;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.mqtt.MqttProperties;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Reuses the regular schema 3 workflow and schedule lifecycle for bounded patrol runs. */
@Service
@RequiredArgsConstructor
public class PatrolWorkflowService {
    private static final String SERVICE = "PATROL";
    private static final String CODE_PREFIX = "WF_PATROL_";
    private static final String TASK = "PATROL";
    private static final Set<JobStatus> OCCUPIED = Set.of(
            JobStatus.QUEUED, JobStatus.ACCEPTED, JobStatus.RUNNING, JobStatus.RECOVERY_REQUIRED);

    private final WorkflowService workflows;
    private final WorkflowRepository definitions;
    private final StepRepository steps;
    private final FacilityMapRepository maps;
    private final EndpointRepository endpoints;
    private final ZoneRepository zones;
    private final JobRepository jobs;
    private final RobotMqttSessionRepository links;
    private final MappingSessionRepository mappingSessions;
    private final RobotCommandOutboxRepository commands;
    private final AiSensingProfileRepository profiles;
    private final AiSensingService ai;
    private final MqttSessionRegistry registry;
    private final MqttProperties mqtt;
    private final NavigationProfileService navigation;
    private final StaffOperationalScopePolicy staffScope;
    private final MqttWire wire;
    private final ObjectMapper json;

    public record Checkpoint(@NotNull UUID endpointId, @NotNull UUID zoneId,
                             @NotBlank @Pattern(regexp = "CROWD_DENSITY|RESTRICTED_ZONE") String sensingType) { }

    public record Create(@NotBlank @Size(max = 160) String name, @NotNull UUID mapId,
                         @NotEmpty @Size(max = 5) List<@NotNull @Valid Checkpoint> checkpoints,
                         @DecimalMin("0.05") @DecimalMax("0.60") Double maxSpeedMps,
                         @Min(15) @Max(120) Integer moveTimeoutSec,
                         @Min(1) @Max(30) Integer sensingWindowSec) { }

    public record Result(UUID workflowId, UUID mapId, int checkpointCount) { }

    @Transactional(readOnly = true)
    public GraphCreateRequest preview(Create request, AccountPrincipal actor) {
        requireAdmin(actor);
        requireMap(request.mapId());
        for (Checkpoint checkpoint : request.checkpoints()) {
            requireCheckpoint(request.mapId(), checkpoint);
        }
        return graph(request, CODE_PREFIX + wire.canonicalHash(json.valueToTree(request))
                .substring(0, 32).toUpperCase(Locale.ROOT));
    }

    @Transactional
    public Result create(Create request, AccountPrincipal actor, RequestMetadata metadata) {
        var graph = preview(request, actor);
        // The workflow service owns validation, immutable publication and audit.
        var reusable = definitions.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                graph.workflow().workflowCode(), WorkflowStatus.PUBLISHED).orElse(null);
        if (reusable != null && isPatrol(reusable)) {
            return new Result(reusable.getWorkflowId(), request.mapId(), request.checkpoints().size());
        }
        if (definitions.existsByWorkflowCodeIgnoreCaseAndVersionNo(graph.workflow().workflowCode(), 1)) {
            graph = graph(request, CODE_PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        }
        UUID id = workflows.createGraph(graph, actor.accountId(), metadata).workflow().workflowId();
        workflows.validate(id, actor.accountId(), metadata);
        workflows.publish(id, actor.accountId(), metadata);
        return new Result(id, request.mapId(), request.checkpoints().size());
    }

    GraphCreateRequest graph(Create request, String code) {
        double speed = request.maxSpeedMps() == null ? .25 : request.maxSpeedMps();
        int moveTimeout = request.moveTimeoutSec() == null ? 60 : request.moveTimeoutSec();
        int window = request.sensingWindowSec() == null ? 12 : request.sensingWindowSec();
        List<GraphStepRequest> actions = new ArrayList<>();
        for (Checkpoint checkpoint : request.checkpoints()) {
            int number = actions.size() / 2 + 1;
            actions.add(step(StepType.MOVE, "MOVE_" + number, "Move to checkpoint " + number,
                    actions.size() + 1, moveTimeout, json.createObjectNode().put("mode", SERVICE)
                            .put("map_id", request.mapId().toString())
                            .put("endpoint_id", checkpoint.endpointId().toString()).put("max_speed_mps", speed)));
            actions.add(step(StepType.EXECUTE_SENSING, "SENSE_" + number, "Observe checkpoint " + number,
                    actions.size() + 1, window + 10, json.createObjectNode().put("zone_id", checkpoint.zoneId().toString())
                            .put("sensing_type", checkpoint.sensingType()).put("window_sec", window)));
        }
        List<GraphTransitionRequest> edges = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            String from = actions.get(i).stepCode();
            boolean last = i + 1 == actions.size();
            edges.add(new GraphTransitionRequest(from, last ? null : actions.get(i + 1).stepCode(),
                    TransitionOutcome.SUCCESS, null, 1, true, last ? TerminalStatus.SUCCEEDED : null));
            edges.add(new GraphTransitionRequest(from, null, TransitionOutcome.FAILURE, null, 1, true, TerminalStatus.FAILED));
            // Orchestration enters RECOVERY_REQUIRED separately if physical stop is unconfirmed.
            edges.add(new GraphTransitionRequest(from, null, TransitionOutcome.TIMEOUT, null, 1, true, TerminalStatus.FAILED));
        }
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties").putObject("map_id").put("type", "string").put("format", "uuid");
        schema.putArray("required").add("map_id");
        var defaults = json.createObjectNode().put("map_id", request.mapId().toString());
        int duration = request.checkpoints().size() * (moveTimeout + window + 10) + 30;
        var workflow = new WorkflowUpsertRequest(code, 1, request.name(), SERVICE,
                "Finite patrol with one sensing window after each successful MOVE", schema, json.createObjectNode(),
                defaults, json.createObjectNode(), json.createArrayNode(), duration, actions.size(), 3, 3);
        return new GraphCreateRequest(workflow, new GraphRequest(actions.getFirst().stepCode(),
                List.of(new TaskRequest(TASK, "Patrol checkpoints", 1, null, json.createObjectNode())), actions, edges));
    }

    private GraphStepRequest step(StepType type, String code, String name, int sequence, int timeout, JsonNode params) {
        return new GraphStepRequest(TASK, type, code, name, sequence, null, params, json.createObjectNode(),
                timeout, 1, 0, json.createArrayNode(), 1, StepAuditLevel.FULL);
    }

    public static boolean isPatrol(Workflow workflow) {
        return SERVICE.equals(workflow.getServiceType()) && workflow.getWorkflowCode() != null
                && workflow.getWorkflowCode().startsWith(CODE_PREFIX);
    }

    public void validateSchedule(Workflow workflow, Robot robot, int priority, Integer intervalSec, JsonNode inputs) {
        if (!isPatrol(workflow)) return;
        if (robot == null || !mqtt.manages(robot.getRobotId())) {
            throw new BusinessRuleException("Patrol requires an explicit MQTT robot");
        }
        if (priority > 10 || (intervalSec != null && intervalSec < 60)) {
            throw new BusinessRuleException("Patrol priority must be 0..10 and interval must be at least 60 seconds");
        }
        if (!inputs.isObject() || !inputs.isEmpty()) {
            throw new BusinessRuleException("Patrol schedule inputTemplateJson must be {}; checkpoints belong to its workflow");
        }
        requireMap(mapId(workflow));
    }

    /** Caller holds the robot write lock before asking whether a scheduled run can use it. */
    public FacilityMap readyMap(Workflow workflow, Robot robot, int priority) {
        if (robot == null || robot.getStatus() != RobotStatus.IDLE || !mqtt.dispatches(StepType.MOVE)
                || !mqtt.dispatches(StepType.EXECUTE_SENSING) || !mqtt.manages(robot.getRobotId())) return null;
        UUID robotId = robot.getRobotId();
        UUID mapId = mapId(workflow);
        FacilityMap map = robot.getCurrentMap();
        if (map == null || !mapId.equals(map.getMapId()) || map.getStatus() != MapStatus.ACTIVE
                || map.getAssetHash() == null || !map.getAssetHash().equalsIgnoreCase(robot.getMapAssetHash())) return null;
        if (!jobs.findByRobotRobotIdAndStatusIn(robotId, OCCUPIED).isEmpty()
                || jobs.existsHigherPriorityQueuedForMap(mapId, priority)
                || !mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty()
                || !commands.findByRobotIdAndStateIn(robotId, List.of("PENDING", "PUBLISHING", "PUBLISHED",
                        "ACKNOWLEDGED", "STARTED", "RECOVERY_REQUIRED")).isEmpty()) return null;
        var link = links.findById(robotId).orElse(null);
        if (!registry.fresh(link) || !registry.advertises(link, "MOVE") || !registry.advertises(link, "EXECUTE_SENSING")
                || link.getHeartbeatJson() == null || !"IDLE".equals(link.getHeartbeatJson().path("status").asText())
                || !link.getHeartbeatJson().path("activeJobStepId").isNull()
                || (link.getHelloJson().path("simulation").asBoolean() && !mqtt.isAllowSimulation())) return null;
        ai.requireEnabled();
        var actions = steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId());
        if (actions.isEmpty() || actions.size() > 10 || actions.size() % 2 != 0) {
            throw new BusinessRuleException("Patrol requires 1..5 MOVE/sensing checkpoint pairs");
        }
        if (navigation.ready(robot, link, actions.getFirst().getParamsJson()) == null) return null;
        for (int i = 0; i < actions.size(); i += 2) {
            var move = actions.get(i);
            var sensing = actions.get(i + 1);
            if (move.getStepType() != StepType.MOVE || sensing.getStepType() != StepType.EXECUTE_SENSING
                    || !SERVICE.equals(move.getParamsJson().path("mode").asText())
                    || !mapId.toString().equals(move.getParamsJson().path("map_id").asText())) {
                throw new BusinessRuleException("Patrol requires MOVE on its map followed by sensing");
            }
            var checkpoint = new Checkpoint(requiredUuid(move.getParamsJson(), "endpoint_id"),
                    requiredUuid(sensing.getParamsJson(), "zone_id"), sensing.getParamsJson().path("sensing_type").asText());
            requireCheckpoint(mapId, checkpoint);
            boolean commissioned = profiles.findByRobotIdAndZoneIdAndSensingType(robotId, checkpoint.zoneId(), checkpoint.sensingType())
                    .filter(p -> p.isEnabled() && map.getAssetHash().equalsIgnoreCase(p.getMapAssetHash())
                            && map.getFrameId().equals(p.getFrameId())).isPresent();
            if (!commissioned) return null;
        }
        return map;
    }

    private UUID mapId(Workflow workflow) {
        return requiredUuid(workflow.getDefaultInputsJson(), "map_id");
    }

    private UUID requiredUuid(JsonNode data, String field) {
        try {
            return UUID.fromString(data.path(field).asText());
        } catch (IllegalArgumentException error) {
            throw new BusinessRuleException("Patrol requires a valid " + field);
        }
    }

    private FacilityMap requireMap(UUID mapId) {
        FacilityMap map = maps.findById(mapId).orElseThrow(() -> new ResourceNotFoundException("Patrol map not found"));
        if (map.getStatus() != MapStatus.ACTIVE || map.getAssetHash() == null
                || !map.getAssetHash().matches("[a-fA-F0-9]{64}") || map.getFrameId() == null || map.getFrameId().isBlank()) {
            throw new BusinessRuleException("Patrol requires an ACTIVE map with an asset hash and frame");
        }
        return map;
    }

    private void requireAdmin(AccountPrincipal actor) {
        if (staffScope.requireCurrentStaffOrAdmin(actor).getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can configure patrol workflows");
        }
    }

    private void requireCheckpoint(UUID mapId, Checkpoint checkpoint) {
        var endpoint = endpoints.findById(checkpoint.endpointId()).orElseThrow(() -> new BusinessRuleException("Patrol endpoint not found"));
        var zone = zones.findById(checkpoint.zoneId()).orElseThrow(() -> new BusinessRuleException("Patrol zone not found"));
        if (!endpoint.isActive() || !endpoint.isAccessible() || !mapId.equals(endpoint.getMap().getMapId())
                || endpoint.getYawRad() == null) {
            throw new BusinessRuleException("Patrol checkpoint needs an active accessible endpoint with heading on the patrol map");
        }
        if (!zone.isActive() || !mapId.equals(zone.getMap().getMapId()) || !AiSensingService.TYPES.contains(checkpoint.sensingType())
                || ("RESTRICTED_ZONE".equals(checkpoint.sensingType()) && zone.getZoneType() != ZoneType.KEEP_OUT)) {
            throw new BusinessRuleException("Patrol needs an active sensing zone on the same map; RESTRICTED_ZONE requires KEEP_OUT");
        }
    }
}
