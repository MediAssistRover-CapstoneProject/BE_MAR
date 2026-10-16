# Kế hoạch triển khai toàn bộ API Patient và Staff

> Trạng thái tài liệu: Phase 0–1 đã hoàn tất; Phase 2–9 đã triển khai và đang chờ gate MySQL 8.4/full regression.
> Cập nhật: 2026-10-16. Phạm vi: Backend Java/Spring Boot của MediAssistRover.

## 1. Mục tiêu và cách đọc

Tài liệu này là backlog thực thi cho toàn bộ luồng Patient và Staff trong phạm vi Backend. Mỗi endpoint được gắn một trong bốn trạng thái:

| Trạng thái | Ý nghĩa |
|---|---|
| `KEEP` | Endpoint đã tồn tại và contract hiện tại phù hợp với phạm vi mục tiêu. |
| `HARDEN` | Endpoint đã tồn tại nhưng cần bổ sung validation, filter, privacy, authorization hoặc contract. |
| `ADD` | Endpoint chưa tồn tại và cần được triển khai. |
| `COMPAT` | Endpoint chỉ được giữ để tương thích client/Workflow cũ; không dùng cho tích hợp mới. |

Tài liệu phân biệt rõ ba lớp:

1. **Patient** là khách sử dụng màn hình/voice gắn trên robot, không có Account hay JWT cá nhân.
2. **Robot UI/Pi** đại diện Patient khi gọi Backend và xác thực bằng Device JWT.
3. **Staff** có Account loại `STAFF`, đăng nhập bằng user JWT và chỉ thao tác trong operational scope được cấp.

Các phase là thứ tự triển khai. Một phase chỉ được đánh dấu hoàn tất khi đạt Definition of Done ở mục 17.

## 2. Nguồn và tính tái lập

| Ưu tiên | Nguồn | SHA-256 | Cách sử dụng |
|---:|---|---|---|
| 1 | Quyết định đã chốt trong phiên lập kế hoạch | Không áp dụng | Chốt Patient không có Account; Staff bị giới hạn theo assignment/ca trực; hiện chỉ có một robot. |
| 2 | `Context + Swimlane-Swimlane.drawio.png` | `08B0375B2ED732702BFC7048B912D2B39455B68BF10E4A5CBFF6ACA7EECFA7BB` | Nguồn hành vi cho Patient guidance, voice, delivery, SOS và obstacle recovery. |
| 3 | Source code, Flyway và test hiện hành | Theo Git revision | Nguồn sự thật cho endpoint đã có, schema đang chạy và hành vi tương thích. |
| 4 | `MediAssistRover_Logical_ERD_Full_Fields (3).drawio` | `0F910CD253102B7EDB15B28A24E002DF9D54476AE37A5F69D0FA4F01AC7BE12F` | Nguồn tham chiếu field, quan hệ và constraint logical. Không tự động thay schema đang chạy. |
| 5 | `MediAssistRover_ERD (1) (2).drawio` | `8F3803B538BE33BD10021066CC62F20F96041B91A2B2007B2B87C7E995650805` | ERD tổng quan để đối chiếu quan hệ. |
| 6 | `FA26SE015_Autonomous Hospital Assistant Robot System for Outpatient Department_NhanDT35(1).docx` | `54302C56F2A1D3196B0157FE8BBBF28BF1EE99DC9EF79D707A317F46FB0D8E3B` | Nguồn phạm vi chức năng Patient và Staff. |

### 2.1. Quy tắc giải quyết xung đột

- Quyết định nghiệp vụ đã chốt trong tài liệu này cao hơn các giá trị đề xuất trong ERD.
- `Account.account_type = PATIENT` trong Logical ERD **không được triển khai**. Account vẫn chỉ có `ADMIN` và `STAFF`.
- Không thêm bảng Patient. `EscortRequest.patient_reference` là mã ngoài hệ thống, nullable, không phải FK.
- Không tạo bảng/CRUD `StepType`; 11 StepType tiếp tục là enum và contract cố định trong source.
- Java 21, Spring Boot, MySQL, Flyway và UUID `BINARY(16)` là stack đã chốt; nội dung .NET/SQL Server trong hồ sơ đề xuất không thay stack.
- Không sửa migration đã chạy. Mọi thay đổi schema về sau phải là forward migration mới.
- Không diễn giải một hộp trong swimlane thành bằng chứng phần cứng đã có hoặc thành StepType mới.

## 3. Ranh giới Backend

Backend chịu trách nhiệm:

- xác thực Device/Staff, authorization và scope;
- validate request, map/room/endpoint/group và dữ liệu workflow;
- tạo ServiceRequest, Job, Alert, EmergencyIncident và lịch sử;
- điều phối một robot, queue, dispatch, cancellation, recovery và reconciliation;
- lưu event trước khi ACK, chống trùng, audit và phát SSE cho UI;
- cung cấp contract cho voice, navigation, delivery, SOS và Staff response.

Backend không chịu trách nhiệm triển khai:

- STT, TTS, LLM/RAG model hoặc xử lý ảnh AI;
- ROS2/Nav2, SLAM, path planning, obstacle avoidance hoặc patient-following;
- PWM/servo, điều khiển motor, cảm biến cửa/chốt, cân hoặc nhiệt độ;
- tự docking/tự sạc;
- giao diện Patient/Staff.

Backend chỉ ghi nhận kết quả do adapter được tin cậy gửi lên; không biến “đã phát lệnh” thành “trạng thái vật lý đã được xác minh”.

## 4. Ma trận truy vết nghiệp vụ

