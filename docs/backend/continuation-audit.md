# Backend continuation audit

Audit date: 2026-10-20. Scope: repository source only; no production deploy, MQTT command, workflow publish, or robot reset was performed.

## Completion state

- `BACKEND_AUDIT_COMPLETE`: yes for the navigation, workflow runtime, MQTT telemetry, robot realtime, Job realtime, and FE-facing APIs named in `doc.md`.
- `BACKEND_CODE_READY`: yes; source and test source compile.
- `BACKEND_TESTS_PASSED`: partial. The 56 focused non-Docker tests pass. The complete suite cannot start 318 MySQL integration tests because Docker/Testcontainers is unavailable on this workstation.
- `BACKEND_REALTIME_API_READY`: code-ready; live MQTT/ROS verification remains outstanding.
- `RAILWAY_DEPLOYED`: no.
- `ROBOT_TELEMETRY_PUBLISHING`: not verified; robot-side dependency.
- `ROBOT_MULTI_MOVE_READY`: backend code and regression test are ready; Adapter/E2E not verified.
- `FRONTEND_3D_CONNECTED`: no; frontend dependency.
- `END_TO_END_MULTI_MOVE_PASSED`: no; requires MySQL, broker, and ROS 2 Adapter/Gazebo.

## Audit classification

| Area | Classification | Evidence / remaining dependency |
|---|---|---|
| Exactly 11 `StepType` values | `EXISTING_AND_VERIFIED` | Existing enum, workflow metadata, schema and tests are retained. |
| MQTT dispatch allow-list | `EXISTING_AND_VERIFIED` | `MOVE,CONTROL_HARDWARE,EXECUTE_SENSING,WAIT`; backend-owned steps cannot enter the dispatcher. |
| MAR-STEP-2, durable inbox/outbox, idempotency, recovery | `EXISTING_AND_VERIFIED` | Existing MQTT wire, inbox/outbox, session registry and recovery services retained. |
| Navigation destination Endpoint | `EXISTING_AND_VERIFIED` | Existing map/active/scope validation and frozen Job input retained. |
| Navigation origin Endpoint A | `EXISTING_BUT_INCOMPLETE` -> completed in prior commit | Optional `originEndpointId`, migration V32 and snapshot support already existed when this continuation began. |
| Explicit published workflow version on Navigation request | `MISSING` -> implemented | Optional `workflowId`; legacy omission still selects the latest compatible published workflow. |
| Client identity override prevention | `EXISTING_BUT_INCOMPLETE` -> fixed | `workflow_id`, `origin_endpoint_id`, `endpoint_id`, and `endpoint_group_id` are now removed from free-form `data` then rebuilt from validated fields. |
| Sequential multi-MOVE | `EXISTING_AND_VERIFIED` in code | Graph transition selects one visit; dispatcher permits only one unresolved robot command. Added two-MOVE regression coverage. Live E2E still pending. |
| Job REST/SSE and INTERACT response | `EXISTING_AND_VERIFIED` | `/api/v1/jobs/{id}`, `/events`, `/stream`, and idempotent `/api/v1/job-steps/{id}/responses` already exist. |
| Robot latest-pose snapshot and SSE | `EXISTING_BUT_INCOMPLETE` -> hardened | Prior commit added snapshot/SSE. This continuation fixes connection semantics, keepalive/freshness refresh and event publication for REST telemetry/offline changes. |
| MQTT `telemetry.map_pose` ingestion | `EXISTING_BUT_INCOMPLETE` -> hardened | Map identity, age, numeric bounds and yaw are validated before persistence; stale pose is not exposed as fresh. |
| Real periodic ROS map pose | `ROBOT_SIDE_DEPENDENCY` | Adapter must publish TF/localization-derived `map_pose`; backend does not synthesize it. |
| Nav2 planned path / feedback stream | `ROBOT_SIDE_DEPENDENCY` | No authoritative Adapter source exists in this repository. No straight-line fake path was added. |
| 3D hospital assets/rendering | frontend/asset dependency | Backend exposes data only. |

## Final navigation contract

`POST /api/v1/navigation-requests` requires `Idempotency-Key` and accepts:

```json
{
  "mapId": "<map-uuid>",
  "workflowId": "<optional-published-navigation-workflow-uuid>",
  "roomId": null,
  "originEndpointId": "<optional-endpoint-a-uuid>",
  "destinationEndpointId": "<endpoint-b-uuid>",
  "destinationGroupId": null,
  "targetMode": "ENDPOINT",
  "navigationMode": "STANDARD",
  "accessibilityNeed": null,
  "passengerCount": 1,
  "arrivalInstruction": null,
  "priority": 50,
  "note": "Guide patient",
  "data": {}
}
```

`workflowId` is optional for compatibility. When present it must identify a published `NAVIGATION` workflow accepting new Jobs and is persisted as a foreign key on `NavigationRequest`; free-form client data is never its authority. `originEndpointId` is optional for compatibility. The frozen Job input contains validated `map_id`, optional `workflow_id`, optional `origin_endpoint_id`, `endpoint_id` or `endpoint_group_id`, and `target_mode`.

## FE API matrix

