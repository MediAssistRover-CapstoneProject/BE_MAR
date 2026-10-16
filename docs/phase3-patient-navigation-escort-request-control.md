# Phase 3 — Patient navigation, escort và request control

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## API và boundary đã triển khai

- Giữ `POST /api/v1/pi/requests/navigation` và `POST /api/v1/pi/requests/escort`, đồng thời buộc Device session còn hiệu lực bằng `DeviceResourceScopePolicy`.
- Thêm `GET /api/v1/pi/requests/{requestId}` với `DeviceRequestDetailResponse`; response chỉ có request state và Job summary, không có Staff account/assignment PII, raw request data hay idempotency key.
- Thêm `POST /api/v1/pi/requests/{requestId}/cancel`; chỉ robot sở hữu request được đọc/hủy.
- Cancellation lặp lại giữ nguyên terminal state. Nếu cùng cancellation key được dùng với reason khác, API trả conflict.
- Job đang `RUNNING` được xem là có trạng thái vật lý chưa chắc chắn: request/Job chuyển `RECOVERY_REQUIRED`, robot chuyển `ERROR` và cần reconciliation. Request chưa chạy chuyển `CANCELLED` và phát cancel command một lần.

## Validation và idempotency

- Retry create cùng actor/key/payload trả resource cũ; đổi payload với cùng key trả `409 CONFLICT`.
- Map phải `ACTIVE`; destination endpoint/group phải active, đúng map và group phải còn active member trên map.
- Room phải active và thuộc cùng floor của map; origin endpoint của escort phải active và cùng map.
- Direct accessible navigation không chấp nhận endpoint không accessible khi client khai báo accessibility need.
- Escort speed bị giới hạn server-side và Bean Validation trong khoảng `0.100–0.300 m/s`; enum chỉ hỗ trợ `GUIDED`/`ASSISTED`, không suy diễn patient-following.

## Bảng trạng thái hủy

| Trạng thái trước | Kết quả request | Kết quả Job/robot |
|---|---|---|
| `QUEUED`/`ACCEPTED` | `CANCELLED` | Ghi cancel command tối đa một lần; robot không bị giải phóng giả bởi API. |
| `IN_PROGRESS` với Job `RUNNING` | `RECOVERY_REQUIRED` | Job `RECOVERY_REQUIRED`, robot `ERROR`, chờ reconciliation. |
| `COMPLETED`/`FAILED`/`CANCELLED`/`RECOVERY_REQUIRED` | Giữ nguyên | Không tạo side effect mới. |

Robot bận hoặc đang giữ Job chưa reconciliation không làm request hợp lệ fail: `JobExecutionService` tạo Job `QUEUED` chưa gắn robot và dispatcher chỉ gắn khi robot thực sự sẵn sàng.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS** (bao gồm compile toàn bộ main và test source).
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- `RequestJobIntegrationTest` đã được mở rộng cho payload mismatch, device ownership, DTO redaction, idempotent cancel và running-navigation recovery.
- MySQL acceptance chưa chạy được vì môi trường hiện không có Docker daemon/CLI cho Testcontainers. Phase chưa được đánh dấu `COMPLETE` cho đến khi `RequestJobIntegrationTest` và full regression chạy xanh trên MySQL 8.4.
