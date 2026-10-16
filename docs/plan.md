# MediAssistRover — Backend Implementation Plan

> Kế hoạch làm tiếp riêng Backend (2026-10-08): [Phase 15–21](backend-remaining-phases.md). Phase 15 và Phase 16 đã DONE; Phase 16 đạt 100/100 regression. Phase 17–21 chưa triển khai.

> Bổ sung nguồn ngày 2026-10-07: đọc [bản ghi nhớ nguồn](source-baseline.md), [kế hoạch đối chiếu](source-alignment-plan.md) và [danh mục nguồn có hash](sources/README.md) trước công việc tiếp theo. Đây là review/delta đề xuất; chưa thay schema, protocol hoặc tám workflow hiện hành. Trạng thái triển khai mới nhất xem [implementation-status.md](implementation-status.md); một số nhãn Phase cũ bên dưới là lịch sử kế hoạch.

Cập nhật: 2026-10-05. Quyết định business mới thay thế các mục Patient authentication và StepType catalog trong kế hoạch cũ.

## 1. Phạm vi và nguyên tắc
Monolith Spring Boot MVC; Java 21, Spring Boot 3.5.16, Maven, MySQL 8.4, Lombok không @Data Entity, MapStruct. PascalCase table, UUID Java/BINARY(16) cho mọi PK/FK, JSON MySQL, enum VARCHAR không ordinal.
Account chỉ ADMIN/STAFF; Patient là actor tương tác màn hình robot, không Account/credential. Không thêm Patient entity. EscortRequest.patient_reference nullable là mã khám ngoài hệ thống.
Robot và màn hình dùng chung Device JWT. Device gửi ServiceRequest; Server chọn Workflow/tạo Job. Không mở API device trực tiếp tạo Job.
Không làm frontend, chương trình Pi, ROS/Nav2, hardware/AI, Redis/Kafka/microservice.

## 2. Impact Analysis
| Module | Logic trước | Logic đã chốt | Hành động |
|---|---|---|---|
| Patient Auth | Đăng ký/duyệt/token | Không Account | REMOVE API/DTO/service/enum approval |
| Account | Ba role, approval fields | ADMIN/STAFF, mustChangePassword | REVISE entity/security/baseline |
| StaffProfile | Có model, thiếu admin CRUD hoàn chỉnh | Admin nhập username/password tạm | NEW CRUD transactional |
| JWT | Access/refresh/blacklist | Giữ nguyên, hạn chế mật khẩu tạm | REVISE filter/refresh/change password |
| StepType | Entity/table có bật/tắt | Enum 11 giá trị cố định | REMOVE entity/table/write API |
| Workflow | Granular graph API | Giữ granular, thêm aggregate | REVISE route/code immutability, NEW graph API |
| Seed | Bốn Workflow | Tám DRAFT | REVISE V5 |
| Request/Job | Ownership dựa Account | Robot interaction không Account | REVISE nullable actor/device entry |
| Facility/Robot | UUID/BINARY, JWT device | Giữ nguyên | DONE; không rewrite |

## 3. Revised Phase 1–5
| Phase | Nhãn | Công việc/Entity | API/service | Test và bàn giao |
|---|---|---|---|---|
| 1 Foundation | DONE + REVISE baseline | Cấu hình, Flyway, Docker, error contract | Config/Security/OpenAPI | MySQL fresh migrate + Hibernate validate; giữ UUID/case |
| 2 Auth/Account | REVISE/REMOVE | Account, RefreshToken, RevokedToken, AuditLog | AuthService/AdminAccountService; xóa Patient register/approval | ADMIN/STAFF login; rotation/reuse/logout; mustChangePassword gate; 401/403 |
| 3 Facility và Staff | DONE Facility; NEW Staff | StaffProfile/Account; HospitalInformation/Floor/Map/Zone/Room/Endpoint/Group | StaffService/Impl/Mapper/AdminStaffController | Atomic create; uniqueness; no secret response; deactivate giữ history |
| 4 Robot | DONE + REVISE interaction boundary | Robot/status/location history | DeviceAuth/PiRobot; Device screen request | Device JWT riêng, không public screen; heartbeat 10s/offline 30s; idempotency |
| 5 Workflow | REVISE + NEW aggregate | Workflow/Task/Step/StepTransition, StepType enum | WorkflowService/validator/compiler; admin graph | Rollback graph invalid; eight DRAFT validation; published immutable/code immutable |

