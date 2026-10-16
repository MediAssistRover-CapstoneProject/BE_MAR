# Phase 14 — Chuyển runtime sang MQTT và kiểm tra vận hành

Ngày thực hiện: **13/10/2026**. Phạm vi: BE trong workspace hiện tại. Theo lựa chọn của người dùng, lượt này chỉ làm Phase 14; không thêm Phase 15.

**Đã triển khai cơ chế chuyển đổi trên BE; chưa chuyển đổi môi trường sản xuất hoặc nghiệm thu robot thật.** Migration mặc định giữ chế độ tương thích. Các cờ MQTT, mapping và AI tiếp tục cần cấu hình theo môi trường. Không tự publish các workflow DRAFT hoặc bật UNLOCK khi Pi chưa hỗ trợ authorization đã kiểm chứng.

## 1. Chế độ chuyển đổi lưu bền vững

[V30](../src/main/resources/db/migration/V30__mqtt_rollout_control.sql) thêm `MqttRolloutControl` và `MqttRolloutAudit`. [MqttRolloutService](../src/main/java/com/mediassistrover/backend/mqtt/MqttRolloutService.java) đọc chế độ từ DB, độc lập với `app.mqtt.enabled`.

| Chế độ | Tạo runtime mới | Thực thi Pi qua REST | Job đang có |
|---|---|---|---|
| `LEGACY_COMPATIBLE` | Giữ lựa chọn workflow hiện có | Giữ tương thích schema 2 | Giữ transport gốc |
| `DRAINING` | Chỉ schema 3 | Cho runtime cũ hoàn tất/reconcile | Không chuyển transport |
| `MQTT_ONLY` | Chỉ schema 3, BE điều phối | Các API thực thi cũ trả 410 | Lịch sử vẫn đọc được |

Chuyển chế độ theo thứ tự `LEGACY_COMPATIBLE → DRAINING → MQTT_ONLY`; có thể giữ nguyên chế độ để bật/tắt `dispatchHalted`. Không cho quay về chế độ trước hoặc bỏ qua DRAINING. Khi MQTT bị tắt, chế độ DB vẫn giữ nguyên: không mở lại API execution REST.

- Runtime request chọn workflow schema 3 khi đang DRAINING/MQTT_ONLY; không chọn bản legacy có version cao hơn và không fallback khi chưa có workflow mới phù hợp.
- Schedule chỉ được tạo runtime mới từ schema 3. Retry của lần chạy đã tồn tại trả đúng Job cũ, giữ transport và identity.
- Publish workflow schema 2 bị chặn từ DRAINING. Schema 3 vẫn phải qua validate/compile, kiểm definition hash và readiness hiện có.
- Trước khi vào MQTT_ONLY, mọi Job legacy QUEUED/ACCEPTED/RUNNING/RECOVERY_REQUIRED phải kết thúc hoặc reconcile. Job terminal có JobStep RECOVERY_REQUIRED chưa được reconcile cũng chặn chuyển đổi.
- Row điều khiển bị thiếu thì chặn tạo/dispatch/thực thi legacy; không tự khởi tạo lại chế độ tương thích.
- Thay đổi cần ADMIN đang ACTIVE, `expectedRevision` hiện tại và `note`. Cùng transaction ghi state và audit; revision cũ trả 409.

Tạo runtime/publish giữ shared lock trên row điều khiển đến commit; thay đổi chế độ giữ exclusive lock. Nhờ đó DRAINING chờ các transaction tạo runtime đã bắt đầu hoàn tất rồi mới chặn runtime legacy mới. Shared lock cho phép các request đồng thời, tránh tuần tự hóa toàn bộ việc tạo Job.

## 2. API quản trị và quan sát

[MqttRolloutController](../src/main/java/com/mediassistrover/backend/controller/pi/MqttRolloutController.java), chỉ ADMIN:

```http
GET /api/v1/admin/mqtt-rollout
Authorization: Bearer <admin-token>
```

Kết quả của [MqttOperationsStatusService](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationsStatusService.java) gồm:

