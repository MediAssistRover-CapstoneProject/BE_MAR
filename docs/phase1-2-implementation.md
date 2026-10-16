# Bàn giao code Phase 1–2 — 12/10/2026

**Cập nhật tiếp theo:** [Phase 3–4](phase3-4-implementation.md) đã bổ sung orchestrator và callback BE. Gate schema 3 hiện cho phép subset BE đã tích hợp; các mô tả “chưa có orchestrator/callback” dưới đây ghi trạng thái tại lần bàn giao Phase 1–2.

Đã triển khai kiểm tham số, snapshot input, version hóa output và migration workflow trên BE. MQTT/orchestrator và callback schema 3 chưa chạy: workflow mới giữ DRAFT, có gate chặn publish/tạo Job. Những kết quả dưới đây là kiểm thử BE, chưa phải nghiệm thu robot thật.

## Phase 1 — Tham số và snapshot

| Điểm kiểm | Code và hành vi |
|---|---|
| Validate/publish | [WorkflowStepContractValidator](../src/main/java/com/mediassistrover/backend/validation/WorkflowStepContractValidator.java) kiểm params/mapping, merged template, input mặc định, tên input/output và scope DB của UUID literal |
| Contract 11 StepType/action | [StepInputContractValidator](../src/main/java/com/mediassistrover/backend/validation/StepInputContractValidator.java) kiểm field/type/enum/UUID/required/bounds/timeout và cấu trúc `$ref`; hoãn giá trị tham chiếu tới runtime |
| Sau resolve | [Resolver](../src/main/java/com/mediassistrover/backend/service/impl/JobStepInputResolverImpl.java) giữ mapping ghi đè params, kiểm giá trị và scope trước khi caller lưu resolvedInputsJson/chạy handler |
| Scope | [StepInputScopeValidator](../src/main/java/com/mediassistrover/backend/validation/StepInputScopeValidator.java) kiểm map/endpoint/zone/group tồn tại và cùng map; endpoint MOVE active/accessible, robot ở đúng map, request đúng Job, hàng đúng snapshot |
| Snapshot Job | [Factory](../src/main/java/com/mediassistrover/backend/service/impl/RequestJobInputSnapshotFactory.java) chạy trong transaction tạo Job; identity lấy từ ServiceRequest và chi tiết Navigation/Escort/Delivery trong DB |
| Gate params MQTT | `validatePiParams` yêu cầu một action, target cụ thể, không có `$ref`/field ngoài contract; dispatcher sẽ dùng tại Phase 7 |

- MOVE: tốc độ hữu hạn `>0`, `<=0.3 m/s`. Schema 2 chưa cấu hình mode/speed nhận `NORMAL`/`0.05` từ BE; schema 3 phải cấu hình cả hai.
- WAIT/DURATION: `1–3600s`; timeout tối thiểu duration + 1, kể cả duration thập phân. WAIT/EVENT schema 2 giữ fallback correlation theo operationKey; schema 3 bắt buộc correlation_key.
- AI: window `0–30s`, timeout tối thiểu window + 1. ROBOT_HEALTH/SIDE_CLEARANCE chỉ nhận sensing_type, không nhận field lấy mẫu AI.
- Hardware: field nghiệp vụ, `CART_MAIN`, `maxAttempts=1`; GPIO/PWM/servo ID không thuộc input BE. Schema 2 giữ alias LOCK/UNLOCK và metadata request; gate wire Pi không nhận metadata request.
- CHECK_CONDITION dùng SAFE_EXPR; SET_CONTEXT chỉ ghi `/vars/<identifier>`, không trùng path, INCREMENT cần number. DATA_SERVICE chỉ cho service_key có handler; không chạy URL/SQL/script/code tùy ý.
- Delivery: Job.inputJson có `items` (item_code/quantity), `delivery_items` (ID/tên/loại/đơn vị/nhạy cảm/hướng dẫn/ghi chú). Thay đổi request hoặc DeliveryItem sau tạo Job không đổi snapshot. HANDLE_PAYLOAD cũ chưa mapping items được bổ sung từ snapshot khi resolve.
- Client `data` không làm nguồn verification, actor/quyền/context/job identity; factory loại field quyền dành riêng trong object/array con. Identity nghiệp vụ và hàng bị ghi đè bằng DB. Việc này không cấp quyền mở chốt; authorization thật thuộc Phase 10.