Dependency: Phase 2 cần 1; Staff/Facility cần 2; Robot cần Facility/Auth; Workflow cần baseline và enum mới. Không xem Phase 5 hoàn tất về robot thực tế chỉ vì seed graph validate.
Mỗi phase giao source, migration liên quan, Swagger contract và test. Chỉ đánh DONE sau build/test thực tế; giữ source có trước, không tạo XxxV2.

## 4. Authentication
Admin bootstrap giữ startup initializer từ ADMIN_USERNAME/ADMIN_PASSWORD/ADMIN_EMAIL; không seed secret.
Admin tạo Staff với username/temporaryPassword; BCrypt, unique username/email/phone/staffCode, Account STAFF ACTIVE và StaffProfile active trong một transaction.
mustChangePassword=true: token được cấp để đổi mật khẩu, nhưng chỉ PUT /api/v1/auth/change-password được phép; me/logout/refresh/API nghiệp vụ bị chặn. Refresh không Bearer cũng bị service từ chối khi cờ còn true.
Đổi mật khẩu thành công: false, tăng token version/revoke phiên theo service; đăng nhập lại. Admin reset tạo mật khẩu tạm và bật cờ.
DELETE Staff: Account INACTIVE + profile inactive + thu hồi refresh/tăng token version, không hard delete.
Access JWT 1h; refresh hash MySQL 7 ngày, rotation và family reuse revocation; jti blacklist MySQL; logout idempotent; không plaintext token/password.
ADMIN/STAFF là role Account; DEVICE là authority kỹ thuật riêng, không role nghiệp vụ.

## 5. Permission và CRUD
C=service/system create; U=chuyển trạng thái có kiểm soát. Bảng chưa triển khai API vẫn phải được đưa vào phase tiếp theo, không đồng nghĩa đã có Entity Java.
| Entity | Create | Read | Update | Delete | Soft delete | Role/phạm vi |
|---|---|---|---|---|---|---|
| Account | Admin bootstrap/Staff create | Admin/self | Admin lock/reset; self password | Không | INACTIVE | ADMIN/STAFF |
| StaffProfile | ADMIN | ADMIN | ADMIN | Không | is_active=false | ADMIN |
| RefreshToken/RevokedToken | Auth system | Không API dữ liệu nhạy cảm | Rotation/revoke | Expired cleanup | Theo expiration | System |
| HospitalInformation/Floor/Map/Zone/Room/Endpoint/EndpointGroup/Member | ADMIN | ADMIN/STAFF; public-filter device | ADMIN | Chỉ unreferenced nếu policy cho phép | status/is_active; Map ARCHIVED | Theo dữ liệu public và FK |
| SystemSetting | ADMIN | Theo cấu hình | ADMIN | Theo dependency | Ưu tiên inactive | ADMIN |
| Robot | ADMIN | ADMIN/STAFF; own device sync | ADMIN, heartbeat system | Không khi có history | Operational status | ADMIN/STAFF/DEVICE đúng robot |
| Workflow/Task/Step/StepTransition | ADMIN Draft | ADMIN; STAFF published; device sync | ADMIN Draft unused | Draft unused | Published ARCHIVED | Giữ FK/history |
| StepType | Không | Enum metadata | Không | Không | Không | Không table/CRUD |
| ServiceRequest/NavigationRequest/EscortRequest | STAFF/ADMIN hoặc DEVICE | ADMIN/STAFF ownership | Submit/cancel/runtime | Không | Không | Robot screen actor nullable Account |
| DeliveryRequest/DeliveryItem | Phase 7 | Theo quyền | Nghiệp vụ giao nhận | Không tùy ý | Không | ADMIN/STAFF |
| Schedule | ADMIN/STAFF scope | Scope | Activate/pause/retire | Không thông thường | RETIRED | Phase 6 |
| Job/JobTask/JobStep | Server | ADMIN/STAFF scope/device own | Runtime/cancel/reconcile | Không | Không | Device không trực tiếp tạo Job |
| Alert/AlertRecipient/EmergencyIncident/AuthVerification | Phase 7 system/business | Theo scope | State machine | Không thông thường | Không | ADMIN/STAFF/device event |
| AuditLog/JobEvent/RobotStatusHistory/RobotLocationHistory | System | Authorized read | Không | Không API xóa | Không | Retention riêng |
| DetectionEvent/CrowdDensityRecord/HeatmapRecord/VoiceInteraction/VoiceCommand | Phase sau/system | Authorized | Không API chỉnh history | Không | Không | Hợp đồng event trước, không AI |
| Shift/Slot | Phase sau ADMIN | Theo scope | Theo ca trực | Chỉ unreferenced | Theo ERD | Chưa MVP |

