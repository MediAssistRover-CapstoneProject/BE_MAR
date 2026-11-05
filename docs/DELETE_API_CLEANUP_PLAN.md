# Kế hoạch chuẩn hóa API soft-delete và hard-delete

## 0. Trạng thái triển khai

**Cập nhật gần nhất:** 06/11/2026

**Nhánh:** `main`
**Trạng thái:** Giai đoạn Facility đã hoàn thiện trong working tree, chờ commit local; các commit trước vẫn chưa push theo yêu cầu chỉ push khi hoàn thành toàn bộ.

### Đã hoàn thành trong lượt hiện tại

- [x] Tạo contract dùng chung `LifecycleActionRequest` và `DeletionImpactResponse`.
- [x] Tạo `DeletionPolicy` để bắt buộc `X-Confirm-Hard-Delete: true`.
- [x] Chuẩn hóa workflow:
  - `DELETE /api/v1/admin/workflows/{id}` chuyển thành soft-delete về `ARCHIVED` và bắt buộc `reason`.
  - `DELETE /api/v1/admin/workflows/{id}/force` bắt buộc confirmation header và `reason`.
  - Thêm `POST /api/v1/admin/workflows/{id}/restore`; restore về `DRAFT`, không tự publish.
  - Thêm `GET /api/v1/admin/workflows/{id}/deletion-impact`.
  - List/Get ẩn `ARCHIVED` mặc định; ADMIN dùng `includeDeleted=true` để xem.
  - Endpoint `/archive` cũ được giữ làm alias deprecated và bắt buộc `reason`.
  - Audit workflow archive/restore/force-delete đã ghi `reason`.
- [x] Chuẩn hóa một phần Staff:
  - `DELETE /api/v1/admin/staff/{id}` trả `StaffResponse`, bắt buộc `reason` và soft-delete Account/Profile.
  - Thêm `POST /api/v1/admin/staff/{id}/restore`.
  - List/Get ẩn Staff inactive mặc định; ADMIN dùng `includeDeleted=true`.
  - Audit deactivate/restore đã ghi `reason`.
- [x] Hoàn thiện Staff/Account hard-delete:
  - Thêm `GET /api/v1/admin/staff/{id}/deletion-impact`.
  - Thêm `DELETE /api/v1/admin/staff/{id}/force` với confirmation header và reason.
  - Tự kiểm kê tất cả foreign key thực tế đang tham chiếu `Account` trong schema.
  - Chỉ xóa dữ liệu sở hữu gồm StaffProfile, RefreshToken và RevokedToken.
  - Trả `409` nếu Account còn bất kỳ tham chiếu nghiệp vụ nào khác.
  - Chặn account tự hard-delete chính mình.
- [x] Hoàn thiện Hospital/Article:
  - Soft-delete bằng `RETIRED`, restore về `DRAFT`, ẩn mặc định và hỗ trợ `includeDeleted=true`.
  - Có `deletion-impact` và `/force`, bắt buộc reason/confirmation theo chuẩn chung.
  - Hospital hard-delete xóa Article sở hữu nhưng chặn nếu còn Floor.
  - Article không có dependent table nên được hard-delete độc lập.
- [x] Hoàn thiện Facility (Hospital, Floor, Map, Zone, Room, Endpoint, EndpointGroup):
  - Soft-delete/restore và `reason` cho toàn bộ resource cấu hình; Map dùng `ARCHIVED` và restore về `INACTIVE`, các resource còn lại dùng `active=false/true`.
  - List/Get ẩn resource soft-deleted mặc định và có `includeDeleted=true` cho ADMIN.
  - Thêm `GET /{resource}/{id}/deletion-impact` và `DELETE /{resource}/{id}/force` cho Floor, Map, Zone, Room, Endpoint, EndpointGroup.
  - Force-delete luôn yêu cầu `X-Confirm-Hard-Delete: true`, khóa record và chặn khi còn FK nghiệp vụ; chỉ tự xóa MapBinaryAsset/MapArtifact hoặc EndpointGroupMember thuộc aggregate.
  - Audit ghi reason, trạng thái trước/sau và thống kê record xóa cứng.
- [x] Hoàn thiện Robot:
  - Thêm trạng thái soft-delete `RETIRED` bằng migration `V44`; restore luôn về `OFFLINE`.
  - Thêm `DELETE /api/v1/robots/{id}`, `POST /{id}/restore`, `GET /{id}/deletion-impact`, `DELETE /{id}/force`.
  - Robot `BUSY` hoặc `STARTING` không thể retire; hard-delete bị chặn nếu còn bất kỳ FK nào, gồm history/runtime/operational records.
  - List/Get ẩn `RETIRED` mặc định và hỗ trợ `includeDeleted=true`.
