# Bàn giao Phase 10: quyền mở theo operation và mốc delivery

Ngày **12/10/2026**. Tiếp nối [Phase 8–9](phase8-9-implementation.md) và [kế hoạch tổng](04_Ke_hoach_BE_MQTT_theo_phase.md).

**Trạng thái:** đã bổ sung phần BE về binding quyền mở, callback và mốc delivery. **Chưa nghiệm thu toàn Phase 10**, vì contract/code Pi hiện chưa hỗ trợ mở bằng quyền staff. UNLOCK chính thức vẫn bị chặn; workflow delivery schema 3 tiếp tục ở DRAFT. Không sửa năm tài liệu nguồn, không bật MQTT hoặc publish workflow tự động.

## Thay đổi BE

[V23](../src/main/resources/db/migration/V23__delivery_operation_authorization.sql) bổ sung:

- `RobotOperationAuthorization`: mỗi verification chỉ gắn một JobStep UNLOCK, một robot và một operationKey. Lưu snapshot authorization và thời hạn; unique constraint chặn dùng lại verification cho visit khác.
- `AuthVerification.actor_token_version`: quyền mới ghi phiên account đã được xác thực. Thu hồi phiên trước dispatch làm quyền không còn sử dụng được. Verification cũ không có giá trị này không tự được nâng cấp thành quyền mở schema 3.
- `DeliveryRequest.pickup_payload_confirmed_at` và `delivery_payload_confirmed_at`: mốc xác nhận hàng, độc lập với `picked_up_at`/`delivered_at` và các mốc xác nhận ngăn hàng.
- Chỉ cập nhật mapping UNLOCK của bản DRAFT schema 3 do migration tạo, lấy năm field authorization từ AUTH_SENDER/AUTH_RECEIVER tương ứng. Xóa compilation/hash của các draft này để initializer compile lại; giữ schema 2, workflow đã publish và draft tự soạn.

[OperationAuthorizationService](../src/main/java/com/mediassistrover/backend/service/OperationAuthorizationService.java) được nối vào [resolver](../src/main/java/com/mediassistrover/backend/service/impl/JobStepInputResolverImpl.java) trước khi đóng băng input và vào [dispatch](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java) trước tạo command:

- Job phải RUNNING, schema 3, được BE điều phối; step phải là UNLOCK cố định, PENDING và đang được chọn. Phải có robot và DeliveryRequest đúng Job.
- Verification phải thuộc Job, AUTHENTICATE đã SUCCEEDED trên visit được chọn trước đó, purpose/permission đúng, còn hạn, chưa thu hồi; account phải còn hoạt động, phiên chưa bị thu hồi.
- Purpose của UNLOCK được cố định trong cấu hình BE. Receiver không được mở trước mốc pickup; không mở lại stage đã hoàn tất.
- Verification cũ bị chặn khi có authentication visit mới hơn. Actor/purpose/expiry trong input phải khớp bản ghi BE; không dùng authorization từ client làm chứng cứ.
- Lưu snapshot năm field đã có trong tài liệu: `mode`, `verification_id`, `actor_account_id`, `purpose`, `valid_until`. Ràng buộc robot/JobStep/operation nằm trong bản ghi BE và envelope operation; chưa tự thêm field vào wire Pi.
- Retry không đổi quyền hoặc gia hạn expiry. Một verification không cấp quyền cho UNLOCK visit khác. Đổi robot hoặc operationKey khiến binding không còn hợp lệ.

[Input validator](../src/main/java/com/mediassistrover/backend/validation/StepInputContractValidator.java) chặn template UNLOCK schema 3 thiếu authorization, purpose động hoặc action bị mapping ghi đè. Input có quyền chưa được binding vẫn bị scope validator từ chối. Thời gian verification/QR được lưu ở độ chính xác microsecond để snapshot không đổi khi đọc lại từ MySQL.

[Staff verification](../src/main/java/com/mediassistrover/backend/service/impl/AuthVerificationServiceImpl.java) kiểm thêm token version, method được cấu hình và thời hạn 30–900 giây. [QR](../src/main/java/com/mediassistrover/backend/service/impl/QrVerificationServiceImpl.java) kiểm phiên active, method QR, selected step, cancel/deadline ở cả issue và verify; thời hạn 30–300 giây. Completion vẫn lưu kết quả và yêu cầu điều phối trong cùng transaction.

