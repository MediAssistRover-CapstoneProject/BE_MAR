# Team Pi contract review and hardware acceptance

Cập nhật: 2026-10-06

## 1. Pre-publish review của 8 DRAFT seeds

Phạm vi review là contract `input → action → output` giữa backend và Pi runtime. Migration V13 chỉ nâng cấp workflow còn ở trạng thái `DRAFT`; không validate hoặc publish tự động. Endpoint validate/publish vẫn là thao tác Admin riêng.

| Workflow | Input bắt buộc | Action Pi/runtime chính | Output Pi phải report |
|---|---|---|---|
| `WF_PATIENT_ESCORT` | `map_id`, `endpoint_id`; `patient_reference` tùy chọn | `INTERACT/CAPTURE_INPUT`, `CHECK_CONDITION`, `MOVE`, `INTERACT/DISPLAY` | `CONFIRM.confirmed:boolean`; MOVE success hoặc error; ARRIVED success |
| `WF_INFORMATION_ASSISTANCE` | `question` | server `QUERY_HOSPITAL_INFORMATION`; `INTERACT/DISPLAY` | `QUERY.data.items[]`; ANSWER success |
| `WF_SECURE_MEDICAL_DELIVERY` | `map_id`, `pickup_endpoint_id`, `delivery_endpoint_id`, `service_request_id`, `compartment_id` | MOVE; staff authentication; `UNLOCK_COMPARTMENT`/`LOCK_COMPARTMENT`; payload confirmation; close/lock confirmation | AUTH `verified:boolean`; PAYLOAD `confirmed:boolean`; CLOSE `closed:boolean`; VERIFY `locked:boolean`; hardware/MOVE success hoặc typed error |
| `WF_EMERGENCY_SOS_RESPONSE` | `incident_id`, `message` | NOTIFY; `INTERACT/CAPTURE_INPUT`; CHECK; `INTERACT/DISPLAY`; `WAIT/EVENT:STAFF_ARRIVED` | REMOTE_REVIEW `confirmed:boolean`; WAIT success khi đúng event; error/timeout có mã |
| `WF_CROWD_MONITORING` | `map_id`, `checkpoint_id`, `zone_id`; default `warning_count=10`, `max_loops=5` | MOVE; `EXECUTE_SENSING/CROWD_DENSITY`; CHECK; `WAIT/DURATION` | SENSE `metrics.count:number` (và nên có `captured_at`); CHECK `value:boolean` |
| `WF_FALL_RESPONSE` | `detection_event_id`, `confidence`; default `min_confidence=0.8` | CHECK; `INTERACT/CAPTURE_INPUT`; server idempotent `CREATE_FALL_INCIDENT`; NOTIFY; `WAIT/EVENT:STAFF_ARRIVED` | STAFF_REVIEW `confirmed:boolean`; CREATE `data.incident_id`; WAIT success/error |
| `WF_RESTRICTED_MONITORING` | `map_id`, `checkpoint_id`, `zone_id` | MOVE; `EXECUTE_SENSING/RESTRICTED_ZONE`; CHECK; warning DISPLAY; NOTIFY | SENSE `metrics.intrusion_detected:boolean`; CHECK `value:boolean` |
| `WF_NAVIGATION` | `map_id`, `target_mode` và đúng một trong `endpoint_id`/`endpoint_group_id` | CHECK; server resolve/select/check target; DISPLAY; MOVE | CHECK `value:boolean`; MOVE success; lỗi đường bị chặn dùng `error.code=PATH_BLOCKED` |

Quy ước chung:

