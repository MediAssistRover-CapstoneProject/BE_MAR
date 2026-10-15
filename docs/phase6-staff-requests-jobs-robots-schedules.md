# Phase 6 — Staff: request, Job, robot và schedule

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## ServiceRequest và operational scope

- Các API tạo information, navigation, escort và operational request tiếp tục dùng Staff/Admin JWT; navigation/escort giữ validation target và cơ chế server chọn robot.
- `GET /api/v1/service-requests` hỗ trợ status, request type, robot, khoảng thời gian nửa mở `[from,to)` và pagination.
- Staff được xem request theo hợp của ba điều kiện: là creator, được gán vào Job của request, hoặc có ca trực hiệu lực tại room liên quan. Admin giữ quyền đọc toàn cục.
- Get, submit và cancel đều áp dụng cùng operational scope. Submit chỉ hợp lệ từ `DRAFT` và không tạo lặp Job.
- Cancel là idempotent. Nếu delivery đã pickup hoặc còn cargo/door/lock chưa đối soát, request và Job được giữ trong luồng recovery thay vì giải phóng robot giả định.

## Job và realtime progress

- `GET /api/v1/jobs` hỗ trợ status, workflow, request, robot, `[from,to)` và pagination; danh sách Staff dùng cùng scope union với request, assignment và schedule ownership.
- Job detail trả selected/running step cần cho UI nhưng redacts secret, password, token, proof, operation key và producer session. Event actor của Staff cũng được giới hạn.
- Event history giữ thứ tự thời gian và giới hạn page size tối đa 200; SSE xác thực scope trước khi subscribe và cleanup emitter khi complete, timeout hoặc error.
- Cancel kiểm tra scope và giữ safe-cancellation semantics. Reconcile chỉ nhận Job `RECOVERY_REQUIRED` và bị từ chối nếu vẫn có MQTT operation chưa được đối soát.
- Workflow event kiểm tra event registry, cửa sổ thời gian, actor-bound idempotency và correlation với WAIT step. Mapping session là read-only đối với Staff trong scope; quyền tạo Mapping Job vẫn Admin-only.

## Robot và Schedule

- Staff/Admin có thể đọc robot, status history và location history mà không nhận device secret/hash; location history hỗ trợ `[from,to)` và giới hạn pagination.
- Robot reconcile yêu cầu Staff có ít nhất một Job `RECOVERY_REQUIRED` thuộc operational scope trên robot đó; Admin giữ quyền recovery toàn cục.
- Staff chỉ tạo schedule cho service type được phép và không được ghim robot; robot được chọn phía server khi chạy.
- List/get schedule giới hạn theo creator đối với Staff. Activate kiểm tra workflow readiness, time window và collision; pause idempotent; retire giữ lịch sử, không hard delete.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS**, bao gồm compile main và test source.
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- `RequestJobIntegrationTest` đã được mở rộng cho filters, scope union, server-selected schedule và pause idempotency.
- Nhóm integration liên quan Phase 6–7 phát hiện 47 test, 0 assertion failure và 47 lỗi khởi tạo môi trường do Testcontainers không tìm thấy Docker. Chỉ chuyển phase sang `COMPLETE` sau khi MySQL integration và full regression chạy xanh trên môi trường có Docker.
