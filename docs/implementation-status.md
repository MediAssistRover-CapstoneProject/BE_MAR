# MediAssistRover Backend — Implementation Status
Cập nhật: 2026-10-08

## Checkpoint
| Phase | Trạng thái | Kết quả |
|---|---|---|
| 0 | REVISED | Business mới đã chốt; xem plan.md/business-refactor.md |
| 1 | DONE; baseline REVISED | UUID BINARY(16), PascalCase, MySQL JSON, Flyway/Swagger/Docker giữ nguyên |
| 2 | REVISED | Bỏ Patient auth/approval; ADMIN/STAFF JWT refresh rotation/blacklist; mật khẩu tạm chỉ gọi đổi password |
| 3 | DONE Facility; NEW Staff hoàn tất refactor | Admin tạo Account+StaffProfile transactional; deactivate giữ history |
| 4 | DONE core; REVISED screen boundary | Device JWT dùng chung screen; anonymous information/navigation/escort request entry |
| 5 | REVISED | 11 StepType enum, không table/write API; admin granular + aggregate graph; immutable code; 8 DRAFT seeds |
| 6 | DONE — core runtime | Request/Schedule/Job, resolver JSON Pointer, graph transition, repeated visits, queue/dispatch, cancel ACK, timeout và reboot recovery đã hoàn tất trong phạm vi Information/Navigation/Escort |
| 7 | DONE | Delivery/Alert/Emergency/AuthVerification, device SOS, audit và test end-to-end |
| 8 | DONE | Scheduler flag thống nhất; cleanup/offline/due/timeout có khóa race và inclusive deadline; report, setting precedence và retention metadata |
| 9 | DONE — automated backend scope | JUnit5/Mockito/MockMvc/Testcontainers, OpenAPI acceptance, CI, JAR/image, Compose và Nginx HTTPS; hardware acceptance vẫn tách riêng |
| 10 | DONE | Shift/Slot theo Logical ERD; Admin quản lý, Staff chỉ xem ca của mình; overlap/state/audit được kiểm soát |
| 11 | DONE | DetectionEvent/CrowdDensityRecord/HeatmapRecord; device ingestion idempotent, lịch sử read-only, validation vị trí/JSON/evidence và admin heatmap |
| 12 | DONE | VoiceInteraction/VoiceCommand đúng Logical ERD; device lifecycle/idempotency, state transition, ownership Robot và lịch sử read-only cho ADMIN/STAFF |
| 13 | DONE | ShedLock JDBC/MySQL cho toàn bộ scheduler; retention dry-run/DELETE/ARCHIVE theo policy, batch, audit và metrics; destructive execution mặc định tắt |
| 14 | DONE | Analytics Job/Request/Robot/Delivery/Emergency/Monitoring, time bucket + breakdown, drill-down và CSV giới hạn có audit |

## Database
V1–V12 DEV baselines đã điều chỉnh theo quyết định mới. V4 không còn StepType table; V5 seed tám Workflow DRAFT; V8 thêm nghiệp vụ Phase 7; V9 seed operations/retention metadata; V10 thêm Shift/Slot; V11 thêm Detection/Crowd/Heatmap; V12 thêm VoiceInteraction/VoiceCommand; V13 harden contract Team Pi; V14 thêm `ShedLock`/`RetentionArchive`; V15 thêm analytics indexes; V16 thêm event consumption, QR challenge và physical delivery confirmation theo migration tiến tới. Priority>=1; Account không approval/PATIENT.
ERD Draw.io không sửa. Không xóa database/volume hiện có. DB dev đã apply baseline cũ cần backup/recreate có chủ đích hoặc migration upgrade nếu phải giữ dữ liệu; không tự flyway repair.
Mục tiêu 42 bảng domain/security đã đạt (41 ERD cũ - StepType + RefreshToken/RevokedToken); bổ sung 2 bảng vận hành `ShedLock` và `RetentionArchive`; không tạo Patient hoặc StepType table.