- [x] Hoàn thiện Schedule, Shift và Slot:
  - Schedule `RETIRED` có DELETE/restore về `PAUSED`, reason, includeDeleted, impact và force-delete.
  - DELETE Shift hiện chuyển `PLANNED` thành `CANCELLED`; restore chỉ khi Shift chưa bắt đầu.
  - DELETE Slot hiện chuyển thành `CLOSED`; restore chỉ khi Slot/Shift chưa bắt đầu.
  - Thêm impact/force-delete cho Shift và Slot. Force-delete Shift chỉ xóa Slot con; mọi FK vận hành khác là blocker.
- [x] Hoàn thiện AI Sensing Profile:
  - `DELETE /api/v1/admin/monitoring/robots/ai-profiles/{profileId}` đặt `enabled=false`; restore phải qua kiểm tra catalog đang hoạt động.
  - Có deletion-impact và force-delete, xác thực ADMIN session và confirmation header.
- [x] Hoàn thiện SystemSetting:
  - Migration `V45` bổ sung `is_active`; runtime chỉ đọc override active, nên soft-delete quay về property fallback.
  - Có DELETE/restore/deletion-impact/force-delete, reason và confirmation header; list/get ẩn setting inactive mặc định.
- [x] Hoàn thiện MediaAsset:
  - Migration `V46` bổ sung `is_active`; DELETE chỉ retire catalog, restore không tạo S3 object mới.
  - `/force` kiểm tra toàn bộ reference hiện có trước khi xóa catalog và S3 object; có deletion-impact và confirmation header.
- [~] Đang triển khai ServiceRequest:
  - Migration `V47` thêm `archived_at`; DELETE chỉ archive request terminal (`COMPLETED`, `FAILED`, `CANCELLED`), không hỗ trợ restore.
  - List/Get ẩn request archived mặc định; ADMIN dùng `includeDeleted=true`.
  - Đã thêm deletion-impact chặn khi còn Job chưa terminal.
  - Chưa có `/force`: cần hoàn thiện thứ tự xóa subtype, Job terminal và runtime FK trước khi mở endpoint.
- [~] Đang triển khai Job:
  - Migration `V48` thêm `archived_at`; DELETE archive chỉ áp dụng `SUCCEEDED`, `FAILED`, `CANCELLED` và không hỗ trợ restore.
  - List/Get ẩn Job archived mặc định; có `deletion-impact` chặn mọi Job chưa terminal.
  - Chưa có `/force`: cần dọn JobTask/JobStep/JobEvent/MQTT/mapping/AI runtime theo thứ tự FK trước khi mở endpoint.
- [x] `mvn -DskipTests compile` thành công sau thay đổi Facility.
- [x] `git diff --check` thành công sau thay đổi Facility.

### Đang làm dở

- [ ] Chưa kiểm tra Swagger thủ công trên database phát triển.
- [ ] Admin Account controller hiện mới có lock/unlock/reset-password; chưa có bộ lifecycle endpoint riêng cho account không phải Staff.
- [ ] Chưa triển khai lifecycle riêng cho endpoint-group membership (hiện đã có deactivate bằng `DELETE`, chưa có restore/force/impact độc lập do đây là association).
- [ ] Chưa hoàn thiện hard-delete ServiceRequest, Job, Alert/Incident, VoiceInteraction và retention.

### Bước tiếp theo cho lần làm sau

1. Rà diff Facility, commit local; tuyệt đối chưa push.
2. Bổ sung lifecycle API riêng cho account không phải Staff nếu sản phẩm cho phép quản lý các role đó.
3. Chuyển sang Robot, Schedule/Shift/Slot, AI profile/Media/SystemSetting.
4. Sau khi hoàn tất mọi nhóm, kiểm tra Swagger thủ công trên database phát triển và push một lần theo yêu cầu.

## 1. Mục tiêu và phạm vi

Chuẩn hóa API xóa cho các aggregate nghiệp vụ chính của Backend MediAssistRover.

- Có soft-delete, hard-delete và restore phù hợp với từng loại dữ liệu.
- Không tạo API xóa riêng cho các bảng kỹ thuật như `JobStep`, `JobEvent`, MQTT outbox, telemetry hoặc audit log.
- Bảng runtime/log con chỉ bị xóa cùng aggregate cha hoặc thông qua retention.
- Tận dụng lifecycle hiện có như `ARCHIVED`, `RETIRED`, `INACTIVE`, `CANCELLED` và `CLOSED`.
- Triển khai theo từng giai đoạn, mỗi giai đoạn là một commit độc lập.
- Không bổ sung automated test theo yêu cầu hiện tại.