| Actor/luồng | Yêu cầu nguồn | Entity/Workflow chính | API mục tiêu | Test/acceptance chính |
|---|---|---|---|---|
| Patient information | Hỏi vị trí, quy trình, phòng, lab, pharmacy; nhận fallback an toàn | HospitalInformation, HospitalInformationArticle, VoiceInteraction, VoiceCommand, `WF_INFORMATION_ASSISTANCE` | Public catalog, `/pi/requests/information`, `/pi/voice/**` | Bài private/draft/hết hạn không xuất hiện; answer có source hoặc fallback. |
| Patient navigation | Chọn đích, xác nhận, robot kiểm readiness và dẫn đường | ServiceRequest, NavigationRequest, Job, JobStep, `WF_NAVIGATION` | `/pi/requests/navigation`, request status/cancel | Same-map/XOR/active target; robot bận thì queue; cancel an toàn. |
| Patient escort | Xác nhận đích và được dẫn tới vị trí | ServiceRequest, EscortRequest, Job, `WF_PATIENT_ESCORT` | `/pi/requests/escort`, request status/cancel | `patient_reference` optional; MOVE mode ESCORT; không suy ra person-following. |
| Patient SOS | SOS thủ công, tạo alert ngay, Staff tiếp nhận và xử lý | EmergencyIncident, Alert, AlertRecipient, ServiceRequest, Job, `WF_EMERGENCY_SOS_RESPONSE` | `/pi/incidents/sos`, incident status | Idempotent; trạng thái incident tách ACK/start/resolve/close. |
| Staff task assignment | Tạo navigation/escort/delivery/operational request | ServiceRequest subtypes, Job, Schedule | Staff request APIs, delivery, schedules | Server tự chọn robot duy nhất; Staff không gọi assign-robot. |
| Staff monitoring | Theo dõi robot, Job và tiến độ | Robot, RobotStatusHistory, RobotLocationHistory, JobEvent | robots, jobs, SSE | Staff active đọc robot; Job/request vẫn kiểm operational scope. |
| Staff delivery | Tạo delivery, xác thực hai đầu, xác nhận hàng/cửa/chốt | DeliveryRequest, DeliveryItem, AuthVerification, JobStepResponse | delivery, QR/auth verification, JobStep response | QR một lần dùng; snapshot hàng khớp; schema 3 dùng response tách biệt. |
| Staff emergency | Nhận cảnh báo, tới hiện trường, resolve hoặc false alarm | Alert/Recipient, EmergencyIncident, JobEvent | alerts, emergency-incidents, workflow event | Alert ACK khác incident resolve; scope theo recipient/ca trực. |
| Staff recovery | Xử lý path blocked hoặc trạng thái vật lý chưa rõ | Job, JobStep, JobEvent, recovery receipt | workflow event, operation recovery | Không tự replay lệnh vật lý; chỉ release robot sau reconciliation. |

## 5. Contract dùng chung

### 5.1. Authentication và authorization

| Boundary | Credential | Authority | Quy tắc |
|---|---|---|---|
| Public catalog | Không credential | Anonymous | Chỉ trả dữ liệu public/active/effective đã rút gọn. |
| Patient qua Robot UI | Device JWT | `ROLE_DEVICE` | `robotId` lấy từ token; không nhận robot/account tùy ý từ body. |
| Staff | User JWT | `ROLE_STAFF` | Account phải ACTIVE, token version hợp lệ và đã qua password-change gate. |
| Admin dùng chung endpoint | User JWT | `ROLE_ADMIN` | Có quyền superset nhưng Admin-only CRUD không thuộc phạm vi tài liệu này. |

Không thêm `/auth/register/patient`, Patient login, Patient refresh token, Patient profile hoặc lịch sử cá nhân.

### 5.2. Staff operational scope

Một Staff được phép truy cập khi thỏa ít nhất một điều kiện:

1. Staff là người tạo ServiceRequest/Schedule;
2. Staff là `Job.assigned_staff_account_id`;
3. Staff là AlertRecipient đã resolve;
4. resource gắn với Room thuộc Shift `ACTIVE`, trong khoảng `[starts_at, ends_at)`, của Staff;
5. endpoint là robot read-only status/location và Account Staff đang ACTIVE.

Các quan hệ room được resolve theo subtype request: Navigation room, Escort origin/destination room, Delivery pickup/delivery room hoặc Endpoint → Room. Resource tồn tại nhưng ngoài scope trả `403`; ID không tồn tại trả `404`. Client không được truyền `accountId` để mở rộng scope.

### 5.3. Device scope

- Device chỉ đọc/hủy ServiceRequest có `requested_robot_id` trùng `robotId` trong Device JWT.
- Device chỉ đọc EmergencyIncident có `robot_id` trùng token.
- Mọi identity lấy từ principal, không tin `robotId` trong body.
- Một `Idempotency-Key` chỉ dùng cho một actor, operation và payload canonical. Retry cùng payload trả cùng resource; payload khác trả `409 CONFLICT`.

### 5.4. Pagination, thời gian và lỗi

- Danh sách dùng `PageResponse<T>`: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- Mặc định `page=0`, `size=20`; `size` nằm trong `[1,100]`, trừ event history tối đa 200.
- Thời gian ở API là ISO-8601 UTC; filter thời gian dùng khoảng `[from,to)` và yêu cầu `from < to`.
- Lỗi dùng `ApiErrorResponse`: `timestamp`, HTTP `status`, `error`, `message`, `path`, `correlationId`, `violations`.
- Mã chính: `400 VALIDATION_ERROR`, `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`, `409 CONFLICT/CONCURRENT_UPDATE`, `410 LEGACY_EXECUTION_RETIRED`, `422 BUSINESS_RULE_VIOLATION`.

### 5.5. Audit và concurrency

- Mọi mutation quan trọng ghi actor, action code, entity/id, before/after, IP/user-agent, correlation ID và UTC occurred-at.
- Dùng optimistic locking cho resource có `row_version`; thao tác tranh chấp trả `409` thay vì ghi đè.
- Command/event/response có side effect phải idempotent. Event và state transition phải commit cùng transaction trước khi ACK.

## 6. Phase 0 — Baseline và contract freeze

> **Trạng thái: COMPLETE (2026-10-15).** Biên bản gate và inventory được lưu tại [`docs/baselines/phase0-baseline.md`](./baselines/phase0-baseline.md); raw OpenAPI trước Phase 1 được lưu tại [`docs/baselines/openapi-phase0.json`](./baselines/openapi-phase0.json), SHA-256 `58F982FA12056FC1761EFBC06C948EA17A31C20E2D9DA66B5882DAEC45D1AAFF`.

### Mục tiêu

- Chốt source precedence và các deviation ở mục 2.
- Chụp OpenAPI hiện tại, inventory controller, entity, Flyway và test liên quan.
- Mỗi endpoint ở các phase sau phải có owner, contract, scope và acceptance test trước khi code.

### Deliverable và gate

