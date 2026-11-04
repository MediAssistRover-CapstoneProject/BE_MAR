# Robot test workflows

Use `/mapping.html#robotTestWorkflow` with an ADMIN token. No command is sent on page load or preview.

Supported items, executed in order in one schema 3 workflow/job:

- `MOVE`: `endpointId` on the selected map; uses the existing READY navigation session and Nav2 path.
- `WAIT`: `durationSec` between 1 and 300; Pi executes WAIT/DURATION.
- `OPEN_SERVO` / `CLOSE_SERVO`: CONTROL_HARDWARE on `compartmentId` (default `CART_MAIN`).
- `WAIT_CONFIRM`: BE WAIT/EVENT, timeout 600 seconds; the operator explicitly confirms in the HTML page.

The existing Pi contract is MG996R, S6, ROS servo ID 4, open raw 2400, close raw 1500. There is no physical position feedback; job success confirms publication, not a mechanically verified door position. Opening requires BE `MQTT_ALLOW_COMMISSIONING_UNLOCK=true` and Pi `unlock_policy=COMMISSIONING_ONLY`. These are commissioning flows, not the staff identity verification/delivery handover flow.

All APIs below require ADMIN:

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/robots/{robotId}/test-configuration` | Read BE allowlist, last Pi HELLO policy, fixed servo contract |
| POST | `/api/v1/test-workflows/preview` | Return graph JSON; no database writes or commands |
| POST | `/api/v1/test-workflows/run` | Generate/reuse published graph and create one job |
| GET | `/api/v1/test-workflows/by-idempotency-key?key=...` | Recover a timed-out submission without dispatch |
| POST | `/api/v1/robots/{robotId}/servo-test` | One servo job; body `{"action":"OPEN","compartmentId":"CART_MAIN"}` or `CLOSE` |

Both execution POSTs require `Idempotency-Key` (1–150 safe characters). Retries from the same account reuse the original job, including after completion; a changed body with the same key returns 409. Creating a new physical action requires a new key. Preview does not reserve a key or job.

Example preview/run body (replace IDs with real catalog/session values):

```json
{
  "robotId": "0e3f1083-2546-4f45-8004-5eda5cb75a6f",
  "mapId": "e571fe71-d3ea-4aa5-a673-ffd857ac9f09",
  "navigationSessionId": "00000000-0000-4000-8000-000000000001",
  "maxSpeedMps": 0.4,
  "compartmentId": "CART_MAIN",
  "steps": [
    {"kind":"MOVE","endpointId":"00000000-0000-4000-8000-000000000002"},
    {"kind":"WAIT","durationSec":5},
    {"kind":"OPEN_SERVO"},
    {"kind":"WAIT_CONFIRM"},
    {"kind":"CLOSE_SERVO"}
  ]
}
```

`mapId` and `navigationSessionId` can be omitted when there is no MOVE. Preview permits an absent navigationSessionId; execution requires READY for MOVE. The server checks endpoint/map identities and takes the same robot/job locks as ordinary jobs. It never creates or reinitializes an AMCL session.

Track with `GET /api/v1/jobs/{jobId}`. Cancel with `POST /api/v1/jobs/{jobId}/cancel`, body `{"reason":"Stop test"}`; wait for the job/Pi result. No servo close command is inferred from cancellation or a page closing.

To confirm, use the **currently selected RUNNING** WAIT/EVENT step. POST `/api/v1/jobs/{jobId}/events` with a unique eventId, `eventType=TEST_OPERATOR_CONFIRMED`, the step's resolved `correlation_key`, current occurredAt and object data. Each confirmation step has a separate job-specific random correlation key. The HTML button supplies these values and does not confirm future steps.
