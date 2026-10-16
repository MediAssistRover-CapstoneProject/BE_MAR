# Hợp đồng giao tiếp BE – Pi

Ngày chốt: 11/10/2026. Contract `MAR-STEP-2`, protocolVersion `1.0`, mã Pi đối chiếu: runtime `0.7.0` trong bộ MQTT 08/10. Bản tài liệu này bổ sung chi tiết, không thay đổi code Pi hay BE.

**Phạm vi:** BE điều phối workflow, Pi nhận từng bước đã giải tham số. Có 11 StepType trong hệ thống; Pi chạy MOVE, CONTROL_HARDWARE, EXECUTE_SENSING và WAIT/DURATION. Schema wire là hợp đồng đang được code Pi xử lý. Hợp đồng bước chạy tại BE trong tài liệu 02 là yêu cầu triển khai, không có nghĩa BE đã hoàn thiện.

## 1. Quy ước

- JSON UTF-8. Field phân biệt hoa/thường. Ngoài envelope dùng camelCase; params và kết quả từng bước chủ yếu dùng snake_case. Giữ đúng tên trong schema, không đổi đồng loạt.
- UUID là chuỗi UUID có dấu gạch ngang; thời gian ISO 8601 có múi giờ, BE nên gửi UTC hậu tố Z.
- Độ dài m, góc rad, tốc độ m/s, thời gian s. Không truyền tọa độ pixel vào MOVE.
- `B` = bắt buộc; `T` = tùy chọn; `ĐK` = bắt buộc theo trường hợp. Nullable nghĩa field có thể mang null; khác với được bỏ field.
- Chỉ gửi field thuộc hợp đồng. Không nhét toàn bộ Job.inputJson vào params.
- `timeoutSec` hiện là **number**, nhận số thập phân, miền 1–3605; không phải schema integer như một số giải thích cũ. Khi tạo lệnh, BE dùng giây nguyên cho dễ cấu hình.
- Các bản mẫu trong thư mục examples là dữ liệu minh họa, không phải số đo robot. Cần thay UUID/phiên và cấp thời hạn mới khi thực sự gửi.

## 2. Thông tin triển khai cần thống nhất

| Giá trị | Chốt hoặc cần điền | Người quản lý |
|---|---|---|
| Protocol | MQTT 5 trên TLS/TCP; không phải MQTT over WebSocket | Hai bên |
| brokerHost | CẦN ĐIỀN hostname/IP thật; không kèm mqtt:// hay đường dẫn | Người triển khai broker |
| brokerPort | 8883, nếu đổi phải đổi cả hai client | Người triển khai broker |
| CA | CA hệ thống hoặc CA nội bộ công khai; hostname/IP phải khớp chứng chỉ | Người triển khai broker |
| robotId | CẦN ĐIỀN UUID Robot thật trong BE | BE cấp, Pi cấu hình |
| Pi MQTT username | Chính robotId | Broker/Pi |
| Pi MQTT password | Secret riêng của robot; cài vào file cục bộ | Broker/Pi, không đưa vào JSON lệnh |
| Pi clientId | `mar-pi-{robotId}` | Pi tự tạo |
| BE MQTT username/clientId | Tài khoản BE riêng, clientId duy nhất cho instance | Broker/BE |
| topicPrefix | `mar/robots/{robotId}` | Cố định |
| contract | `MAR-STEP-2` | Cố định |
| mapId, endpointId, zoneId | CẦN ĐIỀN UUID thật, đồng bộ với cấu hình/model trên Pi | BE + Pi |
| compartment_id | `CART_MAIN` | Hai bên |
| tốc độ demo | 0.05 m/s, không vượt giới hạn profile Pi | BE/Pi |
| servo | MG996R, RRC Lite S6, ROS ID4, khóa 1500/mở 2400 | Chỉ cấu hình Pi |
| US100 | Trái TRIG17/ECHO18, phải TRIG22/ECHO23 theo BCM | Chỉ cấu hình Pi |

Host/UUID/credential thực tế chưa được xác nhận trong phiên này. File `deployment.values.template.json` để điền các giá trị không bí mật; mật khẩu không gửi trong tài liệu handoff.

