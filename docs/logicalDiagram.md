# Database Schema

**Hệ thống:** MediAssistRover — robot hỗ trợ khoa khám bệnh ngoại trú.

**Nguồn duy nhất của schema:** `MediAssistRover_Logical_ERD_Full_Fields(1).drawio`. Đọc cả hai tab, toàn bộ field, tooltip, ghi chú và đường relationship. Các tài liệu được nhắc trong tab 02 được chép lại làm provenance; không dùng tài liệu bên ngoài để tự bổ sung schema.

**Đối soát:** 40 entity; 550 field; 110 đường FK đơn; 37 field `json`; 0 field `jsonb`. Giữ nguyên tên bảng, tên field, kiểu dữ liệu và ràng buộc đang thể hiện.

**SHA-256 file nguồn:** `7993505f4aa16fe1323a01402229e54b44ae8010e554d7b101a591c85ab05d63`.

### Reading conventions

- `PK`: khóa chính; `FK`: khóa ngoại; `UK`: ký hiệu unique một cột trong ERD. `UQ(...)`: ràng buộc unique trong ghi chú. PK ghép không có nghĩa từng cột là unique riêng lẻ.
- `NN` trong ERD được ghi là **Not Null**; `NULL` được ghi là **Nullable**.
- `Unclear from ERD`: thông tin không được nêu đủ. Không có default được ghi không đồng nghĩa default là NULL; không có nhãn UK không phủ định ràng buộc ghép/điều kiện ở phần Constraints.
- Cột Default chỉ chép giá trị mặc định được mô tả rõ. ERD không xác định các giá trị này được cấp bởi database, ứng dụng hay compiler. Không tự thêm UUID generator, timestamp default hoặc SQL DDL.
- Cardinality được đọc từ `startArrow`/`endArrow` của từng đường nối, kiểm cùng `source`, `target`, tooltip FK và field. Trong file này source là bảng được tham chiếu, target là bảng chứa FK. Đây là hướng lưu của đường vẽ, không phải chiều FK.
- `Parent → Child: 0..N` nghĩa một bản ghi cha có thể liên quan từ 0 đến nhiều bản ghi con. `Child → Parent: 0..1` nghĩa một bản ghi con có thể không có cha. Tên loại `1:1`/`1:N` ghi số lượng tối đa; bắt buộc/tùy chọn được nêu riêng.
- `ERone` được đọc là `1` theo legend của file; `ERzeroToOne` là `0..1`; `ERzeroToMany` là `0..N`. Không có đầu nối `ERoneToMany` trong file này. Điều kiện tối thiểu theo trạng thái nghiệp vụ được giữ trong Constraints, không tự đổi đầu nối.
- `enum`, `json`, `datetime UTC` và các kiểu khác được giữ nguyên. Các danh sách enum/đơn vị/giới hạn nằm trong Description nếu tooltip có nêu; không chuyển sang kiểu của một DBMS cụ thể.
- `Structure not specified in ERD`: chưa có cấu trúc JSON chi tiết. Phần đã được mô tả vẫn được chép lại; không tạo schema hoặc ví dụ mới.

### Entity index

