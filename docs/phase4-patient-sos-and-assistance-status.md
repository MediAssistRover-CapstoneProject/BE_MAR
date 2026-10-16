# Phase 4 — Patient SOS và theo dõi hỗ trợ

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## API và security boundary

- Giữ và harden `POST /api/v1/pi/incidents/sos`.
- Thêm `GET /api/v1/pi/incidents/{incidentId}` trả `DeviceIncidentStatusResponse`.
- Cả create và read đều yêu cầu Device JWT hiện hành: robot code, token version, expiry và device enrollment phải còn hợp lệ.
- Incident tồn tại nhưng thuộc robot khác trả `403`; ID không tồn tại trả `404`.
- Device response chỉ chứa incident/request/alert/Job status rút gọn. Không trả recipient, contact snapshot, Staff account ID, resolution note, alert data nội bộ hay row version.

## Atomicity và idempotency

- SOS chạy trong một transaction tạo `EmergencyIncident`, `ServiceRequest`, `Alert`, recipient và Job khi có thể dispatch.
- Robot row được khóa trước khi kiểm operation key. Hai request đồng thời của cùng robot không thể cùng vượt qua bước duplicate check.
- Operation key được scope theo robot; retry cùng canonical payload trả lại cùng incident/request/alert/job IDs.
- Cùng operation key nhưng payload khác trả `409 CONFLICT`.
- Nếu transaction không tạo được Alert/recipient thì incident và request cũng rollback.

## Busy robot và lifecycle

- Robot `BUSY` hoặc giữ Job active/recovery vẫn tạo incident, request và Alert ngay lập tức; request được đánh dấu `dispatch_pending` và không chiếm/cancel Job hiện hữu.
- Backend không tự resume Job cũ sau SOS.

| Trạng thái incident | Ý nghĩa |
|---|---|
| `OPEN` | SOS đã được lưu và Alert đã phát. |
| `ACKNOWLEDGED` | Staff đã tiếp nhận Alert/incident, chưa đồng nghĩa đã tới hiện trường. |
| `IN_PROGRESS` | Staff đã bắt đầu xử lý. |
| `RESOLVED` | Xử lý đã hoàn tất. |
| `CLOSED` | Hồ sơ đã được đóng. |

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS**, bao gồm compile main và toàn bộ test source.
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- `Phase7IntegrationTest` đã mở rộng để kiểm device status DTO, redaction, cross-robot denial và lifecycle status.
- Lần chạy `Phase7IntegrationTest` hiện tại dừng khi Testcontainers khởi tạo vì không có Docker environment: 5 test được phát hiện, 0 assertion failure, 5 environment initialization errors. Phase chưa được đánh dấu `COMPLETE` cho đến khi MySQL 8.4 integration và full regression chạy xanh.