| Deliverable | Acceptance |
|---|---|
| Endpoint inventory | Không bỏ sót endpoint hiện cho phép `STAFF`, `DEVICE` hoặc anonymous Patient flow. |
| Source-to-API matrix | Mọi yêu cầu Patient/Staff trong hồ sơ và swimlane có API hoặc ghi rõ thuộc Pi/UI/hardware. |
| OpenAPI baseline | Lưu JSON để phát hiện breaking change ở Phase 9. |
| Database baseline | Fresh MySQL chạy toàn bộ Flyway và Hibernate validate. |
| Regression baseline | Full Maven test pass trước thay đổi chức năng. |

## 7. Phase 1 — Security, actor và contract dùng chung

> **Trạng thái: COMPLETE (2026-10-15).** Báo cáo triển khai và bằng chứng nghiệm thu được lưu tại [`docs/phase1-security-and-shared-contract.md`](./phase1-security-and-shared-contract.md). Không có migration mới trong phase này.

| Trạng thái | Method và path | Actor/Auth | Input | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `POST /api/v1/auth/login` | Staff/Admin, public credential exchange | username, password | access/refresh token, role, password gate | Chỉ Account ACTIVE; lockout và audit login. |
| `KEEP` | `POST /api/v1/auth/refresh` | Staff/Admin, refresh token | refresh token | rotated token pair | Rotation, reuse detection và token-version gate. |
| `KEEP` | `POST /api/v1/auth/logout` | Staff/Admin bearer hoặc idempotent public route | access/refresh identifiers | `204` | Thu hồi phiên hiện tại; gọi lại vẫn `204`. |
| `KEEP` | `GET /api/v1/auth/me` | Staff/Admin JWT | Không | AccountResponse | Không trả password hash/token internals. |
| `KEEP` | `PUT /api/v1/auth/change-password` | Staff/Admin JWT | current/new password | `204` | Thu hồi phiên cũ và tắt `mustChangePassword`. |
| `ADD` | `GET /api/v1/staff/me` | Staff JWT | Không | `StaffMeResponse` | Account + StaffProfile + Shift đang hiệu lực; Staff không có profile trả `422`. |

### Public interface dự kiến

`StaffMeResponse` gồm `account`, `staffProfile`, `activeShifts`; không đưa dữ liệu Account/Shift của người khác. Đã triển khai `StaffOperationalScopePolicy` dùng chung cho controller/service ở Phase 5–8 và `DeviceResourceScopePolicy` cho Phase 3–4. Hai policy là boundary dùng chung; việc gắn chúng vào từng endpoint nghiệp vụ được thực hiện tại phase sở hữu endpoint đó.

### Kết quả triển khai

| Hạng mục | Kết quả |
|---|---|
| Auth hiện hữu | Giữ nguyên login, refresh, logout, me và change-password; xác nhận Account ACTIVE, token version và password-change gate hiện hữu. |
| `GET /api/v1/staff/me` | Đã thêm, chỉ nhận `ROLE_STAFF`; trả Account, StaffProfile đang active và Shift `ACTIVE` trong khoảng `[startsAt, endsAt)`. |
| Device boundary | Đã thêm policy kiểm Device JWT còn hiệu lực, token version hiện hành và ownership theo robot của request/incident. |
| Staff boundary | Đã thêm policy hợp nhất creator, assigned staff, AlertRecipient, active-shift room và robot read-only; Admin giữ quyền superset. |
| Contract dùng chung | Tiếp tục dùng `PageResponse`, `ApiErrorResponse`, UTC timestamp, correlation ID và Bean Validation hiện hữu; kiểm tra `size <= 100`. |
| OpenAPI | `/api/v1/staff/me` có tag, user bearer security và schema `StaffMeResponse`. |
| Kiểm thử | Full Maven regression trên MySQL 8.4: **490 test, 0 failure, 0 error, 7 skipped**. |

## 8. Phase 2 — Patient: danh mục bệnh viện, information và voice

> **Trạng thái: IMPLEMENTED, VERIFICATION PENDING (2026-10-15).** Public catalog đã được tách DTO an toàn, bổ sung filter/detail/endpoint-group/article APIs và acceptance test tại [`FacilityIntegrationTest`](../src/test/java/com/mediassistrover/backend/FacilityIntegrationTest.java). Java compile và unit regression liên quan đã đạt; MySQL integration gate đang chờ Docker/Testcontainers khả dụng. Chi tiết tại [`phase2-patient-catalog-information-voice.md`](./phase2-patient-catalog-information-voice.md).

### 8.1. Public catalog

| Trạng thái | Method và path | Actor/Auth | Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `GET /api/v1/public/hospital-information` | Anonymous | `q`, `category`, `languageCode`, `page`, `size` | Page public hospital/profile | Chỉ public, PUBLISHED và effective. |
| `ADD` | `GET /api/v1/public/hospital-information/{id}` | Anonymous | ID | Public hospital/profile detail | Không lộ updated-by hay dữ liệu quản trị. |
| `HARDEN` | `GET /api/v1/public/floors` | Anonymous | `hospitalInformationId`, `q`, pagination | Page public floor | Chỉ active. |
| `ADD` | `GET /api/v1/public/floors/{id}` | Anonymous | ID | Public floor detail | Inactive/private trả `404`. |
| `HARDEN` | `GET /api/v1/public/zones` | Anonymous | `mapId`, `zoneType`, `q`, pagination | Page public zone | Chỉ `active && publicVisible`; không trả camera/ROI. |
| `ADD` | `GET /api/v1/public/zones/{id}` | Anonymous | ID | Public zone detail | Không trả polygon/camera nếu không cần cho UI. |
| `HARDEN` | `GET /api/v1/public/rooms` | Anonymous | `floorId`, `roomType`, `departmentCode`, `q`, pagination | Page public room | Chỉ active/public. |
| `ADD` | `GET /api/v1/public/rooms/{id}` | Anonymous | ID | Public room detail | Trả default destination reference nhưng không internal notes. |
| `HARDEN` | `GET /api/v1/public/endpoints` | Anonymous | `mapId`, `roomId`, `endpointType`, `accessible`, `q`, pagination | Page public endpoint | Không trả tọa độ, asset hash hoặc internal description. |
| `ADD` | `GET /api/v1/public/endpoints/{id}` | Anonymous | ID | Public endpoint detail | Chỉ active/public. |
| `ADD` | `GET /api/v1/public/endpoint-groups` | Anonymous | `mapId`, `q`, pagination | Page public group | Chỉ active group có ít nhất một active member. |
| `ADD` | `GET /api/v1/public/endpoint-groups/{id}` | Anonymous | ID | Public group detail | Không lộ policy nội bộ ngoài display metadata cần thiết. |
| `ADD` | `GET /api/v1/public/endpoint-groups/{id}/members` | Anonymous | pagination | Page public endpoint summary | Member phải active và cùng Map. |
| `ADD` | `GET /api/v1/public/hospital-information/{hospitalId}/articles` | Anonymous | `q`, `category`, `languageCode`, pagination | Page `PublicArticleResponse` | Chỉ article public/PUBLISHED/effective; query deterministic. |
| `ADD` | `GET /api/v1/public/hospital-information/{hospitalId}/articles/{articleId}` | Anonymous | IDs | Public article detail | Article phải thuộc đúng hospital và còn hiệu lực. |

