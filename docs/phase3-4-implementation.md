# Bàn giao Phase 3–4: BE điều phối graph và nhận phản hồi UI

**Ngày:** 12/10/2026. **Phạm vi:** code BE trong workspace. Phase 3–4 có triển khai BE và kiểm thử MySQL; chưa nghiệm thu Pi, broker, frontend hoặc audio client.

## Luồng thực thi

Job schema 2 tiếp tục dùng runtime REST cũ. Job schema 3 mới có `be_orchestrated=true`; BE khởi chạy, chọn bước và kết thúc Job. REST Pi không được khởi chạy/kết thúc/tạo visit hoặc chạy server step của Job thuộc mode này.

```text
Tạo Job + visit PENDING + công việc điều phối [cùng transaction]
  → worker khóa Job, chọn start/transition, lưu selected_at và snapshot
  → transaction tiếp theo chạy server handler của đúng bước đã chọn
  → kết quả tức thì / callback staff / verification / WAIT event
  → lưu completion + tăng generation điều phối [cùng transaction]
  → worker đọc công việc đã commit, chọn transition tiếp theo
  → terminal: cập nhật Job, request, Task và robot
```

Worker mặc định chạy mỗi 500 ms khi `app.operations.scheduler-enabled=true`. `app.workflow.orchestration-delay-ms` đổi chu kỳ; `app.workflow.deadline-check-ms` mặc định 1000 ms. Profile test tắt scheduler; test gọi worker trực tiếp để kiểm từng transaction.

Workflow schema 3 có MOVE, CONTROL_HARDWARE, EXECUTE_SENSING, WAIT/DURATION, NOTIFY hoặc SPEAK vẫn bị chặn publish/tạo Job. Các bước đó cần tích hợp ở Phase 5–10/12. Workflow chỉ gồm các bước BE đã nối có thể validate, publish và tạo Job qua API hiện có. Selector `wait_type`, `action`, `service_key` hiện cần cấu hình cố định trong params; readiness chưa cho ghi đè selector bằng input mapping.

## Phase 3 — Những phần đã triển khai

| Phần | Code và hành vi |
|---|---|
| Migration | [V20](../src/main/resources/db/migration/V20__be_orchestration_and_ui_responses.sql): mode Job, con trỏ selected JobStep, `selected_at`, hàng đợi và bản ghi callback |
| Công việc bền vững | [OrchestrationWorkService](../src/main/java/com/mediassistrover/backend/service/OrchestrationWorkService.java): một dòng/Job, requested/processed generation, khóa Job trước khi ghi |
| Điều phối | [WorkflowOrchestratorService](../src/main/java/com/mediassistrover/backend/service/WorkflowOrchestratorService.java): chọn theo graph, tái dùng đúng PENDING chưa chọn, tạo visit mới cho loop |
| Worker/deadline | [WorkflowOrchestrationScheduler](../src/main/java/com/mediassistrover/backend/config/WorkflowOrchestrationScheduler.java): xử lý công việc đã commit, đánh thức Job sẵn sàng và kiểm deadline |
| Server execution | [ServerStepExecutionService](../src/main/java/com/mediassistrover/backend/service/ServerStepExecutionService.java): chạy tại BE, không cần DevicePrincipal/HTTP nội bộ |
| Tạo Job | [JobExecutionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobExecutionServiceImpl.java): tạo Job schema 3 và công việc trong transaction; không tạo DISPATCH cả Job |
| Gate triển khai | [WorkflowExecutionReadiness](../src/main/java/com/mediassistrover/backend/validation/WorkflowExecutionReadiness.java): kiểm handler/transport sẵn sàng khi publish và tạo Job |

`maxVisits` và `maxTotalVisits` tính theo visit có `selected_at`, không tính toàn bộ PENDING tạo sẵn. `executionSequenceNo` phản ánh thứ tự chọn thực tế để lấy output gần nhất trong loop. Mỗi visit mới có `operationKey` riêng; snapshot không resolve lại trong lúc chờ UI/Pi.

Task tổng kết theo visit đã chọn khi Job terminal; Task không đi qua nhận NOT_VISITED. Nếu một visit thất bại rồi graph xử lý thành công, Job có thể SUCCEEDED nhưng Task chứa visit thất bại vẫn ghi FAILED để giữ dấu vết.

