# One workflow visiting multiple endpoints

Use the existing MOVE step once per stop. A route A, B, C, D is one Job with
MOVE_01 through MOVE_04 in that order. Every step uses the same robot, map and
navigation session. Pi continues tracking position; it must not reset AMCL
between steps. Completing A is not completion of the Job.

Only SUCCESS advances to the next step. FAILURE terminates the Job as FAILED;
TIMEOUT requires recovery/stop confirmation. Cancellation stops the current
operation and does not dispatch later stops. Every MOVE has its own jobStepId,
operationKey and path receipt. Endpoint groups are alternative destinations,
not a sequence of stops.

## Create and submit a route in one call

ADMIN: POST `/api/v1/navigation-routes`, with a new `Idempotency-Key` header:

```json
{
  "robotId": "<robot UUID>",
  "mapId": "<map UUID>",
  "navigationSessionId": "<current READY session UUID>",
  "endpointIds": ["<A UUID>", "<B UUID>", "<C UUID>", "<D UUID>"],
  "maxSpeedMps": 0.60,
  "submit": true
}
```

Supply 1..20 saved endpoint IDs in visit order. BE creates, validates and
publishes the matching workflow, then creates and submits one request. There
is no workflow ID to configure manually. The response includes `workflowId`,
the ordered `endpointIds`, and `request`. `submit` defaults to true; false
creates a draft. Retry the identical body with the identical key to retrieve
the same request, without creating another workflow or Job. Changed bodies
need a new key.

A robot has one navigation session at a time. Reuse it for successive routes
on the same map while it remains open and READY; do not reopen or reinitialize
AMCL between stops. Two independently submitted requests for a pinned robot
are not a queue: while its Job is active or unreconciled, another submission
is rejected (409 for an occupied Job, or 422 if the robot is not IDLE). Combine known stops into one route, or submit the next
request after the previous Job is terminal and its physical stop is confirmed.
The session cannot be replaced or closed while an active Job owns it.

For example, first send `endpointIds: [A]`. After that Job succeeds, send
`endpointIds: [B]`, or `[B,C,D]`, with the same robot/map/navigationSessionId
and a NEW Idempotency-Key. This creates a new Job without restarting Pi,
reopening the map, reseeding localization or changing the session revision.
Completing a route does not close its navigation session. If the session has
expired, was explicitly closed, or Pi restarted, open a new session instead.

Each MOVE endpoint is a confirmed arrival before the next step starts.
The 2026-10-31 position-only Pi patch defaults to a 6 cm Nav2 position
tolerance, verifies the stopped live position within 8 cm and does not enforce
endpoint yaw or issue a final Spin. Actual robot yaw remains in telemetry and
`verified_pose`; optional `yaw_error_rad` is omitted when heading is not verified.
An explicit `--arrival-mode POSITION_THEN_SPIN` retains strict heading mode.
This can include a brief stop at the endpoint;
it is not a pass-through waypoint or a guarantee of uninterrupted wheel motion.
Obstacle checks, localization/TF validity, restricted zones and explicit
cancellation remain in force throughout movement.

On the mapping test page, the saved-zone checkbox now persists activation
immediately. Draft polygon/speed/name edits still require Save. The page shows
BE's active rule count separately from Pi's profile acknowledgement; clearing
a checkbox on an older page without saving did not deactivate the stored zone.
Changing a rule does not authorize dispatch until the new profile is acknowledged.

`GET /navigation-route-workflow.json` provides the four-stop graph for Swagger.
For another number of stops, run `python scripts/create_endpoint_route.py --stops 6 --out endpoint-route`.
The helper only writes JSON; it does not move the robot.

## Prepare in Swagger

1. POST `/api/v1/admin/workflows/graph` with the generated `workflow.json`.
2. POST `/api/v1/admin/workflows/{workflowId}/validate`, then `/publish`.
3. Open ONE navigation session for the chosen map and robot. Run its Pi command,
   initialize position and wait for READY and profile ACK.
4. POST `/api/v1/navigation-requests` with a fresh Idempotency-Key. Use the
   published workflowId, robotId, navigationSessionId, mapId,
   targetMode ENDPOINT, navigationMode STANDARD, passengerCount 1.
   Set destinationEndpointId to D. Set `data.route_waypoint_ids` to [A,B,C].
5. POST `/api/v1/service-requests/{serviceRequestId}/submit` once after creation.
   Do not create a separate request for each intermediate stop.

All endpoints must be saved, active, accessible and on this map. A white centre
pixel does not establish that the oriented robot footprint fits at the point.
Restricted zones and speed limits apply independently on every leg.

The sample cap is 0.60 m/s and remains bounded by Pi hardware, zones and obstacles.
This is not a patient escort speed recommendation; the existing ESCORT contract
has its own walking-speed range and staff/patient interaction requirements.

If the BE database changes, workflow, map, endpoint and session IDs must come from
the new database. Identical .env credentials do not copy these records. Keep a
single authoritative BE for a given robot MQTT lane; two independent BEs must
not dispatch commands to the same robot simultaneously.