### 8.2. Information request và voice

| Trạng thái | Method và path | Actor/Auth | Input | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `POST /api/v1/pi/requests/information` | Device JWT | `Idempotency-Key`, question/note, priority, data | ServiceRequestResponse | Robot từ token; tạo đúng một request/job. |
| `KEEP` | `POST /api/v1/pi/voice/interactions` | Device JWT | PiEnvelope start | AckResponse | Idempotent theo robot/operation; một active lifecycle hợp lệ. |
| `KEEP` | `POST /api/v1/pi/voice/interactions/{interactionId}/completion` | Device JWT | completion/cancel/fail | AckResponse | Cross-robot bị chặn; terminal transition hợp lệ. |
| `KEEP` | `POST /api/v1/pi/voice/commands` | Device JWT | transcript/intent/parameters | AckResponse | Sequence và command id idempotent; không chứng minh STT đã chạy. |
| `KEEP` | `POST /api/v1/pi/voice/commands/{commandId}/result` | Device JWT | controlled processing result | AckResponse | Link ServiceRequest nếu có; fallback/source được lưu rõ. |

Public DTO phải tách khỏi internal Facility DTO. Không trả `updatedByAccountId`, map URI/hash, camera source/ROI, evidence reference, audio URI hoặc audit fields.

## 9. Phase 3 — Patient: navigation, escort và request control

> **Trạng thái: IMPLEMENTED, VERIFICATION PENDING (2026-10-15).** Đã bổ sung device-owned request detail/cancel, payload-bound idempotency, target/map hardening, escort speed policy và recovery-safe cancellation. Java compile/package và 18 unit regression test đạt; MySQL integration gate đang chờ Docker/Testcontainers khả dụng. Chi tiết tại [`phase3-patient-navigation-escort-request-control.md`](./phase3-patient-navigation-escort-request-control.md).

| Trạng thái | Method và path | Actor/Auth | Input | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `POST /api/v1/pi/requests/navigation` | Device JWT | key, map, room/endpoint/group, mode, accessibility | NavigationResponse | XOR endpoint/group; cùng Map; active target; retry cùng payload idempotent. |
| `HARDEN` | `POST /api/v1/pi/requests/escort` | Device JWT | key, origin/destination, group, `patientReference`, speed/timeout | EscortResponse | Speed không vượt 0.3 m/s/policy; không bật patient-following. |
| `ADD` | `GET /api/v1/pi/requests/{requestId}` | Device JWT | request ID | `DeviceRequestDetailResponse` | Chỉ request của robot token; kèm trạng thái Job rút gọn, không Staff PII. |
| `ADD` | `POST /api/v1/pi/requests/{requestId}/cancel` | Device JWT | reason, optional idempotency key | Updated device request detail | Idempotent; terminal giữ nguyên; physical uncertainty → `RECOVERY_REQUIRED`. |

### Quy tắc điều phối một robot

- Staff/Patient không chọn robot. Server sử dụng robot duy nhất nếu IDLE và đủ capability/map hash.
- Khi robot BUSY hoặc đang giữ Job `RECOVERY_REQUIRED`, request hợp lệ ở `QUEUED` và không bị fail giả.
- Không có Staff `assign-robot` trong target API. Route hiện hữu tiếp tục Admin-only.
- Destination invalid trả `422`; entity không tồn tại trả `404`; target khác map hoặc inactive trả `422`.

## 10. Phase 4 — Patient: SOS và theo dõi hỗ trợ

> **Trạng thái: IMPLEMENTED, VERIFICATION PENDING (2026-10-15).** Đã bổ sung device-safe incident status, gắn current-device/ownership policy và serialize SOS creation theo robot để bảo toàn duplicate operation. Java compile/package và 18 unit regression test đạt; MySQL integration gate đang chờ Docker/Testcontainers khả dụng. Chi tiết tại [`phase4-patient-sos-and-assistance-status.md`](./phase4-patient-sos-and-assistance-status.md).

| Trạng thái | Method và path | Actor/Auth | Input | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `POST /api/v1/pi/incidents/sos` | Device JWT | PiEnvelope, map/endpoint/coordinates, severity, description | SosResponse | Một transaction tạo incident/request/alert/job; duplicate operation trả cùng IDs. |
| `ADD` | `GET /api/v1/pi/incidents/{incidentId}` | Device JWT | incident ID | `DeviceIncidentStatusResponse` | Chỉ incident của robot token; không trả recipient/contact hoặc Staff PII. |

SOS phải phát Alert ngay cả khi robot đang BUSY. `ACKNOWLEDGED` chỉ là Staff tiếp nhận; `IN_PROGRESS` là đã bắt đầu xử lý; `RESOLVED` là xử lý xong; `CLOSED` là đóng hồ sơ. Không tự resume Job cũ sau SOS.

## 11. Phase 5 — Staff: hồ sơ, ca trực và dữ liệu tham chiếu

> **Trạng thái: IMPLEMENTED, VERIFICATION PENDING (2026-10-15).** Đã sửa quyền xem ca trực về đúng `403`, harden voice history theo operational scope và bổ sung published Workflow catalog canonical không mang prefix `/admin`. Java compile/package và 18 unit regression test đạt; MySQL integration gate đang chờ Docker/Testcontainers khả dụng. Chi tiết tại [`phase5-staff-shifts-reference-voice-workflows.md`](./phase5-staff-shifts-reference-voice-workflows.md).

### 11.1. Ca trực và Facility read-only

