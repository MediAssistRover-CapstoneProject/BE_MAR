# Phase 1 — Security, actor và contract dùng chung

> Trạng thái: **COMPLETE**  
> Ngày nghiệm thu: 2026-10-15  
> Baseline đầu vào: [`baselines/phase0-baseline.md`](./baselines/phase0-baseline.md)

## 1. Kết quả

Phase 1 bổ sung boundary dùng chung cho Patient qua Device JWT và Staff qua user JWT, đồng thời triển khai API hồ sơ của Staff hiện tại. Phase này không thêm bảng, không sửa Flyway migration và không tạo Patient Account/token/entity.

| Hạng mục | Trạng thái | Hiện thực |
|---|---|---|
| Auth Staff/Admin | `KEEP` | Giữ `/api/v1/auth/login`, `/refresh`, `/logout`, `/me`, `/change-password`. |
| Hồ sơ Staff hiện tại | `ADD` | `GET /api/v1/staff/me`. |
| Device ownership | `ADD` | `DeviceResourceScopePolicy` cho ServiceRequest và EmergencyIncident theo robot trong Device JWT. |
| Staff operational scope | `ADD` | `StaffOperationalScopePolicy` cho request, job, schedule, alert, incident và robot read-only. |
| Contract chung | `KEEP/HARDEN` | Dùng lại `PageResponse`, `ApiErrorResponse`, correlation ID, UTC và Bean Validation; bổ sung test giới hạn trang. |
| OpenAPI | `ADD` | Tag `Staff Profile`, user bearer security và schema `StaffMeResponse`. |

## 2. Contract `GET /api/v1/staff/me`

- Actor: Staff.
- Auth: user bearer JWT có `ROLE_STAFF`.
- Request body/query: không có.
- Response `200`: `StaffMeResponse` gồm `account`, `staffProfile`, `activeShifts`.
- `activeShifts` chỉ chứa Shift có `status=ACTIVE`, `startsAt <= now` và `endsAt > now` của chính Staff.
- Account không tồn tại, inactive, sai role, token version cũ hoặc chưa qua password-change gate bị từ chối.
- Staff chưa có StaffProfile hoặc StaffProfile inactive trả `422 BUSINESS_RULE_VIOLATION` theo `ApiErrorResponse`.
- Admin, Device và anonymous không được dùng endpoint này.

## 3. Policy scope dùng chung

### Device

`DeviceResourceScopePolicy` lấy robot từ `DevicePrincipal`, kiểm thời hạn token, robot hiện hữu, robot code, device secret và token version. Device chỉ được thao tác ServiceRequest có `requestedRobot` trùng robot trong token hoặc EmergencyIncident có `robot` trùng token.

### Staff

`StaffOperationalScopePolicy` xác nhận Account hiện tại còn `ACTIVE`, role/token version khớp và không còn `mustChangePassword`. Staff được truy cập resource khi thuộc ít nhất một nhánh:

1. là người tạo ServiceRequest hoặc Schedule;
2. là Staff được gán cho Job;
3. là AlertRecipient;
4. resource liên kết Room thuộc Shift đang `ACTIVE` và còn hiệu lực;
5. đọc trạng thái/vị trí robot khi Staff đang hoạt động.

Admin được giữ quyền superset. Policy đã sẵn sàng để các phase sở hữu endpoint tích hợp; Phase 1 không tự thay đổi hành vi của toàn bộ controller Phase 3–8.

## 4. Contract chung đã xác nhận

- Pagination dùng `PageResponse<T>`; API ca trực từ chối `size > 100` bằng `400 VALIDATION_ERROR`.
- Lỗi dùng `ApiErrorResponse`, có UTC timestamp, status, error, message, path, correlation ID và violations khi phù hợp.
- `X-Correlation-ID` đầu vào được giữ lại trên response; thiếu header thì server sinh giá trị mới.
- Staff dùng user bearer scheme; Device dùng device bearer scheme. Staff JWT không thể gọi route Patient/Device và ngược lại.
- Không có Patient registration, login, profile hoặc role mới.

## 5. File triển khai

- `controller/staff/StaffMeController.java`
- `service/StaffMeService.java`
- `dto/staff/response/StaffMeResponse.java`
- `validation/DeviceResourceScopePolicy.java`
- `validation/StaffOperationalScopePolicy.java`
- Các repository query bổ sung trong `ShiftRepository`, `JobRepository` và `AlertRecipientRepository`.
- Test: `PatientStaffPhase1IntegrationTest` và `Phase1ScopePolicyTest`.

## 6. Nghiệm thu

Chạy Maven trên bản sao source sạch để tránh IDE ghi đè bytecode trong `target`, dùng JetBrains Runtime 21.0.6 và Testcontainers MySQL 8.4:

```text
Tests run: 490, Failures: 0, Errors: 0, Skipped: 7
BUILD SUCCESS
Total time: 07:10 min
```

Các acceptance test mới bao phủ:

- Staff xem đúng profile và chỉ ca trực đang hiệu lực;
- lỗi StaffProfile dùng error/correlation contract chung;
- anonymous, Admin và Device bị chặn khỏi `/staff/me`;
- Staff bị chặn khỏi route Device;
- pagination vượt giới hạn trả validation error;
- OpenAPI có path, bearer security và schema mới;
- Device ownership và Staff scope theo creator, assignment, active-shift room; ngoài scope bị từ chối.

Không có migration mới. Fresh Flyway V1–V30 và Hibernate validate đã được xác nhận ở Phase 0; full regression Phase 1 tiếp tục chạy qua Testcontainers MySQL 8.4 thành công.
