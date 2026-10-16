# Phase 0 baseline — Patient và Staff API

> Baseline ID: `patient-staff-phase0-2026-10-15`  
> Git revision: `21bfdbc7c0ba5cd8e9e7b157b5e915663760e0b1` (`thanh`)  
> Phạm vi snapshot: source code trước khi triển khai Phase 1.

## 1. Nguồn đầu vào đã khóa

| Nguồn | SHA-256 | Kết quả |
|---|---|---|
| `Context + Swimlane-Swimlane.drawio.png` | `08B0375B2ED732702BFC7048B912D2B39455B68BF10E4A5CBFF6ACA7EECFA7BB` | Đã đối chiếu |
| `MediAssistRover_Logical_ERD_Full_Fields (3).drawio` | `0F910CD253102B7EDB15B28A24E002DF9D54476AE37A5F69D0FA4F01AC7BE12F` | Đã đối chiếu |
| `MediAssistRover_ERD (1) (2).drawio` | `8F3803B538BE33BD10021066CC62F20F96041B91A2B2007B2B87C7E995650805` | Đã đối chiếu |
| `FA26SE015_Autonomous Hospital Assistant Robot System for Outpatient Department_NhanDT35(1).docx` | `54302C56F2A1D3196B0157FE8BBBF28BF1EE99DC9EF79D707A317F46FB0D8E3B` | Đã đối chiếu; lần re-hash cuối Phase 0 bị khóa file do tài liệu đang mở, giá trị giữ từ lần đọc thành công trước đó |