Account role PATIENT và bảng StepType không còn là mục tiêu schema. Không tự thêm Patient table. Đối chiếu ERD cũ 41 bảng trừ StepType cộng hai token tables = 42 bảng mục tiêu; không phải tất cả đã hiện diện trong Java hiện tại.
Quan hệ giữ: StaffProfile → Account; Floor → HospitalInformation; Map → Floor; Workflow → Task → Step; StepTransition source/target cùng workflow; Job → Workflow; JobTask → Task; JobStep → Step. ServiceRequest.requested_by_account_id nullable cho robot; requested_robot_id là identity device. Không đổi tên Step thành WorkflowStep.

## 6. Workflow architecture
Workflow template → Task → Step(enum StepType) + StepTransition → Job execution snapshot → JobTask/JobStep.
11 enum giữ nguyên: MOVE, INTERACT, DATA_SERVICE, AUTHENTICATE, CONTROL_HARDWARE, HANDLE_PAYLOAD, EXECUTE_SENSING, NOTIFY, WAIT, CHECK_CONDITION, SET_CONTEXT.
Granular API hữu ích editor; aggregate POST /graph và PUT /{id}/graph thay toàn Tasks/Steps/Transitions trong @Transactional. Graph lỗi rollback toàn bộ; dùng step/task code để liên kết request graph.
workflow_code/version bất biến khi update; published immutable; clone version mới DRAFT. Không sửa/xóa graph đang được Job tham chiếu. Draft chưa dùng có thể hard delete; published archive.
Priority bắt đầu 1, fallback cuối. Validate start, unique order/code, source/target same workflow, reachability, route outcome/default, visit/time budgets, enum, JSON shapes và condition structure. Không LOAD-before-UNLOAD trong engine.
JSON validation hiện tại là subset cấu trúc, không triển khai trọn JSON Schema standard. Condition MVP: path/operator/value, EQ/NE/GT/GTE/LT/LTE/IN/EXISTS. Pi contract cần thống nhất evaluator trước acceptance runtime.
Input mapping: {"destination":{"$ref":"/inputs/destination_endpoint_id"}}, outputs qua /outputs/STEP_CODE, vars qua /vars. Không JSONPath.
JobStep resolvedInputs là giá trị resolve thực tế từ runtime root `/inputs`, `/outputs/STEP_CODE`, `/vars`; missing pointer bị từ chối, không lưu wrapper mapping chưa resolve.

