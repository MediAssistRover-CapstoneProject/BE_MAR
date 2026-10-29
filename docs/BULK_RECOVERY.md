# Batch closure of recovery jobs

`POST /api/v1/jobs/recovery/bulk-close` requires an ADMIN bearer token.
This closes selected recovery jobs using the existing operation reconciliation rules;
it does not forcibly cancel every RUNNING job or bypass physical stop evidence.

1. List candidates using `GET /api/v1/jobs?status=RECOVERY_REQUIRED&size=50&page=0` (paginate as needed).
2. Read each job's `GET /api/v1/jobs/{jobId}/operations/recovery` evidence.
3. Submit up to 50 explicit items, with `dryRun: true` to inspect evidence without writes.
4. After obtaining actual stop evidence, submit `dryRun: false` with the same identities.

Request shape (replace placeholders with verified values):

```json
{
  "dryRun": true,
  "operations": [{
    "jobId": "JOB_UUID",
    "jobStepId": "STEP_UUID",
    "idempotencyKey": "recovery-batch-20261028-job-1",
    "recovery": {
      "commandId": "COMMAND_UUID",
      "operationKey": "EXACT_OPERATION_KEY_FROM_EVIDENCE",
      "transportSessionId": "CURRENT_SESSION_UUID",
      "evidence": "PI_STOP_CONFIRMED",
      "confirmedStopped": true,
      "outcome": "CONFIRM_CANCELLED",
      "note": "Describe the verified stop evidence"
    }
  }]
}
```

Do not use `confirmedStopped: true` to assert a stop that has not been verified.
`PI_STOP_CONFIRMED` requires the stored Pi stop receipt. `STAFF_INSPECTED_STOP`
also requires the existing authenticated Pi operator reconciliation evidence;
changing this enum alone does not unlock recovery.

Responses contain per-item `EVIDENCE_ONLY`, `CLOSED`, or `BLOCKED` plus a reason,
with `closed`/`blocked` totals. A 200 response does not mean every item closed.
Preview only retrieves evidence; apply rechecks eligibility under locks.
Each apply item commits independently. An unexpected server error can occur
after previous items committed: retry the exact request with the same per-item
idempotency keys. Do not reuse keys with changed request bodies.

Closing a job invalidates that robot's transport session by design. Subsequent
jobs for the same robot can be BLOCKED until a fresh HELLO and evidence arrive.
No robot motion command is sent by this endpoint. Jobs with missing/faded
heartbeat, missing stop receipts, or rejected-before-acceptance commands without
a terminal Pi journal are not force-closed. Existing single-job APIs still apply.

Validation: `BulkRecoveryServiceTest` covers read-only preview, partial business
failure continuation, preservation of per-item keys, and duplicate rejection
before any writes. Physical robot and end-to-end recovery remain runtime checks.