## 2. Hiện trạng API xóa

Backend hiện có các endpoint `DELETE` sau:

| Endpoint | Hành vi hiện tại |
|---|---|
| `DELETE /api/v1/admin/workflows/{id}` | Đã đổi thành soft-delete workflow về `ARCHIVED` trong working tree |
| `DELETE /api/v1/admin/workflows/{id}/force` | Xóa cứng workflow mọi trạng thái cùng runtime không hoạt động |
| `DELETE /api/v1/admin/workflows/{id}/tasks/{taskId}` | Xóa cứng Task |
| `DELETE /api/v1/admin/workflows/{id}/steps/{stepId}` | Xóa cứng Step |
| `DELETE /api/v1/admin/workflows/{id}/transitions/{transitionId}` | Xóa cứng Transition |
| `DELETE /api/v1/admin/staff/{id}` | Soft-delete Account/Profile, bắt buộc reason trong working tree |
| `DELETE /api/v1/admin/shifts/{shiftId}` | Xóa cứng Shift `PLANNED` chưa chạy |
| `DELETE /api/v1/admin/shifts/{shiftId}/slots/{slotId}` | Xóa cứng Slot chưa chạy |
| `DELETE /api/v1/media?objectKey=...` | Xóa cứng media không còn tham chiếu |
| `DELETE /api/v1/admin/system-settings/{id}` | Xóa cứng override |
| `DELETE /api/v1/endpoint-group-members/{groupId}/{endpointId}` | Soft-delete bằng `active=false` |

Các lifecycle tương đương soft-delete đã có qua endpoint action:

- Workflow và Map: `archive`.
- Hospital, Article và Schedule: `retire`.
- Floor, Zone, Room, Endpoint và EndpointGroup: `deactivate`.
- ServiceRequest và Job: `cancel`.
- Alert và EmergencyIncident: `close`.

## 3. Chuẩn API chung

Các aggregate mới áp dụng chung quy ước:

```http
DELETE /{resource}/{id}
POST   /{resource}/{id}/restore
GET    /{resource}/{id}/deletion-impact
DELETE /{resource}/{id}/force
```

### 3.1 Soft-delete

```http
DELETE /{resource}/{id}
Content-Type: application/json

{
  "reason": "Lý do xóa mềm"
}
```

- Chuyển tài nguyên sang lifecycle không hoạt động phù hợp.
- Không xóa vật lý record hoặc lịch sử.
- Trả `200 OK` với trạng thái tài nguyên sau thay đổi.

### 3.2 Restore

```http
POST /{resource}/{id}/restore
Content-Type: application/json

{
  "reason": "Lý do khôi phục"
}
```

- Chỉ áp dụng cho master/configuration data.
- Không restore dữ liệu vận hành như Job, ServiceRequest, Alert, Incident hoặc VoiceInteraction.
- Restore không tự publish, activate schedule hoặc khởi chạy lại tác vụ.

### 3.3 Deletion impact

```http
GET /{resource}/{id}/deletion-impact
```

Response cần cung cấp:

- Tài nguyên có thể hard-delete hay không.
- Các điều kiện đang chặn.
- Số lượng record phụ thuộc sẽ bị xóa hoặc gỡ liên kết.
- Trạng thái hoạt động cần kết thúc trước khi xóa.

### 3.4 Hard-delete

```http
DELETE /{resource}/{id}/force
X-Confirm-Hard-Delete: true
Content-Type: application/json

{
  "reason": "Lý do xóa vĩnh viễn"
}
```

- Chỉ role `ADMIN` được thực hiện.
- `reason` bắt buộc, độ dài từ 5 đến 500 ký tự.
- Chạy trong một transaction.
- Sử dụng pessimistic lock và kiểm tra lại blockers sau khi khóa.
- Trả `200 OK` với ID, trạng thái cũ và thống kê dữ liệu đã xóa.

### 3.5 Hiển thị dữ liệu soft-delete

- List và Get mặc định không trả bản ghi đã soft-delete.
- Chỉ ADMIN được dùng `includeDeleted=true`.
- Reference validation không cho tạo dữ liệu mới dựa trên tài nguyên đã soft-delete.

### 3.6 Audit và lỗi

Mọi thao tác soft-delete, restore và hard-delete phải ghi `AuditLog` gồm:

- Actor.
- Reason.
- Trạng thái trước và sau.
- Dependency counts.
- Correlation ID.

Chuẩn mã lỗi:

- `404 Not Found`: tài nguyên không tồn tại hoặc bị ẩn với người gọi hiện tại.
- `409 Conflict`: còn tham chiếu hoặc tác vụ đang hoạt động.
- `422 Unprocessable Entity`: lifecycle không cho phép thao tác.

## 4. Workflow đã có

Giữ các API hiện tại:

```http
DELETE /api/v1/admin/workflows/{id}
DELETE /api/v1/admin/workflows/{id}/force
POST   /api/v1/admin/workflows/{id}/archive
```

Không viết lại luồng hard-delete workflow. Chỉ clean và chuẩn hóa:

- Bổ sung `reason` cho delete, archive và force-delete.
- Force-delete yêu cầu `X-Confirm-Hard-Delete: true`.
- Thêm `POST /api/v1/admin/workflows/{id}/restore`.
- Thêm `GET /api/v1/admin/workflows/{id}/deletion-impact`.
- Restore workflow về `DRAFT`, không tự publish lại.
- Tiếp tục chặn force-delete khi còn Job `QUEUED`, `ACCEPTED` hoặc `RUNNING`.
- Giữ toàn bộ hard-delete trong một transaction.
- Giữ `ServiceRequest` và `NavigationRequest` gốc, chỉ gỡ liên kết workflow khi cần.

## 5. Giai đoạn 1 - Nền tảng và clean code

Tạo contract dùng chung:

```java
LifecycleActionRequest(reason)
DeletionImpactResponse(...)
HardDeleteResponse(...)
```

Tạo component dùng chung cho:

- Validate reason.
- Validate confirmation header.
- Kiểm tra quyền ADMIN.
- Ghi audit before/after.
- Chuẩn hóa lỗi.
- Chuẩn hóa `includeDeleted`.

Không xây service SQL động dùng chung cho toàn database. Mỗi aggregate có deletion service riêng và thứ tự xóa khóa ngoại tường minh.

## 6. Giai đoạn 2 - Master/configuration data

### 6.1 Account và Staff

- Soft-delete: `Account.status=INACTIVE`, `StaffProfile.active=false`.
- Restore: chuyển về `ACTIVE`.
- Không cho account tự xóa chính mình.
- Hard-delete bị chặn nếu còn Request, Shift, AuditLog, Job hoặc dữ liệu nghiệp vụ tham chiếu.
- API Staff hiện tại được giữ làm alias tương thích.

### 6.2 Hospital và Article

- Soft-delete bằng `RETIRED`.
- Restore về `DRAFT`.
- Hard-delete Hospital chỉ khi không còn Floor.
- Hard-delete Article xóa bài viết và media reference thuộc riêng bài viết.

### 6.3 Facility

Áp dụng cho Floor, Map, Zone, Room, Endpoint, EndpointGroup và EndpointGroupMember.

- Floor, Zone, Room, Endpoint, EndpointGroup: `active=false/true`.
- Map soft-delete: `ARCHIVED`; restore về `INACTIVE`, không tự activate.
- EndpointGroupMember: `active=false/true`; hard-delete xóa vật lý membership.
- Hard-delete bị chặn khi còn Map, Request, Job, Robot, Route, Navigation hoặc telemetry tham chiếu.
- Map chỉ xóa binary assets thuộc sở hữu khi không còn runtime sử dụng.

### 6.4 Robot

Thêm lifecycle quản trị riêng, không dùng `RobotStatus` runtime để biểu diễn xóa:

```text
lifecycle_status = ACTIVE | RETIRED
```

- Soft-delete: `RETIRED`, thu hồi device secret và ngừng nhận Job mới.
- Restore: `ACTIVE`, không tự cấp device secret mới.
- Hard-delete chỉ khi robot offline, không có Job hoạt động và không còn dữ liệu nghiệp vụ tham chiếu.
- Telemetry lịch sử chỉ được dọn qua retention.

### 6.5 Schedule, Shift và Slot

- Schedule soft-delete: `RETIRED`; restore về `PAUSED`.
- Shift soft-delete: `CANCELLED` khi chưa hoàn tất.
- Slot soft-delete: `CLOSED`.
- Chỉ restore Shift/Slot chưa bắt đầu.
- Hard-delete bị chặn nếu đã tạo Job hoặc có lịch sử vận hành.
- Các API DELETE Shift/Slot hiện tại chuyển sang soft-delete; xóa vật lý dùng `/force`.

### 6.6 AI profile, Media và SystemSetting