| Trạng thái | Method và path | Actor/Auth | Query/Input | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `GET /api/v1/staff/shifts` | Staff JWT | `status`, pagination | Page ShiftResponse | Chỉ shift của Staff hiện tại. |
| `KEEP` | `GET /api/v1/staff/shifts/{shiftId}` | Staff JWT | ID | ShiftResponse | Shift người khác trả `403`. |
| `KEEP` | `GET /api/v1/hospital-information` | Staff/Admin JWT | pagination | Page HospitalInformationResponse | Internal read-only cho Staff. |
| `KEEP` | `GET /api/v1/hospital-information/{id}` | Staff/Admin JWT | ID | HospitalInformationResponse | Không cho Staff mutation. |
| `KEEP` | `GET /api/v1/floors` | Staff/Admin JWT | pagination | Page FloorResponse | Read-only. |
| `KEEP` | `GET /api/v1/floors/{id}` | Staff/Admin JWT | ID | FloorResponse | Read-only. |
| `KEEP` | `GET /api/v1/maps` | Staff/Admin JWT | pagination | Page MapResponse | Read-only. |
| `KEEP` | `GET /api/v1/maps/{id}` | Staff/Admin JWT | ID | MapResponse | Read-only. |
| `KEEP` | `GET /api/v1/maps/{id}/artifact-metadata` | Staff/Admin JWT | ID | MapArtifactResponse | Không trả download secret; audit nếu metadata nhạy cảm. |
| `KEEP` | `GET /api/v1/zones` | Staff/Admin JWT | pagination | Page ZoneResponse | Read-only. |
| `KEEP` | `GET /api/v1/zones/{id}` | Staff/Admin JWT | ID | ZoneResponse | Read-only. |
| `KEEP` | `GET /api/v1/rooms` | Staff/Admin JWT | pagination | Page RoomResponse | Read-only. |
| `KEEP` | `GET /api/v1/rooms/{id}` | Staff/Admin JWT | ID | RoomResponse | Read-only. |
| `KEEP` | `GET /api/v1/endpoints` | Staff/Admin JWT | pagination | Page EndpointResponse | Read-only. |
| `KEEP` | `GET /api/v1/endpoints/{id}` | Staff/Admin JWT | ID | EndpointResponse | Read-only. |
| `KEEP` | `GET /api/v1/endpoint-groups` | Staff/Admin JWT | pagination | Page EndpointGroupResponse | Read-only. |
| `KEEP` | `GET /api/v1/endpoint-groups/{id}` | Staff/Admin JWT | ID | EndpointGroupResponse | Read-only. |
| `KEEP` | `GET /api/v1/endpoint-group-members` | Staff/Admin JWT | pagination | Page member response | Read-only. |
| `KEEP` | `GET /api/v1/hospital-information/{hospitalId}/articles` | Staff/Admin JWT | `status`, pagination | Page article response | Staff được đọc cả trạng thái nội bộ; không được mutation. |
| `KEEP` | `GET /api/v1/hospital-information/{hospitalId}/articles/{articleId}` | Staff/Admin JWT | IDs | Article response | Article phải thuộc hospital. |

### 11.2. Voice history và privacy

| Trạng thái | Method và path | Actor/Auth | Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `GET /api/v1/voice/interactions` | Staff/Admin JWT | robot, status, `[from,to)`, pagination | Page metadata | Staff không nhận transcript/audio trong list. |
| `HARDEN` | `GET /api/v1/voice/interactions/{id}` | Staff/Admin JWT | ID | Detail đã redact hoặc đầy đủ | Raw data chỉ khi command → request thuộc operational scope. |
| `HARDEN` | `GET /api/v1/voice/commands` | Staff/Admin JWT | interaction, status, time, pagination | Page command metadata | Không cho filter account tùy ý; redact raw text ngoài scope. |
| `HARDEN` | `GET /api/v1/voice/commands/{id}` | Staff/Admin JWT | ID | Command detail | Ngoài scope trả `403`; Admin giữ quyền audit. |

### 11.3. Published Workflow catalog dùng cho Staff UI

Các route đọc hiện tại nằm dưới prefix `/admin` dù cho phép Staff. Giữ chúng một release để tương thích, đồng thời cung cấp route canonical không mang nghĩa Admin.

| Trạng thái | Method và path | Actor/Auth | Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `COMPAT` | `GET /api/v1/admin/workflows/step-types` | Staff/Admin JWT | Không | List StepTypeResponse | Deprecated sau khi route canonical được phát hành. |
| `COMPAT` | `GET /api/v1/admin/workflows/published/all` | Staff/Admin JWT | Không | List WorkflowDetailResponse | Giữ một release; chỉ workflow PUBLISHED nhận Job mới. |
| `COMPAT` | `GET /api/v1/admin/workflows/published/{id}` | Staff/Admin JWT | Workflow ID | WorkflowDetailResponse | Giữ một release; draft/archive trả `404`. |
| `ADD` | `GET /api/v1/workflows/step-types` | Staff/Admin JWT | Không | List StepTypeResponse | Read-only catalog 11 enum; không có write API. |
| `ADD` | `GET /api/v1/workflows/published` | Staff/Admin JWT | `serviceType`, pagination | Page Workflow summary | Chỉ PUBLISHED và `acceptsNewJobs=true`. |
| `ADD` | `GET /api/v1/workflows/published/{id}` | Staff/Admin JWT | Workflow ID | WorkflowDetailResponse | Dùng cho request/schedule UI; không trả draft. |

## 12. Phase 6 — Staff: request, Job, robot và schedule

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15). Chi tiết: [phase6-staff-requests-jobs-robots-schedules.md](phase6-staff-requests-jobs-robots-schedules.md).

### 12.1. ServiceRequest

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `POST /api/v1/information-requests` | Staff/Admin JWT | key, question/note/data | ServiceRequestResponse | Creator ownership; idempotent. |
| `HARDEN` | `POST /api/v1/navigation-requests` | Staff/Admin JWT | key + navigation contract | NavigationResponse | Target validation như Phase 3; server chọn robot. |
| `HARDEN` | `POST /api/v1/escort-requests` | Staff/Admin JWT | key + escort contract | EscortResponse | `patientReference` optional; không Patient FK. |
| `HARDEN` | `POST /api/v1/operational-requests` | Staff/Admin JWT | key, request type, robot/data | ServiceRequestResponse | Allowlist request type; startup readiness vẫn Admin-only. |
| `HARDEN` | `GET /api/v1/service-requests` | Staff/Admin JWT | `status`, `requestType`, robot, `[from,to)`, pagination | Page ServiceRequestResponse | Staff scope union; không chỉ creator. |
| `HARDEN` | `GET /api/v1/service-requests/{id}` | Staff/Admin JWT | ID | ServiceRequest detail | Owner/assigned/shift scope. |
| `KEEP` | `POST /api/v1/service-requests/{id}/submit` | Staff/Admin JWT | ID | Updated response | Chỉ DRAFT và actor có scope; tạo một Job. |
| `HARDEN` | `POST /api/v1/service-requests/{id}/cancel` | Staff/Admin JWT | reason | Updated response | Physical uncertainty → recovery; cancellation idempotent. |

