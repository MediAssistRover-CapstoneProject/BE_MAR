# Bàn giao Phase 5–7: MQTT và điều phối từng hành động

Ngày: **12/10/2026**. Tiếp nối [Phase 3–4](phase3-4-implementation.md) và [kế hoạch](04_Ke_hoach_BE_MQTT_theo_phase.md).

**Phạm vi đã triển khai:** MQTT 5/TLS, handshake/heartbeat, command outbox, event inbox và nối orchestrator với từng hành động Pi. Schema 2 giữ runtime REST; schema 3 dùng BE điều phối. MQTT và dispatch mặc định tắt; không tự publish workflow DRAFT V19 hoặc chuyển Job đang chạy sang transport khác.

**Phạm vi kiểm chứng:** MySQL test riêng và broker Mosquitto local có TLS/password/ACL, với simulator Pi trong test. Chưa có code Pi/UI/AI, broker sản xuất, robot/map/endpoint thật trong workspace để nghiệm thu phần cứng. Các schema wire vẫn mang nguồn `RECONSTRUCTED_UNVERIFIED_PI` từ Phase 0.

## Phase 5 — Kết nối, handshake, telemetry

### Hiện trạng và thay đổi BE

- Thêm dependency `org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5` trong [pom.xml](../pom.xml), dùng API async MQTT 5 của [Eclipse Paho](https://github.com/eclipse-paho/paho.mqtt.java).
- [PahoMqttTransport](../src/main/java/com/mediassistrover/backend/mqtt/PahoMqttTransport.java) dùng TLS, kiểm hostname và trust store PKCS12; có thể thêm key store cho mTLS. Credentials lấy từ cấu hình triển khai, không nằm trong payload. Chỉ coi connection sẵn sàng sau SUBACK đủ bốn topic; reconnect thay connection identity của BE.
- [MqttProperties](../src/main/java/com/mediassistrover/backend/mqtt/MqttProperties.java) quản lý allowlist robot/StepType và gate dispatch. URI plaintext hoặc cấu hình credential/timing sai bị chặn khi bật client.
- [MqttTopics](../src/main/java/com/mediassistrover/backend/mqtt/MqttTopics.java), [MqttWire](../src/main/java/com/mediassistrover/backend/mqtt/MqttWire.java), [MqttInboundRouter](../src/main/java/com/mediassistrover/backend/mqtt/MqttInboundRouter.java) kiểm topic, robotId, QoS, retain và schema. Parser chặn key trùng, nhiều object nối tiếp, nesting quá sâu và payload trên 1.000.000 byte.
- [MqttSessionRegistry](../src/main/java/com/mediassistrover/backend/mqtt/MqttSessionRegistry.java) lưu phiên theo robot, transport/producer và connection/instance BE. Retained presence không mở phiên. HELLO được đối soát journal trước HELLO_ACK; dispatch cần ACK handshake đã publish và heartbeat mới trong 15 giây.
- [MqttMaintenanceScheduler](../src/main/java/com/mediassistrover/backend/mqtt/MqttMaintenanceScheduler.java) phát SERVER_HEARTBEAT mỗi 3 giây sau handshake; đóng session khi quá 15 giây không heartbeat, kể cả HELLO chưa có heartbeat đầu tiên. HELLO replay sau BE/broker reconnect phải chạy lại đối soát, không chỉ dùng receipt cũ để hồi sinh phiên.
- Heartbeat kiểm producer, timestamp, map và active step; cập nhật Robot, raw battery và location khi dữ liệu pose mới/đúng map. Battery raw có đơn vị UNKNOWN, không tự đổi thành phần trăm. Job RUNNING/ACCEPTED giữ robot BUSY trong lúc chờ staff. Job QUEUED vẫn giữ reservation qua DB nhưng không ép BUSY trước khi orchestrator khởi chạy.
- Heartbeat/presence/preview không vào inbox durable và không nhận EVENT_ACK. Preview chỉ giữ bản cuối trong bộ nhớ, chưa giải nén/phục vụ UI mapping; phần đó thuộc Phase 12.

### Phối hợp ngoài repo và tiêu chí

Broker cần CA/hostname, credential riêng, ACL cho Pi chỉ đọc/ghi topic robot của mình; BE không nhận được broker principal trong payload nên bảo đảm này cần broker cấu hình thực tế. Pi cần MQTT 5, HELLO và heartbeat đúng contract, chờ HELLO_ACK trong 10 giây và theo dõi SERVER_HEARTBEAT.

Test local đã kiểm handshake, phiên sống hơn 15 giây và broker chặn publish sang robot khác. Chưa xác nhận ACL/CA/broker thật hoặc đồng hồ/NTP của robot triển khai.

## Phase 6 — Outbox, inbox và commit trước ACK

### Hiện trạng và thay đổi BE

[V21](../src/main/resources/db/migration/V21__mqtt_sessions_command_outbox_and_event_inbox.sql) tạo ba bảng, giữ nguyên migration V19/V20:

| Bảng | Identity và nội dung |
|---|---|
| RobotMqttSession | Một phiên/robot; transport, producer, BE owner/connection, HELLO, heartbeat, readiness và lý do chặn |
| RobotCommandOutbox | commandId; Job/JobStep/robot/operationKey; payload đóng băng, expiresAt, state, sendAttempts, timestamp publish/ACK; unique JobStep |
| RobotMqttInbox | messageId và unique producerSessionId/sequenceNo; raw envelope, canonical hash, trạng thái, receipt ACK và timestamp |

- [MqttInboxStore](../src/main/java/com/mediassistrover/backend/mqtt/MqttInboxStore.java) commit envelope trước xử lý. Hash chuẩn hóa thứ tự key object và bỏ riêng transportSessionId để nhận replay hợp lệ; business body đổi cùng identity là lỗi giao thức. Receipt gốc được giữ nguyên khi có conflict; Job đang giữ robot chuyển recovery trong transaction trước rejection false để không chạy graph tiếp; conflict được ghi log.
- [MqttInboxProcessor](../src/main/java/com/mediassistrover/backend/mqtt/MqttInboxProcessor.java) xử lý event, JobEvent, trạng thái và yêu cầu orchestrator trong một transaction. Khóa Job → Robot → Session → Inbox; refresh receipt có khóa để đọc kết quả transaction thắng cuộc trên MySQL REPEATABLE READ.
- Infrastructure failure/phiên chưa sẵn sàng giữ PENDING và chưa ACK. Business rejection rollback xử lý trước, sau đó lưu receipt REJECTED trong transaction khác; lỗi event của Job đang giữ robot dẫn tới recovery bảo thủ.
- [MqttPublicationPlans](../src/main/java/com/mediassistrover/backend/mqtt/MqttPublicationPlans.java) commit PUBLISHING/sendAttempts trước network publish. [MqttDurablePublisher](../src/main/java/com/mediassistrover/backend/mqtt/MqttDurablePublisher.java) chỉ gửi sau commit; ghi publish/ACK receipt trong transaction mới sau PUBACK. Rollback transaction gọi publisher không phát ACK.
- Scheduler tiếp tục PENDING/PUBLISHING/PUBLISHED và ACK chưa xác nhận publish. PUBACK chỉ chứng minh transport publish; COMMAND_ACK/STEP_STARTED/STEP_RESULT mới thay đổi runtime nghiệp vụ.
- Tắt dispatch, bỏ enrollment hoặc bỏ StepType khỏi allowlist cũng chặn publisher phát lại outbox. Thay config/context sau khi đã tạo outbox không được diễn giải lại operation cũ; params/identity/expiry tiếp tục lấy từ snapshot đã commit.
- Duplicate receipt chỉ ACK lại, không completion/wake-up lần hai. Resend giữ commandId, operationKey, params và expiresAt. Đổi transport chỉ khi command chưa từng publish hoặc HELLO đã đối soát operation đang tồn tại trong journal.
- Lệnh hết hạn chưa publish hoàn tất step TIMED_OUT mà không gửi Pi. Lệnh đã từng publish nhưng hết hạn chưa có business ACK chuyển recovery, giữ robot; không tự kết luận chưa thực thi.

### Phối hợp ngoài repo và tiêu chí

Pi phải giữ durable journal và dedup theo commandId/operationKey, giữ messageId/producerSessionId/sequenceNo khi replay và chờ EVENT_ACK. BE không thể bảo đảm tác động motor/servo đúng một lần nếu worker không thực hiện phần này.

Test MySQL kiểm commit/rollback, receipt replay, xử lý duplicate đồng thời, publish lại cùng snapshot và expiry. Các cửa sổ crash được kiểm bằng transaction rollback và trạng thái DB còn lại; chưa chạy thử kill/restart process BE/Pi trên robot thật. Full recovery/cancel thuộc Phase 8; chưa có công cụ vận hành để staff reconcile journal. Chưa triển khai HA nhiều BE cùng điều phối một robot hoặc chính sách retention dọn inbox/outbox.

## Phase 7 — Một EXECUTE_STEP, ACK, START và kết quả

### Hiện trạng và thay đổi BE

- [WorkflowOrchestratorService](../src/main/java/com/mediassistrover/backend/service/WorkflowOrchestratorService.java) vẫn chọn đúng graph edge/visit, đóng băng input và tự chạy server step. [MqttStepDispatchService](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java) được gọi với Job lock và chỉ tạo command cho selected PENDING Pi step.
- Kiểm input/scope sau resolve, session, allowlist, simulation và StepType Pi đang bật; MOVE yêu cầu map UUID và asset hash khớp. Một robot chỉ có một command chưa giải quyết. Không gửi GPIO/PWM/servo hoặc metadata BE vào params Pi.
- [WorkflowExecutionReadiness](../src/main/java/com/mediassistrover/backend/validation/WorkflowExecutionReadiness.java) chỉ mở publish/tạo Job cho action đã nối. [JobExecutionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobExecutionServiceImpl.java), [JobDispatchServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobDispatchServiceImpl.java) và API assignRobot chọn robot phù hợp transport: Pi step schema 3 cần robot MQTT enrolled; server-only schema 3 vẫn chạy được; robot enrolled không nhận Job schema 2 mới.
- [PiJobRuntimeServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java) không trả DISPATCH/CANCEL REST cho robot enrolled. Job legacy đang giữ robot làm session mới không dispatch; cần hoàn tất/reconcile trước khi enrollment. Không đổi transport giữa Job.
- [MqttRuntimeEvents](../src/main/java/com/mediassistrover/backend/mqtt/MqttRuntimeEvents.java) kiểm Job/robot/selected visit/operationKey/commandId. COMMAND_ACK accepted chưa làm step RUNNING. STEP_STARTED cần command ACK và params/StepType khớp tuyệt đối snapshot; commit rồi mới EVENT_ACK. Completion hợp lệ ghi output và wake-up cùng transaction; cập nhật SSE qua RuntimeChanged sau commit.
- STEP_RESULT cần STEP_STARTED đã commit, output schema 3 đúng action và ngữ nghĩa theo input. MOVE/hardware thiếu hoặc false physicalStopConfirmed dẫn recovery, không đi nhánh kế tiếp. STEP_ERROR có thể đến trước STARTED; giữ FAILED/TIMED_OUT/CANCELLED/RECOVERY_REQUIRED, không tự retry nghiệp vụ.
- ACK replay ACCEPTED/RUNNING không hoàn tất. Replay SUCCEEDED chỉ hoàn tất khi có nested output hợp lệ và START đã commit. Journal thiếu hoặc không đối soát được giữ recovery. Pi process mới không được tự tiếp tục operation đã thử publish.

Envelope gửi Pi đúng một object `step`:

```text
protocolVersion="1.0", contract="MAR-STEP-2", messageType="COMMAND"
commandId, correlationId, robotId, transportSessionId
commandType="EXECUTE_STEP", expiresAt
step={jobId,jobStepId,stepId,stepCode,stepType,visitNo,operationKey,timeoutSec,params}
```

Không có workflowDefinition, jobInput, context, danh sách jobSteps hoặc attemptNo trong command. Chuỗi bình thường:

```text
BE: commit snapshot/outbox → EXECUTE_STEP
Pi: COMMAND_ACK → BE commit → EVENT_ACK
Pi: STEP_STARTED → BE kiểm snapshot/commit → EVENT_ACK
Pi: thực thi → STEP_RESULT hoặc STEP_ERROR
BE: kiểm output/stop, commit completion+wake-up → EVENT_ACK
BE: chọn nhánh tiếp; server step chạy BE, Pi step tạo command riêng
```

### Action được mở và phần phối hợp

| Action | BE | Bằng chứng Pi |
|---|---|---|
| WAIT/DURATION | Dispatch, START, output, transition | Simulator chạy đủ 2 giây qua MQTT/TLS thật |
| ROBOT_HEALTH | Dispatch và validator output | Fixture/DB test; chưa đọc hardware thật |
| SIDE_CLEARANCE | Dispatch và validator output | Fixture/DB test; chưa đọc sensor thật |
| LOCK_COMPARTMENT | Dispatch và stop guard | Fixture/DB test; chưa điều khiển servo thật |
| MOVE | Scope map/endpoint/hash, dispatch và stop guard | Fixture/DB test; chưa chạy ROS/Nav2/motor thật |
| UNLOCK | Chặn publish/dispatch | Phase 10, cần authorization operation thật |
| Sensing AI/MAPPING | Chặn publish/dispatch | Phase 12–13 |

NOTIFY schema 3, SPEAK và COMMIT_MAP_ARTIFACT tiếp tục bị chặn theo dependency riêng. WAIT/DURATION không được BE tự tạo thành công bằng đồng hồ. Timeout step Pi/CANCEL_STEP và staff reconcile đầy đủ thuộc Phase 8; yêu cầu hủy/giới hạn thời gian Job hiện giữ recovery nếu đã có Pi work, không tự điều khiển hành động kế tiếp. API reconcile Job cũ bị chặn cho Job BE đã có command MQTT; một ghi chú staff không thay được xác nhận dừng/đối soát operation. Phase 8 cần API phục hồi phù hợp và điều chỉnh outbox để lưu CANCEL_STEP riêng bên cạnh EXECUTE_STEP của cùng visit.

## Cấu hình và chạy kiểm thử

### Cấu hình ứng dụng

Trong [application.properties](../src/main/resources/application.properties), mặc định:

| Environment | Mặc định / ý nghĩa |
|---|---|
| MQTT_ENABLED | false; bật client |
| MQTT_DISPATCH_ENABLED | false; bật dispatch sau cấu hình/enrollment |
| MQTT_BROKER_URI | ssl://localhost:8883 |
| MQTT_CLIENT_ID | mar-be-mqtt; cần riêng cho instance |
| MQTT_USERNAME / MQTT_PASSWORD | Rỗng; cấp từ deployment |
| MQTT_ROBOT_IDS | Rỗng; UUID robot đã đăng ký, danh sách phân cách dấu phẩy |
| MQTT_ENABLED_STEP_TYPES | Rỗng; WAIT,MOVE,CONTROL_HARDWARE,EXECUTE_SENSING trong subset trên |
| MQTT_ALLOW_SIMULATION | false; chỉ bật cho test/simulation |
| MQTT_COMMAND_TTL_SEC | 30; khoảng 1–300 giây |
| MQTT_RETRY_INTERVAL_SEC | 3; khoảng 1–10 giây |

Trust/key store tùy chọn bind trực tiếp bằng Spring configuration `app.mqtt.trust-store`, `app.mqtt.trust-store-password`, `app.mqtt.key-store`, `app.mqtt.key-store-password`; env relaxed binding tương ứng `APP_MQTT_TRUSTSTORE`, `APP_MQTT_TRUSTSTOREPASSWORD`, `APP_MQTT_KEYSTORE`, `APP_MQTT_KEYSTOREPASSWORD`. Khi không cấu hình store, dùng trust mặc định của JVM. Có thể dùng file YAML deployment chỉ rõ path PKCS12.

Worker MQTT cần `app.operations.scheduler-enabled=true` (mặc định ứng dụng); test tắt scheduler chung và gọi scheduler/orchestrator chủ động. Broker cần account riêng cho Pi và ACL như [script local](../src/test/resources/mqtt/run-local-broker.sh), thay credential test bằng cấu hình thực tế khi triển khai.

### Broker local cho test TLS

[prepare-local-broker.sh](../src/test/resources/mqtt/prepare-local-broker.sh) dành cho WSL Ubuntu-22.04, tải package Ubuntu rồi giải nén vào `target/mqtt-broker`; không cài service toàn hệ thống. Lần kiểm này dùng Mosquitto 2.0.11. Script tạo CA/cert localhost dùng 2 ngày; nếu tạo lại CA phải import lại trust store.

```powershell
wsl -d Ubuntu-22.04 -- sh /mnt/d/DoAn/BE_MAR/src/test/resources/mqtt/prepare-local-broker.sh /mnt/d/DoAn/BE_MAR/target/mqtt-broker
$taskTrust = Join-Path (Resolve-Path target/mqtt-broker) ("trust-" + [guid]::NewGuid() + ".p12")
keytool -importcert -noprompt -alias mar-test-ca -file target/mqtt-broker/ca.pem -keystore $taskTrust -storetype PKCS12 -storepass test-trust
$env:MQTT_TEST_BROKER_SCRIPT = '/mnt/d/DoAn/BE_MAR/src/test/resources/mqtt/run-local-broker.sh'
$env:MQTT_TEST_BROKER_HOME = '/mnt/d/DoAn/BE_MAR/target/mqtt-broker'
$env:MQTT_TEST_TRUSTSTORE = $taskTrust
```

Thiết lập TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD cho **DB MySQL test riêng**, Java 21 và Maven. Test integration dọn dữ liệu; không dùng DB ứng dụng. Test tự chọn port, khởi chạy broker local, hoàn tất luồng và dừng đúng process broker đã tạo trong finally.

```powershell
mvn test "-Dtest=MqttWireContractTest,MqttRuntimeIntegrationTest,MqttTlsBrokerIntegrationTest"
```

Không có biến MQTT_TEST_BROKER_SCRIPT thì test TLS skip, không được tính là nghiệm thu broker. [MqttTlsBrokerIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttTlsBrokerIntegrationTest.java) hiện dùng WSL Ubuntu-22.04/localhost, không phải harness portable cho mọi OS.

## Kiểm thử và kết quả bàn giao

- [MqttWireContractTest](../src/test/java/com/mediassistrover/backend/MqttWireContractTest.java): fixture wire positive/negative qua validator Java, parser strict và hash replay.
- [MqttRuntimeIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttRuntimeIntegrationTest.java): transaction MySQL, graph, action output, session, immutable resend, conflict/duplicate đồng thời, error trước START, replay journal, restart, expiry và robot allocation.
- [MqttTlsBrokerIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttTlsBrokerIntegrationTest.java): Paho/Mosquitto MQTT 5 qua TLS/password/ACL, chuỗi ACK thật, WAIT rồi server step, phiên sống hơn 15 giây và chặn publish chéo robot.
- Tái chạy toàn bộ test Java để kiểm schema 2, request/delivery, migration V19–V21 và orchestration/UI hiện có. Output build dùng thư mục riêng tránh IDE ghi đè class.

**Kết quả (Java 21, MySQL 9.5, broker Mosquitto local):**

- 78 test MQTT đạt: 25 runtime/transaction, 52 wire/fixture/parser/hash, 1 broker MQTT 5/TLS/password/ACL. Broker test thực sự chạy, không skip; simulator chờ ACK, chạy WAIT đủ 2 giây, rồi BE tự chạy SET_CONTEXT và kết thúc Job. Phiên tiếp tục hơn 15 giây.
- Lượt acceptance liên quan đạt **112/112**, gồm MQTT, 18 orchestration/UI, request và authorization regression. Log: `target/phase567-acceptance-tests.log`.
- Lượt toàn suite trước guard assignment/reconcile cuối: **296 test: 294 đạt, 1 fail baseline analytics, 1 skip Docker**. Log: `target/phase567-final-full-tests.log`. Sau guard và thêm regression tương ứng, đã kiểm lại các nhóm ảnh hưởng trong 112 test trên; không ghi toàn suite đã xanh.
- Lỗi cũ: `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`; MySQL 9.5 trả `possible_keys=null` thay vì `IX_Job_queued_at`. Cùng lỗi đã tái hiện trên DB V18 ở lượt Phase 1–2; không đổi analytics/index/test để che lỗi. Test skip: `Phase19MigrationTest`, Docker daemon chưa chạy; V19–V21 đã chạy trên MySQL local 9.5, chưa kiểm MySQL 8.4 qua Docker.
- Sau kiểm tra acceptance, bổ sung ca config/context đổi sau dispatch và tắt dispatch chặn resend; kiểm lại nhóm runtime trong `target/phase567-snapshot-regression-tests.log`.
- Contract offline đạt 9 schema/129 fixture/11 StepType; 8 schema đóng gói khớp bản tài liệu khi bỏ root comment. Link Markdown/UTF-8 và whitespace đã kiểm.

Build dùng POM tạm với output `target/phase567-verification` để tránh IDE ghi đè `target/classes`; POM tạm được bỏ sau kiểm tra. `pom.xml` thật giữ cấu hình build và thêm Paho. Log/report/broker binary/cert trong target là artifact local bị git ignore, người khác cần tạo lại để kiểm chứng.

Năm tài liệu nguồn không sửa; V21 chỉ chạy trên DB test `be_mar_phase567_test_20261012`, chưa triển khai lên DB ứng dụng. Chưa có nghiệm thu robot thật hoặc code Pi/UI/AI ngoài workspace.

**Phase tiếp theo:** Phase 8 cần CANCEL_STEP outbox, timeout từng Pi step, HELLO/journal reconciliation đầy đủ và API staff recovery trước nghiệm thu thao tác vật lý trên robot. Các phase 9–14 giữ trạng thái chưa triển khai.

**Cập nhật tiếp nối:** [Phase 8–9](phase8-9-implementation.md) đã thêm CANCEL_STEP, timeout, recovery theo operation và NOTIFY/WAIT ACK riêng. Các gate/chưa triển khai phía trên ghi thời điểm Phase 5–7; hiện NOTIFY schema 3 đã mở theo readiness, recovery dùng endpoint operation mới. UNLOCK, mapping/AI/audio và robot thật vẫn chờ phase tương ứng.
