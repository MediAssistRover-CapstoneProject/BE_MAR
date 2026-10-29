# Close a MOVE rejected before acceptance

POST /api/v1/jobs/{jobId}/operations/{jobStepId}/close-rejected
Authorization: ADMIN bearer token
Idempotency-Key: close-rejected-a25124ad-v1

For the recorded job:
- jobId: a25124ad-80b6-42c5-8cdf-665c1b732106
- jobStepId: 8d3e612d-9e55-425f-be62-18c260547571

```json
{
  "commandId": "3fa90698-9a4b-4cf1-8d29-9ce9f797d174",
  "operationKey": "a25124ad-80b6-42c5-8cdf-665c1b732106:MOVE_TO_ENDPOINT:1",
  "note": "Close MOVE rejected before execution: LOCALIZATION_NOT_READY"
}
```

The backend checks the stored, accepted MQTT inbox negative COMMAND_ACK, exact command/job/step/operation identity, LOCALIZATION_NOT_READY, an expired single-attempt MOVE, no started/acknowledged operation, and no other commands in this job. Missing or conflicting evidence is rejected with 422. It closes the recovery job as CANCELLED, preserving the original FAILED step and audit history. It neither fabricates physicalStopConfirmed nor changes robot readiness or sends movement commands. No fresh heartbeat, Pi stop journal, or transportSessionId is required for this specific unexecuted-command case. Retry the same body and key for the cached receipt.

This is not an offline force-cancel API for operations that started or might still be executing. Those still use normal reconcile with stop evidence.
