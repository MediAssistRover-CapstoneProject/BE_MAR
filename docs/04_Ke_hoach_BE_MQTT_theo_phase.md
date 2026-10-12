# Kế hoạch BE cấu hình tham số và điều phối từng hành động xuống Pi

**Ngày đối chiếu:** 12/10/2026.  
**Baseline:** code hiện tại trong `BE_MAR` và năm tài liệu được cung cấp.  
**Trạng thái bàn giao:** Phase 0–9 đã có phần BE và kiểm broker local/simulator execute/cancel. Phase 10 bổ sung binding quyền mở và mốc delivery tại BE; chưa nghiệm thu toàn phase vì Pi thiếu STAFF_VERIFIED. Phase 11 có chọn endpoint group, profile ACK và zone config tại BE; client Pi/AI và navigation thật chưa nghiệm thu. Phase 12 có phiên mapping, upload/commit artifact, preview và STOP cleanup tại BE; SLAM/upload Pi và UI chưa nghiệm thu. Phase 13–14 chưa triển khai. Pi/hardware/UI/AI và broker sản xuất chưa nghiệm thu.
**Phạm vi:** phần BE, kèm contract/checklist Pi/UI/AI. Xem [Phase 1–2](phase1-2-implementation.md), [Phase 3–4](phase3-4-implementation.md), [Phase 5–7](phase5-7-implementation.md), [Phase 8–9](phase8-9-implementation.md), [Phase 10](phase10-implementation.md), [Phase 11](phase11-implementation.md) và [Phase 12](phase12-implementation.md). Schema 2 giữ cách chạy cũ. Schema 3 chỉ publish/tạo Job với subset đã tích hợp và cấu hình MQTT phù hợp; MOVE cần profile ACK theo phiên. UNLOCK, AI và SPEAK còn gate dependency; MAPPING/artifact mở qua cấu hình và capability Pi theo Phase 12. MQTT/dispatch/mapping mặc định tắt.

## 1. Mục tiêu và hiện trạng

BE giữ toàn bộ Job, workflow, tham số, context và quyền chọn bước tiếp theo. Pi chỉ nhận một hành động cụ thể đã resolve tham số, thực hiện rồi trả kết quả. Sau khi kiểm tra kết quả hoặc nhận xác nhận từ staff, BE mới điều phối hành động tiếp theo.

“Từng chi tiết nhỏ” được chốt là tách từng hành động nghiệp vụ. Mỗi hành động có JobStep, input, output và điều kiện hoàn tất riêng. Các tham số của một hành động được gửi cùng nhau trong một command; BE không gửi toàn bộ Job xuống Pi.

Ví dụ delivery:

```text
MOVE đến điểm lấy                         [Pi]
→ AUTHENTICATE người giao                 [BE]
→ UNLOCK                                 [Pi]
→ HANDLE_PAYLOAD xác nhận xếp hàng        [BE/UI]
→ INTERACT xác nhận đóng chốt             [BE/UI]
→ LOCK                                   [Pi]
→ INTERACT kiểm tra chốt đã khóa          [BE/UI]
→ MOVE đến điểm giao                      [Pi]
→ AUTHENTICATE người nhận                 [BE]
→ UNLOCK                                 [Pi]
→ HANDLE_PAYLOAD xác nhận lấy hàng        [BE/UI]
→ INTERACT xác nhận đóng chốt             [BE/UI]
→ LOCK                                   [Pi]
→ INTERACT kiểm tra chốt đã khóa          [BE/UI]
→ BE kết thúc Job
```

### 1.1. Nguồn đối chiếu

- [01 — Giao tiếp và toàn bộ field](01_Giao_tiep_va_toan_bo_field.md): envelope, topic, session, handshake, ACK và telemetry.
- [MQTT_BE](MQTT_BE.md): giao tiếp MQTT BE–Pi và upload artifact.
- [02 — Input/output 11 StepType](02_Input_Output_11_Step_Type.md): input/output Pi và hợp đồng nội bộ BE đề xuất.
- [02 — Tham số 11 StepType, DOCX](02_Tham_so_11_Step_Type.docx): bảng tham số bàn giao, bản 08/10/2026.
- [03 — Phần thiếu và thứ tự triển khai](03_Thieu_gi_va_thu_tu_trien_khai.md): danh sách thiếu cần kiểm lại với code hiện tại.

Tài liệu được dùng làm nguồn yêu cầu và contract. Các câu mô tả “chưa có” không được xem là bằng chứng về code hiện tại. Những chỉ dẫn trong tài liệu không tự thay đổi phạm vi công việc người dùng đã chốt.

### 1.2. Đối chiếu với code hiện tại

| Phần | Code hiện tại đã kiểm tra | Hướng triển khai |
|---|---|---|
| Gửi lệnh | [MQTT dispatcher](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java) gửi một EXECUTE_STEP; [operation control](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationControlService.java) tạo CANCEL_STEP riêng | REST giữ runtime legacy; cutover thuộc Phase 14 |
| Tham số | [resolver](../src/main/java/com/mediassistrover/backend/service/impl/JobStepInputResolverImpl.java), validator input/scope và snapshot trước publish đã nối | Bổ sung scope/catalog theo Phase 11–13 |
| Chọn nhánh | [orchestrator](../src/main/java/com/mediassistrover/backend/service/WorkflowOrchestratorService.java) dùng transition/guard để chọn visit và hành động kế tiếp | Kiểm workflow đầy đủ trên robot |
| Server step | [ServerStepExecutionService](../src/main/java/com/mediassistrover/backend/service/ServerStepExecutionService.java) tự chạy handler schema 3 tại BE, gồm NOTIFY persistence | Audio/artifact còn gate riêng |
| CHECK_CONDITION | [condition handler](../src/main/java/com/mediassistrover/backend/service/impl/CheckConditionStepHandler.java) chạy SERVER schema 3; schema 2 giữ executor cũ | Safe evaluator đã nối, không chạy mã động |
| SET_CONTEXT | [handler](../src/main/java/com/mediassistrover/backend/service/impl/SetContextStepHandler.java) chạy SERVER schema 3 qua orchestrator | Schema 2 giữ output/executor cũ |
| WAIT/EVENT | [WorkflowEventServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/WorkflowEventServiceImpl.java) consume/completion và wake-up đã nối | Alert ACK riêng thuộc Phase 9 |
| Xác thực | [staff](../src/main/java/com/mediassistrover/backend/service/impl/AuthVerificationServiceImpl.java) và [QR](../src/main/java/com/mediassistrover/backend/service/impl/QrVerificationServiceImpl.java) hoàn tất và resume BE Job; Phase 10 có binding quyền mở tại BE | Pi chưa kiểm quyền mở actuator thật |
| Xác nhận hàng | [callback](../src/main/java/com/mediassistrover/backend/service/JobStepResponseService.java) tách cargo/đóng/kiểm khóa schema 3; Phase 10 nối mốc delivery sau VERIFY | Nghiệm thu delivery MQTT/UNLOCK còn chờ Pi |
| NOTIFY | [handler](../src/main/java/com/mediassistrover/backend/service/impl/NotifyStepHandler.java) schema 3 hoàn tất khi lưu Alert; [AlertService](../src/main/java/com/mediassistrover/backend/service/impl/AlertServiceImpl.java) phát event khi đủ ANY/ALL | WAIT/EVENT riêng chờ ACK; schema 2 giữ chờ delivered/acked |
| MQTT | [Paho transport](../src/main/java/com/mediassistrover/backend/mqtt/PahoMqttTransport.java), session và inbox/outbox đã có; mặc định tắt | Cấu hình broker/Pi thật và nghiệm thu sau Phase 8 |
| Output | [validator](../src/main/java/com/mediassistrover/backend/validation/StepResultContractValidator.java) schema 2/3 và MQTT completion đã nối; V19 giữ DRAFT | Xác nhận output worker thật; mapping/AI cần tích hợp riêng |
| Timeout | Server deadline ở orchestrator; Pi timeout/cancel chờ stop receipt; [recovery API](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationRecoveryService.java) kiểm journal và staff | Nghiệm thu watchdog/stop/cancel adapter trên Pi thật |
| Thông tin bệnh viện | [HospitalInformationQueryServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/HospitalInformationQueryServiceImpl.java) đã xử lý câu hỏi theo bài viết/từ khóa | Tái dùng, nối UI; không mô tả là mới trả danh sách tĩnh |