## 7. API contract
Base /api/v1; UUID chuỗi; list pagination; DTO không Entity; UTC/correlation/error chuẩn; Swagger hai security schemes userBearer/deviceBearer.
| Method | Route | Role | Request/purpose |
|---|---|---|---|
| POST | /auth/login | Public credentials ADMIN/STAFF | LoginRequest |
| POST | /auth/refresh | Public refresh credential | RefreshTokenRequest; cấm pending password |
| POST | /auth/logout | User token | LogoutRequest; blacklist + revoke |
| GET | /auth/me | ADMIN/STAFF | Self |
| PUT | /auth/change-password | ADMIN/STAFF | Current/new password; pending duy nhất API này |
| POST/GET | /admin/staff | ADMIN | StaffRequests.Create/list pageable |
| GET/PUT/DELETE | /admin/staff/{id} | ADMIN | Read/Update/deactivate |
| POST | /admin/accounts/{id}/lock,/unlock,/reset-password | ADMIN | Account controls |
| GET/POST | /admin/workflows | ADMIN | List/create definition |
| GET/PUT/DELETE | /admin/workflows/{id} | ADMIN | Detail/update Draft/delete unused |
| POST | /admin/workflows/graph | ADMIN | GraphCreateRequest(workflow,graph) |
| PUT | /admin/workflows/{id}/graph | ADMIN | GraphRequest(tasks,steps,transitions,startStepCode) |
| GET | /admin/workflows/step-types | ADMIN/STAFF | Read-only enum metadata |
| GET | /admin/workflows/published/all,/published/{id} | ADMIN/STAFF | Published read |
| POST/PUT/DELETE | /admin/workflows/{id}/tasks[/{taskId}],steps[/{stepId}],transitions[/{transitionId}] | ADMIN | Existing granular DTOs |
| PUT | /admin/workflows/{id}/start-step | ADMIN | StartStepRequest |
| POST | /admin/workflows/{id}/validate,/publish,/archive,/clone | ADMIN | Lifecycle/CloneWorkflowRequest |
| POST | /pi/auth/token | Device credentials | robotCode/deviceSecret |
| POST | /pi/requests/information,/navigation,/escort | DEVICE | Existing request DTO + Idempotency-Key; Server creates Job |
| POST | /pi/monitoring/detections,/crowd-density | DEVICE | Phase 11 append-only sensing PiEnvelope |
| POST | /pi/voice/interactions,/commands | DEVICE | Phase 12 idempotent start/append PiEnvelope |
| POST | /pi/voice/interactions/{id}/completion,/commands/{id}/result | DEVICE | Controlled Voice lifecycle transition |
| GET | /voice/interactions[/{id}],/voice/commands[/{id}] | ADMIN/STAFF | Read-only Voice history |
| GET/POST | Existing /requests, /jobs, /schedules routes | ADMIN/STAFF scope | Existing request/Job controls |
| Existing | /pi telemetry/commands/runtime routes | DEVICE own robot | Protocol v1 envelope |

Deleted: /auth/register/patient; /admin/accounts/pending; approve/reject; StepType enable/write APIs; old /workflows base route. No public registration for Staff/Admin.
Danh sách thay đổi module và checklist API acceptance: business-refactor.md. Swagger là nguồn contract method/DTO chi tiết của các route hiện có.

## 8. State machines
Account ACTIVE ↔ LOCKED; ACTIVE/LOCKED → INACTIVE. failed-login locked_until independent timed lock. No approval states.
Workflow DRAFT → VALIDATED → PUBLISHED → ARCHIVED; editing VALIDATED resets DRAFT; published update requires clone.
Request DRAFT → SUBMITTED → QUEUED → IN_PROGRESS → COMPLETED; FAILED/CANCELLED/RECOVERY_REQUIRED branches through services.
DeliveryRequest inherits ServiceRequest status; no duplicate overall status.
Job QUEUED → ACCEPTED → RUNNING → SUCCEEDED/FAILED/CANCELLED/RECOVERY_REQUIRED. CANCEL is request/ACK, never assume hardware stopped.
JobTask PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED/NOT_VISITED; JobStep PENDING → RUNNING → SUCCEEDED/FAILED/TIMED_OUT/CANCELLED, RETRYING only approved safe policy.
Robot heartbeat/offline states existing; disconnect running job needs recovery/reconcile, no automatic hardware replay.
Alert OPEN → ACKNOWLEDGED → RESOLVED → CLOSED; recipient acceptance separate.
EmergencyIncident state exact enum/ERD must be confirmed during Phase 7; not invent runtime states without that review.

