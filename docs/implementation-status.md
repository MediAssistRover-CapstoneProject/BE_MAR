# MediAssistRover Backend — Implementation Status
Cập nhật: 2026-10-05

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
| 7–9 | Chưa hoàn tất | Delivery/Alert/Emergency/AuthVerification, report/schema coverage, robot acceptance và mở rộng tests |

## Database
V1–V7 DEV baselines đã điều chỉnh theo quyết định mới. V4 không còn StepType table; V5 seed tám Workflow DRAFT; priority>=1; Account không approval/PATIENT.
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
Chưa thực hiện robot hardware acceptance hoặc bấm Swagger thủ công. Phase 6 hoàn thành ở server contract/core runtime; không đồng nghĩa đã kiểm thử phần mềm hoặc phần cứng trên Raspberry Pi thật.

## Cần tiếp tục
1. Phase7 DeliveryRequest/DeliveryItem, Alert/AlertRecipient, EmergencyIncident/AuthVerification và device SOS API.
2. Bổ sung server handler nghiệp vụ Phase7 cho AUTHENTICATE/HANDLE_PAYLOAD/NOTIFY/emergency; type chưa hỗ trợ tiếp tục fail-closed, không sinh kết quả giả.
3. Scope assignment Staff/Floor/Room/ca trực, Shift/Slot; history sensing/voice schema coverage.
4. Report cơ bản, multi-instance ShedLock khi triển khai, retention chuẩn bị nhưng chưa tự xóa.
5. Team Pi review input/output/action của 8 DRAFT seeds trước publish và thực hiện hardware acceptance riêng.
