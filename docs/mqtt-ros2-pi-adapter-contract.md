# MQTT contract cho Pi/ROS 2 adapter

Contract hiện hành là `MAR-STEP-2`, MQTT 5, JSON, QoS 1 cho command/event bền vững. Backend gửi đúng một step đã resolve; adapter không nhận toàn bộ workflow và không tự chọn nhánh graph.

## EXECUTE_STEP

```json
{
  "protocolVersion": "1.0",
  "contract": "MAR-STEP-2",
  "messageType": "COMMAND",
  "commandId": "uuid",
  "correlationId": "uuid-same-as-commandId",
  "robotId": "uuid",
  "transportSessionId": "uuid",
  "commandType": "EXECUTE_STEP",
  "expiresAt": "2026-10-17T01:00:30Z",
  "step": {
    "jobId": "uuid",
    "jobStepId": "uuid",
    "stepId": "uuid",
    "stepCode": "MOVE_TO_ROOM",
    "stepType": "MOVE",
    "visitNo": 1,
    "operationKey": "job-id:MOVE_TO_ROOM:1",
    "timeoutSec": 120,
    "params": {}
  }
}
```

`jobStepId` là định danh step execution (`stepExecutionId` trong một số tài liệu tích hợp). Adapter phải deduplicate theo `commandId`, đồng thời journal theo `operationKey`. Không chạy command sau `expiresAt`. Retry publish giữ nguyên `commandId`, body và TTL ban đầu.

Adapter trả `COMMAND_ACK`, sau đó `STEP_STARTED`, cuối cùng đúng một trạng thái terminal `STEP_RESULT` hoặc `STEP_ERROR`. `STEP_STARTED.resolvedInputs` phải bằng chính xác `step.params`.

## STEP_RESULT và STEP_ERROR

Hai payload dưới đây nằm trong envelope event chuẩn có `messageId`, `operationKey`, `correlationId=commandId`, `robotId`, `jobId`, `jobStepId`, `occurredAt`, `producerSessionId`, `sequenceNo` và `transportSessionId`.

```json
{
  "stepType": "MOVE",
  "status": "SUCCEEDED",
  "result": {},
  "errorCode": null,
  "retryable": false,
  "details": null,
  "physicalStopConfirmed": true
}
```

```json
{
  "stepType": "MOVE",
  "status": "FAILED",
  "result": null,
  "errorCode": "PATH_UNAVAILABLE",
  "errorMessage": "Nav2 could not find a path",
  "retryable": false,
  "details": null,
  "physicalStopConfirmed": true
}
```

Backend vẫn nhận event cũ không có `stepType`/`errorMessage`; event mới có `stepType` sẽ bị reject nếu khác snapshot đã dispatch. `result` luôn qua validator riêng của StepType. `physicalStopConfirmed` đặc biệt quan trọng với `MOVE`, `CONTROL_HARDWARE` và mapping; thiếu bằng chứng dừng có thể đưa Job sang `RECOVERY_REQUIRED`.

## Contract theo StepType

