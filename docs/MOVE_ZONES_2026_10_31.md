# Saved-map MOVE and zone synchronization

The backend dispatches the existing MOVE step. Pi owns Nav2 planning, path following, final orientation and physical stop confirmation. No new step type or broker lane is introduced.

## End-to-end flow

1. Load an ACTIVE saved map and its metadata/image hash.
2. Open a temporary navigation session for one robot process. Pi loads that exact map and verifies localization.
3. Save endpoints and active zone rules while idle. The BE navigation profile includes map/session identity, endpoint coordinates and the `zones` array. Changing a speed, polygon or activation state changes the profile hash.
4. Pi validates and applies the profile before returning NAV_PROFILE_ACK. A new MOVE waits for the current session/hash to be acknowledged. Rule edits are synchronized while idle rather than replacing an in-flight MOVE profile.
5. The navigation request pins robot, session, map and endpoint. Pi submits the actual Nav2 path through the existing navigation-session plan API before beginning MOVE; the snapshot exposes only the current job/step's plan.
6. With the matching Pi patch, translation completes before at most one collision-checked final Spin. The step succeeds only after the final pose and stationary state are verified.

## Zone API

Use the existing ADMIN zone endpoints to create/update rules and `POST /api/v1/zones/{id}/activate` or `/deactivate` to switch them. Polygons are `[x,y]` in metres in the original map frame, either an array or `{"points":[...]}`. A rotated browser view does not rotate the stored coordinate system.

- `KEEP_OUT`: restricted polygon. Pi checks the oriented robot body and rejects a destination or final turn that intersects it. Full-map planning can take a longer route when one is feasible; unknown space, walls and physically blocked passages remain blocked.
- `SPEED`: positive absolute `speedLimitMps` up to 0.8 m/s, additionally bounded by the robot/workflow configuration. Pi applies both the Nav2 controller SpeedLimit and a final output cap, begins slowing before entry, takes the lowest overlapping speed rule and releases the limit after exit.

`GET /api/v1/robots/{robotId}/navigation-zones` returns expected/acknowledged profile hashes, `profileAcknowledged`, `zoneCount`, `restrictedZoneCount` and `speedZoneCount`. A count is configuration metadata; an offline Pi has not newly acknowledged that configuration.

The MQTT response schema explicitly permits validated zone polygons and speed fields. Unknown profile fields remain rejected. Public API counts do not add fields to MQTT messages.

## Endpoint types

The map editor supports WAYPOINT, PARK and CHARGER. A parking endpoint can populate an initialization estimate after the operator confirms the robot is actually there. Loading a map or selecting parking does not teleport/localize the robot or start motion. A charger endpoint is an arrival location, not a confirmation of electrical charging.

## Failure visibility

New failed jobs copy the terminal failed/timed-out step's error code into the job summary when no workflow-level error already exists. This makes errors such as PATH_UNAVAILABLE, ENDPOINT_IN_RESTRICTED_ZONE and FINAL_ALIGNMENT_BLOCKED visible without opening internal logs. Successful recovery branches and cancelled jobs do not acquire a spurious failure summary. Existing historical rows are not rewritten.

Unselected PENDING visits in terminal jobs are historical workflow records, not queued physical commands. Durable operation journals and stop-evidence requirements remain intact.

## Deployment and validation

Migration V38 adds storage for the currently verified planned path. Preserve database volumes, environment files and certificates when rebuilding the backend. The Pi change requires a new navigation session so its generated Nav2 YAML contains the matching planner, masks and arrival behavior.

Validation covers zone/profile hashing and wire schema, job failure summaries, synthetic browser navigation flows and the matching Pi's offline arrival/zone tests. Read-only Nav2 tests verified a footprint-safe detour in an open layout; this does not establish physical accuracy or hospital readiness. The latest Pi change still requires on-floor verification.