## Verification
AuthFlowIntegrationTest: Admin Staff create, BCrypt/no password response, mustChangePassword restriction, deactivate, token rotation/reuse/logout.
WorkflowGraphIntegrationTest: create/replace transaction, rollback invalid graph, priority0/unknown enum/bad pointer rejected, immutable code/published, validation cả8 seed và absence StepType table.
RequestJobIntegrationTest: anonymous robot request/idempotency/ownership; queue/dispatch; command chứa runtime step key; graph branch và dynamic visit; DATA_SERVICE/INTERACT; cancel request/ACK; reboot đang chạy chuyển RECOVERY_REQUIRED.
WorkflowRuntimeTransitionServiceTest: condition EQ/NE/GT/GTE/LT/LTE/IN/EXISTS, priority và default fallback.
JobStepInputResolverTest: resolve `/inputs`, `/outputs/STEP_CODE`, `/vars` thành giá trị runtime thực tế.
Facility/Robot/bootstrap/architecture regression giữ nguyên sau refactor.
Maven chạy Java 21. Ngày 2026-10-07: fresh MySQL chạy Flyway V1–V15 + Hibernate validate thành công; clean full regression 62 tests, 0 failure, 0 error, 0 skipped. JAR, Docker image và Compose config đều được xác nhận lại khi đóng Phase 14.
Phase10IntegrationTest kiểm tra aggregate Shift/Slot, UUID BINARY(16), overlap, state machine, giữ history, Staff ownership và phân quyền route Admin.
Phase11IntegrationTest kiểm tra ingestion idempotent, cross-map validation, UUID BINARY(16), heatmap uniqueness/read-only và phân quyền.
Phase12IntegrationTest kiểm tra interaction/command lifecycle, idempotency, sequence/timestamp/state guard, UUID BINARY(16), route DEVICE và quyền đọc ADMIN/STAFF.
Phase13IntegrationTest kiểm tra hai lock provider tranh cùng lock, recovery lock hết hạn, retention dry-run, safety switch, bounded DELETE và archive-before-delete.
Phase14IntegrationTest kiểm tra công thức Job/SLA percentile, timezone bucket, delivery/emergency/monitoring metric, breakdown, pagination, CSV audit, input bounds và analytics indexes của MySQL.
Swagger/OpenAPI manual acceptance đã hoàn tất trên Chromium với Bearer JWT và protected API HTTP 200/401. Chưa thực hiện robot hardware acceptance. Phase 6 hoàn thành ở server contract/core runtime; không đồng nghĩa đã kiểm thử phần mềm hoặc phần cứng trên Raspberry Pi thật.

## Cần tiếp tục

Kế hoạch tiếp nối riêng Backend: [backend-remaining-phases.md](backend-remaining-phases.md).

| Phase đề xuất | Phạm vi | Trạng thái |
|---|---|---|
| 15 | Quyền runtime, cấp grant, scope operationKey | DONE — 83/83 regression; chi tiết phase15-runtime-authorization.md |
| 16 | Contract/validation 11 StepType | DONE — schema/result validation đủ 11 enum; 100/100 regression |
| 17 | Context, WAIT/event, NOTIFY/ACK | DONE — durable early event, duration deadline, ACK ANY/ALL, row-lock race safety |
| 18 | Delivery đầy đủ, QR, recovery, SOS | DONE — cargo/stage/physical confirmation, hashed single-use QR, recovery, false-alarm |
| 19 | Schema và dịch vụ thông tin bệnh viện | PLANNED — chờ nghĩa dữ liệu được duyệt |
| 20 | Catalog Workflow và entry point nghiệp vụ | PLANNED — chờ danh mục canonical |
| 21 | Nghiệm thu riêng Backend bằng API/simulator | PLANNED |

Các phase này không bao gồm triển khai Pi, frontend, driver hoặc model AI; nghiệm thu hardware vẫn tách riêng.

### Phase 15 — checkpoint 2026-10-08

- Chặn Device hoàn tất/báo lỗi thay Server/Staff; kiểm cả cấu hình gốc và resolved inputs để mapping không hạ quyền. Callback Patient/display/audio hợp lệ vẫn giữ.
- AuthVerification kiểm role, owner/assigned Job, Account ACTIVE, mustChangePassword, purpose và permission cấu hình; không chấp nhận QR_CODE chưa có proof. Retry cùng actor trả grant còn hiệu lực, không cấp hai lần.
- Step visit kiểm operationKey đúng Robot/Job/Step; khóa Job trong transaction cho runtime/Server execute/grant; terminal Job không tạo visit mới.
- Không đổi schema, Flyway hoặc Workflow đã publish. Không tác động dữ liệu demo.
- `mvn -q -l target/phase15-final-regression.log test` bằng Java 21, MySQL 8.4 Testcontainers: **83 tests / 21 suites, 0 failure, 0 error, 0 skipped**, exit 0. Trong đó 15 unit case và 6 integration case mới; có concurrent visit/grant, audit actor và negative authorization.
- Lần chạy đầu ngày 08 bị lỗi môi trường do Docker chưa chạy; đã khởi động Docker và chạy lại toàn bộ thành công. Không bỏ qua integration test để đánh dấu DONE.
- Chi tiết file/API/logic cũ–mới: [phase15-runtime-authorization.md](phase15-runtime-authorization.md). Chưa nghiệm thu hardware hoặc toàn graph delivery; QR proof và API xác nhận cửa/chốt thuộc Phase 18, contract tổng quát thuộc Phase 16.

