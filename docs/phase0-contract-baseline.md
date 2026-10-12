# Phase 0 — Baseline và contract BE–Pi

**Ngày:** 12/10/2026.  
**Trạng thái:** hoàn thành bộ baseline/contract tham chiếu phía BE; xác nhận với schema gốc, code Pi và broker thực tế vẫn chờ bên tích hợp.  
**Phạm vi:** ma trận 11 StepType, schema tham chiếu, quyết định version/compatibility và kiểm chứng offline. Chưa triển khai MQTT, orchestrator, validator schema 3, migration hoặc thay executor.

## 1. Nguồn và mức xác nhận

| Nguồn | Vai trò | Mức xác nhận |
|---|---|---|
| [Code enum](../src/main/java/com/mediassistrover/backend/enums/StepType.java), [validator input](../src/main/java/com/mediassistrover/backend/validation/WorkflowStepContractValidator.java), [validator output](../src/main/java/com/mediassistrover/backend/validation/StepResultContractValidator.java) | Truth về runtime BE schema 2 | Đã đọc code |
| [Ma trận hiện hữu](step-contract-matrix.md) | Contract schema 2 đang thực thi | Giữ nguyên, đối chiếu lại bằng code |
| [Giao tiếp Markdown](01_Giao_tiep_va_toan_bo_field.md), [MQTT_BE](MQTT_BE.md) | Wire MAR-STEP-2: envelope, topic, ACK, session | Tài liệu bàn giao; chưa đọc code Pi |
| [Input/output Markdown](02_Input_Output_11_Step_Type.md) | Pi input/output và thiết kế BE schema 3 | Chuẩn tài liệu mới được chọn |
| [DOCX tham số](02_Tham_so_11_Step_Type.docx) | Bản handoff trước, 08/10/2026 | Nguồn đối chiếu delta; không ghi đè quyết định mới |
| [Danh sách thiếu](03_Thieu_gi_va_thu_tu_trien_khai.md) | Gợi ý backlog | Có mô tả đã cũ so với code BE |

Schema trong [docs/schemas](schemas/README.md) được dựng lại từ các nguồn trên và mang metadata provenance. Đây không phải bản schema gốc Pi. File `$id` dùng `.invalid` làm identity; script kiểm hoàn toàn bằng tài nguyên local.

## 2. Ma trận 11 StepType

Input/output dưới đây là input đã resolve và kết quả nghiệp vụ đã hoàn tất. Instruction đang chờ không được coi là output thành công. WAIT có hai biến thể trong một tên StepType.