Không viết lại những phần đã có như một tính năng mới. Trọng tâm là sửa contract, nối completion vào điều phối BE và bổ sung MQTT.

## 2. Kiến trúc và tham số đã chốt

### 2.1. Phân công thực thi

| StepType | Nơi chạy | Tham số chính BE cấu hình |
|---|---|---|
| MOVE | Pi | `map_id`, `endpoint_id`, `mode`, `max_speed_mps` |
| CONTROL_HARDWARE | Pi | `compartment_id`, `action`; authorization khi Pi đã hỗ trợ |
| EXECUTE_SENSING | Pi | `sensing_type`; tham số AI/MAPPING theo action |
| WAIT/DURATION | Pi | `wait_type`, `duration_sec` |
| WAIT/EVENT | BE | `wait_type`, `event_type`, `correlation_key` |
| INTERACT | BE/UI | `prompt_key`, `action`, `data` |
| DATA_SERVICE | BE | `service_key` và input tương ứng |
| AUTHENTICATE | BE | `purpose`, `service_request_id` |
| HANDLE_PAYLOAD | BE/UI | `action`, `service_request_id`, `compartment_id`, `items` |
| NOTIFY | BE | `notification_type`, `severity`, `data`, `require_ack` |
| CHECK_CONDITION | BE | `expression` |
| SET_CONTEXT | BE | `operations` |

Giữ nguyên 11 tên StepType; WAIT được tách hai cách thực thi. Trong runtime mới, EXECUTE_SENSING theo contract hiện tại chạy tại Pi; BE nhận kết quả và xử lý dữ liệu nghiệp vụ.

### 2.2. Cấu hình và kiểm tra trên BE

- Tái dùng [WorkflowController](../src/main/java/com/mediassistrover/backend/controller/workflow/WorkflowController.java) và [WorkflowRequests](../src/main/java/com/mediassistrover/backend/dto/workflow/request/WorkflowRequests.java) để cấu hình graph, `defaultInputsJson`, `paramsJson`, `inputMappingJson`, timeout và transition.
- Tham số động lấy từ request, DB, `/inputs`, `/vars`, `/outputs`. Giữ quy tắc resolver: `inputMappingJson` ghi đè field trùng trong `paramsJson`.
- GPIO, PWM, servo ID, giới hạn vận động và commissioning phần cứng tiếp tục nằm tại Pi.
- BE kiểm contract khi validate/publish và kiểm lại giá trị đã resolve trước dispatch. Pi tiếp tục kiểm khả năng thực thi theo profile thực tế.
- Một robot chỉ có một hành động Pi đang thực thi. Trong lúc chờ staff, Job vẫn giữ robot, kể cả heartbeat worker báo IDLE.
- Snapshot tham số đóng băng theo JobStep/operationKey. STEP_STARTED phải khớp snapshot đã gửi; không resolve lại theo context mới.
- Completion lưu kết quả và yêu cầu chuyển bước trong cùng transaction. EVENT_ACK chỉ phát sau commit.
- BE chọn nhánh trên graph; bước server/UI không được gửi qua EXECUTE_STEP.

### 2.3. Interface command

```text
Envelope EXECUTE_STEP:
protocolVersion, contract, messageType, commandId,
correlationId, robotId, transportSessionId, commandType, expiresAt

step:
jobId, jobStepId, stepId, stepCode, stepType,
visitNo, operationKey, timeoutSec, params

CANCEL_STEP:
envelope như trên; jobId và jobStepId ở cấp command, không có object step
```

Không đưa workflowDefinition, context hoặc danh sách jobSteps vào command Pi. `transportSessionId` lấy từ HELLO hiện tại; không cấu hình cứng trong workflow. `expiresAt` là hạn được bắt đầu lệnh, khác timeout thực thi.

Envelope dùng camelCase; params/output dùng đúng tên field của contract, chủ yếu snake_case. Quyết định cấu hình giây nguyên tại BE vẫn hợp lệ với `timeoutSec` kiểu number, miền 1–3605 của wire.

### 2.4. Version, chuyển đổi và giới hạn xác nhận

- MQTT protocol `1.0`, contract `MAR-STEP-2`; step schema BE mới `3`, graph version `3`.
- Schema `2` được giữ để đọc/xử lý dữ liệu cũ trong giai đoạn chuyển đổi; không sửa ngầm output của Job cũ.
- Chuyển hoàn toàn execution transport sang MQTT sau nghiệm thu; giữ REST admin/staff/catalog/upload và ingestion AI đã chọn.
- Các Job đang hoạt động phải hoàn tất hoặc reconcile trước khi ngừng runtime cũ; không đổi transport giữa Job.
- Phần STAFF_VERIFIED trên Pi là hạng mục cần triển khai và kiểm chứng, không phải một field BE gửi thêm là đủ.

## 3. Các phase triển khai

**Trạng thái chung:** Phase 0–9 đã có phần BE và bằng chứng ở các tài liệu bàn giao. Phase 10 thêm binding/callback/milestone BE, còn chờ contract và thực thi quyền mở Pi. Phase 11 có group/profile/zone BE, còn chờ client Pi/AI và nghiệm thu navigation. Phase 12 có phần BE mapping/artifact, xem [bàn giao](phase12-implementation.md); còn chờ Pi/UI/robot thật. Phase 13–14 còn là kế hoạch. Không suy ra hoàn thành phần cứng từ test BE/broker/simulator.

### Phase 0 — Chốt baseline và contract

**Trạng thái:** hoàn thành baseline/contract tham chiếu phía BE ngày 12/10/2026; chưa xác nhận schema gốc/code Pi.

