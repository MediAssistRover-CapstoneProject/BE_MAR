# Phase 8 — Staff: alert, emergency và recovery

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## Alert

- `GET /api/v1/alerts` hỗ trợ status, severity, notification type, khoảng thời gian nửa mở `[from,to)` và pagination.
- Staff chỉ thấy alert khi là recipient hoặc có operational scope qua request, Job hay incident/ca trực hiệu lực. Admin giữ quyền đọc toàn cục.
- Alert detail của Staff chỉ trả recipient record của chính actor; danh sách recipient khác được redaction.
- Acknowledge yêu cầu recipient hoặc Staff delegate có operational scope. Retry của cùng actor là idempotent; `ANY` chuyển trạng thái sau acknowledgement đầu tiên, còn `ALL` chờ đủ recipient snapshot.
- Resolve chỉ nhận alert `ACKNOWLEDGED` và không được dùng để thay thế việc resolve incident liên kết. Close chỉ nhận alert `RESOLVED`; incident liên kết phải ở trạng thái đóng phù hợp. Mọi mutation đều được audit.

## Emergency incident

- Controller truyền `AccountPrincipal` vào cả list và detail. List hỗ trợ status, severity, type, map, room, `[from,to)` và pagination.
- Staff list/get theo recipient hoặc ca trực hiệu lực tại room/resource liên quan; ngoài scope trả `403`. Admin giữ quyền đọc toàn cục.
- Mutation dùng pessimistic lock và state guard: `OPEN → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → CLOSED`.
- Acknowledge lưu actor/time nhất quán; start/resolve yêu cầu responder đúng scope và không cho Staff khác chiếm incident đã được nhận.
- Resolve lưu actor, resolution note và evidence tùy chọn vào audit. False alarm chỉ áp dụng từ `OPEN`/`ACKNOWLEDGED`, lưu reason/evidence và không xóa detector/event nguồn.

## Operation recovery

- `GET /api/v1/jobs/{jobId}/operations/recovery` dùng cùng Job operational scope với Phase 6.
- Recovery evidence trả command state, event timeline và cờ physical uncertainty; raw MQTT receipt, HELLO journal và heartbeat payload không còn được trả cho Staff UI.
- `POST /api/v1/jobs/{jobId}/operations/{jobStepId}/reconcile` tiếp tục kiểm tra đúng JobStep/operation/session, terminal Pi journal và stop evidence.
- Reconciliation giữ actor-bound idempotency, không replay hardware command, không suy diễn Pi stop từ khai báo của Staff và buộc robot thực hiện HELLO/heartbeat mới trước lần dispatch tiếp theo.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS**, bao gồm compile main và test source.
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- `Phase1ScopePolicyTest` có coverage cho recipient, active-shift incident/alert và foreign resource denial.
- `Phase7IntegrationTest` được mở rộng cho alert/incident filters, recipient redaction, foreign Staff denial và linked-incident resolve guard.
- `NotificationAckWorkflowIntegrationTest` bao phủ ACK `ANY`/`ALL`, retry, rollback và concurrency; `MqttCancellationRecoveryIntegrationTest` bao phủ evidence, idempotency, session/stop validation và robot hold/release.
- Ba integration suite trên phát hiện 35 test, 0 assertion failure và 35 lỗi khởi tạo môi trường vì Testcontainers không tìm thấy Docker. Chỉ chuyển phase sang `COMPLETE` sau khi chạy xanh trên MySQL 8.4 và full regression.