### 12.2. Job và realtime progress

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `GET /api/v1/jobs` | Staff/Admin JWT | status, workflow, request, robot, `[from,to)`, pagination | Page JobResponse | Staff scope union. |
| `HARDEN` | `GET /api/v1/jobs/{id}` | Staff/Admin JWT | ID | JobDetailResponse | Gồm selected/running step cần cho UI; không lộ secret. |
| `HARDEN` | `GET /api/v1/jobs/{id}/events` | Staff/Admin JWT | pagination tối đa 200 | Page JobEventResponse | Chronological, actor-safe và scope. |
| `KEEP` | `GET /api/v1/jobs/{id}/stream` | Staff/Admin JWT | SSE accept | SSE UI snapshot/event | Xác thực ở initial request; cleanup emitter; scope trước subscribe. |
| `HARDEN` | `POST /api/v1/jobs/{id}/cancel` | Staff/Admin JWT | reason | JobResponse | Scope và safe cancellation. |
| `HARDEN` | `POST /api/v1/jobs/{id}/reconcile` | Staff/Admin JWT | outcome, note | JobResponse | Chỉ `RECOVERY_REQUIRED`; không dùng cho MQTT operation chưa đối soát. |
| `HARDEN` | `POST /api/v1/jobs/{id}/events` | Staff/Admin JWT | eventId/type/correlation/occurredAt/data | WorkflowEventResponse | Event registry, time window, idempotency và WAIT correlation. |
| `HARDEN` | `GET /api/v1/jobs/{jobId}/mapping-session` | Staff/Admin JWT | Job ID | Mapping session detail | Chỉ Job thuộc scope; Staff read-only, tạo Mapping Job vẫn Admin-only. |

`POST /api/v1/jobs/{id}/assign-robot` và `POST /api/v1/jobs/{id}/assign-staff` vẫn Admin-only và không phải Staff target API.

### 12.3. Robot và Schedule

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `GET /api/v1/robots` | Staff/Admin JWT | status, pagination | Page RobotResponse | Active Staff được đọc robot duy nhất. |
| `KEEP` | `GET /api/v1/robots/{id}` | Staff/Admin JWT | ID | RobotResponse | Không trả device secret/hash. |
| `KEEP` | `GET /api/v1/robots/{id}/status-history` | Staff/Admin JWT | pagination | Page status history | Read-only. |
| `KEEP` | `GET /api/v1/robots/{id}/location-history` | Staff/Admin JWT | pagination | Page location history | Read-only; giới hạn time/page khi hardening. |
| `KEEP` | `POST /api/v1/robots/{id}/reconcile` | Staff/Admin JWT | reason | RobotResponse | Actor phải có Job recovery scope; audit bắt buộc. |
| `HARDEN` | `POST /api/v1/schedules` | Staff/Admin JWT | schedule contract | ScheduleResponse | Staff chỉ workflow/type được phép; server chọn robot duy nhất. |
| `HARDEN` | `GET /api/v1/schedules` | Staff/Admin JWT | status, pagination | Page ScheduleResponse | Staff chỉ schedule do mình tạo. |
| `HARDEN` | `GET /api/v1/schedules/{id}` | Staff/Admin JWT | ID | ScheduleResponse | Creator/assigned operational scope. |
| `HARDEN` | `POST /api/v1/schedules/{id}/activate` | Staff/Admin JWT | ID | ScheduleResponse | Validate window/workflow/collision. |
| `HARDEN` | `POST /api/v1/schedules/{id}/pause` | Staff/Admin JWT | ID | ScheduleResponse | Idempotent transition. |
| `HARDEN` | `POST /api/v1/schedules/{id}/retire` | Staff/Admin JWT | ID | ScheduleResponse | Giữ history, không hard delete. |

## 13. Phase 7 — Staff: delivery, QR và physical confirmation

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15). Chi tiết: [phase7-staff-delivery-qr-physical-confirmation.md](phase7-staff-delivery-qr-physical-confirmation.md).

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `KEEP` | `POST /api/v1/delivery-requests` | Staff/Admin JWT | key, pickup/delivery target, recipient, deadline, items | DeliveryResponse | Item code unique; snapshot; creator ownership; server chọn robot. |
| `ADD` | `GET /api/v1/delivery-requests` | Staff/Admin JWT | request status, sender/receiver, rooms, deadline, `[from,to)`, pagination | Page DeliveryResponse | Creator/assigned/active-shift scope. |
| `KEEP` | `GET /api/v1/delivery-requests/{id}` | Staff/Admin JWT | ID | DeliveryResponse | Scope; item/confirmation history. |
| `COMPAT` | `POST /api/v1/delivery-requests/{id}/confirm-pickup` | Staff/Admin JWT | legacy verification/items/door/lock | DeliveryResponse | Chỉ schema 2; schema 3 trả `410 LEGACY_EXECUTION_RETIRED`. |
| `COMPAT` | `POST /api/v1/delivery-requests/{id}/confirm-delivery` | Staff/Admin JWT | legacy verification/items/door/lock | DeliveryResponse | Chỉ schema 2; schema 3 trả `410`. |
| `HARDEN` | `POST /api/v1/jobs/{jobId}/auth-verifications` | Staff/Admin JWT | auth step, method, purpose, TTL | AuthVerificationResponse | Job scope; purpose/permission lấy từ running step; idempotent. |
| `KEEP` | `GET /api/v1/auth-verifications/{id}` | Staff/Admin JWT | ID | AuthVerificationResponse | Actor/job scope; không trả QR secret/proof. |
| `HARDEN` | `POST /api/v1/jobs/{jobId}/qr-challenges` | Staff/Admin JWT | purpose/TTL | QrChallengeResponse | Proof hash, hạn ngắn, single-use, đúng AUTH step. |
| `HARDEN` | `POST /api/v1/jobs/{jobId}/qr-verifications` | Staff/Admin JWT | proof | AuthVerificationResponse | Wrong/expired/reused proof bị từ chối; audit actor. |
| `HARDEN` | `POST /api/v1/job-steps/{id}/responses` | Staff/Admin JWT | key, `responseType`, data, note | Receipt | Schema 3; selected unexpired RUNNING step; actor và payload snapshot đúng. |

