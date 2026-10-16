# Phase 13 — AI, monitoring và các workflow còn lại

Ngày triển khai: 13/10/2026; đối chiếu kế hoạch/contract baseline 12/10/2026. **Phần BE đã có code và kiểm thử; producer/model AI, Pi, UI/audio và robot thật chưa nghiệm thu.** Protocol `1.0`, contract `MAR-STEP-2`, step schema `3`, graph `3`. Không thêm StepType hoặc FALL_DETECTION vào MQTT. Schema Pi vẫn `RECONSTRUCTED_UNVERIFIED_PI`.

## AI sensing và cấu hình BE

[V28](../src/main/resources/db/migration/V28__ai_observation_and_ingestion_receipts.sql) thêm AiSensingProfile, AiObservation, MonitoringIngestionReceipt và snapshot profile trong RobotCommandOutbox. [AiSensingService](../src/main/java/com/mediassistrover/backend/ai/AiSensingService.java) nối CROWD_DENSITY/RESTRICTED_ZONE vào runtime MQTT hiện có, không chạy inference tại BE.

Dispatch AI qua MQTT mặc định tắt: `AI_SENSING_ENABLED=false`. Muốn mở cần MQTT/dispatch/enrollment, EXECUTE_SENSING trong enabledStepTypes, HELLO/heartbeat hợp lệ và profile robot/zone đã được admin cấu hình. REST ingestion hiện có tiếp tục nhận producer có Device auth; vị trí người cần commissioning riêng, không được bật chỉ bởi flag MQTT. Admin cấu hình:

```http
PUT /api/v1/admin/monitoring/robots/{robotId}/ai-profile
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "zoneId": "UUID_ZONE",
  "sensingType": "CROWD_DENSITY",
  "modelVersion": "MODEL_VERSION_DA_NGHIEM_THU",
  "producerRef": "PRODUCER_REFERENCE",
  "enabled": true,
  "personPositionsEnabled": false,
  "commissioningNote": "Bang chung/ngay phien ban producer da kiem"
}
```

SensingType chỉ CROWD_DENSITY hoặc RESTRICTED_ZONE. Zone/map phải active, cùng map của robot và asset hash robot khớp catalog; RESTRICTED_ZONE yêu cầu zone RESTRICTED. Model 1–100 ký tự, producer 1–255, commissioningNote bắt buộc; lưu admin và thời điểm cấu hình. Đây là quyết định commissioning tại BE, không tự chứng minh producer đã triển khai.

Khi dispatch, BE đóng băng map UUID/hash/frame, zone, model và producer trong outbox. Metadata này **không thêm vào Pi params**: wire tiếp tục chỉ sensing_type, zone_id và window_sec. Lần publish đầu kiểm lại profile/catalog; cấu hình thay đổi không sửa body operation đã tạo. Kết quả sau publish đối chiếu model của snapshot cũ, không tự đổi sang model mới. Đổi map/frame khi operation đang chạy bị từ chối ghi observation vào catalog mới.

Validator kiểm map/zone/type/model, count integer không âm trong phạm vi DB, intrusion_detected boolean, captured_at có timezone và tuổi <=2s tại thời điểm Pi sản xuất result. `window_sec>0` yêu cầu observation không trước STEP_STARTED; đây là cửa sổ chờ mẫu mới, không phải trung bình số người. Profile pin model dù Job không có model_version. Frame/producer_ref nếu producer gửi thêm phải khớp snapshot. Evidence chỉ là reference tối đa 4000 ký tự, không nhận data URI.

## Persist và chống trùng observation

Completion hợp lệ lưu JobStep result, monitoring, AiObservation, JobEvent và wakeup trong cùng transaction; EVENT_ACK phát sau commit theo inbox/outbox hiện có. Kết quả sai rollback completion/monitoring và đi cơ chế rejection/recovery hiện có.

Identity observation được tính từ robot, sensing type, map, zone, model, producer và captured_at chuẩn hóa về Instant. Cùng identity/nội dung tái dùng bản ghi; cùng identity khác metrics/output là conflict. Hai visit dùng lại mẫu cache hợp lệ không tăng số bản ghi monitoring. Command/operationKey của mỗi visit vẫn riêng.

- CROWD_DENSITY ghi CrowdDensityRecord với personCount=metrics.count, zone, JobStep, model, thời điểm và metrics nguyên bản. Không tự gán confidence, densityLevel hoặc count/m² khi producer chưa có contract/calibration cho các giá trị này.
- RESTRICTED_ZONE luôn ghi AiObservation. intrusion_detected=true tạo DetectionEvent/RESTRICTED_INTRUSION với locationBasis=UNKNOWN, không bịa tọa độ. false là observation hoàn tất hợp lệ và không tạo positive detection.
- Không tự tạo Incident hoặc kết luận nghiệp vụ từ observation AI. Graph/staff review quyết định phản ứng; producer fall/SOS dùng REST tương ứng.