### Phase 16 — checkpoint 2026-10-08

- Giữ đúng 11 `StepType` enum; không tạo bảng hoặc CRUD StepType. Read-only catalog hiện hữu tiếp tục dùng `/api/v1/admin/workflows/step-types`.
- Khóa ba version độc lập: transport protocol `1.0`, Step schema `2`, Workflow graph `3`. Workflow/Job có version không được runtime hỗ trợ bị từ chối rõ ràng.
- Publish-time validation kiểm tra contract theo từng StepType, JSON Pointer/reference, condition budget, DATA_SERVICE allowlist và chặn cấu hình URL/SQL/script/code.
- Runtime validation kiểm tra exact root schema, kiểu dữ liệu và success semantics đủ 11 loại trước khi ghi `JobStep=SUCCEEDED`. Áp dụng cho callback Device, Server handler và AuthVerification; result sai rollback và không đổi trạng thái Step.
- Giữ compatibility profile hiện hữu `/inputs`, `/outputs/{STEP_CODE}`, `/vars`; không sửa graph đã publish hoặc trộn syntax nguồn mới vào schema 2. Tại checkpoint Phase 16, `NOTIFY` mới chỉ lưu record; lifecycle delivery/ACK sau đó đã hoàn tất ở Phase 17.
- Tài liệu chuẩn: [step-contract-matrix.md](step-contract-matrix.md).
- `mvn -q -l target/phase16-final-regression.log test` bằng Java 21, MySQL 8.4 Testcontainers: **100 tests / 22 suites, 0 failure, 0 error, 0 skipped**. Bao gồm fixture đúng đủ 11 loại, negative schema/semantic/version và integration rollback result sai.
- Không thay schema, không thêm migration và không tác động database demo. Phần Pi/ROS2/hardware vẫn ngoài phạm vi.

### Phase 17–18 — checkpoint 2026-10-08

- Hoàn thiện `SET_CONTEXT`, WAIT duration/event bền vững, NOTIFY delivery/ACK ANY–ALL và khóa race bằng pessimistic row lock.
- Nối confirmation pickup/handoff vào đúng HANDLE_PAYLOAD Step, đối chiếu item code/quantity và xác nhận đóng/khóa vật lý.
- Bổ sung QR challenge 256-bit chỉ lưu hash, TTL/scope và chống replay; recovery sau tác động vật lý; SOS không phụ thuộc robot rảnh và có lifecycle false-alarm riêng.
- Migration forward V16; không sửa V1–V15 và không tác động database demo. Chi tiết: [phase17-18-runtime-delivery.md](phase17-18-runtime-delivery.md).
- Fresh MySQL Flyway V1–V16 + Hibernate validate thành công. Full regression: **106 test / 24 suite, 0 failure, 0 error, 0 skipped**.

Ưu tiên review 2026-10-07: [backend-completeness-review.md](backend-completeness-review.md) ghi nhận các gap bằng chứng từ code. Phase 15–18 đã đóng các gap quyền/executor/grant/scope operationKey, contract 11 StepType, ACK/context và delivery recovery. Phase 19–21 vẫn còn theo kế hoạch riêng.

0. Bộ nguồn mới được đọc và lưu ngày 2026-10-07: xem [source-baseline.md](source-baseline.md) và [source-alignment-plan.md](source-alignment-plan.md). Có khác biệt thực tế về schema tổng quan, contract StepType, tên workflow, state và transport. Kết quả Phase 1–14 bên trên thuộc baseline cũ; chưa chứng minh tương thích trọn bộ nguồn mới. Lượt lưu nguồn chỉ bổ sung tài liệu; Phase 15 sau đó đã sửa code và kiểm thử như checkpoint phía trên.

1. Tám Workflow đã được Admin validate/publish trong database demo và Swagger acceptance đã hoàn tất; xem `backend-demo-acceptance.md`. Team Pi vẫn cần chạy bảng hardware acceptance `pi-contract-and-hardware-acceptance.md` trên thiết bị thật và review bốn message type Voice trước deploy.
2. Trước khi bật retention destructive, Admin phải cấu hình mode/ngày giữ dữ liệu và bật `retention.execution_enabled`; mặc định hệ thống chỉ dry-run/disabled.
3. Khi triển khai nhiều instance, dùng chung MySQL lock table và theo dõi metric scheduler/retention; business idempotency và row lock vẫn giữ nguyên.
