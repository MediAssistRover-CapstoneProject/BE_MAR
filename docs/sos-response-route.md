# SOS confirmation and responder route

Admin can inspect current routing eligibility with `GET /api/v1/emergency-incidents/{id}/routing`.
The response includes the saved `incident`, current `eligibility` (room ID/name, reason,
candidate account and check time), and `canRetry`. Inspection does not assign anyone.

For an OPEN, unarchived SOS or suspected fall with no responder or acknowledgement,
Admin can call `POST /api/v1/emergency-incidents/{id}/retry-routing` with
`{"rowVersion":3}` using the incident version from the inspection. The server locks the
incident, rechecks eligibility, audits the attempt, and returns the same response shape.
Stale versions or already-claimed/terminal incidents return 409. Repeating a successful
assignment returns the existing responder without duplicating notifications or media.
A failed eligibility check returns its current reason and leaves the incident unassigned.

The monitoring room must have an enabled endpoint on an ACTIVE map; when the incident
has a map, its room must also have an enabled endpoint on that exact ACTIVE map ID.
Saving a monitoring room whose endpoints are only on inactive maps is rejected before
changing the global SOS setting. Exactly one eligible Staff account must be checked in
to an ACTIVE shift in that room, within its actual time interval and not checked out.
Successful retry adds the responder to the existing SOS notification, opens the normal
SOS media flow where available, and makes the original automatic SOS request eligible
for the existing dispatch scheduler. Suspected falls retain their camera review and
confirmation requirements and never gain automatic robot dispatch from routing retry.

The confirmation and route endpoints below require a valid ADMIN or STAFF bearer token and incident access.
Confirmation requires ADMIN or the assigned SOS responder.

1. Call `POST /api/v1/emergency-incidents/{id}/confirm` with an empty body.
   OPEN incidents are acknowledged and moved to IN_PROGRESS atomically.
   ACKNOWLEDGED incidents move to IN_PROGRESS. Repeating a confirmed request returns the current incident.
   The SOS_CONFIRMED audit event records the actor and timestamp.
2. For a false alarm, call `POST /api/v1/emergency-incidents/{id}/false-alarm` with
   `{"resolutionNote":"Staff verified this was an accidental button press"}`.
3. Call `GET /api/v1/emergency-incidents/{id}/response-route` after confirmation.

Example successful route, using the saved map coordinate frame and centimetres:

```json
{
  "status": "FOUND",
  "reason": null,
  "mapId": "11111111-1111-4111-8111-111111111111",
  "mapAssetHash": "<verified map asset hash>",
  "unit": "cm",
  "origin": {"xCm": 5, "yCm": 5},
  "destination": {"xCm": 25, "yCm": 5},
  "points": [
    {"xCm": 5, "yCm": 5},
    {"xCm": 5, "yCm": 5},
    {"xCm": 15, "yCm": 5},
    {"xCm": 25, "yCm": 5},
    {"xCm": 25, "yCm": 5}
  ],
  "distanceCm": 20
}
```

The route selects the shortest reachable active, accessible endpoint of the configured
SOS monitoring room on the incident map. Its destination is the stored incident xM/yM.
A* minimizes distance over eight-connected free cells, including segments connecting the
exact endpoints to their cell centres. Diagonals require both adjoining cells to be free.
Unknown and occupied cells are blocked. Identical consecutive points can occur when an
exact coordinate coincides with a cell centre. Resolution remains that of the saved map.
Map origin translation and rotation apply before converting metres to centimetres.

An unavailable route returns HTTP 200 with status UNAVAILABLE, an empty points array,
null origin/distanceCm, and a reason:
MAP_MISSING, SOS_POSITION_MISSING, MONITORING_ROOM_MISSING, MONITORING_ROOM_INVALID,
MONITORING_ROOM_UNAVAILABLE, DIFFERENT_FLOOR, ORIGIN_ENDPOINT_MISSING_ON_MAP,
MAP_DATA_UNAVAILABLE, SOS_POSITION_OUTSIDE_MAP, SOS_POSITION_NOT_FREE,
ORIGIN_POSITION_NOT_FREE, or NO_PATH.
The destination and map identity are included where available.
Route failures do not undo confirmation.
Missing/archived incidents return 404; unsupported incident type/status follows the existing business-rule error response.

The API returns map-frame centimetres. App rendering converts them to metres or map pixels
using the map metadata. The stored incident and existing map APIs continue to use metres.
The route uses saved static occupancy and the incident's recorded position.