## 9. Flyway
V1 identity/security/settings: no Patient approval, must_change_password; two token tables.
V2 facility; V3 robot telemetry/device security.
V4 Workflow/Task/Step/StepTransition; enum VARCHAR + CHECK11, no StepType FK/table, priority >=1.
V5 seed eight DRAFT definitions. Legacy filename still contains step_types; contents no catalog.
V6 request/schedule/job; nullable actor; source STAFF_UI/ROBOT_UI/VOICE/SYSTEM/DETECTOR.
V7 job events/protocol; no PATIENT producer.
V8 delivery/alert/emergency; V9 operations/retention settings; V10 Shift/Slot.
V11 DetectionEvent/CrowdDensityRecord/HeatmapRecord; V12 VoiceInteraction/VoiceCommand.
These are revised DEV baselines. No existing database/volume was deleted by this task. If older migrations already applied, checksums differ: back up and explicitly recreate only intended development DB, or use forward upgrade migration if data must be retained. Do NOT flyway repair to disguise incompatible schema.
After shared environment schema freeze, only new forward migrations. Keep baseline-on-migrate=false, clean-disabled=true, ddl-auto=validate; no automatic destructive reset.
ERD Draw.io itself remains unchanged; this approved schema delta must be incorporated into its next revision.

## 10. Phases 6–9
Phase 6 DONE — core runtime: ServiceRequest/Navigation/Escort/Schedule/Job/JobTask/JobStep/JobEvent; real JSON Pointer resolver; condition/priority/default transition evaluation; runtime guard; repeated step visits; queue và một active Job mỗi Robot; DISPATCH/CANCEL command + ACK; timeout; offline/reboot chuyển RECOVERY_REQUIRED; reconciliation; DATA_SERVICE handler và INTERACT orchestration. Nhánh không được chọn được phản ánh ở JobTask `NOT_VISITED`; không tạo JobStep giả cho nhánh đó. Device screen hiện chỉ có information/navigation/escort; SOS thuộc Phase 7. Step SERVER chưa có handler nghiệp vụ fail-closed, tuyệt đối không tự báo success.
Phase 7 NEW: DeliveryRequest/DeliveryItem, Alert/AlertRecipient, EmergencyIncident/AuthVerification; confirm schema then migration forward; authorized verification, payload safe errors, audit. DoD end-to-end delivery/SOS tests and no fake server result.
Phase 8 DONE: Audit existing; all schedulers use app.operations.scheduler-enabled. Expired token cleanup, robot offline, due Schedule, overdue Job/Step timeout include edge-boundary tests and pessimistic locking; runtime settings override property fallback. Basic report API is implemented. Retention preparation only: Audit/JobEvent/Alert/Emergency3y; RobotStatus1y/location6mo; Detection/Crowd/Heatmap1y; no automatic history deletion. Multi-instance locking và retention execution thuộc Phase 13.
Phase 9 DONE — automated backend scope: JUnit5/Mockito/MockMvc/Testcontainers MySQL; fresh migrations, security isolation, rollback, unique/FK/case UUID JSON, concurrency/idempotency/reboot/offline and automated OpenAPI acceptance are covered. CI runs compile/test/JAR/image and validates Compose; Compose includes backend+MySQL and a production Nginx HTTPS profile. Hardware/Pi acceptance remains a separate integration activity.