- Pi lấy `stepType`, `params`, resolved input và `operationKey` từ job command; mọi callback phải giữ nguyên job/step/operation identity.
- Step thành công report payload vào `result`; backend lưu thành `/outputs/STEP_CODE/...`. `CHECK_CONDITION` phải trả `result.value:boolean` để transition chọn nhánh.
- Không tự retry `CONTROL_HARDWARE`; `max_attempts=1`. Duplicate operation phải được Pi xử lý idempotent theo `operationKey`.
- Event wait chỉ hoàn tất khi nhận đúng `event_type`. Timeout/error phải report rõ, không giả lập success.
- `CREATE_FALL_INCIDENT` chỉ nhận DetectionEvent `SUSPECTED_FALL`, kiểm tra cùng Robot và tái sử dụng incident đã gắn với detection đó.

Gate tự động khi Admin validate workflow hiện chặn: service key server không hỗ trợ, thiếu config bắt buộc theo StepType, `$ref` tới input chưa khai báo/step không tồn tại, hardware action lạ, WAIT/CHECK/SET_CONTEXT sai contract. Tám seed vẫn giữ `DRAFT` sau migration.

## 2. Hardware acceptance — chạy độc lập

Trạng thái hiện tại: **NOT RUN**. Repository/backend không có kết nối tới Raspberry Pi, ROS/Nav2, camera, servo/lock hoặc thiết bị test thật trong môi trường này; vì vậy không được ghi PASS bằng test MockMvc/MySQL.

Ghi nhận môi trường trước khi chạy:

| Trường | Giá trị |
|---|---|
| Robot ID / serial | `TBD` |
| Pi runtime / firmware | `TBD` |
| Backend commit / image digest | `TBD` |
| `protocolMajor`, schema/graph version | `TBD` |
| Map ID / map asset hash | `TBD` |
| Catalog hash | `TBD` |
| Người thực hiện / thời gian | `TBD` |

### Bộ ca acceptance

| ID | Ca kiểm thử thật | Tiêu chí PASS | Trạng thái |
|---|---|---|---|
| HW-01 | Heartbeat và nhận job command | Robot online; hash/version đúng; command identity đúng | NOT RUN |
| HW-02 | MOVE tới pickup/destination/escort/checkpoint | Dừng đúng sai số đã thống nhất; obstacle/path error đúng mã | NOT RUN |
| HW-03 | Emergency stop/cancel ACK trong khi MOVE | Phần cứng dừng an toàn; backend chỉ chuyển trạng thái sau ACK | NOT RUN |
| HW-04 | Lock/unlock compartment, kể cả duplicate command | Một tác động vật lý cho mỗi operation; trạng thái khóa được cảm biến xác nhận | NOT RUN |
| HW-05 | Sender/receiver auth và payload handoff | Sai người không mở khóa; đúng người tạo đúng boolean output | NOT RUN |
| HW-06 | Crowd sensing tại ngưỡng dưới/bằng/trên | `metrics.count` và nhánh warning đúng; timestamp/evidence hợp lệ | NOT RUN |
| HW-07 | Fall review: reject/confirm/duplicate | Reject không tạo incident; confirm tạo đúng một incident | NOT RUN |
| HW-08 | Restricted intrusion false/true | Chỉ true mới warning/alert; output đúng schema | NOT RUN |
| HW-09 | Navigation group với `PATH_BLOCKED` | Chuyển target tiếp theo; hết target đi HELP | NOT RUN |
| HW-10 | WAIT duration/event và timeout | Không hoàn tất sớm; event đúng mới success; timeout đúng transition | NOT RUN |
| HW-11 | Mất mạng/reboot khi job đang chạy | Không replay hardware tự động; job vào recovery/reconcile | NOT RUN |
| HW-12 | Chạy end-to-end cả 8 workflow | Mỗi workflow đạt expected terminal state và audit trail đầy đủ | NOT RUN |

Mỗi ca cần lưu: request/command JSON đã che secret, callback JSON, job event timeline, ảnh/video hoặc log ROS phù hợp, kết quả quan sát vật lý, người xác nhận và mã lỗi nếu FAIL. Chỉ publish seed sau khi semantic validation PASS và các ca hardware liên quan PASS; seed không dùng hardware vẫn cần contract replay bằng Pi runtime thật.