- Mode, revision, dispatchHalted và số Job legacy đang chặn chuyển đổi.
- MQTT enabled, kết nối broker của instance hiện tại, cấu hình dispatch và khả năng dispatch hiện tại.
- Count/tuổi bản ghi lâu nhất của outbox chưa giải quyết, lệnh chưa gửi, inbox PENDING và event ACK chưa publish.
- Số Job recovery và phiên mapping còn mở.
- Trung bình độ trễ từ **lần publish gần nhất** đến COMMAND_ACK, đơn vị ms; null khi chưa có mẫu. Đây không phải độ trễ tổng từ tạo lệnh hoặc thời gian thực hiện hành động.
- Từng robot enrolled: handshake accepted/acked, phiên có đủ điều kiện dispatch, tuổi heartbeat và lý do phiên bị chặn.
- Workflow schema 3 chưa archived: trạng thái, acceptsNewJobs, prerequisites cấu hình và lý do bị chặn.

`dispatchEffective=true` chỉ cho biết instance có cấu hình dispatch, không bị halt và có ít nhất một robot với session fresh. Một MOVE cụ thể vẫn phải qua kiểm map/profile; UNLOCK phải qua authorization; AI/mapping phải qua các gate tương ứng. `configurationReady` của workflow không thay thế validate/hash lúc publish hoặc nghiệm thu Pi/UI/AI. Báo cáo không trả credential, payload lệnh hoặc context của Job.

```http
PUT /api/v1/admin/mqtt-rollout
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "mode": "DRAINING",
  "dispatchHalted": true,
  "expectedRevision": 0,
  "note": "Bắt đầu chuyển đổi; chặn runtime legacy mới"
}
```

`expectedRevision` phải lấy từ GET gần nhất; 0 chỉ đúng sau migration mới. Mỗi lần thay đổi thành công tăng revision. Khi retry sau lỗi mạng, GET lại state trước (audit được lưu trong DB); không tự tăng revision rồi phát lại request.

## 3. Ngừng các API execution REST

Khi ở MQTT_ONLY, service kiểm mode trước logic execution và trả HTTP 410 với `error=LEGACY_EXECUTION_RETIRED`:

| API cũ dưới `/api/v1/pi` | Thay thế |
|---|---|
| `GET /commands/next`, `POST /commands/{commandId}/ack` | Command outbox, EXECUTE_STEP/CANCEL_STEP và COMMAND_ACK |
| `POST /jobs/{jobId}/started`, `/result`, `/errors` | BE quản lý trạng thái Job |
| `POST /job-steps/{jobStepId}/started`, `/result`, `/errors` | MQTT STEP_STARTED/STEP_RESULT/STEP_ERROR |
| `POST /jobs/{jobId}/steps/{stepId}/visits` | BE chọn graph và tạo visit |
| `POST /step-services/execute`, `GET /job-steps/{jobStepId}/service-result` | BE thực thi server step và callback staff/UI |
| `POST /heartbeat`, `POST /telemetry/location` | MQTT heartbeat/telemetry |

Các controller REST admin/staff, Job GET, workflow, catalog/map/profile/zone config, mapping upload/download, xác thực và ingestion AI đã chọn vẫn dùng contract hiện có. Không xóa schema 2 hoặc dữ liệu runtime lịch sử. Request không hợp lệ vẫn có thể bị chặn ở authentication/binding trước khi vào service.

Code nối guard: [PiJobRuntimeServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java), [PiStepServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiStepServiceImpl.java), [PiIntegrationServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiIntegrationServiceImpl.java), [JobExecutionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobExecutionServiceImpl.java), [WorkflowServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/WorkflowServiceImpl.java).

## 4. Dừng dispatch và xử lý lỗi triển khai

`dispatchHalted=true` chặn tạo outbox EXECUTE_STEP mới và chặn publication/retry EXECUTE_STEP thông thường ở [MqttStepDispatchService](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java) và [MqttPublicationPlans](../src/main/java/com/mediassistrover/backend/mqtt/MqttPublicationPlans.java).

