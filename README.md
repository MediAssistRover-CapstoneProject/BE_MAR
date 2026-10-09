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
