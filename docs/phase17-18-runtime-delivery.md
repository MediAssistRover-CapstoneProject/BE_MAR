# Phase 17–18 — Runtime event, ACK và giao nhận an toàn

Cập nhật: 2026-10-08. Trạng thái: **DONE** trong phạm vi Backend; không bao gồm scanner/camera, driver servo, Pi/ROS2 hoặc nghiệm thu phần cứng.

## Phase 17

- `SET_CONTEXT` chỉ ghi dưới `/vars`, tất cả phép gán đọc cùng snapshot trước cập nhật, cấm gán trùng path; context, result và event nằm trong cùng transaction. Retry cùng message không tăng biến lần hai.
- `WAIT DURATION` chỉ hoàn tất khi đủ thời lượng. `WAIT EVENT` nhận event Staff qua `POST /api/v1/jobs/{jobId}/events`, kiểm tra Job/scope/type/correlation/time; event đến sớm được lưu bền và chỉ một lượt WAIT được consume.
- `NOTIFY` dùng `severity`, `recipients`, `require_ack`, `ack_policy` từ contract. Bản ghi recipient bắt đầu ở `PENDING`; chỉ chuyển `DELIVERED` khi recipient nhận qua API. ACK trễ bị từ chối, ANY/ALL được tính dưới row lock và chỉ khi đủ policy mới hoàn tất Step.
- Scheduler và callback cùng khóa dòng `JobStep`; kết quả thắng race đầu tiên, bên đến sau không tạo tác dụng nghiệp vụ lần hai.

## Phase 18

- Xác nhận pickup/handoff API yêu cầu `jobStepId`, danh sách `itemCode/quantity`, xác nhận đóng và khóa khoang. Server đối chiếu tập hàng chính xác, đúng Delivery/Job/stage/actor và hoàn tất đúng `HANDLE_PAYLOAD` Step; verification chỉ được consume bởi một Step.
- QR proof có challenge 256-bit, TTL 30–300 giây, scope Job/AUTHENTICATE/purpose; database chỉ lưu SHA-256. Proof hết hạn, sai scope hoặc replay bị chặn. API: `POST /api/v1/jobs/{jobId}/qr-challenges` và `/qr-verifications`.
- Cancel/error sau một Step UNLOCK hoặc HANDLE_PAYLOAD thành công chuyển Job/Request/Robot sang `RECOVERY_REQUIRED`/`ERROR`; robot không được dispatch lại trước reconcile. Reconcile từ ERROR đưa robot về STARTING.
- SOS tạo Incident/Alert trước và không phụ thuộc robot rảnh. Khi chưa có Workflow EMERGENCY published, request ở SUBMITTED với dispatch pending thay vì làm mất cảnh báo. Bổ sung hành vi `FALSE_ALARM` riêng qua `POST /api/v1/emergency-incidents/{id}/false-alarm`; Job thành công không tự đóng Incident.

## Schema và tương thích

- Migration tiến tới `V16__phase17_18_runtime_and_delivery_proof.sql`: thêm dấu consume event, bảng `QrChallenge`, timestamp xác nhận khoang và trạng thái Incident `FALSE_ALARM`.
- Không sửa V1–V15, không đụng database demo. Transport protocol 1.0, Step schema 2 và graph 3 giữ nguyên.
- Confirm DTO hai tham số cũ chỉ được giữ cho lời gọi Java nội bộ/baseline; HTTP validation mới bắt buộc Step, items và xác nhận vật lý.

## Bằng chứng

- `RuntimeContextEventIntegrationTest`: snapshot context; event sớm/consume/idempotency; delivery/ACK của NOTIFY.
- `DeliveryWorkflowAcceptanceTest`: sai số lượng rollback; đúng hàng + đóng/khóa hoàn tất HANDLE_PAYLOAD; QR proof scope/single-use.
- `target/phase17-18-final-regression.log`: MySQL 8.0 local isolated database, fresh Flyway V1–V16, Hibernate validate, **106 test / 24 suite, 0 failure, 0 error, 0 skipped**.
- Database kiểm thử `backend_robot_phase1718_test` đã được xóa sau khi chạy; database demo `backend_robot_v1` không bị thay đổi.