Caller hiện có lưu resolved input trước handler hoặc khi nhận STEP_STARTED, giữ khi retry cùng visit. **Snapshot command trước MQTT publish**, đối chiếu STEP_STARTED, outbox và operation idempotency thuộc Phase 6–7. REST DISPATCH hiện tại vẫn chứa workflow/Job; loại cơ chế này thuộc Phase 14.

## Phase 2 — Output schema 3 và migration

[StepResultContractValidator](../src/main/java/com/mediassistrover/backend/validation/StepResultContractValidator.java) chọn validator theo **schemaVersion của Job**. Schema 2 giữ rule cũ. [Validator schema 3](../src/main/java/com/mediassistrover/backend/validation/Schema3StepResultContractValidator.java) dùng [schema đóng gói](../src/main/resources/contracts/mar-step-2/) và kiểm thêm quan hệ với resolved input/Job.

| Output schema 3 | Kiểm chính |
|---|---|
| MOVE | Đủ endpoint_group_id=null và attempts; endpoint/attempts cùng target; reached=true/action_status=4 |
| Hardware | Đủ 9 field, action/compartment khớp input; profile bàn giao ID4/1500/2400, controller_ack/verified=false; publish không chứng minh đã khóa |
| Health/clearance | sensing_type, đủ metrics nullable; battery uint16 chưa xác minh phần trăm; clearance cần range fresh |
| AI | Đúng map Job, zone input, model cấu hình nếu có; tuổi mẫu tối đa 2s tại lúc tạo RESULT. window=0 nhận cache mới; window>0 cần mẫu sau STEP_STARTED |
| MAPPING | START/SAVE/STOP, session/artifact đúng input; upload state, pixel/resolution/dimensions. Không tạo Map hoặc lưu artifact chỉ nhờ output hợp lệ |
| INTERACT | source/actor/time/action/prompt; đóng/kiểm khóa cần staff source/actor |
| AUTHENTICATE/HANDLE_PAYLOAD | Purpose/request/action/items đúng input, actor/time đúng kiểu. Denied authentication hoặc confirmed=false được graph chọn nhánh |
| NOTIFY | alert_id, persisted=true, requires_ack khớp input; không dùng delivered/acked schema 2 |
| WAIT/SET_CONTEXT | EVENT đúng type/correlation; DURATION elapsed đủ. SET_CONTEXT trả updated đúng key/value/kiểu INCREMENT |
| DATA_SERVICE/CHECK_CONDITION | Giữ request_id và dữ liệu bệnh viện; kiểm theo service_key. CHECK_CONDITION false vẫn là completion hợp lệ |

AI được giữ field mở rộng theo tài liệu; output khác kiểm field dư theo schema. [FixedContractSchemaValidator](../src/main/java/com/mediassistrover/backend/validation/FixedContractSchemaValidator.java) chỉ xử lý vocabulary của schema đóng gói, không nhận schema người dùng/remote ref, báo lỗi với keyword chưa hỗ trợ. Không phải thư viện JSON Schema tổng quát.

### Migration và tương thích

[V19](../src/main/resources/db/migration/V19__step_schema_3_contract_drafts.sql) thêm constraint RECOVERY_REQUIRED; clone 10 workflow canonical và WF_NAVIGATION sang version mới, UUID mới cho Workflow/Task/Step/Transition. Không sửa V1–V18 hoặc graph schema 2. Start/transition remap UUID; các predicate `value`/`confirmed`/`verified` còn tương thích nên giữ.

Graph mới cấu hình MOVE; dữ liệu UI/NOTIFY vào data; WAIT/EVENT có correlation; HANDLE_PAYLOAD có action/items; hardware bỏ request metadata. [Initializer](../src/main/java/com/mediassistrover/backend/config/Schema3DraftCompilationInitializer.java) compile definition/hash sau Flyway, giữ DRAFT. [Runtime guard](../src/main/java/com/mediassistrover/backend/service/impl/JobStepRuntimeGuardImpl.java) chặn đi tiếp/kết thúc thành công khi có Step cần recovery.