## 10A. Phase 10 — Staff Shift/Slot
DONE. V10 creates `Shift` and `Slot` exactly with PascalCase tables, UUID `BINARY(16)`, ERD fields and FK `Shift.staff_profile_id → StaffProfile`, `Shift.room_id → Room`, `Slot.shift_id → Shift`. Admin manages aggregate or granular Slot endpoints; Staff has read-only ownership-scoped endpoints. Shift state machine is `PLANNED → ACTIVE → COMPLETED` with cancellation from PLANNED/ACTIVE. Terminal shifts are retained. Service validation enforces active Staff/Room, `starts_at < ends_at`, no overlapping non-cancelled shifts for one Staff, Slot containment, unique sequence and no overlapping Slots. Every mutation is audited.

DoD Phase 10: Flyway V1–V10 and Hibernate validate pass on MySQL 8.4; `Phase10IntegrationTest` passes 3/3; full regression passes 47/47; JAR, both Compose models and Docker image build pass.

## 10B. Phase 11 — Detection/Crowd/Heatmap
DONE. V11 tạo `DetectionEvent`, `CrowdDensityRecord`, `HeatmapRecord` theo Logical ERD và nối FK deferred từ Alert/EmergencyIncident. Device gửi detection/crowd qua Pi envelope với message-id idempotency, kiểm tra Robot–Map–Zone–Endpoint–JobStep ownership và chỉ lưu evidence reference. Admin đọc lịch sử và lưu heatmap tổng hợp; không có API sửa/xóa history và backend không chạy AI.

## 10C. Phase 12 — VoiceInteraction/VoiceCommand
DONE. V12 tạo đủ 10 field `VoiceInteraction` và 13 field `VoiceCommand` theo Logical ERD, UUID `BINARY(16)`, JSON, unique interaction/sequence, FK và CHECK lifecycle/timestamp/confidence. Device có contract start/complete interaction, append command và record controlled result; message-id chống trùng, Robot ownership và ServiceRequest ownership được kiểm tra. ADMIN/STAFF chỉ đọc lịch sử; không có CRUD sửa/xóa và backend không chạy speech/intent AI.

DoD Phase 11–12: fresh MySQL apply Flyway V1–V12, Hibernate validate và full regression pass 53/53 ngày 2026-10-06. Contract của tám DRAFT seed được harden tại V13; hardware/Pi acceptance vẫn tách riêng theo `pi-contract-and-hardware-acceptance.md`.

## 10D. Phase 13 — Multi-instance & Operations Scalability

DONE. Phase này gồm hai workstream:

1. **ShedLock cho nhiều Backend instance**: dùng JDBC/MySQL làm shared lock provider; tạo lock table bằng forward Flyway migration; áp dụng lock có tên ổn định cho schedule scan, robot offline scan, Job overdue, Step timeout, token cleanup và retention. Mỗi task phải có `lockAtMostFor` lớn hơn worst-case runtime hợp lý, `lockAtLeastFor` khi cần chống chạy dồn, và vẫn giữ transaction/pessimistic lock nghiệp vụ hiện có.
2. **Retention tự động xóa/archive dữ liệu**: đọc policy `retention.*_days` từ SystemSetting; cutoff theo UTC; xử lý batch có giới hạn; tôn trọng thứ tự FK và không đụng active/non-terminal records. Chế độ DELETE hoặc ARCHIVE phải được cấu hình rõ theo từng dataset; archive phải xác nhận thành công trước khi xóa nguồn. Mỗi run có audit, metrics, số bản ghi xử lý, duration và lỗi có thể retry.

Không đồng nhất ShedLock với business lock: ShedLock chỉ bảo đảm một instance nhận scheduler tick, còn idempotency, unique key và row lock vẫn là lớp bảo vệ cuối. Không chạy retention destructive mặc định khi chưa có policy hợp lệ; không dùng Flyway để xóa dữ liệu lịch sử.

DoD Phase 13: test đồng thời tối thiểu hai application instance chỉ có một scheduler owner; lock hết hạn có thể recovery; retention dry-run và execute đúng cutoff/batch/FK; archive failure không xóa nguồn; full regression và package pass trên fresh MySQL.

