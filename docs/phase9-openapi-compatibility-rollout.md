# Phase 9 — OpenAPI, compatibility và rollout

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-16).

## Kết quả triển khai

- OpenAPI không còn áp một user bearer mặc định cho toàn bộ API. Mỗi operation được khai báo rõ một trong ba boundary: anonymous, `deviceBearerAuth` hoặc `bearerAuth`.
- Nhóm API Patient/Staff được chuẩn hóa về bảy tag: `Patient Public Catalog`, `Robot Screen Requests`, `Robot Emergency`, `Staff Operations`, `Staff Delivery`, `Staff Alerts` và `Staff Incidents`.
- `ApiErrorResponse` được đăng ký thành schema dùng chung. Các response component cho `400`, `401`, `403`, `404`, `409`, `410` và `422` được tái sử dụng tại operation phù hợp.
- Hai endpoint xác nhận delivery schema 2 vẫn giữ nguyên path. OpenAPI mô tả `410 LegacyExecutionRetired` khi Job dùng schema 3; tích hợp mới phải dùng auth/QR và `POST /api/v1/job-steps/{id}/responses`.
- Không thêm migration và không sửa V1–V30.

## Changelog tương thích từ Phase 0

Các thay đổi Patient/Staff là additive hoặc hardening:

- thêm Staff self-service/profile và published workflow catalog;
- thêm public detail/article/endpoint-group contract đã redaction;
- thêm Device request detail/cancel và incident status;
- thêm các filter/list contract cho request, Job, delivery, alert và incident;
- thêm QR/auth verification, physical response và operation recovery;
- giữ các path Phase 0, bao gồm alias workflow cũ và hai delivery confirmation endpoint.

Các route tương thích `/api/v1/admin/workflows/step-types` và `/api/v1/admin/workflows/published/**` tiếp tục tồn tại, được đánh dấu deprecated trong OpenAPI và phải được giữ ít nhất một release. Không có Workflow `PUBLISHED` hay migration đã chạy nào bị sửa tại chỗ.

## Kiểm thử tự động

- `OpenApiConfigTest` kiểm tra độc lập ba security boundary, tag Phase 9, success response và reusable error responses.
- `Phase9OpenApiIntegrationTest` gọi `/v3/api-docs`, kiểm tra security/tag/error contract thực tế và so từng `path + HTTP method` với `docs/baselines/openapi-phase0.json`. Bất kỳ operation Phase 0 nào biến mất đều làm test fail.
- `mvn -q -DskipTests package` trên Java 21: **PASS**.
- `mvn -q -Dtest=OpenApiConfigTest test` trên Java 21: **2/2 PASS**.
- Nhóm unit regression không cần database: **194 test, 0 failure, 0 error, 0 skipped**.

Docker Desktop không khả dụng trong môi trường kiểm tra ngày 2026-10-16, nên `Phase9OpenApiIntegrationTest`, fresh Flyway/Hibernate validate trên MySQL 8.4 và full Maven regression chưa được chạy lại. Phase chỉ chuyển sang `COMPLETE` sau khi các gate dưới đây đều xanh.

## Rollout gate

1. Chạy fresh MySQL 8.4: Flyway V1–V30 và Hibernate `validate` phải pass.
2. Chạy `mvn test`; không chấp nhận failure/error mới và phải chạy xanh `Phase9OpenApiIntegrationTest`.
3. Xuất `/v3/api-docs` và lưu artifact của release; test baseline phải xác nhận không mất path/method Phase 0.
4. Chạy security matrix anonymous/device/Staff đúng scope/Staff ngoài scope/Admin cho bảy nhóm tag.
5. Chạy acceptance walkthrough cho information, navigation/escort, SOS, Staff operation, delivery và emergency/recovery.
6. Chỉ triển khai production sau khi client xác nhận không còn gọi delivery confirmation schema 2 cho Job schema 3; vẫn giữ route cũ trong release này.

## Giới hạn nghiệm thu

Phase 9 xác nhận contract Backend. Pi/ROS2, MQTT broker production, phần cứng, STT/TTS/AI và UI thật vẫn cần báo cáo tích hợp riêng; test Backend không được dùng làm bằng chứng rằng các thành phần đó đã nghiệm thu.