| StepType | Executor hiện tại → đích | Handler/callback hiện có | Input đích | Output đích | Phần còn phải sửa |
|---|---|---|---|---|---|
| **MOVE** | PI → PI | Pi callback qua [PiJobRuntimeServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java) | map_id, endpoint_id, mode, max_speed_mps | reached=true, endpoint_id, action_status=4, last_feedback_pose, distance_remaining_m, endpoint_group_id=null, attempts[] | Resolve/snapshot trước gửi; validator hiện thiếu 2 field cuối; kiểm map/hash/profile |
| **INTERACT** | SERVER → BE/UI | [InteractStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/InteractStepHandler.java) tạo instruction; Device callback bị giới hạn theo authority | prompt_key, action, data | completed=true, prompt_key, action, source, actor_account_id, completed_at; confirmed/closed/locked/values theo hành động | Callback UI mới; phân biệt nhận hiển thị, dữ liệu nhập và xác nhận staff; SPEAK cần audio client |
| **DATA_SERVICE** | SERVER → BE | [DataServiceStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/DataServiceStepHandler.java) có 5 service và hoàn tất đồng bộ | service_key và input theo registry dưới đây | request_id, data theo service | Scope map/robot, thêm COMMIT_MAP_ARTIFACT; nối orchestrator |
| **AUTHENTICATE** | SERVER → BE/Staff | [AuthenticateStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/AuthenticateStepHandler.java) chờ; [staff verification](../src/main/java/com/mediassistrover/backend/service/impl/AuthVerificationServiceImpl.java), [QR verification](../src/main/java/com/mediassistrover/backend/service/impl/QrVerificationServiceImpl.java) có thật | purpose, service_request_id | verified, verification_id, actor_account_id, purpose, service_request_id, verified_at, valid_until | Đổi output theo version, resume orchestrator; Pi chưa kiểm STAFF_VERIFIED để mở |
| **CONTROL_HARDWARE** | PI → PI | Pi callback; BE hiện kiểm grant input trước chấp nhận UNLOCK result | compartment_id=CART_MAIN, action; authorization dành sẵn | compartment_id, action, published=true, controller_ack=false, verified=false, commanded_state, position_raw, ros_servo_id, authorization_mode | Params không chứa service_request_id/PWM; validator 9 field; UNLOCK chính thức chờ Pi kiểm quyền |
| **HANDLE_PAYLOAD** | SERVER → BE/UI | [HandlePayloadStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/HandlePayloadStepHandler.java) chờ; [DeliveryServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/DeliveryServiceImpl.java) đã complete đúng JobStep | action=LOAD/UNLOAD, service_request_id, compartment_id, items[{item_code,quantity}] | confirmed, action, service_request_id, compartment_id, items, actor_account_id, confirmed_at, source=STAFF_MANUAL, note? | Snapshot items, callback riêng; không đòi đóng/khóa ngay ở xác nhận xếp/lấy hàng |
| **EXECUTE_SENSING** | MIXED → PI | Pi callback; chưa có server sensing handler | sensing_type; zone_id/window_sec với AI; action/session/artifact với MAPPING | sensing_type, metrics, captured_at, evidence_ref; AI thêm map_id/zone_id/model_version; mapping thêm mapping_session_id | Hiện output task_type; thêm MAPPING; kiểm nguồn/freshness; producer AI ngoài repo |
| **NOTIFY** | SERVER → BE | [NotifyStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/NotifyStepHandler.java), [AlertServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/AlertServiceImpl.java) có delivery/ACK ANY/ALL | notification_type, severity, data, require_ack, message? | alert_id, notification_type, persisted=true, requires_ack | Schema 3 lưu xong là hoàn tất; dùng WAIT riêng; recipients do BE quản lý |
| **WAIT** | MIXED → PI/DURATION hoặc BE/EVENT | [WaitStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/WaitStepHandler.java), [WorkflowEventServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/WorkflowEventServiceImpl.java), scheduler có duration completion | DURATION: duration_sec; EVENT: event_type/correlation_key | completed=true, wake_reason, elapsed_sec, event=null hoặc {event_type,correlation_key,occurred_at,data} | Route theo wait_type; duration Pi không được scheduler tự complete; đổi EVENT output theo version |
| **CHECK_CONDITION** | PI → BE | Chưa có ServerStepHandler; [transition evaluator](../src/main/java/com/mediassistrover/backend/service/impl/WorkflowRuntimeTransitionServiceImpl.java) đã có logic so sánh | expression{path,operator,value?} | value:boolean; false vẫn là kết quả hợp lệ | Đổi executor, handler chung evaluator; context step khác root transition |
| **SET_CONTEXT** | PI → BE | [SetContextStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/SetContextStepHandler.java) đã có SET/INCREMENT | operations[{path:/vars/identifier,operation?,value}] | updated:{identifier:value} | Đổi executor; output hiện changed_keys/values; atomic/dedup INCREMENT |

Schema resolved input Pi có 4 nhóm; schema BE có 8 nhóm, trong đó WAIT chung tên nhưng khác wait_type. Không thêm CALL_WORKFLOW hoặc loại FALL_DETECTION vào wire hiện tại.

### DATA_SERVICE registry

| service_key | Input thêm | data output đích | Hiện trạng |
|---|---|---|---|
| QUERY_HOSPITAL_INFORMATION | question, language_code? mặc định vi-VN | found, answer, source hoặc fallback_code, items[], count; giữ language_code | Có truy vấn theo câu hỏi/từ khóa/bài viết, không phải AI sinh câu trả lời |
| RESOLVE_TARGETS | endpoint_group_id | targets[] | Có handler, cần kiểm scope và accessible |
| SELECT_TARGET | targets[], index >=0 | has_target; true có target, endpoint_id | Có handler |
| CHECK_TARGET | endpoint_id | is_valid; true có endpoint | Kiểm DB, không chứng minh đường đi vật lý |
| CREATE_FALL_INCIDENT | detection_event_id | incident_id, created | Có handler, kiểm SUSPECTED_FALL và robot |
| COMMIT_MAP_ARTIFACT | mapping_session_id, artifact_id, floor_id, map_name | map_id, artifact_id, asset_hash, created | Chưa có; cần artifact/file được kiểm chứng |

Endpoint trả số cho x_m/y_m/yaw_rad. Giữ request_id ở root vì handler hiện có dùng operationKey làm identity. Các `data` được kiểm theo service_key; không cấp quyền gọi URL/SQL/script tùy ý.

### Pi sensing và mapping

| Biến thể | Params | Điều kiện ngoài schema |
|---|---|---|
| ROBOT_HEALTH/SIDE_CLEARANCE | sensing_type | Range/scan mới; health có thể báo sensors_ready=false; SIDE_CLEARANCE success cần range fresh |
| CROWD_DENSITY/RESTRICTED_ZONE | sensing_type, zone_id, window_sec? 0–30 | map/zone/model đúng, mẫu mới; timeout >= window+1; window là chờ mẫu, không tính trung bình |
| MAPPING/START | sensing_type, action, mapping_session_id, preview_rate_hz? 0.2–1 | START timeout >= profile.start_timeout_sec+10; cấu hình demo 60s |
| MAPPING/SAVE | sensing_type, action, mapping_session_id, artifact_id | Cùng Job/session; timeout tối thiểu 60s, demo 90s; uploaded=false không tạo Map BE |
| MAPPING/STOP | sensing_type, action, mapping_session_id | Timeout tối thiểu 15s, demo 20s; đóng session, không nhất thiết đổi state SAVED_LOCAL |