| Screen | Snapshot / command APIs | Realtime |
|---|---|---|
| Robot dashboard | `GET /api/v1/robots`, `GET /api/v1/robots/{id}/snapshot`, status/location history | `GET /api/v1/robots/{id}/stream` (`robot-state`) |
| Create A -> B task | `GET /api/v1/maps`, `GET /api/v1/endpoints` or public catalog filtered by `mapId`, published workflows, `POST /api/v1/navigation-requests`, `POST /api/v1/service-requests/{id}/submit` | Subscribe to returned Job after submit |
| Digital Twin 3D | map/endpoint catalog plus robot snapshot | robot stream for pose/status; Job stream for task progress |
| Workflow Designer | `/api/v1/admin/workflows` graph/CRUD/clone/validate/publish and `/step-types` | Not required |
| Job Monitoring | `GET /api/v1/jobs/{id}`, `/events`, cancel/reconcile, `POST /api/v1/job-steps/{id}/responses` | `GET /api/v1/jobs/{id}/stream` (`job-state`) |

Use authenticated `fetch()` streaming (or an SSE client supporting the `Authorization: Bearer ...` header); do not place JWTs in query strings. On page open/reconnect: fetch the REST snapshot first, subscribe second, and refetch the snapshot if the stream reconnects.

## Robot event schemas

Robot SSE event name is `robot-state`. Its payload contains `observedAt`, full robot response, `mqttConnected`, `heartbeatFresh`, `heartbeatAgeSeconds`, `dispatchReady`, session failure reason, active Job/selected Step identity and `latestPose`. Pose contains robot/map/Job IDs, `xM`, `yM`, `yawRad`, confidence, `frameId`, `source`, computed `fresh`, `capturedAt`, and `receivedAt`.

Job SSE event name is `job-state`; payload is the persisted Job detail with tasks and Step visits. Mapping preview remains a distinct event. Planned path is not represented as pose or Job state.

## INTERACT(DISPLAY)

Read the selected Step from Job detail/SSE. Display its resolved `prompt_key`, data and action, calculate the deadline as `startedAt + timeoutSecSnapshot`, then send the actual user/display acknowledgement to:

```http
POST /api/v1/job-steps/{jobStepId}/responses
Authorization: Bearer <JWT>
Idempotency-Key: <stable-key-for-this-response>
Content-Type: application/json
```

Do not acknowledge merely because an SSE event arrived. The existing response service validates selected Step state, actor scope, deadline, response contract and idempotency.

## Adapter contracts

For pose, publish MAR-STEP-2 `HEARTBEAT` on the robot heartbeat topic with `currentMapId` and `telemetry.map_pose`:

```json
{
  "map_pose": {
    "value": {"x_m": 1.25, "y_m": 2.5, "yaw_rad": 0.3, "map_id": "<map-uuid>"},
    "age_sec": 0.1,
    "fresh": true
  }
}
```

The pose must be real map-frame localization (for example TF `map -> base_link`), not odometry without transform, endpoint coordinates, or a planned pose. A fresh pose must be no older than one second, match `currentMapId`, fit persisted coordinate precision, and use yaw in `[-3.141593, 3.141593]`.

For multi-MOVE, the Adapter executes one `EXECUTE_STEP` at a time and journals by `operationKey`/`commandId`. It must ACK, emit `STEP_STARTED`, then exactly one terminal `STEP_RESULT`/`STEP_ERROR` with physical-stop evidence. The next MOVE is a new JobStep/operation and will not be dispatched until the previous command is durably terminal. Reconnect uses HELLO journal reconciliation; it must not replay physical movement under a new identity.

## Database and configuration

This continuation adds V34, a nullable `NavigationRequest.workflow_id` foreign key and index. Existing pending migrations V32 (origin Endpoint) and V33 (pose provenance) also remain required in environments that have not applied them. V34 is backward-compatible: legacy Navigation requests keep `workflow_id = NULL` and continue selecting the latest compatible published workflow.

Railway variables: **KEEP CURRENT**. `ROBOT_POSE_FRESHNESS_SECONDS` is optional and defaults to `3`. Keep `MQTT_USERNAME=mar-be` and `MQTT_ENABLED_STEP_TYPES=MOVE,CONTROL_HARDWARE,EXECUTE_SENSING,WAIT`. Do not expose MQTT credentials to FE.

## Verification

- `mvn test-compile`: passed (472 main sources, 66 test sources).
- Focused tests: 56 passed, 0 failed (`MqttWireContractTest`, `RequestJobInputSnapshotTest`, `RobotRealtimeSnapshotServiceTest`).
- Complete `mvn test`: test execution reached 534 tests, but 318 MySQL integration tests errored during Testcontainers initialization because no Docker environment is installed/available. This is an environment blocker, not a passing integration result.
- New MySQL regression cases for explicit workflow pinning and two sequential MOVE visits compile but could not execute here.

## Safe deploy and rollback

Before deployment, run Docker and execute the full suite, then exercise HELLO/heartbeat/profile ACK and two MOVE operations in a non-production broker/Gazebo environment. Review the diff and deploy without auto-publishing a workflow. Flyway should apply V32/V33/V34 once. Take a DB backup first.

Rollback application code by redeploying the previous artifact. Do not delete V32/V33/V34 columns during an incident rollback; they are nullable and backward-compatible. Only reverse schema in a separately reviewed migration after confirming no stored origin/provenance/workflow-selection data is needed.

Current branch is `main`. A safer PowerShell handoff is:

```powershell
git switch -c feat/navigation-realtime-continuation
git add src docs
git commit -m "feat: complete navigation and robot realtime backend"
git push -u origin feat/navigation-realtime-continuation
```

No commit, push, merge, production deploy, workflow publish, Job creation, MQTT command, or robot action was performed by this audit.
