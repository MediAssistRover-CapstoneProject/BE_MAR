# MediAssistRover Backend — Implementation Status
Cập nhật: 2026-10-06

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
| 13 | PLANNED | Multi-instance & Operations Scalability: ShedLock dùng MySQL cho scheduler và retention tự động xóa/archive theo policy |
| 14 | PLANNED | Analytics & Advanced Reports: báo cáo xu hướng, SLA, utilization, incident/monitoring và export có kiểm soát |

## Database
V1–V12 DEV baselines đã điều chỉnh theo quyết định mới. V4 không còn StepType table; V5 seed tám Workflow DRAFT; V8 thêm nghiệp vụ Phase 7; V9 seed operations/retention metadata; V10 thêm Shift/Slot; V11 thêm Detection/Crowd/Heatmap; V12 thêm VoiceInteraction/VoiceCommand; V13 harden contract Team Pi cho tám DRAFT seed và bảo đảm một fall incident trên mỗi detection. Priority>=1; Account không approval/PATIENT.
ERD Draw.io không sửa. Không xóa database/volume hiện có. DB dev đã apply baseline cũ cần backup/recreate có chủ đích hoặc migration upgrade nếu phải giữ dữ liệu; không tự flyway repair.
Mục tiêu 42 bảng đã đạt (41 ERD cũ - StepType + RefreshToken/RevokedToken); không tạo Patient hoặc StepType table.

## Verification
AuthFlowIntegrationTest: Admin Staff create, BCrypt/no password response, mustChangePassword restriction, deactivate, token rotation/reuse/logout.
WorkflowGraphIntegrationTest: create/replace transaction, rollback invalid graph, priority0/unknown enum/bad pointer rejected, immutable code/published, validation cả8 seed và absence StepType table.
RequestJobIntegrationTest: anonymous robot request/idempotency/ownership; queue/dispatch; command chứa runtime step key; graph branch và dynamic visit; DATA_SERVICE/INTERACT; cancel request/ACK; reboot đang chạy chuyển RECOVERY_REQUIRED.
WorkflowRuntimeTransitionServiceTest: condition EQ/NE/GT/GTE/LT/LTE/IN/EXISTS, priority và default fallback.
JobStepInputResolverTest: resolve `/inputs`, `/outputs/STEP_CODE`, `/vars` thành giá trị runtime thực tế.
Facility/Robot/bootstrap/architecture regression giữ nguyên sau refactor.
Maven chạy Java 21. Ngày 2026-10-06: fresh MySQL chạy Flyway V1–V13 + Hibernate validate thành công; full regression 55 tests, 0 failure, 0 error, 0 skipped. JAR package build thành công. Development/production Compose và Docker image gần nhất đã được xác nhận ở checkpoint Phase 10.
Phase10IntegrationTest kiểm tra aggregate Shift/Slot, UUID BINARY(16), overlap, state machine, giữ history, Staff ownership và phân quyền route Admin.
Phase11IntegrationTest kiểm tra ingestion idempotent, cross-map validation, UUID BINARY(16), heatmap uniqueness/read-only và phân quyền.
Phase12IntegrationTest kiểm tra interaction/command lifecycle, idempotency, sequence/timestamp/state guard, UUID BINARY(16), route DEVICE và quyền đọc ADMIN/STAFF.
Chưa thực hiện robot hardware acceptance hoặc bấm Swagger thủ công. Phase 6 hoàn thành ở server contract/core runtime; không đồng nghĩa đã kiểm thử phần mềm hoặc phần cứng trên Raspberry Pi thật.

## Cần tiếp tục
1. Phase 13: thêm ShedLock MySQL trước khi chạy nhiều Backend instance; triển khai retention job xóa/archive theo metadata hiện có, theo batch và có audit/metrics.
2. Phase 14: mở rộng báo cáo cơ bản thành analytics/report nâng cao; chưa tính vào phạm vi DONE hiện tại.
3. Review tĩnh input/output/action của 8 DRAFT seeds đã hoàn tất và có semantic gate; chưa tự publish. Team Pi cần chạy bảng hardware acceptance `pi-contract-and-hardware-acceptance.md` trên thiết bị thật và review bốn message type Voice trước deploy.