### Cấu hình worker Pi liên quan tới BE

Tên sau là field của mqtt.worker.json, không phải body lệnh MQTT:

| Field | Kiểu / giá trị | Ý nghĩa |
|---|---|---|
| transport | string = mqtt5 | Chọn transport |
| robot_id | UUID | Robot trong BE |
| hardware_profile | absolute path | File phần cứng đã hiệu chuẩn |
| state_path | absolute path | SQLite worker hiện có; giữ khi chuyển WSS sang MQTT |
| legacy_state_path | absolute path | Journal runtime cũ, dùng khóa chống chạy đồng thời |
| tls_ca_file | path hoặc null | CA MQTT |
| mqtt.host / port | string / integer | Broker, port 1–65535 |
| mqtt.username / password_file | robot UUID / absolute path | Tài khoản broker của Pi |
| mqtt.be_timeout_sec | number 5–30, chốt 15 | Mất thông điệp BE hợp lệ thì kết thúc phiên |
| heartbeat_sec | number 1–10, chốt 3 | Heartbeat Pi |
| ack_timeout_sec | number 3–60, chốt 15 | Chờ EVENT_ACK |
| connect_timeout_sec | number 3–60, chốt 10 | Kết nối/publish |
| enabled_step_types | string[] | Tập con của MOVE, CONTROL_HARDWARE, EXECUTE_SENSING, WAIT |
| compartments | object | `{ "CART_MAIN": "cargo_lock" }` |
| unlock_policy | DISABLED hoặc COMMISSIONING_ONLY | Hiện chưa có chế độ staff verified hoàn chỉnh |
| ai.enabled / topic / max_age_sec | bool / string / number | Mặc định topic `/mediassist/ai/observations`, tuổi dữ liệu chốt 2 giây |
| mapping.enabled | bool | Bật các action quét map |
| mapping.storage_dir / map_topic | absolute path / string | Lưu file cục bộ, ROS `/map` |
| mapping.max_cells | number, tối đa 4000000 | Giới hạn occupancy grid; cấu hình bằng số nguyên |
| mapping.max_map_age_sec | number 1–30, chốt 10 | Tuổi map tối đa |
| mapping.start_timeout_sec | number 5–120, chốt 45 | Chờ map/TF sẵn sàng |
| mapping.launch_argv | string[] | Lệnh SLAM cục bộ; [] nếu chạy terminal khác. BE không gửi shell command |
| mapping.upload_base_url | HTTPS URL hoặc null | Tiền tố endpoint upload file map |
| mapping.upload_token_file | path hoặc null | Bearer token HTTP, khác mật khẩu MQTT |
| mapping.upload_ca_file | path hoặc null | CA HTTP upload |

Không bật capability/commissioning chỉ để vượt lỗi. MQTT credential xác thực thiết bị; không thay xác thực nhân viên.

## 3. Topic và kết nối

| Topic dưới prefix | Hướng | QoS | Retain | Payload |
|---|---|---|---|---|
| commands | BE → Pi | 1 | false | COMMAND |
| event-acks | BE → Pi | 1 | false | HELLO_ACK / EVENT_ACK / SERVER_HEARTBEAT |
| events | Pi → BE | 1 | false | HELLO / COMMAND_ACK / STEP_STARTED / STEP_RESULT / STEP_ERROR / OPERATOR_RECONCILED / PROTOCOL_ERROR |
| heartbeat | Pi → BE | 0 | false | HEARTBEAT |
| presence | Pi → BE | 1 | true | Object presence riêng |
| mapping/preview | Pi → BE | 0 | false | Object MAP_PREVIEW riêng |

Pi đăng ký đúng hai topic của robot mình. BE đăng ký events/heartbeat/presence/preview của các robot được quản lý. Broker ACL: robot chỉ ghi các topic Pi→BE của mình, chỉ đọc commands/event-acks của mình; BE có quyền ngược lại. Không cho robot tự gửi lệnh đến robot khác.

Pi dùng clean_start=true, SessionExpiryInterval=0, MQTT keepalive 10 giây. BE giữ command trong DB, không giao lệnh cũ khi robot vừa reconnect. Không retained command/ACK. Pi nhận tối đa 1048576 byte; Pi phát JSON tối đa 1000000 byte. QoS 1 có thể giao trùng.

