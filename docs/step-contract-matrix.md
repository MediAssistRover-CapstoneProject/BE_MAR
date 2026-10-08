# Phase 16 — Hợp đồng thực thi 11 StepType

Cập nhật: 2026-10-08. Tài liệu này là contract Backend đang thực thi, không phải mã điều khiển Raspberry Pi/ROS2.

## 1. Phiên bản và nguyên tắc

- `protocolVersion = 1.0`, `schemaVersion = 2`, `graphVersion = 3` là bộ phiên bản được Backend hiện tại hỗ trợ.
- Workflow chỉ được validate/publish khi đúng `schemaVersion=2`, `graphVersion=3`. Kết quả runtime được kiểm theo version đã snapshot trong `Job`; version không hỗ trợ bị từ chối rõ ràng.
- `StepType` là enum cố định gồm đúng 11 giá trị. Không có bảng `StepType`, không có API create/update/delete. API `GET /api/v1/admin/workflows/step-types` chỉ đọc enum.
- Mọi `STEP_RESULT` phải là JSON object, đúng kiểu dữ liệu, đủ field bắt buộc và không có field root lạ. JSON khác `null` không đồng nghĩa thành công.
- Namespace mapping được khóa theo implementation hiện hữu: `/inputs`, `/outputs/{STEP_CODE}`, `/vars`. Phase này không đổi âm thầm sang JSONPath hoặc `/input`.
- Điều kiện hiện tại là compatibility profile `POINTER_CONDITION_V1`: `path`, `operator`, `value`; operator `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `EXISTS`; tối đa 128 node. Đây chưa phải cây biểu thức `SAFE_EXPR_V1` đầy đủ.

## 2. Ma trận contract

| StepType | Executor | Cấu hình/input chính | Kết quả thành công (root fields) | Kiểm tra ngữ nghĩa chính |
|---|---|---|---|---|
| `MOVE` | PI | `endpoint_id`; tùy chọn `mode=NORMAL/ESCORT/PATROL`, `max_speed_mps` `(0,0.3]` | `reached`, `endpoint_id`, `action_status`, `last_feedback_pose`, `distance_remaining_m` | `reached=true`, `action_status=4`, endpoint trùng resolved input; pose nếu có gồm `x_m/y_m/yaw_rad` |
| `INTERACT` | SERVER, callback Device khi cần UI/audio | `prompt_key`; action `SPEAK/DISPLAY/CAPTURE_INPUT/CONFIRM` | `completed`, `confirmed`, `input_data`, `actor_user_id`; có thể thêm `closed`, `locked` cho prompt bảo vệ | `completed=true`; `CONFIRM` cần boolean; xác nhận cửa/khóa cần actor Staff/Admin theo Phase 15 |
| `DATA_SERVICE` | SERVER | `service_key` trong registry; operation tùy chọn `QUERY/CREATE/UPDATE`; cấm `url/sql/script/code` | `data`, `request_id` | Output shape theo service; request identity bắt buộc |
| `AUTHENTICATE` | SERVER/Staff | `purpose`; method `QR_CODE/STAFF_CONFIRMATION` | `authenticated`, `verified`, `verification_id`, `actor_user_id`, `valid_until`, `purpose` | cả `authenticated` và compatibility alias `verified` phải true; grant đúng actor/purpose và còn hạn |
| `CONTROL_HARDWARE` | PI | action `LOCK/UNLOCK` hoặc alias hiện hữu `LOCK_COMPARTMENT/UNLOCK_COMPARTMENT`; `maxAttempts=1` | `published`, `controller_ack`, `verified`, `commanded_state`, `position_raw`, `ros_servo_id` | publish phải true; state khớp action; position 0..2500; unlock cần `verification_id` và `purpose`; không auto retry lệnh phần cứng |
| `HANDLE_PAYLOAD` | SERVER/Staff | action tùy chọn `LOAD/UNLOAD/VERIFY` | `confirmed`, `verification_mode`, `actor_user_id`, `items`, `weight_kg`, `temperature_c` | confirmed true; actor bắt buộc; item code không rỗng và quantity >= 1 |
| `EXECUTE_SENSING` | MIXED | `sensing_type` allowlist; `window_sec` 0..30 | `task_type`, `metrics`, `captured_at`, `evidence_ref` | metric theo loại: health, clearance, crowd hoặc restricted-area; timestamp ISO-8601 |
| `NOTIFY` | SERVER | `notification_type`, `recipients[]`; tùy chọn severity và `require_ack` | `notification_id`, `delivered`, `acked` | success cần delivered true; `require_ack=true` cần acked true. Chỉ lưu notification chưa được coi là đã giao/ACK |
| `WAIT` | MIXED | `wait_type=DURATION/EVENT`; duration 0..3600 hoặc `event_type`; timeout lớn hơn duration | `completed`, `wake_reason`, `elapsed_sec`, `event` | completed true; elapsed >= 0; DURATION có event null; EVENT có identity/correlation/job/actor/time |
| `CHECK_CONDITION` | PI | `expression` theo `POINTER_CONDITION_V1` | `value` | `value` phải boolean; `false` là kết quả hợp lệ để chọn nhánh, không phải schema error |
| `SET_CONTEXT` | PI | `operations[]`, path `/vars/{identifier}`, operation `SET/INCREMENT`, value | `changed_keys`, `values` | key duy nhất và tập `changed_keys` phải đúng bằng field names trong `values` |

## 3. DATA_SERVICE registry

Backend chỉ cho phép các key được đăng ký trong `DataServiceStepHandler`:

- `QUERY_HOSPITAL_INFORMATION`: `data.items[]`, `data.count`.
- `RESOLVE_TARGETS`: `data.targets[]`.
- `SELECT_TARGET`: `data.has_target`.
- `CHECK_TARGET`: `data.is_valid`.
- `CREATE_FALL_INCIDENT`: `data.incident_id`, `data.created`.

Workflow không được nhúng URL, SQL, script hoặc code tùy ý trong params/input mapping.

## 4. Error contract

- Sai version, thiếu field, sai kiểu, field root lạ hoặc vi phạm ngữ nghĩa trả lỗi nghiệp vụ chuẩn qua `GlobalExceptionHandler`; không ghi Step thành `SUCCEEDED`.
- Callback Device không được hoàn tất Step thuộc Staff/Server trái quyền; kiểm tra executor/actor của Phase 15 vẫn chạy trước contract result.
- Gửi lại cùng operation/message tiếp tục tuân theo idempotency hiện hữu. Result sai bị rollback và `JobStep` giữ trạng thái trước callback.
- Không đưa stack trace hoặc nội dung nhạy cảm ra response.

## 5. Compatibility và giới hạn đã công bố

- Graph đã có sử dụng `LOCK_COMPARTMENT`/`UNLOCK_COMPARTMENT`, `SET_CONTEXT.operations`, input `sensing_type`, và output AUTH alias `verified`; Phase 16 hỗ trợ rõ các alias này, không sửa graph đã publish.
- Contract nguồn mới có một số cách đặt tên khác (`LOCK/UNLOCK`, `assignments`, `task_type`, `/input`, cây SAFE_EXPR). Đây là delta đã ghi nhận, không được trộn lẫn trong cùng `schemaVersion=2`.
- `NotifyStepHandler` trả `completed=false` sau khi mới lưu bản ghi; Step chỉ được thành công khi delivery/ACK contract hoàn tất. Lifecycle event/ACK đầy đủ thuộc Phase 17.
- Phase 16 không thêm bảng, không sửa Flyway V1–V15, không thay 11 enum và không triển khai phần cứng.

## 6. Điểm kiểm thử

- Unit fixture hợp lệ cho đủ 11 StepType.
- Negative cases: field lạ, field bắt buộc null/thiếu, sai kiểu số, MOVE chưa đến đích, confirmation false, output/data shape sai và version không hỗ trợ.
- Integration: result sai bị từ chối trong transaction và không đổi `JobStep` sang success; Information/Escort hiện hữu vẫn chạy end-to-end với result đúng contract.
- Regression toàn bộ project phải đạt trước khi Phase 16 được đánh dấu DONE.