Selection commit trước khi chạy handler. Handler bị từ chối làm transaction thực thi rollback; worker ghi SERVER_STEP_REJECTED trong transaction mới rồi đi nhánh FAILURE. Lỗi chọn graph/resolve không xác định được bước an toàn chuyển Job sang RECOVERY_REQUIRED. Lỗi hạ tầng giữ công việc chưa xử lý để worker thử lại.

Bước Pi đã chọn chỉ có snapshot và PENDING, chờ Phase 5–7. BE không tự tạo kết quả WAIT/DURATION. Scheduler timeout REST cũ chỉ lấy Job ngoài mode BE để tránh hoàn tất nhầm bước Pi.

Hủy server wait hoặc Job QUEUED chưa chạy có thể kết thúc tại BE. Khi đã chọn/thực thi bước vật lý, hủy hoặc vượt thời gian chuyển recovery và giữ robot. CANCEL_STEP, xác nhận dừng và recovery journal đầy đủ thuộc Phase 8.

## Phase 4 — Handler và callback

- CHECK_CONDITION/SET_CONTEXT là SERVER cho schema 3; schema 2 giữ executor cũ qua policy theo version.
- [SafeConditionEvaluator](../src/main/java/com/mediassistrover/backend/validation/SafeConditionEvaluator.java) dùng chung cho transition và [condition handler](../src/main/java/com/mediassistrover/backend/service/impl/CheckConditionStepHandler.java). Chỉ so sánh JSON cố định, không có script engine. `value=false` vẫn là step SUCCEEDED.
- SET_CONTEXT schema 3 trả `updated`; schema 2 giữ `changed_keys`/`values`. Context và completion commit cùng nhau.
- WAIT/EVENT tái dùng event lưu sẵn, kể cả event đến trước WAIT, tiêu thụ một lần. Output mới chứa `event_type`, `correlation_key`, `occurred_at`, `data`.
- [Completion service](../src/main/java/com/mediassistrover/backend/service/impl/RuntimeStepCompletionServiceImpl.java) khóa Job trước Step, lưu completion và wake-up cùng transaction. Worker đọc sau commit.
- Staff/QR verification xuất output theo version và resume Job. Generic callback không cấp verification.

### API phản hồi

`POST /api/v1/job-steps/{jobStepId}/responses`

Header Authorization của STAFF/ADMIN và `Idempotency-Key` dài 1–150 ký tự: chữ, số, `.`, `_`, `:`, `-`.

```json
{
  "responseType": "DOOR_CLOSED",
  "data": { "closed": true },
  "note": "Đã đóng chốt"
}
```

| responseType | Instruction | data |
|---|---|---|
| UI_DISPLAYED | INTERACT/DISPLAY | `prompt_key` phải khớp |
| INTERACTION_CONFIRMED | CONFIRM/CAPTURE_INPUT thường | `confirmed`, `values?` |
| DOOR_CLOSED | Prompt đóng chốt trong contract | `closed` |
| LOCK_CHECKED | Prompt kiểm khóa trong contract | `locked` |
| PAYLOAD_CONFIRMED | HANDLE_PAYLOAD/LOAD hoặc UNLOAD | `confirmed`, `items` đúng snapshot BE |

[Schema callback](schemas/staff-step-response.schema.json) chặn field dư ở body/data. Actor lấy từ phiên đăng nhập. Endpoint chỉ nhận step schema 3 đang được chọn và RUNNING, đúng scope, trước deadline, chưa yêu cầu hủy. Yêu cầu ADMIN trong definition không thể bị mapping hạ quyền.

Cùng step/key/actor/body trả receipt cũ (`duplicate=true`), kể cả sau khi Job terminal. Cùng key nhưng body/actor khác trả conflict. Key mới cho bước đã kết thúc bị chặn. Record callback, completion, event và yêu cầu chuyển bước commit cùng transaction.

`confirmed=false`, `closed=false`, `locked=false` là phản hồi hoàn tất hợp lệ; graph chọn nhánh. Xác nhận cargo không tự xác nhận đóng/khóa chốt hoặc ghi milestone pickup/delivery mới. LOCK_CHECKED có nguồn STAFF_MANUAL và không sửa hardware `verified`.

Code: [controller](../src/main/java/com/mediassistrover/backend/controller/job/JobStepResponseController.java), [service](../src/main/java/com/mediassistrover/backend/service/JobStepResponseService.java), [validator](../src/main/java/com/mediassistrover/backend/validation/StaffStepResponseValidator.java).

### SSE và reconnect