### Bắt tay

1. Pi kết nối và subscribe thành công, phát presence online rồi HELLO.
2. BE kiểm robot/contract/phiên; trả HELLO_ACK trong 10 giây.
3. Sau HELLO_ACK, BE phát SERVER_HEARTBEAT khoảng 3 giây/lần. Pi không nhận dữ liệu BE hợp lệ trong 15 giây thì đóng phiên.
4. BE đọc trạng thái recovery trong HELLO, kiểm map/capability rồi mới gửi step.

Presence online không chứng minh workflow engine BE còn hoạt động. Sau restart BE, không suy ra phiên sẵn sàng chỉ từ retained presence: chờ HELLO mới và đối soát.

## 4. BE → Pi: COMMAND

Schema: `schemas/mqtt-command.schema.json` và `schemas/pi-step-inputs.schema.json`. Schema command có nhúng điều kiện params cho 4 nhóm Pi; cấu hình runtime vẫn kiểm map, capability, giới hạn tốc độ và enabled flag riêng.

| Field | Kiểu | Mức | Nội dung |
|---|---|---|---|
| protocolVersion | string | B | 1.0 |
| contract | string | B | MAR-STEP-2 |
| messageType | string | B | COMMAND |
| commandId | UUID | B | ID lần gửi lệnh nghiệp vụ, BE tạo |
| correlationId | UUID | B | ID đối chiếu request/response, BE tạo |
| robotId | UUID | B | Trùng Robot và topic |
| transportSessionId | UUID | B | Lấy từ HELLO của phiên Pi hiện tại, BE không tự cấp |
| commandType | enum | B | EXECUTE_STEP hoặc CANCEL_STEP |
| expiresAt | datetime | B | Hạn được bắt đầu thực thi, đề xuất now+60s |
| step | object | ĐK | Chỉ EXECUTE_STEP |
| jobId / jobStepId | UUID / UUID | ĐK | Chỉ CANCEL_STEP, ở cấp ngoài |

EXECUTE_STEP không thêm jobId/jobStepId ở ngoài step. CANCEL_STEP không chứa object step.

### Toàn bộ field step

| Field | Kiểu | Mức | Nội dung |
|---|---|---|---|
| jobId | UUID | B | Job đang chạy |
| jobStepId | UUID | B | Một lần thực thi/visit cụ thể |
| stepId | UUID | B | Định nghĩa Step trong workflow |
| stepCode | string 1–2000 | B | PICKUP, TRANSIT, UNLOCK_SENDER… |
| stepType | enum | B | MOVE / CONTROL_HARDWARE / EXECUTE_SENSING / WAIT trên wire này |
| visitNo | integer >=1 | B | Số lần ghé bước trong Job |
| operationKey | string 1–150 | B | Khóa thao tác ổn định, ví dụ `{jobId}:PICKUP:1` |
| timeoutSec | number 1–3605 | B | Hạn thực thi tính từ khi handler bắt đầu |
| params | object | B | Input đã resolve, xem tài liệu 02 |

stepId có thể lặp giữa nhiều Job; jobStepId/operationKey phân biệt thao tác cụ thể. Không dùng commandId như dispatchCommandId cấp Job cũ.

## 5. BE → Pi: ACK và heartbeat

Ba object dưới đây có đầy đủ field; không bọc thêm payload:

```json
{"messageType":"HELLO_ACK","robotId":"<UUID>","transportSessionId":"<UUID_PHIEN_PI>","contract":"MAR-STEP-2","accepted":true}
```

```json
{"messageType":"EVENT_ACK","robotId":"<UUID>","transportSessionId":"<UUID_PHIEN_PI>","messageId":"<UUID_EVENT_PI>","accepted":true}
```

```json
{"messageType":"SERVER_HEARTBEAT","robotId":"<UUID>","transportSessionId":"<UUID_PHIEN_PI>"}
```