Tốc độ MOVE tối đa 0.3 m/s và không vượt profile Pi; demo 0.05. WAIT/DURATION 1–3600s, timeout >= duration+1. `timeoutSec` wire là number 1–3605; BE cấu hình giây nguyên, schema vẫn nhận số thập phân.

## 3. Quyết định khi tài liệu khác nhau

| Điểm khác | DOCX/bản cũ | Markdown mới/code hiện tại | Quyết định Phase 0 |
|---|---|---|---|
| NOTIFY | Đợi ACK ngay trong NOTIFY | Markdown mới tách persisted/WAIT; code cũ đợi delivered/acked | Schema 2 giữ cũ, schema 3 dùng NOTIFY → WAIT/EVENT |
| INTERACT output | input_data, verification_source hoặc actor_account_id đề xuất | Markdown dùng values/source/actor_account_id/time; runtime 2 dùng input_data/actor_user_id | Schema 3 theo Markdown; giữ closed/locked cho nhánh graph |
| SET_CONTEXT output | changed_keys/values | Markdown chốt updated; code đã có handler trả changed_keys/values | Schema 3 dùng updated; Phase 2/4 sửa handler và mapping cùng nhau |
| AUTHENTICATE | Chỉ purpose, mô tả đang hoãn | Markdown thêm request; BE đã có grant/QR completion | Giữ tái dùng BE; scope service_request_id rõ; Pi authorization vẫn chưa được chứng minh |
| HANDLE_PAYLOAD | verification_mode=STAFF_CONFIRMED | Markdown có action/request/compartment/source/time; code cũ đòi đóng/khóa | Schema 3 tách payload → close → lock → verify; schema 2 giữ cũ |
| DATA_SERVICE | Output data; COMMIT có version_no/status đề xuất | Code trả request_id; Markdown COMMIT chốt map_id/artifact_id/hash/created | Giữ request_id và câu trả lời bệnh viện; COMMIT theo Markdown, Map chưa active |
| MOVE output | Các field hành trình | Validator hiện chỉ 5 root fields | Pi shape đủ 7 field; phase sau bổ sung validator version mới |
| Sensing output | sensing_type | Runtime hiện task_type; Markdown có AI/MAPPING mở rộng | Schema 3 dùng sensing_type, giữ JSON nguồn AI |
| Timeout | Một số giải thích cũ integer | Markdown giao tiếp khẳng định number | Wire number; cấu hình BE mặc định giây nguyên |
| Capability | Có params authorization và mode ESCORT | Pi chưa kiểm STAFF_VERIFIED; ESCORT dùng NavigateToPose | Không suy capability từ shape; chưa coi escort là bám người |

Các mô tả backlog “chưa có WAIT/EVENT/SET_CONTEXT handler”, “confirm delivery chưa complete JobStep”, “NOTIFY chưa dùng severity” và “query chỉ trả danh sách” đã lệch code hiện tại. Ma trận trên dùng code làm baseline BE, giữ backlog đúng phần còn thiếu: executor, orchestration, snapshot, schema và tích hợp.

## 4. Thiết kế version và compatibility

| Lớp version | Hiện tại | Đích đã chốt | Trạng thái Phase 0 |
|---|---|---|---|
| REST API | /api/v1 | /api/v1 | Không đổi |
| Transport protocol | 1.0 | 1.0 | Hằng PROTOCOL giữ nguyên |
| MQTT contract | Chưa có transport | MAR-STEP-2 | Thêm hằng MQTT_CONTRACT; chưa thêm client |
| Step schema BE | 2 | Hỗ trợ 2 và 3 riêng | Hằng PLANNED_STEP_SCHEMA=3; runtime vẫn chỉ nhận 2 |
| Graph version | 3 | 3 | Không đổi |

Thiết kế áp dụng ở Phase 2:

1. Workflow/Job chọn validator theo schemaVersion của definition/snapshot; không chọn theo protocolVersion hay graphVersion.
2. Validator schema 2 giữ shape và ngữ nghĩa cũ. Validator schema 3 dùng contract mới và kiểm tiếp resolved input/DB/context.
3. Chỉ mở allowlist runtime 2/3 sau khi validator 3 và handler cần thiết có test. Không đưa schema 3 vào validator 2 rồi coi là hỗ trợ.
4. Workflow cũ đã publish không sửa tại chỗ. Clone version mới/schema 3, cập nhật output mapping và transition, compile/hash mới; giữ DRAFT tới khi publish được.
5. MQTT wire không có field schemaVersion mới trong command; BE quản lý version trong DB. Robot theo MAR-STEP-2 chỉ nhận shape Pi đúng tài liệu.
6. Job đang chạy giữ schema/transport cũ đến khi kết thúc hoặc reconcile. Sau cutover, Job mới dùng MQTT; không nhận cùng việc qua polling.