Kết quả: ShedLock 7.10.1 dùng JDBC/MySQL và DB time; sáu scheduler hiện hữu cùng retention scheduler có lock name ổn định. V14 tạo `ShedLock`, `RetentionArchive` và safety settings. Retention hỗ trợ dry-run, `DISABLED`/`DELETE`/`ARCHIVE`, UTC cutoff, bounded batch/FK order, archive verification, audit và Micrometer metrics. Execution destructive mặc định tắt. `Phase13IntegrationTest` pass 3/3.

## 10E. Phase 14 — Analytics & Advanced Reports

DONE. Mở rộng `Admin Reports` hiện có với bộ lọc time range/timezone và các nhóm báo cáo: request/job throughput, success/failure/recovery rate, SLA và duration percentile, Robot utilization/offline/battery, delivery/emergency response, crowd/fall/restricted-zone trends. Hỗ trợ breakdown theo workflow, Robot, map/zone và khoảng thời gian; export CSV đồng bộ có giới hạn 5.000 dòng.

Report phải chỉ đọc, phân quyền ADMIN, tránh lộ dữ liệu nhạy cảm, dùng aggregate/index phù hợp thay vì load toàn bộ history vào memory. Contract response phải nêu timezone, interval, generated-at và tiêu chí tính metric; pagination/row limit bắt buộc cho detail drill-down.

DoD Phase 14: metric có công thức được tài liệu hóa và fixture kiểm chứng; timezone/boundary chính xác; query plan/index được kiểm tra trên volume đại diện; export có giới hạn và audit; không làm ảnh hưởng scheduler/runtime SLA.

Kết quả: API ADMIN `/analytics`, `/jobs`, `/jobs/export.csv` đã hoàn tất; response có generated-at/timezone/interval, bucket `[start,end)`, breakdown Workflow/Robot/Map/Zone. V15 thêm index truy vấn. CSV chống formula injection, giới hạn cấu hình và ghi `REPORT_CSV_EXPORTED`. Công thức và contract nằm tại `docs/analytics-reports.md`; `Phase14IntegrationTest` pass 4/4 trên MySQL 8.4.

## 11. Files/classes and DoD
Existing packages config/controller/dto/entity/enums/exception/repository/security/service/service.impl/mapper/validation/scheduler/integration/util remain.
Changed identity: Account/AccountStatus/Role, AccountResponse/TokenResponse, AuthService/Impl/Controller, AdminAccountService/Impl/Controller, JwtAuthenticationFilter, RefreshTokenServiceImpl, Security/OpenAPI config.
New Staff: StaffRequests/StaffResponse, StaffMapper, StaffService/StaffServiceImpl, AdminStaffController; reuse StaffProfileRepository.
Workflow: enum StepType (remove entity/repository), WorkflowRequests/Responses/Mapper, WorkflowService/Impl/Controller, WorkflowJsonValidator, WorkflowValidationServiceImpl.
Request/runtime: ServiceRequestService/Impl, PiRequestController, JobExecutionService/Impl, JobDispatchService/Impl, PiJobRuntimeService/Impl, PiStepService/Impl, JobStepInputResolver/Impl, WorkflowRuntimeTransitionService/Impl, JobStepRuntimeGuard/Impl, ServerStepHandler implementations, JobStepRepository.
Tests: AuthFlowIntegrationTest, WorkflowGraphIntegrationTest, WorkflowIntegrationTest, RequestJobIntegrationTest, WorkflowRuntimeTransitionServiceTest, JobStepInputResolverTest and adapted preexisting tests.
Future phases extend existing modules; no duplicate V2 classes. Exact class lists should follow actual schema, not generate unused repository/controller per history table.
DoD current checkpoint: Maven clean full suite passes 62/62 on Java 21/fresh MySQL schema; Flyway V1–V15 and Hibernate validation pass. JAR, Docker image và Compose config pass. Manual Swagger walkthrough và Admin publish tám Workflow đã pass trong database demo; xem `backend-demo-acceptance.md`. Robot/Raspberry Pi hardware acceptance remains separate from backend completion.
