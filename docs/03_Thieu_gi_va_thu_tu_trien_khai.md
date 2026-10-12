# Các phần còn thiếu và thứ tự thực hiện

Đối chiếu bản BE_MAR-thanh và bộ Pi MQTT 08/10 trên máy, ngày 11/10/2026. Không có bằng chứng robot hiện tại đã chạy đúng bản này hoặc có broker đang vận hành. Các phần dưới phân biệt code đã có với tích hợp chưa hoàn thành.

## 1. Chốt kiến trúc

- BE giữ workflow, Job/JobStep, context, phân nhánh, dữ liệu UI và quyền staff.
- Pi giữ handler ROS, cấu hình phần cứng, giới hạn vận động, journal chống thực thi trùng.
- Broker trung chuyển; robot và BE là hai client MQTT 5/TLS.
- 11 StepType giữ nguyên tên. MOVE, CONTROL_HARDWARE, EXECUTE_SENSING và WAIT/DURATION gửi xuống Pi; còn lại BE thực hiện. Không thêm CALL_WORKFLOW.
- Không thay raw servo/gpio theo params BE. Cấu hình cục bộ: ID4, 1500/2400, US100 BCM17/18 và 22/23.

## 2. Bắt buộc hoàn thiện trước demo delivery MQTT

| Mức | Chỗ còn thiếu/sai | Việc cần làm | Người phụ trách |
|---|---|---|---|
| P0 | Chưa có broker/giá trị triển khai được xác nhận | Chốt host, TLS8883, CA, UUID robot, tài khoản riêng và ACL | Broker + BE + Pi |
| P0 | BE chưa có MQTT dependency/client/subscriber/publisher | Thêm kết nối và DTO theo bộ schema | BE |
| P0 | Chưa có HELLO_ACK/SERVER_HEARTBEAT/EVENT_ACK | Trả đúng phiên, đúng topic, ACK sau commit | BE |
| P0 | Luồng cũ DISPATCH/CANCEL cấp Job | Thêm điều phối từng step; robot MQTT không nhận cùng việc qua REST polling | BE |
| P0 | CHECK_CONDITION/SET_CONTEXT đang executor PI | Chuyển SERVER + handler, migration/compiled graph tương ứng | BE |
| P0 | Step input chưa đóng băng trước gửi | Resolve và lưu snapshot; STEP_STARTED đối chiếu lại, không resolve khác | BE |
| P0 | CONTROL_HARDWARE seed thêm service_request_id | Chỉ gửi compartment_id/action; request ID giữ trong BE | BE |
| P0 | Confirm delivery chỉ ghi delivery, chưa resume step | Nối đúng JobStep và chuyển nhánh sau commit | BE |
| P0 | Không có chốt cảm biến phản hồi | Giữ published/verified đúng nghĩa; hoàn thiện UI xác nhận CLOSE/VERIFY có sẵn | BE + người vận hành |
| P0 | Staff verification đang hoãn | Chế độ demo tường minh; không làm giả verificationId, không đánh verified=true | BE + Pi |
| P0 | Enum JobStep BE thiếu RECOVERY_REQUIRED | Thêm enum, constraint/migration và xử lý; không gộp thành lỗi retry thường | BE |
| P0 | stepError hiện chỉ nhận RUNNING, gom FAILED/RETRYING | Nhận lỗi hợp lệ trước STARTED và bảo toàn timeout/cancel/recovery | BE |
| P0 | Chống trùng/ACK và command outbox chưa cho MQTT | Lưu bền vững, khóa giao dịch, không chuyển bước hai lần | BE |
| P0 | Timeout scheduler chỉ đổi DB | Phối hợp hủy/dừng Pi, chờ kết quả vật lý hoặc recovery | BE |
| P0 | Heartbeat DTO và lifecycle REST không khớp MQTT | Mapper riêng; HELLO xử lý STARTING/đối soát; tránh OFFLINE→IDLE bị guard cũ từ chối | BE |
| P0 | Chưa xác nhận map/endpoint thực tế đã đồng bộ | Cùng UUID/map hash và tọa độ; commissioning Nav2/xe đẩy đúng thực tế | Pi + BE |

Không nói “code hoàn chỉnh 11 step trên Pi” khi 7 nhóm xử lý ở server/UI, AI còn thiếu producer và các giá trị triển khai chưa được điền.

## 3. Cần làm tiếp cho đầy đủ các chức năng đồ án