Tất cả field trong mỗi object là bắt buộc. accepted là boolean. HELLO_ACK accepted=false bị từ chối phiên. EVENT_ACK accepted=false đối với event đang chờ làm worker lỗi phiên; không dùng nó như tín hiệu “hãy thử lại sau”. Khi DB tạm lỗi, chưa ACK; xử lý lại theo timeout/recovery. ACK sự kiện chỉ sau commit. Schema ở `schemas/mqtt-be-response.schema.json`.

## 6. Pi → BE: envelope chung

Áp dụng events/heartbeat, không áp dụng presence/map preview.

| Field | Kiểu | Mức | Ý nghĩa |
|---|---|---|---|
| protocolVersion | string=1.0 | B | Phiên bản |
| messageId | UUID | B | ID event do Pi tạo, khóa chống trùng |
| operationKey | string | B | Theo step; event hệ thống dùng messageId |
| correlationId | UUID | B | Thường theo command; event hệ thống/recovery có thể tự sinh |
| robotId | UUID | B | Trùng topic |
| jobId | UUID hoặc null | B | Null nếu event không gắn step |
| jobStepId | UUID hoặc null | B | Null nếu event không gắn step |
| messageType | enum | B | Các loại ở bảng topic |
| occurredAt | datetime | B | Pi tạo timestamp |
| producerSessionId | UUID | B | Phiên tiến trình tạo event |
| sequenceNo | integer >=1 | B | Thứ tự tạo event trong producerSessionId, không liên tục trên từng topic |
| transportSessionId | UUID | B | Phiên MQTT đang mang event |
| payload | object | B | Theo bảng dưới |

Khi reconnect, event bền vững phát lại giữ messageId/producerSessionId/sequenceNo nhưng mang transportSessionId mới. Không chỉ dùng sequenceNo làm khóa toàn hệ thống. Kiểm ID trùng nhưng nội dung khác như lỗi giao thức.

### 6.1 HELLO.payload — tất cả bắt buộc

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| contract | MAR-STEP-2 | Hợp đồng |
| runtimeVersion | string, hiện 0.7.0 | Version code, không hardcode mọi bản tương lai vào DTO |
| simulation | bool, runtime thật false | Không chứng minh cảm biến đã sẵn sàng |
| enabledStepTypes | string[] | Các nhóm Pi được bật |
| unlockPolicy | DISABLED / COMMISSIONING_ONLY | Chính sách mở chốt |
| mappingEnabled | bool | Cấu hình mapping |
| capabilities | string[] | Tên capability cục bộ; phân biệt với StepType |
| states | object[] | Các thao tác active/recovery và tối đa 30 bản ghi gần đây; không phải toàn bộ lịch sử |
| mappingSession | object hoặc null | Trạng thái phiên map, xem mục 9 |

Mỗi states[] gồm đủ `jobId:UUID`, `jobStepId:UUID`, `operationKey:string`, `status:string`. Status trong journal: ACCEPTED, RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, RECOVERY_REQUIRED.

### 6.2 HEARTBEAT.payload — tất cả bắt buộc

`status: IDLE|BUSY|ERROR`, `runtimeVersion:string`, `protocolMajor:integer=1`, `capabilities:string[]`, `activeJobStepId:UUID|null`, `currentMapId:UUID|null`, `mapAssetHash:string|null`, `mappingSession:object|null`, `telemetry:object`.

Khi mapping đang mở, currentMapId/mapAssetHash null vì chưa phải map đã được cấp/hiệu chuẩn. IDLE ở worker chỉ nói không có bước/phiên đang chạy và không bị recovery; chưa chứng minh Nav2/cảm biến đã sẵn sàng. BE vẫn giữ robot đang được Job sử dụng khi Pi tạm IDLE để chờ staff, không giao Job thứ hai.

### 6.3 COMMAND_ACK.payload

| Field | Kiểu | Mức |
|---|---|---|
| commandId | UUID | B |
| commandType | EXECUTE_STEP / CANCEL_STEP | B |
| accepted | boolean | B |
| status | string | T: ACCEPTED, trạng thái đã lưu hoặc kết quả hủy |
| errorCode | string | T: lý do từ chối |
| replayed | boolean | T: true khi operation đã tồn tại |
| result | object hoặc null | T: snapshot đã lưu của thao tác trước |
| physicalStopConfirmed | boolean hoặc null | T: chủ yếu ACK hủy |