Schema 3 dùng ba response chính:

- `PAYLOAD_CONFIRMED`: item code/quantity phải bằng snapshot Backend;
- `DOOR_CLOSED`: xác nhận thao tác Staff, không phải tín hiệu cảm biến;
- `LOCK_CHECKED`: xác nhận kiểm tra vật lý, tách khỏi `CONTROL_HARDWARE.published`.

Hủy sau pickup hoặc khi cargo/door/lock chưa đối soát phải giữ robot trong `RECOVERY_REQUIRED` đến khi Staff reconcile.

## 14. Phase 8 — Staff: alert, emergency và recovery

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15). Chi tiết: [phase8-staff-alert-emergency-recovery.md](phase8-staff-alert-emergency-recovery.md).

### 14.1. Alert và incident

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `GET /api/v1/alerts` | Staff/Admin JWT | status, severity, type, `[from,to)`, pagination | Page AlertResponse | Chỉ AlertRecipient hoặc active-shift resource. |
| `HARDEN` | `GET /api/v1/alerts/{id}` | Staff/Admin JWT | ID | AlertResponse | Không trả recipients khác cho Staff nếu không cần. |
| `HARDEN` | `POST /api/v1/alerts/{id}/acknowledge` | Staff/Admin JWT | ID | AlertResponse | Chỉ recipient/authorized delegate; ACK ANY/ALL chính xác. |
| `HARDEN` | `POST /api/v1/alerts/{id}/resolve` | Staff/Admin JWT | ID | AlertResponse | Không thay thế incident resolve; state guard. |
| `HARDEN` | `POST /api/v1/alerts/{id}/close` | Staff/Admin JWT | ID | AlertResponse | Chỉ sau resolved/policy; audit. |
| `HARDEN` | `GET /api/v1/emergency-incidents` | Staff/Admin JWT | status, severity, type, map/room, `[from,to)`, pagination | Page IncidentResponse | Controller truyền actor; recipient/active-shift scope. |
| `HARDEN` | `GET /api/v1/emergency-incidents/{id}` | Staff/Admin JWT | ID | IncidentResponse | Ngoài scope trả `403`. |
| `HARDEN` | `POST /api/v1/emergency-incidents/{id}/acknowledge` | Staff/Admin JWT | ID | IncidentResponse | OPEN → ACKNOWLEDGED; actor/time nhất quán. |
| `HARDEN` | `POST /api/v1/emergency-incidents/{id}/start` | Staff/Admin JWT | ID | IncidentResponse | ACKNOWLEDGED → IN_PROGRESS; responder có scope. |
| `HARDEN` | `POST /api/v1/emergency-incidents/{id}/resolve` | Staff/Admin JWT | resolution | IncidentResponse | IN_PROGRESS → RESOLVED; lưu actor/note. |
| `HARDEN` | `POST /api/v1/emergency-incidents/{id}/close` | Staff/Admin JWT | ID | IncidentResponse | RESOLVED → CLOSED. |
| `HARDEN` | `POST /api/v1/emergency-incidents/{id}/false-alarm` | Staff/Admin JWT | reason/evidence | IncidentResponse | Audit bắt buộc; không xóa detector/event. |

### 14.2. Operation recovery

| Trạng thái | Method và path | Actor/Auth | Input/Query | Output | Scope và acceptance |
|---|---|---|---|---|---|
| `HARDEN` | `GET /api/v1/jobs/{jobId}/operations/recovery` | Staff/Admin JWT | Job ID | Recovery evidence JSON/DTO | Job scope; response nêu command/event/physical uncertainty. |
| `HARDEN` | `POST /api/v1/jobs/{jobId}/operations/{jobStepId}/reconcile` | Staff/Admin JWT | key, outcome, evidence/note | Reconciliation result | Idempotent; đúng JobStep; không replay hardware command. |

`POST /api/v1/jobs/{jobId}/events` ở Phase 6 là entry point cho `PATH_CLEARED`, responder arrival và event đánh thức WAIT. Server dùng registry allowlist, kiểm `eventType + correlationKey + occurredAt`, lưu event trước ACK và từ chối event ngoài Job scope.

## 15. Phase 9 — OpenAPI, compatibility và rollout

> **Trạng thái: IMPLEMENTED, VERIFICATION PENDING (2026-10-16).** Security boundary, tag, reusable error contract và kiểm tra tương thích tự động với snapshot Phase 0 đã được triển khai. Java 21 package và unit test OpenAPI đã pass; fresh MySQL 8.4/full regression đang chờ Docker khả dụng. Chi tiết tại [`phase9-openapi-compatibility-rollout.md`](./phase9-openapi-compatibility-rollout.md).

### 15.1. Public interfaces/types

| Type/policy | Mục tiêu |
|---|---|
| `StaffMeResponse` | Account + StaffProfile + active shifts của chính Staff. |
| Public facility DTOs | Chỉ display/navigation-selection data; không internal URI/hash/camera/audit. |
| `PublicArticleResponse` | Article đã duyệt với source metadata, không updated-by. |
| `DeviceRequestDetailResponse` | Request + subtype + Job status rút gọn; không Staff PII. |
| `DeviceIncidentStatusResponse` | Incident state/timestamps và thông điệp UI an toàn; không recipient details. |
| Delivery page/filter | Query typed theo status/participant/room/deadline/time và trả `PageResponse<DeliveryResponse>`. |
| `StaffOperationalScopePolicy` | Một nơi duy nhất kiểm owner/assignment/recipient/active shift. |
| `DeviceResourceScopePolicy` | Một nơi duy nhất kiểm request/incident thuộc robot token. |

### 15.2. Compatibility

- Thay đổi mới ưu tiên additive; không đổi path/JSON hiện hữu khi chưa có migration client.
- Hai legacy delivery confirm endpoint được mô tả rõ là schema 2 và trả `410` cho schema 3.
- Nếu tạo alias Staff cho một read endpoint đang nằm dưới prefix Admin, giữ route cũ trong ít nhất một release và đánh dấu deprecated trong OpenAPI.
- Không sửa Workflow PUBLISHED tại chỗ; clone/version mới nếu contract graph thay đổi.
- Không sửa Flyway V1–V30. Phạm vi hiện tại chưa yêu cầu bảng mới; chỉ thêm migration index khi có bằng chứng `EXPLAIN`/load test.

