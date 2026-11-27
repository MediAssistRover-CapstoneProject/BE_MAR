package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.ai.AiSensingService;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.media.MediaSession;
import com.mediassistrover.backend.media.MediaSessionRepository;
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
import java.util.Objects;

/** Reuses the regular schema 3 workflow and schedule lifecycle for bounded patrol runs. */
@Service
@RequiredArgsConstructor
public class PatrolWorkflowService {
    private static final String SERVICE = "PATROL";
    private static final String CODE_PREFIX = "WF_PATROL_";
    private static final String TASK = "PATROL";
    public static final String FALL_CAPABILITY = "PATROL_FALL";
    private static final Set<EmergencyIncidentStatus> OPEN_INCIDENTS = Set.of(EmergencyIncidentStatus.OPEN,
            EmergencyIncidentStatus.ACKNOWLEDGED, EmergencyIncidentStatus.IN_PROGRESS);
    private static final Set<JobStatus> OCCUPIED = JobOccupancyPolicy.MOTION_OWNERS;

    private final WorkflowService workflows;
    private final WorkflowRepository definitions;
    private final StepRepository steps;
    private final FacilityMapRepository maps;
    private final EndpointRepository endpoints;
    private final EndpointGroupRepository groups;
    private final EndpointGroupMemberRepository members;
    private final StepTransitionRepository transitions;
    private final ZoneRepository zones;
    private final JobRepository jobs;
    private final RobotMqttSessionRepository links;
    private final MappingSessionRepository mappingSessions;
    private final RobotCommandOutboxRepository commands;
    private final AiSensingProfileRepository profiles;
    private final MediaSessionRepository mediaSessions;
    private final EmergencyIncidentRepository incidents;
    private final AiSensingService ai;
    private final MqttSessionRegistry registry;
    private final MqttProperties mqtt;
    private final NavigationProfileService navigation;
    private final StaffOperationalScopePolicy staffScope;
    private final MqttWire wire;
    private final ObjectMapper json;
    private final com.mediassistrover.backend.validation.WorkflowInputValidator inputValidator;
    private final WorkflowValidationService definitionValidation;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;

    public record Checkpoint(@NotNull UUID endpointId, UUID zoneId,
                             @NotBlank @Pattern(regexp = "CROWD_DENSITY|RESTRICTED_ZONE|PATROL_FALL") String sensingType,
                             UUID endpointGroupId) {
        public Checkpoint(UUID endpointId, UUID zoneId, String sensingType) {
            this(endpointId, zoneId, sensingType, null);
        }
    }

    public record Create(@NotBlank @Size(max = 160) String name, @NotNull UUID mapId,
                         @NotEmpty @Size(max = 5) List<@NotNull @Valid Checkpoint> checkpoints,
                         @DecimalMin("0.05") @DecimalMax("0.60") Double maxSpeedMps,
                         @Min(15) @Max(120) Integer moveTimeoutSec,
                         @Min(1) @Max(30) Integer sensingWindowSec,
                         Boolean fallDetection,
                         @Min(0) @Max(1000) Integer repeatCount) {
        public Create(String name, UUID mapId, List<Checkpoint> checkpoints, Double maxSpeedMps,
                      Integer moveTimeoutSec, Integer sensingWindowSec, Boolean fallDetection) {
            this(name, mapId, checkpoints, maxSpeedMps, moveTimeoutSec, sensingWindowSec, fallDetection, null);
        }
        public Create(String name, UUID mapId, List<Checkpoint> checkpoints, Double maxSpeedMps,
                      Integer moveTimeoutSec, Integer sensingWindowSec) {
            this(name, mapId, checkpoints, maxSpeedMps, moveTimeoutSec, sensingWindowSec, null, null);
        }
    }

    public record Result(UUID workflowId, UUID mapId, int checkpointCount) { }

    public record PrepareTemplate(@Min(3) @Max(11) int checkpointCount) { }
    public record TemplateResult(UUID workflowId, int checkpointCount, boolean available) { }