Snapshot trong `payload.result` của ACK replay có thể chứa `status`, `result` (output step), `errorCode`, `physicalStopConfirmed`, `actuatorPositionVerified`, hoặc `reason/source` sau đối soát. Không đọc nhầm nó như output trực tiếp. accepted=true + replayed=true không có nghĩa chạy lần nữa hoặc SUCCEEDED; phải đọc status.

ACK hủy không tìm thấy step active có thể có jobId/jobStepId=null ở envelope. BE đối chiếu commandId trong payload với lệnh hủy đã lưu.

### 6.4 STEP_STARTED.payload

Đủ 3 field: `attemptNo:integer=1`, `stepType:string`, `resolvedInputs:object` là params Pi đã nhận. Worker này không tự retry handler, attemptNo hiện luôn 1. BE không gửi field attemptNo trong step envelope.

### 6.5 STEP_RESULT / STEP_ERROR.payload

| Field | Kiểu | Mức / ý nghĩa |
|---|---|---|
| status | enum | B: SUCCEEDED / FAILED / TIMED_OUT / CANCELLED / RECOVERY_REQUIRED |
| result | object hoặc null | B ở STEP_RESULT; T ở STEP_ERROR do restart/abandon |
| errorCode | string hoặc null | B; null khi thành công |
| retryable | boolean | B, code hiện trả false |
| details | object hoặc null | B, thường `{ "code": errorCode }` |
| physicalStopConfirmed | boolean hoặc null | T ở STEP_ERROR do restart; B ở kết quả thực thi thông thường |

STEP_RESULT mang SUCCEEDED. Các trạng thái lỗi đi STEP_ERROR. Khi restart trước khi hoàn tất có thể không có result/physicalStopConfirmed, BE phải chấp nhận field vắng và không suy ra đã dừng.

physicalStopConfirmed=true chỉ là điều kiện dừng được adapter xác nhận, không nói cửa khóa/hàng giao xong. Khi false/vắng trong tình huống nguy hiểm: giữ recovery, không giao tiếp bước vật lý tiếp theo. Output từng bước nằm trong payload.result, xem tài liệu 02.

### 6.6 OPERATOR_RECONCILED.payload

Đủ `reason:string`, `status:"CANCELLED"`. Nhật ký local cho biết người vận hành đã đối soát. BE lưu event và quyết định trạng thái Job tương ứng, không tự cho chuyến giao thành công.

### 6.7 PROTOCOL_ERROR.payload

Đủ `commandId` (giá trị commandId nhận được, có thể null/sai định dạng nếu lệnh hỏng), `errorCode:string`. Envelope này không gắn Job/Step. Không phát EVENT_ACK cho loại transient này.

## 7. Telemetry: toàn bộ field đang có

Các mẫu left/right/battery/scan/odom_speed/gate chỉ xuất hiện sau khi nhận được topic. Mỗi mẫu có `value` và `age_sec:number>=0`.

| Field | value | Ghi chú |
|---|---|---|
| left / right | number hoặc null | Mét từ US100; nonfinite được chuyển null |
| battery | integer | Raw UInt16, chưa quy đổi thành % |
| scan | boolean | Có dữ liệu ranges LiDAR, không phải số người |
| odom_speed | number hoặc null | Chỉ số nội bộ max(abs(vx),abs(vy),abs(wz)); pha trộn đơn vị, không trình bày như vận tốc m/s chuẩn |
| gate | string | Nội dung ROS safety_state; hiện là chuỗi, không phải object lồng sẵn |
| map_pose | object riêng | Có cả khi mất TF |

map_pose gồm `value:object|null`, `age_sec:number|null`, `fresh:boolean`. Khi có value: `map_id:UUID|null`, `x_m:number`, `y_m:number`, `yaw_rad:number`; lúc mapping có thêm `mapping_session_id:UUID` và map_id=null. Fresh TF được code tính với tuổi 0–1 giây. Tuổi âm có thể xuất hiện khi lệch clock và fresh=false.