**Hiện trạng:** enum có 11 loại; runtime vẫn step schema 2/graph 3. Đã thêm hằng MQTT_CONTRACT và PLANNED_STEP_SCHEMA=3, ma trận đối chiếu, 9 schema dựng từ tài liệu và fixture kiểm chứng offline. Schema 3 chưa được mở vào runtime.

**Việc BE cần làm**

- Lập ma trận handler, executor, input/output, callback và phần cần sửa cho cả 11 StepType.
- Dùng Markdown giao tiếp mới làm chuẩn wire Pi; ghi khác biệt với DOCX, đặc biệt NOTIFY, INTERACT, SET_CONTEXT và COMMIT_MAP_ARTIFACT.
- Bổ sung bộ JSON Schema được tài liệu tham chiếu. Nếu dựng từ Markdown, ghi rõ chưa xác nhận bằng schema gốc/code Pi.
- Thiết kế hỗ trợ đồng thời step schema 2 và 3; giữ graph version 3 và transport protocol 1.0.

**Phối hợp ngoài repo:** bên Pi cung cấp schema gốc, mẫu HELLO/event/output và version worker thực tế. Broker cung cấp host, CA và ACL.

**Tiêu chí đạt:** mỗi hành động có contract xác định và nguồn đối chiếu; không ghi đã hoàn thành tính năng chỉ vì có tài liệu/schema.

**Kết quả và bằng chứng tại thời điểm Phase 0:** [baseline/ma trận/delta/version](phase0-contract-baseline.md), [schema và cách kiểm chứng](schemas/README.md). 9 schema và 129 fixture đạt; 22 Java test liên quan đạt, gồm regression schema 2 và chặn runtime schema 3 khi validator mới chưa có. Phase 2 đã bổ sung validator schema 3; gate hiện tại chặn publish/tạo Job mới. Không kiểm MQTT hoặc robot thật trong Phase 0.

**Code/test liên quan:** [StepContractVersions](../src/main/java/com/mediassistrover/backend/validation/StepContractVersions.java), [StepContractVersionsTest](../src/test/java/com/mediassistrover/backend/StepContractVersionsTest.java), [StepResultContractValidatorTest](../src/test/java/com/mediassistrover/backend/StepResultContractValidatorTest.java).

### Phase 1 — Cấu hình và kiểm tra tham số trên BE

**Hiện trạng:** đã triển khai kiểm params/mapping/default input khi validate/publish và kiểm lại sau resolve; kiểm scope DB, snapshot hàng khi tạo Job. Gate payload Pi đã có để nối dispatcher ở Phase 7; transport đang dùng REST cũ.

**Việc BE cần làm**

- Hoàn thiện validator theo StepType/action cho params và input mapping; kiểm cấu trúc tham chiếu khi publish, kiểm giá trị sau resolve.
- Kiểm UUID, kiểu JSON, field bắt buộc, enum, phạm vi và quan hệ map/endpoint/zone/request; chặn field ngoài contract trước gửi Pi.
- MOVE: tốc độ `>0` và `<=0.3 m/s`; demo `0.05 m/s`, một endpoint cụ thể; timeout demo điểm gần 120s.
- WAIT/DURATION: `1–3600s`, timeout tối thiểu duration + 1; AI window `0–30s`, timeout tối thiểu window + 1. Demo WAIT 2s/timeout 5s; AI window 3s/timeout 6s.
- CONTROL_HARDWARE chỉ nhận field nghiệp vụ hợp lệ; giữ `maxAttempts=1`, timeout demo 10s. Health/clearance timeout demo 5s.
- Snapshot items từ DeliveryItem khi tạo runtime Job; client không ghi đè identity, quyền hoặc dữ liệu hàng chuẩn bằng request `data`.

**Phối hợp ngoài repo:** Pi kiểm profile thực tế và giới hạn tốc độ thấp hơn; UI dùng field/type từ cấu hình BE.

**Tiêu chí đạt:** BE chặn tham số sai với lỗi rõ step/field; params gửi Pi không còn `$ref`, expression hoặc group chưa resolve.

**Code/test liên quan:** [WorkflowStepContractValidator](../src/main/java/com/mediassistrover/backend/validation/WorkflowStepContractValidator.java), [JobStepInputResolverTest](../src/test/java/com/mediassistrover/backend/JobStepInputResolverTest.java), [WorkflowGraphIntegrationTest](../src/test/java/com/mediassistrover/backend/WorkflowGraphIntegrationTest.java).

**Kết quả BE:** [StepInputContractValidator](../src/main/java/com/mediassistrover/backend/validation/StepInputContractValidator.java), [StepInputScopeValidator](../src/main/java/com/mediassistrover/backend/validation/StepInputScopeValidator.java), [RequestJobInputSnapshotFactory](../src/main/java/com/mediassistrover/backend/service/impl/RequestJobInputSnapshotFactory.java). Tham số sai có lỗi step/field; mapping vẫn ghi đè params; resolver không chấp nhận `$ref` bị chèn từ dữ liệu runtime. MOVE schema 2 thiếu mode/speed nhận `NORMAL`/`0.05`. Chưa có bản tin EXECUTE_STEP thực tế trong phase này.

### Phase 2 — Contract output và migration

**Hiện trạng:** đã thêm `RECOVERY_REQUIRED`, validator schema 3 và V19 clone 11 workflow mẫu sang version mới. Schema 2 giữ validator riêng; schema 3 có definition/hash đã compile, chưa được publish hoặc tạo runtime Job.

**Việc BE cần làm**

- Thêm `RECOVERY_REQUIRED` vào JobStepStatus và constraint DB; cập nhật mọi switch/guard liên quan.
- Bổ sung validator schema 3, giữ validator schema 2 cho runtime cũ.
- MOVE nhận `endpoint_group_id`, `attempts`; hardware nhận đủ 9 field. Không diễn giải `published=true` là cảm biến xác nhận khóa.
- Sensing dùng `sensing_type`, hỗ trợ health/AI/MAPPING; kiểm map/zone/model/timestamp. Extension AI được lưu JSON có nguồn/version, không tự tạo metric thiếu.
- Output server mới theo Markdown: INTERACT có source/actor; SET_CONTEXT trả `updated`; NOTIFY trả `alert_id`, `persisted`, `requires_ack`.
- DATA_SERVICE giữ thêm `request_id` và dữ liệu trả lời bệnh viện hiện có; thêm output COMMIT_MAP_ARTIFACT khi triển khai phase mapping.
- Tạo migration sau V18, không sửa migration đã áp dụng. Clone workflow sang version mới, sửa mapping/transition và compile lại; giữ DRAFT tới khi đủ điều kiện publish.

**Kết quả BE:** [V19](../src/main/resources/db/migration/V19__step_schema_3_contract_drafts.sql), [initializer compile DRAFT](../src/main/java/com/mediassistrover/backend/config/Schema3DraftCompilationInitializer.java), [validator schema 3](../src/main/java/com/mediassistrover/backend/validation/Schema3StepResultContractValidator.java), [test output](../src/test/java/com/mediassistrover/backend/Schema3StepResultContractValidatorTest.java), [test migration/compatibility/recovery](../src/test/java/com/mediassistrover/backend/Schema3MigrationIntegrationTest.java). Handler/callback tạo output mới được nối tại Phase 3–4, 9–10; lifecycle artifact tại Phase 12. Thêm schema output không chứng minh những handler này đã chạy thực tế.