    @Transactional(readOnly = true)
    public List<TemplateResult> templates(AccountPrincipal actor) {
        requireAdmin(actor);
        List<TemplateResult> result = new ArrayList<>();
        for (int count = 3; count <= 11; count++) {
            var existing = definitions.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                    PatrolTemplateDefinition.code(count), WorkflowStatus.PUBLISHED).orElse(null);
            if (existing != null && (!isTemplate(existing) || existing.getDefinitionHash() == null
                    || existing.getInputUiJson().path("patrol_template").path("version").asInt() != PatrolTemplateDefinition.VERSION
                    || existing.getInputUiJson().path("patrol_template").path("checkpointCount").asInt() != count)) existing = null;
            result.add(new TemplateResult(existing == null ? null : existing.getWorkflowId(), count, existing != null));
        }
        return result;
    }

    @Transactional
    public TemplateResult prepareTemplate(PrepareTemplate request, AccountPrincipal actor, RequestMetadata metadata) {
        requireAdmin(actor);
        PatrolTemplateDefinition.requireCount(request.checkpointCount());
        // The runtime policy row serializes concurrent preparation before checking the unique code/version.
        rollout.lockForRuntimeCreation();
        String code = PatrolTemplateDefinition.code(request.checkpointCount());
        var existing = definitions.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                code, WorkflowStatus.PUBLISHED).orElse(null);
        if (existing != null) {
            requireTemplateDefinition(existing);
            return new TemplateResult(existing.getWorkflowId(), request.checkpointCount(), true);
        }
        if (definitions.existsByWorkflowCodeIgnoreCaseAndVersionNo(code, 1))
            throw new BusinessRuleException("This patrol template definition is unavailable; restore its published version");
        var graph = PatrolTemplateDefinition.graph(json, request.checkpointCount(), code);
        UUID id = workflows.createGraph(graph, actor.accountId(), metadata).workflow().workflowId();
        workflows.validate(id, actor.accountId(), metadata);
        workflows.publish(id, actor.accountId(), metadata);
        return new TemplateResult(id, request.checkpointCount(), true);
    }

    public static boolean isTemplate(Workflow workflow) {
        return isPatrol(workflow) && workflow.getInputUiJson() != null && workflow.getInputUiJson().has("patrol_template");
    }

    /** Only this field is added by PublishedWorkflowJobService after validating the submitted inputs. */
    public JsonNode templateJobInputs(Job job) {
        if (job.getInputJson() == null || !job.getInputJson().isObject())
            throw new BusinessRuleException("Patrol Job inputs must be a JSON object");
        ObjectNode inputs = job.getInputJson().deepCopy();
        inputs.remove("requested_by_account_id");
        return inputs;
    }

    /** Freeze catalog authority alongside, without adding authority fields to the caller's input contract. */
    public void snapshotTemplateRoute(Job job) {
        if (!isTemplate(job.getWorkflow())) return;
        ObjectNode context = job.getContextJson() == null ? json.createObjectNode() : job.getContextJson().deepCopy();
        context.set("patrol_checkpoint_snapshot", templateRouteSnapshot(templateJobInputs(job)));
        job.setContextJson(context);
    }

    /** Permanent queued-route validation. Connectivity/localization and competing work belong to readyMap. */
    public void requireQueuedTemplate(Job job) {
        if (!isTemplate(job.getWorkflow())) return;
        JsonNode inputs = templateJobInputs(job);
        validateSchedule(job.getWorkflow(), job.getRobot(), job.getPriority(), null, inputs);
        if (job.getMap() == null || !job.getMap().getMapId().equals(requiredUuid(inputs, "map_id"))
                || !Objects.equals(job.getWorkflowHash(), job.getWorkflow().getDefinitionHash()))
            throw new BusinessRuleException("Patrol route differs from the Job's frozen map or definition hash");
        JsonNode frozen = job.getContextJson() == null ? json.missingNode()
                : job.getContextJson().path("patrol_checkpoint_snapshot");
        if (!frozen.isObject() || !sameJson(routeAuthority(frozen), routeAuthority(templateRouteSnapshot(inputs))))
            throw new BusinessRuleException("Patrol checkpoint map, room or ordered endpoint-group authority changed after queuing");
    }

    private ObjectNode templateRouteSnapshot(JsonNode inputs) {
        UUID mapId = requiredUuid(inputs, "map_id"); FacilityMap map = requireMap(mapId);
        var snapshot = json.createObjectNode().put("version", 1).put("map_id", mapId.toString())
                .put("map_asset_hash", map.getAssetHash()).put("frame_id", map.getFrameId());
        var targets = snapshot.putArray("checkpoints");
        for (var checkpoint : templateCheckpoints(inputs)) {
            requireTemplateCheckpoint(mapId, checkpoint);
            var slot = targets.addObject();
            if (checkpoint.endpointGroupId() == null) {
                slot.put("target_mode", "ENDPOINT");
                slot.set("endpoint", snapshotEndpoint(endpoints.findById(checkpoint.endpointId()).orElseThrow()));
            } else {
                var group = groups.findById(checkpoint.endpointGroupId()).orElseThrow();
                slot.put("target_mode", "ENDPOINT_GROUP").put("endpoint_group_id", group.getEndpointGroupId().toString())
                        .put("endpoint_group_name", group.getGroupName()).put("room_id", group.getRoom().getRoomId().toString())
                        .put("room_name", group.getRoom().getRoomName()).put("fallback_radius_m", group.getFallbackRadiusM());
                slot.put("fallback_trigger",group.getFallbackTrigger().name()).put("path_search_budget_sec",group.getPathSearchBudgetSec());
                var ordered = slot.putArray("targets");
                for (var member : members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(checkpoint.endpointGroupId()))
                    if (member.isActive()) ordered.add(snapshotEndpoint(member.getEndpoint()).put("sequence_no", member.getSequenceNo()));
            }
        }
        return snapshot;
    }

    private ObjectNode snapshotEndpoint(Endpoint endpoint) {
        var snapshot = json.createObjectNode().put("endpoint_id", endpoint.getEndpointId().toString())
                .put("endpoint_code", endpoint.getEndpointCode()).put("endpoint_name", endpoint.getEndpointName())
                .put("x_m", endpoint.getXM()).put("y_m", endpoint.getYM()).put("yaw_rad", endpoint.getYawRad());
        if (endpoint.getRoom() != null) snapshot.put("room_id", endpoint.getRoom().getRoomId().toString())
                .put("room_name", endpoint.getRoom().getRoomName());
        return snapshot;
    }

    private JsonNode routeAuthority(JsonNode snapshot) {
        JsonNode result = snapshot.deepCopy();
        if (result.isObject()) {
            ((ObjectNode) result).remove(Set.of("endpoint_code", "endpoint_name", "room_name", "endpoint_group_name"));
            ((ObjectNode) result).properties().forEach(field -> ((ObjectNode) result).set(field.getKey(), routeAuthority(field.getValue())));
        } else if (result.isArray()) for (int index = 0; index < result.size(); index++)
            ((com.fasterxml.jackson.databind.node.ArrayNode) result).set(index, routeAuthority(result.get(index)));
        return result;
    }

    @Transactional(readOnly = true)
    public GraphCreateRequest preview(Create request, AccountPrincipal actor) {
        requireAdmin(actor);
        requireMap(request.mapId());
        for (Checkpoint checkpoint : request.checkpoints()) {
            requireCheckpoint(request.mapId(), checkpoint);
        }
        boolean grouped=request.checkpoints().stream().anyMatch(checkpoint->checkpoint.endpointGroupId()!=null);
        return graph(request, CODE_PREFIX + (grouped?"V2_":"") + wire.canonicalHash(json.valueToTree(request))
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
        return graph(request,code,true);
    }

    private GraphCreateRequest graph(Create request, String code, boolean skipBlockedCheckpoint) {
        if (request.checkpoints() == null || request.checkpoints().isEmpty() || request.checkpoints().size() > 5)
            throw new BusinessRuleException("Patrol requires 1..5 checkpoints");
        int repeats = request.repeatCount() == null ? 1 : request.repeatCount();
        if (repeats < 0 || repeats > 1000) throw new BusinessRuleException("Patrol repeatCount must be 0 (continuous) or 1..1000");
        double speed = request.maxSpeedMps() == null ? .25 : request.maxSpeedMps();
        int moveTimeout = request.moveTimeoutSec() == null ? 60 : request.moveTimeoutSec();
        int window = request.sensingWindowSec() == null ? 12 : request.sensingWindowSec();
        if (speed < .05 || speed > .60 || !Double.isFinite(speed) || moveTimeout < 15 || moveTimeout > 120 || window < 1 || window > 30)
            throw new BusinessRuleException("Patrol speed or timeout is outside its supported range");
        if (window < 3 && (Boolean.TRUE.equals(request.fallDetection()) || request.checkpoints().stream().anyMatch(PatrolWorkflowService::fallOnly)))
            throw new BusinessRuleException("Patrol fall detection requires a sensing window of at least 3 seconds");
        List<GraphStepRequest> actions = new ArrayList<>();
        List<GraphTransitionRequest> edges = new ArrayList<>();
        int candidateMoves = 0;
        for (int index = 0; index < request.checkpoints().size(); index++) {
            Checkpoint checkpoint = request.checkpoints().get(index);
            int number = index + 1;
            if (checkpoint.endpointGroupId() == null) {
                actions.add(step(StepType.MOVE, "MOVE_" + number, "Move to checkpoint " + number,
                        actions.size() + 1, moveTimeout, json.createObjectNode().put("mode", SERVICE)
                                .put("map_id", request.mapId().toString())
                                .put("endpoint_id", checkpoint.endpointId().toString()).put("max_speed_mps", speed)));
                standardRoutes(edges, "MOVE_" + number, "SENSE_" + number);
                candidateMoves++;
            } else {
                EndpointGroupWorkflowSupport.append(json, actions, edges, TASK, "GROUP_" + number,
                        json.getNodeFactory().textNode(checkpoint.endpointGroupId().toString()),
                        json.getNodeFactory().textNode(request.mapId().toString()), SERVICE, speed, moveTimeout,
                        2, "SENSE_" + number, TerminalStatus.FAILED, skipBlockedCheckpoint,
                        index+1==request.checkpoints().size() ? null
                            : request.checkpoints().get(index+1).endpointGroupId()==null ? "MOVE_"+(number+1) : "GROUP_"+(number+1)+"_RESOLVE");
                candidateMoves += 2;
            }
            var sensing = json.createObjectNode().put("sensing_type", checkpoint.sensingType()).put("window_sec", window);
            if (checkpoint.zoneId() != null) sensing.put("zone_id", checkpoint.zoneId().toString());
            if (fallOnly(checkpoint) || Boolean.TRUE.equals(request.fallDetection())) sensing.put("fall_detection", true);
            actions.add(step(StepType.EXECUTE_SENSING, "SENSE_" + number, "Observe checkpoint " + number,
                    actions.size() + 1, window + 10, sensing));
            String following = index + 1 == request.checkpoints().size() ? null
                    : request.checkpoints().get(index + 1).endpointGroupId() == null
                        ? "MOVE_" + (number + 1) : "GROUP_" + (number + 1) + "_RESOLVE";
            standardRoutes(edges, "SENSE_" + number, following);
        }
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        var fields = schema.putObject("properties");
        fields.putObject("map_id").put("type", "string").put("format", "uuid");
        fields.putObject("patrol_repeat_count").put("type", "integer").put("minimum", 0).put("maximum", 1000);
        schema.putArray("required").add("map_id");
        var defaults = json.createObjectNode().put("map_id", request.mapId().toString()).put("patrol_repeat_count", repeats);
        int duration = candidateMoves * moveTimeout + request.checkpoints().size() * (window + 10) + 300;
        boolean grouped = request.checkpoints().stream().anyMatch(checkpoint -> checkpoint.endpointGroupId() != null);
        var ui = json.createObjectNode();
        if (grouped) {
            ui.set("patrol_definition", json.valueToTree(request));
            if(skipBlockedCheckpoint) ui.put("patrol_group_exhaustion","SKIP_CHECKPOINT");
        }
        var workflow = new WorkflowUpsertRequest(code, 1, request.name(), SERVICE,
                "Finite patrol with one sensing window after each successful MOVE", schema, ui,
                defaults, json.createObjectNode(), json.createArrayNode(), duration,
                actions.stream().mapToInt(GraphStepRequest::maxVisits).sum(), 3, 3);
        return new GraphCreateRequest(workflow, new GraphRequest(actions.getFirst().stepCode(),
                List.of(new TaskRequest(TASK, "Patrol checkpoints", 1, null, json.createObjectNode())), actions, edges));
    }

    private void standardRoutes(List<GraphTransitionRequest> routes, String from, String next) {
        routes.add(new GraphTransitionRequest(from, next, TransitionOutcome.SUCCESS, null, 1, true,
                next == null ? TerminalStatus.SUCCEEDED : null));
        routes.add(new GraphTransitionRequest(from, null, TransitionOutcome.FAILURE, null, 1, true, TerminalStatus.FAILED));
        // A missing physical stop is handled by the runtime safety gate before these routes run.
        routes.add(new GraphTransitionRequest(from, null, TransitionOutcome.TIMEOUT, null, 1, true, TerminalStatus.FAILED));
    }

    private GraphStepRequest step(StepType type, String code, String name, int sequence, int timeout, JsonNode params) {
        return new GraphStepRequest(TASK, type, code, name, sequence, null, params, json.createObjectNode(),
                timeout, 1, 0, json.createArrayNode(), 1, StepAuditLevel.FULL);
    }

    public static boolean isPatrol(Workflow workflow) {
        return workflow != null && SERVICE.equals(workflow.getServiceType()) && workflow.getWorkflowCode() != null
                && workflow.getWorkflowCode().startsWith(CODE_PREFIX);
    }

    public void validateSchedule(Workflow workflow, Robot robot, int priority, Integer intervalSec, JsonNode inputs) {
        if (SERVICE.equalsIgnoreCase(workflow.getServiceType()) && !isPatrol(workflow)) {
            throw new BusinessRuleException("Use a patrol definition created with the patrol workflow API");
        }
        if (!isPatrol(workflow)) return;
        if (robot == null || !mqtt.manages(robot.getRobotId())) {
            throw new BusinessRuleException("Patrol requires an explicit MQTT robot");
        }
        if (priority < 0 || priority > 10 || (intervalSec != null && intervalSec < 60)) {
            throw new BusinessRuleException("Patrol priority must be 0..10 and interval must be at least 60 seconds");
        }
        if (isTemplate(workflow)) {
            requireTemplateDefinition(workflow);
            inputValidator.validate(workflow.getInputSchemaJson(), inputs);
            UUID mapId = requiredUuid(inputs, "map_id");
            requireMap(mapId);
            for (var checkpoint : templateCheckpoints(inputs)) requireTemplateCheckpoint(mapId, checkpoint);
            return;
        }
        if (inputs == null || !inputs.isObject() || !inputs.isEmpty()) {
            throw new BusinessRuleException("Patrol schedule inputTemplateJson must be {}; checkpoints belong to its workflow");
        }
        requireMap(mapId(workflow));
    }

    /** Future patrol occurrences bind their verified live session at admission, never at schedule creation. */
    public void validateScheduledRoute(Workflow workflow, Robot robot, int priority, Integer intervalSec, JsonNode inputs) {
        if (isTemplate(workflow) && inputs != null && inputs.has("navigation_session_id"))
            throw new BusinessRuleException("Patrol schedule inputs must not pin a navigation_session_id; each run binds its current verified session");
        validateSchedule(workflow, robot, priority, intervalSec, inputs);
    }

    /** Validate durable intent without requiring a connected, idle robot. */
    public FacilityMap queuedMap(Workflow workflow, Robot robot, int priority, Integer intervalSec, JsonNode inputs) {
        validateSchedule(workflow, robot, priority, intervalSec, inputs);
        if (!isPatrol(workflow) || workflow.getSchemaVersion() != 3) {
            throw new BusinessRuleException("Patrol requires a schema 3 patrol definition");
        }
        FacilityMap map = requireMap(isTemplate(workflow) ? requiredUuid(inputs, "map_id") : mapId(workflow));
        if (!isTemplate(workflow)) {
            var actions = checkpointSteps(workflow, map.getMapId());
            for (var action : actions) requireCheckpoint(map.getMapId(), action.checkpoint());
        }
        return map;
    }

    /** Caller holds the robot write lock before asking whether a scheduled run can use it. */
    public FacilityMap readyMap(Workflow workflow, Robot robot, int priority) {
        return readyMap(workflow, robot, priority, null);
    }

    /** A queued candidate must exclude itself while rechecking every runtime guard. */
    public FacilityMap readyMap(Workflow workflow, Robot robot, int priority, UUID candidateJobId) {
        return readyMap(workflow, robot, priority, candidateJobId, workflow.getDefaultInputsJson());
    }

    public FacilityMap readyMap(Workflow workflow, Robot robot, int priority, UUID candidateJobId, JsonNode inputs) {
        if (robot == null || robot.getStatus() != RobotStatus.IDLE || !mqtt.dispatches(StepType.MOVE)
                || !mqtt.dispatches(StepType.EXECUTE_SENSING) || !mqtt.manages(robot.getRobotId())) return null;
        UUID robotId = robot.getRobotId();
        boolean template = isTemplate(workflow);
        UUID mapId = template ? requiredUuid(inputs, "map_id") : mapId(workflow);
        FacilityMap map = robot.getCurrentMap();
        if (map == null || !mapId.equals(map.getMapId()) || map.getStatus() != MapStatus.ACTIVE
                || map.getAssetHash() == null || !map.getAssetHash().equalsIgnoreCase(robot.getMapAssetHash())) return null;
        boolean occupied = candidateJobId == null
                ? jobs.existsNonterminalPatrol(robotId) || !jobs.findByRobotRobotIdAndStatusIn(robotId, OCCUPIED).isEmpty()
                : jobs.findByRobotRobotIdAndStatusIn(robotId, JobOccupancyPolicy.NON_TERMINAL).stream()
                    // canStart already chooses the first queued job by priority/FIFO.
                    .anyMatch(other -> !candidateJobId.equals(other.getJobId())
                            && (other.getStatus() != JobStatus.QUEUED || other.stopOutstanding()));
        if (occupied
                || jobs.existsHigherPriorityQueuedForMap(mapId, priority)
                || mediaSessions.findByActiveRobotId(robotId).filter(s -> s.getMode() == MediaSession.Mode.ASSIST).isPresent()
                || incidents.existsByRobotRobotIdAndIncidentTypeAndStatusInAndArchivedAtIsNull(
                        robotId, EmergencyIncident.SUSPECTED_FALL_TYPE, OPEN_INCIDENTS)
                || !mappingSessions.findByRobotIdAndPotentiallyOpenTrue(robotId).isEmpty()
                || !commands.findByRobotIdAndStateIn(robotId, List.of("PENDING", "PUBLISHING", "PUBLISHED",
                        "ACKNOWLEDGED", "STARTED", "RECOVERY_REQUIRED")).isEmpty()) return null;
        var link = links.findById(robotId).orElse(null);
        if (!registry.fresh(link) || !registry.advertises(link, "MOVE") || !registry.advertises(link, "EXECUTE_SENSING")
                || link.getHeartbeatJson() == null || !"IDLE".equals(link.getHeartbeatJson().path("status").asText())
                || !link.getHeartbeatJson().path("activeJobStepId").isNull()
                || (link.getHelloJson().path("simulation").asBoolean() && !mqtt.isAllowSimulation())) return null;
        if (template) {
            validateSchedule(workflow, robot, priority, null, inputs);
            if (!fallCapable(link)) return null;
            for (var checkpoint : templateCheckpoints(inputs)) {
                requireTemplateCheckpoint(mapId, checkpoint);
                if (checkpoint.endpointGroupId() == null) {
                    if (!templateEndpointReady(robot, link, mapId, checkpoint.endpointId())) return null;
                } else for (var member : members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(checkpoint.endpointGroupId())) {
                    if (member.isActive() && !templateEndpointReady(robot, link, mapId, member.getEndpoint().getEndpointId())) return null;
                }
            }
            return map;
        }
        var actions = checkpointSteps(workflow, mapId);
        for (var action : actions) {
            var sensing = action.sensing();
            var config = sensing.getParamsJson();
            var checkpoint = action.checkpoint();
            requireCheckpoint(mapId, checkpoint);
            if (checkpoint.endpointGroupId() == null) {
                if (navigation.ready(robot, link, action.move().getParamsJson()) == null) return null;
            } else {
                for (var member : members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(checkpoint.endpointGroupId())) {
                    if (!member.isActive()) continue;
                    var params = json.createObjectNode().put("map_id", mapId.toString())
                            .put("endpoint_id", member.getEndpoint().getEndpointId().toString());
                    if (navigation.ready(robot, link, params) == null) return null;
                }
            }
            if ((fallOnly(checkpoint) || config.path("fall_detection").asBoolean()) && !fallCapable(link)) return null;
            if (fallOnly(checkpoint)) continue;
            ai.requireEnabled();
            boolean commissioned = profiles.findByRobotIdAndZoneIdAndSensingType(robotId, checkpoint.zoneId(), checkpoint.sensingType())
                    .filter(p -> p.isEnabled() && map.getAssetHash().equalsIgnoreCase(p.getMapAssetHash())
                            && map.getFrameId().equals(p.getFrameId())).isPresent();
            if (!commissioned) return null;
        }
        return map;
    }

    private record CheckpointSteps(Checkpoint checkpoint, Step move, Step sensing) { }

    private boolean templateEndpointReady(Robot robot, RobotMqttSession link, UUID mapId, UUID endpointId) {
        return navigation.ready(robot, link, json.createObjectNode().put("map_id", mapId.toString())
                .put("endpoint_id", endpointId.toString())) != null;
    }

    private List<Checkpoint> templateCheckpoints(JsonNode inputs) {
        List<Checkpoint> result = new ArrayList<>();
        for (var slot : inputs.path("checkpoints")) result.add(new Checkpoint(
                slot.hasNonNull("endpoint_id") ? requiredUuid(slot, "endpoint_id") : null,
                null, FALL_CAPABILITY, slot.hasNonNull("endpoint_group_id") ? requiredUuid(slot, "endpoint_group_id") : null));
        return result;
    }

    private void requireTemplateCheckpoint(UUID mapId, Checkpoint checkpoint) {
        if (checkpoint.endpointGroupId() == null) {
            requireCheckpoint(mapId, checkpoint);
            var endpoint = endpoints.findById(checkpoint.endpointId()).orElseThrow();
            if (endpoint.getRoom() != null && (!endpoint.getRoom().isActive() || endpoint.getRoom().getFloor() == null
                    || !endpoint.getRoom().getFloor().isActive()
                    || !endpoint.getRoom().getFloor().getFloorId().equals(endpoint.getMap().getFloor().getFloorId())))
                throw new BusinessRuleException("Patrol endpoint room/floor is inactive or outside its map");
            return;
        }
        var group = groups.findById(checkpoint.endpointGroupId())
                .orElseThrow(() -> new BusinessRuleException("Patrol endpoint group not found"));
        if (!group.isActive() || !group.isAllowFallback() || group.getSelectionPolicy() != SelectionPolicy.PRIORITY
                || group.getMap() == null || !mapId.equals(group.getMap().getMapId()) || group.getRoom() == null
                || !group.getRoom().isActive() || group.getRoom().getFloor() == null || !group.getRoom().getFloor().isActive()
                || !group.getRoom().getFloor().getFloorId().equals(group.getMap().getFloor().getFloorId())
                || group.getFallbackRadiusM() == null || group.getFallbackRadiusM().signum() <= 0)
            throw new BusinessRuleException("Patrol fallback group requires an active same-map room and ordered fallback policy");
        var pair = members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(checkpoint.endpointGroupId()).stream()
                .filter(EndpointGroupMember::isActive).toList();
        if (pair.size() != 2 || pair.getFirst().getSequenceNo() != 1 || pair.get(1).getSequenceNo() != 2)
            throw new BusinessRuleException("Patrol fallback group requires two active members in priority order 1,2");
        for (var member : pair) {
            if (member.getMap() == null || !mapId.equals(member.getMap().getMapId()) || member.getEndpoint() == null
                    || member.getEndpoint().getRoom() == null
                    || !group.getRoom().getRoomId().equals(member.getEndpoint().getRoom().getRoomId()))
                throw new BusinessRuleException("Patrol fallback members must belong to their group room/map");
            requireTemplateCheckpoint(mapId, new Checkpoint(member.getEndpoint().getEndpointId(), null, FALL_CAPABILITY));
        }
    }

    private void requireTemplateDefinition(Workflow workflow) {
        var metadata = workflow.getInputUiJson() == null ? json.missingNode() : workflow.getInputUiJson().path("patrol_template");
        if (!isTemplate(workflow) || metadata.size() != 2 || !metadata.path("version").isIntegralNumber()
                || (metadata.path("version").asInt()!=1 && metadata.path("version").asInt()!=2
                    && metadata.path("version").asInt()!=PatrolTemplateDefinition.VERSION)
                || !metadata.path("checkpointCount").isIntegralNumber())
            throw new BusinessRuleException("Patrol template metadata is invalid");
        int count = metadata.path("checkpointCount").asInt();
        PatrolTemplateDefinition.requireCount(count);
        int version=metadata.path("version").asInt();
        if (!PatrolTemplateDefinition.code(count,version).equals(workflow.getWorkflowCode()) || workflow.getSchemaVersion() != 3
                || workflow.getGraphVersion() != 3 || workflow.getVersionNo() != 1 || workflow.getDefinitionHash() == null)
            throw new BusinessRuleException("Patrol template identity differs from its immutable definition");
        var expected = PatrolTemplateDefinition.graph(json, count, workflow.getWorkflowCode(),version);
        if (!sameJson(workflow.getInputSchemaJson(), expected.workflow().inputSchemaJson())
                || !sameJson(workflow.getInputUiJson(), expected.workflow().inputUiJson())
                || !sameJson(workflow.getDefaultInputsJson(), expected.workflow().defaultInputsJson())
                || !sameJson(workflow.getOutputMappingJson(), expected.workflow().outputMappingJson())
                || !sameJson(workflow.getRequiredCapabilitiesJson(), expected.workflow().requiredCapabilitiesJson()))
            throw new BusinessRuleException("Patrol template inputs differ from the generated definition");
        requireGeneratedGraph(workflow, expected, steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId()));
        var compiled = definitionValidation.validateAndCompile(workflow);
        if (!workflow.getDefinitionHash().equals(compiled.definitionHash())
                || !sameJson(workflow.getCompiledDefinitionJson(), compiled.compiledDefinition()))
            throw new BusinessRuleException("Patrol template hash differs from its published definition");
    }

    private List<CheckpointSteps> checkpointSteps(Workflow workflow, UUID mapId) {
        var actions = steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId());
        if (workflow.getInputUiJson() != null && workflow.getInputUiJson().has("patrol_definition"))
            return generatedCheckpointSteps(workflow, mapId, actions);
        if (actions.isEmpty() || actions.size() > 10 || actions.size() % 2 != 0) {
            throw new BusinessRuleException("Patrol requires 1..5 MOVE/sensing checkpoint pairs");
        }
        List<CheckpointSteps> checkpoints = new ArrayList<>();
        for (int i = 0; i < actions.size(); i += 2) {
            var move = actions.get(i);
            var sensing = actions.get(i + 1);
            if (!move.isEnabled() || !sensing.isEnabled() || move.getStepType() != StepType.MOVE
                    || sensing.getStepType() != StepType.EXECUTE_SENSING
                    || !SERVICE.equals(move.getParamsJson().path("mode").asText())
                    || !mapId.toString().equals(move.getParamsJson().path("map_id").asText())) {
                throw new BusinessRuleException("Patrol requires enabled MOVE on its map followed by sensing");
            }
            var config = sensing.getParamsJson();
            checkpoints.add(new CheckpointSteps(new Checkpoint(requiredUuid(move.getParamsJson(), "endpoint_id"),
                    config.hasNonNull("zone_id") ? requiredUuid(config, "zone_id") : null,
                    config.path("sensing_type").asText()), move, sensing));
        }
        return checkpoints;
    }

    /** A group branch is accepted only when it is exactly the bounded graph authored by this API. */
    private List<CheckpointSteps> generatedCheckpointSteps(Workflow workflow, UUID mapId, List<Step> actions) {
        Create definition;
        try { definition = json.treeToValue(workflow.getInputUiJson().path("patrol_definition"), Create.class); }
        catch (Exception invalid) { throw new BusinessRuleException("Patrol checkpoint definition is invalid"); }
        if (definition == null || !mapId.equals(definition.mapId()) || definition.checkpoints() == null
                || definition.checkpoints().stream().anyMatch(Objects::isNull)
                || definition.checkpoints().stream().noneMatch(checkpoint -> checkpoint.endpointGroupId() != null))
            throw new BusinessRuleException("Patrol group definition must retain its fixed checkpoint/map authority");
        var expected = graph(definition, workflow.getWorkflowCode(),
                "SKIP_CHECKPOINT".equals(workflow.getInputUiJson().path("patrol_group_exhaustion").asText()));
        requireGeneratedGraph(workflow, expected, actions);
        List<CheckpointSteps> result = new ArrayList<>();
        for (int index = 0; index < definition.checkpoints().size(); index++) {
            var checkpoint = definition.checkpoints().get(index); int number = index + 1;
            String move = checkpoint.endpointGroupId() == null ? "MOVE_" + number : "GROUP_" + number + "_MOVE";
            Step movement = actions.stream().filter(step -> move.equals(step.getStepCode())).findFirst().orElseThrow();
            Step sensing = actions.stream().filter(step -> ("SENSE_" + number).equals(step.getStepCode())).findFirst().orElseThrow();
            result.add(new CheckpointSteps(checkpoint, movement, sensing));
        }
        return result;
    }

    private void requireGeneratedGraph(Workflow workflow, GraphCreateRequest expected, List<Step> actions) {
        var planned = expected.graph().steps();
        if (actions.size() != planned.size() || workflow.getMaxTotalVisits() != expected.workflow().maxTotalVisits()
                || workflow.getMaxDurationSec() != expected.workflow().maxDurationSec()
                || workflow.getStartStep() == null
                || !expected.graph().startStepCode().equals(workflow.getStartStep().getStepCode()))
            throw new BusinessRuleException("Patrol group graph or execution budget differs from its checkpoint definition");
        for (int index = 0; index < planned.size(); index++) {
            var actual = actions.get(index); var intended = planned.get(index);
            if (!actual.isEnabled() || actual.getStepType() != intended.stepType()
                    || !intended.stepCode().equals(actual.getStepCode()) || actual.getSequenceNo() != intended.sequenceNo()
                    || actual.getTask() == null || !TASK.equals(actual.getTask().getTaskCode())
                    || actual.getTimeoutSec() != intended.timeoutSec() || actual.getMaxVisits() != intended.maxVisits()
                    || actual.getMaxAttempts() != 1 || actual.getRetryDelaySec() != 0
                    || !sameJson(actual.getRetryCodesJson(), intended.retryCodesJson())
                    || !sameJson(actual.getParamsJson(), intended.paramsJson())
                    || !sameJson(actual.getInputMappingJson(), intended.inputMappingJson()))
                throw new BusinessRuleException("Patrol group steps differ from the generated bounded navigation/sensing graph");
        }
        var routes = transitions.findByWorkflowWorkflowIdOrderByFromStepSequenceNoAscOutcomeAscPriorityAsc(workflow.getWorkflowId());
        if (routes.size() != expected.graph().transitions().size()
                || expected.graph().transitions().stream().anyMatch(intended -> routes.stream().noneMatch(actual ->
                    intended.fromStepCode().equals(actual.getFromStep().getStepCode())
                    && Objects.equals(intended.toStepCode(), actual.getToStep() == null ? null : actual.getToStep().getStepCode())
                    && intended.outcome() == actual.getOutcome() && intended.priority() == actual.getPriority()
                    && intended.defaultTransition() == actual.isDefaultTransition() && intended.terminalStatus() == actual.getTerminalStatus()
                    && sameJson(intended.conditionJson(), actual.getConditionJson()))))
            throw new BusinessRuleException("Patrol group transitions differ from the generated fallback guard");
    }

    private boolean sameJson(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) return right == null || right.isNull();
        if (right == null || right.isNull()) return false;
        if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
        if (left.isObject() && right.isObject()) {
            if (left.size() != right.size()) return false;
            var fields = left.fieldNames();
            while (fields.hasNext()) { String field = fields.next(); if (!right.has(field) || !sameJson(left.get(field), right.get(field))) return false; }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) if (!sameJson(left.get(index), right.get(index))) return false;
            return true;
        }
        return left.equals(right);
    }

    public static boolean fallCapable(RobotMqttSession link) {
        if (link == null || link.getHelloJson() == null) return false;
        for (JsonNode capability : link.getHelloJson().path("capabilities")) {
            if (FALL_CAPABILITY.equals(capability.asText())) return true;
        }
        return false;
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
        if (!endpoint.isActive() || !endpoint.isAccessible() || !mapId.equals(endpoint.getMap().getMapId())
                || endpoint.getYawRad() == null) {
            throw new BusinessRuleException("Patrol checkpoint needs an active accessible endpoint with heading on the patrol map");
        }
        if (checkpoint.endpointGroupId() != null) {
            var group = groups.findById(checkpoint.endpointGroupId()).orElseThrow(() -> new BusinessRuleException("Patrol endpoint group not found"));
            if (!group.isActive() || !group.isAllowFallback() || group.getRoom() == null
                    || group.getSelectionPolicy() != SelectionPolicy.PRIORITY || !mapId.equals(group.getMap().getMapId())
                    || endpoint.getRoom() == null || !group.getRoom().getRoomId().equals(endpoint.getRoom().getRoomId()))
                throw new BusinessRuleException("Patrol fallback group must be ordered and scoped to the primary endpoint room/map");
            var pair = members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(checkpoint.endpointGroupId()).stream()
                    .filter(EndpointGroupMember::isActive).toList();
            if (pair.size() != 2 || pair.getFirst().getSequenceNo() != 1 || pair.get(1).getSequenceNo() != 2
                    || !checkpoint.endpointId().equals(pair.getFirst().getEndpoint().getEndpointId())
                    || pair.stream().anyMatch(member -> !member.getMap().getMapId().equals(mapId)
                        || !member.getEndpoint().isActive() || !member.getEndpoint().isAccessible()
                        || member.getEndpoint().getYawRad() == null || !member.getEndpoint().getMap().getMapId().equals(mapId)
                        || member.getEndpoint().getRoom() == null
                        || !group.getRoom().getRoomId().equals(member.getEndpoint().getRoom().getRoomId())))
                throw new BusinessRuleException("Patrol fallback requires its explicit primary as member 1 and one usable same-room fallback");
        }
        if (fallOnly(checkpoint)) {
            if (checkpoint.zoneId() != null) throw new BusinessRuleException("A patrol fall-only checkpoint does not require a sensing zone");
            return;
        }
        if (checkpoint.zoneId() == null) throw new BusinessRuleException("Patrol sensing zone is required");
        var zone = zones.findById(checkpoint.zoneId()).orElseThrow(() -> new BusinessRuleException("Patrol zone not found"));
        if (!zone.isActive() || !mapId.equals(zone.getMap().getMapId()) || !AiSensingService.TYPES.contains(checkpoint.sensingType())
                || ("RESTRICTED_ZONE".equals(checkpoint.sensingType()) && zone.getZoneType() != ZoneType.KEEP_OUT)) {
            throw new BusinessRuleException("Patrol needs an active sensing zone on the same map; RESTRICTED_ZONE requires KEEP_OUT");
        }
    }

    private static boolean fallOnly(Checkpoint checkpoint) {
        return FALL_CAPABILITY.equals(checkpoint.sensingType());
    }
}
