# SOS confirmation and responder route

All endpoints require a valid ADMIN or STAFF bearer token and incident access.
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