[StepContractVersions](../src/main/java/com/mediassistrover/backend/validation/StepContractVersions.java) chỉ thêm danh tính contract và version dự kiến. [StepContractVersionsTest](../src/test/java/com/mediassistrover/backend/StepContractVersionsTest.java) khóa schema 3 khỏi runtime trước khi có implementation đúng. Enum executor, validator output và Flyway chưa đổi trong Phase 0.

## 5. Contract giao tiếp và callback

- EXECUTE_STEP chỉ mang một `step` đầy đủ metadata và params đã resolve; CANCEL_STEP mang jobId/jobStepId ngoài step. Command hết hạn không được hiểu là vẫn có thể bắt đầu.
- Topic prefix `mar/robots/{robotId}`; commands/event-acks QoS1 retain=false. Events QoS1, heartbeat/preview QoS0; presence QoS1 retain=true.
- Pi session mới qua HELLO; BE gửi HELLO_ACK trong 10s và heartbeat mỗi 3s. Retained presence không xác nhận session readiness.
- COMMAND_ACK và STEP_STARTED phải được BE commit/ACK trước Pi thực thi; result/error commit trước EVENT_ACK. HELLO dùng HELLO_ACK; heartbeat/preview/PROTOCOL_ERROR không EVENT_ACK.
- Dedup messageId và producerSessionId/sequenceNo; replay giữ identity/business payload, thay transportSessionId. Lệnh resend không đổi expiresAt/params.
- COMMAND_ACK replay result là journal snapshot, không phải handler result trực tiếp. STEP_ERROR restart có thể thiếu result/physicalStopConfirmed; không suy đã dừng.
- Callback UI dự kiến qua `/api/v1/job-steps/{jobStepId}/responses` với Idempotency-Key; không cho client cấp actor/verification. Body types nằm trong staff-step-response schema.
- AUTHENTICATE dùng verification/QR API hiện có; staff/QR thành công tại BE không chứng minh Pi kiểm quyền UNLOCK.
- Mapping upload dùng HTTP Bearer khác MQTT credential; receipt chỉ sau lưu/kiểm file bền vững. Không active Map mới từ uploaded/saved flag đơn lẻ.

## 6. Bằng chứng kiểm chứng và hạng mục chờ

**Đã kiểm chứng phía BE**

- 9 schema Draft 2020-12 hợp lệ; mọi `$ref` local resolve offline, kiểm UUID/date-time và metadata provenance.
- 129 fixture: 87 hợp lệ và 42 bị từ chối đúng kỳ vọng; input có đủ 11 StepType và các sub-action Pi/BE.
- 22 Java test đạt: version gate, validator schema 2 cho 11 StepType, resolver và transition. Không có test bị skip trong nhóm này.
- Lệnh: `mvn -B -Dtest=StepContractVersionsTest,StepResultContractValidatorTest,JobStepInputResolverTest,WorkflowRuntimeTransitionServiceTest test` với JDK 21; script schema và cài test dependency xem [README schemas](schemas/README.md).
- Không thay đổi endpoint, executor, migration, params/runtime behavior của schema 2. Chưa chạy nghiệm thu MQTT hoặc phần cứng.

**Chờ bên tích hợp**

| Hạng mục | Cần bằng chứng | Trạng thái |
|---|---|---|
| Schema Pi gốc | File/version/hash và diff với bộ dựng lại | Chưa cung cấp |
| Code worker đang chạy | Version/commit thực tế, mẫu output từng action | Chưa đối chiếu |
| Broker | Host, port, CA/hostname, ACL, BE/Pi account; secret lưu riêng | Chưa xác nhận |
| UUID/profile thực | Robot/map/endpoint/zone và hash bản đồ đã đồng bộ | Chưa xác nhận |
| STAFF_VERIFIED | Pi kiểm quyền, scope operation và chống replay thật | Chưa hỗ trợ theo tài liệu bàn giao |
| UI/AI/audio | Repo/client/producer và dữ liệu thực | Chưa kiểm code |

Phase 0 hoàn thành phần BE có thể thực hiện tại workspace; contract Pi còn mang nhãn reconstructed/unverified. Phase 1–14 chưa được triển khai. Trước nghiệm thu tích hợp, phải đối chiếu schema gốc/code Pi và giải quyết khác biệt thay vì coi fixture là bằng chứng robot đã hoạt động.
