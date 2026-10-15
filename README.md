<<<<<<< Updated upstream
# BE_MAR
=======
# MediAssistRover Backend

Spring Boot backend quản lý nghiệp vụ bệnh viện, workflow/job và hợp đồng giao tiếp Server–Raspberry Pi. Source hiện đã hoàn tất Phase 1–21 trong phạm vi Backend. Trạng thái kiểm chứng mới nhất được ghi tại `docs/implementation-status.md`; danh mục Workflow canonical tại `docs/workflow-source-matrix.md`; kết quả nghiệm thu tại `docs/backend-acceptance-final.md`; contract/hardware riêng tại `docs/pi-contract-and-hardware-acceptance.md`.

## Yêu cầu

- Java 21 LTS
- Maven 3.9+
- MySQL 8.4 LTS, hoặc Docker + Docker Compose

## Chạy bằng Docker Compose

1. Sao chép `.env.example` thành `.env` và thay toàn bộ secret/mật khẩu mẫu.
2. Chạy `docker compose up --build`.
3. Swagger UI: `http://localhost:8080/swagger-ui.html`.
4. OpenAPI JSON: `http://localhost:8080/v3/api-docs`.
5. Health: `http://localhost:8080/actuator/health`.

## Production HTTPS

Backend chỉ bind vào `127.0.0.1` theo mặc định. Để chạy kết hợp reverse proxy HTTPS:

1. Đặt certificate chain tại `${TLS_CERT_DIR}/fullchain.pem` và private key tại `${TLS_CERT_DIR}/privkey.pem`.
2. Đặt `PUBLIC_BASE_URL=https://<domain>` và `TLS_CERT_DIR` trong `.env`.
3. Chạy `docker compose --profile production up --build -d`.

Nginx chỉ mở cổng HTTPS `${HTTPS_PORT:-443}`; backend vẫn là upstream nội bộ và xử lý forwarded headers bằng Spring `framework` strategy.

Không commit file `.env`. Flyway là nguồn duy nhất tạo/cập nhật schema; Hibernate chỉ chạy ở chế độ `validate`.

## MQTT với Pi — bản sửa 13/10/2026

MQTT 5/TLS, opt-in qua MQTT_ENABLED và MQTT_DISPATCH_ENABLED; Compose chuyển các biến trong .env.example vào backend. API workflow quản trị: /api/v1/admin/workflows. Robot thực thi MOVE, CONTROL_HARDWARE, EXECUTE_SENSING, WAIT/DURATION; BE giữ điều phối và các bước nghiệp vụ còn lại.

NAV_PROFILE / NAV_PROFILE_ACK đồng bộ endpoint qua MQTT khi Pi rảnh, cùng map UUID/hash/frame; MOVE vẫn yêu cầu xác nhận phiên hiện tại. Topic preview chuẩn là mapping/preview. Tốc độ publish mặc định 8 bản tin/giây; queue hữu hạn, ưu tiên heartbeat/cancel.

MQTT_ALLOW_COMMISSIONING_UNLOCK mặc định false. Chỉ bật để thử cơ cấu khi Pi cũng bật COMMISSIONING_ONLY. Không tạo xác thực người gửi/nhận giả. Đợt sửa này không bổ sung luồng device authentication; API ADMIN hiện có vẫn hoạt động như trước.

Kiểm tra cục bộ: `mvn -Dtest=MqttBridgeTest,MqttWireContractTest,StepInputContractValidatorTest,JobStepInputResolverTest test`. Bộ hướng dẫn triển khai và script thử nằm trong gói bàn giao MediAssistRover_MQTT_Final_2026_10_13. Chạy trên robot thật vẫn cần commissioning map/Nav2/phần cứng và broker đã cấu hình.
>>>>>>> Stashed changes