| StepType | `params` đầu vào | `STEP_RESULT.result` thành công | `errorCode` gợi ý |
|---|---|---|---|
| `AUTHENTICATE` | `purpose`, `service_request_id` | `verified`, `verification_id`, `actor_account_id`, `purpose`, `service_request_id`, `verified_at`, `valid_until` | `AUTH_FAILED`, `AUTH_EXPIRED`, `AUTH_CANCELLED`, `STAFF_NOT_AUTHORIZED` |
| `CONTROL_HARDWARE` | `compartment_id=CART_MAIN`, `action=LOCK_COMPARTMENT\|UNLOCK_COMPARTMENT`; unlock có `authorization` do BE cấp | `published`, `controller_ack`, `verified`, `action`, `compartment_id`, `position_raw`, `ros_servo_id`, `authorization_mode` | `CONTROLLER_UNAVAILABLE`, `SERVO_TIMEOUT`, `LOCK_JAMMED`, `AUTHORIZATION_INVALID`, `UNSAFE_TO_ACTUATE` |
| `MOVE` | `map_id`, `endpoint_id`, `mode=NORMAL\|ESCORT\|PATROL`, `max_speed_mps` | `reached=true`, `endpoint_id`, `action_status=4`, `attempts`, `last_feedback_pose`, `distance_remaining_m` | `PATH_UNAVAILABLE`, `NAV2_REJECTED`, `NAV2_ABORTED`, `LOCALIZATION_LOST`, `MAP_MISMATCH`, `OBSTACLE_TIMEOUT` |
| `WAIT` | `wait_type=DURATION,duration_sec` hoặc `wait_type=EVENT,event_type,correlation_key` | `completed=true`, `wake_reason`, `elapsed_sec`, `event` (`null` cho DURATION) | `WAIT_CANCELLED`, `EVENT_TIMEOUT`, `EVENT_SOURCE_UNAVAILABLE` |
| `INTERACT` | `prompt_key`, `action=SPEAK\|DISPLAY\|CAPTURE_INPUT\|CONFIRM`, `data` và các field scope tùy prompt | `completed`, `prompt_key`, `action`, các field `confirmed/closed/locked/values`, `source`, `actor_account_id`, `completed_at` | `DISPLAY_UNAVAILABLE`, `AUDIO_UNAVAILABLE`, `INPUT_TIMEOUT`, `USER_CANCELLED`, `STAFF_CONFIRMATION_REQUIRED` |
| `HANDLE_PAYLOAD` | `action=LOAD\|UNLOAD`, `service_request_id`, `compartment_id`, `items[{item_code,quantity}]` | `confirmed=true`, cùng action/request/compartment/items, `actor_account_id`, `confirmed_at`, `source`, `note` tùy chọn | `PAYLOAD_MISMATCH`, `COMPARTMENT_OPEN`, `STAFF_CONFIRMATION_REQUIRED`, `LOAD_CANCELLED` |
| `DATA_SERVICE` | `service_key` và fields tương ứng: query information, resolve/select/check target, create incident hoặc commit map artifact | `request_id`, `data` đúng subtype của `service_key` | `SERVICE_UNAVAILABLE`, `INVALID_SERVICE_KEY`, `TARGET_NOT_FOUND`, `INCIDENT_CREATE_FAILED`, `ARTIFACT_COMMIT_FAILED` |
| `SET_CONTEXT` | `operations[{path:/vars/<name>,operation:SET\|INCREMENT,value}]` | `updated` chứa đúng các key đã yêu cầu | `INVALID_CONTEXT_PATH`, `TYPE_MISMATCH`, `CONTEXT_CONFLICT` |
| `CHECK_CONDITION` | `expression{path,operator,value?}` | `value:boolean` | `INVALID_EXPRESSION`, `CONTEXT_VALUE_MISSING`, `TYPE_MISMATCH` |
| `EXECUTE_SENSING` | `sensing_type`; tùy loại có `zone_id`, `window_sec` hoặc mapping `action`, `mapping_session_id`, `artifact_id`, `preview_rate_hz` | `sensing_type`, `metrics`, `captured_at`, `evidence_ref`; AI/mapping có thêm identity và measurement đã định nghĩa trong schema | `SENSOR_UNAVAILABLE`, `SENSOR_STALE`, `MODEL_UNAVAILABLE`, `MAPPING_FAILED`, `ARTIFACT_UPLOAD_FAILED` |
| `NOTIFY` | `notification_type`, `severity`, `data`, `require_ack`, tùy chọn `message`, `ack_policy`, `recipients` | `alert_id`, `notification_type`, `persisted=true`, `requires_ack` | `DELIVERY_FAILED`, `RECIPIENT_UNAVAILABLE`, `ACK_TIMEOUT`, `NOTIFICATION_REJECTED` |

Các JSON Schema chính thức nằm tại `src/main/resources/contracts/mar-step-2/`. Bảng trên là hướng dẫn adapter; schema và validator backend là nguồn quyết định cuối cùng.

## MOVE → ROS 2 Nav2

Contract hiện tại dùng navigation profile `BE-NAV-PROFILE-1`:

1. Adapter tải profile cho robot qua REST và kiểm `map_id`, `map_asset_sha256`, `frame_id`.
2. Profile ánh xạ mỗi `endpoint_id` sang `x_m`, `y_m`, `yaw_rad`.
3. Adapter ACK `profileHash`; backend chỉ dispatch MOVE khi map/hash/profile khớp session hiện tại.
4. Khi nhận MOVE, adapter tra `endpoint_id` trong profile, tạo pose trong `frame_id`, đổi `yaw_rad` thành quaternion và gọi action `/navigate_to_pose` (`nav2_msgs/action/NavigateToPose`).
5. Nav2 success mới trả `reached=true`, `action_status=4`; cancel/abort phải dừng robot trước khi đặt `physicalStopConfirmed=true`.

Backend hiện không gửi trực tiếp `x/y/yaw` trong MOVE command. Các tọa độ nằm trong profile đã được hash/ACK để command nhỏ và không bị diễn giải lại giữa các lần retry.

## Lưu ý authority và side effect

Bật một StepType trong `MQTT_ENABLED_STEP_TYPES` là opt-in thay đổi nơi thực thi. Adapter không được tự coi output là quyền Staff:

- `AUTHENTICATE` qua MQTT không tự tạo `AuthVerification` đáng tin cậy để mở khóa; grant unlock vẫn phải do BE tạo và bind.
- `HANDLE_PAYLOAD`/các prompt Staff phải có quy trình actor proof phù hợp; không dùng callback robot để giả mạo xác nhận.
- `NOTIFY` hoặc `DATA_SERVICE` chạy ở adapter không tự tạo mọi entity domain mà server handler local vốn tạo. Chỉ bật khi adapter/integration tương ứng đã được commissioning.
- `SET_CONTEXT` result hợp lệ được backend áp vào `Job.contextJson`; adapter không được cập nhật key ngoài `operations`.

## HELLO capability

`HELLO.payload.enabledStepTypes` chỉ công bố những loại adapter thực sự hỗ trợ. Backend yêu cầu đồng thời: rollout không halt, MQTT/dispatch bật, robot được enroll, session/heartbeat hợp lệ, StepType có trong cấu hình BE và HELLO. Không điều kiện nào được tự bật trong code.