Observation identity chưa có observation_id chính thức từ Pi nên dùng tuple trên. Producer phải có timestamp đủ phân giải và producerRef ổn định/phân biệt; hai kết quả khác nhau cùng tuple bị coi là lỗi. Nếu producer cần identity khác, phải version hóa contract thay vì bỏ chống trùng.

## REST fall/SOS và quyền review

REST hiện có giữ nguyên:

- `POST /api/v1/pi/monitoring/detections` nhận DETECTION_EVENT, gồm SUSPECTED_FALL/SOS_SIGNAL/OTHER theo DTO hiện có. HTTP Device Bearer, không dùng mật khẩu MQTT.
- `POST /api/v1/pi/monitoring/crowd-density` nhận CROWD_DENSITY_RECORD.
- API emergency SOS hiện có tạo Incident/Alert và request phản ứng.

[MonitoringServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/MonitoringServiceImpl.java) kiểm device token version/thời hạn, scope robot/map/zone và Job/JobStep nếu envelope khai báo. Receipt REST lưu hash toàn envelope và scope; trùng messageId nhưng đổi robot/type/body bị conflict, không ACK lại như bản cũ. Bản ghi lịch sử trước migration chưa có receipt được đối chiếu toàn bộ business payload/robot rồi mới cấp receipt cho retry đúng; không thay nội dung lịch sử. Timestamp DB có sai số tối đa 1µs khi so retry legacy.

REST có thể lưu observation lịch sử, nhưng không được dùng để hoàn tất một step đang chờ mẫu fresh. Timestamp thiếu hoặc vượt thời điểm producer/BE quá 5s bị chặn. Hình dạng/vùng/model/evidence được kiểm; dữ liệu lịch sử không tự đánh thức workflow. Receipt REST chống trùng messageId; producer cần giữ messageId/body khi retry. MQTT AiObservation bổ sung chống trùng theo observation tuple qua nhiều message/visit.

[StaffIncidentReviewPolicy](../src/main/java/com/mediassistrover/backend/service/StaffIncidentReviewPolicy.java) yêu cầu CREATE_FALL_INCIDENT của Job BE schema 3 có INTERACT đã SUCCEEDED, confirmed=true, source=STAFF_MANUAL, đúng detection_id và receipt callback có actor Staff/Admin. Không thể bỏ qua review bằng một server step/context tự đặt confirmed=true. Handler vẫn giữ tính idempotent theo DetectionEvent và scope robot; legacy schema 2 giữ contract cũ. Incident vẫn mang loại SUSPECTED_FALL, không tự kết luận y tế.

[V29](../src/main/resources/db/migration/V29__schema3_ai_and_staff_review_drafts.sql) chỉ sửa draft do migration sở hữu: STAFF_REVIEW của fall dùng CONFIRM và quyền staff; SOS REMOTE_REVIEW/CONFIRM_DISPATCH có actor/required_role STAFF; sensing AI dùng window_sec=3. Historical/schema 2, published và authored draft giữ nguyên.

[EmergencyServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/EmergencyServiceImpl.java) giữ Incident/Alert khi robot bận hoặc workflow chưa đủ readiness/transport; request ở SUBMITTED với dispatch_pending=true. Không lấy robot khỏi Job đang chạy. SOS cùng operation key nhưng payload đổi bị conflict. Khi staff đánh dấu false alarm, Job BE đang chạy nhận cancel intent/wakeup; action Pi đi qua CANCEL_STEP/stop evidence trước terminal. Schema 2 giữ hành vi cũ. Request pending chưa được scheduler mới tự dispatch; staff tiếp tục theo dõi/điều phối qua flow hiện có.

## Heatmap từ vị trí người có nguồn

[PeopleHeatmapService](../src/main/java/com/mediassistrover/backend/ai/PeopleHeatmapService.java) tính grid tại BE, không nhận một grid count/range rồi coi đó là phân bố người. API createHeatmap hiện có hỗ trợ PERSON_POSITIONS, giữ tên CROWD_DENSITY như alias nhưng nội dung là counts của vị trí track, không phải người/m². Những heatmap loại khác cần contract nguồn riêng và hiện bị chặn tạo mới. Heatmap cũ vẫn đọc được.