- AI profile: `enabled=false/true`; hard-delete khi không còn runtime sử dụng.
- MediaAsset: thêm lifecycle `ACTIVE/RETIRED`; hard-delete xóa catalog và S3 object nếu không còn tham chiếu.
- SystemSetting: thêm `is_active`; soft-delete dùng application property fallback; hard-delete xóa override vật lý.

## 7. Giai đoạn 3 - Operational data

Dữ liệu vận hành không hỗ trợ restore.

### 7.1 ServiceRequest

Áp dụng chung cho InformationRequest, NavigationRequest, EscortRequest, DeliveryRequest và OperationalRequest.

- Chỉ soft-delete record terminal: `COMPLETED`, `FAILED`, `CANCELLED`.
- Thêm metadata archive để ẩn record mà không thay đổi trạng thái nghiệp vụ cuối cùng.
- Hard-delete bị chặn nếu còn Job hoạt động.
- Hard-delete xóa subtype, dữ liệu con và Job terminal thuộc Request.
- Không xóa Robot, Map, Account hoặc Incident độc lập.

### 7.2 Job

- Chỉ soft-delete `SUCCEEDED`, `FAILED`, `CANCELLED`.
- Không dùng soft-delete thay cho API cancel Job.
- Hard-delete chặn `QUEUED`, `ACCEPTED`, `RUNNING`, `RECOVERY_REQUIRED`.
- Hard-delete xóa JobTask, JobStep, JobEvent, MQTT command, orchestration work, QR/Auth verification, MappingSession, MapArtifact, AI observation và runtime receipt.
- Giữ ServiceRequest, Schedule, Robot, Map và Workflow; chỉ gỡ liên kết phù hợp.

### 7.3 Alert và EmergencyIncident

- Alert chỉ được soft/hard-delete khi `CLOSED`.
- Incident chỉ được soft/hard-delete khi `CLOSED` hoặc `FALSE_ALARM`.
- Hard-delete Alert xóa AlertRecipient.
- Hard-delete Incident chỉ xóa Alert đã đóng thuộc Incident.
- Giữ DetectionEvent độc lập.

### 7.4 VoiceInteraction

- Chỉ được xóa khi không còn `ACTIVE`.
- Hard-delete VoiceInteraction xóa VoiceCommand thuộc Interaction.
- Không hỗ trợ restore.

## 8. Giai đoạn 4 - Retention

Không tạo CRUD delete riêng cho:

- AuditLog.
- JobEvent riêng lẻ.
- RobotStatusHistory.
- RobotLocationHistory.
- DetectionEvent.
- CrowdDensityRecord.
- HeatmapRecord.
- MQTT inbox/outbox receipt.
- Token history.
- Runtime receipt.

Các bảng này chỉ được dọn qua retention:

- Dry-run thống kê trước.
- Có thể archive trước khi xóa.
- Xóa theo batch và thời hạn cấu hình.
- Kiểm tra dependency trước khi xóa.
- AuditLog không được xóa thủ công theo ID.

## 9. Migration và tương thích

Chia Flyway migration theo từng giai đoạn:

1. Lifecycle metadata và index dùng chung.
2. Master data lifecycle.
3. Operational archive metadata.
4. Retention datasets.

Các endpoint `/archive`, `/retire`, `/deactivate`, `/cancel` và `/close` hiện tại tiếp tục hoạt động để frontend không bị lỗi. Sau khi endpoint chuẩn được sử dụng ổn định, đánh dấu các endpoint action cũ là deprecated.

Mỗi aggregate phải cập nhật đồng thời:

- Controller và OpenAPI.
- Service và deletion policy.
- Repository list/get filtering.
- Reference validation.
- Audit.
- Flyway migration liên quan.

## 10. Xác minh không dùng automated test

Theo yêu cầu, không bổ sung hoặc chạy automated test.

Mỗi giai đoạn thực hiện:

```powershell
mvn -q -DskipTests compile
mvn -q -DskipTests package
git diff --check
```

Kiểm tra thủ công:

- Khởi động ứng dụng để Flyway chạy trên database phát triển.
- Kiểm tra Swagger/OpenAPI có đủ endpoint và schema.
- Gọi soft-delete, restore, deletion-impact và force-delete.
- Kiểm tra list/get ẩn đúng dữ liệu soft-delete.
- Kiểm tra hard-delete bị chặn khi còn tài nguyên hoạt động.
- Kiểm tra khóa ngoại và orphan records bằng SQL.
- Commit và push từng giai đoạn riêng để dễ rollback.