| Phần | Hiện có | Còn thiếu |
|---|---|---|
| WAIT/EVENT | Có tên và schema | Handler BE + correlation/timeout + callback sự kiện |
| INTERACT | BE tạo instruction và chờ | Output callbacks, UI hiển thị/nhập/xác nhận; TTS/audio nếu cần SPEAK |
| HANDLE_PAYLOAD | Handler chờ staff | Items snapshot, quyền staff, resume step, không giao hàng hai lần |
| NOTIFY | Tạo Alert thật trong BE | Tôn trọng severity/require_ack, thống nhất người nhận và WAIT alert ACK |
| AUTHENTICATE | Handler chờ và API verification riêng | Gắn kết quả với step; chính sách quyền mở chốt thực sự trên Pi |
| AI crowd/restricted | Pi đọc ROS JSON và trả kết quả | Node model thật, hiệu chuẩn/map-zone, đánh giá đếm và vùng quan sát |
| AI té ngã/SOS chủ động | BE có DetectionEvent/Incident API, service tạo incident | Producer AI + schema/event bridge vào BE; worker chưa phát fall event MQTT |
| Heatmap | Có hướng dữ liệu BE | Vị trí người theo map, chống đếm lặp, gắn zone/thời gian; không dùng range hai bên làm person count |
| Quét map | Pi START/SAVE/STOP, preview, file local/upload client | BE upload endpoint, COMMIT_MAP_ARTIFACT, UI render, lifecycle phiên |
| Điều khiển map từ UI | Có keyboard ROS local | Chưa có protocol lệnh điều khiển tay từ UI qua MQTT; không dùng command hiện tại cho velocity |
| Endpoint group | BE có RESOLVE/SELECT/CHECK; Pi MOVE một endpoint | BE loop/visit/giới hạn thử, lưu endpoint đã chọn, xử lý lỗi đúng loại |
| Đồng bộ endpoint/zone | Có REST catalog và cấu hình local | Worker MQTT chưa tự sync/ack revision; cần sync tool/flow và đối chiếu hash |
| Cloud | Pi chủ động mở kết nối TLS | Internet/DNS/clock/CA, broker firewall, giám sát disconnect thực tế |

## 4. Giới hạn Pi cần biết khi viết BE

1. UNLOCK hiện chỉ COMMISSIONING_ONLY. Authorization object có schema nhưng chưa được dùng để cấp quyền; đây là chức năng còn thiếu, không phải chỉ thiếu một field config.
2. `mode=ESCORT` không tự bám hoặc phát hiện người đi theo; mode hiện dùng chung navigation handler.
3. AI nhận count/intrusion; chưa tự tính người/m², phân bố người trên toàn zone hoặc té ngã.
4. Không có MQTT bước INTERACT/SPEAK, không có STEP_PROGRESS chuyên biệt, không có remote teleop topic đang xử lý.
5. Bộ profile mẫu chưa commissioning/map endpoint. Gói code tồn tại không chứng minh đã chạy được trên robot thực.
6. Map preview/file không tự active map mới; cần đồng bộ, đổi map và định vị lại có kiểm soát.
7. Broker/TLS/schema test không xác nhận motor/servo/Nav2. Cần test phần cứng thật từng lớp.

## 5. Tái dùng các class hiện tại, bổ sung gì

Tái dùng JobExecutionService tạo runtime records; JobStepInputResolver giải $ref; WorkflowRuntimeTransitionService chọn nhánh; handlers BE hiện có; JobEventRepository chống trùng. Tách logic transport khỏi PiJobRuntimeService/PiStepService, không gọi vòng HTTP nội bộ và không giả DevicePrincipal để tạo sự kiện server.

Thêm các nhóm:

- `RobotMqttProperties`, `RobotMqttClient`, `RobotMqttTopics`, `RobotMqttPublisher`, `RobotMqttInboundHandler`.
- `RobotMqttSessionRegistry`, `RobotServerHeartbeatScheduler`.
- `RobotCommandService`, `RobotEventService`, `RobotRecoveryService`.
- `WorkflowOrchestratorService`, `ServerStepExecutionService`, `StaffStepCompletionService`.
- DTO/schema theo messageType; không deserialize mọi payload vào DTO REST cũ.

Lưu `RobotCommandOutbox` (commandId, robotId, jobId, jobStepId, commandType, payloadJson, expiresAt, status, publishAttempts, lastPublishedAt, acceptedAt, createdAt). Incoming event cần unique messageId và producerSessionId/sequenceNo, raw payload, receivedAt/processedAt. Có thể mở rộng bảng event hiện có nếu đáp ứng; không bắt buộc tạo bảng trùng chức năng. MySQL dùng JSON, không phải JSONB.

## 6. Thứ tự làm và tiêu chí đạt

1. **Chốt thông số:** điền template; Pi/BE thống nhất UUID, topic, executor, params. Không cần broker để đọc/validate schema.
2. **Broker + handshake:** kết nối ổn định quá 15 giây; theo dõi hai chiều, không chỉ presence online.
3. **WAIT/DURATION:** command→ACK→started→ACK→result→ACK, DB chỉ cập nhật một lần khi duplicate.
4. **ROBOT_HEALTH/SIDE_CLEARANCE:** nhận dữ liệu thật, xử lý thiếu dữ liệu và null đúng nghĩa.
5. **Servo/MOVE:** demo từng lệnh, map/endpoint thật; output không khẳng định cửa đã khóa bằng cảm biến.
6. **Delivery A→B:** hoàn thiện UI callbacks, chọn workflow demo rõ, không fake xác thực; chỉ đi tiếp khi xác nhận cần thiết đã có.
7. **Lỗi thực tế:** mất BE, mất broker, reconnect, command hết hạn, duplicate, hủy/timeout, thao tác dở dang. Không cho robot chạy lại mù quáng.
8. **AI/map/group:** làm các hạng mục P1 theo danh sách chức năng, không đưa vào demo đã hoàn thành khi mới có schema.

Code BE chưa bị sửa trong lần bàn giao này. Mỗi hạng mục cần người phụ trách triển khai và kiểm tra, không được suy “đã có tài liệu” thành “đã có tính năng”.