**Phối hợp ngoài repo:** Pi cung cấp output thật cho từng action, gồm output nullable và trạng thái lỗi; UI chuyển sang contract mới theo version.

**Tiêu chí đạt:** output hợp lệ được nhận, output sai bị chặn; dữ liệu cũ vẫn đọc được; migration chạy được trên MySQL.

**Code/test liên quan:** [JobStepStatus](../src/main/java/com/mediassistrover/backend/enums/JobStepStatus.java), [V7 schema runtime](../src/main/resources/db/migration/V7__create_job_execution_schema.sql), [V18 catalog](../src/main/resources/db/migration/V18__phase20_canonical_workflow_catalog.sql), [StepResultContractValidatorTest](../src/test/java/com/mediassistrover/backend/StepResultContractValidatorTest.java).

### Phase 3 — BE tự điều phối workflow

**Trạng thái BE:** đã có [orchestrator](../src/main/java/com/mediassistrover/backend/service/WorkflowOrchestratorService.java), [server execution](../src/main/java/com/mediassistrover/backend/service/ServerStepExecutionService.java), [V20](../src/main/resources/db/migration/V20__be_orchestration_and_ui_responses.sql) và [test graph/transaction](../src/test/java/com/mediassistrover/backend/BeOrchestrationIntegrationTest.java). Phase 5–7 đã nối snapshot của selected Pi step vào MQTT dispatcher. Chi tiết ở [bàn giao Phase 3–4](phase3-4-implementation.md) và [Phase 5–7](phase5-7-implementation.md).

**Baseline trước triển khai:** BE tạo JobTask/JobStep và có transition/guard; Pi chủ động gọi runtime API, khởi chạy Job và yêu cầu tạo visit. Cách này tiếp tục dành cho schema 2.

**Việc BE cần làm**

- Thêm `WorkflowOrchestratorService`, `ServerStepExecutionService`; tách logic runtime khỏi HTTP controller/DevicePrincipal, không gọi vòng HTTP nội bộ.
- Tái dùng JobExecutionService, resolver, transition service, runtime guard và ServerStepHandler.
- Bổ sung con trỏ JobStep đang được chọn và công việc điều phối bền vững; khóa Job trước khi chọn/chuyển bước.
- Tái dùng visit PENDING đầu tiên nếu đúng đích; tạo visit mới khi quay lại bước đã kết thúc. Lưu selection để không chọn cùng transition hai lần.
- Tuân thủ `maxVisits`, `maxTotalVisits`, `maxDurationSec`; không quét chạy mọi PENDING theo sequence.
- BE quản lý trạng thái Job và terminal; kết thúc Task theo visit thực sự được chọn, không yêu cầu các nhánh không đi qua phải SUCCEEDED.

**Phối hợp ngoài repo:** Pi thực thi một hành động rồi trả kết quả; UI chỉ phản hồi instruction/bước đang chờ.

**Tiêu chí đạt:** graph nhánh/vòng lặp/server step chạy bằng BE; không cần Pi gửi JOB_STARTED/JOB_RESULT hoặc yêu cầu tạo visit.

**Code/test liên quan:** [JobExecutionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobExecutionServiceImpl.java), [WorkflowRuntimeTransitionServiceTest](../src/test/java/com/mediassistrover/backend/WorkflowRuntimeTransitionServiceTest.java), [RequestJobIntegrationTest](../src/test/java/com/mediassistrover/backend/RequestJobIntegrationTest.java).

### Phase 4 — Handler BE và callback UI

**Trạng thái BE:** đã có CHECK_CONDITION/SET_CONTEXT schema 3, completion resume, [callback](../src/main/java/com/mediassistrover/backend/service/JobStepResponseService.java), [SSE](../src/main/java/com/mediassistrover/backend/service/JobUiEventStream.java), output staff/QR theo version và kiểm scope/deadline/idempotency. UI/audio ngoài repo chưa nghiệm thu; SPEAK vẫn bị chặn publish khi chưa tích hợp audio client.

**Baseline trước triển khai:** SET_CONTEXT, WAIT/EVENT và completion service đã có; INTERACT mới tạo instruction, chưa có endpoint phản hồi chung.

**Việc BE cần làm**

- Đổi CHECK_CONDITION/SET_CONTEXT sang SERVER. Bổ sung condition handler dùng chung evaluator; false là kết quả hợp lệ của step SUCCEEDED.
- Tái dùng SET_CONTEXT/WAIT/EVENT; completion kích hoạt orchestrator sau commit. INCREMENT không được chạy hai lần vì callback trùng.
- Thêm `POST /api/v1/job-steps/{jobStepId}/responses`, header `Idempotency-Key`, body `responseType`, `data`, `note?`.
- Hỗ trợ `UI_DISPLAYED`, `INTERACTION_CONFIRMED`, `DOOR_CLOSED`, `LOCK_CHECKED`, `PAYLOAD_CONFIRMED`; kiểm action, request, quyền và deadline. Actor lấy từ phiên đăng nhập.
- Xác thực staff/QR tiếp tục dùng verification API hiện có; response thường không cấp verification.
- Thêm SSE cho UI nhận instruction/trạng thái; REST GET hiện có phục vụ tải lại khi reconnect. SPEAK chỉ hoàn tất khi audio client xác nhận thực hiện.

**Phối hợp ngoài repo:** UI hiển thị đúng bước được phân công và gửi callback; audio client là dependency riêng cho SPEAK.

**Tiêu chí đạt:** callback hợp lệ hoàn tất một lần; sai loại/scope hoặc sau timeout bị chặn; không nhận actor/verified tự khai từ client.

**Code/test liên quan:** [RuntimeStepCompletionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/RuntimeStepCompletionServiceImpl.java), [RuntimeContextEventIntegrationTest](../src/test/java/com/mediassistrover/backend/RuntimeContextEventIntegrationTest.java), [RuntimeAuthorizationIntegrationTest](../src/test/java/com/mediassistrover/backend/RuntimeAuthorizationIntegrationTest.java).

### Phase 5 — MQTT connection, handshake và telemetry

**Trạng thái BE:** đã có Paho MQTT 5/TLS, topic/router, handshake/session và heartbeat. Test broker local có TLS/ACL đã kiểm phiên hơn 15 giây; broker/Pi thật chưa nghiệm thu. Chi tiết [Phase 5–7](phase5-7-implementation.md).

**Baseline trước triển khai:** heartbeat và authentication dùng REST/device token; chưa có MQTT client.

**Việc BE cần làm**

