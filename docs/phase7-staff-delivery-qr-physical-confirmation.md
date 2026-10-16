# Phase 7 — Staff: delivery, QR và physical confirmation

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## Delivery request

- `GET /api/v1/delivery-requests` hỗ trợ request status, sender, receiver, pickup/delivery room, deadline, `[from,to)` và pagination.
- Staff list/get theo scope union: creator, assigned Job hoặc ca trực hiệu lực tại pickup/delivery room; Admin giữ quyền đọc toàn cục.
- Create tiếp tục kiểm tra actor hiện tại, item contract và server-side robot selection.
- Hai legacy confirmation endpoint chỉ còn phục vụ schema 2. Delivery dùng schema 3 nhận `410 LEGACY_EXECUTION_RETIRED` thay vì thực thi song song hai mô hình xác nhận.

## Auth verification, QR và step response

- Tạo/đọc auth verification, phát hành/xác minh QR challenge và gửi Job-step response đều kiểm tra operational Job scope bằng một policy thống nhất.
- QR proof vẫn được lưu dưới dạng hash, có TTL ngắn, single-use và gắn đúng AUTH step; response không làm lộ proof/secret.
- Job-step response chỉ áp dụng cho selected, unexpired, running step. Server kiểm tra actor trước khi xử lý payload và giữ snapshot để phục vụ audit/idempotency.
- Workflow event idempotency được ràng buộc cả payload lẫn actor, kiểm tra event registry/time window và tiêu thụ đúng WAIT step theo correlation.

## Physical safety

- `PAYLOAD_CONFIRMED` đối chiếu item code/quantity với snapshot Backend.
- `DOOR_CLOSED` và `LOCK_CHECKED` là hai xác nhận Staff độc lập, không được suy diễn từ trạng thái MQTT command đã publish.
- Cancel sau pickup hoặc khi cargo/door/lock còn chưa chắc chắn chuyển sang `RECOVERY_REQUIRED`; robot chỉ được reconcile bởi actor có recovery scope.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS**, bao gồm compile main và test source.
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- `DeliveryWorkflowAcceptanceTest` có coverage cho list filter; `DeliveryOperationAuthorizationIntegrationTest` kiểm tra schema 3 nhận `LegacyExecutionRetiredException`/HTTP 410.
- Nhóm `RequestJobIntegrationTest`, `DeliveryWorkflowAcceptanceTest`, `DeliveryOperationAuthorizationIntegrationTest` và `Phase13WorkflowOrchestrationIntegrationTest` phát hiện 47 test, 0 assertion failure và 47 lỗi khởi tạo môi trường do Docker/Testcontainers không khả dụng. Cần chạy lại trên MySQL 8.4 trước khi đánh dấu `COMPLETE`.