- CANCEL_STEP và STOP cleanup cho phiên mapping đang mở vẫn được phép, nếu MQTT/session đáp ứng các kiểm tra hiện có.
- BE tiếp tục nhận/lưu/ACK event, heartbeat và kết quả của hành động đã gửi. Halt không cấp quyền kết thúc giả cho step vật lý.
- Halt không hủy hành động đã chạy, không thu hồi packet đã được chuẩn bị/gửi trên mạng và không đóng băng deadline. Lệnh đã hết hạn không được nới `expiresAt` khi resume.
- Để dừng tác động đang chạy, dùng API cancel Job hiện có; giữ robot đến stop evidence hoặc RECOVERY_REQUIRED. Khi mất kết nối, đối soát HELLO/journal và dùng operation recovery theo Phase 8–12.
- Resume cùng mode với `dispatchHalted=false`; giữ commandId, operationKey, params snapshot và expiresAt của operation cũ.

Các kiểm tra halt dùng transaction READ_COMMITTED riêng để thấy thay đổi đã commit dù transaction Job bắt đầu trước đó. Không lấy mode từ cache JPA của Job hoặc từ flag kết nối.

Khi rollback, giữ bản BE có guard chuyển đổi, halt dispatch và reconcile. Không downgrade sang binary bỏ qua latch, không đổi Job schema/transport và không phát lại thao tác vật lý qua polling REST.

## 5. Trình tự chuyển đổi môi trường thực

1. Backup DB và cấu hình; deploy BE có V30. GET state, kiểm migration/readiness và các Job đang giữ robot. Migration không tự chuyển mode hay publish workflow.
2. Chuyển DRAINING, ưu tiên halt dispatch mới trong thời gian kiểm tra. Hoàn tất hoặc reconcile Job legacy; không migrate transport của Job đang chạy. API tạo request không có workflow schema 3 phù hợp phải báo lỗi.
3. Cấu hình broker TLS/CA/ACL, credentials và robot UUID thật. Cho Pi handshake/heartbeat, đối soát journal và catalog/profile. Kiểm owner instance/session; retained presence không được coi là sẵn sàng.
4. Chỉnh tham số workflow schema 3 bằng API hiện có. `POST /api/v1/admin/workflows/{id}/validate`, rồi `/publish` từng workflow khi hash/readiness đúng. Không publish toàn bộ DRAFT chỉ vì có migration hoặc JSON Schema.
5. GET lại báo cáo; `legacyJobsBlockingCutover` phải bằng 0. Chuyển MQTT_ONLY với revision hiện tại. Loại client polling/API execution cũ; kiểm rằng server trả 410 và REST catalog/upload vẫn hoạt động.
6. Bật và nghiệm thu lần lượt theo bảng dưới. Khi có lỗi, halt, cancel khi cần và đối soát; không tự fallback REST. Ghi robot/map/endpoint, phiên bản Pi/UI/producer, người kiểm, log/event IDs và bằng chứng cho mỗi chức năng.

| Thứ tự | Chức năng | Gate/kiểm tra trước khi nghiệm thu |
|---|---|---|
| 1 | WAIT/DURATION | enabledStepTypes WAIT, handshake, timeout/cancel; kết quả do Pi trả |
| 2 | ROBOT_HEALTH | EXECUTE_SENSING, sensing health thật; sensors_ready phải được workflow kiểm |
| 3 | LOCK | CONTROL_HARDWARE, compartment và thiết bị thật; output contract và stop evidence |
| 4 | MOVE | map UUID/hash, navigation profile ACK, endpoint accessible, tốc độ demo 0.05 m/s |
| 5 | Delivery | staff/QR, quyền mở gắn operation thật ở Pi, UI LOAD/UNLOAD/door/VERIFY tách riêng |
| 6 | Endpoint group | BE chọn một endpoint, thử hữu hạn; chỉ đổi đích khi PATH_UNAVAILABLE và xác nhận dừng |
| 7 | Mapping/AI | capability/cờ tương ứng; upload/hash/receipt thật, model/frame/zone/profile và UI/producer |