| Entity                                        | Fields | Group                                   |
| --------------------------------------------- | ------ | --------------------------------------- |
| [Account](#account)                           | 14     | 01 · Tài khoản, nhân sự và tương tác    |
| [StaffProfile](#staffprofile)                 | 9      | 01 · Tài khoản, nhân sự và tương tác    |
| [Shift](#shift)                               | 11     | 01 · Tài khoản, nhân sự và tương tác    |
| [Slot](#slot)                                 | 8      | 01 · Tài khoản, nhân sự và tương tác    |
| [HospitalInformation](#hospitalinformation)   | 14     | 01 · Tài khoản, nhân sự và tương tác    |
| [AuditLog](#auditlog)                         | 11     | 01 · Tài khoản, nhân sự và tương tác    |
| [SystemSetting](#systemsetting)               | 9      | 01 · Tài khoản, nhân sự và tương tác    |
| [VoiceInteraction](#voiceinteraction)         | 10     | 01 · Tài khoản, nhân sự và tương tác    |
| [VoiceCommand](#voicecommand)                 | 13     | 01 · Tài khoản, nhân sự và tương tác    |
| [Map](#map)                                   | 18     | 02 · Bản đồ, vùng và điểm đích          |
| [Zone](#zone)                                 | 15     | 02 · Bản đồ, vùng và điểm đích          |
| [Room](#room)                                 | 11     | 02 · Bản đồ, vùng và điểm đích          |
| [Endpoint](#endpoint)                         | 15     | 02 · Bản đồ, vùng và điểm đích          |
| [EndpointGroup](#endpointgroup)               | 10     | 02 · Bản đồ, vùng và điểm đích          |
| [EndpointGroupMember](#endpointgroupmember)   | 6      | 02 · Bản đồ, vùng và điểm đích          |
| [StepType](#steptype)                         | 9      | 03 · Định nghĩa Workflow / 11 STEP TYPE |
| [Workflow](#workflow)                         | 24     | 03 · Định nghĩa Workflow / 11 STEP TYPE |
| [Task](#task)                                 | 8      | 03 · Định nghĩa Workflow / 11 STEP TYPE |
| [Step](#step)                                 | 18     | 03 · Định nghĩa Workflow / 11 STEP TYPE |
| [StepTransition](#steptransition)             | 9      | 03 · Định nghĩa Workflow / 11 STEP TYPE |
| [Robot](#robot)                               | 21     | 04 · Robot, giám sát và cảnh báo        |
| [RobotStatusHistory](#robotstatushistory)     | 8      | 04 · Robot, giám sát và cảnh báo        |
| [RobotLocationHistory](#robotlocationhistory) | 11     | 04 · Robot, giám sát và cảnh báo        |
| [DetectionEvent](#detectionevent)             | 14     | 04 · Robot, giám sát và cảnh báo        |
| [EmergencyIncident](#emergencyincident)       | 17     | 04 · Robot, giám sát và cảnh báo        |
| [Alert](#alert)                               | 16     | 04 · Robot, giám sát và cảnh báo        |
| [AlertRecipient](#alertrecipient)             | 12     | 04 · Robot, giám sát và cảnh báo        |
| [CrowdDensityRecord](#crowddensityrecord)     | 13     | 04 · Robot, giám sát và cảnh báo        |
| [HeatmapRecord](#heatmaprecord)               | 11     | 04 · Robot, giám sát và cảnh báo        |
| [ServiceRequest](#servicerequest)             | 17     | 05 · Yêu cầu dịch vụ và xác thực        |
| [NavigationRequest](#navigationrequest)       | 11     | 05 · Yêu cầu dịch vụ và xác thực        |
| [EscortRequest](#escortrequest)               | 14     | 05 · Yêu cầu dịch vụ và xác thực        |
| [DeliveryRequest](#deliveryrequest)           | 19     | 05 · Yêu cầu dịch vụ và xác thực        |
| [DeliveryItem](#deliveryitem)                 | 10     | 05 · Yêu cầu dịch vụ và xác thực        |
| [AuthVerification](#authverification)         | 11     | 05 · Yêu cầu dịch vụ và xác thực        |
| [Schedule](#schedule)                         | 20     | 06 · Điều phối và lịch sử thực thi      |
| [Job](#job)                                   | 35     | 06 · Điều phối và lịch sử thực thi      |
| [JobTask](#jobtask)                           | 11     | 06 · Điều phối và lịch sử thực thi      |
| [JobStep](#jobstep)                           | 18     | 06 · Điều phối và lịch sử thực thi      |
| [JobEvent](#jobevent)                         | 19     | 06 · Điều phối và lịch sử thực thi      |

## Account

### Purpose

Tài khoản; xác thực và vai trò ứng dụng.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field              | Type           | Key | Nullable | References      | Unique                                                                   | Default          | Description                                             |
| ------------------ | -------------- | --- | -------- | --------------- | ------------------------------------------------------------------------ | ---------------- | ------------------------------------------------------- |
| `account_id`       | `UUID`         | PK  | Not Null | — (không có FK) | Có — PK một cột                                                          | Unclear from ERD | ID nội bộ; chuỗi UUID được dùng khi truyền giao thức.   |
| `account_code`     | `varchar(64)`  | UK  | Not Null | — (không có FK) | Có — UK một cột                                                          | Unclear from ERD | Mã quản lý tài khoản.                                   |
| `username`         | `varchar(100)` | UK  | Not Null | — (không có FK) | Có — UK một cột                                                          | Unclear from ERD | Tên đăng nhập; duy nhất sau chuẩn hóa chữ hoa/thường.   |
| `full_name`        | `varchar(160)` | —   | Not Null | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Tên người dùng.                                         |
| `email`            | `varchar(254)` | —   | Nullable | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Email liên hệ; không mặc định là định danh đăng nhập.   |
| `phone`            | `varchar(32)`  | —   | Nullable | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Số điện thoại.                                          |
| `account_type`     | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | PATIENT, STAFF, ADMIN; quyền theo ma trận ứng dụng.     |
| `auth_provider`    | `enum`         | —   | Not Null | — (không có FK) | Thuộc UQ(auth_provider, external_subject); xem điều kiện tại Constraints | Unclear from ERD | LOCAL hoặc OIDC; lựa chọn triển khai.                   |
| `password_hash`    | `text`         | —   | Nullable | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Chỉ lưu hash mật khẩu khi LOCAL; không lưu mật khẩu rõ. |
| `external_subject` | `varchar(255)` | —   | Nullable | — (không có FK) | Thuộc UQ(auth_provider, external_subject); xem điều kiện tại Constraints | Unclear from ERD | Định danh từ nhà cung cấp OIDC.                         |
| `status`           | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | ACTIVE, INACTIVE, LOCKED.                               |
| `last_login_at`    | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Lần đăng nhập gần nhất.                                 |
| `created_at`       | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Thời điểm tạo.                                          |
| `updated_at`       | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD                                                         | Unclear from ERD | Thời điểm cập nhật.                                     |

### Relationships

- **R001 — Account 1:1 StaffProfile** (chiều ngược: 1:1). Một Account → 0..1 StaffProfile; một StaffProfile → 1 Account. FK: `StaffProfile.account_id → Account.account_id`. Đường nguồn: `fk-000`.
- **R007 — Account 1:N HospitalInformation** (chiều ngược: N:1). Một Account → 0..N HospitalInformation; một HospitalInformation → 1 Account. FK: `HospitalInformation.updated_by_account_id → Account.account_id`. Đường nguồn: `fk-006`.
- **R008 — Account 1:N AuditLog** (chiều ngược: N:1). Một Account → 0..N AuditLog; một AuditLog → 0..1 Account. FK: `AuditLog.account_id → Account.account_id`. Đường nguồn: `fk-007`.
- **R009 — Account 1:N SystemSetting** (chiều ngược: N:1). Một Account → 0..N SystemSetting; một SystemSetting → 0..1 Account. FK: `SystemSetting.updated_by_account_id → Account.account_id`. Đường nguồn: `fk-008`.
- **R010 — Account 1:N VoiceInteraction** (chiều ngược: N:1). Một Account → 0..N VoiceInteraction; một VoiceInteraction → 0..1 Account. FK: `VoiceInteraction.account_id → Account.account_id`. Đường nguồn: `fk-009`.
- **R024 — Account 1:N Workflow** (chiều ngược: N:1). Một Account → 0..N Workflow; một Workflow → 0..1 Account. FK: `Workflow.published_by_account_id → Account.account_id`. Đường nguồn: `fk-023`.
- **R045 — Account 1:N EmergencyIncident** (chiều ngược: N:1). Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. FK: `EmergencyIncident.acknowledged_by_account_id → Account.account_id`. Đường nguồn: `fk-044`.
- **R046 — Account 1:N EmergencyIncident** (chiều ngược: N:1). Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. FK: `EmergencyIncident.resolved_by_account_id → Account.account_id`. Đường nguồn: `fk-045`.
- **R053 — Account 1:N AlertRecipient** (chiều ngược: N:1). Một Account → 0..N AlertRecipient; một AlertRecipient → 1 Account. FK: `AlertRecipient.account_id → Account.account_id`. Đường nguồn: `fk-052`.
- **R059 — Account 1:N ServiceRequest** (chiều ngược: N:1). Một Account → 0..N ServiceRequest; một ServiceRequest → 0..1 Account. FK: `ServiceRequest.requested_by_account_id → Account.account_id`. Đường nguồn: `fk-058`.
- **R084 — Account 1:N DeliveryRequest** (chiều ngược: N:1). Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. FK: `DeliveryRequest.sender_account_id → Account.account_id`. Đường nguồn: `fk-083`.
- **R085 — Account 1:N DeliveryRequest** (chiều ngược: N:1). Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. FK: `DeliveryRequest.receiver_account_id → Account.account_id`. Đường nguồn: `fk-084`.
- **R089 — Account 1:N AuthVerification** (chiều ngược: N:1). Một Account → 0..N AuthVerification; một AuthVerification → 1 Account. FK: `AuthVerification.actor_account_id → Account.account_id`. Đường nguồn: `fk-088`.
- **R093 — Account 1:N Schedule** (chiều ngược: N:1). Một Account → 0..N Schedule; một Schedule → 0..1 Account. FK: `Schedule.created_by_account_id → Account.account_id`. Đường nguồn: `fk-092`.
- **R100 — Account 1:N Job** (chiều ngược: N:1). Một Account → 0..N Job; một Job → 0..1 Account. FK: `Job.cancel_requested_by_account_id → Account.account_id`. Đường nguồn: `fk-099`.
- **R101 — Account 1:N Job** (chiều ngược: N:1). Một Account → 0..N Job; một Job → 0..1 Account. FK: `Job.reconciled_by_account_id → Account.account_id`. Đường nguồn: `fk-100`.
- **R110 — Account 1:N JobEvent** (chiều ngược: N:1). Một Account → 0..N JobEvent; một JobEvent → 0..1 Account. FK: `JobEvent.actor_account_id → Account.account_id`. Đường nguồn: `fk-109`.

### Constraints and source notes

- Khóa chính: `PK(account_id)`.
- UQ(auth_provider, external_subject) khi subject có giá trị.
- LOCAL yêu cầu password_hash; OIDC yêu cầu external_subject.
- Account không bắt buộc có StaffProfile; vai trò STAFF/ADMIN được kiểm theo policy.

Nguồn: S0, S2

---

## StaffProfile

### Purpose

Hồ sơ nhân viên, bổ sung cho Account.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field               | Type           | Key     | Nullable | References           | Unique           | Default          | Description                                                                 |
| ------------------- | -------------- | ------- | -------- | -------------------- | ---------------- | ---------------- | --------------------------------------------------------------------------- |
| `staff_profile_id`  | `UUID`         | PK      | Not Null | — (không có FK)      | Có — PK một cột  | Unclear from ERD | ID hồ sơ nhân viên.                                                         |
| `account_id`        | `UUID`         | FK / UK | Not Null | `Account.account_id` | Có — UK một cột  | Unclear from ERD | Mỗi tài khoản có tối đa một hồ sơ nhân viên. Tham chiếu Account.account_id. |
| `staff_code`        | `varchar(64)`  | UK      | Not Null | — (không có FK)      | Có — UK một cột  | Unclear from ERD | Mã nhân viên.                                                               |
| `department_code`   | `varchar(64)`  | —       | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Mã khoa/bộ phận theo danh mục bên ngoài; chưa là FK.                        |
| `job_title`         | `varchar(100)` | —       | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Chức danh.                                                                  |
| `contact_extension` | `varchar(20)`  | —       | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Máy nhánh nội bộ.                                                           |
| `is_active`         | `bool`         | —       | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Còn được phân công hay không.                                               |
| `created_at`        | `datetime UTC` | —       | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                              |
| `updated_at`        | `datetime UTC` | —       | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                         |

### Relationships

- **R001 — Account 1:1 StaffProfile** (chiều ngược: 1:1). Một Account → 0..1 StaffProfile; một StaffProfile → 1 Account. FK: `StaffProfile.account_id → Account.account_id`. Đường nguồn: `fk-000`.
- **R002 — StaffProfile 1:N Shift** (chiều ngược: N:1). Một StaffProfile → 0..N Shift; một Shift → 1 StaffProfile. FK: `Shift.staff_profile_id → StaffProfile.staff_profile_id`. Đường nguồn: `fk-001`.

### Constraints and source notes

- Khóa chính: `PK(staff_profile_id)`.
- Không lặp full_name/email/phone đã có tại Account.

Nguồn: S5 + thiết kế đề xuất

---

## Shift

### Purpose

Một lần phân công một nhân viên tại một phòng.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field              | Type           | Key | Nullable | References                      | Unique           | Default          | Description                                                         |
| ------------------ | -------------- | --- | -------- | ------------------------------- | ---------------- | ---------------- | ------------------------------------------------------------------- |
| `shift_id`         | `UUID`         | PK  | Not Null | — (không có FK)                 | Có — PK một cột  | Unclear from ERD | ID lần phân công.                                                   |
| `staff_profile_id` | `UUID`         | FK  | Not Null | `StaffProfile.staff_profile_id` | Unclear from ERD | Unclear from ERD | Nhân viên được phân công. Tham chiếu StaffProfile.staff_profile_id. |
| `room_id`          | `UUID`         | FK  | Not Null | `Room.room_id`                  | Unclear from ERD | Unclear from ERD | Phòng phụ trách. Tham chiếu Room.room_id.                           |
| `shift_code`       | `varchar(64)`  | UK  | Not Null | — (không có FK)                 | Có — UK một cột  | Unclear from ERD | Mã phân công.                                                       |
| `shift_name`       | `varchar(100)` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Tên ca.                                                             |
| `starts_at`        | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Bắt đầu phân công.                                                  |
| `ends_at`          | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Kết thúc phân công.                                                 |
| `status`           | `enum`         | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | PLANNED, ACTIVE, COMPLETED, CANCELLED.                              |
| `note`             | `text`         | —   | Nullable | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Ghi chú phân công.                                                  |
| `created_at`       | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                      |
| `updated_at`       | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                 |

### Relationships

- **R002 — StaffProfile 1:N Shift** (chiều ngược: N:1). Một StaffProfile → 0..N Shift; một Shift → 1 StaffProfile. FK: `Shift.staff_profile_id → StaffProfile.staff_profile_id`. Đường nguồn: `fk-001`.
- **R003 — Room 1:N Shift** (chiều ngược: N:1). Một Room → 0..N Shift; một Shift → 1 Room. FK: `Shift.room_id → Room.room_id`. Đường nguồn: `fk-002`.
- **R004 — Shift 1:N Slot** (chiều ngược: N:1). Một Shift → 0..N Slot; một Slot → 1 Shift. FK: `Slot.shift_id → Shift.shift_id`. Đường nguồn: `fk-003`.

### Constraints and source notes

- Khóa chính: `PK(shift_id)`.
- CHECK starts_at < ends_at. Không chồng ca của cùng nhân viên theo policy.
- Shift ở đây là phân công cá nhân; ca chung nhiều nhân viên cần mô hình khác.

Nguồn: S5 + giả định cần chốt

---

## Slot

### Purpose

Khung thời gian con của Shift; không phải hồ sơ lịch hẹn bệnh nhân.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field         | Type           | Key | Nullable | References       | Unique                                                         | Default          | Description                                          |
| ------------- | -------------- | --- | -------- | ---------------- | -------------------------------------------------------------- | ---------------- | ---------------------------------------------------- |
| `slot_id`     | `UUID`         | PK  | Not Null | — (không có FK)  | Có — PK một cột                                                | Unclear from ERD | ID khung thời gian.                                  |
| `shift_id`    | `UUID`         | FK  | Not Null | `Shift.shift_id` | Thuộc UQ(shift_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Phân công chứa khung này. Tham chiếu Shift.shift_id. |
| `sequence_no` | `int`          | —   | Not Null | — (không có FK)  | Thuộc UQ(shift_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Thứ tự trong ca; >= 1.                               |
| `starts_at`   | `datetime UTC` | —   | Not Null | — (không có FK)  | Unclear from ERD                                               | Unclear from ERD | Bắt đầu khung.                                       |
| `ends_at`     | `datetime UTC` | —   | Not Null | — (không có FK)  | Unclear from ERD                                               | Unclear from ERD | Kết thúc khung.                                      |
| `status`      | `enum`         | —   | Not Null | — (không có FK)  | Unclear from ERD                                               | Unclear from ERD | AVAILABLE, BLOCKED, CLOSED.                          |
| `note`        | `text`         | —   | Nullable | — (không có FK)  | Unclear from ERD                                               | Unclear from ERD | Lý do đóng hoặc hướng dẫn.                           |
| `created_at`  | `datetime UTC` | —   | Not Null | — (không có FK)  | Unclear from ERD                                               | Unclear from ERD | Thời điểm tạo.                                       |

### Relationships

- **R004 — Shift 1:N Slot** (chiều ngược: N:1). Một Shift → 0..N Slot; một Slot → 1 Shift. FK: `Slot.shift_id → Shift.shift_id`. Đường nguồn: `fk-003`.

### Constraints and source notes

- Khóa chính: `PK(slot_id)`.
- UQ(shift_id, sequence_no).
- Slot nằm trong thời gian Shift; các Slot trong cùng Shift không chồng lấn.

Nguồn: S5 + giả định cần chốt

---

## HospitalInformation

### Purpose

Nội dung tra cứu bệnh viện/FAQ đã được duyệt.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field                     | Type           | Key | Nullable | References           | Unique           | Default          | Description                                                         |
| ------------------------- | -------------- | --- | -------- | -------------------- | ---------------- | ---------------- | ------------------------------------------------------------------- |
| `hospital_information_id` | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột  | Unclear from ERD | ID mục thông tin.                                                   |
| `map_id`                  | `UUID`         | FK  | Nullable | `Map.map_id`         | Unclear from ERD | Unclear from ERD | Bản đồ liên quan; NULL với thông tin chung. Tham chiếu Map.map_id.  |
| `room_id`                 | `UUID`         | FK  | Nullable | `Room.room_id`       | Unclear from ERD | Unclear from ERD | Phòng liên quan; NULL với thông tin chung. Tham chiếu Room.room_id. |
| `updated_by_account_id`   | `UUID`         | FK  | Not Null | `Account.account_id` | Unclear from ERD | Unclear from ERD | Người cập nhật nội dung. Tham chiếu Account.account_id.             |
| `info_category`           | `varchar(64)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | PROCEDURE, ROOM_INFO, OPENING_HOURS, FAQ... theo danh mục.          |
| `title`                   | `varchar(255)` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Tiêu đề.                                                            |
| `content`                 | `text`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Nội dung để hiển thị hoặc trả lời bằng TTS.                         |
| `language_code`           | `varchar(16)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Ví dụ vi-VN.                                                        |
| `keywords_json`           | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Mảng chuỗi từ khóa tìm kiếm.                                        |
| `status`                  | `enum`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | DRAFT, PUBLISHED, RETIRED.                                          |
| `effective_from`          | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Bắt đầu hiệu lực.                                                   |
| `effective_until`         | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Hết hiệu lực.                                                       |
| `created_at`              | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                      |
| `updated_at`              | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                 |

### Relationships

- **R005 — Map 1:N HospitalInformation** (chiều ngược: N:1). Một Map → 0..N HospitalInformation; một HospitalInformation → 0..1 Map. FK: `HospitalInformation.map_id → Map.map_id`. Đường nguồn: `fk-004`.
- **R006 — Room 1:N HospitalInformation** (chiều ngược: N:1). Một Room → 0..N HospitalInformation; một HospitalInformation → 0..1 Room. FK: `HospitalInformation.room_id → Room.room_id`. Đường nguồn: `fk-005`.
- **R007 — Account 1:N HospitalInformation** (chiều ngược: N:1). Một Account → 0..N HospitalInformation; một HospitalInformation → 1 Account. FK: `HospitalInformation.updated_by_account_id → Account.account_id`. Đường nguồn: `fk-006`.

### Constraints and source notes

- Khóa chính: `PK(hospital_information_id)`.
- Không làm bảng cha của Map/Room. Đây là nội dung, không phải thực thể Hospital.
- Chỉ QUERY nội dung PUBLISHED, đúng ngôn ngữ và còn hiệu lực.

Nguồn: S0, S2, S3

---

## AuditLog

### Purpose

Nhật ký thao tác quản trị, lưu theo dạng append-only.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field              | Type           | Key | Nullable | References           | Unique           | Default          | Description                                                          |
| ------------------ | -------------- | --- | -------- | -------------------- | ---------------- | ---------------- | -------------------------------------------------------------------- |
| `audit_log_id`     | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột  | Unclear from ERD | ID nhật ký.                                                          |
| `account_id`       | `UUID`         | FK  | Nullable | `Account.account_id` | Unclear from ERD | Unclear from ERD | Người thao tác; NULL khi do hệ thống. Tham chiếu Account.account_id. |
| `action_code`      | `varchar(80)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thao tác được ghi nhận.                                              |
| `entity_type`      | `varchar(80)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Loại thực thể bị tác động.                                           |
| `entity_id`        | `UUID`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Tham chiếu đa hình; không phải FK tới một bảng cố định.              |
| `before_data_json` | `json`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Snapshot trước thao tác; loại bỏ thông tin bí mật.                   |
| `after_data_json`  | `json`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Snapshot sau thao tác; loại bỏ thông tin bí mật.                     |
| `ip_address`       | `varchar(45)`  | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | IPv4/IPv6.                                                           |
| `user_agent`       | `text`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thông tin client.                                                    |
| `correlation_id`   | `varchar(100)` | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Mã truy vết request.                                                 |
| `occurred_at`      | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm thao tác.                                                  |

### Relationships

- **R008 — Account 1:N AuditLog** (chiều ngược: N:1). Một Account → 0..N AuditLog; một AuditLog → 0..1 Account. FK: `AuditLog.account_id → Account.account_id`. Đường nguồn: `fk-007`.

### Constraints and source notes

- Khóa chính: `PK(audit_log_id)`.
- Không ghi password_hash, token, QR secret vào snapshot.
- entity_type + entity_id do ứng dụng kiểm; không vẽ FK giả cho tham chiếu đa hình.

Nguồn: S0, S2

---

## SystemSetting

### Purpose

Cấu hình nghiệp vụ toàn hệ thống.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field                   | Type           | Key | Nullable | References           | Unique           | Default          | Description                                                            |
| ----------------------- | -------------- | --- | -------- | -------------------- | ---------------- | ---------------- | ---------------------------------------------------------------------- |
| `system_setting_id`     | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột  | Unclear from ERD | ID cấu hình.                                                           |
| `updated_by_account_id` | `UUID`         | FK  | Nullable | `Account.account_id` | Unclear from ERD | Unclear from ERD | Người cập nhật; NULL với seed hệ thống. Tham chiếu Account.account_id. |
| `setting_key`           | `varchar(100)` | UK  | Not Null | — (không có FK)      | Có — UK một cột  | Unclear from ERD | Tên khóa cấu hình.                                                     |
| `setting_value_json`    | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Giá trị JSON có kiểu.                                                  |
| `data_type`             | `enum`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | STRING, NUMBER, BOOLEAN, OBJECT, ARRAY.                                |
| `category`              | `varchar(64)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Nhóm cấu hình.                                                         |
| `description`           | `text`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Ý nghĩa của cấu hình.                                                  |
| `is_public`             | `bool`         | —   | Not Null | — (không có FK)      | Unclear from ERD | `false`          | Mặc định false; không dùng cho secret.                                 |
| `updated_at`            | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                    |

### Relationships

- **R009 — Account 1:N SystemSetting** (chiều ngược: N:1). Một Account → 0..N SystemSetting; một SystemSetting → 0..1 Account. FK: `SystemSetting.updated_by_account_id → Account.account_id`. Đường nguồn: `fk-008`.

### Constraints and source notes

- Khóa chính: `PK(system_setting_id)`.
- Validate setting_value_json theo setting_key và data_type.
- Secret và thông tin đăng nhập dịch vụ nằm trong secret/config store của triển khai.

Nguồn: S0, S2

---

## VoiceInteraction

### Purpose

Phiên tương tác giọng nói với robot.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field                  | Type           | Key | Nullable | References           | Unique           | Default          | Description                                                              |
| ---------------------- | -------------- | --- | -------- | -------------------- | ---------------- | ---------------- | ------------------------------------------------------------------------ |
| `voice_interaction_id` | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột  | Unclear from ERD | ID phiên giọng nói.                                                      |
| `account_id`           | `UUID`         | FK  | Nullable | `Account.account_id` | Unclear from ERD | Unclear from ERD | Người dùng đã định danh; có thể là khách. Tham chiếu Account.account_id. |
| `robot_id`             | `UUID`         | FK  | Not Null | `Robot.robot_id`     | Unclear from ERD | Unclear from ERD | Robot phục vụ phiên. Tham chiếu Robot.robot_id.                          |
| `language_code`        | `varchar(16)`  | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Ngôn ngữ phiên.                                                          |
| `status`               | `enum`         | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | ACTIVE, COMPLETED, CANCELLED, FAILED.                                    |
| `started_at`           | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD | Unclear from ERD | Bắt đầu phiên.                                                           |
| `ended_at`             | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Kết thúc phiên.                                                          |
| `detected_intent`      | `varchar(80)`  | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Ý định ở mức phiên.                                                      |
| `transcript_summary`   | `text`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Tóm tắt hội thoại.                                                       |
| `audio_uri`            | `text`         | —   | Nullable | — (không có FK)      | Unclear from ERD | Unclear from ERD | Tham chiếu audio nếu được lưu.                                           |

### Relationships

- **R010 — Account 1:N VoiceInteraction** (chiều ngược: N:1). Một Account → 0..N VoiceInteraction; một VoiceInteraction → 0..1 Account. FK: `VoiceInteraction.account_id → Account.account_id`. Đường nguồn: `fk-009`.
- **R011 — Robot 1:N VoiceInteraction** (chiều ngược: N:1). Một Robot → 0..N VoiceInteraction; một VoiceInteraction → 1 Robot. FK: `VoiceInteraction.robot_id → Robot.robot_id`. Đường nguồn: `fk-010`.
- **R012 — VoiceInteraction 1:N VoiceCommand** (chiều ngược: N:1). Một VoiceInteraction → 0..N VoiceCommand; một VoiceCommand → 1 VoiceInteraction. FK: `VoiceCommand.voice_interaction_id → VoiceInteraction.voice_interaction_id`. Đường nguồn: `fk-011`.

### Constraints and source notes

- Khóa chính: `PK(voice_interaction_id)`.
- ended_at >= started_at khi có giá trị.

Nguồn: S0, S2, S3

---

## VoiceCommand

### Purpose

Từng lệnh đã nhận trong một phiên; không phải catalog lệnh.

**Nhóm:** 01 · Tài khoản, nhân sự và tương tác

### Fields

| Field                  | Type           | Key | Nullable | References                              | Unique                                                                     | Default          | Description                                                                   |
| ---------------------- | -------------- | --- | -------- | --------------------------------------- | -------------------------------------------------------------------------- | ---------------- | ----------------------------------------------------------------------------- |
| `voice_command_id`     | `UUID`         | PK  | Not Null | — (không có FK)                         | Có — PK một cột                                                            | Unclear from ERD | ID lệnh giọng nói.                                                            |
| `voice_interaction_id` | `UUID`         | FK  | Not Null | `VoiceInteraction.voice_interaction_id` | Thuộc UQ(voice_interaction_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Phiên chứa lệnh. Tham chiếu VoiceInteraction.voice_interaction_id.            |
| `service_request_id`   | `UUID`         | FK  | Nullable | `ServiceRequest.service_request_id`     | Unclear from ERD                                                           | Unclear from ERD | Yêu cầu tạo ra từ lệnh, nếu có. Tham chiếu ServiceRequest.service_request_id. |
| `sequence_no`          | `int`          | —   | Not Null | — (không có FK)                         | Thuộc UQ(voice_interaction_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Thứ tự lệnh >= 1.                                                             |
| `raw_text`             | `text`         | —   | Not Null | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Văn bản nhận dạng giọng nói.                                                  |
| `normalized_text`      | `text`         | —   | Nullable | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Văn bản chuẩn hóa.                                                            |
| `intent_code`          | `varchar(80)`  | —   | Nullable | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Ý định được nhận diện.                                                        |
| `confidence_score`     | `decimal(5,4)` | —   | Nullable | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Độ tin cậy trong [0,1]; NULL nếu không có.                                    |
| `command_type`         | `varchar(64)`  | —   | Not Null | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Loại lệnh nghiệp vụ.                                                          |
| `parameters_json`      | `json`         | —   | Not Null | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Tham số trích xuất; chưa phải FK trong JSON.                                  |
| `status`               | `enum`         | —   | Not Null | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | RECEIVED, RESOLVED, REJECTED, EXECUTED, FAILED.                               |
| `received_at`          | `datetime UTC` | —   | Not Null | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Thời điểm nhận.                                                               |
| `processed_at`         | `datetime UTC` | —   | Nullable | — (không có FK)                         | Unclear from ERD                                                           | Unclear from ERD | Thời điểm xử lý xong.                                                         |

### Relationships

- **R012 — VoiceInteraction 1:N VoiceCommand** (chiều ngược: N:1). Một VoiceInteraction → 0..N VoiceCommand; một VoiceCommand → 1 VoiceInteraction. FK: `VoiceCommand.voice_interaction_id → VoiceInteraction.voice_interaction_id`. Đường nguồn: `fk-011`.
- **R013 — ServiceRequest 1:N VoiceCommand** (chiều ngược: N:1). Một ServiceRequest → 0..N VoiceCommand; một VoiceCommand → 0..1 ServiceRequest. FK: `VoiceCommand.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-012`.

### Constraints and source notes

- Khóa chính: `PK(voice_command_id)`.
- UQ(voice_interaction_id, sequence_no).

Nguồn: S0, S2

---

## Map

### Purpose

Một phiên bản bản đồ đã định danh.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field                    | Type            | Key | Nullable | References      | Unique                                                        | Default          | Description                                      |
| ------------------------ | --------------- | --- | -------- | --------------- | ------------------------------------------------------------- | ---------------- | ------------------------------------------------ |
| `map_id`                 | `UUID`          | PK  | Not Null | — (không có FK) | Có — PK một cột                                               | Unclear from ERD | ID phiên bản bản đồ; dùng trong hợp đồng map_id. |
| `map_code`               | `varchar(64)`   | —   | Not Null | — (không có FK) | Thuộc UQ(map_code, version_no); xem điều kiện tại Constraints | Unclear from ERD | Mã dòng bản đồ.                                  |
| `version_no`             | `int`           | —   | Not Null | — (không có FK) | Thuộc UQ(map_code, version_no); xem điều kiện tại Constraints | Unclear from ERD | Phiên bản >= 1.                                  |
| `map_name`               | `varchar(160)`  | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Tên bản đồ.                                      |
| `floor_label`            | `varchar(32)`   | —   | Nullable | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Tầng/khu vực.                                    |
| `width_m`                | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Chiều rộng > 0, mét.                             |
| `height_m`               | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Chiều cao bản đồ > 0, mét.                       |
| `resolution_m_per_pixel` | `decimal(10,6)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Mét/pixel > 0.                                   |
| `origin_x_m`             | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Tọa độ gốc X, mét.                               |
| `origin_y_m`             | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Tọa độ gốc Y, mét.                               |
| `origin_yaw_rad`         | `decimal(10,6)` | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Góc gốc, radian.                                 |
| `frame_id`               | `varchar(64)`   | —   | Not Null | — (không có FK) | Unclear from ERD                                              | `map`            | Hệ tọa độ, mặc định map.                         |
| `map_file_uri`           | `text`          | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Tham chiếu bộ tài nguyên bản đồ.                 |
| `asset_hash`             | `char(64)`      | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | SHA-256 của gói tài nguyên bản đồ.               |
| `status`                 | `enum`          | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | DRAFT, ACTIVE, INACTIVE, RETIRED.                |
| `commissioned_at`        | `datetime UTC`  | —   | Nullable | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Thời điểm nghiệm thu bản đồ.                     |
| `created_at`             | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Thời điểm tạo.                                   |
| `updated_at`             | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                              | Unclear from ERD | Thời điểm cập nhật.                              |

### Relationships

- **R005 — Map 1:N HospitalInformation** (chiều ngược: N:1). Một Map → 0..N HospitalInformation; một HospitalInformation → 0..1 Map. FK: `HospitalInformation.map_id → Map.map_id`. Đường nguồn: `fk-004`.
- **R014 — Map 1:N Zone** (chiều ngược: N:1). Một Map → 0..N Zone; một Zone → 1 Map. FK: `Zone.map_id → Map.map_id`. Đường nguồn: `fk-013`.
- **R016 — Map 1:N Endpoint** (chiều ngược: N:1). Một Map → 0..N Endpoint; một Endpoint → 1 Map. FK: `Endpoint.map_id → Map.map_id`. Đường nguồn: `fk-015`.
- **R019 — Map 1:N EndpointGroup** (chiều ngược: N:1). Một Map → 0..N EndpointGroup; một EndpointGroup → 1 Map. FK: `EndpointGroup.map_id → Map.map_id`. Đường nguồn: `fk-018`.
- **R022 — Map 1:N EndpointGroupMember** (chiều ngược: N:1). Một Map → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Map. FK: `EndpointGroupMember.map_id → Map.map_id`. Đường nguồn: `fk-021`.
- **R032 — Map 1:N Robot** (chiều ngược: N:1). Một Map → 0..N Robot; một Robot → 0..1 Map. FK: `Robot.current_map_id → Map.map_id`. Đường nguồn: `fk-031`.
- **R036 — Map 1:N RobotLocationHistory** (chiều ngược: N:1). Một Map → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Map. FK: `RobotLocationHistory.map_id → Map.map_id`. Đường nguồn: `fk-035`.
- **R039 — Map 1:N DetectionEvent** (chiều ngược: N:1). Một Map → 0..N DetectionEvent; một DetectionEvent → 1 Map. FK: `DetectionEvent.map_id → Map.map_id`. Đường nguồn: `fk-038`.
- **R043 — Map 1:N EmergencyIncident** (chiều ngược: N:1). Một Map → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Map. FK: `EmergencyIncident.map_id → Map.map_id`. Đường nguồn: `fk-042`.
- **R058 — Map 1:N HeatmapRecord** (chiều ngược: N:1). Một Map → 0..N HeatmapRecord; một HeatmapRecord → 1 Map. FK: `HeatmapRecord.map_id → Map.map_id`. Đường nguồn: `fk-057`.
- **R063 — Map 1:N NavigationRequest** (chiều ngược: N:1). Một Map → 0..N NavigationRequest; một NavigationRequest → 1 Map. FK: `NavigationRequest.map_id → Map.map_id`. Đường nguồn: `fk-062`.
- **R069 — Map 1:N EscortRequest** (chiều ngược: N:1). Một Map → 0..N EscortRequest; một EscortRequest → 1 Map. FK: `EscortRequest.map_id → Map.map_id`. Đường nguồn: `fk-068`.
- **R077 — Map 1:N DeliveryRequest** (chiều ngược: N:1). Một Map → 0..N DeliveryRequest; một DeliveryRequest → 1 Map. FK: `DeliveryRequest.map_id → Map.map_id`. Đường nguồn: `fk-076`.
- **R098 — Map 1:N Job** (chiều ngược: N:1). Một Map → 0..N Job; một Job → 0..1 Map. FK: `Job.map_id → Map.map_id`. Đường nguồn: `fk-097`.

### Constraints and source notes

- Khóa chính: `PK(map_id)`.
- UQ(map_code, version_no). Bản đã nghiệm thu không sửa tọa độ/hash tại chỗ.
- Tọa độ dùng mét, góc dùng radian thống nhất với MOVE; không trộn orientation_deg.

Nguồn: S0, S3

---

## Zone

### Purpose

Vùng không gian và vùng quan sát trên một Map.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field                | Type            | Key | Nullable | References      | Unique                                                                                                               | Default          | Description                                        |
| -------------------- | --------------- | --- | -------- | --------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------- | -------------------------------------------------- |
| `zone_id`            | `UUID`          | PK  | Not Null | — (không có FK) | Có — PK một cột; Thuộc UQ(map_id, zone_id); xem điều kiện tại Constraints                                            | Unclear from ERD | ID vùng.                                           |
| `map_id`             | `UUID`          | FK  | Not Null | `Map.map_id`    | Thuộc UQ(map_id, zone_code); xem điều kiện tại Constraints; Thuộc UQ(map_id, zone_id); xem điều kiện tại Constraints | Unclear from ERD | Phiên bản bản đồ chứa vùng. Tham chiếu Map.map_id. |
| `zone_code`          | `varchar(64)`   | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, zone_code); xem điều kiện tại Constraints                                                           | Unclear from ERD | Mã vùng trong Map.                                 |
| `zone_name`          | `varchar(160)`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Tên vùng.                                          |
| `zone_type`          | `enum`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | WAITING, CORRIDOR, RESTRICTED, SERVICE, OTHER.     |
| `polygon_json`       | `json`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Đường bao trong frame Map, đơn vị mét.             |
| `speed_limit_mps`    | `decimal(5,3)`  | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Trần tốc độ vùng > 0; không vượt chính sách Pi.    |
| `capacity_limit`     | `int`           | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Ngưỡng số người; >= 0.                             |
| `calibrated_area_m2` | `decimal(12,4)` | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Diện tích đã hiệu chuẩn; > 0 khi có.               |
| `camera_source_ref`  | `varchar(255)`  | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Mã camera/nguồn ảnh do dịch vụ quản lý.            |
| `camera_roi_json`    | `json`          | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Vùng quan sát được hiệu chuẩn trên ảnh.            |
| `is_active`          | `bool`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Còn dùng hay không.                                |
| `description`        | `text`          | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Mô tả.                                             |
| `created_at`         | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Thời điểm tạo.                                     |
| `updated_at`         | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                     | Unclear from ERD | Thời điểm cập nhật.                                |

### Relationships

- **R014 — Map 1:N Zone** (chiều ngược: N:1). Một Map → 0..N Zone; một Zone → 1 Map. FK: `Zone.map_id → Map.map_id`. Đường nguồn: `fk-013`.
- **R017 — Zone 1:N Endpoint** (chiều ngược: N:1). Một Zone → 0..N Endpoint; một Endpoint → 0..1 Zone. FK: `Endpoint.zone_id → Zone.zone_id`. Đường nguồn: `fk-016`.
- **R040 — Zone 1:N DetectionEvent** (chiều ngược: N:1). Một Zone → 0..N DetectionEvent; một DetectionEvent → 0..1 Zone. FK: `DetectionEvent.zone_id → Zone.zone_id`. Đường nguồn: `fk-039`.
- **R051 — Zone 1:N Alert** (chiều ngược: N:1). Một Zone → 0..N Alert; một Alert → 0..1 Zone. FK: `Alert.zone_id → Zone.zone_id`. Đường nguồn: `fk-050`.
- **R055 — Zone 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Zone → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Zone. FK: `CrowdDensityRecord.zone_id → Zone.zone_id`. Đường nguồn: `fk-054`.

### Constraints and source notes

- Khóa chính: `PK(zone_id)`.
- UQ(map_id, zone_code); UQ(map_id, zone_id) để hỗ trợ FK ghép.
- Zone giám sát ảnh phải có ánh xạ camera và thời điểm khung hình.

Nguồn: S0, S3

---

## Room

### Purpose

Phòng nghiệp vụ; có thể có nhiều điểm robot dừng.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field                       | Type           | Key | Nullable | References                        | Unique           | Default          | Description                                                                               |
| --------------------------- | -------------- | --- | -------- | --------------------------------- | ---------------- | ---------------- | ----------------------------------------------------------------------------------------- |
| `room_id`                   | `UUID`         | PK  | Not Null | — (không có FK)                   | Có — PK một cột  | Unclear from ERD | ID phòng nghiệp vụ.                                                                       |
| `default_endpoint_group_id` | `UUID`         | FK  | Nullable | `EndpointGroup.endpoint_group_id` | Unclear from ERD | Unclear from ERD | Nhóm đích mặc định khi người dùng chọn phòng. Tham chiếu EndpointGroup.endpoint_group_id. |
| `room_code`                 | `varchar(64)`  | UK  | Not Null | — (không có FK)                   | Có — UK một cột  | Unclear from ERD | Mã phòng duy nhất trong phạm vi hệ thống một bệnh viện.                                   |
| `room_name`                 | `varchar(160)` | —   | Not Null | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Tên phòng.                                                                                |
| `room_type`                 | `varchar(64)`  | —   | Not Null | — (không có FK)                   | Unclear from ERD | Unclear from ERD | EXAM, LAB, PHARMACY, PAYMENT, OTHER.                                                      |
| `floor_label`               | `varchar(32)`  | —   | Nullable | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Tầng/khu vực vật lý.                                                                      |
| `department_code`           | `varchar(64)`  | —   | Nullable | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Khoa quản lý; không phải FK khi chưa có bảng khoa.                                        |
| `description`               | `text`         | —   | Nullable | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Mô tả.                                                                                    |
| `is_active`                 | `bool`         | —   | Not Null | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Cho phép chọn trên UI.                                                                    |
| `created_at`                | `datetime UTC` | —   | Not Null | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                                            |
| `updated_at`                | `datetime UTC` | —   | Not Null | — (không có FK)                   | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                                       |

### Relationships

- **R003 — Room 1:N Shift** (chiều ngược: N:1). Một Room → 0..N Shift; một Shift → 1 Room. FK: `Shift.room_id → Room.room_id`. Đường nguồn: `fk-002`.
- **R006 — Room 1:N HospitalInformation** (chiều ngược: N:1). Một Room → 0..N HospitalInformation; một HospitalInformation → 0..1 Room. FK: `HospitalInformation.room_id → Room.room_id`. Đường nguồn: `fk-005`.
- **R015 — EndpointGroup 1:N Room** (chiều ngược: N:1). Một EndpointGroup → 0..N Room; một Room → 0..1 EndpointGroup. FK: `Room.default_endpoint_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-014`.
- **R018 — Room 1:N Endpoint** (chiều ngược: N:1). Một Room → 0..N Endpoint; một Endpoint → 0..1 Room. FK: `Endpoint.room_id → Room.room_id`. Đường nguồn: `fk-017`.
- **R064 — Room 1:N NavigationRequest** (chiều ngược: N:1). Một Room → 0..N NavigationRequest; một NavigationRequest → 0..1 Room. FK: `NavigationRequest.room_id → Room.room_id`. Đường nguồn: `fk-063`.
- **R070 — Room 1:N EscortRequest** (chiều ngược: N:1). Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. FK: `EscortRequest.origin_room_id → Room.room_id`. Đường nguồn: `fk-069`.
- **R071 — Room 1:N EscortRequest** (chiều ngược: N:1). Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. FK: `EscortRequest.destination_room_id → Room.room_id`. Đường nguồn: `fk-070`.
- **R078 — Room 1:N DeliveryRequest** (chiều ngược: N:1). Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. FK: `DeliveryRequest.pickup_room_id → Room.room_id`. Đường nguồn: `fk-077`.
- **R079 — Room 1:N DeliveryRequest** (chiều ngược: N:1). Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. FK: `DeliveryRequest.delivery_room_id → Room.room_id`. Đường nguồn: `fk-078`.

### Constraints and source notes

- Khóa chính: `PK(room_id)`.
- Map của phòng được xác định qua Endpoint ở từng phiên bản Map.
- default_endpoint_group_id là lựa chọn mặc định, không biểu diễn tất cả nhóm chứa phòng.

Nguồn: S5 + thiết kế đề xuất

---

## Endpoint

### Purpose

Một vị trí robot có thể di chuyển tới trên Map.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field           | Type            | Key | Nullable | References      | Unique                                                                                                                       | Default          | Description                                                              |
| --------------- | --------------- | --- | -------- | --------------- | ---------------------------------------------------------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------------------ |
| `endpoint_id`   | `UUID`          | PK  | Not Null | — (không có FK) | Có — PK một cột; Thuộc UQ(map_id, endpoint_id); xem điều kiện tại Constraints                                                | Unclear from ERD | ID điểm đích.                                                            |
| `map_id`        | `UUID`          | FK  | Not Null | `Map.map_id`    | Thuộc UQ(map_id, endpoint_code); xem điều kiện tại Constraints; Thuộc UQ(map_id, endpoint_id); xem điều kiện tại Constraints | Unclear from ERD | Phiên bản bản đồ. Tham chiếu Map.map_id.                                 |
| `zone_id`       | `UUID`          | FK  | Nullable | `Zone.zone_id`  | Unclear from ERD                                                                                                             | Unclear from ERD | Tối đa một vùng chính. Tham chiếu Zone.zone_id.                          |
| `room_id`       | `UUID`          | FK  | Nullable | `Room.room_id`  | Unclear from ERD                                                                                                             | Unclear from ERD | Phòng liên quan; NULL với checkpoint/hành lang. Tham chiếu Room.room_id. |
| `endpoint_code` | `varchar(64)`   | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, endpoint_code); xem điều kiện tại Constraints                                                               | Unclear from ERD | Mã điểm trong Map.                                                       |
| `endpoint_name` | `varchar(160)`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Tên điểm.                                                                |
| `endpoint_type` | `enum`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | ROOM_STOP, PICKUP, DROPOFF, WAITING, CHECKPOINT, MANUAL_CHARGE.          |
| `x_m`           | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Tọa độ X, mét.                                                           |
| `y_m`           | `decimal(12,4)` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Tọa độ Y, mét.                                                           |
| `yaw_rad`       | `decimal(10,6)` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Hướng đến đích, radian trong [-pi,pi].                                   |
| `is_active`     | `bool`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Cho phép chọn hay không.                                                 |
| `is_accessible` | `bool`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Đã xác nhận khả năng tiếp cận theo cấu hình.                             |
| `description`   | `text`          | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Ghi chú điểm.                                                            |
| `created_at`    | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Thời điểm tạo.                                                           |
| `updated_at`    | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                             | Unclear from ERD | Thời điểm cập nhật.                                                      |

### Relationships

- **R016 — Map 1:N Endpoint** (chiều ngược: N:1). Một Map → 0..N Endpoint; một Endpoint → 1 Map. FK: `Endpoint.map_id → Map.map_id`. Đường nguồn: `fk-015`.
- **R017 — Zone 1:N Endpoint** (chiều ngược: N:1). Một Zone → 0..N Endpoint; một Endpoint → 0..1 Zone. FK: `Endpoint.zone_id → Zone.zone_id`. Đường nguồn: `fk-016`.
- **R018 — Room 1:N Endpoint** (chiều ngược: N:1). Một Room → 0..N Endpoint; một Endpoint → 0..1 Room. FK: `Endpoint.room_id → Room.room_id`. Đường nguồn: `fk-017`.
- **R021 — Endpoint 1:N EndpointGroupMember** (chiều ngược: N:1). Một Endpoint → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Endpoint. FK: `EndpointGroupMember.endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-020`.
- **R044 — Endpoint 1:N EmergencyIncident** (chiều ngược: N:1). Một Endpoint → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Endpoint. FK: `EmergencyIncident.endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-043`.
- **R056 — Endpoint 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Endpoint → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 Endpoint. FK: `CrowdDensityRecord.checkpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-055`.
- **R065 — Endpoint 1:N NavigationRequest** (chiều ngược: N:1). Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. FK: `NavigationRequest.destination_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-064`.
- **R067 — Endpoint 1:N NavigationRequest** (chiều ngược: N:1). Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. FK: `NavigationRequest.resolved_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-066`.
- **R072 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.origin_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-071`.
- **R073 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.destination_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-072`.
- **R075 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.resolved_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-074`.
- **R080 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 1 Endpoint. FK: `DeliveryRequest.pickup_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-079`.
- **R081 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. FK: `DeliveryRequest.delivery_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-080`.
- **R083 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. FK: `DeliveryRequest.resolved_delivery_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-082`.

### Constraints and source notes

- Khóa chính: `PK(endpoint_id)`.
- UQ(map_id, endpoint_code); UQ(map_id, endpoint_id).
- FK ghép (map_id, zone_id) -> Zone(map_id, zone_id), khi zone_id có giá trị.
- Không khai báo docking thành tính năng đã hỗ trợ; sạc hiện do nhân viên thực hiện.

Nguồn: S0, S3, S4

---

## EndpointGroup

### Purpose

Nhóm các điểm cùng Map để chọn đích thích hợp.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field               | Type           | Key | Nullable | References      | Unique                                                                                                                          | Default          | Description                                                   |
| ------------------- | -------------- | --- | -------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------- |
| `endpoint_group_id` | `UUID`         | PK  | Not Null | — (không có FK) | Có — PK một cột; Thuộc UQ(map_id, endpoint_group_id); xem điều kiện tại Constraints                                             | Unclear from ERD | ID nhóm điểm.                                                 |
| `map_id`            | `UUID`         | FK  | Not Null | `Map.map_id`    | Thuộc UQ(map_id, group_code); xem điều kiện tại Constraints; Thuộc UQ(map_id, endpoint_group_id); xem điều kiện tại Constraints | Unclear from ERD | Giới hạn nhóm trong một phiên bản Map. Tham chiếu Map.map_id. |
| `group_code`        | `varchar(64)`  | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, group_code); xem điều kiện tại Constraints                                                                     | Unclear from ERD | Mã nhóm trong Map.                                            |
| `group_name`        | `varchar(160)` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Tên nhóm.                                                     |
| `selection_policy`  | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | PRIORITY, NEAREST_REACHABLE; policy do server kiểm.           |
| `allow_fallback`    | `bool`         | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Có được thử điểm khác trong nhóm khi đích bị chặn.            |
| `description`       | `text`         | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Mục đích nhóm.                                                |
| `is_active`         | `bool`         | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Cho phép sử dụng.                                             |
| `created_at`        | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Thời điểm tạo.                                                |
| `updated_at`        | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                                                | Unclear from ERD | Thời điểm cập nhật.                                           |

### Relationships

- **R015 — EndpointGroup 1:N Room** (chiều ngược: N:1). Một EndpointGroup → 0..N Room; một Room → 0..1 EndpointGroup. FK: `Room.default_endpoint_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-014`.
- **R019 — Map 1:N EndpointGroup** (chiều ngược: N:1). Một Map → 0..N EndpointGroup; một EndpointGroup → 1 Map. FK: `EndpointGroup.map_id → Map.map_id`. Đường nguồn: `fk-018`.
- **R020 — EndpointGroup 1:N EndpointGroupMember** (chiều ngược: N:1). Một EndpointGroup → 0..N EndpointGroupMember; một EndpointGroupMember → 1 EndpointGroup. FK: `EndpointGroupMember.endpoint_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-019`.
- **R066 — EndpointGroup 1:N NavigationRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N NavigationRequest; một NavigationRequest → 0..1 EndpointGroup. FK: `NavigationRequest.destination_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-065`.
- **R074 — EndpointGroup 1:N EscortRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N EscortRequest; một EscortRequest → 0..1 EndpointGroup. FK: `EscortRequest.destination_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-073`.
- **R082 — EndpointGroup 1:N DeliveryRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N DeliveryRequest; một DeliveryRequest → 0..1 EndpointGroup. FK: `DeliveryRequest.delivery_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-081`.

### Constraints and source notes

- Khóa chính: `PK(endpoint_group_id)`.
- UQ(map_id, group_code); UQ(map_id, endpoint_group_id).
- Chọn nhóm và chọn một endpoint là hai loại ý định khác nhau.

Nguồn: S0, S4

---

## EndpointGroupMember

### Purpose

Bảng trung gian n-n của nhóm và điểm.

**Nhóm:** 02 · Bản đồ, vùng và điểm đích

### Fields

| Field               | Type           | Key     | Nullable | References                        | Unique                                                                                                                      | Default          | Description                                                     |
| ------------------- | -------------- | ------- | -------- | --------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---------------- | --------------------------------------------------------------- |
| `endpoint_group_id` | `UUID`         | PK / FK | Not Null | `EndpointGroup.endpoint_group_id` | Thành phần PK ghép; không suy ra duy nhất riêng lẻ; Thuộc UQ(endpoint_group_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Thành phần PK ghép. Tham chiếu EndpointGroup.endpoint_group_id. |
| `endpoint_id`       | `UUID`         | PK / FK | Not Null | `Endpoint.endpoint_id`            | Thành phần PK ghép; không suy ra duy nhất riêng lẻ                                                                          | Unclear from ERD | Thành phần PK ghép. Tham chiếu Endpoint.endpoint_id.            |
| `map_id`            | `UUID`         | FK      | Not Null | `Map.map_id`                      | Unclear from ERD                                                                                                            | Unclear from ERD | Map chung để ràng buộc FK ghép. Tham chiếu Map.map_id.          |
| `sequence_no`       | `int`          | —       | Not Null | — (không có FK)                   | Thuộc UQ(endpoint_group_id, sequence_no); xem điều kiện tại Constraints                                                     | Unclear from ERD | Thứ tự ưu tiên >= 1.                                            |
| `is_active`         | `bool`         | —       | Not Null | — (không có FK)                   | Unclear from ERD                                                                                                            | Unclear from ERD | Bật/tắt thành viên mà không mất lịch sử cấu hình.               |
| `created_at`        | `datetime UTC` | —       | Not Null | — (không có FK)                   | Unclear from ERD                                                                                                            | Unclear from ERD | Thời điểm thêm.                                                 |

### Relationships

- **R020 — EndpointGroup 1:N EndpointGroupMember** (chiều ngược: N:1). Một EndpointGroup → 0..N EndpointGroupMember; một EndpointGroupMember → 1 EndpointGroup. FK: `EndpointGroupMember.endpoint_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-019`.
- **R021 — Endpoint 1:N EndpointGroupMember** (chiều ngược: N:1). Một Endpoint → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Endpoint. FK: `EndpointGroupMember.endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-020`.
- **R022 — Map 1:N EndpointGroupMember** (chiều ngược: N:1). Một Map → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Map. FK: `EndpointGroupMember.map_id → Map.map_id`. Đường nguồn: `fk-021`.

### Constraints and source notes

- Khóa chính: `PK(endpoint_group_id, endpoint_id)`.
- PK(endpoint_group_id, endpoint_id); UQ(endpoint_group_id, sequence_no).
- FK(map_id, endpoint_group_id) và FK(map_id, endpoint_id) buộc thành viên cùng Map.

Nguồn: S0, S4

---

## StepType

### Purpose

Catalog đúng 11 handler; lưu hợp đồng I/O schema version 2.

**Nhóm:** 03 · Định nghĩa Workflow / 11 STEP TYPE

### Fields

| Field                        | Type           | Key | Nullable | References      | Unique           | Default          | Description                                                                                                                                |
| ---------------------------- | -------------- | --- | -------- | --------------- | ---------------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `step_type_code`             | `varchar(40)`  | PK  | Not Null | — (không có FK) | Có — PK một cột  | Unclear from ERD | MOVE, INTERACT, DATA_SERVICE, AUTHENTICATE, CONTROL_HARDWARE, HANDLE_PAYLOAD, EXECUTE_SENSING, NOTIFY, WAIT, CHECK_CONDITION, SET_CONTEXT. |
| `display_name`               | `varchar(120)` | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Tên hiển thị handler.                                                                                                                      |
| `executor_kind`              | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | PI, SERVER, MIXED.                                                                                                                         |
| `schema_version`             | `int`          | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Hợp đồng hiện tại = 2.                                                                                                                     |
| `input_schema_json`          | `json`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | JSON Schema kiểm input, cả quy tắc điều kiện.                                                                                              |
| `output_schema_json`         | `json`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | JSON Schema kiểm data output; field null vẫn phải có khóa.                                                                                 |
| `required_capabilities_json` | `json`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Danh sách capability cần hỗ trợ.                                                                                                           |
| `is_enabled`                 | `bool`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Handler được phép cấu hình/publish.                                                                                                        |
| `description`                | `text`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Ý nghĩa và giới hạn handler.                                                                                                               |

### Relationships

- **R028 — StepType 1:N Step** (chiều ngược: N:1). Một StepType → 0..N Step; một Step → 1 StepType. FK: `Step.step_type_code → StepType.step_type_code`. Đường nguồn: `fk-027`.

### Constraints and source notes

- Khóa chính: `PK(step_type_code)`.
- Không có CALL_WORKFLOW, FACE_ID hoặc tự docking trong catalog này.
- Không sửa nghĩa contract v2 tại chỗ; phiên bản mới phải có quy trình nâng catalog.

Nguồn: S3

---

## Workflow

### Purpose

Định nghĩa một phiên bản quy trình có graph bất biến sau publish.

**Nhóm:** 03 · Định nghĩa Workflow / 11 STEP TYPE

### Fields

| Field                        | Type           | Key | Nullable | References           | Unique                                                             | Default          | Description                                                                   |
| ---------------------------- | -------------- | --- | -------- | -------------------- | ------------------------------------------------------------------ | ---------------- | ----------------------------------------------------------------------------- |
| `workflow_id`                | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột                                                    | Unclear from ERD | ID của phiên bản workflow.                                                    |
| `start_step_id`              | `UUID`         | FK  | Nullable | `Step.step_id`       | Unclear from ERD                                                   | Unclear from ERD | Bước bắt đầu; bắt buộc khi publish. Tham chiếu Step.step_id.                  |
| `published_by_account_id`    | `UUID`         | FK  | Nullable | `Account.account_id` | Unclear from ERD                                                   | Unclear from ERD | Người công bố. Tham chiếu Account.account_id.                                 |
| `workflow_code`              | `varchar(80)`  | —   | Not Null | — (không có FK)      | Thuộc UQ(workflow_code, version_no); xem điều kiện tại Constraints | Unclear from ERD | Mã nghiệp vụ của workflow.                                                    |
| `version_no`                 | `int`          | —   | Not Null | — (không có FK)      | Thuộc UQ(workflow_code, version_no); xem điều kiện tại Constraints | Unclear from ERD | Phiên bản định nghĩa >= 1.                                                    |
| `workflow_name`              | `varchar(160)` | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Tên hiển thị.                                                                 |
| `service_type`               | `varchar(64)`  | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Loại yêu cầu/workflow phục vụ.                                                |
| `description`                | `text`         | —   | Nullable | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Mô tả.                                                                        |
| `input_schema_json`          | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Schema input; tương đương validation_schema của WorkflowParam trong tài liệu. |
| `input_ui_json`              | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Cấu hình form; tương đương ui_config của WorkflowParam.                       |
| `default_inputs_json`        | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Giá trị mặc định đã kiểm schema.                                              |
| `output_mapping_json`        | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Ánh xạ kết quả cuối workflow.                                                 |
| `required_capabilities_json` | `json`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Danh sách capability trước dispatch.                                          |
| `max_duration_sec`           | `int`          | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Giới hạn toàn job > 0.                                                        |
| `max_total_visits`           | `int`          | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Giới hạn tổng lượt ghé bước > 0.                                              |
| `graph_version`              | `int`          | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Định dạng graph hiện tại = 3.                                                 |
| `schema_version`             | `int`          | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Định dạng I/O step hiện tại = 2.                                              |
| `compiled_definition_json`   | `json`         | —   | Nullable | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Artifact được biên dịch từ Task/Step/StepTransition.                          |
| `definition_hash`            | `char(64)`     | —   | Nullable | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | SHA-256 artifact; bắt buộc khi publish.                                       |
| `status`                     | `enum`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | DRAFT, PUBLISHED, RETIRED.                                                    |
| `accepts_new_jobs`           | `bool`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Được tạo job mới hay không.                                                   |
| `published_at`               | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Thời điểm publish.                                                            |
| `created_at`                 | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Thời điểm tạo.                                                                |
| `updated_at`                 | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD                                                   | Unclear from ERD | Thời điểm cập nhật.                                                           |

### Relationships

- **R023 — Step 1:N Workflow** (chiều ngược: N:1). Một Step → 0..N Workflow; một Workflow → 0..1 Step. FK: `Workflow.start_step_id → Step.step_id`. Đường nguồn: `fk-022`.
- **R024 — Account 1:N Workflow** (chiều ngược: N:1). Một Account → 0..N Workflow; một Workflow → 0..1 Account. FK: `Workflow.published_by_account_id → Account.account_id`. Đường nguồn: `fk-023`.
- **R025 — Workflow 1:N Task** (chiều ngược: N:1). Một Workflow → 0..N Task; một Task → 1 Workflow. FK: `Task.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-024`.
- **R026 — Workflow 1:N Step** (chiều ngược: N:1). Một Workflow → 0..N Step; một Step → 1 Workflow. FK: `Step.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-025`.
- **R029 — Workflow 1:N StepTransition** (chiều ngược: N:1). Một Workflow → 0..N StepTransition; một StepTransition → 1 Workflow. FK: `StepTransition.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-028`.
- **R091 — Workflow 1:N Schedule** (chiều ngược: N:1). Một Workflow → 0..N Schedule; một Schedule → 1 Workflow. FK: `Schedule.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-090`.
- **R094 — Workflow 1:N Job** (chiều ngược: N:1). Một Workflow → 0..N Job; một Job → 1 Workflow. FK: `Job.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-093`.

### Constraints and source notes

- Khóa chính: `PK(workflow_id)`.
- UQ(workflow_code, version_no). start_step phải thuộc chính workflow này.
- PUBLISHED bất biến; đổi cấu hình tạo version mới; compiled JSON là artifact, không là bản cấu hình song song.
- Task/StepTransition là nguồn graph; không lưu thêm transitions_json độc lập trong Step.

Nguồn: S0, S1, S3

---

## Task

### Purpose

Nhóm bước trong một phiên bản Workflow.

**Nhóm:** 03 · Định nghĩa Workflow / 11 STEP TYPE

### Fields

| Field         | Type           | Key | Nullable | References             | Unique                                                                                                                         | Default          | Description                                          |
| ------------- | -------------- | --- | -------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------- | ---------------------------------------------------- |
| `task_id`     | `UUID`         | PK  | Not Null | — (không có FK)        | Có — PK một cột; Thuộc UQ(workflow_id, task_id); xem điều kiện tại Constraints                                                 | Unclear from ERD | ID task định nghĩa.                                  |
| `workflow_id` | `UUID`         | FK  | Not Null | `Workflow.workflow_id` | Thuộc UQ(workflow_id, task_code); xem điều kiện tại Constraints; Thuộc UQ(workflow_id, task_id); xem điều kiện tại Constraints | Unclear from ERD | Workflow chứa task. Tham chiếu Workflow.workflow_id. |
| `task_code`   | `varchar(80)`  | —   | Not Null | — (không có FK)        | Thuộc UQ(workflow_id, task_code); xem điều kiện tại Constraints                                                                | Unclear from ERD | Mã task trong Workflow.                              |
| `task_name`   | `varchar(160)` | —   | Not Null | — (không có FK)        | Unclear from ERD                                                                                                               | Unclear from ERD | Tên task.                                            |
| `sequence_no` | `int`          | —   | Not Null | — (không có FK)        | Unclear from ERD                                                                                                               | Unclear from ERD | Thứ tự hiển thị >= 1; không quyết định graph.        |
| `description` | `text`         | —   | Nullable | — (không có FK)        | Unclear from ERD                                                                                                               | Unclear from ERD | Mô tả nhóm bước.                                     |
| `config_json` | `json`         | —   | Not Null | — (không có FK)        | Unclear from ERD                                                                                                               | Unclear from ERD | Cấu hình hiển thị/nghiệp vụ đã đăng ký.              |
| `created_at`  | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD                                                                                                               | Unclear from ERD | Thời điểm tạo.                                       |

### Relationships

- **R025 — Workflow 1:N Task** (chiều ngược: N:1). Một Workflow → 0..N Task; một Task → 1 Workflow. FK: `Task.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-024`.
- **R027 — Task 1:N Step** (chiều ngược: N:1). Một Task → 0..N Step; một Step → 1 Task. FK: `Step.task_id → Task.task_id`. Đường nguồn: `fk-026`.
- **R103 — Task 1:N JobTask** (chiều ngược: N:1). Một Task → 0..N JobTask; một JobTask → 1 Task. FK: `JobTask.task_id → Task.task_id`. Đường nguồn: `fk-102`.

### Constraints and source notes

- Khóa chính: `PK(task_id)`.
- UQ(workflow_id, task_code); UQ(workflow_id, task_id).
- Task không tự retry nguyên nhóm và không tự mở khóa lại; điều khiển theo graph Step.

Nguồn: S0, S1, S3

---

## Step

### Purpose

Bước cấu hình: handler, tham số và chính sách thực thi.

**Nhóm:** 03 · Định nghĩa Workflow / 11 STEP TYPE

### Fields

| Field                | Type           | Key | Nullable | References                | Unique                                                                                                                         | Default          | Description                                                          |
| -------------------- | -------------- | --- | -------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------- | -------------------------------------------------------------------- |
| `step_id`            | `UUID`         | PK  | Not Null | — (không có FK)           | Có — PK một cột; Thuộc UQ(workflow_id, step_id); xem điều kiện tại Constraints                                                 | Unclear from ERD | ID bước định nghĩa.                                                  |
| `workflow_id`        | `UUID`         | FK  | Not Null | `Workflow.workflow_id`    | Thuộc UQ(workflow_id, step_code); xem điều kiện tại Constraints; Thuộc UQ(workflow_id, step_id); xem điều kiện tại Constraints | Unclear from ERD | Workflow chứa bước; hỗ trợ FK ghép. Tham chiếu Workflow.workflow_id. |
| `task_id`            | `UUID`         | FK  | Not Null | `Task.task_id`            | Unclear from ERD                                                                                                               | Unclear from ERD | Nhóm task của bước. Tham chiếu Task.task_id.                         |
| `step_type_code`     | `varchar(40)`  | FK  | Not Null | `StepType.step_type_code` | Unclear from ERD                                                                                                               | Unclear from ERD | Một trong 11 handler. Tham chiếu StepType.step_type_code.            |
| `step_code`          | `varchar(80)`  | —   | Not Null | — (không có FK)           | Thuộc UQ(workflow_id, step_code); xem điều kiện tại Constraints                                                                | Unclear from ERD | Mã duy nhất trong Workflow; dùng trong /outputs/STEP_CODE.           |
| `step_name`          | `varchar(160)` | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Tên hiển thị.                                                        |
| `sequence_no`        | `int`          | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Thứ tự hiển thị trong task.                                          |
| `description`        | `text`         | —   | Nullable | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Mô tả hành động.                                                     |
| `is_enabled`         | `bool`         | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Chỉ được thay đổi khi workflow là DRAFT.                             |
| `params_json`        | `json`         | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Hằng và $ref được đăng ký; hợp đồng params của handler.              |
| `input_mapping_json` | `json`         | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Binding input/outputs/vars; ưu tiên binding đè hằng theo compiler.   |
| `timeout_sec`        | `int`          | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Timeout một lượt thực thi > 0.                                       |
| `max_attempts`       | `int`          | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | `1`              | Tổng số lần thử, gồm lần đầu; bản mặc định = 1.                      |
| `retry_delay_sec`    | `int`          | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | `0`              | Mặc định = 0; >= 0.                                                  |
| `retry_codes_json`   | `json`         | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | `[]`             | Mảng mã lỗi được phép retry; bản mặc định [].                        |
| `max_visits`         | `int`          | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Giới hạn vòng lặp ghé bước; > 0.                                     |
| `audit_level`        | `enum`         | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | BASIC, FULL; bước xác thực/giao nhận FULL.                           |
| `created_at`         | `datetime UTC` | —   | Not Null | — (không có FK)           | Unclear from ERD                                                                                                               | Unclear from ERD | Thời điểm tạo.                                                       |

### Relationships

- **R023 — Step 1:N Workflow** (chiều ngược: N:1). Một Step → 0..N Workflow; một Workflow → 0..1 Step. FK: `Workflow.start_step_id → Step.step_id`. Đường nguồn: `fk-022`.
- **R026 — Workflow 1:N Step** (chiều ngược: N:1). Một Workflow → 0..N Step; một Step → 1 Workflow. FK: `Step.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-025`.
- **R027 — Task 1:N Step** (chiều ngược: N:1). Một Task → 0..N Step; một Step → 1 Task. FK: `Step.task_id → Task.task_id`. Đường nguồn: `fk-026`.
- **R028 — StepType 1:N Step** (chiều ngược: N:1). Một StepType → 0..N Step; một Step → 1 StepType. FK: `Step.step_type_code → StepType.step_type_code`. Đường nguồn: `fk-027`.
- **R030 — Step 1:N StepTransition** (chiều ngược: N:1). Một Step → 0..N StepTransition; một StepTransition → 1 Step. FK: `StepTransition.from_step_id → Step.step_id`. Đường nguồn: `fk-029`.
- **R031 — Step 1:N StepTransition** (chiều ngược: N:1). Một Step → 0..N StepTransition; một StepTransition → 0..1 Step. FK: `StepTransition.to_step_id → Step.step_id`. Đường nguồn: `fk-030`.
- **R105 — Step 1:N JobStep** (chiều ngược: N:1). Một Step → 0..N JobStep; một JobStep → 1 Step. FK: `JobStep.step_id → Step.step_id`. Đường nguồn: `fk-104`.

### Constraints and source notes

- Khóa chính: `PK(step_id)`.
- UQ(workflow_id, step_code); UQ(workflow_id, step_id).
- FK(workflow_id, task_id) -> Task(workflow_id, task_id).
- Vòng lặp do StepTransition; không dùng sequence_no thay graph.
- Không tự retry hành động vật lý khi mất ACK; mặc định max_attempts=1.

Nguồn: S0, S1, S3

---

## StepTransition

### Purpose

Cạnh graph có FK thật tới bước nguồn/đích.

**Nhóm:** 03 · Định nghĩa Workflow / 11 STEP TYPE

### Fields

| Field             | Type   | Key | Nullable | References             | Unique                                                                   | Default          | Description                                                |
| ----------------- | ------ | --- | -------- | ---------------------- | ------------------------------------------------------------------------ | ---------------- | ---------------------------------------------------------- |
| `transition_id`   | `UUID` | PK  | Not Null | — (không có FK)        | Có — PK một cột                                                          | Unclear from ERD | ID cạnh graph.                                             |
| `workflow_id`     | `UUID` | FK  | Not Null | `Workflow.workflow_id` | Unclear from ERD                                                         | Unclear from ERD | Workflow của cạnh. Tham chiếu Workflow.workflow_id.        |
| `from_step_id`    | `UUID` | FK  | Not Null | `Step.step_id`         | Thuộc UQ(from_step_id, outcome, priority); xem điều kiện tại Constraints | Unclear from ERD | Bước nguồn. Tham chiếu Step.step_id.                       |
| `to_step_id`      | `UUID` | FK  | Nullable | `Step.step_id`         | Unclear from ERD                                                         | Unclear from ERD | Bước đích; NULL nếu kết thúc job. Tham chiếu Step.step_id. |
| `outcome`         | `enum` | —   | Not Null | — (không có FK)        | Thuộc UQ(from_step_id, outcome, priority); xem điều kiện tại Constraints | Unclear from ERD | SUCCESS, FAILURE, TIMEOUT.                                 |
| `condition_json`  | `json` | —   | Nullable | — (không có FK)        | Unclear from ERD                                                         | Unclear from ERD | SAFE_EXPR_V1; xét output, ví dụ confirmed=true.            |
| `priority`        | `int`  | —   | Not Null | — (không có FK)        | Thuộc UQ(from_step_id, outcome, priority); xem điều kiện tại Constraints | Unclear from ERD | Số nhỏ xét trước; >= 1.                                    |
| `is_default`      | `bool` | —   | Not Null | — (không có FK)        | Unclear from ERD                                                         | Unclear from ERD | Nhánh cuối không điều kiện của outcome.                    |
| `terminal_status` | `enum` | —   | Nullable | — (không có FK)        | Unclear from ERD                                                         | Unclear from ERD | SUCCEEDED, FAILED, CANCELLED, RECOVERY_REQUIRED.           |

### Relationships

- **R029 — Workflow 1:N StepTransition** (chiều ngược: N:1). Một Workflow → 0..N StepTransition; một StepTransition → 1 Workflow. FK: `StepTransition.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-028`.
- **R030 — Step 1:N StepTransition** (chiều ngược: N:1). Một Step → 0..N StepTransition; một StepTransition → 1 Step. FK: `StepTransition.from_step_id → Step.step_id`. Đường nguồn: `fk-029`.
- **R031 — Step 1:N StepTransition** (chiều ngược: N:1). Một Step → 0..N StepTransition; một StepTransition → 0..1 Step. FK: `StepTransition.to_step_id → Step.step_id`. Đường nguồn: `fk-030`.

### Constraints and source notes

- Khóa chính: `PK(transition_id)`.
- UQ(from_step_id, outcome, priority); tối đa một default mỗi (from_step_id,outcome).
- XOR(to_step_id, terminal_status); default không condition, nhánh khác phải có condition.
- FK ghép buộc from/to cùng workflow; compiler kiểm start, reachable, fallback và giới hạn loop.

Nguồn: S1, S3 + chuẩn hóa đề xuất

---

## Robot

### Purpose

Robot vật lý và snapshot vận hành gần nhất.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                   | Type           | Key | Nullable | References      | Unique           | Default          | Description                                                        |
| ----------------------- | -------------- | --- | -------- | --------------- | ---------------- | ---------------- | ------------------------------------------------------------------ |
| `robot_id`              | `UUID`         | PK  | Not Null | — (không có FK) | Có — PK một cột  | Unclear from ERD | ID robot.                                                          |
| `current_map_id`        | `UUID`         | FK  | Nullable | `Map.map_id`    | Unclear from ERD | Unclear from ERD | Map đang được triển khai trên Pi. Tham chiếu Map.map_id.           |
| `robot_code`            | `varchar(64)`  | UK  | Not Null | — (không có FK) | Có — UK một cột  | Unclear from ERD | Mã robot công khai.                                                |
| `robot_name`            | `varchar(160)` | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Tên robot.                                                         |
| `model`                 | `varchar(100)` | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | MentorPi M1 cho phần cứng hiện tại.                                |
| `serial_number`         | `varchar(100)` | UK  | Nullable | — (không có FK) | Có — UK một cột  | Unclear from ERD | Số sê-ri nếu có.                                                   |
| `runtime_version`       | `varchar(64)`  | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Phiên bản runtime Pi.                                              |
| `firmware_version`      | `varchar(64)`  | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Phiên bản firmware.                                                |
| `protocol_version`      | `int`          | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Giao thức hiện tại = 1.                                            |
| `capabilities_json`     | `json`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Capability từ HELLO đã xác minh.                                   |
| `deployed_catalog_hash` | `char(64)`     | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Hash catalog đã triển khai; kiểm trước dispatch.                   |
| `map_asset_hash`        | `char(64)`     | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Hash Map trên Pi; phải khớp Map.asset_hash.                        |
| `status`                | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | OFFLINE, STARTING, IDLE, BUSY, EMERGENCY_STOP, ERROR, MAINTENANCE. |
| `battery_raw`           | `int`          | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | UInt16 từ driver, [0,65535].                                       |
| `battery_unit`          | `varchar(32)`  | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | UNKNOWN cho tới khi xác minh đơn vị.                               |
| `battery_percent`       | `decimal(5,2)` | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Hiện phải NULL; không suy từ raw khi chưa hiệu chuẩn.              |
| `battery_captured_at`   | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Tuổi mẫu pin được suy từ thời điểm này.                            |
| `last_heartbeat_at`     | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Heartbeat gần nhất.                                                |
| `commissioned_at`       | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD | Unclear from ERD | Thời điểm nghiệm thu.                                              |
| `created_at`            | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                     |
| `updated_at`            | `datetime UTC` | —   | Not Null | — (không có FK) | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật snapshot.                                       |

### Relationships

- **R011 — Robot 1:N VoiceInteraction** (chiều ngược: N:1). Một Robot → 0..N VoiceInteraction; một VoiceInteraction → 1 Robot. FK: `VoiceInteraction.robot_id → Robot.robot_id`. Đường nguồn: `fk-010`.
- **R032 — Map 1:N Robot** (chiều ngược: N:1). Một Map → 0..N Robot; một Robot → 0..1 Map. FK: `Robot.current_map_id → Map.map_id`. Đường nguồn: `fk-031`.
- **R033 — Robot 1:N RobotStatusHistory** (chiều ngược: N:1). Một Robot → 0..N RobotStatusHistory; một RobotStatusHistory → 1 Robot. FK: `RobotStatusHistory.robot_id → Robot.robot_id`. Đường nguồn: `fk-032`.
- **R035 — Robot 1:N RobotLocationHistory** (chiều ngược: N:1). Một Robot → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Robot. FK: `RobotLocationHistory.robot_id → Robot.robot_id`. Đường nguồn: `fk-034`.
- **R038 — Robot 1:N DetectionEvent** (chiều ngược: N:1). Một Robot → 0..N DetectionEvent; một DetectionEvent → 1 Robot. FK: `DetectionEvent.robot_id → Robot.robot_id`. Đường nguồn: `fk-037`.
- **R042 — Robot 1:N EmergencyIncident** (chiều ngược: N:1). Một Robot → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Robot. FK: `EmergencyIncident.robot_id → Robot.robot_id`. Đường nguồn: `fk-041`.
- **R054 — Robot 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Robot → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Robot. FK: `CrowdDensityRecord.robot_id → Robot.robot_id`. Đường nguồn: `fk-053`.
- **R060 — Robot 1:N ServiceRequest** (chiều ngược: N:1). Một Robot → 0..N ServiceRequest; một ServiceRequest → 0..1 Robot. FK: `ServiceRequest.requested_robot_id → Robot.robot_id`. Đường nguồn: `fk-059`.
- **R092 — Robot 1:N Schedule** (chiều ngược: N:1). Một Robot → 0..N Schedule; một Schedule → 0..1 Robot. FK: `Schedule.robot_id → Robot.robot_id`. Đường nguồn: `fk-091`.
- **R097 — Robot 1:N Job** (chiều ngược: N:1). Một Robot → 0..N Job; một Job → 0..1 Robot. FK: `Job.robot_id → Robot.robot_id`. Đường nguồn: `fk-096`.
- **R107 — Robot 1:N JobEvent** (chiều ngược: N:1). Một Robot → 0..N JobEvent; một JobEvent → 0..1 Robot. FK: `JobEvent.robot_id → Robot.robot_id`. Đường nguồn: `fk-106`.

### Constraints and source notes

- Khóa chính: `PK(robot_id)`.
- Không lưu active_job_id lặp; tra Job có trạng thái đang chiếm robot.
- Snapshot không thay lịch sử. Không giả định sensors_ready nghĩa mọi capability đã sẵn sàng.

Nguồn: S0, S2, S3

---

## RobotStatusHistory

### Purpose

Lịch sử chuyển trạng thái robot.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                     | Type           | Key | Nullable | References       | Unique           | Default          | Description                                  |
| ------------------------- | -------------- | --- | -------- | ---------------- | ---------------- | ---------------- | -------------------------------------------- |
| `robot_status_history_id` | `UUID`         | PK  | Not Null | — (không có FK)  | Có — PK một cột  | Unclear from ERD | ID bản ghi.                                  |
| `robot_id`                | `UUID`         | FK  | Not Null | `Robot.robot_id` | Unclear from ERD | Unclear from ERD | Robot liên quan. Tham chiếu Robot.robot_id.  |
| `job_id`                  | `UUID`         | FK  | Nullable | `Job.job_id`     | Unclear from ERD | Unclear from ERD | Job liên quan nếu có. Tham chiếu Job.job_id. |
| `previous_status`         | `enum`         | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | RobotStatus cũ; NULL với bản ghi đầu.        |
| `new_status`              | `enum`         | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | RobotStatus mới.                             |
| `reason`                  | `text`         | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Lý do chuyển trạng thái.                     |
| `error_code`              | `varchar(100)` | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Mã lỗi liên quan.                            |
| `recorded_at`             | `datetime UTC` | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Thời điểm ghi nhận.                          |

### Relationships

- **R033 — Robot 1:N RobotStatusHistory** (chiều ngược: N:1). Một Robot → 0..N RobotStatusHistory; một RobotStatusHistory → 1 Robot. FK: `RobotStatusHistory.robot_id → Robot.robot_id`. Đường nguồn: `fk-032`.
- **R034 — Job 1:N RobotStatusHistory** (chiều ngược: N:1). Một Job → 0..N RobotStatusHistory; một RobotStatusHistory → 0..1 Job. FK: `RobotStatusHistory.job_id → Job.job_id`. Đường nguồn: `fk-033`.

### Constraints and source notes

- Khóa chính: `PK(robot_status_history_id)`.
- Append-only; cùng transaction với cập nhật Robot.status.

Nguồn: S0, S3

---

## RobotLocationHistory

### Purpose

Snapshot vị trí robot trong Map.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                       | Type            | Key | Nullable | References       | Unique           | Default          | Description                                  |
| --------------------------- | --------------- | --- | -------- | ---------------- | ---------------- | ---------------- | -------------------------------------------- |
| `robot_location_history_id` | `UUID`          | PK  | Not Null | — (không có FK)  | Có — PK một cột  | Unclear from ERD | ID bản ghi vị trí.                           |
| `robot_id`                  | `UUID`          | FK  | Not Null | `Robot.robot_id` | Unclear from ERD | Unclear from ERD | Robot được đo. Tham chiếu Robot.robot_id.    |
| `map_id`                    | `UUID`          | FK  | Not Null | `Map.map_id`     | Unclear from ERD | Unclear from ERD | Map dùng cho tọa độ. Tham chiếu Map.map_id.  |
| `job_id`                    | `UUID`          | FK  | Nullable | `Job.job_id`     | Unclear from ERD | Unclear from ERD | Job đang chạy nếu có. Tham chiếu Job.job_id. |
| `x_m`                       | `decimal(12,4)` | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Tọa độ X, mét.                               |
| `y_m`                       | `decimal(12,4)` | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Tọa độ Y, mét.                               |
| `yaw_rad`                   | `decimal(10,6)` | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Hướng, radian.                               |
| `localization_confidence`   | `decimal(5,4)`  | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Độ tin cậy [0,1].                            |
| `is_fresh`                  | `bool`          | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Mẫu còn hợp lệ theo policy.                  |
| `captured_at`               | `datetime UTC`  | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Thời điểm đo tại robot.                      |
| `received_at`               | `datetime UTC`  | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Thời điểm backend nhận.                      |

### Relationships

- **R035 — Robot 1:N RobotLocationHistory** (chiều ngược: N:1). Một Robot → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Robot. FK: `RobotLocationHistory.robot_id → Robot.robot_id`. Đường nguồn: `fk-034`.
- **R036 — Map 1:N RobotLocationHistory** (chiều ngược: N:1). Một Map → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Map. FK: `RobotLocationHistory.map_id → Map.map_id`. Đường nguồn: `fk-035`.
- **R037 — Job 1:N RobotLocationHistory** (chiều ngược: N:1). Một Job → 0..N RobotLocationHistory; một RobotLocationHistory → 0..1 Job. FK: `RobotLocationHistory.job_id → Job.job_id`. Đường nguồn: `fk-036`.

### Constraints and source notes

- Khóa chính: `PK(robot_location_history_id)`.
- Index(robot_id, captured_at); map_id gắn phiên bản bản đồ đã dùng khi đo.

Nguồn: S0, S3

---

## DetectionEvent

### Purpose

Sự kiện phát hiện từ robot/dịch vụ ảnh, chưa chắc là incident.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                | Type            | Key | Nullable | References       | Unique           | Default          | Description                                              |
| -------------------- | --------------- | --- | -------- | ---------------- | ---------------- | ---------------- | -------------------------------------------------------- |
| `detection_event_id` | `UUID`          | PK  | Not Null | — (không có FK)  | Có — PK một cột  | Unclear from ERD | ID sự kiện detector; dùng chống trùng.                   |
| `robot_id`           | `UUID`          | FK  | Not Null | `Robot.robot_id` | Unclear from ERD | Unclear from ERD | Robot nguồn. Tham chiếu Robot.robot_id.                  |
| `map_id`             | `UUID`          | FK  | Not Null | `Map.map_id`     | Unclear from ERD | Unclear from ERD | Map nguồn. Tham chiếu Map.map_id.                        |
| `zone_id`            | `UUID`          | FK  | Nullable | `Zone.zone_id`   | Unclear from ERD | Unclear from ERD | Vùng quan sát nếu xác định. Tham chiếu Zone.zone_id.     |
| `event_type`         | `enum`          | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | SUSPECTED_FALL, RESTRICTED_INTRUSION, SOS_SIGNAL, OTHER. |
| `confidence_score`   | `decimal(5,4)`  | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Độ tin cậy [0,1].                                        |
| `x_m`                | `decimal(12,4)` | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Tọa độ X của đối tượng hoặc robot theo location_basis.   |
| `y_m`                | `decimal(12,4)` | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Tọa độ Y, mét.                                           |
| `location_basis`     | `enum`          | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | OBJECT, ROBOT, UNKNOWN.                                  |
| `model_version`      | `varchar(100)`  | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Phiên bản mô hình nhận biết.                             |
| `event_payload_json` | `json`          | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Chi tiết kết quả detector.                               |
| `evidence_ref`       | `text`          | —   | Nullable | — (không có FK)  | Unclear from ERD | Unclear from ERD | Mã/URI minh chứng, không ảnh base64.                     |
| `detected_at`        | `datetime UTC`  | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Thời điểm sự kiện.                                       |
| `received_at`        | `datetime UTC`  | —   | Not Null | — (không có FK)  | Unclear from ERD | Unclear from ERD | Thời điểm backend nhận.                                  |

### Relationships

- **R038 — Robot 1:N DetectionEvent** (chiều ngược: N:1). Một Robot → 0..N DetectionEvent; một DetectionEvent → 1 Robot. FK: `DetectionEvent.robot_id → Robot.robot_id`. Đường nguồn: `fk-037`.
- **R039 — Map 1:N DetectionEvent** (chiều ngược: N:1). Một Map → 0..N DetectionEvent; một DetectionEvent → 1 Map. FK: `DetectionEvent.map_id → Map.map_id`. Đường nguồn: `fk-038`.
- **R040 — Zone 1:N DetectionEvent** (chiều ngược: N:1). Một Zone → 0..N DetectionEvent; một DetectionEvent → 0..1 Zone. FK: `DetectionEvent.zone_id → Zone.zone_id`. Đường nguồn: `fk-039`.
- **R041 — DetectionEvent 1:N EmergencyIncident** (chiều ngược: N:1). Một DetectionEvent → 0..N EmergencyIncident; một EmergencyIncident → 0..1 DetectionEvent. FK: `EmergencyIncident.detection_event_id → DetectionEvent.detection_event_id`. Đường nguồn: `fk-040`.
- **R048 — DetectionEvent 1:N Alert** (chiều ngược: N:1). Một DetectionEvent → 0..N Alert; một Alert → 0..1 DetectionEvent. FK: `Alert.detection_event_id → DetectionEvent.detection_event_id`. Đường nguồn: `fk-047`.

### Constraints and source notes

- Khóa chính: `PK(detection_event_id)`.
- x_m/y_m cùng có hoặc cùng NULL; zone phải cùng Map.
- Một event có thể không tạo incident nếu không đạt ngưỡng.

Nguồn: S0, S1, S3

---

## EmergencyIncident

### Purpose

Hồ sơ sự cố và quá trình nhân viên xử lý.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                        | Type            | Key | Nullable | References                          | Unique                                                                     | Default          | Description                                                                              |
| ---------------------------- | --------------- | --- | -------- | ----------------------------------- | -------------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------------- |
| `incident_id`                | `UUID`          | PK  | Not Null | — (không có FK)                     | Có — PK một cột                                                            | Unclear from ERD | ID sự cố.                                                                                |
| `detection_event_id`         | `UUID`          | FK  | Nullable | `DetectionEvent.detection_event_id` | Thuộc UQ(detection_event_id, incident_type); xem điều kiện tại Constraints | Unclear from ERD | Nguồn detector; SOS trực tiếp có thể NULL. Tham chiếu DetectionEvent.detection_event_id. |
| `robot_id`                   | `UUID`          | FK  | Nullable | `Robot.robot_id`                    | Unclear from ERD                                                           | Unclear from ERD | Robot phát hiện/tiếp nhận ban đầu. Tham chiếu Robot.robot_id.                            |
| `map_id`                     | `UUID`          | FK  | Nullable | `Map.map_id`                        | Unclear from ERD                                                           | Unclear from ERD | Map vị trí sự cố nếu biết. Tham chiếu Map.map_id.                                        |
| `endpoint_id`                | `UUID`          | FK  | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD                                                           | Unclear from ERD | Điểm gần sự cố, không bắt buộc. Tham chiếu Endpoint.endpoint_id.                         |
| `acknowledged_by_account_id` | `UUID`          | FK  | Nullable | `Account.account_id`                | Unclear from ERD                                                           | Unclear from ERD | Nhân viên tiếp nhận. Tham chiếu Account.account_id.                                      |
| `resolved_by_account_id`     | `UUID`          | FK  | Nullable | `Account.account_id`                | Unclear from ERD                                                           | Unclear from ERD | Nhân viên xử lý xong. Tham chiếu Account.account_id.                                     |
| `incident_type`              | `varchar(64)`   | —   | Not Null | — (không có FK)                     | Thuộc UQ(detection_event_id, incident_type); xem điều kiện tại Constraints | Unclear from ERD | SOS, SUSPECTED_FALL, INTRUSION... theo policy.                                           |
| `severity`                   | `enum`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | WARNING, CRITICAL.                                                                       |
| `status`                     | `enum`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED.                                       |
| `description`                | `text`          | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Mô tả.                                                                                   |
| `x_m`                        | `decimal(12,4)` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Vị trí sự cố nếu có.                                                                     |
| `y_m`                        | `decimal(12,4)` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Vị trí sự cố nếu có.                                                                     |
| `occurred_at`                | `datetime UTC`  | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Thời điểm xảy ra.                                                                        |
| `acknowledged_at`            | `datetime UTC`  | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Đã tiếp nhận, chưa phải đã xử lý.                                                        |
| `resolved_at`                | `datetime UTC`  | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Đã xử lý xong.                                                                           |
| `closed_at`                  | `datetime UTC`  | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                           | Unclear from ERD | Đóng hồ sơ.                                                                              |

### Relationships

- **R041 — DetectionEvent 1:N EmergencyIncident** (chiều ngược: N:1). Một DetectionEvent → 0..N EmergencyIncident; một EmergencyIncident → 0..1 DetectionEvent. FK: `EmergencyIncident.detection_event_id → DetectionEvent.detection_event_id`. Đường nguồn: `fk-040`.
- **R042 — Robot 1:N EmergencyIncident** (chiều ngược: N:1). Một Robot → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Robot. FK: `EmergencyIncident.robot_id → Robot.robot_id`. Đường nguồn: `fk-041`.
- **R043 — Map 1:N EmergencyIncident** (chiều ngược: N:1). Một Map → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Map. FK: `EmergencyIncident.map_id → Map.map_id`. Đường nguồn: `fk-042`.
- **R044 — Endpoint 1:N EmergencyIncident** (chiều ngược: N:1). Một Endpoint → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Endpoint. FK: `EmergencyIncident.endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-043`.
- **R045 — Account 1:N EmergencyIncident** (chiều ngược: N:1). Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. FK: `EmergencyIncident.acknowledged_by_account_id → Account.account_id`. Đường nguồn: `fk-044`.
- **R046 — Account 1:N EmergencyIncident** (chiều ngược: N:1). Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. FK: `EmergencyIncident.resolved_by_account_id → Account.account_id`. Đường nguồn: `fk-045`.
- **R047 — EmergencyIncident 1:N Alert** (chiều ngược: N:1). Một EmergencyIncident → 0..N Alert; một Alert → 0..1 EmergencyIncident. FK: `Alert.incident_id → EmergencyIncident.incident_id`. Đường nguồn: `fk-046`.
- **R061 — EmergencyIncident 1:N ServiceRequest** (chiều ngược: N:1). Một EmergencyIncident → 0..N ServiceRequest; một ServiceRequest → 0..1 EmergencyIncident. FK: `ServiceRequest.related_incident_id → EmergencyIncident.incident_id`. Đường nguồn: `fk-060`.

### Constraints and source notes

- Khóa chính: `PK(incident_id)`.
- UQ(detection_event_id, incident_type) khi detection_event_id không NULL.
- ACK/resolve actor và thời điểm phải nhất quán; tọa độ hoặc endpoint yêu cầu map_id.
- Nếu endpoint_id có giá trị: FK(map_id, endpoint_id) -> Endpoint(map_id, endpoint_id).

Nguồn: S0, S1, S3

---

## Alert

### Purpose

Một thông báo nghiệp vụ; notification_id trong contract là alert_id.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                       | Type           | Key | Nullable | References                          | Unique           | Default          | Description                                                                                  |
| --------------------------- | -------------- | --- | -------- | ----------------------------------- | ---------------- | ---------------- | -------------------------------------------------------------------------------------------- |
| `alert_id`                  | `UUID`         | PK  | Not Null | — (không có FK)                     | Có — PK một cột  | Unclear from ERD | ID thông báo; trả dưới tên notification_id.                                                  |
| `incident_id`               | `UUID`         | FK  | Nullable | `EmergencyIncident.incident_id`     | Unclear from ERD | Unclear from ERD | Sự cố liên quan. Tham chiếu EmergencyIncident.incident_id.                                   |
| `detection_event_id`        | `UUID`         | FK  | Nullable | `DetectionEvent.detection_event_id` | Unclear from ERD | Unclear from ERD | Detector liên quan khi chưa tạo được incident. Tham chiếu DetectionEvent.detection_event_id. |
| `job_step_id`               | `UUID`         | FK  | Nullable | `JobStep.job_step_id`               | Unclear from ERD | Unclear from ERD | Bước NOTIFY tạo thông báo; NULL với cảnh báo độc lập. Tham chiếu JobStep.job_step_id.        |
| `service_request_id`        | `UUID`         | FK  | Nullable | `ServiceRequest.service_request_id` | Unclear from ERD | Unclear from ERD | Yêu cầu giao nhận/sạc/phục hồi liên quan. Tham chiếu ServiceRequest.service_request_id.      |
| `zone_id`                   | `UUID`         | FK  | Nullable | `Zone.zone_id`                      | Unclear from ERD | Unclear from ERD | Vùng giám sát liên quan. Tham chiếu Zone.zone_id.                                            |
| `notification_type`         | `varchar(100)` | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | SOS, DELIVERY_COMPLETED, NAVIGATION_FAILED... theo registry.                                 |
| `severity`                  | `enum`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | INFO, WARNING, CRITICAL.                                                                     |
| `message`                   | `text`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Thông điệp đã resolve.                                                                       |
| `data_json`                 | `json`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Payload theo notification_type, có schema.                                                   |
| `requested_recipients_json` | `json`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Nhóm/người nhận lúc yêu cầu, dùng audit.                                                     |
| `require_ack`               | `bool`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Có yêu cầu nhân viên tiếp nhận.                                                              |
| `ack_policy`                | `enum`         | —   | Not Null | — (không có FK)                     | Unclear from ERD | `ANY`            | ANY, ALL; mặc định ANY.                                                                      |
| `operation_key`             | `varchar(150)` | UK  | Not Null | — (không có FK)                     | Có — UK một cột  | Unclear from ERD | Khóa chống tạo trùng; bước NOTIFY dùng job_step_id.                                          |
| `created_at`                | `datetime UTC` | —   | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                                               |
| `expires_at`                | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Hết thời gian giao/ACK.                                                                      |

### Relationships

- **R047 — EmergencyIncident 1:N Alert** (chiều ngược: N:1). Một EmergencyIncident → 0..N Alert; một Alert → 0..1 EmergencyIncident. FK: `Alert.incident_id → EmergencyIncident.incident_id`. Đường nguồn: `fk-046`.
- **R048 — DetectionEvent 1:N Alert** (chiều ngược: N:1). Một DetectionEvent → 0..N Alert; một Alert → 0..1 DetectionEvent. FK: `Alert.detection_event_id → DetectionEvent.detection_event_id`. Đường nguồn: `fk-047`.
- **R049 — JobStep 1:N Alert** (chiều ngược: N:1). Một JobStep → 0..N Alert; một Alert → 0..1 JobStep. FK: `Alert.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-048`.
- **R050 — ServiceRequest 1:N Alert** (chiều ngược: N:1). Một ServiceRequest → 0..N Alert; một Alert → 0..1 ServiceRequest. FK: `Alert.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-049`.
- **R051 — Zone 1:N Alert** (chiều ngược: N:1). Một Zone → 0..N Alert; một Alert → 0..1 Zone. FK: `Alert.zone_id → Zone.zone_id`. Đường nguồn: `fk-050`.
- **R052 — Alert 1:N AlertRecipient** (chiều ngược: N:1). Một Alert → 0..N AlertRecipient; một AlertRecipient → 1 Alert. FK: `AlertRecipient.alert_id → Alert.alert_id`. Đường nguồn: `fk-051`.

### Constraints and source notes

- Khóa chính: `PK(alert_id)`.
- Alert không bắt buộc có incident; hỗ trợ lỗi điều hướng và cảnh báo độc lập.
- require_ack=true yêu cầu expires_at; mục tiêu ACK phải được resolve thành AlertRecipient.
- delivered/acked tổng hợp từ AlertRecipient theo ack_policy; khác EVENT_ACK của giao thức.

Nguồn: S0, S3

---

## AlertRecipient

### Purpose

Người nhận và kết quả giao/ACK của từng kênh.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                        | Type           | Key | Nullable | References           | Unique                                                                 | Default          | Description                                                         |
| ---------------------------- | -------------- | --- | -------- | -------------------- | ---------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------------- |
| `alert_recipient_id`         | `UUID`         | PK  | Not Null | — (không có FK)      | Có — PK một cột                                                        | Unclear from ERD | ID đích nhận.                                                       |
| `alert_id`                   | `UUID`         | FK  | Not Null | `Alert.alert_id`     | Thuộc UQ(alert_id, account_id, channel); xem điều kiện tại Constraints | Unclear from ERD | Thông báo. Tham chiếu Alert.alert_id.                               |
| `account_id`                 | `UUID`         | FK  | Not Null | `Account.account_id` | Thuộc UQ(alert_id, account_id, channel); xem điều kiện tại Constraints | Unclear from ERD | Người nhận đã resolve và kiểm quyền. Tham chiếu Account.account_id. |
| `channel`                    | `enum`         | —   | Not Null | — (không có FK)      | Thuộc UQ(alert_id, account_id, channel); xem điều kiện tại Constraints | Unclear from ERD | WEB, MOBILE_PUSH, EMAIL; chỉ kênh đã triển khai.                    |
| `recipient_address_snapshot` | `varchar(255)` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Địa chỉ đích khi gửi; không chứa secret.                            |
| `resolved_role_snapshot`     | `varchar(64)`  | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Vai trò làm căn cứ resolve.                                         |
| `delivery_status`            | `enum`         | —   | Not Null | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | PENDING, DELIVERED, FAILED.                                         |
| `sent_at`                    | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Thời điểm gửi.                                                      |
| `delivered_at`               | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Thời điểm xác nhận giao.                                            |
| `acknowledged_at`            | `datetime UTC` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Nhân viên này tiếp nhận.                                            |
| `error_code`                 | `varchar(100)` | —   | Nullable | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Lỗi gửi.                                                            |
| `created_at`                 | `datetime UTC` | —   | Not Null | — (không có FK)      | Unclear from ERD                                                       | Unclear from ERD | Thời điểm tạo.                                                      |

### Relationships

- **R052 — Alert 1:N AlertRecipient** (chiều ngược: N:1). Một Alert → 0..N AlertRecipient; một AlertRecipient → 1 Alert. FK: `AlertRecipient.alert_id → Alert.alert_id`. Đường nguồn: `fk-051`.
- **R053 — Account 1:N AlertRecipient** (chiều ngược: N:1). Một Account → 0..N AlertRecipient; một AlertRecipient → 1 Account. FK: `AlertRecipient.account_id → Account.account_id`. Đường nguồn: `fk-052`.

### Constraints and source notes

- Khóa chính: `PK(alert_recipient_id)`.
- UQ(alert_id, account_id, channel).
- ACK chỉ chấp nhận từ đúng người nhận hoặc policy ủy quyền được audit.

Nguồn: S3 + chuẩn hóa đề xuất

---

## CrowdDensityRecord

### Purpose

Lượt đếm người tại Zone; chưa tự suy mật độ người/m².

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                     | Type           | Key | Nullable | References             | Unique           | Default          | Description                                                |
| ------------------------- | -------------- | --- | -------- | ---------------------- | ---------------- | ---------------- | ---------------------------------------------------------- |
| `crowd_density_record_id` | `UUID`         | PK  | Not Null | — (không có FK)        | Có — PK một cột  | Unclear from ERD | ID lượt đo.                                                |
| `robot_id`                | `UUID`         | FK  | Not Null | `Robot.robot_id`       | Unclear from ERD | Unclear from ERD | Robot quan sát. Tham chiếu Robot.robot_id.                 |
| `zone_id`                 | `UUID`         | FK  | Not Null | `Zone.zone_id`         | Unclear from ERD | Unclear from ERD | Vùng quan sát. Tham chiếu Zone.zone_id.                    |
| `checkpoint_id`           | `UUID`         | FK  | Nullable | `Endpoint.endpoint_id` | Unclear from ERD | Unclear from ERD | Điểm robot đứng quan sát. Tham chiếu Endpoint.endpoint_id. |
| `job_step_id`             | `UUID`         | FK  | Nullable | `JobStep.job_step_id`  | Unclear from ERD | Unclear from ERD | Bước sensing tạo mẫu. Tham chiếu JobStep.job_step_id.      |
| `person_count`            | `int`          | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Số người >= 0.                                             |
| `density_level`           | `enum`         | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | LOW, MEDIUM, HIGH; theo policy ngưỡng đã cấu hình.         |
| `confidence_score`        | `decimal(5,4)` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Độ tin cậy [0,1].                                          |
| `model_version`           | `varchar(100)` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Phiên bản detector.                                        |
| `metrics_json`            | `json`         | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Kết quả sensing gốc theo schema.                           |
| `evidence_ref`            | `text`         | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Minh chứng.                                                |
| `captured_at`             | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Thời điểm khung hình/lượt đo.                              |
| `recorded_at`             | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Thời điểm lưu.                                             |

### Relationships

- **R054 — Robot 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Robot → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Robot. FK: `CrowdDensityRecord.robot_id → Robot.robot_id`. Đường nguồn: `fk-053`.
- **R055 — Zone 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Zone → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Zone. FK: `CrowdDensityRecord.zone_id → Zone.zone_id`. Đường nguồn: `fk-054`.
- **R056 — Endpoint 1:N CrowdDensityRecord** (chiều ngược: N:1). Một Endpoint → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 Endpoint. FK: `CrowdDensityRecord.checkpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-055`.
- **R057 — JobStep 1:N CrowdDensityRecord** (chiều ngược: N:1). Một JobStep → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 JobStep. FK: `CrowdDensityRecord.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-056`.

### Constraints and source notes

- Khóa chính: `PK(crowd_density_record_id)`.
- Checkpoint và Zone cùng Map; không suy danh tính người từ đếm người.
- Không gọi person_count là người/m² khi chưa có diện tích hiệu chuẩn.

Nguồn: S0, S3

---

## HeatmapRecord

### Purpose

Kết quả tổng hợp theo không gian và thời gian.

**Nhóm:** 04 · Robot, giám sát và cảnh báo

### Fields

| Field                 | Type            | Key | Nullable | References      | Unique                                                                                               | Default          | Description                                             |
| --------------------- | --------------- | --- | -------- | --------------- | ---------------------------------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------- |
| `heatmap_record_id`   | `UUID`          | PK  | Not Null | — (không có FK) | Có — PK một cột                                                                                      | Unclear from ERD | ID heatmap.                                             |
| `map_id`              | `UUID`          | FK  | Not Null | `Map.map_id`    | Thuộc UQ(map_id, heatmap_type, period_start, period_end, cell_size_m); xem điều kiện tại Constraints | Unclear from ERD | Map nền. Tham chiếu Map.map_id.                         |
| `heatmap_type`        | `varchar(64)`   | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, heatmap_type, period_start, period_end, cell_size_m); xem điều kiện tại Constraints | Unclear from ERD | CROWD_COUNT, VISIT_FREQUENCY... theo định nghĩa chỉ số. |
| `period_start`        | `datetime UTC`  | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, heatmap_type, period_start, period_end, cell_size_m); xem điều kiện tại Constraints | Unclear from ERD | Bắt đầu cửa sổ dữ liệu.                                 |
| `period_end`          | `datetime UTC`  | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, heatmap_type, period_start, period_end, cell_size_m); xem điều kiện tại Constraints | Unclear from ERD | Kết thúc cửa sổ dữ liệu.                                |
| `cell_size_m`         | `decimal(10,4)` | —   | Not Null | — (không có FK) | Thuộc UQ(map_id, heatmap_type, period_start, period_end, cell_size_m); xem điều kiện tại Constraints | Unclear from ERD | Kích thước ô lưới > 0.                                  |
| `grid_data_json`      | `json`          | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                     | Unclear from ERD | Ô lưới và giá trị; có schema/version.                   |
| `image_uri`           | `text`          | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                     | Unclear from ERD | Ảnh dựng sẵn nếu có.                                    |
| `max_value`           | `decimal(14,4)` | —   | Nullable | — (không có FK) | Unclear from ERD                                                                                     | Unclear from ERD | Giá trị cực đại trong grid.                             |
| `source_record_count` | `int`           | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                     | Unclear from ERD | Số bản ghi dùng tổng hợp >= 0.                          |
| `generated_at`        | `datetime UTC`  | —   | Not Null | — (không có FK) | Unclear from ERD                                                                                     | Unclear from ERD | Thời điểm tạo.                                          |

### Relationships

- **R058 — Map 1:N HeatmapRecord** (chiều ngược: N:1). Một Map → 0..N HeatmapRecord; một HeatmapRecord → 1 Map. FK: `HeatmapRecord.map_id → Map.map_id`. Đường nguồn: `fk-057`.

### Constraints and source notes

- Khóa chính: `PK(heatmap_record_id)`.
- CHECK period_start < period_end; UQ(map_id, heatmap_type, period_start, period_end, cell_size_m).

Nguồn: S0, S2

---

## ServiceRequest

### Purpose

Yêu cầu nghiệp vụ gốc; tách khỏi lần thực thi Job.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                     | Type           | Key | Nullable | References                      | Unique           | Default          | Description                                                                                                                               |
| ------------------------- | -------------- | --- | -------- | ------------------------------- | ---------------- | ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `service_request_id`      | `UUID`         | PK  | Not Null | — (không có FK)                 | Có — PK một cột  | Unclear from ERD | ID yêu cầu.                                                                                                                               |
| `requested_by_account_id` | `UUID`         | FK  | Nullable | `Account.account_id`            | Unclear from ERD | Unclear from ERD | Người tạo; NULL với guest/system. Tham chiếu Account.account_id.                                                                          |
| `requested_robot_id`      | `UUID`         | FK  | Nullable | `Robot.robot_id`                | Unclear from ERD | Unclear from ERD | Robot được yêu cầu, ví dụ sạc; chưa phải phân công job. Tham chiếu Robot.robot_id.                                                        |
| `related_incident_id`     | `UUID`         | FK  | Nullable | `EmergencyIncident.incident_id` | Unclear from ERD | Unclear from ERD | SOS/sự cố liên quan. Tham chiếu EmergencyIncident.incident_id.                                                                            |
| `request_code`            | `varchar(64)`  | UK  | Not Null | — (không có FK)                 | Có — UK một cột  | Unclear from ERD | Mã tra cứu yêu cầu.                                                                                                                       |
| `request_type`            | `enum`         | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | NAVIGATION, ESCORT, DELIVERY, INFORMATION, CROWD_MONITORING, SOS, FALL_RESPONSE, RESTRICTED_MONITORING, MANUAL_CHARGE, RECOVERY, STARTUP. |
| `source_type`             | `enum`         | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | STAFF_UI, ROBOT_UI, VOICE, SYSTEM, DETECTOR.                                                                                              |
| `priority`                | `int`          | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | [0,100]; số lớn ưu tiên hơn.                                                                                                              |
| `status`                  | `enum`         | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | DRAFT, SUBMITTED, QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED, RECOVERY_REQUIRED.                                                   |
| `request_note`            | `text`         | —   | Nullable | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Ghi chú chung.                                                                                                                            |
| `request_data_json`       | `json`         | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Dữ liệu riêng các loại không có bảng con; validate theo request_type.                                                                     |
| `idempotency_key`         | `varchar(150)` | UK  | Not Null | — (không có FK)                 | Có — UK một cột  | Unclear from ERD | Khóa chống gửi trùng của lần tạo request.                                                                                                 |
| `requested_at`            | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm tạo yêu cầu.                                                                                                                    |
| `scheduled_at`            | `datetime UTC` | —   | Nullable | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm mong muốn phục vụ.                                                                                                              |
| `cancelled_at`            | `datetime UTC` | —   | Nullable | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm hủy.                                                                                                                            |
| `completed_at`            | `datetime UTC` | —   | Nullable | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm hoàn thành thành công.                                                                                                          |
| `updated_at`              | `datetime UTC` | —   | Not Null | — (không có FK)                 | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                                                                                       |

### Relationships

- **R013 — ServiceRequest 1:N VoiceCommand** (chiều ngược: N:1). Một ServiceRequest → 0..N VoiceCommand; một VoiceCommand → 0..1 ServiceRequest. FK: `VoiceCommand.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-012`.
- **R050 — ServiceRequest 1:N Alert** (chiều ngược: N:1). Một ServiceRequest → 0..N Alert; một Alert → 0..1 ServiceRequest. FK: `Alert.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-049`.
- **R059 — Account 1:N ServiceRequest** (chiều ngược: N:1). Một Account → 0..N ServiceRequest; một ServiceRequest → 0..1 Account. FK: `ServiceRequest.requested_by_account_id → Account.account_id`. Đường nguồn: `fk-058`.
- **R060 — Robot 1:N ServiceRequest** (chiều ngược: N:1). Một Robot → 0..N ServiceRequest; một ServiceRequest → 0..1 Robot. FK: `ServiceRequest.requested_robot_id → Robot.robot_id`. Đường nguồn: `fk-059`.
- **R061 — EmergencyIncident 1:N ServiceRequest** (chiều ngược: N:1). Một EmergencyIncident → 0..N ServiceRequest; một ServiceRequest → 0..1 EmergencyIncident. FK: `ServiceRequest.related_incident_id → EmergencyIncident.incident_id`. Đường nguồn: `fk-060`.
- **R062 — ServiceRequest 1:1 NavigationRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 NavigationRequest; một NavigationRequest → 1 ServiceRequest. FK: `NavigationRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-061`.
- **R068 — ServiceRequest 1:1 EscortRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 EscortRequest; một EscortRequest → 1 ServiceRequest. FK: `EscortRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-067`.
- **R076 — ServiceRequest 1:1 DeliveryRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 DeliveryRequest; một DeliveryRequest → 1 ServiceRequest. FK: `DeliveryRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-075`.
- **R095 — ServiceRequest 1:N Job** (chiều ngược: N:1). Một ServiceRequest → 0..N Job; một Job → 0..1 ServiceRequest. FK: `Job.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-094`.

### Constraints and source notes

- Khóa chính: `PK(service_request_id)`.
- NAVIGATION/ESCORT/DELIVERY có đúng một bảng con tương ứng khi SUBMITTED; loại trừ hai bảng còn lại.
- Các loại khác có thể không có ba bảng con; không tạo FK 1-1 bắt buộc tất cả.
- Một request có nhiều Job theo thời gian; trạng thái request được tổng hợp theo nghiệp vụ.

Nguồn: S0, S1, S3

---

## NavigationRequest

### Purpose

Chọn một điểm hoặc một nhóm điểm để điều hướng.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                     | Type   | Key     | Nullable | References                          | Unique           | Default          | Description                                                                          |
| ------------------------- | ------ | ------- | -------- | ----------------------------------- | ---------------- | ---------------- | ------------------------------------------------------------------------------------ |
| `service_request_id`      | `UUID` | PK / FK | Not Null | `ServiceRequest.service_request_id` | Có — PK một cột  | Unclear from ERD | PK dùng chung với request gốc. Tham chiếu ServiceRequest.service_request_id.         |
| `map_id`                  | `UUID` | FK      | Not Null | `Map.map_id`                        | Unclear from ERD | Unclear from ERD | Map của yêu cầu. Tham chiếu Map.map_id.                                              |
| `room_id`                 | `UUID` | FK      | Nullable | `Room.room_id`                      | Unclear from ERD | Unclear from ERD | Phòng đích trên UI nếu có. Tham chiếu Room.room_id.                                  |
| `destination_endpoint_id` | `UUID` | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Đích cố định người dùng chọn. Tham chiếu Endpoint.endpoint_id.                       |
| `destination_group_id`    | `UUID` | FK      | Nullable | `EndpointGroup.endpoint_group_id`   | Unclear from ERD | Unclear from ERD | Nhóm đích; hệ thống chọn điểm phù hợp. Tham chiếu EndpointGroup.endpoint_group_id.   |
| `resolved_endpoint_id`    | `UUID` | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Điểm đã chốt trước dispatch; không thay ý định gốc. Tham chiếu Endpoint.endpoint_id. |
| `target_mode`             | `enum` | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | ENDPOINT hoặc GROUP.                                                                 |
| `navigation_mode`         | `enum` | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | NORMAL hoặc ESCORT theo MOVE.                                                        |
| `accessibility_need`      | `text` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Nhu cầu tiếp cận.                                                                    |
| `passenger_count`         | `int`  | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Số người được hướng dẫn >= 1; không chở người.                                       |
| `arrival_instruction`     | `text` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Hướng dẫn khi tới nơi.                                                               |

### Relationships

- **R062 — ServiceRequest 1:1 NavigationRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 NavigationRequest; một NavigationRequest → 1 ServiceRequest. FK: `NavigationRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-061`.
- **R063 — Map 1:N NavigationRequest** (chiều ngược: N:1). Một Map → 0..N NavigationRequest; một NavigationRequest → 1 Map. FK: `NavigationRequest.map_id → Map.map_id`. Đường nguồn: `fk-062`.
- **R064 — Room 1:N NavigationRequest** (chiều ngược: N:1). Một Room → 0..N NavigationRequest; một NavigationRequest → 0..1 Room. FK: `NavigationRequest.room_id → Room.room_id`. Đường nguồn: `fk-063`.
- **R065 — Endpoint 1:N NavigationRequest** (chiều ngược: N:1). Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. FK: `NavigationRequest.destination_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-064`.
- **R066 — EndpointGroup 1:N NavigationRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N NavigationRequest; một NavigationRequest → 0..1 EndpointGroup. FK: `NavigationRequest.destination_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-065`.
- **R067 — Endpoint 1:N NavigationRequest** (chiều ngược: N:1). Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. FK: `NavigationRequest.resolved_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-066`.

### Constraints and source notes

- Khóa chính: `PK(service_request_id)`.
- XOR destination_endpoint_id / destination_group_id theo target_mode.
- Mọi endpoint/group cùng map_id; room_id phải khớp endpoint nếu được chọn theo phòng.
- ENDPOINT: resolved bằng đích đã chọn. GROUP: resolved phải là thành viên đang active tại lúc resolve.

Nguồn: S0, S1, S4

---

## EscortRequest

### Purpose

Dẫn người đến đích; chưa có theo dõi người đi sau.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                      | Type           | Key     | Nullable | References                          | Unique           | Default          | Description                                                         |
| -------------------------- | -------------- | ------- | -------- | ----------------------------------- | ---------------- | ---------------- | ------------------------------------------------------------------- |
| `service_request_id`       | `UUID`         | PK / FK | Not Null | `ServiceRequest.service_request_id` | Có — PK một cột  | Unclear from ERD | PK/FK dùng chung. Tham chiếu ServiceRequest.service_request_id.     |
| `map_id`                   | `UUID`         | FK      | Not Null | `Map.map_id`                        | Unclear from ERD | Unclear from ERD | Map của yêu cầu. Tham chiếu Map.map_id.                             |
| `origin_room_id`           | `UUID`         | FK      | Nullable | `Room.room_id`                      | Unclear from ERD | Unclear from ERD | Phòng xuất phát nếu có. Tham chiếu Room.room_id.                    |
| `destination_room_id`      | `UUID`         | FK      | Nullable | `Room.room_id`                      | Unclear from ERD | Unclear from ERD | Phòng đích nếu có. Tham chiếu Room.room_id.                         |
| `origin_endpoint_id`       | `UUID`         | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Điểm đón nếu nghiệp vụ xác định. Tham chiếu Endpoint.endpoint_id.   |
| `destination_endpoint_id`  | `UUID`         | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Đích cố định. Tham chiếu Endpoint.endpoint_id.                      |
| `destination_group_id`     | `UUID`         | FK      | Nullable | `EndpointGroup.endpoint_group_id`   | Unclear from ERD | Unclear from ERD | Nhóm đích. Tham chiếu EndpointGroup.endpoint_group_id.              |
| `resolved_endpoint_id`     | `UUID`         | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Đích đã chọn từ ý định. Tham chiếu Endpoint.endpoint_id.            |
| `target_mode`              | `enum`         | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | ENDPOINT, GROUP.                                                    |
| `patient_reference`        | `varchar(100)` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Tham chiếu ngoài hệ thống; không FK tới bảng Patient không tồn tại. |
| `escort_mode`              | `enum`         | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | GUIDE_TO_TARGET cho phạm vi phần cứng hiện tại.                     |
| `walking_speed_mps`        | `decimal(5,3)` | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | > 0, <= 0.3 và không vượt trần cục bộ.                              |
| `confirmation_timeout_sec` | `int`          | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Giới hạn chờ xác nhận điểm đến > 0.                                 |
| `escort_note`              | `text`         | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Ghi chú hộ tống.                                                    |

### Relationships

- **R068 — ServiceRequest 1:1 EscortRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 EscortRequest; một EscortRequest → 1 ServiceRequest. FK: `EscortRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-067`.
- **R069 — Map 1:N EscortRequest** (chiều ngược: N:1). Một Map → 0..N EscortRequest; một EscortRequest → 1 Map. FK: `EscortRequest.map_id → Map.map_id`. Đường nguồn: `fk-068`.
- **R070 — Room 1:N EscortRequest** (chiều ngược: N:1). Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. FK: `EscortRequest.origin_room_id → Room.room_id`. Đường nguồn: `fk-069`.
- **R071 — Room 1:N EscortRequest** (chiều ngược: N:1). Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. FK: `EscortRequest.destination_room_id → Room.room_id`. Đường nguồn: `fk-070`.
- **R072 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.origin_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-071`.
- **R073 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.destination_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-072`.
- **R074 — EndpointGroup 1:N EscortRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N EscortRequest; một EscortRequest → 0..1 EndpointGroup. FK: `EscortRequest.destination_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-073`.
- **R075 — Endpoint 1:N EscortRequest** (chiều ngược: N:1). Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. FK: `EscortRequest.resolved_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-074`.

### Constraints and source notes

- Khóa chính: `PK(service_request_id)`.
- Quy tắc XOR và cùng Map như NavigationRequest; nguồn/đích Room phải phù hợp Endpoint.
- Không bật PATIENT_FOLLOW, auto-return hoặc tự docking chỉ bằng thêm field.

Nguồn: S0, S1, S3, S4

---

## DeliveryRequest

### Purpose

Giao nhận vật phẩm: điểm lấy, điểm giao và xác nhận hai đầu.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                           | Type           | Key     | Nullable | References                          | Unique           | Default          | Description                                                                                               |
| ------------------------------- | -------------- | ------- | -------- | ----------------------------------- | ---------------- | ---------------- | --------------------------------------------------------------------------------------------------------- |
| `service_request_id`            | `UUID`         | PK / FK | Not Null | `ServiceRequest.service_request_id` | Có — PK một cột  | Unclear from ERD | PK/FK dùng chung; contract delivery_id tham chiếu khóa này. Tham chiếu ServiceRequest.service_request_id. |
| `map_id`                        | `UUID`         | FK      | Not Null | `Map.map_id`                        | Unclear from ERD | Unclear from ERD | Map của lượt giao. Tham chiếu Map.map_id.                                                                 |
| `pickup_room_id`                | `UUID`         | FK      | Nullable | `Room.room_id`                      | Unclear from ERD | Unclear from ERD | Phòng lấy nếu có. Tham chiếu Room.room_id.                                                                |
| `delivery_room_id`              | `UUID`         | FK      | Nullable | `Room.room_id`                      | Unclear from ERD | Unclear from ERD | Phòng giao nếu có. Tham chiếu Room.room_id.                                                               |
| `pickup_endpoint_id`            | `UUID`         | FK      | Not Null | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Điểm lấy cụ thể. Tham chiếu Endpoint.endpoint_id.                                                         |
| `delivery_endpoint_id`          | `UUID`         | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Điểm giao cố định. Tham chiếu Endpoint.endpoint_id.                                                       |
| `delivery_group_id`             | `UUID`         | FK      | Nullable | `EndpointGroup.endpoint_group_id`   | Unclear from ERD | Unclear from ERD | Nhóm điểm giao nếu dùng chế độ nhóm. Tham chiếu EndpointGroup.endpoint_group_id.                          |
| `resolved_delivery_endpoint_id` | `UUID`         | FK      | Nullable | `Endpoint.endpoint_id`              | Unclear from ERD | Unclear from ERD | Điểm giao đã chốt. Tham chiếu Endpoint.endpoint_id.                                                       |
| `sender_account_id`             | `UUID`         | FK      | Nullable | `Account.account_id`                | Unclear from ERD | Unclear from ERD | Người gửi thực tế đã xác thực. Tham chiếu Account.account_id.                                             |
| `receiver_account_id`           | `UUID`         | FK      | Nullable | `Account.account_id`                | Unclear from ERD | Unclear from ERD | Người nhận thực tế đã xác thực. Tham chiếu Account.account_id.                                            |
| `target_mode`                   | `enum`         | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | ENDPOINT, GROUP.                                                                                          |
| `recipient_name`                | `varchar(160)` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Tên người nhận dự kiến.                                                                                   |
| `recipient_contact`             | `varchar(100)` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Liên hệ người nhận dự kiến.                                                                               |
| `requires_confirmation`         | `bool`         | —       | Not Null | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Luồng secure delivery hiện tại phải true.                                                                 |
| `temperature_requirement`       | `text`         | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Yêu cầu ghi chú; không phải số đo hay khả năng làm lạnh.                                                  |
| `delivery_deadline`             | `datetime UTC` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Hạn giao.                                                                                                 |
| `picked_up_at`                  | `datetime UTC` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Nhân viên xác nhận đã nhận hàng.                                                                          |
| `delivered_at`                  | `datetime UTC` | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Nhân viên xác nhận bàn giao.                                                                              |
| `delivery_note`                 | `text`         | —       | Nullable | — (không có FK)                     | Unclear from ERD | Unclear from ERD | Ghi chú.                                                                                                  |

### Relationships

- **R076 — ServiceRequest 1:1 DeliveryRequest** (chiều ngược: 1:1). Một ServiceRequest → 0..1 DeliveryRequest; một DeliveryRequest → 1 ServiceRequest. FK: `DeliveryRequest.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-075`.
- **R077 — Map 1:N DeliveryRequest** (chiều ngược: N:1). Một Map → 0..N DeliveryRequest; một DeliveryRequest → 1 Map. FK: `DeliveryRequest.map_id → Map.map_id`. Đường nguồn: `fk-076`.
- **R078 — Room 1:N DeliveryRequest** (chiều ngược: N:1). Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. FK: `DeliveryRequest.pickup_room_id → Room.room_id`. Đường nguồn: `fk-077`.
- **R079 — Room 1:N DeliveryRequest** (chiều ngược: N:1). Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. FK: `DeliveryRequest.delivery_room_id → Room.room_id`. Đường nguồn: `fk-078`.
- **R080 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 1 Endpoint. FK: `DeliveryRequest.pickup_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-079`.
- **R081 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. FK: `DeliveryRequest.delivery_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-080`.
- **R082 — EndpointGroup 1:N DeliveryRequest** (chiều ngược: N:1). Một EndpointGroup → 0..N DeliveryRequest; một DeliveryRequest → 0..1 EndpointGroup. FK: `DeliveryRequest.delivery_group_id → EndpointGroup.endpoint_group_id`. Đường nguồn: `fk-081`.
- **R083 — Endpoint 1:N DeliveryRequest** (chiều ngược: N:1). Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. FK: `DeliveryRequest.resolved_delivery_endpoint_id → Endpoint.endpoint_id`. Đường nguồn: `fk-082`.
- **R084 — Account 1:N DeliveryRequest** (chiều ngược: N:1). Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. FK: `DeliveryRequest.sender_account_id → Account.account_id`. Đường nguồn: `fk-083`.
- **R085 — Account 1:N DeliveryRequest** (chiều ngược: N:1). Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. FK: `DeliveryRequest.receiver_account_id → Account.account_id`. Đường nguồn: `fk-084`.
- **R086 — DeliveryRequest 1:N DeliveryItem** (chiều ngược: N:1). Một DeliveryRequest → 0..N DeliveryItem; một DeliveryItem → 1 DeliveryRequest. FK: `DeliveryItem.delivery_request_id → DeliveryRequest.service_request_id`. Đường nguồn: `fk-085`.

### Constraints and source notes

- Khóa chính: `PK(service_request_id)`.
- XOR delivery_endpoint_id / delivery_group_id; resolved theo chế độ chọn.
- Cùng Map, room tương ứng endpoint; cần >= 1 DeliveryItem trước SUBMITTED/dispatch.
- Không coi servo published=true là đã khóa vật lý; xác nhận nhân viên lưu trong JobStep/JobEvent.

Nguồn: S0, S1, S3, S4

---

## DeliveryItem

### Purpose

Dòng vật phẩm dự kiến của một yêu cầu giao.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                  | Type           | Key | Nullable | References                           | Unique                                                                  | Default          | Description                                                                                          |
| ---------------------- | -------------- | --- | -------- | ------------------------------------ | ----------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------------------------- |
| `delivery_item_id`     | `UUID`         | PK  | Not Null | — (không có FK)                      | Có — PK một cột                                                         | Unclear from ERD | ID dòng vật phẩm.                                                                                    |
| `delivery_request_id`  | `UUID`         | FK  | Not Null | `DeliveryRequest.service_request_id` | Thuộc UQ(delivery_request_id, item_code); xem điều kiện tại Constraints | Unclear from ERD | Tham chiếu PK service_request_id của DeliveryRequest. Tham chiếu DeliveryRequest.service_request_id. |
| `item_code`            | `varchar(80)`  | —   | Not Null | — (không có FK)                      | Thuộc UQ(delivery_request_id, item_code); xem điều kiện tại Constraints | Unclear from ERD | Mã vật phẩm, bắt buộc theo HANDLE_PAYLOAD.                                                           |
| `item_name`            | `varchar(160)` | —   | Not Null | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Tên vật phẩm.                                                                                        |
| `item_category`        | `varchar(64)`  | —   | Not Null | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | DOCUMENT, SAMPLE, MEDICATION, SUPPLY... theo danh mục.                                               |
| `quantity`             | `int`          | —   | Not Null | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Số nguyên >= 1.                                                                                      |
| `unit`                 | `varchar(32)`  | —   | Not Null | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Đơn vị tính.                                                                                         |
| `is_sensitive`         | `bool`         | —   | Not Null | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Yêu cầu xử lý nhạy cảm.                                                                              |
| `handling_instruction` | `text`         | —   | Nullable | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Hướng dẫn xử lý.                                                                                     |
| `item_note`            | `text`         | —   | Nullable | — (không có FK)                      | Unclear from ERD                                                        | Unclear from ERD | Ghi chú.                                                                                             |

### Relationships

- **R086 — DeliveryRequest 1:N DeliveryItem** (chiều ngược: N:1). Một DeliveryRequest → 0..N DeliveryItem; một DeliveryItem → 1 DeliveryRequest. FK: `DeliveryItem.delivery_request_id → DeliveryRequest.service_request_id`. Đường nguồn: `fk-085`.

### Constraints and source notes

- Khóa chính: `PK(delivery_item_id)`.
- UQ(delivery_request_id, item_code); gộp số lượng cùng mã trong một yêu cầu.
- Snapshot danh sách vào Job.input_json; xác nhận thực tế lưu result_json của HANDLE_PAYLOAD.

Nguồn: S0, S1, S3

---

## AuthVerification

### Purpose

Grant xác thực có phạm vi và hạn dùng cho job.

**Nhóm:** 05 · Yêu cầu dịch vụ và xác thực

### Fields

| Field                 | Type           | Key     | Nullable | References            | Unique           | Default          | Description                                                                         |
| --------------------- | -------------- | ------- | -------- | --------------------- | ---------------- | ---------------- | ----------------------------------------------------------------------------------- |
| `verification_id`     | `UUID`         | PK      | Not Null | — (không có FK)       | Có — PK một cột  | Unclear from ERD | ID grant trả trong AUTHENTICATE.                                                    |
| `job_id`              | `UUID`         | FK      | Not Null | `Job.job_id`          | Unclear from ERD | Unclear from ERD | Job được cấp grant. Tham chiếu Job.job_id.                                          |
| `auth_job_step_id`    | `UUID`         | FK / UK | Not Null | `JobStep.job_step_id` | Có — UK một cột  | Unclear from ERD | Bước AUTHENTICATE cấp grant; chống tạo grant trùng. Tham chiếu JobStep.job_step_id. |
| `actor_account_id`    | `UUID`         | FK      | Not Null | `Account.account_id`  | Unclear from ERD | Unclear from ERD | Nhân viên đã xác thực. Tham chiếu Account.account_id.                               |
| `used_by_job_step_id` | `UUID`         | FK / UK | Nullable | `JobStep.job_step_id` | Có — UK một cột  | Unclear from ERD | Bước UNLOCK sử dụng grant nếu dùng policy một lần. Tham chiếu JobStep.job_step_id.  |
| `method`              | `enum`         | —       | Not Null | — (không có FK)       | Unclear from ERD | Unclear from ERD | QR_CODE, STAFF_CONFIRMATION.                                                        |
| `purpose`             | `enum`         | —       | Not Null | — (không có FK)       | Unclear from ERD | Unclear from ERD | DELIVERY_SENDER, DELIVERY_RECEIVER.                                                 |
| `required_permission` | `varchar(80)`  | —       | Not Null | — (không có FK)       | Unclear from ERD | Unclear from ERD | DELIVERY_SEND hoặc DELIVERY_RECEIVE theo policy.                                    |
| `issued_at`           | `datetime UTC` | —       | Not Null | — (không có FK)       | Unclear from ERD | Unclear from ERD | Thời điểm cấp.                                                                      |
| `valid_until`         | `datetime UTC` | —       | Not Null | — (không có FK)       | Unclear from ERD | Unclear from ERD | Hạn dùng grant.                                                                     |
| `revoked_at`          | `datetime UTC` | —       | Nullable | — (không có FK)       | Unclear from ERD | Unclear from ERD | Thời điểm thu hồi.                                                                  |

### Relationships

- **R087 — Job 1:N AuthVerification** (chiều ngược: N:1). Một Job → 0..N AuthVerification; một AuthVerification → 1 Job. FK: `AuthVerification.job_id → Job.job_id`. Đường nguồn: `fk-086`.
- **R088 — JobStep 1:1 AuthVerification** (chiều ngược: 1:1). Một JobStep → 0..1 AuthVerification; một AuthVerification → 1 JobStep. FK: `AuthVerification.auth_job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-087`.
- **R089 — Account 1:N AuthVerification** (chiều ngược: N:1). Một Account → 0..N AuthVerification; một AuthVerification → 1 Account. FK: `AuthVerification.actor_account_id → Account.account_id`. Đường nguồn: `fk-088`.
- **R090 — JobStep 1:1 AuthVerification** (chiều ngược: 1:1). Một JobStep → 0..1 AuthVerification; một AuthVerification → 0..1 JobStep. FK: `AuthVerification.used_by_job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-089`.

### Constraints and source notes

- Khóa chính: `PK(verification_id)`.
- Các JobStep phải cùng job_id; auth step phải đúng handler AUTHENTICATE.
- UNLOCK kiểm job, actor, purpose, hạn dùng; không lưu QR secret trong grant.
- auth_job_step_id là khóa idempotency; không tạo actor giả khi xác thực thất bại.

Nguồn: S3 + chuẩn hóa đề xuất

---

## Schedule

### Purpose

Lịch sinh Job theo Workflow đã publish.

**Nhóm:** 06 · Điều phối và lịch sử thực thi

### Fields

| Field                   | Type           | Key | Nullable | References             | Unique           | Default          | Description                                                                  |
| ----------------------- | -------------- | --- | -------- | ---------------------- | ---------------- | ---------------- | ---------------------------------------------------------------------------- |
| `schedule_id`           | `UUID`         | PK  | Not Null | — (không có FK)        | Có — PK một cột  | Unclear from ERD | ID lịch.                                                                     |
| `workflow_id`           | `UUID`         | FK  | Not Null | `Workflow.workflow_id` | Unclear from ERD | Unclear from ERD | Phiên bản Workflow sẽ chạy. Tham chiếu Workflow.workflow_id.                 |
| `robot_id`              | `UUID`         | FK  | Nullable | `Robot.robot_id`       | Unclear from ERD | Unclear from ERD | Robot cố định; NULL nếu scheduler lựa chọn. Tham chiếu Robot.robot_id.       |
| `created_by_account_id` | `UUID`         | FK  | Nullable | `Account.account_id`   | Unclear from ERD | Unclear from ERD | Người cấu hình; NULL với system. Tham chiếu Account.account_id.              |
| `schedule_code`         | `varchar(64)`  | UK  | Not Null | — (không có FK)        | Có — UK một cột  | Unclear from ERD | Mã lịch.                                                                     |
| `schedule_name`         | `varchar(160)` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Tên lịch.                                                                    |
| `schedule_type`         | `enum`         | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | ONCE, INTERVAL, CRON.                                                        |
| `timezone`              | `varchar(64)`  | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | IANA timezone, ví dụ Asia/Ho_Chi_Minh.                                       |
| `starts_at`             | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Thời điểm bắt đầu hiệu lực.                                                  |
| `ends_at`               | `datetime UTC` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Hết hiệu lực.                                                                |
| `interval_sec`          | `int`          | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Khoảng lặp > 0 cho INTERVAL.                                                 |
| `cron_expression`       | `varchar(120)` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Biểu thức cron đã kiểm cho CRON.                                             |
| `input_template_json`   | `json`         | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Input mẫu; resolve điểm/nhóm trước khi tạo job.                              |
| `priority`              | `int`          | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | [0,100]; số lớn ưu tiên hơn.                                                 |
| `misfire_policy`        | `enum`         | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | SKIP, RUN_ONCE; tránh tự phát lại tác vụ vật lý hàng loạt.                   |
| `status`                | `enum`         | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | ACTIVE, PAUSED, RETIRED; đây là lịch, không phải trạng thái Pause của robot. |
| `next_run_at`           | `datetime UTC` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Lần kích hoạt tiếp theo.                                                     |
| `last_run_at`           | `datetime UTC` | —   | Nullable | — (không có FK)        | Unclear from ERD | Unclear from ERD | Lần kích hoạt gần nhất.                                                      |
| `created_at`            | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Thời điểm tạo.                                                               |
| `updated_at`            | `datetime UTC` | —   | Not Null | — (không có FK)        | Unclear from ERD | Unclear from ERD | Thời điểm cập nhật.                                                          |

### Relationships

- **R091 — Workflow 1:N Schedule** (chiều ngược: N:1). Một Workflow → 0..N Schedule; một Schedule → 1 Workflow. FK: `Schedule.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-090`.
- **R092 — Robot 1:N Schedule** (chiều ngược: N:1). Một Robot → 0..N Schedule; một Schedule → 0..1 Robot. FK: `Schedule.robot_id → Robot.robot_id`. Đường nguồn: `fk-091`.
- **R093 — Account 1:N Schedule** (chiều ngược: N:1). Một Account → 0..N Schedule; một Schedule → 0..1 Account. FK: `Schedule.created_by_account_id → Account.account_id`. Đường nguồn: `fk-092`.
- **R096 — Schedule 1:N Job** (chiều ngược: N:1). Một Schedule → 0..N Job; một Job → 0..1 Schedule. FK: `Job.schedule_id → Schedule.schedule_id`. Đường nguồn: `fk-095`.

### Constraints and source notes

- Khóa chính: `PK(schedule_id)`.
- CHECK cấu hình thời gian theo schedule_type; workflow PUBLISHED và accepts_new_jobs.
- Job có schedule_fire_at + schedule_item_key để chống scheduler tạo trùng.

Nguồn: S1, S5 + thiết kế đề xuất

---

## Job

### Purpose

Một lần thực thi; input bất biến, graph/version/hash đã chốt.

**Nhóm:** 06 · Điều phối và lịch sử thực thi

### Fields

| Field                            | Type           | Key | Nullable | References                          | Unique                                                                                    | Default          | Description                                                                              |
| -------------------------------- | -------------- | --- | -------- | ----------------------------------- | ----------------------------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------------- |
| `job_id`                         | `UUID`         | PK  | Not Null | — (không có FK)                     | Có — PK một cột                                                                           | Unclear from ERD | ID lần chạy.                                                                             |
| `workflow_id`                    | `UUID`         | FK  | Not Null | `Workflow.workflow_id`              | Unclear from ERD                                                                          | Unclear from ERD | Đúng phiên bản Workflow đã chọn. Tham chiếu Workflow.workflow_id.                        |
| `service_request_id`             | `UUID`         | FK  | Nullable | `ServiceRequest.service_request_id` | Unclear from ERD                                                                          | Unclear from ERD | Yêu cầu gốc; NULL với lịch/system độc lập. Tham chiếu ServiceRequest.service_request_id. |
| `schedule_id`                    | `UUID`         | FK  | Nullable | `Schedule.schedule_id`              | Thuộc UQ(schedule_id, schedule_fire_at, schedule_item_key); xem điều kiện tại Constraints | Unclear from ERD | Lịch tạo job; NULL với job thủ công. Tham chiếu Schedule.schedule_id.                    |
| `robot_id`                       | `UUID`         | FK  | Nullable | `Robot.robot_id`                    | Unclear from ERD                                                                          | Unclear from ERD | Robot thực thi; NULL khi chưa phân công. Tham chiếu Robot.robot_id.                      |
| `map_id`                         | `UUID`         | FK  | Nullable | `Map.map_id`                        | Unclear from ERD                                                                          | Unclear from ERD | Map của job; bắt buộc khi có MOVE. Tham chiếu Map.map_id.                                |
| `recovery_of_job_id`             | `UUID`         | FK  | Nullable | `Job.job_id`                        | Unclear from ERD                                                                          | Unclear from ERD | Job cũ được đối soát/phục hồi; không phải gọi workflow con. Tham chiếu Job.job_id.       |
| `cancel_requested_by_account_id` | `UUID`         | FK  | Nullable | `Account.account_id`                | Unclear from ERD                                                                          | Unclear from ERD | Người yêu cầu hủy; NULL với hệ thống. Tham chiếu Account.account_id.                     |
| `reconciled_by_account_id`       | `UUID`         | FK  | Nullable | `Account.account_id`                | Unclear from ERD                                                                          | Unclear from ERD | Người đối soát robot/cửa/hàng sau RECOVERY_REQUIRED. Tham chiếu Account.account_id.      |
| `job_code`                       | `varchar(64)`  | UK  | Not Null | — (không có FK)                     | Có — UK một cột                                                                           | Unclear from ERD | Mã theo dõi.                                                                             |
| `status`                         | `enum`         | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | QUEUED, ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED, RECOVERY_REQUIRED.              |
| `priority`                       | `int`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | [0,100]; số lớn ưu tiên hơn.                                                             |
| `input_json`                     | `json`         | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | JobParam đã resolve/validate; snapshot bất biến khi dispatch.                            |
| `context_json`                   | `json`         | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Biến /vars của lần chạy.                                                                 |
| `result_json`                    | `json`         | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Kết quả cuối hợp lệ; không dựng kết quả thành công khi thiếu dữ liệu.                    |
| `workflow_hash`                  | `char(64)`     | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Hash Workflow đã chốt.                                                                   |
| `protocol_version`               | `int`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | = 1 cho giao thức hiện tại.                                                              |
| `schema_version`                 | `int`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | = 2 cho I/O step hiện tại.                                                               |
| `graph_version`                  | `int`          | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | = 3 cho artifact graph hiện tại.                                                         |
| `schedule_fire_at`               | `datetime UTC` | —   | Nullable | — (không có FK)                     | Thuộc UQ(schedule_id, schedule_fire_at, schedule_item_key); xem điều kiện tại Constraints | Unclear from ERD | Lần kích hoạt lịch tạo job.                                                              |
| `schedule_item_key`              | `varchar(100)` | —   | Nullable | — (không có FK)                     | Thuộc UQ(schedule_id, schedule_fire_at, schedule_item_key); xem điều kiện tại Constraints | Unclear from ERD | Khóa checkpoint/đơn vị sinh job trong cùng lần kích hoạt.                                |
| `dispatch_command_id`            | `UUID`         | UK  | Nullable | — (không có FK)                     | Có — UK một cột                                                                           | Unclear from ERD | ID DISPATCH_JOB dùng chống phát lệnh trùng.                                              |
| `dispatch_expires_at`            | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Hạn nhận lệnh dispatch.                                                                  |
| `queued_at`                      | `datetime UTC` | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Vào hàng đợi.                                                                            |
| `scheduled_at`                   | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Thời điểm dự kiến chạy.                                                                  |
| `accepted_at`                    | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Pi xác nhận chấp nhận; chưa có nghĩa thành công.                                         |
| `started_at`                     | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Bắt đầu chạy.                                                                            |
| `cancel_command_id`              | `UUID`         | UK  | Nullable | — (không có FK)                     | Có — UK một cột                                                                           | Unclear from ERD | ID CANCEL_JOB nếu có.                                                                    |
| `cancel_requested_at`            | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Đã yêu cầu hủy, chưa chắc robot dừng xong.                                               |
| `ended_at`                       | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Kết thúc lần chạy kể cả FAILED/CANCELLED/RECOVERY_REQUIRED.                              |
| `error_code`                     | `varchar(100)` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Mã lỗi cuối.                                                                             |
| `failure_reason`                 | `text`         | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Mô tả lỗi.                                                                               |
| `reconciled_at`                  | `datetime UTC` | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Đã kiểm và giải phóng khóa vận hành sau sự cố.                                           |
| `reconciliation_note`            | `text`         | —   | Nullable | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Kết quả kiểm robot, cửa và hàng; không tự suy trạng thái vật lý.                         |
| `row_version`                    | `bigint`       | —   | Not Null | — (không có FK)                     | Unclear from ERD                                                                          | Unclear from ERD | Số phiên bản optimistic concurrency >= 1.                                                |

### Relationships

- **R034 — Job 1:N RobotStatusHistory** (chiều ngược: N:1). Một Job → 0..N RobotStatusHistory; một RobotStatusHistory → 0..1 Job. FK: `RobotStatusHistory.job_id → Job.job_id`. Đường nguồn: `fk-033`.
- **R037 — Job 1:N RobotLocationHistory** (chiều ngược: N:1). Một Job → 0..N RobotLocationHistory; một RobotLocationHistory → 0..1 Job. FK: `RobotLocationHistory.job_id → Job.job_id`. Đường nguồn: `fk-036`.
- **R087 — Job 1:N AuthVerification** (chiều ngược: N:1). Một Job → 0..N AuthVerification; một AuthVerification → 1 Job. FK: `AuthVerification.job_id → Job.job_id`. Đường nguồn: `fk-086`.
- **R094 — Workflow 1:N Job** (chiều ngược: N:1). Một Workflow → 0..N Job; một Job → 1 Workflow. FK: `Job.workflow_id → Workflow.workflow_id`. Đường nguồn: `fk-093`.
- **R095 — ServiceRequest 1:N Job** (chiều ngược: N:1). Một ServiceRequest → 0..N Job; một Job → 0..1 ServiceRequest. FK: `Job.service_request_id → ServiceRequest.service_request_id`. Đường nguồn: `fk-094`.
- **R096 — Schedule 1:N Job** (chiều ngược: N:1). Một Schedule → 0..N Job; một Job → 0..1 Schedule. FK: `Job.schedule_id → Schedule.schedule_id`. Đường nguồn: `fk-095`.
- **R097 — Robot 1:N Job** (chiều ngược: N:1). Một Robot → 0..N Job; một Job → 0..1 Robot. FK: `Job.robot_id → Robot.robot_id`. Đường nguồn: `fk-096`.
- **R098 — Map 1:N Job** (chiều ngược: N:1). Một Map → 0..N Job; một Job → 0..1 Map. FK: `Job.map_id → Map.map_id`. Đường nguồn: `fk-097`.
- **R099 — Job 1:N Job** (chiều ngược: N:1). Một Job → 0..N Job; một Job → 0..1 Job. FK: `Job.recovery_of_job_id → Job.job_id`. Đường nguồn: `fk-098`.
- **R100 — Account 1:N Job** (chiều ngược: N:1). Một Account → 0..N Job; một Job → 0..1 Account. FK: `Job.cancel_requested_by_account_id → Account.account_id`. Đường nguồn: `fk-099`.
- **R101 — Account 1:N Job** (chiều ngược: N:1). Một Account → 0..N Job; một Job → 0..1 Account. FK: `Job.reconciled_by_account_id → Account.account_id`. Đường nguồn: `fk-100`.
- **R102 — Job 1:N JobTask** (chiều ngược: N:1). Một Job → 0..N JobTask; một JobTask → 1 Job. FK: `JobTask.job_id → Job.job_id`. Đường nguồn: `fk-101`.
- **R106 — Job 1:N JobEvent** (chiều ngược: N:1). Một Job → 0..N JobEvent; một JobEvent → 1 Job. FK: `JobEvent.job_id → Job.job_id`. Đường nguồn: `fk-105`.

### Constraints and source notes

- Khóa chính: `PK(job_id)`.
- UQ(schedule_id, schedule_fire_at, schedule_item_key) khi có lịch.
- Tối đa một Job chiếm một robot ở ACCEPTED/RUNNING; RECOVERY_REQUIRED giữ khóa vận hành đến khi đối soát.
- ACCEPTED/RUNNING yêu cầu robot_id; check hash/catalog/Map trước dispatch.
- recovery_of_job_id != job_id, không tạo vòng; robot chỉ nhận việc mới sau khi Job RECOVERY_REQUIRED đã reconciled_at.
- reconciled_by_account_id và reconciled_at cùng có hoặc cùng NULL; ghi audit kết quả đối soát.

Nguồn: S0, S1, S3

---

## JobTask

### Purpose

Task của một Job; trạng thái tổng hợp từ graph thực tế.

**Nhóm:** 06 · Điều phối và lịch sử thực thi

### Fields

| Field                  | Type           | Key | Nullable | References      | Unique                                                   | Default          | Description                                                  |
| ---------------------- | -------------- | --- | -------- | --------------- | -------------------------------------------------------- | ---------------- | ------------------------------------------------------------ |
| `job_task_id`          | `UUID`         | PK  | Not Null | — (không có FK) | Có — PK một cột                                          | Unclear from ERD | ID task thực thi.                                            |
| `job_id`               | `UUID`         | FK  | Not Null | `Job.job_id`    | Thuộc UQ(job_id, task_id); xem điều kiện tại Constraints | Unclear from ERD | Lần chạy chứa task. Tham chiếu Job.job_id.                   |
| `task_id`              | `UUID`         | FK  | Not Null | `Task.task_id`  | Thuộc UQ(job_id, task_id); xem điều kiện tại Constraints | Unclear from ERD | Task cấu hình. Tham chiếu Task.task_id.                      |
| `sequence_no`          | `int`          | —   | Not Null | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Thứ tự hiển thị >= 1.                                        |
| `task_name_snapshot`   | `varchar(160)` | —   | Not Null | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Tên task tại lúc tạo job.                                    |
| `config_snapshot_json` | `json`         | —   | Not Null | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Cấu hình task tại lúc tạo job.                               |
| `status`               | `enum`         | —   | Not Null | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED, NOT_VISITED. |
| `started_at`           | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Lần bắt đầu đầu tiên.                                        |
| `ended_at`             | `datetime UTC` | —   | Nullable | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Thời điểm kết thúc theo lần chạy.                            |
| `error_code`           | `varchar(100)` | —   | Nullable | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Mã lỗi nếu có.                                               |
| `error_message`        | `text`         | —   | Nullable | — (không có FK) | Unclear from ERD                                         | Unclear from ERD | Mô tả lỗi.                                                   |

### Relationships

- **R102 — Job 1:N JobTask** (chiều ngược: N:1). Một Job → 0..N JobTask; một JobTask → 1 Job. FK: `JobTask.job_id → Job.job_id`. Đường nguồn: `fk-101`.
- **R103 — Task 1:N JobTask** (chiều ngược: N:1). Một Task → 0..N JobTask; một JobTask → 1 Task. FK: `JobTask.task_id → Task.task_id`. Đường nguồn: `fk-102`.
- **R104 — JobTask 1:N JobStep** (chiều ngược: N:1). Một JobTask → 0..N JobStep; một JobStep → 1 JobTask. FK: `JobStep.job_task_id → JobTask.job_task_id`. Đường nguồn: `fk-103`.
- **R108 — JobTask 1:N JobEvent** (chiều ngược: N:1). Một JobTask → 0..N JobEvent; một JobEvent → 0..1 JobTask. FK: `JobEvent.job_task_id → JobTask.job_task_id`. Đường nguồn: `fk-107`.

### Constraints and source notes

- Khóa chính: `PK(job_task_id)`.
- UQ(job_id, task_id); task.workflow_id phải bằng job.workflow_id.
- Một JobTask chứa nhiều JobStep/lượt ghé; không ghi đè lượt lặp cũ.

Nguồn: S0, S1, S3

---

## JobStep

### Purpose

Một lượt ghé Step; mỗi vòng lặp có ID mới.

**Nhóm:** 06 · Điều phối và lịch sử thực thi

### Fields

| Field                   | Type           | Key | Nullable | References            | Unique                                                                                                                                               | Default          | Description                                                          |
| ----------------------- | -------------- | --- | -------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- | -------------------------------------------------------------------- |
| `job_step_id`           | `UUID`         | PK  | Not Null | — (không có FK)       | Có — PK một cột                                                                                                                                      | Unclear from ERD | ID lượt ghé; là operation_key mặc định của dịch vụ bước.             |
| `job_task_id`           | `UUID`         | FK  | Not Null | `JobTask.job_task_id` | Thuộc UQ(job_task_id, step_id, visit_no); xem điều kiện tại Constraints; Thuộc UQ(job_task_id, execution_sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Task thực thi chứa bước. Tham chiếu JobTask.job_task_id.             |
| `step_id`               | `UUID`         | FK  | Not Null | `Step.step_id`        | Thuộc UQ(job_task_id, step_id, visit_no); xem điều kiện tại Constraints                                                                              | Unclear from ERD | Bước định nghĩa. Tham chiếu Step.step_id.                            |
| `visit_no`              | `int`          | —   | Not Null | — (không có FK)       | Thuộc UQ(job_task_id, step_id, visit_no); xem điều kiện tại Constraints                                                                              | Unclear from ERD | Lần ghé step trong Job >= 1.                                         |
| `execution_sequence_no` | `int`          | —   | Not Null | — (không có FK)       | Thuộc UQ(job_task_id, execution_sequence_no); xem điều kiện tại Constraints                                                                          | Unclear from ERD | Thứ tự phát sinh lượt thực thi trong JobTask >= 1.                   |
| `step_code_snapshot`    | `varchar(80)`  | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Mã Step lúc chạy.                                                    |
| `step_type_snapshot`    | `varchar(40)`  | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Một trong 11 handler tại lúc chạy.                                   |
| `resolved_inputs_json`  | `json`         | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Params thực tế đã resolve; immutable khi bước bắt đầu.               |
| `result_json`           | `json`         | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Data output nguyên hợp đồng, gồm cả các khóa có giá trị null.        |
| `status`                | `enum`         | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | PENDING, RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, RETRYING. |
| `attempt_no`            | `int`          | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Số lần thử hiện tại trong lượt ghé, tính từ 1.                       |
| `operation_key`         | `varchar(150)` | UK  | Not Null | — (không có FK)       | Có — UK một cột                                                                                                                                      | `job_step_id`    | Ổn định qua retry của cùng lượt ghé; mặc định job_step_id.           |
| `timeout_sec_snapshot`  | `int`          | —   | Not Null | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Timeout đã chốt > 0.                                                 |
| `started_at`            | `datetime UTC` | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Bắt đầu lượt.                                                        |
| `ended_at`              | `datetime UTC` | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Kết thúc lượt.                                                       |
| `error_code`            | `varchar(100)` | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Mã lỗi máy đọc.                                                      |
| `error_message`         | `text`         | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Thông tin tra cứu; không dùng để rẽ nhánh.                           |
| `error_details_json`    | `json`         | —   | Nullable | — (không có FK)       | Unclear from ERD                                                                                                                                     | Unclear from ERD | Chi tiết lỗi hợp đồng.                                               |

### Relationships

- **R049 — JobStep 1:N Alert** (chiều ngược: N:1). Một JobStep → 0..N Alert; một Alert → 0..1 JobStep. FK: `Alert.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-048`.
- **R057 — JobStep 1:N CrowdDensityRecord** (chiều ngược: N:1). Một JobStep → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 JobStep. FK: `CrowdDensityRecord.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-056`.
- **R088 — JobStep 1:1 AuthVerification** (chiều ngược: 1:1). Một JobStep → 0..1 AuthVerification; một AuthVerification → 1 JobStep. FK: `AuthVerification.auth_job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-087`.
- **R090 — JobStep 1:1 AuthVerification** (chiều ngược: 1:1). Một JobStep → 0..1 AuthVerification; một AuthVerification → 0..1 JobStep. FK: `AuthVerification.used_by_job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-089`.
- **R104 — JobTask 1:N JobStep** (chiều ngược: N:1). Một JobTask → 0..N JobStep; một JobStep → 1 JobTask. FK: `JobStep.job_task_id → JobTask.job_task_id`. Đường nguồn: `fk-103`.
- **R105 — Step 1:N JobStep** (chiều ngược: N:1). Một Step → 0..N JobStep; một JobStep → 1 Step. FK: `JobStep.step_id → Step.step_id`. Đường nguồn: `fk-104`.
- **R109 — JobStep 1:N JobEvent** (chiều ngược: N:1). Một JobStep → 0..N JobEvent; một JobEvent → 0..1 JobStep. FK: `JobEvent.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-108`.

### Constraints and source notes

- Khóa chính: `PK(job_step_id)`.
- UQ(job_task_id, step_id, visit_no); UQ(job_task_id, execution_sequence_no).
- Step.task_id phải bằng JobTask.task_id; task và job cùng phiên bản Workflow.
- Không UNIQUE(job_id, step_id): vòng lặp phải giữ nhiều lượt.
- Mỗi attempt có event riêng; default max_attempts=1, không giả định retry đồng nghĩa chạy lại servo.

Nguồn: S0, S1, S3

---

## JobEvent

### Purpose

Event bền vững, chống trùng và làm timeline thực thi.

**Nhóm:** 06 · Điều phối và lịch sử thực thi

### Fields

| Field                 | Type           | Key | Nullable | References            | Unique                                                                    | Default          | Description                                                      |
| --------------------- | -------------- | --- | -------- | --------------------- | ------------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------- |
| `job_event_id`        | `UUID`         | PK  | Not Null | — (không có FK)       | Có — PK một cột                                                           | Unclear from ERD | Dùng chính event_id của giao thức khi đến từ Pi.                 |
| `job_id`              | `UUID`         | FK  | Not Null | `Job.job_id`          | Unclear from ERD                                                          | Unclear from ERD | Job liên quan. Tham chiếu Job.job_id.                            |
| `robot_id`            | `UUID`         | FK  | Nullable | `Robot.robot_id`      | Unclear from ERD                                                          | Unclear from ERD | Robot phát event nếu có. Tham chiếu Robot.robot_id.              |
| `job_task_id`         | `UUID`         | FK  | Nullable | `JobTask.job_task_id` | Unclear from ERD                                                          | Unclear from ERD | Task liên quan. Tham chiếu JobTask.job_task_id.                  |
| `job_step_id`         | `UUID`         | FK  | Nullable | `JobStep.job_step_id` | Unclear from ERD                                                          | Unclear from ERD | Lượt bước liên quan. Tham chiếu JobStep.job_step_id.             |
| `actor_account_id`    | `UUID`         | FK  | Nullable | `Account.account_id`  | Unclear from ERD                                                          | Unclear from ERD | Người xác nhận sự kiện nghiệp vụ. Tham chiếu Account.account_id. |
| `producer_type`       | `enum`         | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | PI, SERVER, USER_ACTION.                                         |
| `producer_session_id` | `UUID`         | —   | Not Null | — (không có FK)       | Thuộc UQ(producer_session_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | ID phiên producer.                                               |
| `sequence_no`         | `bigint`       | —   | Not Null | — (không có FK)       | Thuộc UQ(producer_session_id, sequence_no); xem điều kiện tại Constraints | Unclear from ERD | Thứ tự event trong producer session >= 1.                        |
| `event_type`          | `varchar(100)` | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | STEP_RESULT, JOB_RESULT, STAFF_ARRIVED... theo registry.         |
| `correlation_key`     | `varchar(150)` | —   | Nullable | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Mã incident/yêu cầu để WAIT đối chiếu; không phải FK đa hình.    |
| `attempt_no`          | `int`          | —   | Nullable | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Lần thử của bước nếu có.                                         |
| `previous_status`     | `varchar(40)`  | —   | Nullable | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Trạng thái nguồn trước event, diễn giải theo scope.              |
| `new_status`          | `varchar(40)`  | —   | Nullable | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Trạng thái nguồn sau event.                                      |
| `status_scope`        | `enum`         | —   | Nullable | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | JOB, JOB_TASK, JOB_STEP; NULL nếu không chuyển trạng thái.       |
| `event_data_json`     | `json`         | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Payload event theo schema, gồm output thật.                      |
| `occurred_at`         | `datetime UTC` | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Thời điểm phát sinh.                                             |
| `received_at`         | `datetime UTC` | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Thời điểm server nhận.                                           |
| `persisted_at`        | `datetime UTC` | —   | Not Null | — (không có FK)       | Unclear from ERD                                                          | Unclear from ERD | Thời điểm transaction đã ghi bền.                                |

### Relationships

- **R106 — Job 1:N JobEvent** (chiều ngược: N:1). Một Job → 0..N JobEvent; một JobEvent → 1 Job. FK: `JobEvent.job_id → Job.job_id`. Đường nguồn: `fk-105`.
- **R107 — Robot 1:N JobEvent** (chiều ngược: N:1). Một Robot → 0..N JobEvent; một JobEvent → 0..1 Robot. FK: `JobEvent.robot_id → Robot.robot_id`. Đường nguồn: `fk-106`.
- **R108 — JobTask 1:N JobEvent** (chiều ngược: N:1). Một JobTask → 0..N JobEvent; một JobEvent → 0..1 JobTask. FK: `JobEvent.job_task_id → JobTask.job_task_id`. Đường nguồn: `fk-107`.
- **R109 — JobStep 1:N JobEvent** (chiều ngược: N:1). Một JobStep → 0..N JobEvent; một JobEvent → 0..1 JobStep. FK: `JobEvent.job_step_id → JobStep.job_step_id`. Đường nguồn: `fk-108`.
- **R110 — Account 1:N JobEvent** (chiều ngược: N:1). Một Account → 0..N JobEvent; một JobEvent → 0..1 Account. FK: `JobEvent.actor_account_id → Account.account_id`. Đường nguồn: `fk-109`.

### Constraints and source notes

- Khóa chính: `PK(job_event_id)`.
- UQ(producer_session_id, sequence_no); job_event_id chống trùng toàn cục.
- FK JobTask/JobStep phải cùng job_id; scope khớp đối tượng nguồn.
- Lưu event và cập nhật Job/JobStep trong cùng transaction rồi gửi EVENT_ACK.
- Các attempt/result của bước lưu append-only; không lấy EVENT_ACK thay ACK cảnh báo.

Nguồn: S0, S3

---

# Relationship Summary

Mỗi dòng dưới đây tương ứng đúng một đường relationship trong ERD. Cardinality nêu cả hai chiều; các FK khác vai trò giữa cùng hai bảng vẫn giữ riêng.

| Parent Table        | Relationship   | Child Table            | Foreign Key                                     | Description                                                                                                                                                                   |
| ------------------- | -------------- | ---------------------- | ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Account`           | 1:1; ngược 1:1 | `StaffProfile`         | `StaffProfile.account_id`                       | R001 — Một Account → 0..1 StaffProfile; một StaffProfile → 1 Account. Đích: Account.account_id; fk-000: ERone / ERzeroToOne.                                                  |
| `StaffProfile`      | 1:N; ngược N:1 | `Shift`                | `Shift.staff_profile_id`                        | R002 — Một StaffProfile → 0..N Shift; một Shift → 1 StaffProfile. Đích: StaffProfile.staff_profile_id; fk-001: ERone / ERzeroToMany.                                          |
| `Room`              | 1:N; ngược N:1 | `Shift`                | `Shift.room_id`                                 | R003 — Một Room → 0..N Shift; một Shift → 1 Room. Đích: Room.room_id; fk-002: ERone / ERzeroToMany.                                                                           |
| `Shift`             | 1:N; ngược N:1 | `Slot`                 | `Slot.shift_id`                                 | R004 — Một Shift → 0..N Slot; một Slot → 1 Shift. Đích: Shift.shift_id; fk-003: ERone / ERzeroToMany.                                                                         |
| `Map`               | 1:N; ngược N:1 | `HospitalInformation`  | `HospitalInformation.map_id`                    | R005 — Một Map → 0..N HospitalInformation; một HospitalInformation → 0..1 Map. Đích: Map.map_id; fk-004: ERzeroToOne / ERzeroToMany.                                          |
| `Room`              | 1:N; ngược N:1 | `HospitalInformation`  | `HospitalInformation.room_id`                   | R006 — Một Room → 0..N HospitalInformation; một HospitalInformation → 0..1 Room. Đích: Room.room_id; fk-005: ERzeroToOne / ERzeroToMany.                                      |
| `Account`           | 1:N; ngược N:1 | `HospitalInformation`  | `HospitalInformation.updated_by_account_id`     | R007 — Một Account → 0..N HospitalInformation; một HospitalInformation → 1 Account. Đích: Account.account_id; fk-006: ERone / ERzeroToMany.                                   |
| `Account`           | 1:N; ngược N:1 | `AuditLog`             | `AuditLog.account_id`                           | R008 — Một Account → 0..N AuditLog; một AuditLog → 0..1 Account. Đích: Account.account_id; fk-007: ERzeroToOne / ERzeroToMany.                                                |
| `Account`           | 1:N; ngược N:1 | `SystemSetting`        | `SystemSetting.updated_by_account_id`           | R009 — Một Account → 0..N SystemSetting; một SystemSetting → 0..1 Account. Đích: Account.account_id; fk-008: ERzeroToOne / ERzeroToMany.                                      |
| `Account`           | 1:N; ngược N:1 | `VoiceInteraction`     | `VoiceInteraction.account_id`                   | R010 — Một Account → 0..N VoiceInteraction; một VoiceInteraction → 0..1 Account. Đích: Account.account_id; fk-009: ERzeroToOne / ERzeroToMany.                                |
| `Robot`             | 1:N; ngược N:1 | `VoiceInteraction`     | `VoiceInteraction.robot_id`                     | R011 — Một Robot → 0..N VoiceInteraction; một VoiceInteraction → 1 Robot. Đích: Robot.robot_id; fk-010: ERone / ERzeroToMany.                                                 |
| `VoiceInteraction`  | 1:N; ngược N:1 | `VoiceCommand`         | `VoiceCommand.voice_interaction_id`             | R012 — Một VoiceInteraction → 0..N VoiceCommand; một VoiceCommand → 1 VoiceInteraction. Đích: VoiceInteraction.voice_interaction_id; fk-011: ERone / ERzeroToMany.            |
| `ServiceRequest`    | 1:N; ngược N:1 | `VoiceCommand`         | `VoiceCommand.service_request_id`               | R013 — Một ServiceRequest → 0..N VoiceCommand; một VoiceCommand → 0..1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-012: ERzeroToOne / ERzeroToMany.           |
| `Map`               | 1:N; ngược N:1 | `Zone`                 | `Zone.map_id`                                   | R014 — Một Map → 0..N Zone; một Zone → 1 Map. Đích: Map.map_id; fk-013: ERone / ERzeroToMany.                                                                                 |
| `EndpointGroup`     | 1:N; ngược N:1 | `Room`                 | `Room.default_endpoint_group_id`                | R015 — Một EndpointGroup → 0..N Room; một Room → 0..1 EndpointGroup. Đích: EndpointGroup.endpoint_group_id; fk-014: ERzeroToOne / ERzeroToMany.                               |
| `Map`               | 1:N; ngược N:1 | `Endpoint`             | `Endpoint.map_id`                               | R016 — Một Map → 0..N Endpoint; một Endpoint → 1 Map. Đích: Map.map_id; fk-015: ERone / ERzeroToMany.                                                                         |
| `Zone`              | 1:N; ngược N:1 | `Endpoint`             | `Endpoint.zone_id`                              | R017 — Một Zone → 0..N Endpoint; một Endpoint → 0..1 Zone. Đích: Zone.zone_id; fk-016: ERzeroToOne / ERzeroToMany.                                                            |
| `Room`              | 1:N; ngược N:1 | `Endpoint`             | `Endpoint.room_id`                              | R018 — Một Room → 0..N Endpoint; một Endpoint → 0..1 Room. Đích: Room.room_id; fk-017: ERzeroToOne / ERzeroToMany.                                                            |
| `Map`               | 1:N; ngược N:1 | `EndpointGroup`        | `EndpointGroup.map_id`                          | R019 — Một Map → 0..N EndpointGroup; một EndpointGroup → 1 Map. Đích: Map.map_id; fk-018: ERone / ERzeroToMany.                                                               |
| `EndpointGroup`     | 1:N; ngược N:1 | `EndpointGroupMember`  | `EndpointGroupMember.endpoint_group_id`         | R020 — Một EndpointGroup → 0..N EndpointGroupMember; một EndpointGroupMember → 1 EndpointGroup. Đích: EndpointGroup.endpoint_group_id; fk-019: ERone / ERzeroToMany.          |
| `Endpoint`          | 1:N; ngược N:1 | `EndpointGroupMember`  | `EndpointGroupMember.endpoint_id`               | R021 — Một Endpoint → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Endpoint. Đích: Endpoint.endpoint_id; fk-020: ERone / ERzeroToMany.                               |
| `Map`               | 1:N; ngược N:1 | `EndpointGroupMember`  | `EndpointGroupMember.map_id`                    | R022 — Một Map → 0..N EndpointGroupMember; một EndpointGroupMember → 1 Map. Đích: Map.map_id; fk-021: ERone / ERzeroToMany.                                                   |
| `Step`              | 1:N; ngược N:1 | `Workflow`             | `Workflow.start_step_id`                        | R023 — Một Step → 0..N Workflow; một Workflow → 0..1 Step. Đích: Step.step_id; fk-022: ERzeroToOne / ERzeroToMany.                                                            |
| `Account`           | 1:N; ngược N:1 | `Workflow`             | `Workflow.published_by_account_id`              | R024 — Một Account → 0..N Workflow; một Workflow → 0..1 Account. Đích: Account.account_id; fk-023: ERzeroToOne / ERzeroToMany.                                                |
| `Workflow`          | 1:N; ngược N:1 | `Task`                 | `Task.workflow_id`                              | R025 — Một Workflow → 0..N Task; một Task → 1 Workflow. Đích: Workflow.workflow_id; fk-024: ERone / ERzeroToMany.                                                             |
| `Workflow`          | 1:N; ngược N:1 | `Step`                 | `Step.workflow_id`                              | R026 — Một Workflow → 0..N Step; một Step → 1 Workflow. Đích: Workflow.workflow_id; fk-025: ERone / ERzeroToMany.                                                             |
| `Task`              | 1:N; ngược N:1 | `Step`                 | `Step.task_id`                                  | R027 — Một Task → 0..N Step; một Step → 1 Task. Đích: Task.task_id; fk-026: ERone / ERzeroToMany.                                                                             |
| `StepType`          | 1:N; ngược N:1 | `Step`                 | `Step.step_type_code`                           | R028 — Một StepType → 0..N Step; một Step → 1 StepType. Đích: StepType.step_type_code; fk-027: ERone / ERzeroToMany.                                                          |
| `Workflow`          | 1:N; ngược N:1 | `StepTransition`       | `StepTransition.workflow_id`                    | R029 — Một Workflow → 0..N StepTransition; một StepTransition → 1 Workflow. Đích: Workflow.workflow_id; fk-028: ERone / ERzeroToMany.                                         |
| `Step`              | 1:N; ngược N:1 | `StepTransition`       | `StepTransition.from_step_id`                   | R030 — Một Step → 0..N StepTransition; một StepTransition → 1 Step. Đích: Step.step_id; fk-029: ERone / ERzeroToMany.                                                         |
| `Step`              | 1:N; ngược N:1 | `StepTransition`       | `StepTransition.to_step_id`                     | R031 — Một Step → 0..N StepTransition; một StepTransition → 0..1 Step. Đích: Step.step_id; fk-030: ERzeroToOne / ERzeroToMany.                                                |
| `Map`               | 1:N; ngược N:1 | `Robot`                | `Robot.current_map_id`                          | R032 — Một Map → 0..N Robot; một Robot → 0..1 Map. Đích: Map.map_id; fk-031: ERzeroToOne / ERzeroToMany.                                                                      |
| `Robot`             | 1:N; ngược N:1 | `RobotStatusHistory`   | `RobotStatusHistory.robot_id`                   | R033 — Một Robot → 0..N RobotStatusHistory; một RobotStatusHistory → 1 Robot. Đích: Robot.robot_id; fk-032: ERone / ERzeroToMany.                                             |
| `Job`               | 1:N; ngược N:1 | `RobotStatusHistory`   | `RobotStatusHistory.job_id`                     | R034 — Một Job → 0..N RobotStatusHistory; một RobotStatusHistory → 0..1 Job. Đích: Job.job_id; fk-033: ERzeroToOne / ERzeroToMany.                                            |
| `Robot`             | 1:N; ngược N:1 | `RobotLocationHistory` | `RobotLocationHistory.robot_id`                 | R035 — Một Robot → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Robot. Đích: Robot.robot_id; fk-034: ERone / ERzeroToMany.                                         |
| `Map`               | 1:N; ngược N:1 | `RobotLocationHistory` | `RobotLocationHistory.map_id`                   | R036 — Một Map → 0..N RobotLocationHistory; một RobotLocationHistory → 1 Map. Đích: Map.map_id; fk-035: ERone / ERzeroToMany.                                                 |
| `Job`               | 1:N; ngược N:1 | `RobotLocationHistory` | `RobotLocationHistory.job_id`                   | R037 — Một Job → 0..N RobotLocationHistory; một RobotLocationHistory → 0..1 Job. Đích: Job.job_id; fk-036: ERzeroToOne / ERzeroToMany.                                        |
| `Robot`             | 1:N; ngược N:1 | `DetectionEvent`       | `DetectionEvent.robot_id`                       | R038 — Một Robot → 0..N DetectionEvent; một DetectionEvent → 1 Robot. Đích: Robot.robot_id; fk-037: ERone / ERzeroToMany.                                                     |
| `Map`               | 1:N; ngược N:1 | `DetectionEvent`       | `DetectionEvent.map_id`                         | R039 — Một Map → 0..N DetectionEvent; một DetectionEvent → 1 Map. Đích: Map.map_id; fk-038: ERone / ERzeroToMany.                                                             |
| `Zone`              | 1:N; ngược N:1 | `DetectionEvent`       | `DetectionEvent.zone_id`                        | R040 — Một Zone → 0..N DetectionEvent; một DetectionEvent → 0..1 Zone. Đích: Zone.zone_id; fk-039: ERzeroToOne / ERzeroToMany.                                                |
| `DetectionEvent`    | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.detection_event_id`          | R041 — Một DetectionEvent → 0..N EmergencyIncident; một EmergencyIncident → 0..1 DetectionEvent. Đích: DetectionEvent.detection_event_id; fk-040: ERzeroToOne / ERzeroToMany. |
| `Robot`             | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.robot_id`                    | R042 — Một Robot → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Robot. Đích: Robot.robot_id; fk-041: ERzeroToOne / ERzeroToMany.                                      |
| `Map`               | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.map_id`                      | R043 — Một Map → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Map. Đích: Map.map_id; fk-042: ERzeroToOne / ERzeroToMany.                                              |
| `Endpoint`          | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.endpoint_id`                 | R044 — Một Endpoint → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-043: ERzeroToOne / ERzeroToMany.                          |
| `Account`           | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.acknowledged_by_account_id`  | R045 — Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. Đích: Account.account_id; fk-044: ERzeroToOne / ERzeroToMany.                              |
| `Account`           | 1:N; ngược N:1 | `EmergencyIncident`    | `EmergencyIncident.resolved_by_account_id`      | R046 — Một Account → 0..N EmergencyIncident; một EmergencyIncident → 0..1 Account. Đích: Account.account_id; fk-045: ERzeroToOne / ERzeroToMany.                              |
| `EmergencyIncident` | 1:N; ngược N:1 | `Alert`                | `Alert.incident_id`                             | R047 — Một EmergencyIncident → 0..N Alert; một Alert → 0..1 EmergencyIncident. Đích: EmergencyIncident.incident_id; fk-046: ERzeroToOne / ERzeroToMany.                       |
| `DetectionEvent`    | 1:N; ngược N:1 | `Alert`                | `Alert.detection_event_id`                      | R048 — Một DetectionEvent → 0..N Alert; một Alert → 0..1 DetectionEvent. Đích: DetectionEvent.detection_event_id; fk-047: ERzeroToOne / ERzeroToMany.                         |
| `JobStep`           | 1:N; ngược N:1 | `Alert`                | `Alert.job_step_id`                             | R049 — Một JobStep → 0..N Alert; một Alert → 0..1 JobStep. Đích: JobStep.job_step_id; fk-048: ERzeroToOne / ERzeroToMany.                                                     |
| `ServiceRequest`    | 1:N; ngược N:1 | `Alert`                | `Alert.service_request_id`                      | R050 — Một ServiceRequest → 0..N Alert; một Alert → 0..1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-049: ERzeroToOne / ERzeroToMany.                         |
| `Zone`              | 1:N; ngược N:1 | `Alert`                | `Alert.zone_id`                                 | R051 — Một Zone → 0..N Alert; một Alert → 0..1 Zone. Đích: Zone.zone_id; fk-050: ERzeroToOne / ERzeroToMany.                                                                  |
| `Alert`             | 1:N; ngược N:1 | `AlertRecipient`       | `AlertRecipient.alert_id`                       | R052 — Một Alert → 0..N AlertRecipient; một AlertRecipient → 1 Alert. Đích: Alert.alert_id; fk-051: ERone / ERzeroToMany.                                                     |
| `Account`           | 1:N; ngược N:1 | `AlertRecipient`       | `AlertRecipient.account_id`                     | R053 — Một Account → 0..N AlertRecipient; một AlertRecipient → 1 Account. Đích: Account.account_id; fk-052: ERone / ERzeroToMany.                                             |
| `Robot`             | 1:N; ngược N:1 | `CrowdDensityRecord`   | `CrowdDensityRecord.robot_id`                   | R054 — Một Robot → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Robot. Đích: Robot.robot_id; fk-053: ERone / ERzeroToMany.                                             |
| `Zone`              | 1:N; ngược N:1 | `CrowdDensityRecord`   | `CrowdDensityRecord.zone_id`                    | R055 — Một Zone → 0..N CrowdDensityRecord; một CrowdDensityRecord → 1 Zone. Đích: Zone.zone_id; fk-054: ERone / ERzeroToMany.                                                 |
| `Endpoint`          | 1:N; ngược N:1 | `CrowdDensityRecord`   | `CrowdDensityRecord.checkpoint_id`              | R056 — Một Endpoint → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-055: ERzeroToOne / ERzeroToMany.                        |
| `JobStep`           | 1:N; ngược N:1 | `CrowdDensityRecord`   | `CrowdDensityRecord.job_step_id`                | R057 — Một JobStep → 0..N CrowdDensityRecord; một CrowdDensityRecord → 0..1 JobStep. Đích: JobStep.job_step_id; fk-056: ERzeroToOne / ERzeroToMany.                           |
| `Map`               | 1:N; ngược N:1 | `HeatmapRecord`        | `HeatmapRecord.map_id`                          | R058 — Một Map → 0..N HeatmapRecord; một HeatmapRecord → 1 Map. Đích: Map.map_id; fk-057: ERone / ERzeroToMany.                                                               |
| `Account`           | 1:N; ngược N:1 | `ServiceRequest`       | `ServiceRequest.requested_by_account_id`        | R059 — Một Account → 0..N ServiceRequest; một ServiceRequest → 0..1 Account. Đích: Account.account_id; fk-058: ERzeroToOne / ERzeroToMany.                                    |
| `Robot`             | 1:N; ngược N:1 | `ServiceRequest`       | `ServiceRequest.requested_robot_id`             | R060 — Một Robot → 0..N ServiceRequest; một ServiceRequest → 0..1 Robot. Đích: Robot.robot_id; fk-059: ERzeroToOne / ERzeroToMany.                                            |
| `EmergencyIncident` | 1:N; ngược N:1 | `ServiceRequest`       | `ServiceRequest.related_incident_id`            | R061 — Một EmergencyIncident → 0..N ServiceRequest; một ServiceRequest → 0..1 EmergencyIncident. Đích: EmergencyIncident.incident_id; fk-060: ERzeroToOne / ERzeroToMany.     |
| `ServiceRequest`    | 1:1; ngược 1:1 | `NavigationRequest`    | `NavigationRequest.service_request_id`          | R062 — Một ServiceRequest → 0..1 NavigationRequest; một NavigationRequest → 1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-061: ERone / ERzeroToOne.           |
| `Map`               | 1:N; ngược N:1 | `NavigationRequest`    | `NavigationRequest.map_id`                      | R063 — Một Map → 0..N NavigationRequest; một NavigationRequest → 1 Map. Đích: Map.map_id; fk-062: ERone / ERzeroToMany.                                                       |
| `Room`              | 1:N; ngược N:1 | `NavigationRequest`    | `NavigationRequest.room_id`                     | R064 — Một Room → 0..N NavigationRequest; một NavigationRequest → 0..1 Room. Đích: Room.room_id; fk-063: ERzeroToOne / ERzeroToMany.                                          |
| `Endpoint`          | 1:N; ngược N:1 | `NavigationRequest`    | `NavigationRequest.destination_endpoint_id`     | R065 — Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-064: ERzeroToOne / ERzeroToMany.                          |
| `EndpointGroup`     | 1:N; ngược N:1 | `NavigationRequest`    | `NavigationRequest.destination_group_id`        | R066 — Một EndpointGroup → 0..N NavigationRequest; một NavigationRequest → 0..1 EndpointGroup. Đích: EndpointGroup.endpoint_group_id; fk-065: ERzeroToOne / ERzeroToMany.     |
| `Endpoint`          | 1:N; ngược N:1 | `NavigationRequest`    | `NavigationRequest.resolved_endpoint_id`        | R067 — Một Endpoint → 0..N NavigationRequest; một NavigationRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-066: ERzeroToOne / ERzeroToMany.                          |
| `ServiceRequest`    | 1:1; ngược 1:1 | `EscortRequest`        | `EscortRequest.service_request_id`              | R068 — Một ServiceRequest → 0..1 EscortRequest; một EscortRequest → 1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-067: ERone / ERzeroToOne.                   |
| `Map`               | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.map_id`                          | R069 — Một Map → 0..N EscortRequest; một EscortRequest → 1 Map. Đích: Map.map_id; fk-068: ERone / ERzeroToMany.                                                               |
| `Room`              | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.origin_room_id`                  | R070 — Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. Đích: Room.room_id; fk-069: ERzeroToOne / ERzeroToMany.                                                  |
| `Room`              | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.destination_room_id`             | R071 — Một Room → 0..N EscortRequest; một EscortRequest → 0..1 Room. Đích: Room.room_id; fk-070: ERzeroToOne / ERzeroToMany.                                                  |
| `Endpoint`          | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.origin_endpoint_id`              | R072 — Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-071: ERzeroToOne / ERzeroToMany.                                  |
| `Endpoint`          | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.destination_endpoint_id`         | R073 — Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-072: ERzeroToOne / ERzeroToMany.                                  |
| `EndpointGroup`     | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.destination_group_id`            | R074 — Một EndpointGroup → 0..N EscortRequest; một EscortRequest → 0..1 EndpointGroup. Đích: EndpointGroup.endpoint_group_id; fk-073: ERzeroToOne / ERzeroToMany.             |
| `Endpoint`          | 1:N; ngược N:1 | `EscortRequest`        | `EscortRequest.resolved_endpoint_id`            | R075 — Một Endpoint → 0..N EscortRequest; một EscortRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-074: ERzeroToOne / ERzeroToMany.                                  |
| `ServiceRequest`    | 1:1; ngược 1:1 | `DeliveryRequest`      | `DeliveryRequest.service_request_id`            | R076 — Một ServiceRequest → 0..1 DeliveryRequest; một DeliveryRequest → 1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-075: ERone / ERzeroToOne.               |
| `Map`               | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.map_id`                        | R077 — Một Map → 0..N DeliveryRequest; một DeliveryRequest → 1 Map. Đích: Map.map_id; fk-076: ERone / ERzeroToMany.                                                           |
| `Room`              | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.pickup_room_id`                | R078 — Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. Đích: Room.room_id; fk-077: ERzeroToOne / ERzeroToMany.                                              |
| `Room`              | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.delivery_room_id`              | R079 — Một Room → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Room. Đích: Room.room_id; fk-078: ERzeroToOne / ERzeroToMany.                                              |
| `Endpoint`          | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.pickup_endpoint_id`            | R080 — Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 1 Endpoint. Đích: Endpoint.endpoint_id; fk-079: ERone / ERzeroToMany.                                       |
| `Endpoint`          | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.delivery_endpoint_id`          | R081 — Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-080: ERzeroToOne / ERzeroToMany.                              |
| `EndpointGroup`     | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.delivery_group_id`             | R082 — Một EndpointGroup → 0..N DeliveryRequest; một DeliveryRequest → 0..1 EndpointGroup. Đích: EndpointGroup.endpoint_group_id; fk-081: ERzeroToOne / ERzeroToMany.         |
| `Endpoint`          | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.resolved_delivery_endpoint_id` | R083 — Một Endpoint → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Endpoint. Đích: Endpoint.endpoint_id; fk-082: ERzeroToOne / ERzeroToMany.                              |
| `Account`           | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.sender_account_id`             | R084 — Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. Đích: Account.account_id; fk-083: ERzeroToOne / ERzeroToMany.                                  |
| `Account`           | 1:N; ngược N:1 | `DeliveryRequest`      | `DeliveryRequest.receiver_account_id`           | R085 — Một Account → 0..N DeliveryRequest; một DeliveryRequest → 0..1 Account. Đích: Account.account_id; fk-084: ERzeroToOne / ERzeroToMany.                                  |
| `DeliveryRequest`   | 1:N; ngược N:1 | `DeliveryItem`         | `DeliveryItem.delivery_request_id`              | R086 — Một DeliveryRequest → 0..N DeliveryItem; một DeliveryItem → 1 DeliveryRequest. Đích: DeliveryRequest.service_request_id; fk-085: ERone / ERzeroToMany.                 |
| `Job`               | 1:N; ngược N:1 | `AuthVerification`     | `AuthVerification.job_id`                       | R087 — Một Job → 0..N AuthVerification; một AuthVerification → 1 Job. Đích: Job.job_id; fk-086: ERone / ERzeroToMany.                                                         |
| `JobStep`           | 1:1; ngược 1:1 | `AuthVerification`     | `AuthVerification.auth_job_step_id`             | R088 — Một JobStep → 0..1 AuthVerification; một AuthVerification → 1 JobStep. Đích: JobStep.job_step_id; fk-087: ERone / ERzeroToOne.                                         |
| `Account`           | 1:N; ngược N:1 | `AuthVerification`     | `AuthVerification.actor_account_id`             | R089 — Một Account → 0..N AuthVerification; một AuthVerification → 1 Account. Đích: Account.account_id; fk-088: ERone / ERzeroToMany.                                         |
| `JobStep`           | 1:1; ngược 1:1 | `AuthVerification`     | `AuthVerification.used_by_job_step_id`          | R090 — Một JobStep → 0..1 AuthVerification; một AuthVerification → 0..1 JobStep. Đích: JobStep.job_step_id; fk-089: ERzeroToOne / ERzeroToOne.                                |
| `Workflow`          | 1:N; ngược N:1 | `Schedule`             | `Schedule.workflow_id`                          | R091 — Một Workflow → 0..N Schedule; một Schedule → 1 Workflow. Đích: Workflow.workflow_id; fk-090: ERone / ERzeroToMany.                                                     |
| `Robot`             | 1:N; ngược N:1 | `Schedule`             | `Schedule.robot_id`                             | R092 — Một Robot → 0..N Schedule; một Schedule → 0..1 Robot. Đích: Robot.robot_id; fk-091: ERzeroToOne / ERzeroToMany.                                                        |
| `Account`           | 1:N; ngược N:1 | `Schedule`             | `Schedule.created_by_account_id`                | R093 — Một Account → 0..N Schedule; một Schedule → 0..1 Account. Đích: Account.account_id; fk-092: ERzeroToOne / ERzeroToMany.                                                |
| `Workflow`          | 1:N; ngược N:1 | `Job`                  | `Job.workflow_id`                               | R094 — Một Workflow → 0..N Job; một Job → 1 Workflow. Đích: Workflow.workflow_id; fk-093: ERone / ERzeroToMany.                                                               |
| `ServiceRequest`    | 1:N; ngược N:1 | `Job`                  | `Job.service_request_id`                        | R095 — Một ServiceRequest → 0..N Job; một Job → 0..1 ServiceRequest. Đích: ServiceRequest.service_request_id; fk-094: ERzeroToOne / ERzeroToMany.                             |
| `Schedule`          | 1:N; ngược N:1 | `Job`                  | `Job.schedule_id`                               | R096 — Một Schedule → 0..N Job; một Job → 0..1 Schedule. Đích: Schedule.schedule_id; fk-095: ERzeroToOne / ERzeroToMany.                                                      |
| `Robot`             | 1:N; ngược N:1 | `Job`                  | `Job.robot_id`                                  | R097 — Một Robot → 0..N Job; một Job → 0..1 Robot. Đích: Robot.robot_id; fk-096: ERzeroToOne / ERzeroToMany.                                                                  |
| `Map`               | 1:N; ngược N:1 | `Job`                  | `Job.map_id`                                    | R098 — Một Map → 0..N Job; một Job → 0..1 Map. Đích: Map.map_id; fk-097: ERzeroToOne / ERzeroToMany.                                                                          |
| `Job`               | 1:N; ngược N:1 | `Job`                  | `Job.recovery_of_job_id`                        | R099 — Một Job → 0..N Job; một Job → 0..1 Job. Đích: Job.job_id; fk-098: ERzeroToOne / ERzeroToMany.                                                                          |
| `Account`           | 1:N; ngược N:1 | `Job`                  | `Job.cancel_requested_by_account_id`            | R100 — Một Account → 0..N Job; một Job → 0..1 Account. Đích: Account.account_id; fk-099: ERzeroToOne / ERzeroToMany.                                                          |
| `Account`           | 1:N; ngược N:1 | `Job`                  | `Job.reconciled_by_account_id`                  | R101 — Một Account → 0..N Job; một Job → 0..1 Account. Đích: Account.account_id; fk-100: ERzeroToOne / ERzeroToMany.                                                          |
| `Job`               | 1:N; ngược N:1 | `JobTask`              | `JobTask.job_id`                                | R102 — Một Job → 0..N JobTask; một JobTask → 1 Job. Đích: Job.job_id; fk-101: ERone / ERzeroToMany.                                                                           |
| `Task`              | 1:N; ngược N:1 | `JobTask`              | `JobTask.task_id`                               | R103 — Một Task → 0..N JobTask; một JobTask → 1 Task. Đích: Task.task_id; fk-102: ERone / ERzeroToMany.                                                                       |
| `JobTask`           | 1:N; ngược N:1 | `JobStep`              | `JobStep.job_task_id`                           | R104 — Một JobTask → 0..N JobStep; một JobStep → 1 JobTask. Đích: JobTask.job_task_id; fk-103: ERone / ERzeroToMany.                                                          |
| `Step`              | 1:N; ngược N:1 | `JobStep`              | `JobStep.step_id`                               | R105 — Một Step → 0..N JobStep; một JobStep → 1 Step. Đích: Step.step_id; fk-104: ERone / ERzeroToMany.                                                                       |
| `Job`               | 1:N; ngược N:1 | `JobEvent`             | `JobEvent.job_id`                               | R106 — Một Job → 0..N JobEvent; một JobEvent → 1 Job. Đích: Job.job_id; fk-105: ERone / ERzeroToMany.                                                                         |
| `Robot`             | 1:N; ngược N:1 | `JobEvent`             | `JobEvent.robot_id`                             | R107 — Một Robot → 0..N JobEvent; một JobEvent → 0..1 Robot. Đích: Robot.robot_id; fk-106: ERzeroToOne / ERzeroToMany.                                                        |
| `JobTask`           | 1:N; ngược N:1 | `JobEvent`             | `JobEvent.job_task_id`                          | R108 — Một JobTask → 0..N JobEvent; một JobEvent → 0..1 JobTask. Đích: JobTask.job_task_id; fk-107: ERzeroToOne / ERzeroToMany.                                               |
| `JobStep`           | 1:N; ngược N:1 | `JobEvent`             | `JobEvent.job_step_id`                          | R109 — Một JobStep → 0..N JobEvent; một JobEvent → 0..1 JobStep. Đích: JobStep.job_step_id; fk-108: ERzeroToOne / ERzeroToMany.                                               |
| `Account`           | 1:N; ngược N:1 | `JobEvent`             | `JobEvent.actor_account_id`                     | R110 — Một Account → 0..N JobEvent; một JobEvent → 0..1 Account. Đích: Account.account_id; fk-109: ERzeroToOne / ERzeroToMany.                                                |

## Many-to-many relationships

ERD ghi rõ quan hệ N:N giữa `EndpointGroup` và `Endpoint`, qua `EndpointGroupMember`. Hai FK bắt buộc và PK ghép được thể hiện đầy đủ:

| Entity A      | Relationship                             | Entity B | Junction Table      | Evidence                                                                                                                     |
| ------------- | ---------------------------------------- | -------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| EndpointGroup | N:N; mỗi phía 0..N qua các dòng liên kết | Endpoint | EndpointGroupMember | endpoint_group_id → EndpointGroup.endpoint_group_id; endpoint_id → Endpoint.endpoint_id; PK(endpoint_group_id, endpoint_id). |

Mỗi `EndpointGroupMember` thuộc đúng 1 `EndpointGroup` và đúng 1 `Endpoint`. `map_id` là FK bổ sung; quy tắc cùng Map được giữ nguyên tại bảng. Không có đường trực tiếp N:N thay thế hai FK này.

Các cấu trúc liên kết khác dưới đây đọc được từ hai đường FK và ràng buộc của ERD. Chúng là đường liên hệ **qua bản ghi liên kết**, không phải đường relationship N:N riêng được vẽ trong file:

| Entities            | Association entity | What is explicit in ERD                                                                  | Interpretation boundary                                                                                                           |
| ------------------- | ------------------ | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Alert ↔ Account     | AlertRecipient     | Alert 1:N AlertRecipient; Account 1:N AlertRecipient; UQ(alert_id, account_id, channel). | Liên kết nhiều thông báo với nhiều tài khoản qua người nhận; một cặp có thể có nhiều kênh. Không thay UQ ba cột bằng UQ hai cột.  |
| Job ↔ Task          | JobTask            | Job 1:N JobTask; Task 1:N JobTask; UQ(job_id, task_id).                                  | Liên kết N:N qua task thực thi; phải thỏa task.workflow_id = job.workflow_id.                                                     |
| StaffProfile ↔ Room | Shift              | StaffProfile 1:N Shift; Room 1:N Shift; một Shift là một lần phân công.                  | Có thể có nhiều liên kết qua các lần phân công. Giữ nghĩa Shift là bản ghi có thời gian; không biến thành junction chỉ có hai FK. |
| JobTask ↔ Step      | JobStep            | JobTask 1:N JobStep; Step 1:N JobStep; UQ(job_task_id, step_id, visit_no).               | Nhiều lượt ghé có thể nối cùng cặp. Giữ visit_no; không ép unique(job_task_id, step_id).                                          |

Không tự coi mọi entity có nhiều FK là bảng trung gian N:N độc lập; toàn bộ FK còn lại đã có trong Relationship Summary.

## Conditional minimum cardinalities

Không có đường nối nào ghi `1..N`. Tuy nhiên ghi chú quy định `DeliveryRequest` cần ít nhất một `DeliveryItem` trước SUBMITTED/dispatch. Đường vẽ vẫn cho phép 0..N ở trạng thái khác. Tương tự, `ServiceRequest` cần đúng một subtype phù hợp khi SUBMITTED đối với NAVIGATION/ESCORT/DELIVERY; từng đường subtype riêng lẻ vẫn là 0..1. Đây là điều kiện nghiệp vụ trong ERD, không phải thay đổi sơ đồ.

# Foreign Key Summary

| Table                  | Foreign Key                      | References                              |
| ---------------------- | -------------------------------- | --------------------------------------- |
| `StaffProfile`         | `account_id`                     | `Account.account_id`                    |
| `Shift`                | `staff_profile_id`               | `StaffProfile.staff_profile_id`         |
| `Shift`                | `room_id`                        | `Room.room_id`                          |
| `Slot`                 | `shift_id`                       | `Shift.shift_id`                        |
| `HospitalInformation`  | `map_id`                         | `Map.map_id`                            |
| `HospitalInformation`  | `room_id`                        | `Room.room_id`                          |
| `HospitalInformation`  | `updated_by_account_id`          | `Account.account_id`                    |
| `AuditLog`             | `account_id`                     | `Account.account_id`                    |
| `SystemSetting`        | `updated_by_account_id`          | `Account.account_id`                    |
| `VoiceInteraction`     | `account_id`                     | `Account.account_id`                    |
| `VoiceInteraction`     | `robot_id`                       | `Robot.robot_id`                        |
| `VoiceCommand`         | `voice_interaction_id`           | `VoiceInteraction.voice_interaction_id` |
| `VoiceCommand`         | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `Zone`                 | `map_id`                         | `Map.map_id`                            |
| `Room`                 | `default_endpoint_group_id`      | `EndpointGroup.endpoint_group_id`       |
| `Endpoint`             | `map_id`                         | `Map.map_id`                            |
| `Endpoint`             | `zone_id`                        | `Zone.zone_id`                          |
| `Endpoint`             | `room_id`                        | `Room.room_id`                          |
| `EndpointGroup`        | `map_id`                         | `Map.map_id`                            |
| `EndpointGroupMember`  | `endpoint_group_id`              | `EndpointGroup.endpoint_group_id`       |
| `EndpointGroupMember`  | `endpoint_id`                    | `Endpoint.endpoint_id`                  |
| `EndpointGroupMember`  | `map_id`                         | `Map.map_id`                            |
| `Workflow`             | `start_step_id`                  | `Step.step_id`                          |
| `Workflow`             | `published_by_account_id`        | `Account.account_id`                    |
| `Task`                 | `workflow_id`                    | `Workflow.workflow_id`                  |
| `Step`                 | `workflow_id`                    | `Workflow.workflow_id`                  |
| `Step`                 | `task_id`                        | `Task.task_id`                          |
| `Step`                 | `step_type_code`                 | `StepType.step_type_code`               |
| `StepTransition`       | `workflow_id`                    | `Workflow.workflow_id`                  |
| `StepTransition`       | `from_step_id`                   | `Step.step_id`                          |
| `StepTransition`       | `to_step_id`                     | `Step.step_id`                          |
| `Robot`                | `current_map_id`                 | `Map.map_id`                            |
| `RobotStatusHistory`   | `robot_id`                       | `Robot.robot_id`                        |
| `RobotStatusHistory`   | `job_id`                         | `Job.job_id`                            |
| `RobotLocationHistory` | `robot_id`                       | `Robot.robot_id`                        |
| `RobotLocationHistory` | `map_id`                         | `Map.map_id`                            |
| `RobotLocationHistory` | `job_id`                         | `Job.job_id`                            |
| `DetectionEvent`       | `robot_id`                       | `Robot.robot_id`                        |
| `DetectionEvent`       | `map_id`                         | `Map.map_id`                            |
| `DetectionEvent`       | `zone_id`                        | `Zone.zone_id`                          |
| `EmergencyIncident`    | `detection_event_id`             | `DetectionEvent.detection_event_id`     |
| `EmergencyIncident`    | `robot_id`                       | `Robot.robot_id`                        |
| `EmergencyIncident`    | `map_id`                         | `Map.map_id`                            |
| `EmergencyIncident`    | `endpoint_id`                    | `Endpoint.endpoint_id`                  |
| `EmergencyIncident`    | `acknowledged_by_account_id`     | `Account.account_id`                    |
| `EmergencyIncident`    | `resolved_by_account_id`         | `Account.account_id`                    |
| `Alert`                | `incident_id`                    | `EmergencyIncident.incident_id`         |
| `Alert`                | `detection_event_id`             | `DetectionEvent.detection_event_id`     |
| `Alert`                | `job_step_id`                    | `JobStep.job_step_id`                   |
| `Alert`                | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `Alert`                | `zone_id`                        | `Zone.zone_id`                          |
| `AlertRecipient`       | `alert_id`                       | `Alert.alert_id`                        |
| `AlertRecipient`       | `account_id`                     | `Account.account_id`                    |
| `CrowdDensityRecord`   | `robot_id`                       | `Robot.robot_id`                        |
| `CrowdDensityRecord`   | `zone_id`                        | `Zone.zone_id`                          |
| `CrowdDensityRecord`   | `checkpoint_id`                  | `Endpoint.endpoint_id`                  |
| `CrowdDensityRecord`   | `job_step_id`                    | `JobStep.job_step_id`                   |
| `HeatmapRecord`        | `map_id`                         | `Map.map_id`                            |
| `ServiceRequest`       | `requested_by_account_id`        | `Account.account_id`                    |
| `ServiceRequest`       | `requested_robot_id`             | `Robot.robot_id`                        |
| `ServiceRequest`       | `related_incident_id`            | `EmergencyIncident.incident_id`         |
| `NavigationRequest`    | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `NavigationRequest`    | `map_id`                         | `Map.map_id`                            |
| `NavigationRequest`    | `room_id`                        | `Room.room_id`                          |
| `NavigationRequest`    | `destination_endpoint_id`        | `Endpoint.endpoint_id`                  |
| `NavigationRequest`    | `destination_group_id`           | `EndpointGroup.endpoint_group_id`       |
| `NavigationRequest`    | `resolved_endpoint_id`           | `Endpoint.endpoint_id`                  |
| `EscortRequest`        | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `EscortRequest`        | `map_id`                         | `Map.map_id`                            |
| `EscortRequest`        | `origin_room_id`                 | `Room.room_id`                          |
| `EscortRequest`        | `destination_room_id`            | `Room.room_id`                          |
| `EscortRequest`        | `origin_endpoint_id`             | `Endpoint.endpoint_id`                  |
| `EscortRequest`        | `destination_endpoint_id`        | `Endpoint.endpoint_id`                  |
| `EscortRequest`        | `destination_group_id`           | `EndpointGroup.endpoint_group_id`       |
| `EscortRequest`        | `resolved_endpoint_id`           | `Endpoint.endpoint_id`                  |
| `DeliveryRequest`      | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `DeliveryRequest`      | `map_id`                         | `Map.map_id`                            |
| `DeliveryRequest`      | `pickup_room_id`                 | `Room.room_id`                          |
| `DeliveryRequest`      | `delivery_room_id`               | `Room.room_id`                          |
| `DeliveryRequest`      | `pickup_endpoint_id`             | `Endpoint.endpoint_id`                  |
| `DeliveryRequest`      | `delivery_endpoint_id`           | `Endpoint.endpoint_id`                  |
| `DeliveryRequest`      | `delivery_group_id`              | `EndpointGroup.endpoint_group_id`       |
| `DeliveryRequest`      | `resolved_delivery_endpoint_id`  | `Endpoint.endpoint_id`                  |
| `DeliveryRequest`      | `sender_account_id`              | `Account.account_id`                    |
| `DeliveryRequest`      | `receiver_account_id`            | `Account.account_id`                    |
| `DeliveryItem`         | `delivery_request_id`            | `DeliveryRequest.service_request_id`    |
| `AuthVerification`     | `job_id`                         | `Job.job_id`                            |
| `AuthVerification`     | `auth_job_step_id`               | `JobStep.job_step_id`                   |
| `AuthVerification`     | `actor_account_id`               | `Account.account_id`                    |
| `AuthVerification`     | `used_by_job_step_id`            | `JobStep.job_step_id`                   |
| `Schedule`             | `workflow_id`                    | `Workflow.workflow_id`                  |
| `Schedule`             | `robot_id`                       | `Robot.robot_id`                        |
| `Schedule`             | `created_by_account_id`          | `Account.account_id`                    |
| `Job`                  | `workflow_id`                    | `Workflow.workflow_id`                  |
| `Job`                  | `service_request_id`             | `ServiceRequest.service_request_id`     |
| `Job`                  | `schedule_id`                    | `Schedule.schedule_id`                  |
| `Job`                  | `robot_id`                       | `Robot.robot_id`                        |
| `Job`                  | `map_id`                         | `Map.map_id`                            |
| `Job`                  | `recovery_of_job_id`             | `Job.job_id`                            |
| `Job`                  | `cancel_requested_by_account_id` | `Account.account_id`                    |
| `Job`                  | `reconciled_by_account_id`       | `Account.account_id`                    |
| `JobTask`              | `job_id`                         | `Job.job_id`                            |
| `JobTask`              | `task_id`                        | `Task.task_id`                          |
| `JobStep`              | `job_task_id`                    | `JobTask.job_task_id`                   |
| `JobStep`              | `step_id`                        | `Step.step_id`                          |
| `JobEvent`             | `job_id`                         | `Job.job_id`                            |
| `JobEvent`             | `robot_id`                       | `Robot.robot_id`                        |
| `JobEvent`             | `job_task_id`                    | `JobTask.job_task_id`                   |
| `JobEvent`             | `job_step_id`                    | `JobStep.job_step_id`                   |
| `JobEvent`             | `actor_account_id`               | `Account.account_id`                    |

## Composite FK notes

Ngoài 110 đường FK đơn, ERD có ràng buộc ghép bằng chữ. Các ràng buộc này bổ sung kiểm tra tính nhất quán; không được đếm như entity hay quan hệ nghiệp vụ mới. Không tự điền cột đích khi ghi chú chưa viết đủ.

| Table               | Foreign Key                                                     | References                                                                                                                                           |
| ------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Endpoint            | (map_id, zone_id)                                               | Zone.(map_id, zone_id); khi zone_id có giá trị — ghi rõ trong ERD.                                                                                   |
| Step                | (workflow_id, task_id)                                          | Task.(workflow_id, task_id) — ghi rõ trong ERD.                                                                                                      |
| EmergencyIncident   | (map_id, endpoint_id)                                           | Endpoint.(map_id, endpoint_id); nếu endpoint_id có giá trị — ghi rõ trong ERD.                                                                       |
| EndpointGroupMember | (map_id, endpoint_group_id)                                     | Unclear from ERD — ghi chú nêu FK ghép nhưng không ghi tuple đích đầy đủ. FK đơn endpoint_group_id → EndpointGroup.endpoint_group_id có đường riêng. |
| EndpointGroupMember | (map_id, endpoint_id)                                           | Unclear from ERD — ghi chú nêu FK ghép nhưng không ghi tuple đích đầy đủ. FK đơn endpoint_id → Endpoint.endpoint_id có đường riêng.                  |
| StepTransition      | Unclear from ERD — ghi chú “FK ghép buộc from/to cùng workflow” | Unclear from ERD — không ghi tuple nguồn/đích cụ thể cho hai FK ghép. Các FK đơn workflow_id, from_step_id, to_step_id đã liệt kê phía trên.         |

Các quy tắc cùng Job/Task/Workflow/Map khác được chép nguyên văn trong Constraints của từng bảng. Chúng chưa tự động trở thành một FK ghép đã khai báo đầy đủ. Các chuỗi ID trong JSON hoặc tham chiếu đa hình không được tạo thêm đường FK.

# JSON / JSONB Fields

Có 37 field kiểu `json`, không có field khai báo kiểu `jsonb`. Mục này chỉ ghi nội dung ERD. Các hợp đồng I/O được chép ở phần [Embedded ERD Notes and Contracts](#embedded-erd-notes-and-contracts); chúng không tự xác định cấu trúc đầy đủ của mọi cột JSON.

## HospitalInformation.keywords_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Mảng chuỗi từ khóa tìm kiếm.
- **Cấu trúc được nêu:** Mảng chuỗi từ khóa (array of strings).
- **Chi tiết chưa có:** `Structure not specified in ERD` đối với toàn bộ ràng buộc/schema máy đọc ngoài cấu trúc cơ bản vừa nêu.

## AuditLog.before_data_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Snapshot trước thao tác; loại bỏ thông tin bí mật.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## AuditLog.after_data_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Snapshot sau thao tác; loại bỏ thông tin bí mật.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## SystemSetting.setting_value_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Giá trị JSON có kiểu.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## VoiceCommand.parameters_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Tham số trích xuất; chưa phải FK trong JSON.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Zone.polygon_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Đường bao trong frame Map, đơn vị mét.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Zone.camera_roi_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Vùng quan sát được hiệu chuẩn trên ảnh.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## StepType.input_schema_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** JSON Schema kiểm input, cả quy tắc điều kiện.
- **Đối chiếu ghi chú:** Tab 02 có 11 bộ INPUT (params). ERD không nhúng tài liệu JSON Schema hoàn chỉnh tương ứng.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## StepType.output_schema_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** JSON Schema kiểm data output; field null vẫn phải có khóa.
- **Đối chiếu ghi chú:** Tab 02 có 11 bộ OUTPUT (data), gồm các field có giá trị null. ERD không nhúng tài liệu JSON Schema hoàn chỉnh tương ứng.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## StepType.required_capabilities_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Danh sách capability cần hỗ trợ.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.input_schema_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Schema input; tương đương validation_schema của WorkflowParam trong tài liệu.
- **Đối chiếu ghi chú:** Tab 02: WorkflowParam.validation_schema_json → Workflow.input_schema_json. Đây là tên ánh xạ trong ghi chú, không phải một entity WorkflowParam đang tồn tại.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.input_ui_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Cấu hình form; tương đương ui_config của WorkflowParam.
- **Đối chiếu ghi chú:** Tab 02: WorkflowParam.ui_config_json → Workflow.input_ui_json; không có entity WorkflowParam trong schema hiện tại.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.default_inputs_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Giá trị mặc định đã kiểm schema.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.output_mapping_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Ánh xạ kết quả cuối workflow.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.required_capabilities_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Danh sách capability trước dispatch.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Workflow.compiled_definition_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Artifact được biên dịch từ Task/Step/StepTransition.
- **Đối chiếu ghi chú:** Tab 02: artifact compile từ graph có FK; không phải một graph độc lập để chỉnh sửa.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Task.config_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Cấu hình hiển thị/nghiệp vụ đã đăng ký.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Step.params_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Hằng và $ref được đăng ký; hợp đồng params của handler.
- **Đối chiếu ghi chú:** Tab 02: Step.params_json + input_mapping_json → JobStep.resolved_inputs_json → handler. Xem INPUT của 11 STEP TYPE; cú pháp $ref cụ thể chưa đầy đủ.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Step.input_mapping_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Binding input/outputs/vars; ưu tiên binding đè hằng theo compiler.
- **Đối chiếu ghi chú:** Tab 02 ghi ánh xạ input/outputs/vars và binding đè hằng theo compiler; không cung cấp toàn bộ grammar binding.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Step.retry_codes_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Mảng mã lỗi được phép retry; bản mặc định [].
- **Cấu trúc được nêu:** Mảng mã lỗi được phép retry; mặc định [].
- **Chi tiết chưa có:** `Structure not specified in ERD` đối với toàn bộ ràng buộc/schema máy đọc ngoài cấu trúc cơ bản vừa nêu.

## StepTransition.condition_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** SAFE_EXPR_V1; xét output, ví dụ confirmed=true.
- **Đối chiếu ghi chú:** Tab 02 CHECK_CONDITION mô tả SAFE_EXPR_V1 với var, op, args; chưa có schema AST hoàn chỉnh.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Robot.capabilities_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Capability từ HELLO đã xác minh.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## DetectionEvent.event_payload_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Chi tiết kết quả detector.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Alert.data_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Payload theo notification_type, có schema.
- **Đối chiếu ghi chú:** NOTIFY.input có data kiểu object; nội dung phụ thuộc notification_type và nghiệp vụ.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Alert.requested_recipients_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Nhóm/người nhận lúc yêu cầu, dùng audit.
- **Đối chiếu ghi chú:** NOTIFY.input có recipients kiểu array, ít nhất một phần tử; cấu trúc phần tử không được định nghĩa đầy đủ.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## CrowdDensityRecord.metrics_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Kết quả sensing gốc theo schema.
- **Đối chiếu ghi chú:** EXECUTE_SENSING.output có metrics kiểu object phụ thuộc task_type; schema metrics chi tiết không được nhúng trong ERD.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## HeatmapRecord.grid_data_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Ô lưới và giá trị; có schema/version.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## ServiceRequest.request_data_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Dữ liệu riêng các loại không có bảng con; validate theo request_type.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Schedule.input_template_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Input mẫu; resolve điểm/nhóm trước khi tạo job.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Job.input_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** JobParam đã resolve/validate; snapshot bất biến khi dispatch.
- **Đối chiếu ghi chú:** Tab 02: JobParam → Job.input_json; không có entity JobParam. DeliveryItem được snapshot vào input_json theo ghi chú bảng.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Job.context_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Biến /vars của lần chạy.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## Job.result_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Kết quả cuối hợp lệ; không dựng kết quả thành công khi thiếu dữ liệu.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## JobTask.config_snapshot_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Cấu hình task tại lúc tạo job.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## JobStep.resolved_inputs_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Params thực tế đã resolve; immutable khi bước bắt đầu.
- **Đối chiếu ghi chú:** Tab 02 liên hệ với INPUT (params) của handler. Không bổ sung wrapper hoặc key ngoài các mô tả trong ERD.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## JobStep.result_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Data output nguyên hợp đồng, gồm cả các khóa có giá trị null.
- **Đối chiếu ghi chú:** Tab 02 liên hệ với OUTPUT (data) của handler; giữ khóa có giá trị null khi contract yêu cầu.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## JobStep.error_details_json

- **Type / nullability:** `json`; Nullable.
- **Mục đích và dữ liệu được mô tả:** Chi tiết lỗi hợp đồng.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

## JobEvent.event_data_json

- **Type / nullability:** `json`; Not Null.
- **Mục đích và dữ liệu được mô tả:** Payload event theo schema, gồm output thật.
- **Đối chiếu ghi chú:** Tab 02 liên hệ kết quả handler với event_data_json; event envelope chi tiết chưa được liệt kê đầy đủ.
- **Cấu trúc chi tiết:** `Structure not specified in ERD`. Chỉ sử dụng phần cấu trúc/ý nghĩa đã nêu ở trên và trong hợp đồng đính kèm; không tự tạo key, kiểu lồng nhau hoặc giá trị mặc định.

# Database Overview

| Group                                   | Entities                                                                                                                                                       | Role                                                                                                       |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 01 · Tài khoản, nhân sự và tương tác    | `Account`, `StaffProfile`, `Shift`, `Slot`, `HospitalInformation`, `AuditLog`, `SystemSetting`, `VoiceInteraction`, `VoiceCommand`                             | Tài khoản, hồ sơ nhân viên, ca/slot phân công, nội dung bệnh viện, audit/cấu hình và phiên/lệnh giọng nói. |
| 02 · Bản đồ, vùng và điểm đích          | `Map`, `Zone`, `Room`, `Endpoint`, `EndpointGroup`, `EndpointGroupMember`                                                                                      | Phiên bản bản đồ, vùng, phòng, điểm dừng, nhóm điểm và thành viên nhóm.                                    |
| 03 · Định nghĩa Workflow / 11 STEP TYPE | `StepType`, `Workflow`, `Task`, `Step`, `StepTransition`                                                                                                       | Catalog handler, định nghĩa Workflow/Task/Step và cạnh graph StepTransition.                               |
| 04 · Robot, giám sát và cảnh báo        | `Robot`, `RobotStatusHistory`, `RobotLocationHistory`, `DetectionEvent`, `EmergencyIncident`, `Alert`, `AlertRecipient`, `CrowdDensityRecord`, `HeatmapRecord` | Robot, lịch sử vận hành/vị trí, phát hiện/sự cố, thông báo/người nhận và dữ liệu đám đông/heatmap.         |
| 05 · Yêu cầu dịch vụ và xác thực        | `ServiceRequest`, `NavigationRequest`, `EscortRequest`, `DeliveryRequest`, `DeliveryItem`, `AuthVerification`                                                  | Yêu cầu dịch vụ, ba subtype điều hướng/dẫn đường/giao hàng, vật phẩm và grant xác thực.                    |
| 06 · Điều phối và lịch sử thực thi      | `Schedule`, `Job`, `JobTask`, `JobStep`, `JobEvent`                                                                                                            | Lịch sinh việc, lần thực thi Job, task/bước thực thi và event bền vững.                                    |

Phân biệt chính đã có trong ERD: `Workflow/Task/Step/StepTransition` lưu định nghĩa; `Job/JobTask/JobStep/JobEvent` lưu thực thi. `ServiceRequest` là yêu cầu nghiệp vụ và có thể có nhiều Job. `Endpoint` là điểm cụ thể; `EndpointGroup` liên kết nhiều điểm qua `EndpointGroupMember`.

# Embedded ERD Notes and Contracts

Phần này giữ toàn bộ nội dung tab 02. Các tên nguồn S0–S7, tên cũ WorkflowParam/JobParam, ID trên wire và STEP TYPE bên dưới là ghi chú/hợp đồng; không phải các bảng bổ sung. Các câu nói “liệt kê bên dưới” trong nguyên bản không được dùng để tự tạo cấu trúc còn thiếu.

## PHẠM VI VÀ NGUỒN

- Đây là thiết kế logical đề xuất dựa trên tài liệu dự án, không phải schema trích từ database đang chạy.

- S0: MediAssistRover_Giai_thich_Logical_ERD.docx — 31 bảng, 320 field của bản ERD trước; chỉ dùng làm nền để đối chiếu.

- S1: MediAssistRover_10_Workflow_va_Luong_du_lieu_v3.docx — dữ liệu giữa bước, graph v3, vòng lặp và nhánh lỗi.

- S2: FA26SE015_Autonomous Hospital Assistant Robot System for Outpatient Department_NhanDT35.docx — phạm vi nghiệp vụ.

- S3: MediAssistRover_Dac_ta_11_STEP_TYPE (1).docx — ưu tiên cho I/O schema v2, protocol v1 và giới hạn phần cứng.

- S4: MediAssistRover_8_Workflow (1) (1).drawio — đặc biệt luồng chọn một điểm và chọn nhóm điểm.

- S5: Ảnh image(3).png bạn gửi — giữ đủ 36 thực thể. StaffProfile, Shift, Slot, Room, Schedule cần bổ sung field theo giả định được ghi rõ.

- S6: PostgreSQL 18 — Constraints. Đối chiếu PK, FK, UNIQUE, NULL và khóa ghép: https://www.postgresql.org/docs/18/ddl-constraints.html

- S7: draw.io — Crow’s foot notation. Đối chiếu ký hiệu một/nhiều và bắt buộc/tùy chọn: https://www.drawio.com/docs/tutorials/crows-foot-notation/

- Dac_ta_STEP_TYPE_va_WORKFLOW_dong_MediAssistRover (1).docx bản 1.1 có 12 loại; khi mâu thuẫn với S3 thì dùng 11 loại của S3. Không đưa CALL_WORKFLOW vào catalog.

## CHUẨN HÓA VÀ RÀNG BUỘC

- 36 bảng nền được giữ; thêm StepType, StepTransition, AuthVerification, AlertRecipient để có hợp đồng handler, graph có FK, grant có phạm vi và ACK theo người nhận.

- HospitalInformation là nội dung/FAQ, nên Map/Room là tham chiếu tùy chọn của nội dung; không coi đây là hồ sơ Hospital làm cha của bản đồ.

- NavigationRequest/EscortRequest/DeliveryRequest dùng service_request_id vừa PK vừa FK. Khi SUBMITTED, request_type yêu cầu đúng một bảng con phù hợp; các bảng con còn lại không có.

- Room 1-n Endpoint. Room.default_endpoint_group_id chỉ là nhóm chọn mặc định, không phải bảng thành viên. EndpointGroupMember giữ quan hệ n-n Endpoint/EndpointGroup.

- Các nhóm điểm và điểm thành viên phải cùng phiên bản Map. Dùng FK ghép hoặc ràng buộc giao dịch tương đương; FK đơn riêng rẽ chưa đủ.

- Chọn ENDPOINT: không tự đổi đích. Chọn GROUP: chọn thành viên hợp lệ theo policy và giữ snapshot điểm thực tế trong Job.input_json / JobStep.

- Mỗi bước lặp có job_step_id mới. operation_key ổn định trong lượt ghé; các attempt có event append-only.

- Sự cố detector: duy nhất (detection_event_id, incident_type) khi có detector. Incident SOS trực tiếp được phép không có detection.

- Lưu event và cập nhật trạng thái trong một transaction rồi mới EVENT_ACK. EVENT_ACK khác ACK thông báo của nhân viên.

- Các bảng lịch sử tham chiếu version bất biến; dùng RESTRICT/NO ACTION cho xóa cha đã được sử dụng. Không cascade xóa lịch sử Job/incident/audit.

- UK trên field chỉ là duy nhất một cột. UK/FK ghép và điều kiện NULL nằm trong phần quy tắc cuối bảng; cần triển khai bằng constraint/transaction phù hợp DBMS.

## GIỚI HẠN VÀ QUY ƯỚC TRIỂN KHAI

- UUID là lựa chọn khóa kỹ thuật; mã ví dụ như EP_305/USER_01 là minh họa hợp đồng, không phải giá trị seed. Nếu chọn khóa chuỗi thì đổi nhất quán cả PK và FK.

- Kiểu dữ liệu là logic: datetime UTC có múi giờ ở biên API; JSON cần schema riêng. SQL Server/MySQL/PostgreSQL ánh xạ kiểu khi làm physical model.

- Giá trị priority [0,100], số lớn ưu tiên hơn. Confidence [0,1]. Tọa độ mét và yaw radian; không trộn góc độ của tài liệu ERD cũ.

- Shift được hiểu là phân công 1 nhân viên / 1 phòng. Slot là khung thời gian con, không phải lịch hẹn bệnh nhân. Hai định nghĩa này cần chốt nếu nghiệp vụ thực tế khác.

- Job RECOVERY_REQUIRED chưa reconciled_at phải chặn robot nhận việc mới; nhân viên kiểm robot/cửa/hàng trước khi giải phóng. Không tự replay lệnh vật lý.

- Robot hiện không có phản hồi PWM xác nhận khóa, cảm biến cửa/chốt, cân hoặc cảm biến nhiệt; không dùng lệnh đã gửi làm số đo vật lý.

- battery_raw giữ UInt16; battery_percent phải NULL khi chưa xác minh đơn vị/hiệu chuẩn. Tự docking/tự sạc và theo dõi người đi sau không thuộc phạm vi hiện tại.

- Biểu mẫu, params, input_mapping, output, metrics và event là JSON có kiểm schema, không phải một FK giữa các object JSON.

- Sửa workflow PUBLISHED tạo version mới. Không đổi catalog/input contract tại chỗ; kiểm hash và capability trên Pi trước dispatch.

- Danh sách trạng thái ở tooltip của từng field enum. Trạng thái ngoài S3 và các trường quản trị là quyết định thiết kế được đề xuất, cần thống nhất khi viết API.

## ÁNH XẠ JSON VÀ TÊN TRONG TÀI LIỆU

- WorkflowParam.validation_schema_json → Workflow.input_schema_json; WorkflowParam.ui_config_json → Workflow.input_ui_json.

- JobParam → Job.input_json: chụp dữ liệu đã validate trước dispatch. Không tạo thêm bảng EAV WorkflowParam/JobParam song song.

- Step.params_json + input_mapping_json → JobStep.resolved_inputs_json → handler → JobStep.result_json và JobEvent.event_data_json.

- StepTransition là nguồn graph có FK. Workflow.compiled_definition_json là artifact compile, không phải một bản graph tự sửa độc lập.

- contract delivery_id → DeliveryRequest.service_request_id; DeliveryItem.delivery_request_id là FK tới chính khóa đó.

- contract notification_id → Alert.alert_id; delivered/acked được tổng hợp từ AlertRecipient.

- contract actor_user_id → Account.account_id; grant verification_id → AuthVerification.verification_id.

- WAIT correlation_key có thể là incident/request ID theo event_type; đây là tham chiếu đa hình kiểm ở ứng dụng, không vẽ FK giả.

- protocol_version=1, schema_version=2, graph_version=3 là ba lớp phiên bản riêng biệt.

- Trường NULL vẫn xuất hiện trong JSON output nếu S3 yêu cầu. Không thay dữ liệu thiếu bằng 0/false hoặc số đo giả.

## MOVE

### INPUT (params)

| Field           | Contract type | Required in contract | Description                                                                                                              |
| --------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `map_id`        | string        | Có                   | Mã phiên bản bản đồ. Phải khớp bản đồ và hash tài nguyên đã cấu hình trên Pi. Giá trị: độ dài tối đa 2000.               |
| `endpoint_id`   | string        | Có                   | Mã điểm đến. Pi tra tọa độ trên bản đồ đã duyệt; không nhận tọa độ tùy ý từ người dùng. Giá trị: độ dài tối đa 2000.     |
| `mode`          | enum          | Có                   | NORMAL đi thông thường; ESCORT dẫn đường chậm; PATROL đi tới điểm quan sát. Giá trị: NORMAL, ESCORT, PATROL.             |
| `max_speed_mps` | number        | Có                   | Giới hạn tốc độ tịnh tiến, đơn vị m/s. Ví dụ 0.15; không vượt chính sách cục bộ trên Pi. Giá trị: lớn hơn 0; tối đa 0.3. |

### OUTPUT (data)

| Field                  | Contract type    | Required in contract | Description                                                                                                    |
| ---------------------- | ---------------- | -------------------- | -------------------------------------------------------------------------------------------------------------- |
| `reached`              | boolean          | —                    | Nav2 action đạt SUCCEEDED.                                                                                     |
| `endpoint_id`          | string           | —                    | Mã điểm đến. Pi tra tọa độ trên bản đồ đã duyệt; không nhận tọa độ tùy ý từ người dùng. độ dài tối đa 2000.    |
| `action_status`        | integer          | —                    | Mã trạng thái ROS action. 4 là SUCCEEDED; goal được chấp nhận chưa phải đã tới đích.                           |
| `last_feedback_pose`   | object hoặc null | —                    | Feedback cuối dạng {x_m:number, y_m:number, yaw_rad:number} hoặc null; không phải phép đo tư thế cuối độc lập. |
| `distance_remaining_m` | number hoặc null | —                    | Khoảng còn lại theo feedback cuối; null nếu chưa có.                                                           |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## INTERACT

### INPUT (params)

| Field        | Contract type | Required in contract | Description                                                                                     |
| ------------ | ------------- | -------------------- | ----------------------------------------------------------------------------------------------- |
| `action`     | enum          | Có                   | Hành động trong loại step. Giá trị: SPEAK, DISPLAY, CAPTURE_INPUT, CONFIRM.                     |
| `language`   | string        | Có                   | Ngôn ngữ backend/UI/TTS đã hỗ trợ. Giá trị: độ dài tối đa 2000.                                 |
| `text`       | string        | Không                | Nội dung tùy chọn thay template. Giá trị: độ dài tối đa 2000.                                   |
| `prompt_key` | string        | Có                   | Mã nội dung hoặc biểu mẫu trên server. Bắt buộc kể cả khi có text. Giá trị: độ dài tối đa 2000. |
| `data`       | object        | Có                   | Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới.   |

### OUTPUT (data)

| Field           | Contract type     | Required in contract | Description                                                                                                 |
| --------------- | ----------------- | -------------------- | ----------------------------------------------------------------------------------------------------------- |
| `completed`     | boolean           | —                    | Tác vụ đã hoàn tất theo adapter. Với CONFIRM, còn phải đọc confirmed để biết người dùng đồng ý hay từ chối. |
| `confirmed`     | boolean hoặc null | —                    | true là đồng ý, false là từ chối; null khi thao tác không hỏi xác nhận.                                     |
| `input_data`    | object            | —                    | Dữ liệu người dùng cung cấp trong tương tác.                                                                |
| `actor_user_id` | string hoặc null  | —                    | Mã người thao tác do server xác thực. Tương tác không yêu cầu danh tính có thể trả null.                    |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## DATA_SERVICE

### INPUT (params)

| Field         | Contract type | Required in contract | Description                                                                                                           |
| ------------- | ------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `service_key` | string        | Có                   | Khóa registry backend. Giá trị: độ dài tối đa 2000.                                                                   |
| `operation`   | enum          | Có                   | Thao tác được service registry cho phép. Server phải kiểm cả service_key và resource. Giá trị: QUERY, CREATE, UPDATE. |
| `resource`    | string        | Có                   | Tài nguyên nghiệp vụ trong registry. Giá trị: độ dài tối đa 2000.                                                     |
| `request`     | object        | Có                   | Dữ liệu gửi dịch vụ nghiệp vụ, đã thay các tham chiếu bằng giá trị thực.                                              |

### OUTPUT (data)

| Field        | Contract type | Required in contract | Description                                                                                   |
| ------------ | ------------- | -------------------- | --------------------------------------------------------------------------------------------- |
| `data`       | object        | —                    | Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới. |
| `request_id` | string        | —                    | Mã yêu cầu tại dịch vụ nghiệp vụ; dùng để đối chiếu giao dịch. độ dài tối đa 2000.            |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## AUTHENTICATE

### INPUT (params)

| Field                 | Contract type | Required in contract | Description                                                                                       |
| --------------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------- |
| `method`              | enum          | Có                   | QR_CODE/STAFF_CONFIRMATION; không có FACE_ID trong bản này. Giá trị: QR_CODE, STAFF_CONFIRMATION. |
| `required_permission` | string        | Có                   | Quyền backend phải kiểm. Giá trị: độ dài tối đa 2000.                                             |
| `purpose`             | string        | Có                   | Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. Giá trị: độ dài tối đa 2000.     |

### OUTPUT (data)

| Field             | Contract type | Required in contract | Description                                                                                       |
| ----------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------- |
| `authenticated`   | boolean       | —                    | Backend đã kiểm danh tính/quyền/phạm vi.                                                          |
| `verification_id` | string        | —                    | Mã lần xác thực; mở khóa phải dùng kết quả cùng job, đúng purpose và còn hạn. độ dài tối đa 2000. |
| `actor_user_id`   | string        | —                    | Mã nhân viên đã được server xác thực; bắt buộc là string và không được null. độ dài tối đa 2000.  |
| `valid_until`     | string        | —                    | Thời điểm hết hạn xác thực, ISO 8601 có múi giờ UTC. date-time.                                   |
| `purpose`         | string        | —                    | Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. độ dài tối đa 2000.              |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## CONTROL_HARDWARE

### INPUT (params)

| Field               | Contract type | Required in contract | Description                                                                                                |
| ------------------- | ------------- | -------------------- | ---------------------------------------------------------------------------------------------------------- |
| `device_id`         | enum          | Có                   | cargo_lock, ánh xạ servo MG996R S6 ID4 ở local. Giá trị: cargo_lock.                                       |
| `action`            | enum          | Có                   | Hành động trong loại step. Giá trị: LOCK, UNLOCK.                                                          |
| `verification_mode` | enum          | Có                   | COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. Giá trị: COMMAND_ONLY.       |
| `verification_id`   | string        | Khi UNLOCK           | Mã lần xác thực; mở khóa phải dùng kết quả cùng job, đúng purpose và còn hạn. Giá trị: độ dài tối đa 2000. |
| `purpose`           | string        | Khi UNLOCK           | Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. Giá trị: độ dài tối đa 2000.              |

### OUTPUT (data)

| Field             | Contract type | Required in contract | Description                                                                                                         |
| ----------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `published`       | hằng true     | —                    | Đã gửi thông điệp ROS tới topic điều khiển.                                                                         |
| `controller_ack`  | hằng false    | —                    | false: driver hiện tại chưa cung cấp phản hồi xác nhận lệnh PWM.                                                    |
| `verified`        | hằng false    | —                    | false: chưa có cảm biến xác nhận cửa hoặc chốt khóa.                                                                |
| `commanded_state` | enum          | —                    | LOCK/UNLOCK được yêu cầu, không phải trạng thái vật lý đo được. LOCK, UNLOCK.                                       |
| `position_raw`    | integer       | —                    | Giá trị gửi nguyên trạng cho driver: LOCK là 1500; UNLOCK là 0. Không dùng làm số đo góc. tối thiểu 0; tối đa 2500. |
| `ros_servo_id`    | hằng 4        | —                    | 4 tương ứng S6 trong image/source đã đối chiếu.                                                                     |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## HANDLE_PAYLOAD

### INPUT (params)

| Field               | Contract type | Required in contract | Description                                                                                             |
| ------------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------------- |
| `action`            | enum          | Có                   | Hành động trong loại step. Giá trị: LOAD, UNLOAD, VERIFY.                                               |
| `delivery_id`       | string        | Có                   | Tham chiếu yêu cầu giao hàng. Giá trị: độ dài tối đa 2000.                                              |
| `items`             | array         | Có                   | Mảng vật phẩm. Mỗi phần tử có item_code và quantity là số nguyên dương. Giá trị: ít nhất số phần tử 1.  |
| `verification_mode` | enum          | Có                   | COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. Giá trị: STAFF_CONFIRMED. |

### OUTPUT (data)

| Field               | Contract type | Required in contract | Description                                                                                      |
| ------------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------ |
| `confirmed`         | boolean       | —                    | true khi nhân viên xác nhận vật phẩm và số lượng đúng; false khi từ chối xác nhận.               |
| `verification_mode` | enum          | —                    | COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. STAFF_CONFIRMED.   |
| `actor_user_id`     | string        | —                    | Mã nhân viên đã được server xác thực; bắt buộc là string và không được null. độ dài tối đa 2000. |
| `items`             | array         | —                    | Mảng vật phẩm. Mỗi phần tử có item_code và quantity là số nguyên dương.                          |
| `weight_kg`         | null          | —                    | null: chưa có cân.                                                                               |
| `temperature_c`     | null          | —                    | null: chưa có sensor nhiệt độ.                                                                   |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## EXECUTE_SENSING

### INPUT (params)

| Field        | Contract type | Required in contract | Description                                                                                                                             |
| ------------ | ------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `task_type`  | enum          | Có                   | Loại đo/nhận biết; schema metrics phụ thuộc task_type. Giá trị: ROBOT_HEALTH, SIDE_CLEARANCE, CROWD_DENSITY, RESTRICTED_AREA_INTRUSION. |
| `zone_id`    | string        | Tác vụ ảnh           | Mã vùng quan sát. Dịch vụ hình ảnh phải ánh xạ vùng này với camera và bản đồ. Giá trị: độ dài tối đa 2000.                              |
| `window_sec` | number        | Không                | Khoảng lấy mẫu cho tác vụ hình ảnh, đơn vị giây. ROBOT_HEALTH và SIDE_CLEARANCE trả snapshot gần nhất. Giá trị: tối thiểu 0; tối đa 30. |

### OUTPUT (data)

| Field          | Contract type    | Required in contract | Description                                                                                                                    |
| -------------- | ---------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `task_type`    | enum             | —                    | Loại đo/nhận biết; schema metrics phụ thuộc task_type. ROBOT_HEALTH, SIDE_CLEARANCE, CROWD_DENSITY, RESTRICTED_AREA_INTRUSION. |
| `metrics`      | object           | —                    | Kết quả đo hoặc phân tích, có cấu trúc riêng theo task_type.                                                                   |
| `captured_at`  | string           | —                    | Thời điểm lấy mẫu hoặc tổng hợp kết quả. Độ mới từng cảm biến xem trong metrics. date-time.                                    |
| `evidence_ref` | string hoặc null | —                    | Tham chiếu chứng cứ; null nếu không lưu.                                                                                       |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## NOTIFY

### INPUT (params)

| Field               | Contract type | Required in contract | Description                                                                                   |
| ------------------- | ------------- | -------------------- | --------------------------------------------------------------------------------------------- |
| `notification_type` | string        | Có                   | Loại thông báo nghiệp vụ. Giá trị: độ dài tối đa 2000.                                        |
| `severity`          | enum          | Có                   | INFO/WARNING/CRITICAL. Giá trị: INFO, WARNING, CRITICAL.                                      |
| `recipients`        | array         | Có                   | Nhóm/người nhận backend resolve và kiểm quyền. Giá trị: ít nhất số phần tử 1.                 |
| `data`              | object        | Có                   | Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới. |
| `require_ack`       | boolean       | Có                   | Yêu cầu người nhận xác nhận.                                                                  |

### OUTPUT (data)

| Field             | Contract type | Required in contract | Description                                                        |
| ----------------- | ------------- | -------------------- | ------------------------------------------------------------------ |
| `notification_id` | string        | —                    | Mã thông báo đã tạo trên server. độ dài tối đa 2000.               |
| `delivered`       | boolean       | —                    | Kết quả giao thông báo.                                            |
| `acked`           | boolean       | —                    | Người nhận đã xác nhận tiếp nhận; khác EVENT_ACK của đường truyền. |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## WAIT

### INPUT (params)

| Field             | Contract type | Required in contract | Description                                                                                         |
| ----------------- | ------------- | -------------------- | --------------------------------------------------------------------------------------------------- |
| `wait_type`       | enum          | Có                   | DURATION hoặc EVENT. Giá trị: DURATION, EVENT.                                                      |
| `duration_sec`    | number        | Theo chế độ          | Thời gian chờ trong DURATION. Giá trị: tối thiểu 0; tối đa 3600.                                    |
| `event_type`      | string        | Theo chế độ          | Loại event chờ. Giá trị: độ dài tối đa 2000.                                                        |
| `correlation_key` | string        | Theo chế độ          | Mã nghiệp vụ ràng buộc sự kiện, ví dụ incident_id hoặc mã yêu cầu sạc. Giá trị: độ dài tối đa 2000. |

### OUTPUT (data)

| Field         | Contract type    | Required in contract | Description                                                 |
| ------------- | ---------------- | -------------------- | ----------------------------------------------------------- |
| `completed`   | boolean          | —                    | Đã hết khoảng chờ DURATION hoặc đã nhận đúng sự kiện EVENT. |
| `wake_reason` | string           | —                    | Nguyên nhân kết thúc chờ. độ dài tối đa 2000.               |
| `elapsed_sec` | number           | —                    | Thời gian chờ đo tại executor.                              |
| `event`       | object hoặc null | —                    | Event đúng type/correlation; null với DURATION.             |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## CHECK_CONDITION

### INPUT (params)

| Field        | Contract type | Required in contract | Description                                                                |
| ------------ | ------------- | -------------------- | -------------------------------------------------------------------------- |
| `expression` | expression    | Có                   | Biểu thức SAFE_EXPR_V1. Dùng var, op, args; không nhận chuỗi mã lập trình. |

### OUTPUT (data)

| Field   | Contract type | Required in contract | Description                |
| ------- | ------------- | -------------------- | -------------------------- |
| `value` | boolean       | —                    | Kết quả boolean điều kiện. |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

## SET_CONTEXT

### INPUT (params)

| Field         | Contract type | Required in contract | Description                                                                                       |
| ------------- | ------------- | -------------------- | ------------------------------------------------------------------------------------------------- |
| `assignments` | object        | Có                   | Tên biến và giá trị hoặc biểu thức cần gán vào /vars. Mỗi biểu thức đọc trạng thái trước lần gán. |

### OUTPUT (data)

| Field          | Contract type | Required in contract | Description           |
| -------------- | ------------- | -------------------- | --------------------- |
| `changed_keys` | array         | —                    | Các biến vars đã sửa. |
| `values`       | object        | —                    | Giá trị mới.          |

Các kiểu/độ dài/hằng ở đây là hợp đồng I/O được ghi trong tab 02, không phải kiểu hoặc default mới cho cột database.

# ERD Review Notes

Các nhận xét dưới đây tách khỏi schema chính; không có field, entity, FK hoặc cardinality nào được chỉnh sửa trong quá trình chuyển đổi. Không phát hiện xung đột giữa 110 đầu nối với nullability/unique của field tương ứng. Chưa đủ căn cứ xác nhận một lỗi Critical chỉ từ ERD.

| ID   | Severity  | Issue                                                                                 | Why it may matter                                                                                                                                              | Suggested follow-up                                                                                                                       |
| ---- | --------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| RN01 | Important | Một số FK ghép mới được mô tả một phần.                                               | EndpointGroupMember nêu hai tuple nguồn nhưng không ghi tuple đích đầy đủ; StepTransition chỉ nêu yêu cầu from/to cùng Workflow.                               | Chốt đầy đủ tuple nguồn/đích và constraint của physical model. Không triển khai chỉ các FK đơn rồi cho rằng đã đảm bảo cùng Map/Workflow. |
| RN02 | Important | Quy tắc cùng Job/Task/Workflow cần cơ chế thực thi ngoài FK đơn.                      | JobTask, JobStep, JobEvent và AuthVerification có ghi chú nhất quán liên bảng; các đường đơn riêng lẻ chỉ xác nhận từng đối tượng tồn tại.                     | Chốt cơ chế constraint hoặc kiểm tra giao dịch cho đúng các quy tắc đã ghi. Tài liệu này không tự bổ sung FK/cột để thực hiện.            |
| RN03 | Important | 1:1 với subtype có điều kiện theo trạng thái.                                         | Ba đường từ ServiceRequest cho tối đa 0..1 mỗi subtype; riêng các đường này không thể hiện quy tắc chỉ đúng một subtype phù hợp khi SUBMITTED.                 | Giữ optionality như sơ đồ; kiểm request_type, trạng thái và sự tồn tại/loại trừ các subtype khi chuyển trạng thái.                        |
| RN04 | Important | Tối thiểu một DeliveryItem là điều kiện nghiệp vụ, không nằm trên đầu nối.            | Đường DeliveryRequest → DeliveryItem là 0..N, nhưng ghi chú yêu cầu >= 1 trước SUBMITTED/dispatch.                                                             | Kiểm số lượng dòng ở thời điểm chuyển trạng thái; không tự đổi toàn bộ quan hệ thành 1..N cho mọi trạng thái.                             |
| RN05 | Important | Cấu trúc JSON chưa đủ để sinh implementation một cách xác định.                       | 37 cột JSON có ý nghĩa và một phần hợp đồng I/O, nhưng không có toàn bộ JSON Schema, cấu trúc metrics, event envelope, form hoặc binding grammar.              | Bổ sung đặc tả schema JSON/version theo từng field và handler trong bước thiết kế tiếp theo; không tạo key dựa trên tên field.            |
| RN06 | Important | Một số ràng buộc nghiệp vụ mới ở dạng ghi chú.                                        | Giữ robot bởi Job đang chạy/recovery, chống chồng Shift/Slot, một default transition, chống vòng recovery và các unique có điều kiện chưa được mô tả bằng DDL. | Chốt cách thực thi và tính nguyên tử khi làm physical model; bảo toàn điều kiện hiện có, không coi từng FK riêng lẻ là đủ.                |
| RN07 | Important | Tên cột JobStep.job_id xuất hiện trong một ghi chú phủ định nhưng không có field này. | Ghi chú viết “Không UNIQUE(job_id, step_id)”; JobStep chỉ có job_task_id và truy Job qua JobTask. Đây không phải khai báo field hoặc constraint mới.           | Làm rõ ghi chú là cảnh báo về vòng lặp ở mức Job. Không tự thêm JobStep.job_id hoặc tạo UNIQUE theo câu phủ định.                         |
| RN08 | Important | Giả định Shift/Slot chưa được chốt theo nghiệp vụ.                                    | Tab 02 ghi rõ Shift là phân công một nhân viên/một phòng, Slot là khung thời gian con, không phải lịch hẹn bệnh nhân.                                          | Xác nhận nghĩa này trước khi triển khai. Giữ nguyên field và quan hệ trong bản chuyển đổi hiện tại.                                       |
| RN09 | Optional  | Mặc định và kiểu dữ liệu chưa là đặc tả physical database.                            | Chỉ 7 field có default rõ bằng lời; enum/datetime UTC/json và cách cấp UUID chưa ánh xạ một DBMS.                                                              | Chọn DBMS và chốt SQL default/constraint trong tài liệu riêng. Không coi Unclear from ERD là NULL, false hoặc CURRENT_TIMESTAMP.          |
| RN10 | Optional  | Nhất quán Room và nhóm đích mặc định chưa được quy định đầy đủ.                       | Room.default_endpoint_group_id có FK nhưng ghi chú không yêu cầu rõ nhóm phải có endpoint của chính Room; ERD chỉ gọi đây là nhóm mặc định.                    | Xác nhận có cần ràng buộc thành viên theo Room hay không. Đây là câu hỏi thiết kế, không phải khẳng định FK hiện tại sai.                 |
| RN11 | Optional  | Liên kết N:N qua bản ghi nghiệp vụ không luôn tương đương junction hai cột.           | AlertRecipient phân biệt channel; JobTask lưu lần thực thi; Shift có thời gian; JobStep có lượt ghé.                                                           | Giữ các khóa/ràng buộc và dữ liệu nghiệp vụ hiện tại; không rút gọn các bảng này thành cặp FK hoặc thêm pair-unique không có trong ERD.   |

### Extraction verification

- Đọc đủ 2 tab: 01 Logical ERD — 40 thực thể; 02 Quy ước, nguồn và JSON I-O.
- Đối chiếu đủ 40 bảng và 550 field theo từng cell của file nguồn.
- Đối chiếu 110 field FK với 110 đường nối: nguồn, đích, tooltip tham chiếu, kiểu dữ liệu và marker cardinality.
- Có 41 field đánh dấu PK: 39 khóa chính đơn và 1 khóa chính ghép hai cột tại EndpointGroupMember.
- Có 19 field gắn nhãn UK riêng; unique ghép/điều kiện được giữ trong Constraints.
- Có 37 field JSON, 7 default được nêu rõ và đủ 15 khối ghi chú/hợp đồng ở tab 02.
- Không thêm/xóa bảng hoặc field; không đổi tên, kiểu, nullability, khóa hoặc đầu nối. Những tham chiếu thiếu chi tiết được giữ là Unclear from ERD.
