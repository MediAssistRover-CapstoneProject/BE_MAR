# Battery warning and return API

Battery telemetry remains in `GET /api/v1/robots/{id}/battery`. Policy testing never writes a synthetic value to `Robot`, MQTT heartbeat JSON, raw voltage, measured percentage, or sensor capture time.

| Method and path | Access | Body and result |
| --- | --- | --- |
| `GET /api/v1/robots/{id}/battery-policy` | ADMIN, STAFF | Current policy status below; `Cache-Control: no-store`. |
| `GET /api/v1/robots/{id}/chargers` | ADMIN, STAFF | Array of `{endpointId, endpointCode, endpointName, mapId}`. Only active accessible endpoints of type `CHARGER` on the current ACTIVE navigation map. Endpoint names are not authority. |
| `POST /api/v1/robots/{id}/return-to-charger` | ADMIN | Optional `{endpointId}`. Omit the endpoint to select the first eligible charger in endpoint-code order. Returns current policy status; repeats while active return the same durable intent. |
| `POST /api/v1/admin/robots/{id}/battery-policy/test` | ADMIN | `{percent: 0..100, dryRun: true, endpointId?, ttlSec?: 15..300}`. `dryRun` defaults to **true**. Preview persists no policy/test/alert/job. `dryRun:false` explicitly executes the normal cancellation, warning and return flow with a temporary policy input. TTL defaults to 120 seconds. |
| `DELETE /api/v1/admin/robots/{id}/battery-policy/test` | ADMIN | Ends a synthetic test early. Any existing test operation is cancelled through normal `CANCEL_STEP` and stop settlement. A real critical episode is retained. Returns current status. |

The status object contains `robotId`, `state`, `episodeId`, `measuredPercent`, `measuredFresh`, `lowThreshold`, `criticalThreshold`, `rearmThreshold`, `foregroundBlocked`, `returnJobId`, `warningJobId`, `chargerEndpointId`, `blockedReason`, `lastEvaluatedAt`, `testMode`, `testExpiresAt`, `dryRun`, `requestedPercent`, `wouldAlert`, `wouldReturn`, and `eligibleChargers`. Only `requestedPercent` represents a synthetic test. `measuredPercent` remains the accepted fresh sensor estimate, or null when unavailable. The preview reports whether the requested percentage would warn/return and the current route or safety blocker; it does not predict future stop receipts.

States are `NORMAL`, `LOW`, `RETURN_REQUESTED`, `WAITING_FOR_STOP`, `BLOCKED`, `RETURNING`, `ARRIVED`, and `FAILED`. During the separate announcement job, state remains `RETURN_REQUESTED` and `warningJobId` identifies that job. `ARRIVED` means navigation arrival; there is no charging-contact sensor, so the backend never reports `CHARGING` based on arrival.

The defaults are warning at <=10%, critical at <=5%, rearm at >=15%. Only fresh accepted `ESTIMATED` voltage-curve telemetry can trigger automatic policy. Critical and rearm require distinct sensor captures spanning at least five seconds; repeated heartbeats of the same capture do not count. Missing, invalid and stale values do not become zero. Estimates with `curveCalibrated:false` remain estimates and have the same threshold policy; their accuracy still depends on battery profile calibration.

Configuration is available through `app.battery.low-percent`, `app.battery.critical-percent`, `app.battery.rearm-percent`, `app.battery.confirmation-sec`, and `app.battery.policy-check-ms` (default 2000). The existing `app.operations.scheduler-enabled` controls scheduled reconciliation.

Each episode persists its alert dedupe flags, critical latch, warning and return job IDs, selected charger, test expiry, and blocker in `RobotBatteryPolicy` (Flyway V69). The normal job runtime and MQTT outbox retain step and publication retry state across backend restart. Warnings reach administrators and any checked-in SOS responders; missing SOS staff or room/endpoint configuration does not prevent alert persistence.

Critical return cancels the active job through `JobCancellationService` and cancels the voice interaction lease. Logical cancellation alone does not permit a new job. After physical stop settlement and all cargo, incident, assisted-media and mapping holds are clear, a separate `INTERACT/SPEAK` job announces the return even if no charger or verified navigation is available yet. A completed failed TTS operation can proceed only when its uninterrrupted INTERACT outbox is settled and the robot is IDLE; cancelled or uncertain speech remains blocked.

The separate MOVE job requires an eligible `CHARGER`, current ACTIVE map, verified fresh navigation session and known free endpoint point. Automatic MOVE also requires a current usable battery estimate. Manual ADMIN return can operate with unknown battery when navigation is verified. The normal MQTT navigation profile, asset hash, endpoint scope, LiDAR, footprint and path safety gates still apply; this API introduces no new Pi motion contract. The conservative requested speed is 0.10 m/s, subject to normal lower limits.

New foreground jobs, voice wake, first MQTT publication and replay yield while critical/return policy owns the robot. CANCEL and mapping cleanup remain publishable. An execution test cannot mask real critical telemetry. Its expiresAt also caps the normal MQTT command deadline, so Pi rejects a command delayed beyond test TTL. Expiry or DELETE requests cancellation of any active test job and retains ownership until stop settles. Failed MOVE and uncertain operations require explicit review/retry; the scheduler does not blindly repeat motion.

Blocker codes include `WAITING_FOR_PHYSICAL_STOP`, `DELIVERY_RECOVERY_REQUIRED`, `INCIDENT_OR_MEDIA_HOLD`, `MAPPING_ACTIVE`, `ROBOT_UNAVAILABLE`, `NO_ACTIVE_MAP`, `MISSING_CHARGER`, `CHARGER_UNAVAILABLE`, `NAVIGATION_NOT_READY`, `BATTERY_TELEMETRY_UNAVAILABLE`, `RETURN_WORKFLOW_UNAVAILABLE`, `WARNING_JOB_FAILED`, `RETURN_JOB_MISSING`, `RETURN_JOB_FAILED`, and `TEST_EXPIRED`. Ordinary receipt waiting does not create a second blocked alert. A durable blocked alert updates its detail if the blocker changes.

No live motion or critical simulation is needed to review this change. Preview can be called safely; execute and manual return are physical operations and should only be exercised in an appropriate supervised robot test.