Initializer dùng marker migration một lần, không tự compile lại draft admin đã sửa ở lần khởi động sau. Có thể validate thủ công qua API hiện có (chuyển VALIDATED), nhưng gate publish vẫn chặn schema 3. JOB_ERROR/CANCEL_ACK cũ gặp Step RECOVERY_REQUIRED giữ Job/robot để reconcile, không giải phóng robot.

Schema 3 được validate/compile nhưng **chặn publish và tạo Job qua execution service hiện tại**. Handler/callback schema 3, executor CHECK_CONDITION/SET_CONTEXT, NOTIFY + WAIT ACK, authorization và lifecycle mapping thuộc các phase tương ứng. Validator nhận output không chứng minh những handler này đã chạy.

## Kiểm thử và phần chưa xác nhận

- [Input](../src/test/java/com/mediassistrover/backend/StepInputContractValidatorTest.java): đủ 11 loại; sai field/type/UUID/bounds, mapping/default, timeout, retry và gate Pi.
- [Scope](../src/test/java/com/mediassistrover/backend/StepInputScopeValidatorTest.java): cross-map, inaccessible, zone/group và request ngoài scope.
- [Resolver](../src/test/java/com/mediassistrover/backend/JobStepInputResolverTest.java), [factory](../src/test/java/com/mediassistrover/backend/RequestJobInputSnapshotTest.java), [delivery integration](../src/test/java/com/mediassistrover/backend/DeliveryWorkflowAcceptanceTest.java): snapshot độc lập dữ liệu mutable, chống chèn `$ref`/quyền giả.
- [Output](../src/test/java/com/mediassistrover/backend/Schema3StepResultContractValidatorTest.java): 11 loại và sub-action; field thiếu/dư, semantic mismatch, map/zone/model/time AI; giữ schema 2 regression riêng.
- [Migration](../src/test/java/com/mediassistrover/backend/Schema3MigrationIntegrationTest.java): 11 clone compile, UUID/graph remap, DRAFT/publish gate và lưu recovery MySQL.

DB kiểm thử riêng `be_mar_contract_test_20261012`, MySQL local 9.5; không dùng DB ứng dụng. Schema offline: 9 schema/129 fixture đạt. Fixture minh họa không gửi robot.

Kết quả kiểm tra:

- Bộ kiểm tra cuối liên quan Phase 1–2: 127 test đạt, không fail/skip; sau đó 18 test runtime/migration/recovery đều đạt, gồm 1 regression recovery mới (tổng 128 test liên quan khác nhau). Log [phase12-final-tests.log](../target/phase12-final-tests.log) và [recovery-final](../target/phase2-recovery-final-tests.log).
- Toàn suite trước regression cuối: 195 test, 193 đạt, 1 fail, 1 skip. Test fail là `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`, assertion `EXPLAIN possible_keys` trên MySQL 9.5; không thay code analytics/index trong lượt này. Chạy riêng cùng test với database mới chỉ migration V1–V18 vẫn fail giống hệt: [log baseline V18](../target/v18-analytics-baseline-test.log).
- Test bị skip là `Phase19MigrationTest` cần Docker/MySQL 8.4. V19 và graph schema 3 đã chạy integration trên MySQL local 9.5, chưa kiểm bản 8.4 trong lượt này.

Log trong target là artifact local bị git ignore; người khác cần chạy lại để tạo log. Các test chọn rõ schema 2 cho fixture runtime cũ, không vô tình chạy version schema 3 mới khi kiểm regression.

Broker/credential, UUID/hash/profile robot/map thật, code Pi/UI/AI chưa có bằng chứng trong workspace. Chưa có MQTT integration hoặc robot test; command snapshot bền vững, ACK/replay/cancel và xác thực actuator chưa nghiệm thu.

**Cập nhật tiếp nối:** [Phase 3–4](phase3-4-implementation.md) đã nối BE orchestrator/UI callback; [Phase 5–7](phase5-7-implementation.md) thêm MQTT/TLS và snapshot/outbox/inbox với broker local. Giới hạn phía trên phản ánh thời điểm Phase 1–2; robot thật, CANCEL_STEP/recovery đầy đủ và authorization UNLOCK vẫn chưa nghiệm thu.
