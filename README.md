# MediAssistRover Backend

Spring Boot backend quản lý nghiệp vụ bệnh viện, workflow/job và hợp đồng giao tiếp Server–Raspberry Pi. Hiện đã hoàn thành đến hết Phase 5: identity/security, facility catalog, Robot/REST polling và Workflow definition với đúng 11 Step Type.

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

Không commit file `.env`. Flyway là nguồn duy nhất tạo/cập nhật schema; Hibernate chỉ chạy ở chế độ `validate`.