Producer vị trí phải được commissioning **riêng** qua personPositionsEnabled=true của profile CROWD_DENSITY. BE đề xuất contract REST extension `BE-PERSON-POSITION-1`, chưa xác nhận producer ngoài repo:

```json
{
  "mapId": "UUID_MAP",
  "zoneId": "UUID_ZONE",
  "eventType": "OTHER",
  "locationBasis": "OBJECT",
  "xM": 2.0,
  "yM": 1.0,
  "modelVersion": "MODEL_VERSION",
  "detectedAt": "2026-10-13T00:00:00Z",
  "eventPayload": {
    "contract": "BE-PERSON-POSITION-1",
    "producer_ref": "PRODUCER_REFERENCE",
    "frame_id": "MAP_FRAME",
    "track_id": "TRACK_ID"
  }
}
```

Payload này nằm trong envelope DETECTION_EVENT của REST; không phải sensing_type MQTT mới. BE kiểm contract, profile cho phép vị trí người, robot/map/zone/model/frame/source, OBJECT và đủ tọa độ; receipt đóng băng provenance lúc ingestion. Vị trí robot, SUSPECTED_FALL đơn lẻ, count hoặc range không đủ điều kiện thay thế producer vị trí người. Track ID phải phân biệt các người trong kỳ, ổn định theo producer/model; producer reset/reuse track cần producerRef/model hoặc contract session mới.

GridData đầu vào tạo heatmap chỉ nhận:

```json
{"frame_id":"MAP_FRAME","source_detection_ids":["UUID_DETECTION_DA_LUU"]}
```

BE kiểm từng source đã có receipt/provenance, cùng map/hash/frame và trong `[periodStart,periodEnd)`. Kiểm nguồn trùng ID, tọa độ ngoài map, track xung đột cùng timestamp, sourceRecordCount và maxValue client so với kết quả BE. BE chuyển tọa độ theo origin/yaw Map rồi chia cell; tối đa 10.000 source và 250.000 cells.

Policy `LATEST_POSITION_PER_TRACK_PER_PERIOD`: một track chỉ tính một lần ở vị trí mới nhất trong kỳ. Đây là grid đếm track theo vị trí cuối, không phải thời gian cư trú, mật độ trung bình hoặc độ đông tức thời. Grid output `BE-PERSON-HEATMAP-1` lưu counts, kích thước, origin/frame/hash, source IDs, unique_tracks và sampling_policy. Không chạy thuật toán tracking/calibration tại BE. Producer/camera/tracking thật vẫn cần nghiệm thu trước khi sử dụng dữ liệu vận hành.

## Workflow và trạng thái tích hợp

| Chức năng | BE trong repo | Pi/UI/AI còn cần | Bằng chứng hiện có |
|---|---|---|---|
| Crowd/patrol | Outbox một action, profile AI, persist/dedup, graph count/loop và gate AI | Producer count thật, map/zone/camera, navigation/checkpoint | MQTT mock/DB; gate AI tắt và hồi quy graph/navigation |
| Restricted zone | Profile restricted, persist observation/detection, graph condition/notification | Intrusion producer thật, ROI/map/frame, UI | Result true/false và validator trong DB test |
| Fall response | REST detection + DB confidence, staff receipt trước Incident, schema 3 CONFIRM | Fall model/producer thật, staff/UI review | Callback true tạo incident; false không tạo; source từ DB |
| SOS | REST Incident/Alert, pending khi robot bận/readiness thiếu, false alarm qua cancel | UI/producer SOS, nhận Alert và vận hành | REST state-machine/idempotency/busy; BE cancel intent/outbox |
| Information | Query article public/approved/effective và ANSWER đưa answer/source/found vào data UI | UI hiển thị và callback UI_DISPLAYED | Query/article tests và full graph query → UI → terminal |
| SPEAK | Publish/callback tiếp tục chặn khi chưa có audio completion client | Audio client thực thi và xác nhận thật | Readiness gate/callback validator; chưa nghiệm thu audio |
| Startup | HEALTH → CHECK sensors_ready → READY/recovery | Pi health thực, commissioning cảm biến | Health SUCCEEDED nhưng false vào recovery; true đi terminal |
| Escort | Snapshot patient_reference từ DB, CONFIRM → DECIDE → một MOVE mode ESCORT | Pi navigation và UI; chưa có person following | Graph/command/profile test; không chứng minh theo người |
| Charging | NOTIFY → WAIT staff event → HEALTH → thông báo sạc thủ công | Staff/Pi kiểm pin/điện/sạc thật | Graph chờ đúng event; automatic_charging=false |
| Navigation recovery | WAIT PATH_CLEARED → MOVE operation mới, profile gate và runtime recovery | Staff xác nhận an toàn/đường, Pi Nav2 | Graph WAIT/correlation/selected MOVE và hồi quy cancel/group |
| Schedule | Tạo runtime schema 3/wakeup, một Job mỗi schedule/fire/item | Robot/producer/capability cho workflow schedule | Factory idempotency và các schedule tests hiện có |
| Mapping teleop UI | Không bật trong MAR-STEP-2 | Giao thức/Pi/UI riêng cho remote velocity | Không thêm velocity/launch argv vào command; mapping hồi quy |

