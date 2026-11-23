# Job cancellation through MQTT

`POST /api/v1/jobs/{jobId}/cancel` with an authorized staff/admin bearer token and
`{"reason":"Stop requested"}` returns the job with `status: CANCELLED` immediately.
The existing job access rules still apply. A repeated request is idempotent.

## API for each stop state

After cancelling, `GET /api/v1/jobs/{jobId}` returns `job.stopStatus` and
`steps`; the cancel POST returns JobResponse directly.

- `NOT_REQUIRED`: cancellation is finished; no Pi recovery call is needed.
- `CONFIRMED`: settlement is finished, unless the selected step still has
  `RECOVERY_REQUIRED` and needs the operation recovery below.
- `PENDING`: keep/reconnect the owning worker, so BE can deliver CANCEL_STEP
  and receive the physical stop receipt. A repeated cancel is safe. Do not
  remove its journal or force status changes in SQL.
- `RECOVERY_REQUIRED`: read `GET /api/v1/jobs/{jobId}/operations/recovery`.
  Select the EXECUTE_STEP command in `commands`, not CANCEL_STEP. Use its
  commandId/operationKey/jobStepId and the current transportSessionId:

```http
POST /api/v1/jobs/{jobId}/operations/{jobStepId}/reconcile
Idempotency-Key: <new UUID, reused only for an identical retry>
Content-Type: application/json
Authorization: Bearer <Staff/Admin access token>
```

```json
{
  "commandId": "<EXECUTE_STEP commandId>",
  "operationKey": "<exact operationKey>",
  "transportSessionId": "<current transportSessionId>",
  "evidence": "PI_STOP_CONFIRMED",
  "confirmedStopped": true,
  "outcome": "CONFIRM_CANCELLED",
  "note": "Pi confirmed the operation stopped; settle cancellation."
}
```

PI_STOP_CONFIRMED requires committed Pi stop evidence, current HELLO, fresh
IDLE heartbeat, no open mapping/active step and terminal journal entries.
Recovery returns `requiresNewHello:true`; the worker must reconnect.

If a person inspected the stop but Pi lacks automatic stop evidence, stop
the stack and record that inspection inside MentorPi first:

```bash
cd /home/ubuntu/mediassist-mqtt-2026-10-08
PYTHONPATH=pi pi/.venv/bin/python -m mediassist.worker_cli reconcile \
  --config pi/config/mqtt.worker.json --job-step-id JOB_STEP_ID \
  --reason "Robot wheels and servo inspected and stopped"
```

Reconnect the worker to send OPERATOR_RECONCILED and terminal HELLO; then use
the same API/body with `evidence: STAFF_INSPECTED_STOP`. A browser flag alone
does not replace that receipt. Open SLAM requires mapping recovery separately.

ADMIN may close the narrower expired MOVE rejected before acceptance with
LOCALIZATION_NOT_READY using
`POST /api/v1/jobs/{jobId}/operations/{jobStepId}/close-rejected`, an
Idempotency-Key and `{ "commandId": "...", "operationKey": "...", "note": "..." }`.
BE verifies the stored rejection ACK; this is not an offline stop bypass.

No ordinary cancel-all API exists. Cancel explicit job IDs, and pause schedules
first if they must not create more jobs. Cancelling foreground work can resume
suspended patrol; cancel that patrol too when stopping everything. Cancelling
a MOVE job does not close its saved-map navigation session.

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

Schema 3 deliveries also retain `stopStatus: RECOVERY_REQUIRED` after physical stop
when the compartment was opened or cargo was loaded and receiver handover is incomplete.
An authorized Staff/Admin must remove the cargo and confirm the closed, locked compartment
with structured `deliveryRecovery` evidence; see [staff delivery recovery](STAFF_DELIVERY.md).
The next delivery and patrol cannot use this robot until that recovery and a new HELLO finish.

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