Thứ tự ưu tiên và các deviation bắt buộc được khóa tại [mục 2 của kế hoạch](../patient-staff-api-phased-plan.md#2-nguồn-và-tính-tái-lập). Không có `Role.PATIENT`, bảng Patient, Patient token hoặc Patient registration; `StepType` tiếp tục là enum; `patient_reference` không phải FK; server tự điều phối robot duy nhất.

## 2. Baseline kỹ thuật

| Hạng mục | Baseline trước Phase 1 |
|---|---|
| Runtime | Java 21, Spring Boot 3.5.16, Maven 3.9.9 |
| Database | MySQL 8.4, UUID `BINARY(16)`, Flyway forward-only |
| Schema | 30 migration, `V1` đến `V30`; 59 JPA entity |
| HTTP surface | 186 path, 226 operation trong OpenAPI |
| Controller source | 38 controller trước Phase 1 |
| Test source | 56 file test trước Phase 1 |
| OpenAPI snapshot | [`openapi-phase0.json`](./openapi-phase0.json) |
| OpenAPI SHA-256 | `58F982FA12056FC1761EFBC06C948EA17A31C20E2D9DA66B5882DAEC45D1AAFF` |

Snapshot OpenAPI là JSON trả trực tiếp từ `GET /v3/api-docs` của ứng dụng chạy trên fresh database tại revision nêu trên. File này là đầu vào diff breaking-change ở Phase 9, không phải contract mục tiêu sau cùng.

## 3. Inventory endpoint trong phạm vi

Các bảng dưới đây là inventory route hiện hữu. Endpoint cần thêm hoặc cần harden được mô tả đầy đủ method, actor, input, output, scope và acceptance tại Phase 1–8 của tài liệu kế hoạch.

### 3.1. Credential exchange và Patient public catalog

| Actor/Auth hiện tại | Endpoint hiện hữu | Phase xử lý |
|---|---|---|
| Anonymous → Staff/Admin token | `POST /api/v1/auth/login`, `POST /refresh`, `POST /logout` | 1 |
| Staff/Admin JWT | `GET /api/v1/auth/me`, `PUT /change-password` | 1 |
| Anonymous Patient UI | `GET /api/v1/public/hospital-information`, `/floors`, `/zones`, `/rooms`, `/endpoints` | 2 |

### 3.2. Patient qua Device JWT

| Luồng | Endpoint hiện hữu | Phase xử lý |
|---|---|---|
| Information/navigation/escort | `POST /api/v1/pi/requests/information`, `/navigation`, `/escort` | 2–3 |
| Voice history contract | `POST /api/v1/pi/voice/interactions`, `/interactions/{interactionId}/completion`, `/commands`, `/commands/{commandId}/result` | 2 |
| SOS | `POST /api/v1/pi/incidents/sos` | 4 |

Các Device endpoint là dependency runtime trực tiếp nhưng không phải Patient/Staff business API mục tiêu: `/api/v1/pi/auth/token`, `/catalog/**`, `/commands/**`, `/heartbeat`, `/telemetry/location`, `/jobs/**`, `/job-steps/**`, `/step-services/execute`, `/maps/**`, `/mapping-*/**`, `/monitoring/**` và `/workflows/**`. Chúng vẫn nằm trong OpenAPI snapshot để phát hiện thay đổi ngoài ý muốn.

### 3.3. Staff JWT

| Nhóm | Endpoint hiện hữu dành cho Staff | Phase xử lý |
|---|---|---|
| Shift | `GET /api/v1/staff/shifts`, `GET /api/v1/staff/shifts/{shiftId}` | 5 |
| Facility read | `GET /api/v1/hospital-information[/{id}]`, `/floors[/{id}]`, `/maps[/{id}]`, `/maps/{id}/artifact-metadata`, `/zones[/{id}]`, `/rooms[/{id}]`, `/endpoints[/{id}]`, `/endpoint-groups[/{id}]`, `/endpoint-group-members` | 5 |
| Article read | `GET /api/v1/hospital-information/{hospitalId}/articles`, `GET .../{articleId}` | 5 |
| Voice history | `GET /api/v1/voice/interactions[/{id}]`, `GET /api/v1/voice/commands[/{id}]` | 5 |
| Workflow catalog compatibility | `GET /api/v1/admin/workflows/step-types`, `/published/all`, `/published/{id}` | 6 |
| Tạo ServiceRequest | `POST /api/v1/information-requests`, `/navigation-requests`, `/escort-requests`, `/operational-requests` | 6 |
| ServiceRequest lifecycle | `GET /api/v1/service-requests[/{id}]`, `POST .../{id}/submit`, `POST .../{id}/cancel` | 6 |
| Job | `GET /api/v1/jobs[/{id}]`, `GET .../{id}/events`, `GET .../{id}/stream`, `POST .../{id}/cancel`, `/reconcile`, `/events`, `GET .../{jobId}/mapping-session` | 6, 8 |
| Robot | `GET /api/v1/robots[/{id}]`, `GET .../{id}/status-history`, `/location-history`, `POST .../{id}/reconcile` | 6 |
| Schedule | `POST/GET /api/v1/schedules`, `GET /{id}`, `POST /{id}/activate`, `/pause`, `/retire` | 6 |
| Delivery | `POST /api/v1/delivery-requests`, `GET /{id}`, `POST /{id}/confirm-pickup`, `/confirm-delivery` | 7 |
| Delivery verification | `POST /api/v1/jobs/{jobId}/auth-verifications`, `/qr-challenges`, `/qr-verifications`, `GET /api/v1/auth-verifications/{id}` | 7 |
| Physical response | `POST /api/v1/job-steps/{id}/responses` | 7 |
| Alert | `GET /api/v1/alerts[/{id}]`, `POST /{id}/acknowledge`, `/resolve`, `/close` | 8 |
| Emergency | `GET /api/v1/emergency-incidents[/{id}]`, `POST /{id}/acknowledge`, `/start`, `/resolve`, `/close`, `/false-alarm` | 8 |
| Recovery | `GET /api/v1/jobs/{jobId}/operations/recovery`, `POST /api/v1/jobs/{jobId}/operations/{jobStepId}/reconcile` | 8 |

`POST /api/v1/jobs/{id}/assign-robot` và `/assign-staff` là Admin-only nên được ghi nhận trong raw snapshot nhưng không thuộc Staff API mục tiêu. Tương tự, mọi Facility mutation và `/api/v1/admin/**` mutation không nằm trong inventory Staff vận hành.

## 4. Ma trận nguồn → Backend contract

| Yêu cầu từ hồ sơ/swimlane | Entity/workflow hiện hành | API/contract | Disposition |
|---|---|---|---|
| Patient hỏi thông tin bệnh viện | HospitalInformation, HospitalInformationArticle, VoiceInteraction, VoiceCommand, `WF_INFORMATION_ASSISTANCE` | Public catalog, `/pi/requests/information`, `/pi/voice/**` | Backend cung cấp nội dung đã duyệt, source metadata và fallback; không chạy STT/TTS/LLM |
| Patient chọn đích và xác nhận dẫn đường | ServiceRequest, NavigationRequest, Job/JobStep, `WF_NAVIGATION` | `/pi/requests/navigation`; cần status/cancel | Backend validate/queue/dispatch; Pi/ROS2 chịu trách nhiệm chuyển động |
| Patient escort | ServiceRequest, EscortRequest, Job, `WF_PATIENT_ESCORT` | `/pi/requests/escort`; dùng chung status/cancel | `patient_reference` optional; không suy ra patient account/person tracking |
| Patient SOS | EmergencyIncident, Alert/Recipient, ServiceRequest, Job, `WF_EMERGENCY_SOS_RESPONSE` | `/pi/incidents/sos`; cần incident status | Incident lifecycle tách khỏi Alert ACK; không tự resume nhiệm vụ cũ |
| Staff đăng nhập và đổi mật khẩu | Account, RefreshToken, RevokedToken | `/auth/**` | Contract hiện hữu giữ nguyên; Patient không dùng auth này |
| Staff xem ca trực | StaffProfile, Shift, Slot, Room | `/staff/shifts/**`; cần `/staff/me` | Chỉ dữ liệu của principal; active shift là một nhánh operational scope |
| Staff tạo và theo dõi task | ServiceRequest subtype, Job/Event, Robot, Schedule | request/job/robot/schedule API | Server tự chọn robot duy nhất; Staff không assign robot |
| Delivery hai đầu và QR | DeliveryRequest/Item, AuthVerification, JobStepResponse | delivery/verification/response API | Schema 3 dùng response vật lý tách biệt; legacy confirmation chỉ compatibility |
| Alert/emergency/recovery | Alert/Recipient, EmergencyIncident, JobEvent, recovery receipt | alert/emergency/job-event/recovery API | Phân biệt transport ACK, alert ACK và incident resolution |
| Obstacle avoidance, servo, sensor, Nav2 | Không phải Backend business entity | Event/receipt boundary duy nhất | Pi/ROS2/hardware nằm ngoài phạm vi triển khai Backend |

## 5. Kết quả gate Phase 0

| Gate | Lệnh/bằng chứng | Kết quả |
|---|---|---|
| Source hash | `Get-FileHash -Algorithm SHA256` | PASS cho PNG và hai Draw.io; DOCX dùng hash đã đọc thành công trước đó vì file đang mở |
| OpenAPI baseline | Chạy app trên fresh MySQL, gọi `GET /v3/api-docs` | PASS — 186 path, 226 operation |
| Fresh database | Testcontainers `mysql:8.4` từ schema rỗng | PASS — validate và apply đủ 30 migration đến `v30` |
| Hibernate validation | `spring.jpa.hibernate.ddl-auto=validate` trong integration context | PASS |
| Regression trước Phase 1 | `mvn test` bằng JBR 21.0.6 | PASS — 483 test, 0 failure, 0 error, 7 skipped, 7 phút 22 giây |
| Worktree trước Phase 1 | `git status --short` | Chỉ có tài liệu kế hoạch chưa tracked; không có code/migration thay đổi |

Ghi chú môi trường: `JAVA_HOME` mặc định của shell là JDK 17 và không đáp ứng `release 21`; baseline pass khi chạy bằng `C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr` (Java 21.0.6). Flyway phát cảnh báo phiên bản thư viện hiện tại công bố hỗ trợ đến MySQL 8.1, nhưng validation/migration trên MySQL 8.4 đã pass.

## 6. Gate chuyển Phase 1

- Source precedence, deviation và boundary đã đóng băng.
- Endpoint hiện hữu có raw OpenAPI snapshot và mapping tới phase mục tiêu.
- Fresh schema, Hibernate validation và regression đều xanh.
- Mọi thay đổi sau baseline phải additive hoặc có compatibility note/deprecation window.

Phase 0: **COMPLETE**.
