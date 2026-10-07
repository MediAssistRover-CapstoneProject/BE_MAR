# MediAssistRover Backend — Implementation Status
Cập nhật: 2026-10-07

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
V1–V12 DEV baselines đã điều chỉnh theo quyết định mới. V4 không còn StepType table; V5 seed tám Workflow DRAFT; V8 thêm nghiệp vụ Phase 7; V9 seed operations/retention metadata; V10 thêm Shift/Slot; V11 thêm Detection/Crowd/Heatmap; V12 thêm VoiceInteraction/VoiceCommand; V13 harden contract Team Pi; V14 thêm `ShedLock`/`RetentionArchive`; V15 thêm analytics indexes. Priority>=1; Account không approval/PATIENT.
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
1. Tám Workflow đã được Admin validate/publish trong database demo và Swagger acceptance đã hoàn tất; xem `backend-demo-acceptance.md`. Team Pi vẫn cần chạy bảng hardware acceptance `pi-contract-and-hardware-acceptance.md` trên thiết bị thật và review bốn message type Voice trước deploy.
2. Trước khi bật retention destructive, Admin phải cấu hình mode/ngày giữ dữ liệu và bật `retention.execution_enabled`; mặc định hệ thống chỉ dry-run/disabled.
3. Khi triển khai nhiều instance, dùng chung MySQL lock table và theo dõi metric scheduler/retention; business idempotency và row lock vẫn giữ nguyên.