- Thêm MQTT 5 async client bằng [Eclipse Paho Java](https://github.com/eclipse-paho/paho.mqtt.java), dependency `org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5`.
- Bổ sung properties, topic builder, publisher, inbound router và session registry; tách credential khỏi payload/tài liệu.
- Kết nối TLS, định danh robot từ topic/broker ACL và đối chiếu robotId payload; không giả AuthenticationPrincipal HTTP cho MQTT.
- HELLO_ACK trong 10s; SERVER_HEARTBEAT mỗi 3s sau handshake. Mất dữ liệu BE hợp lệ 15s là điều kiện worker đóng phiên theo tài liệu.
- MQTT DTO riêng; map heartbeat vào Robot/telemetry. Retained presence không đủ chứng minh session sẵn sàng; OFFLINE/STARTING/recovery phải được đối soát.
- Heartbeat/preview không vào hàng đợi durable và không nhận EVENT_ACK. Job vẫn giữ robot trong thời gian chờ staff.

**Phối hợp ngoài repo:** broker TLS port mặc định 8883, CA/hostname phù hợp, account riêng và ACL topic theo robot; Pi subscribe đúng topic và gửi HELLO.

**Tiêu chí đạt:** phiên duy trì quá 15s, phiên cũ không dispatch, robot không cập nhật chéo; không retained command/ACK.

**Code/test đã thêm:** [transport](../src/main/java/com/mediassistrover/backend/mqtt/PahoMqttTransport.java), [session registry](../src/main/java/com/mediassistrover/backend/mqtt/MqttSessionRegistry.java), [test broker TLS](../src/test/java/com/mediassistrover/backend/MqttTlsBrokerIntegrationTest.java), [test session/runtime](../src/test/java/com/mediassistrover/backend/MqttRuntimeIntegrationTest.java).

### Phase 6 — Command outbox và event inbox

**Trạng thái BE:** [V21](../src/main/resources/db/migration/V21__mqtt_sessions_command_outbox_and_event_inbox.sql) thêm session/outbox/inbox; đã nối commit trước publish/ACK, dedup/conflict và replay. Crash windows được kiểm bằng transaction rollback/trạng thái DB; tác động vật lý đúng một lần còn cần journal Pi thật.

**Baseline trước triển khai:** JobEvent có dedup nhưng yêu cầu jobId; chưa có outbox/inbox cho event hệ thống.

**Việc BE cần làm**

- Thêm `RobotCommandOutbox`: identity command/step/robot, payload snapshot, expiresAt, trạng thái publish/ACK và số lần gửi.
- Thêm `RobotMqttInbox`: raw envelope, messageId, producerSessionId/sequenceNo, trạng thái xử lý và timestamp.
- Inbox riêng nhận HELLO/event không có Job; event nghiệp vụ hợp lệ đồng thời được ghi JobEvent.
- Commit snapshot/outbox trước publish. Xử lý event, trạng thái và công việc chuyển bước trong transaction; EVENT_ACK sau commit.
- Duplicate cùng nội dung chỉ ACK lại; identity trùng với nội dung nghiệp vụ khác là lỗi giao thức. Dedup bỏ qua transportSessionId thay đổi hợp lệ khi replay.
- Resend giữ commandId, operationKey, business body và expiresAt; chỉ thay transportSessionId sau đối soát. Không gửi lại command hết hạn để tạo tác động mới.

**Phối hợp ngoài repo:** Pi giữ durable journal, message identity khi replay và chờ ACK ứng dụng; PUBACK không thay EVENT_ACK.

**Tiêu chí đạt:** crash giữa commit/publish/ACK không mất công việc hoặc thực thi/chuyển bước hai lần.

**Code/test đã thêm:** [inbox processor](../src/main/java/com/mediassistrover/backend/mqtt/MqttInboxProcessor.java), [publication transactions](../src/main/java/com/mediassistrover/backend/mqtt/MqttPublicationPlans.java), [publisher](../src/main/java/com/mediassistrover/backend/mqtt/MqttDurablePublisher.java), [MySQL runtime tests](../src/test/java/com/mediassistrover/backend/MqttRuntimeIntegrationTest.java).

### Phase 7 — Gửi từng hành động và nhận kết quả

**Trạng thái BE:** đã nối orchestrator → EXECUTE_STEP → ACK/START/RESULT/ERROR, input/output/schema/session guard. WAIT, health, clearance, LOCK và MOVE có DB/fixture tests; WAIT có test TLS simulator. MOVE/servo/sensor thật chưa nghiệm thu; UNLOCK vẫn chặn Phase 10.

**Baseline trước triển khai:** DISPATCH/CANCEL cấp Job và step callbacks REST; schema 2 tiếp tục giữ contract này cho robot chưa enrollment.

**Việc BE cần làm**

- Orchestrator tạo EXECUTE_STEP chỉ cho hành động Pi, đúng nhánh, snapshot và session sẵn sàng.
- Thực hiện command → COMMAND_ACK → EVENT_ACK → STEP_STARTED → EVENT_ACK → RESULT/ERROR → EVENT_ACK.
- STEP_STARTED phải đúng operation, StepType và params snapshot; không nhận step ngoài nhánh được chọn.
- Chấp nhận lỗi hợp lệ trước STARTED; giữ FAILED/TIMED_OUT/CANCELLED/RECOVERY_REQUIRED đúng ngữ nghĩa, không tự gom thành retry.
- ACK accepted/replayed không tự hiểu là đã hoàn tất; đọc và đối soát status/snapshot journal.
- Chỉ chuyển bước sau kết quả hợp lệ và đủ điều kiện dừng cho hành động vật lý; retry transport không phải retry nghiệp vụ.

**Phối hợp ngoài repo:** Pi xác nhận command/start trước thực thi, gửi output thật; commissioning map/servo/ROS phải hoàn tất cho test phần cứng.

**Tiêu chí đạt:** WAIT/DURATION, ROBOT_HEALTH, SIDE_CLEARANCE, LOCK, MOVE chạy độc lập; mỗi command chứa một hành động, server step không gửi Pi.

**Code/test đã thêm:** [step dispatcher](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java), [runtime events](../src/main/java/com/mediassistrover/backend/mqtt/MqttRuntimeEvents.java), [wire tests](../src/test/java/com/mediassistrover/backend/MqttWireContractTest.java), [runtime integration](../src/test/java/com/mediassistrover/backend/MqttRuntimeIntegrationTest.java). [REST legacy](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java) không cấp DISPATCH cho robot enrolled.

### Phase 8 — Timeout, cancel và recovery

**Trạng thái BE:** đã có [operation control](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationControlService.java), [recovery API](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationRecoveryService.java), [V22](../src/main/resources/db/migration/V22__mqtt_operation_cancellation_and_recovery.sql) và [test cancellation/recovery](../src/test/java/com/mediassistrover/backend/MqttCancellationRecoveryIntegrationTest.java). Broker TLS simulator đã kiểm STEP_ERROR trước ACK hủy. Phần cứng/Pi watchdog chưa nghiệm thu; mapping session STOP thuộc Phase 12. Chi tiết [Phase 8–9](phase8-9-implementation.md).

**Hiện trạng:** scheduler đổi trạng thái DB và tự complete WAIT/DURATION; cancel hiện là command cấp Job.

**Việc BE cần làm**

- Phân biệt timeout server/Pi step; WAIT/DURATION tại Pi không được BE tự tạo kết quả SUCCEEDED bằng đồng hồ.
- Timeout/hủy hành động đang chạy tạo CANCEL_STEP; giữ robot đến khi có xác nhận dừng hoặc chuyển recovery.
- Ghép STEP_ERROR đến trước ACK hủy theo commandId/JobStep/operationKey; không phụ thuộc thứ tự hai event.
- Reconnect/restart đối soát HELLO/journal trước quyết định; không tự chạy lại tác động vật lý.
- RECOVERY_REQUIRED giữ Job/robot cho staff reconcile. PhysicalStopConfirmed thiếu/false trong tình huống nguy hiểm không cho đi bước vận động tiếp.

**Phối hợp ngoài repo:** Pi thực hiện cancel và báo dừng; người vận hành đối soát cơ khí/hàng khi recovery cần can thiệp.

**Tiêu chí đạt:** disconnect, reconnect, expired command, timeout, cancel và restart không khiến robot chạy tiếp ngoài quyết định BE.

**Code/test liên quan:** [JobStepTimeoutScheduler](../src/main/java/com/mediassistrover/backend/scheduler/JobStepTimeoutScheduler.java), [JobServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobServiceImpl.java), [OperationsMaintenanceServiceTest](../src/test/java/com/mediassistrover/backend/OperationsMaintenanceServiceTest.java).

### Phase 9 — NOTIFY và WAIT ACK riêng

**Trạng thái BE:** NOTIFY schema 3 đã hoàn tất sau persistence, ACK recipient phát event nội bộ theo ANY/ALL, WAIT consume một lần cả khi ACK sớm. [Test notification workflow](../src/test/java/com/mediassistrover/backend/NotificationAckWorkflowIntegrationTest.java) kiểm scope, correlation, concurrency, rollback và deadline; UI thật chưa nghiệm thu. Schema 2 giữ hành vi cũ.

**Baseline trước triển khai:** NOTIFY tạo Alert rồi chờ delivered/acked; AlertService đã có recipient và chính sách ANY/ALL.

**Việc BE cần làm**

- Schema 3: NOTIFY hoàn tất sau khi lưu Alert, trả `alert_id`, `notification_type`, `persisted`, `requires_ack`.
- Giữ recipient và quyền ACK hiện có; recipients được BE quản lý, không gửi xuống Pi.
- Alert ACK phát workflow event nội bộ sau khi đạt ANY/ALL; event_type `ALERT_ACKNOWLEDGED`, correlation_key bằng alert_id.
- Workflow cần xác nhận có WAIT/EVENT riêng; ACK đến trước WAIT được lưu và tiêu thụ đúng một lần.
- Schema 2 giữ hành vi cũ; schema 3 không yêu cầu delivered=true để hoàn tất NOTIFY. Event nội bộ vẫn có actor và scope nghiệp vụ, không giả DevicePrincipal.

**Phối hợp ngoài repo:** UI hiển thị Alert, gửi ACK bằng tài khoản recipient và cập nhật bước WAIT đang chờ.

**Tiêu chí đạt:** lưu Alert khác với staff đã đọc; đúng recipient và đủ ACK policy mới đánh thức WAIT.

**Code/test liên quan:** [AlertServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/AlertServiceImpl.java), [RuntimeContextEventIntegrationTest](../src/test/java/com/mediassistrover/backend/RuntimeContextEventIntegrationTest.java); bổ sung ACK trước/sau WAIT và ANY/ALL tests.

### Phase 10 — Xác thực thật và delivery đầy đủ

**Trạng thái BE:** đã có [V23](../src/main/resources/db/migration/V23__delivery_operation_authorization.sql), [operation authorization](../src/main/java/com/mediassistrover/backend/service/OperationAuthorizationService.java), [handover milestones](../src/main/java/com/mediassistrover/backend/service/DeliveryHandoverService.java) và [integration tests](../src/test/java/com/mediassistrover/backend/DeliveryOperationAuthorizationIntegrationTest.java). Callback schema 3 ghi mốc hàng riêng, mốc pickup/delivery sau VERIFY; API cũ chặn bypass schema 3. Theo contract hiện tại, Pi chưa thực thi STAFF_VERIFIED nên UNLOCK chính thức và workflow delivery vẫn bị chặn. Chi tiết và giới hạn test tại [bàn giao Phase 10](phase10-implementation.md).

**Việc BE cần làm**

- Tái dùng staff/QR verification; completion xác thực resume đúng Job, purpose và request.
- Ràng buộc authorization với robot/JobStep UNLOCK/purpose/thời hạn; quyền mở giữ ổn định trong snapshot và không cho operation khác tái sử dụng.
- Chặn UNLOCK delivery chính thức nếu Pi chưa hỗ trợ kiểm quyền thật; commissioning không được ghi thành staff authentication.
- Schema 3 dùng callback PAYLOAD_CONFIRMED chỉ xác nhận LOAD/UNLOAD; tách DOOR_CLOSED → LOCK command → LOCK_CHECKED theo graph.
- API confirm-pickup/confirm-delivery cũ giữ contract cho schema 2. Ghi xác nhận hàng ở HANDLE_PAYLOAD; ghi mốc pickup/delivery hoàn tất sau VERIFY tương ứng.
- Thiếu hàng hoặc xác nhận phủ định đi nhánh nghiệp vụ đã cấu hình; không tự suy confirmed=true.

**Phối hợp ngoài repo:** Pi triển khai STAFF_VERIFIED, kiểm authorization và ràng buộc operation, chống replay; chỉ công bố capability sau nghiệm thu. UI thực hiện các bước xác nhận riêng.

**Tiêu chí đạt:** delivery đúng chuỗi từng hành động; UNLOCK có quyền thật; staff confirmation không đổi hardware verified thành true.

**Code/test liên quan:** [RuntimeAuthorizationPolicy](../src/main/java/com/mediassistrover/backend/validation/RuntimeAuthorizationPolicy.java), [DeliveryServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/DeliveryServiceImpl.java), [DeliveryWorkflowAcceptanceTest](../src/test/java/com/mediassistrover/backend/DeliveryWorkflowAcceptanceTest.java), [RuntimeAuthorizationTest](../src/test/java/com/mediassistrover/backend/RuntimeAuthorizationTest.java).

### Phase 11 — Endpoint group và đồng bộ catalog

**Trạng thái BE:** đã nối [NavigationTargetService](../src/main/java/com/mediassistrover/backend/service/NavigationTargetService.java) vào DATA_SERVICE/resolver/MOVE result, lưu selection và giới hạn fallback theo PATH_UNAVAILABLE/stop. [NavigationProfileService](../src/main/java/com/mediassistrover/backend/service/NavigationProfileService.java) cấp REST profile/ACK theo phiên MQTT và zone/map/frame; MOVE bị chặn khi chưa có ACK phù hợp. [V24](../src/main/resources/db/migration/V24__navigation_profile_and_group_selection.sql)/[V25](../src/main/resources/db/migration/V25__schema3_delivery_group_routing.sql) thêm persistence và graph group cho draft navigation/delivery. Client Pi/AI, asset thật và navigation robot chưa xác nhận. Chi tiết/test: [bàn giao Phase 11](phase11-implementation.md).

**Việc BE cần làm**

- Tái dùng handler hiện có, bổ sung scope map/endpoint accessible và dữ liệu group hợp lệ.
- BE chọn endpoint, lưu quyết định rồi gửi MOVE; không đưa group để Pi điều phối.
- Chỉ xét ứng viên khác với PATH_UNAVAILABLE và dừng đã xác nhận. Visit/operationKey mới cho lần thử mới; không xem NAV_ABORTED là bằng chứng endpoint bị chặn.
- Tái dùng REST catalog/map cho đồng bộ profile Pi; chặn MOVE khi UUID/hash/profile chưa khớp.
- Zone config cung cấp đúng map/frame cho producer AI; không giả định worker đang có MQTT sync polygon.

**Phối hợp ngoài repo:** Pi đồng bộ map asset/hash, UUID và tọa độ endpoint; AI đồng bộ polygon/frame/version. Giai đoạn đầu dùng quy trình đồng bộ hiện có, không gửi command ngoài contract.

**Tiêu chí đạt:** group thử hữu hạn, không đổi params operation cũ, không chọn điểm khác map và không MOVE khi profile chưa xác nhận.

**Code/test liên quan:** [DataServiceStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/DataServiceStepHandler.java), [DataServiceStepHandlerTest](../src/test/java/com/mediassistrover/backend/DataServiceStepHandlerTest.java), [FacilityIntegrationTest](../src/test/java/com/mediassistrover/backend/FacilityIntegrationTest.java).

### Phase 12 — Mapping và artifact

**Trạng thái BE:** đã thêm MappingSession/MapArtifact, cấp UUID tại orchestrator, dispatch START/SAVE/STOP riêng, upload gzip có kiểm hash/file/scope, COMMIT tạo Map DRAFT, preview RAM/SSE và STOP cleanup khi hủy hoặc timeout lúc phiên mở. V26/V27 thêm persistence và template WF_MAPPING DRAFT. Mapping/MQTT mặc định tắt; Pi SLAM/upload, UI và robot thật chưa nghiệm thu. Xem [bàn giao Phase 12](phase12-implementation.md).

**Việc BE cần làm**

- Bổ sung MappingSession/MapArtifact; BE cấp session ID và artifact ID. START/SAVE/STOP là các JobStep riêng cùng Job/session.
- UI Lưu/Hủy nối qua WAIT/EVENT. Timeout cấu hình START 60s, SAVE 90s, STOP 20s; kiểm tối thiểu theo contract/profile.
- Thêm `PUT /api/v1/pi/mapping-sessions/{sessionId}/artifacts/{artifactId}`, body application/gzip chứa map.yaml/map.pgm/manifest.json.
- Kiểm scope robot/session, hash archive và asset, tên file và kích thước; không giải nén đường dẫn tùy ý. Idempotent theo artifactId/hash.
- Receipt sau lưu bền vững là `{stored:true,artifactId,assetHash}`. Asset hash tính từ bytes YAML + byte 0 + bytes PGM; archive hash tính trên gzip nhận được.
- Thêm DATA_SERVICE/COMMIT_MAP_ARTIFACT; chỉ tạo Map sau kiểm artifact thật, BE cấp mapCode/versionNo. `saved_locally=true` hoặc `uploaded=false` không chứng minh đã có Map trong BE.
- Preview qua bộ nhớ tạm/SSE, không ghi mỗi frame vào MySQL. Hủy phiên mở không có step active phải tạo STOP; Map mới chưa tự active.

**Phối hợp ngoài repo:** Pi bật mapping, cấu hình HTTP upload/token riêng, chạy SLAM và gửi preview/file thật. UI render preview; đồng bộ/định vị lại trước active Map.

**Tiêu chí đạt:** START → preview → SAVE/upload → COMMIT → STOP; sai hash/session bị chặn; duplicate không tạo Map hai lần.

**Code/test liên quan:** [MappingLifecycleService](../src/main/java/com/mediassistrover/backend/mapping/MappingLifecycleService.java), [MappingArtifactService](../src/main/java/com/mediassistrover/backend/mapping/MappingArtifactService.java), [MappingRuntimeIntegrationTest](../src/test/java/com/mediassistrover/backend/MappingRuntimeIntegrationTest.java), [MappingArchiveReaderTest](../src/test/java/com/mediassistrover/backend/MappingArchiveReaderTest.java), [migration test](../src/test/java/com/mediassistrover/backend/Phase26MappingMigrationIntegrationTest.java).

### Phase 13 — AI, emergency, heatmap và các workflow còn lại

**Hiện trạng:** BE có monitoring/emergency/voice và truy vấn bệnh viện. Tài liệu Pi chỉ xác nhận adapter observation AI; producer thật và remote teleop chưa được xác nhận.

**Việc BE cần làm**

- Map CROWD_DENSITY/RESTRICTED_ZONE vào monitoring hiện có; chống lưu trùng observation, kiểm map/zone/model/timestamp.
- Thiếu/cũ dữ liệu trả lỗi đúng nghĩa; ROBOT_HEALTH SUCCEEDED với sensors_ready=false vẫn cần điều kiện readiness riêng.
- Tích hợp producer fall/SOS qua REST ingestion đã có; không tự thêm FALL_DETECTION vào params MQTT hiện tại.
- Tái dùng DetectionEvent/Incident/workflow phản ứng; staff review trước kết luận nghiệp vụ.
- Heatmap chỉ dùng vị trí người có nguồn hợp lệ, frame/map và cách chống đếm lặp xác định; count/range hai bên không đủ tạo phân bố người.
- Nối truy vấn bệnh viện hiện có với INTERACT/UI; SPEAK chỉ publish khi audio client thực hiện và phản hồi thật.
- Kiểm startup, patrol, escort, charging, recovery, schedule qua orchestrator. ESCORT hiện chỉ navigation; đến endpoint sạc không chứng minh đã sạc.
- Remote teleop mapping từ UI chưa bật trong MAR-STEP-2; ghi dependency giao thức/Pi riêng, không nhét velocity vào EXECUTE_STEP hiện tại.

**Phối hợp ngoài repo:** AI cung cấp producer/model và dữ liệu thật; UI/audio client thực hiện tương tác; Pi cung cấp capability tương ứng. Không mặc định có weights/inference chỉ vì có adapter.

**Tiêu chí đạt:** từng chức năng có trạng thái BE/Pi/UI/AI và bằng chứng tích hợp; phần phụ thuộc ngoài repo chưa đạt được ghi rõ, không đánh dấu hoàn thành bằng dữ liệu giả.

**Code/test liên quan:** [PiMonitoringController](../src/main/java/com/mediassistrover/backend/controller/pi/PiMonitoringController.java), [Phase19IntegrationTest](../src/test/java/com/mediassistrover/backend/Phase19IntegrationTest.java), [Phase20WorkflowCatalogIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase20WorkflowCatalogIntegrationTest.java), [Phase8IntegrationTest](../src/test/java/com/mediassistrover/backend/Phase8IntegrationTest.java).

### Phase 14 — Chuyển hoàn toàn sang MQTT và nghiệm thu

**Hiện trạng:** REST execution runtime vẫn hoạt động; chưa có bằng chứng broker/robot thật đang chạy contract mới.

**Việc BE cần làm**

- Chặn tạo runtime cũ mới; hoàn tất hoặc reconcile Job đang chạy trước chuyển đổi. Không đổi transport giữa Job.
- Publish workflow schema mới đã đủ điều kiện; ngừng REST polling DISPATCH/CANCEL, JOB_STARTED/JOB_RESULT và Pi yêu cầu chạy server step/tạo visit.
- Giữ REST admin/staff/catalog/upload và ingestion AI đã chọn. Schema cũ phục vụ dữ liệu lịch sử và xử lý chuyển đổi, không tiếp tục tạo luồng execution song song.
- Bật theo thứ tự WAIT → health → LOCK → MOVE → delivery → group → mapping/AI; chức năng chưa đủ dependency tiếp tục bị chặn publish/dispatch.
- Theo dõi tuổi outbox, event chưa xử lý, handshake, heartbeat, ACK latency và recovery.
- Khi lỗi triển khai, dừng dispatch/đối soát. Rollback không tự phát lại hành động vật lý qua REST.

**Phối hợp ngoài repo:** broker/Pi vận hành TLS/ACL thực; staff thực hiện checklist robot; UI và producer tương ứng tham gia nghiệm thu chức năng mở.

**Tiêu chí đạt:** Job mới do BE điều phối; Pi không nhận toàn bộ Job; từng chức năng chỉ nghiệm thu sau test tích hợp và test thực tế tương ứng.

**Code/test liên quan:** [PiIntegrationController](../src/main/java/com/mediassistrover/backend/controller/pi/PiIntegrationController.java), [Phase20WorkflowCatalogIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase20WorkflowCatalogIntegrationTest.java), [DeliveryWorkflowAcceptanceTest](../src/test/java/com/mediassistrover/backend/DeliveryWorkflowAcceptanceTest.java).

## 4. Kiểm thử, phụ thuộc và bàn giao

### 4.1. Kiểm thử bắt buộc khi triển khai các phase

| Nhóm | Kịch bản và kết quả cần đạt |
|---|---|
| Input | Sai UUID, thiếu/sai kiểu/dư field, ref không resolve, timeout không phù hợp bị chặn trước publish |
| Snapshot | Context/config thay đổi sau dispatch không đổi params operation đã lưu |
| Graph | Nhánh đúng, condition false vẫn SUCCEEDED, loop giới hạn, server step không gửi Pi |
| Identity/session | Duplicate ACK lại; payload conflict bị chặn; event/command sai session không làm tiến workflow |
| Event ordering | Lỗi trước STARTED và STEP_ERROR trước ACK hủy vẫn xử lý đúng |
| Crash | Crash trước/sau commit, publish, ACK không completion/chuyển bước hai lần |
| Physical recovery | Cancel, timeout, restart và physicalStopConfirmed thiếu/false giữ recovery khi cần |
| UI callback | Sai quyền/step/type, key trùng đổi body hoặc sau deadline bị chặn |
| Authorization | Hết hạn, sai purpose/robot/operation và replay không mở chốt |
| Alert/WAIT | ACK trước/sau WAIT, ANY/ALL và sai recipient không gây thức dậy sai |
| Group | Chỉ PATH_UNAVAILABLE sau dừng cho phép xét điểm khác, đúng map và giới hạn visits |
| Mapping | Sai hash/session, upload duplicate, preview và hủy phiên mở xử lý đúng |
| AI | Observation trùng/cũ/sai map/zone bị chặn; không tự bổ sung metric thiếu |

Tái dùng test resolver, transition, authorization, runtime event, delivery và MySQL hiện có. Bổ sung integration MQTT với broker local và migration test MySQL. Test robot thật ghi riêng; unit test, broker test hoặc payload fixture không thay nghiệm thu motor/servo/Nav2/AI.

Mỗi phase ghi: commit/code thay đổi, test đã chạy, kết quả, dependency ngoài repo đã xác nhận và các chức năng còn bị chặn. Phase 0 đã chạy kiểm chứng schema offline và nhóm Java test nêu trên; kiểm tra thêm UTF-8, liên kết, cấu trúc phase và phạm vi diff. Các kiểm thử integration MQTT/phần cứng nằm ở phase sau.

### 4.2. Giá trị và dependency chưa xác nhận

| Hạng mục | Trạng thái tại thời điểm lập tài liệu | Cần xác nhận bởi |
|---|---|---|
| Broker host/TLS/CA/ACL | Chưa xác nhận triển khai thật | Broker + BE + Pi |
| MQTT credentials | Chưa xác nhận; không ghi secret vào tài liệu/command | Người quản lý broker |
| UUID robot/map/endpoint/zone | Chưa xác nhận dữ liệu và profile thực tế | BE + Pi + AI |
| Schema gốc Pi | Chưa có trong repo BE đã kiểm tra | Pi |
| Code/version Pi đang chạy | Chưa được đọc hoặc kiểm chứng trong workspace này | Pi |
| STAFF_VERIFIED trên Pi | Tài liệu hiện nói chưa hỗ trợ hoàn chỉnh | Pi + BE |
| UI/SSE/audio client | Chưa có repo/code để xác nhận thực thi | UI |
| Model/producer AI | Chưa xác nhận code, weights, inference và dữ liệu thực | AI |
| Phần cứng/commissioning | Chưa có bằng chứng test thực trong lần này | Pi + người vận hành |

### 4.3. Quy cách và ranh giới bàn giao

- File kế hoạch UTF-8: `docs/04_Ke_hoach_BE_MQTT_theo_phase.md`; giữ nguyên năm tài liệu nguồn. Artifact Phase 0 bổ sung trong `docs/phase0-contract-baseline.md` và `docs/schemas/`.
- Mỗi phase có hiện trạng, việc BE cần làm, checklist ngoài repo và tiêu chí đạt; liên kết tới code/test hiện có.
- Contract schema 3 và MQTT đã được nối vào runtime BE theo subset/gate trong các tài liệu bàn giao; schema Pi vẫn dựng từ tài liệu, chưa xác nhận code gốc.
- Phase 10 cần hoàn thiện contract/implementation Pi và kiểm delivery qua MQTT/robot thật. Phase 11 cần Pi áp dụng/ACK profile, AI nhận zone config và nghiệm thu navigation thật. Phase 12 cần Pi SLAM/upload, UI và commissioning Map; phần BE ở [bàn giao Phase 12](phase12-implementation.md). Phase 13–14 vẫn cần triển khai. Test BE/broker local không đồng nghĩa nghiệm thu robot hoặc chuyển hoàn toàn khỏi REST.