## Cargo, cửa và mốc hoàn tất

[DeliveryHandoverService](../src/main/java/com/mediassistrover/backend/service/DeliveryHandoverService.java) được gọi từ [callback](../src/main/java/com/mediassistrover/backend/service/JobStepResponseService.java) trong cùng transaction với completion, receipt và orchestration work. Job lock của callback được giữ trước DeliveryRequest lock.

| Bước | Chứng cứ và kết quả nghiệp vụ |
|---|---|
| AUTHENTICATE | Xác thực staff/QR tại BE; chưa phải quyền thực thi UNLOCK trên Pi |
| UNLOCK | Cần binding đúng robot/operation, kết quả đúng compartment/action, command đã publish và dừng được xác nhận; `COMMISSIONING_ONLY` không đủ quyền delivery |
| PAYLOAD_CONFIRMED / LOAD | Danh sách hàng phải khớp snapshot BE. Khi `confirmed=true`, lưu sender và `pickupPayloadConfirmedAt`; chưa ghi pickedUpAt |
| DOOR_CLOSED | Xác nhận riêng của staff; kết quả phủ định được giữ cho nhánh workflow |
| LOCK | Cần visit thực sự được chọn sau xác nhận đóng cửa, kết quả command phù hợp và `physicalStopConfirmed=true` |
| LOCK_CHECKED | Chỉ sau cargo, đóng cửa và LOCK tương ứng. `locked=true` mới ghi pickedUpAt/pickupCompartmentConfirmedAt; `locked=false` không ghi mốc |
| UNLOAD → đóng → LOCK → kiểm | Tương tự receiver; chỉ sau pickup. Ghi deliveryPayloadConfirmedAt trước, deliveredAt/deliveryCompartmentConfirmedAt sau kiểm khóa dương tính |

Chỉ dùng các visit đã được graph chọn, theo executionSequenceNo trong cùng stage authorization. Không đọc mọi PENDING hoặc lấy chứng cứ từ Job/robot khác. Mỗi cargo visit chỉ hoàn tất một lần; graph có thể chọn visit mới để thử lại sau câu trả lời phủ định. Kiểm cửa/khóa dùng cargo visit mới nhất của stage. Callback cùng Idempotency-Key/actor/body trả receipt đã lưu. Actor staff phải là người đã xác thực stage; ADMIN vẫn được xử lý theo quyền quản trị hiện có.

Kết quả phủ định hợp lệ vẫn là output để transition/CHECK_CONDITION chọn nhánh đã cấu hình. Staff dùng danh sách hàng chuẩn với `confirmed=false` và note để báo thiếu hàng; không thay identity/danh sách chuẩn bằng dữ liệu UI. Xác nhận phủ định không ghi mốc hàng hoặc hoàn tất tương ứng.

`controller_ack=false` và `verified=false` của Pi được giữ nguyên. Mốc compartment của delivery thể hiện staff đã kiểm thủ công sau lệnh LOCK, không chứng minh sensor hoặc controller đã xác nhận khóa.

[Delivery REST cũ](../src/main/java/com/mediassistrover/backend/service/impl/DeliveryServiceImpl.java) tiếp tục hành vi schema 2. Request có Job schema 3 không dùng confirm-pickup/confirm-delivery để gộp cargo, cửa và khóa hoặc bỏ qua JobStep; dùng endpoint responses theo từng bước. Delivery GET trả thêm hai mốc payload.

## Phần phối hợp ngoài repo còn thiếu

Contract hiện tại giữ protocol **1.0**, **MAR-STEP-2**, step schema BE **3**, graph **3**:

- HELLO chỉ nhận `unlockPolicy=DISABLED|COMMISSIONING_ONLY`.
- Hardware output chỉ nhận `authorization_mode=DEVICE_COMMAND|COMMISSIONING_ONLY`; profile servo hiện có vẫn trả `controller_ack=false`, `verified=false`.
- Authorization STAFF_VERIFIED trong input là shape dành sẵn; tài liệu nói rõ Pi chưa kiểm/tiêu thụ quyền này.

Vì vậy **không thêm enum/capability mới vào schema và không cho cấu hình bypass UNLOCK** trong lần này. Checklist để mở gate sau khi có code Pi và contract được xác nhận:

1. Pi xác thực nguồn command từ broker/TLS/ACL; kiểm authorization với robot, Job, JobStep, operationKey và purpose của envelope, cùng thời hạn.
2. Pi persist binding/journal trước tác động; dùng lại operation trả journal, dùng verification với operation khác phải từ chối; restart không làm mất chống replay.
3. Chốt phiên bản/capability HELLO và output thể hiện thực thi STAFF_VERIFIED; kiểm lỗi thiếu/sai quyền, hết hạn, replay, session và stop evidence. Không mặc định bổ sung enum vào MAR-STEP-2.
4. Nối gate BE với capability đã nghiệm thu và kiểm lại binding/expiry ở publisher/resend. Expiry command không vượt quyền; rollback transport không tự phát lại tác động.
5. UI nhận từng instruction và gọi callback cargo/door/check riêng. Nghiệm thu delivery qua broker và robot thật theo cả sender/receiver, negative branch, mất mạng/cancel/recovery.

Broker/credential, robot/map/endpoint thật và workspace Pi/UI chưa được cung cấp để xác minh. Test BE không thay các bước nghiệm thu này.

## Kiểm thử

[DeliveryOperationAuthorizationIntegrationTest](../src/test/java/com/mediassistrover/backend/DeliveryOperationAuthorizationIntegrationTest.java) kiểm binding/snapshot sau MySQL reload, expiry/revocation, actor/purpose, operation/robot, reuse, superseded authentication, phiên bị thu hồi, QR deadline, receiver trước pickup, các mốc sender/receiver, commissioning/missing stop, thiếu LOCK, kết quả phủ định, API cũ không bypass và rollback/concurrent duplicate.

Test mốc sender/receiver **seed trực tiếp chứng cứ STAFF_VERIFIED tương lai vào DB** để kiểm thành phần BE. Test riêng chứng minh output đó vẫn bị validator wire hiện tại từ chối và official UNLOCK vẫn bị chặn. Đây không phải test Pi/MQTT delivery đã tích hợp.

Kết quả trên Java 21 và MySQL local **9.5**, database test riêng `be_mar_phase10_verify_20261012`:

- **101/101** kiểm thử trọng tâm đạt ở lần chạy cuối; trong đó suite mới có **20/20** test. Gồm resolver/input, orchestration, authorization, legacy delivery và schema 3 migration. Log: `target/phase10-final-targeted-tests.log`.
- [Phase23MigrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase23MigrationIntegrationTest.java): **1/1** đạt khi nâng database tạm từ V22 → V23. Kiểm draft đã compile được đánh dấu compile lại, giữ schema 2 và draft do admin tự soạn. Test chỉ chạy với TEST_DB_URL local mang prefix `be_mar_phase10_`, dùng schema UUID riêng và xóa schema đó trong finally; không clean database của caller. Credential test cần quyền CREATE DATABASE.
- Toàn suite trước ba test/chỉnh sửa cuối về retry cargo và expiry: **347 test, 345 đạt, 1 fail, 1 skip**. Các bộ MQTT runtime, cancel/recovery và Alert ACK đạt; **2/2** broker TLS tests đạt. Sau sửa cuối chỉ chạy lại nhóm bị ảnh hưởng, đạt 101/101 như trên. Log toàn suite: `target/phase10-full-tests.log`.
- Failure hiện có: `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`, MySQL 9.5 trả `possible_keys=null` thay vì `IX_Job_queued_at`; đã có và được đối chứng trước V23 tại [bàn giao Phase 8–9](phase8-9-implementation.md). Không sửa analytics/index trong scope này.
- `Phase19MigrationTest` skip vì Docker daemon chưa chạy; chưa xác nhận migration trên MySQL 8.4 bằng Docker. Đây là kiểm thử migration cũ khác với bài upgrade V23 local đã đạt.
- Offline contract checker: **9 schema / 131 fixture / 11 StepType** đạt; giữ nguyên wire Pi. Markdown UTF-8 và local links đã kiểm; `git diff --check` đạt.

Build verification dùng thư mục riêng `target/phase10-verification` để tránh IDE ghi lại target/classes trong lúc test. Các log/report giữ trong target; POM tạm đã được bỏ. Chưa đánh dấu Phase 10 hoàn tất tích hợp.