BE có thể cập nhật RobotLocationHistory từ map_pose nếu fresh=true và map_id đã có. Không lấy pose phiên mapping gắn vào map cũ. Worker chưa gửi bản tin ROBOT_LOCATION MQTT riêng. gate là dữ liệu chẩn đoán; UI nghiệp vụ không cần hiển thị nguyên chuỗi.

Nếu giải JSON trong `gate.value`, safety node hiện tạo `{ "reason": string, "moving_command": boolean }`. moving_command chỉ cho biết lệnh vận tốc đang khác 0, không phải đo robot đang thực sự chuyển động. Không dùng raw odom_speed hoặc moving_command để hiển thị vận tốc chính xác.

## 8. Presence — envelope riêng

Đủ `robotId:UUID`, `transportSessionId:UUID`, `online:boolean`. Không có messageType, messageId hay payload. Broker Last Will có online=false; retained có thể thuộc phiên cũ, cần đối chiếu phiên/thời điểm kết nối.

## 9. Mapping session, preview và upload

### mappingSession trong HELLO/HEARTBEAT

Null khi mapping disabled, `{}` khi chưa có phiên. Các field có thể có:

| Field | Kiểu | Khi xuất hiện |
|---|---|---|
| session_id / job_id | UUID / UUID | Từ START |
| state | string | STARTING, MAPPING, SAVING, SAVED_LOCAL, SAVED, STOPPED, ABORTED, RECOVERY_REQUIRED, OPERATOR_RECONCILED |
| session_open | boolean | Phiên còn chiếm robot |
| preview_rate_hz | number | Từ START, mặc định 1 |
| artifact_id | UUID | Sau lưu artifact |
| artifact_dir | string | Đường dẫn trên Pi; không phải URL để BE tải |
| reconciliation_reason | string | Sau đối soát local |
| reconciled_at | datetime | Có thể có từ công cụ local bổ sung |

SAVED_LOCAL/SAVED không tự đồng nghĩa phiên đã đóng. Xem session_open. STOP có thể giữ tên trạng thái SAVED_LOCAL/SAVED nhưng đổi session_open=false.

### MAP_PREVIEW

Envelope đủ `robotId`, `transportSessionId`, `messageType:"MAP_PREVIEW"`, `payload`. Payload đủ:

| Field | Kiểu / đơn vị |
|---|---|
| mapping_session_id | UUID |
| revision | integer, tăng khi Pi nhận grid |
| width / height | integer, số ô |
| resolution | number >0, m/ô |
| origin | [x_m, y_m, yaw_rad] |
| frame_id | string, thường map |
| captured_at | datetime, lúc Pi nhận grid |
| pose | object value/age_sec/fresh; value x_m/y_m/yaw_rad, không map_id |
| encoding | zlib-base64-int8-row-major-bottom-up |
| data | string Base64 |

Giải Base64 → zlib → mảng int8 dài width*height. -1 là chưa biết, 0–100 là occupancy. Có thể đọc unsigned byte 255 rồi quy về -1. Hàng đầu là mép dưới map; ảnh màn hình/PGM thường từ trên xuống, cần đảo trục Y khi hiển thị. Kích thước/tọa độ origin có thể thay đổi trong lúc SLAM chạy. Grid tới 4 triệu ô; data nén quá 850000 ký tự thì code bỏ preview đó. Preview mất frame vẫn nhận frame đầy đủ tiếp theo, không ACK hay lưu mỗi frame vào DB.

### Upload map — endpoint BE cần triển khai

`PUT /api/v1/pi/mapping-sessions/{sessionId}/artifacts/{artifactId}`

Path: sessionId và artifactId là UUID. Body binary application/gzip: tar.gz gồm map.yaml, map.pgm, manifest.json. Headers: `Authorization: Bearer <token HTTP>`, `Content-Type: application/gzip`, `X-Asset-SHA256`, `X-Archive-SHA256`.

asset hash = SHA256(bytes map.yaml + một byte 0 + bytes map.pgm); archive hash = SHA256(bytes tar.gz). Giữ nguyên file khi kiểm hash. BE kiểm quyền robot/phiên, kích thước và tên file, không giải nén đường dẫn tùy ý. Idempotent theo artifactId/hash.

