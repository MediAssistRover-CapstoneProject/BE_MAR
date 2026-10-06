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
| 7 | IMPLEMENTED | Delivery/Alert/Emergency/AuthVerification, device SOS, audit và test end-to-end đã có trong source |
| 8 | IMPLEMENTED; PENDING VERIFY | Scheduler flag thống nhất; cleanup/offline/due/timeout có khóa race và inclusive deadline; report, setting precedence, retention metadata và test đã có |
| 9 | IMPLEMENTED; PENDING VERIFY | JUnit5/Mockito/MockMvc/Testcontainers, OpenAPI acceptance, CI verify/JAR/image, Compose và Nginx HTTPS production đã có; chưa thể chạy lại suite ngày 2026-10-06 |

## Database
V1–V9 DEV baselines đã điều chỉnh theo quyết định mới. V4 không còn StepType table; V5 seed tám Workflow DRAFT; V8 thêm nghiệp vụ Phase 7; V9 seed operations/retention metadata; priority>=1; Account không approval/PATIENT.
ERD Draw.io không sửa. Không xóa database/volume hiện có. DB dev đã apply baseline cũ cần backup/recreate có chủ đích hoặc migration upgrade nếu phải giữ dữ liệu; không tự flyway repair.
Mục tiêu bảng được điều chỉnh 42 thay vì43 trước đây (41 ERD cũ - StepType + RefreshToken/RevokedToken). Số mục tiêu không phải số Entity Java đã hoàn thành.

## Verification
AuthFlowIntegrationTest: Admin Staff create, BCrypt/no password response, mustChangePassword restriction, deactivate, token rotation/reuse/logout.
WorkflowGraphIntegrationTest: create/replace transaction, rollback invalid graph, priority0/unknown enum/bad pointer rejected, immutable code/published, validation cả8 seed và absence StepType table.
RequestJobIntegrationTest: anonymous robot request/idempotency/ownership; queue/dispatch; command chứa runtime step key; graph branch và dynamic visit; DATA_SERVICE/INTERACT; cancel request/ACK; reboot đang chạy chuyển RECOVERY_REQUIRED.
WorkflowRuntimeTransitionServiceTest: condition EQ/NE/GT/GTE/LT/LTE/IN/EXISTS, priority và default fallback.
JobStepInputResolverTest: resolve `/inputs`, `/outputs/STEP_CODE`, `/vars` thành giá trị runtime thực tế.
Facility/Robot/bootstrap/architecture regression giữ nguyên sau refactor.
Maven chạy Java21; MySQL8.4 Testcontainers fresh Flyway V1–V7 + Hibernate validate. Ngày 2026-10-05: 10 test suites, 31 tests, 0 failure, 0 error, 0 skipped.
Ngày 2026-10-06 đã bổ sung test Phase 7–9 và hardening concurrency/deadline, nhưng chưa chạy lại được vì môi trường thiếu `mvn`/Docker trong PATH và quyền tải Maven dependency không được cấp. Theo DoD, Phase 8–9 chưa đánh dấu DONE trước khi CI hoặc local `mvn verify` thực sự pass.
Chưa thực hiện robot hardware acceptance hoặc bấm Swagger thủ công. Phase 6 hoàn thành ở server contract/core runtime; không đồng nghĩa đã kiểm thử phần mềm hoặc phần cứng trên Raspberry Pi thật.

## Cần tiếp tục
1. Chạy `mvn verify`/CI và `docker compose --profile production config` để chuyển Phase 8–9 từ PENDING VERIFY sang DONE.
2. Thêm ShedLock MySQL trước khi chạy nhiều instance; retention vẫn chỉ là metadata, không tự xóa lịch sử.
3. Scope assignment Staff/Floor/Room/ca trực, Shift/Slot; history sensing/voice schema coverage.
4. Team Pi review input/output/action của 8 DRAFT seeds trước publish và thực hiện hardware acceptance riêng.