### 15.3. OpenAPI và tài liệu

- Mỗi endpoint khai báo đúng security scheme: anonymous, device bearer hoặc user bearer.
- Mô tả response 2xx và error 400/401/403/404/409/410/422.
- Tag tách `Patient Public Catalog`, `Robot Screen Requests`, `Robot Emergency`, `Staff Operations`, `Staff Delivery`, `Staff Alerts` và `Staff Incidents`.
- Request/response example không chứa credential, QR proof thật, Patient PII hoặc internal URI.
- So sánh OpenAPI với baseline Phase 0; breaking change không có compatibility note làm Phase 9 fail.

## 16. Kế hoạch kiểm thử

### 16.1. Unit và contract test

- Validator: target XOR, same-map, active/public/effective, time window, pagination, event registry và delivery response type.
- Scope policy: owner, assigned, AlertRecipient, active Shift room, inactive/expired Shift, resource không có room.
- Mapper/redaction: public DTO, Staff voice metadata và device response không lộ dữ liệu nội bộ.
- Idempotency: cùng actor/key/payload; khác actor; khác payload; retry sau timeout.

### 16.2. MockMvc security matrix

Mỗi nhóm route phải có ít nhất các ca:

| Caller | Public | Patient Device | Staff đúng scope | Staff ngoài scope | Admin |
|---|---:|---:|---:|---:|---:|
| Anonymous | `200` | `401` | `401` | `401` | `401` |
| Device JWT | Theo public | `2xx` đúng robot | `403` | `403` | `403` |
| Staff JWT | Theo public | `403` | `2xx` | `403` | Không áp dụng |
| Admin JWT | Theo public | `403` | Theo quyền superset | Theo quyền superset | `2xx` |

### 16.3. MySQL 8.4 integration test

- Fresh Flyway + Hibernate validate.
- Patient: public filter/detail, information fallback/source, voice ownership, navigation/escort validation, queued single robot, request status/cancel và SOS transaction.
- Staff: login/password gate, `staff/me`, active-shift scope, request/job filters, SSE subscription authorization và schedule ownership.
- Delivery: item snapshot, sender/receiver grant, QR wrong/expired/reused, PAYLOAD/DOOR/LOCK response, cancel-after-pickup recovery.
- Alert/incident: recipient scope, ANY/ALL ACK, lifecycle actor/timestamp, false alarm, PATH_CLEARED correlation và reconciliation.
- Race: double submit/cancel/ACK/response/reconcile chỉ có một state transition và một durable event hợp lệ.

### 16.4. Privacy, performance và regression

- Không trả draft/private/expired article, transcript ngoài scope, audio URI, QR secret, device secret, map artifact URI hoặc recipient address không cần thiết.
- Kiểm N+1 và query plan cho request/job/delivery/alert/incident list với volume đại diện.
- SSE emitter được đóng khi disconnect/terminal, không giữ subscriber trái phép.
- Chạy full Maven regression, OpenAPI generation và API acceptance walkthrough theo năm swimlane.
- Hardware/Pi/AI acceptance được báo cáo riêng; không dùng test Backend để tuyên bố phần cứng đã nghiệm thu.

## 17. Definition of Done theo phase

| Phase | Code/API | Database | Test | Security/Audit | Tài liệu |
|---:|---|---|---|---|---|
| 0 | Không đổi runtime | Fresh schema pass | Baseline regression | Security inventory | Source/hash/endpoint matrix hoàn tất. |
| 1 | Shared policies + `staff/me` | Không bảng mới | Auth/scope unit + MockMvc | Password/token/scope gate | OpenAPI shared contract. |
| 2 | Public DTO/filter/detail + voice contract | Index chỉ khi có bằng chứng | Public visibility + voice ownership | Không lộ internal fields | Patient catalog examples. |
| 3 | Device request read/cancel | Không bảng mới | Idempotency, queue, cancel/recovery | Device ownership | Navigation/escort state table. |
| 4 | Device incident status + SOS atomicity | Không bảng mới | Duplicate/race/lifecycle | Robot ownership + audit | SOS sequence/response example. |
| 5 | Staff profile/reference/voice privacy | Không bảng mới | Active-shift + redaction | Operational scope | Staff read catalog. |
| 6 | Request/Job/robot/schedule hardening | Index nếu load test yêu cầu | Filter/SSE/ownership/race | Scope + mutation audit | Staff operations API matrix. |
| 7 | Delivery list + schema 3 canonical response | Không bảng mới dự kiến | E2E sender→receiver | QR/scope/physical audit | Compatibility note cho schema 2. |
| 8 | Alert/incident/recovery scope | Không bảng mới dự kiến | ACK/lifecycle/event/reconcile | Recipient/shift scope | Recovery event registry. |
| 9 | OpenAPI và compatibility hoàn tất | Fresh migration pass | Full regression/load/acceptance | Security matrix pass | Changelog/deprecation/acceptance report. |

## 18. Ngoài phạm vi

- Admin CRUD cho Account/Staff/Facility/Workflow/Robot configuration/reporting.
- Patient account, registration, login, mobile profile hoặc personal history.
- Thay toàn bộ schema theo 41 bảng Logical ERD hoặc tạo bảng StepType.
- Pi/MQTT protocol không trực tiếp phục vụ Patient/Staff UI, trừ dependency được nêu trong endpoint contract.
- AI model, STT/TTS runtime, ROS2/Nav2 và phần cứng thật.
- Nhiều robot và UI cho Staff chỉ định robot; thiết kế queue/capability vẫn phải không khóa khả năng mở rộng tương lai.

## 19. Giả định đã khóa

- Patient là guest qua Robot UI/voice và không có Account/JWT.
- Staff chỉ thấy resource do mình tạo, được giao, được gửi tới hoặc thuộc Room của Shift đang hiệu lực.
- Hệ thống hiện chỉ có một robot; server tự điều phối và queue, Staff không chọn robot.
- Admin giữ quyền superset nhưng Admin-only API không nằm trong backlog Patient/Staff này.
- `patient_reference` là mã ngoài hệ thống, nullable, không phải dữ liệu định danh đăng nhập.
- Mọi thời điểm chuẩn hóa UTC; mọi mutation quan trọng có audit và correlation ID.