`enabledStepTypes` là gate nhóm step, không phải bằng chứng rằng mọi action trong nhóm đã nghiệm thu. Chỉ publish workflow sử dụng các action đã kiểm. UNLOCK chính thức vẫn bị chặn theo gate authorization Pi hiện có; SPEAK, điều khiển tay mapping và tự sạc chưa có completion/hardware thực không được ghi đạt.

## 6. Kiểm thử và giới hạn nghiệm thu

- [MqttRolloutIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttRolloutIntegrationTest.java): drain/cutover, concurrent creation fence, schema 3 selection, legacy schedule retry, revision/quyền ADMIN, thiếu control, REST 410/catalog, disable MQTT, halt/snapshot, cancel và báo cáo vận hành.
- `PUT` yêu cầu khai báo rõ `dispatchHalted` và `expectedRevision`; thiếu field trả 400, tránh mặc định false vô tình resume dispatch. Có kiểm thử HTTP cho validation và state đã commit.
- [Phase30MqttRolloutMigrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase30MqttRolloutMigrationIntegrationTest.java): V29 → V30, giữ workflow và Job legacy RUNNING/schema 2/transport, mặc định compatibility, constraint và Flyway validate. Chỉ dùng DB test có prefix được kiểm tra.
- [MappingRuntimeIntegrationTest](../src/test/java/com/mediassistrover/backend/MappingRuntimeIntegrationTest.java): thêm case halt DB vẫn gửi STOP cleanup, đóng session và chỉ giải phóng robot sau result.
- [MqttTlsBrokerIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttTlsBrokerIntegrationTest.java): MQTT 5/TLS với Mosquitto local, ACL, handshake/heartbeat trên 15 giây, từng hành động WAIT và cancel/result trước cancel ACK.

Kết quả kiểm thử trên Java 21/MySQL 9.5:

| Bộ kiểm tra | Kết quả |
|---|---|
| Nhóm cuối: rollout, V30 migration, mapping, broker TLS, runtime, cancel/recovery, request/REST legacy, authorization | **139/139 pass**, không skip; log `target/phase14-final-verified-tests.log`, summary `target/phase14-final-test-summary.json` |
| Chạy lại hai bài mapping trên cùng DB sau cleanup REST legacy | **2/2 pass**; log `target/phase14-mapping-repeat-tests.log` |
| Hồi quy toàn bộ trước chỉnh sửa cuối về field bắt buộc/cleanup fixture | **482 test: 476 pass, 1 fail, 5 skip**; log `target/phase14-full-tests.log`, summary `target/phase14-full-test-summary.json` |
| Contract offline | **9 schema, 131 fixture: 88 positive, 43 negative**, 11 StepType |
| Tài liệu | UTF-8, link nội bộ và `git diff --check` đạt; năm tài liệu nguồn không sửa |

Lỗi toàn bộ suite là `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`: MySQL 9.5 trả `possible_keys=null` khác kỳ vọng. Đây là bộ analytics cũ đã ghi nhận ở các phase trước; không phải bài rollout MQTT mới. Không sửa query/index hoặc làm yếu assertion để che lỗi. Năm bài skip: Phase19MigrationTest thiếu Docker, migration Phase23/24/26/28 yêu cầu prefix DB test của phase tương ứng. V30 migration và hai bài broker TLS đã chạy thật trong nhóm cuối.

Một lần chạy lại phát hiện [RequestJobIntegrationTest](../src/test/java/com/mediassistrover/backend/RequestJobIntegrationTest.java) cleanup xóa metadata workflow schema 3 của bộ mapping. Đã giới hạn cleanup vào schema 2, cùng cách Phase7IntegrationTest hiện có. Nhóm 139 test được chạy lại trên DB `be_mar_phase14_final_20261013`; không dùng kết quả lỗi fixture cũ làm kết quả cuối.

Test broker local sử dụng Pi mô phỏng trong JVM; không thay thế navigation, khóa/chốt, authorization, SLAM hoặc AI trên robot thật. Chưa có broker/credential sản xuất, robot/map/endpoint đã nghiệm thu hoặc code Pi/UI/AI được xác nhận trong workspace.
