# Job cancellation through MQTT

`POST /api/v1/jobs/{jobId}/cancel` with an authorized staff/admin bearer token and
`{"reason":"Stop requested"}` returns the job with `status: CANCELLED` immediately.
The existing job access rules still apply. A repeated request is idempotent.

Physical completion is tracked separately:

| stopStatus | Meaning |
| --- | --- |
| NOT_REQUIRED | No operation was published and no mapping session remains open. |
| PENDING | Cancellation is committed; waiting for Pi stop evidence/cleanup. |
| CONFIRMED | All published operations have stop evidence or explicit reconciliation. |
| RECOVERY_REQUIRED | Stop cannot be established; inspection/recovery is needed. |

`stopRequestedAt` and `stopConfirmedAt` are UTC timestamps. Historical jobs may have
null stop fields; this migration does not invent hardware evidence for old jobs.
Pending/recovery jobs retain robot ownership. Do not treat `CANCELLED` alone as
permission to assign another job. A disconnected robot cannot be guaranteed to stop
at the instant an HTTP request returns.

The cancellation transaction revokes job operation grants, cancels pending work and
creates durable `CANCEL_STEP` commands for published operations. The existing outbox
publishes with QoS 1, without retained commands. The cancellation scheduler retries
settlement independently of normal workflow progression. Mapping STOP cleanup can
still execute; other execute commands are blocked. No broker-wide purge or forced
MQTT disconnection is performed.

Pi persists cancellation by job/step in SQLite before acknowledging. A delayed
execute command for that operation cannot restart it, including after restart.
Duplicate cancels reuse their saved receipt. BE accepts scoped late stop receipts
without restarting a cancelled job. On a fresh reconnect, an unacknowledged cancel
can be renewed only when HELLO contains the same job, step and operation key.
Uncertain hardware stops keep the robot unavailable and use the existing operation
recovery API; generic job status changes are not stop evidence.

## Fall incidents

`POST /api/v1/emergency-incidents/{incidentId}/cancel`
with `{"reason":"Staff checked the incident"}` is available to ADMIN and any active
STAFF, including staff not assigned to that incident. It only accepts suspected-fall
incidents. Response contains `incident` and `jobs`, including each job's stop status.
It closes the incident and alerts, cancels its related request and response jobs,
and releases its patrol hold. It does not cancel the originating patrol/delivery
job or disable continuous fall detection. Repeated cancellation is idempotent.

## Validation and rollout

Run `mvn -Dmaven.test.skip=false test` in BE_MAR. Tests cover immediate cancellation,
occupancy, authorization, late evidence, durable control creation, reconnect scope,
and incident cancellation. Deploy Flyway V63 with BE and the matching Pi code.

Pi's `PI_MAR/pi/tools/test_cancel_broker.py` is opt-in: set `MAR_TEST_MQTT_PORT` to an
isolated local Mosquitto port and run unittest discovery for that file. It uses real
MQTT and SQLite, with a simulated hardware stop. It is not a live BE-to-robot test.

Before hardware acceptance, test an active MOVE, a server-side authentication wait,
an open mapping session, repeated cancellation, MQTT outage/reconnect and Pi restart.
Check that no new job is dispatched before stop confirmation, and that cancellation
of a fall does not terminate the source job. Verify actual motors/servo separately;
software test success does not establish physical stopping performance.