`GET /api/v1/jobs/{jobId}/stream` trả `text/event-stream`, event `job-state` với dữ liệu Job detail như REST GET. `job.selectedJobStepId`, `job.beOrchestrated` và `step.selectedAt` giúp UI xác định instruction. Step chờ UI có instruction trong `resultJson`, input snapshot trong `resolvedInputsJson`.

[JobUiEventStream](../src/main/java/com/mediassistrover/backend/service/JobUiEventStream.java) gửi snapshot ban đầu, update sau commit và heartbeat snapshot mỗi 20 giây. Stream tối đa 5 phút hoặc đến khi token hết hạn; UI reconnect bằng token hợp lệ và tải lại REST GET. SSE chưa có replay log riêng.

[JobUiSnapshotService](../src/main/java/com/mediassistrover/backend/service/JobUiSnapshotService.java) kiểm lại scope, trạng thái account, role, token version, expiry/revocation mỗi lần gửi. Mất quyền sẽ đóng stream. Security cho phép dispatcher ASYNC hoàn tất luồng đã xác thực; request mở luồng ban đầu vẫn cần token/role đúng.

## Kiểm thử và giới hạn nghiệm thu

- [BeOrchestrationIntegrationTest](../src/test/java/com/mediassistrover/backend/BeOrchestrationIntegrationTest.java): nhánh false, sequence khác start, loop/operationKey, giới hạn visit, callback trùng/sai loại/quyền/quá hạn, event sớm, snapshot Pi, duration, cancellation/recovery, tạo Job và publish subset BE.
- Test dùng transaction commit thật để kiểm handler rollback, công việc lưu DB, rollback callback không lưu completion/wake-up, hai callback đồng thời cùng key chỉ hoàn tất một lần, HTTP và SSE update/đóng luồng khi mất quyền. Receipt/work được đọc có khóa sau Job lock để tránh snapshot cũ của MySQL REPEATABLE READ.
- Staff/QR resume và cargo → close → lock-check có test BE; không thay cho delivery có MOVE/UNLOCK/LOCK trên robot.
- [SafeConditionEvaluatorTest](../src/test/java/com/mediassistrover/backend/SafeConditionEvaluatorTest.java): equality số, thiếu field/null, numeric không ép kiểu và từ chối code.
- Tái chạy test legacy authorization, transition, context/event, request/delivery và schema migration.

**Ngoài repo:** chưa có frontend/Pi/audio/broker để nghiệm thu. NOTIFY schema 3/WAIT ACK thuộc Phase 9; quyền UNLOCK và milestone delivery thuộc Phase 10. Draft V19 chứa bước chưa sẵn sàng không tự publish. Phase tiếp theo: MQTT connection/handshake (5), outbox/inbox (6), dispatch từng hành động (7).

**Kết quả cuối (Java 21, MySQL 9.5, DB test riêng `be_mar_phase34_test_20261012`):**

- 22 test mới đạt: 18 orchestration/integration + 4 SAFE_EXPR.
- Toàn suite: 219 test, 217 đạt, 1 fail analytics đã có trên baseline V18, 1 skip vì Docker không chạy (`Phase19MigrationTest`). Không còn lỗi test do thay đổi Phase 3–4.
- Lỗi cũ: `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`, MySQL 9.5 trả `possible_keys=null` thay vì chứa `IX_Job_queued_at`. Baseline V18 đã có cùng lỗi tại lần kiểm Phase 1–2; không sửa test/index analytics để che lỗi.
- Build cuối dùng thư mục riêng `target/phase34-verification` để tránh IDE ghi đè class trong `target/classes`/`target/test-classes`. Log: `target/phase34-isolated-full-tests.log`; reports: `target/phase34-verification/surefire-reports`. POM tạm đã bỏ; `pom.xml` gốc giữ cấu hình build hiện có.
- Contract checker đạt 9 schema/129 fixture/11 StepType; 4 contract đóng gói khớp tài liệu (bỏ annotation comment khi so sánh). Các link Markdown đã kiểm tồn tại; `git diff --check` không có lỗi whitespace.

Chỉ chạy migration và test trên DB test riêng; chưa triển khai lên DB ứng dụng. Test BE không chứng minh robot thật đã chạy.

**Cập nhật tiếp nối:** [Phase 5–7](phase5-7-implementation.md) đã thêm MQTT/TLS, inbox/outbox và dispatch từng Pi step với broker local/simulator. Các mô tả “chờ MQTT” phía trên phản ánh thời điểm bàn giao Phase 3–4; timeout/CANCEL_STEP đầy đủ vẫn thuộc Phase 8, robot thật chưa nghiệm thu.
