# Backend/API Demo Acceptance

Ngày thực hiện: 2026-10-07

## Môi trường nghiệm thu

- Docker Compose project: `mediassist-demo`
- Backend: `http://localhost:18080`
- Swagger UI: `http://localhost:18080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:18080/v3/api-docs`
- MySQL 8.4: host port `13306`
- Scheduler được tắt trong môi trường demo để tránh phát sinh background mutation.
- Credential và secret được truyền bằng biến môi trường, không ghi vào source code hoặc file Compose.

Hai container `mediassist-demo-backend-1` và `mediassist-demo-mysql-1` đang chạy. Volume `mediassist-demo_mysql-data` giữ trạng thái publish khi container được restart trong cùng Compose project.

## Workflow acceptance

Admin đã gọi đúng state machine `DRAFT → VALIDATED → PUBLISHED`, không cập nhật trực tiếp status. Kết quả:

| Workflow | Step | Transition | Trạng thái |
|---|---:|---:|---|
| `WF_CROWD_MONITORING` | 8 | 26 | PUBLISHED |
| `WF_EMERGENCY_SOS_RESPONSE` | 7 | 22 | PUBLISHED |
| `WF_FALL_RESPONSE` | 9 | 29 | PUBLISHED |
| `WF_INFORMATION_ASSISTANCE` | 3 | 9 | PUBLISHED |
| `WF_NAVIGATION` | 13 | 43 | PUBLISHED |
| `WF_PATIENT_ESCORT` | 5 | 16 | PUBLISHED |
| `WF_RESTRICTED_MONITORING` | 5 | 16 | PUBLISHED |
| `WF_SECURE_MEDICAL_DELIVERY` | 25 | 83 | PUBLISHED |

Kết quả cuối:

- DRAFT còn lại: `0`.
- PUBLISHED và `acceptsNewJobs=true`: `8`.
- `WORKFLOW_VALIDATED` AuditLog: `8`.
- `WORKFLOW_PUBLISHED` AuditLog: `8`.
- Definition hash được tạo cho cả tám Workflow.

Migration vẫn seed Workflow ở `DRAFT` có chủ đích. Việc publish là quyết định của Admin tại từng môi trường, không được hardcode vào Flyway.

## Swagger/OpenAPI acceptance

Đã dùng Chromium thật qua Playwright để kiểm tra:

1. `/swagger-ui.html` redirect và render thành công tại `/swagger-ui/index.html`.
2. Tiêu đề hiển thị `MediAssistRover Backend API v1`, OpenAPI `3.1.0`.
3. OpenAPI có `165` paths, `182` schemas và hai security scheme:
   - `bearerAuth` cho ADMIN/STAFF.
   - `deviceBearerAuth` cho Raspberry Pi.
4. Nút Authorize nhận access token; các endpoint ADMIN chuyển sang trạng thái authorized.
5. Gọi trực tiếp từ Swagger UI `GET /api/v1/admin/reports/overview` trả HTTP `200` và JSON đúng contract.
6. Cùng endpoint không có JWT trả HTTP `401`.
7. Browser console: `0` error, `0` warning.

Ảnh nghiệm thu: `output/playwright/swagger-overview.png`.

## Khởi động lại môi trường demo

Không lưu secret vào repository. Trước khi gọi Compose, đặt lại đầy đủ biến môi trường theo `.env.example`, đặc biệt:

- `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`.
- `JWT_SECRET`, `DEVICE_JWT_SECRET`.
- `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL`.
- `ADMIN_ACCOUNT_CODE`, `ADMIN_FULL_NAME`.
- `MYSQL_PORT=13306`, `BACKEND_PORT=18080` nếu muốn giữ đúng URL demo này.
- `OPERATIONS_SCHEDULER_ENABLED=false` cho demo API tĩnh.

Sau đó chạy:

```powershell
docker compose -p mediassist-demo up -d --build
```

Không dùng credential demo cho môi trường triển khai thật. Production phải cấp secret mới và HTTPS qua reverse proxy.