V29 bổ sung answer/source/found cho ANSWER của information và MOVE mode ESCORT trong draft sở hữu bởi migration. [RequestJobInputSnapshotFactory](../src/main/java/com/mediassistrover/backend/service/impl/RequestJobInputSnapshotFactory.java) chép patient_reference từ EscortRequest; client data không thay thông tin này. Group escort/patrol khác không được tự viết graph chọn group; các gate Phase 11 tiếp tục chặn MOVE group thiếu SELECT_TARGET. Đến endpoint sạc không chứng minh đã sạc, MODE ESCORT không chứng minh robot đang theo người.

## Kiểm thử và phần chưa xác nhận

- [AiMonitoringIntegrationTest](../src/test/java/com/mediassistrover/backend/AiMonitoringIntegrationTest.java): dispatch/profile snapshot, result persistence/rollback, dedup/conflict, model/map/zone/frame/producer/freshness/evidence, REST receipts/history/scope, heatmap provenance/tracks và quyền API.
- [Phase13WorkflowOrchestrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase13WorkflowOrchestrationIntegrationTest.java): startup, sạc thủ công, staff review fall, false alarm, information/UI, escort, recovery, schedule và audio/AI gates qua factory/orchestrator.
- [Phase28AiMigrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase28AiMigrationIntegrationTest.java): V27 → V29 trên schema UUID tạm; giữ schema 2/authored/mapping, thêm bảng/snapshot, cập nhật draft đúng phạm vi và validate checksum. Chỉ chạy với TEST_DB_URL local prefix be_mar_phase13_.

Chạy Java 21/MySQL local **9.5**, DB test riêng `be_mar_phase13_regression_20261013`; không chạy migration hoặc truncate trên DB ứng dụng.

- Nhóm kiểm cuối **50/50 đạt**, log `target/phase13-final-verification.log`: 30 AI/monitoring, 12 workflow orchestration, 3 REST monitoring, 4 SOS/delivery legacy và 1 migration. Bao gồm guard map/profile trước publish, track_id đúng kiểu và provenance đã đóng băng trước khi đổi cấu hình producer.
- Nhóm hồi quy rộng trước các chỉnh guard cuối **226/226 đạt**, log `target/phase13-final-targeted-tests.log`; gồm mapping, MQTT runtime/cancel/group, resolver, staff authorization, contracts và catalog. Hai nhóm có overlap, không cộng thành số test độc lập.
- Lượt toàn suite trước chốt cuối có **464 test: 457 đạt, 1 fail, 6 skip**, log `target/phase13-full-tests.log`. Failure duy nhất là `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner`: MySQL 9.5 trả possible_keys=null thay vì IX_Job_queued_at. Đây là lỗi đã ghi/đối chứng ở [Phase 8–9](phase8-9-implementation.md) và [Phase 11](phase11-implementation.md), không sửa analytics trong phase này. Các chỉnh cuối ở source position/track/provenance được kiểm lại bằng nhóm 50 test trên.
- Sáu skip: 2 MQTT TLS broker test thiếu biến môi trường broker local của lượt này; 1 Phase19MigrationTest thiếu Docker; 3 migration tests Phase23/24/26 chỉ cho phép DB prefix phase10/11/12. Migration V27 → V29 của Phase 13 chạy trên schema UUID tạm và đạt. Không nghiệm thu lại broker/robot ở lượt này.
- Contract offline **9 schema/131 fixture đạt** (88 positive, 43 negative), giữ 11 StepType. UTF-8, liên kết và diff đã kiểm; năm tài liệu nguồn giữ nguyên. Cleanup test Phase7 được giới hạn schema 2 để không xóa compiled draft schema 3 của các test khác.

Broker/Pi/model/camera weights, inference/tracking, calibration, audio/UI và robot thật chưa được đọc/kiểm trong workspace này; test DB/transport mock không thay nghiệm thu các dependency đó. Phase 14 chưa chuyển transport sản xuất hoặc publish workflow. Code được lưu trong workspace; chưa tạo commit hoặc deploy.