Receipt HTTP JSON đủ `stored:true`, `artifactId:UUID`, `assetHash:string 64 hex`. Pi kiểm đúng ID/hash; không theo redirect. Token upload chưa tự refresh trong bộ này.

manifest.json và output SAVE dùng đủ: `artifact_id`, `asset_hash`, `width_px`, `height_px`, `resolution_m_per_pixel`, `width_m`, `height_m`, `origin_x_m`, `origin_y_m`, `origin_yaw_rad`, `frame_id`. BE COMMIT_MAP_ARTIFACT mới tạo Map/map_id sau khi đã lưu và kiểm artifact. Lưu local thành công chưa nghĩa đã có Map trong DB.

Chưa có lệnh MQTT điều khiển tay W/A/D khi mapping. Bộ hiện tại điều khiển từ keyboard ROS cục bộ. Không gửi velocity trong EXECUTE_STEP và kỳ vọng Pi hiểu. Chưa có tự khám phá map hay tự đổi Nav2 sang map vừa tạo.

## 10. Quy tắc lưu và chuyển bước

1. BE lưu command + snapshot params trong DB trước publish. Snapshot không thay đổi trong một jobStepId/operationKey.
2. Pi ghi COMMAND_ACK → BE commit/ACK → Pi ghi STEP_STARTED → BE commit/ACK → Pi chạy handler. Không thiếu một trong hai ACK.
3. BE commit kết quả + cập nhật JobStep + công việc chuyển nhánh trong transaction hoặc hàng đợi bền vững; gửi ACK sau commit. Trùng event chỉ ACK lại.
4. Sự kiện chưa ACK được Pi gửi lại khoảng 5 giây/lần. PUBACK không thay EVENT_ACK.
5. Retry commandId cũ giữ nguyên business body và expiresAt; chỉ transportSessionId được thay theo phiên. Nếu ID cũ đổi nội dung Pi báo COMMAND_ID_CONFLICT.
6. Operation đã có không chạy lại, kể cả lần trước lỗi. Muốn thử lại sau quyết định nghiệp vụ phải tạo visit/jobStepId/operationKey mới, không đổi params của thao tác cũ.
7. Không chọn mọi JobStep PENDING theo thứ tự: BE chạy nhánh hợp lệ trong graph. Bước BE/UI không gửi xuống Pi.
8. Hủy có thể nhận STEP_ERROR trước COMMAND_ACK của CANCEL_STEP. Ghép bằng jobStepId/operationKey và commandId; không giả định thứ tự ngược lại.
9. Hủy khi mapping đang mở nhưng không có step active: phải gửi EXECUTE_STEP MAPPING/STOP cùng job/session; CANCEL_STEP không đóng một phiên mapping đang chờ.
10. Mất kết nối/restart không tự phát lại tác động vật lý. Đối soát trạng thái journal với BE trước quyết định tiếp tục.

## 11. Đồng bộ map, endpoint, zone

MOVE chỉ nhận map_id và endpoint_id. Trước khi dispatch, cần đưa xuống Pi profile: `map_id`, `map_asset_sha256`, `endpoints[endpointId]={map_id,x_m,y_m,yaw_rad}`. UUID/hash phải khớp BE. Bộ worker chưa tự đồng bộ catalog bằng MQTT; demo đồng bộ thủ công bằng công cụ cấu hình hiện có.

BE giữ EndpointGroup và chọn một endpoint cụ thể cho mỗi MOVE. Không gửi group_id vào params MOVE hiện tại. Khi PATH_UNAVAILABLE và đã dừng chắc chắn, có thể chọn ứng viên khác bằng visit mới; không coi NAV_ABORTED hay mất cảm biến là bằng chứng endpoint bị chặn.

Zone polygon, frame/map và phiên bản map phải được đồng bộ cho node AI để đếm người đúng khu vực. Worker chỉ nhận zone_id và kết quả producer; chưa có cơ chế truyền polygon/zone config qua MQTT. Camera thấy bao nhiêu người trong khung hình không tự tương đương số người trong toàn zone.
