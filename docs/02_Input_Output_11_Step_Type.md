# Input/output của 11 Step Type

Phần A là hợp đồng theo handler Pi hiện tại. Phần B là hợp đồng nội bộ BE được chốt trong lần bàn giao này; các chỗ BE phải sửa được nêu rõ. Không gửi bước ở phần B qua EXECUTE_STEP xuống Pi. Output Pi ở đây nằm trong `STEP_RESULT.payload.result`, không phải trực tiếp ở envelope.

## A. Các bước Pi thực hiện

## 1. MOVE

Input: tất cả bắt buộc, không thêm field khác.

| Field | Kiểu / giá trị | Ý nghĩa |
|---|---|---|
| map_id | UUID | Map đã đồng bộ |
| endpoint_id | UUID | Một điểm cụ thể đã có tọa độ trong Pi |
| mode | NORMAL / ESCORT / PATROL | Mục đích bước |
| max_speed_mps | number >0 và <=0.3 | Không vượt max_speed_mps cấu hình Pi; demo chốt 0.05 |

BE chốt timeout 120 giây cho demo điểm gần, điều chỉnh theo tuyến thực tế. Pi không tự di chuyển hết workflow. ESCORT/PATROL hiện dùng cùng NavigateToPose; không tự bổ sung bám người, kiểm người đi theo hay tuần tra cả danh sách điểm.

Output đầy đủ khi thành công:

| Field | Kiểu / giá trị |
|---|---|
| reached | true |
| endpoint_id | UUID điểm thực sự đạt |
| action_status | integer, ROS action success=4 |
| last_feedback_pose | `{x_m:number,y_m:number,yaw_rad:number}` hoặc null |
| distance_remaining_m | number hoặc null |
| endpoint_group_id | null trong hợp đồng MQTT nhận một endpoint hiện tại |
| attempts | array, mỗi phần tử `{endpoint_id:UUID,phase:string,outcome:string}` |

Đường đi thành công một endpoint hiện ghi một attempt phase=NAVIGATE,outcome=REACHED. Không cam kết nhận tiến độ MOVE liên tục qua STEP_PROGRESS: worker chưa phát loại đó. Pose cập nhật qua heartbeat. PATH_UNAVAILABLE có thể dùng để chọn endpoint khác tại BE sau khi xác nhận dừng. NAV_ABORTED không tự khẳng định điểm đến bị chặn.

## 2. CONTROL_HARDWARE

| Input | Kiểu | Bắt buộc |
|---|---|---|
| compartment_id | string, chốt CART_MAIN | Có |
| action | LOCK_COMPARTMENT / UNLOCK_COMPARTMENT | Có |
| authorization | object | Không; dành sẵn, hiện không dùng để cấp quyền mở |

Các field dành sẵn trong authorization, nếu xuất hiện phải đủ: `mode:"STAFF_VERIFIED"`, `verification_id:UUID`, `actor_account_id:UUID`, `purpose:DELIVERY_SENDER|DELIVERY_RECEIVER`, `valid_until:datetime`.

**Chốt triển khai hiện tại:** BE không gửi authorization để giả định staff đã xác thực. Pi chưa kiểm/tiêu thụ quyền này; UNLOCK chỉ chạy khi unlock_policy=COMMISSIONING_ONLY. Cần thiết kế và triển khai xác thực thật trước khi dùng STAFF_VERIFIED. Không gửi service_request_id, device_id, pwm, ros_id vào params; Pi ánh xạ nội bộ.

Output đủ 9 field:

| Field | Kiểu / giá trị | Ý nghĩa |
|---|---|---|
| compartment_id | string | CART_MAIN |
| action | enum như input | Hành động nghiệp vụ |
| published | true | Đã phát ROS command |
| controller_ack | false | Chưa có phản hồi chấp nhận từ controller |
| verified | false | Chưa có cảm biến xác nhận chốt |
| commanded_state | LOCK / UNLOCK | Trạng thái đã yêu cầu |
| position_raw | integer 1500 / 2400 | Khóa / mở |
| ros_servo_id | integer 4 | ID servo trên RRC Lite S6 |
| authorization_mode | DEVICE_COMMAND / COMMISSIONING_ONLY | LOCK / UNLOCK tương ứng |

Không có field đo góc servo. `actuatorPositionVerified` thuộc snapshot kết quả trong journal/ACK replay, không phải output handler. Timeout demo 10 giây. Profile servo phải commissioned và robot được xác nhận đứng yên bằng odom trước thao tác.

Không đổi verified thành true khi staff xác nhận. Staff confirmation là output của INTERACT riêng với source=STAFF_MANUAL.

## 3. EXECUTE_SENSING

### 3.1 ROBOT_HEALTH và SIDE_CLEARANCE

Input chốt chỉ có `sensing_type:ROBOT_HEALTH|SIDE_CLEARANCE`. Schema nền cũng nhận zone_id/window_sec, nhưng không có tác dụng lấy mẫu cửa sổ ở hai loại này; BE bỏ chúng. Timeout demo 5 giây.

Output đủ `sensing_type`, `metrics:object`, `captured_at:datetime`, `evidence_ref:null`.

metrics đủ:

| Field | Kiểu | Nghĩa |
|---|---|---|
| sensors_ready | boolean | Hai range hợp lệ/mới và LiDAR có ranges mới |
| left_m / right_m | number hoặc null | Khoảng cách mét |
| left_fresh / right_fresh | boolean | Dữ liệu mới và trong 0.02–4 m |
| left_age_sec / right_age_sec | number hoặc null | Tuổi mẫu |
| battery_age_sec | number hoặc null | Tuổi raw battery |
| battery_raw | integer hoặc null | UInt16 chưa quy đổi |
| battery_percent | null | Chưa có % battery |
| battery_unit | UNVERIFIED_UINT16 | Chưa xác minh đơn vị thực tế |

ROBOT_HEALTH có thể SUCCEEDED nhưng sensors_ready=false; thành công nghĩa đọc được báo cáo. SIDE_CLEARANCE trả RANGE_NOT_READY nếu một trong hai range không fresh. Không suy hai cảm biến cạnh xe thành số lượng người, người ngã hay cửa đã đóng.

### 3.2 CROWD_DENSITY và RESTRICTED_ZONE

| Input | Kiểu | Bắt buộc |
|---|---|---|
| sensing_type | CROWD_DENSITY / RESTRICTED_ZONE | Có |
| zone_id | UUID | Có theo kiểm tra runtime |
| window_sec | number 0–30, mặc định 0 | Không; chốt dùng 3 khi chờ mẫu mới |

timeoutSec >= window_sec+1; demo 6 giây với cửa sổ 3 giây. window_sec=0 cho phép lấy mẫu cache còn mới; >0 chờ mẫu đến sau khi bắt đầu bước. Đây là thời gian chờ mẫu hợp lệ, **không phải phép tính trung bình số người trong một cửa sổ**.

Output tối thiểu mà node AI phải cung cấp cho worker:

| Field | Kiểu | Bắt buộc |
|---|---|---|
| sensing_type | enum tương ứng | Có |
| map_id | UUID, khớp profile | Có |
| zone_id | UUID | Có |
| model_version | string không rỗng | Có |
| captured_at | datetime có timezone | Có, tuổi mẫu mặc định <=2s |
| metrics | object | Có |
| metrics.count | integer >=0 | Với CROWD_DENSITY |
| metrics.intrusion_detected | boolean | Với RESTRICTED_ZONE |
| evidence_ref | string hoặc null | Tùy chọn, nên null nếu không lưu ảnh |

Worker trả nguyên observation AI; fields mở rộng từ producer cần được version hóa, BE giữ phần chưa biết dưới JSON nếu cần. Chưa chốt thêm confidence/density/person coordinates vì model chưa cung cấp hợp đồng đầy đủ. Không tự coi count là mật độ người/m²; muốn có mật độ phải có diện tích vùng và cách quan sát được xác định.

Hiện chỉ có adapter đọc kết quả AI, chưa có weights/inference producer và chưa có producer chủ động phát té ngã/SOS. Không có sensing_type FALL_DETECTION trong MQTT params hiện tại. Không phát tên đó rồi mong code thực thi.

### 3.3 MAPPING

| Action | Input bắt buộc | Tùy chọn | Timeout chốt |
|---|---|---|---|
| START | sensing_type=MAPPING, action=START, mapping_session_id:UUID | preview_rate_hz:number 0.2–1, mặc định 1 | 60s; tối thiểu start_timeout_sec+10 |
| SAVE | sensing_type=MAPPING, action=SAVE, mapping_session_id:UUID, artifact_id:UUID | Không | 90s; tối thiểu 60 |
| STOP | sensing_type=MAPPING, action=STOP, mapping_session_id:UUID | Không | 20s; tối thiểu 15 |

Cả ba phải cùng jobId/mapping_session_id; mỗi action là một jobStepId/operationKey riêng. BE cấp artifact_id trước SAVE; cùng phiên không được đổi artifact_id sau khi đã chốt artifact. START cần SLAM/map/TF/safety sẵn sàng và Nav2 navigation đã dừng. Không gửi map_id của map cũ làm định danh map đang quét.

Output chung đủ `sensing_type:"MAPPING"`, `mapping_session_id`, `metrics`, `captured_at`, `evidence_ref:null`.

| Action | Toàn bộ metrics |
|---|---|
| START | state=MAPPING, preview_ready=true |
| SAVE | state=SAVED_LOCAL hoặc SAVED, saved_locally=true, uploaded:boolean, artifact_id:UUID, asset_hash:SHA256, width_px:integer, height_px:integer, resolution_m_per_pixel:number, width_m:number, height_m:number, origin_x_m:number, origin_y_m:number, origin_yaw_rad:number, frame_id:string |
| STOP | state:string, stopped=true |

STOP có thể giữ state=SAVED_LOCAL/SAVED; xem thêm session_open=false trong heartbeat. `uploaded=false` khi chưa cấu hình endpoint upload, không đồng nghĩa thất bại lưu local. BE tạo Map sau khi nhận/kiểm file thực tế, không chỉ dựa vào saved_locally=true.

## 4. WAIT

### DURATION — Pi

Input đủ `wait_type:"DURATION"`, `duration_sec:number 1–3600`. Không thêm event_type/correlation_key. timeoutSec >= duration_sec+1. Demo duration=2, timeout=5.

Output đủ `completed:true`, `wake_reason:"DURATION"`, `elapsed_sec:number`, `event:null`. Pi không dùng WAIT để chạy workflow tiếp; hết chờ vẫn trả về BE.

### EVENT — BE

Input chốt `wait_type:"EVENT"`, `event_type:string`, `correlation_key:string`; không có duration_sec. Timeout dùng timeoutSec của JobStep. Output BE cần triển khai: `completed:true`, `wake_reason:"EVENT"`, `elapsed_sec:number`, `event:{event_type,correlation_key,occurred_at,data}`. data là object nghiệp vụ theo loại event. Hết hạn chuyển TIMED_OUT, không tạo event giả.

Pi trả SERVER_STEP_REQUIRED nếu gửi WAIT/EVENT xuống robot.

## B. Các bước BE xử lý — hợp đồng cần triển khai/hoàn thiện

Input/output dưới đây là quyết định chốt cho BE. Các schema nằm trong `schemas/be-step-contracts.json`, không phải schemas mà Pi dùng để thực thi. Đối chiếu với handlers BE hiện tại trước khi publish workflow. Không tự xem output tạm đang chờ là kết quả SUCCEEDED.

## 5. INTERACT

Input bắt buộc: `prompt_key:string`, `action:DISPLAY|CAPTURE_INPUT|CONFIRM|SPEAK`, `data:object`. data chứa dữ liệu nghiệp vụ cần trình bày, ví dụ compartment_id, service_request_id, danh sách hàng hoặc nội dung hỏi. Không tự gửi toàn bộ thông tin staff xuống robot.

Output chốt sau hoàn tất:

| Field | Kiểu | Quy tắc |
|---|---|---|
| completed | boolean=true | Chỉ khi tương tác được hoàn tất theo action |
| prompt_key | string | Trùng input |
| action | enum | Trùng input |
| confirmed | boolean | ĐK với CONFIRM/CAPTURE_INPUT cần xác nhận |
| closed | boolean | ĐK cho CLOSE_SENDER/CLOSE_RECEIVER, dùng bởi graph hiện tại |
| locked | boolean | ĐK cho VERIFY_SENDER/VERIFY_RECEIVER |
| values | object | Tùy chọn, dữ liệu nhập khác đã kiểm tra |
| source | STAFF_MANUAL / UI / AUDIO_CLIENT | Nguồn hoàn tất |
| actor_account_id | UUID hoặc null | BE lấy từ tài khoản, không tin client tự khai |
| completed_at | datetime | BE ghi thời gian |

Handler hiện tại chỉ tạo instruction và completed=false. Cần triển khai callback UI tương ứng. DISPLAY phải chốt tiêu chí nhận/hiển thị; SPEAK chỉ thành công khi có audio client thực hiện và báo lại. Worker Pi hiện chưa có kênh TTS/INTERACT MQTT, nên chưa demo SPEAK qua worker này.

Graph có thể đọc `/outputs/CLOSE_SENDER/closed` và `/outputs/VERIFY_SENDER/locked`; không đổi các field đó thành một confirmed chung mà không sửa graph.

## 6. DATA_SERVICE

Input luôn có `service_key`. Các field theo từng dịch vụ:

| service_key | Input còn lại | Output chốt/đang có |
|---|---|---|
| QUERY_HOSPITAL_INFORMATION | question:string | data.items[]; data.count:integer |
| RESOLVE_TARGETS | endpoint_group_id:UUID | data.targets[] |
| SELECT_TARGET | targets:object[], index:integer>=0 | data.has_target:boolean; khi true thêm data.target và data.endpoint_id |
| CHECK_TARGET | endpoint_id:UUID | data.is_valid:boolean; khi true thêm data.endpoint |
| CREATE_FALL_INCIDENT | detection_event_id:UUID | data.incident_id:UUID, data.created:boolean |
| COMMIT_MAP_ARTIFACT | mapping_session_id:UUID, artifact_id:UUID, floor_id:UUID, map_name:string | data.map_id:UUID, data.artifact_id:UUID, data.asset_hash:string, data.created:boolean |

Mỗi endpoint object ở targets/target/endpoint gồm `endpoint_id:UUID`, `endpoint_code:string`, `endpoint_name:string`, `x_m:number`, `y_m:number`, `yaw_rad:number`. Các trường từ BigDecimal BE cần serialize number, không tùy tiện đổi sang string.

Hospital item hiện gồm `id`, `category`, `title`, `content`, `languageCode`. Handler hiện lấy tối đa 20 thông tin public còn hiệu lực, **chưa tìm kiếm/AI trả lời theo question**. Giữ question làm input nghiệp vụ nhưng phải hoàn thiện bộ truy vấn trước khi nói tính năng hỏi đáp đã xong.

Năm service đầu đã có handler ở BE nhưng cần kiểm scope/điều phối. COMMIT_MAP_ARTIFACT chưa có; BE phải kiểm receipt/file, cấp mapCode/versionNo và tạo Map. CHECK_TARGET chỉ kiểm dữ liệu endpoint active/accessible trong DB, không kiểm đường đi vật lý.

CREATE_FALL_INCIDENT cần DetectionEvent loại SUSPECTED_FALL có thật, đúng robot. Worker hiện chưa tự sinh DetectionEvent té ngã.

## 7. AUTHENTICATE — hoãn, không cấp quyền giả

Input BE chốt: `purpose:DELIVERY_SENDER|DELIVERY_RECEIVER`, `service_request_id:UUID`. service_request_id được thêm rõ vào hợp đồng nội bộ vì seed BE đang dùng để liên kết yêu cầu.

Output dự kiến khi triển khai thật: `verified:boolean`, `verification_id:UUID|null`, `actor_account_id:UUID|null`, `purpose:enum`, `service_request_id:UUID`, `verified_at:datetime|null`, `valid_until:datetime|null`. verified=true phải có các ID/time hợp lệ; xác thực không thành công không cấp verification_id dùng mở cửa.

Handler hiện chỉ trả AWAIT_STAFF_VERIFICATION. Callback/link bước với verification và cơ chế cấp quyền xuống Pi chưa hoàn thiện. Chưa dùng output tương lai này để tuyên bố hệ thống đã xác thực. Pi trực tiếp nhận AUTHENTICATE sẽ báo AUTHENTICATE_NOT_IMPLEMENTED.

## 8. HANDLE_PAYLOAD

Input bắt buộc: `action:LOAD|UNLOAD`, `service_request_id:UUID`, `compartment_id:string`, `items:array` tối thiểu 1.

Mỗi item gồm đủ `item_code:string`, `quantity:integer>=1`. Các mô tả itemName/unit/category lấy từ DeliveryItem khi hiển thị UI; không phải params gửi Pi.

Output BE chốt: `confirmed:boolean`, `action`, `service_request_id`, `compartment_id`, `items` theo cấu trúc input, `actor_account_id:UUID`, `confirmed_at:datetime`, `source:"STAFF_MANUAL"`. Có thể thêm `note:string`. quantity ở kết quả là lượng staff xác nhận; nếu thiếu hàng phải xử lý nhánh nghiệp vụ, không tự suy confirmed=true.

Handler hiện chỉ trả AWAIT_STAFF_PAYLOAD_CONFIRMATION. API confirm-pickup/confirm-delivery hiện chưa hoàn tất đúng JobStep đang chờ; cần nối callback. Items không được DeliveryService tự chép đầy đủ vào Job.inputJson: BE phải đọc DeliveryItem theo service_request_id và tạo snapshot cần thiết.

## 9. NOTIFY

Input BE chốt: `notification_type:string`, `severity:INFO|WARNING|CRITICAL`, `data:object`, `require_ack:boolean`; tùy chọn `message:string`. BE chọn đối tượng nhận theo loại thông báo/quyền nghiệp vụ, không nhận raw địa chỉ gửi từ Pi.

Output chốt: `alert_id:UUID`, `notification_type:string`, `persisted:true`, `requires_ack:boolean`. Chỉ nói alert đã được lưu, không nói staff đã đọc. Nếu workflow cần đợi staff nhận, theo sau là WAIT/EVENT có correlation_key=alert_id và event_type=ALERT_ACKNOWLEDGED.

Handler hiện tự suy severity và yêu cầu ACK theo tên/type, chưa dùng đúng severity/require_ack input. Phải sửa để tôn trọng hợp đồng, bổ sung requires_ack output. Trường recipients trong seed cũ cần được xử lý qua chính sách người nhận hoặc migration thống nhất; không gửi xuống Pi.

## 10. CHECK_CONDITION

Input bắt buộc `expression:object`. Bên trong: `path:string`, `operator:EQ|NE|GT|GTE|LT|LTE|IN|EXISTS`; `value` bắt buộc với mọi operator trừ EXISTS. value nhận kiểu JSON phù hợp với phép so sánh.

Đánh giá trên runtimeContext gồm `/inputs`, `/vars`, `/outputs`. Phép so sánh số yêu cầu hai vế là số. IN yêu cầu value là array; không chạy eval hoặc mã người dùng nhập.

Output chốt `{ "value": boolean }` để khớp transition seed đang đọc `result.value`. Kết quả false vẫn là step SUCCEEDED, sau đó graph đi nhánh false; không chuyển thành FAILED chỉ vì điều kiện sai.

BE có bộ đánh giá condition cho transition nhưng chưa có ServerStepHandler tương ứng cho StepType này. Cần tái dùng logic phù hợp, đổi executor từ PI sang SERVER. Giữ phân biệt: path của điều kiện step là runtimeContext, path của transition là root result/error/inputs của completedStep.

## 11. SET_CONTEXT

Input bắt buộc `operations:array` ít nhất 1. Mỗi operation:

| Field | Kiểu | Quy tắc |
|---|---|---|
| path | string | Chỉ `/vars/<ten_bien>`, tên bắt đầu chữ, tiếp theo chữ/số/_ |
| operation | SET / INCREMENT | Tùy chọn, mặc định SET để khớp seed cũ |
| value | JSON | Bắt buộc; INCREMENT yêu cầu số |

Output chốt `{ "updated": { "ten_bien": <gia_tri_moi> } }`. Lưu atomically vào Job.contextJson. INCREMENT chỉ áp dụng khi biến hiện có là số; chưa khởi tạo thì báo lỗi, workflow dùng SET trước. Không cho ghi `/inputs`, `/outputs`, identity, trạng thái Job hay secret. Chống xử lý trùng để không tăng biến hai lần.

Hiện BE khai báo executor PI và chưa có handler server; phải sửa. Pi sẽ trả SERVER_STEP_REQUIRED.

## C. Field UI cần gửi để hoàn tất bước BE

Đề xuất endpoint mới `POST /api/v1/job-steps/{jobStepId}/responses`, chưa có trong BE hiện tại. Header `Idempotency-Key`; body `responseType:string`, `data:object`, `note:string` tùy chọn. Không đưa actor_account_id/verified=true tùy ý từ client; server xác định tài khoản và kiểm scope.

| responseType | data bắt buộc | Bước đích |
|---|---|---|
| PAYLOAD_CONFIRMED | confirmed:boolean, items:[{item_code,quantity}] | HANDLE_PAYLOAD đang RUNNING |
| DOOR_CLOSED | closed:boolean | INTERACT CLOSE_* đang RUNNING |
| LOCK_CHECKED | locked:boolean | INTERACT VERIFY_* đang RUNNING |
| INTERACTION_CONFIRMED | confirmed:boolean, values:object nếu có dữ liệu nhập | INTERACT CONFIRM/CAPTURE_INPUT |
| UI_DISPLAYED | prompt_key:string | INTERACT DISPLAY |

BE kiểm step được gán cho staff, đúng loại response, đúng request, chỉ ghi một lần và chọn nhánh sau commit. AUTHENTICATE vẫn dùng quy trình verification thật; không cho endpoint này tự cấp quyền xác thực. Profile demo có thể xác nhận thủ công và ghi source riêng nhưng không dùng để bỏ kiểm tra trong production.

Delivery Create REST giữ DTO hiện tại: mapId, pickupRoomId?, deliveryRoomId?, pickupEndpointId, deliveryEndpointId hoặc deliveryGroupId theo targetMode, recipientName, recipientContact?, requiresConfirmation=true, temperatureRequirement?, deliveryDeadline?, deliveryNote?, items, priority, note?, data?. Mỗi item: itemCode, itemName, itemCategory, quantity>=1, unit, sensitive, handlingInstruction?, itemNote?. Đặt data.compartment_id=CART_MAIN hoặc defaultInputs tương ứng. Các field camelCase REST được BE chuyển thành input snake_case cho step, không sửa Pi theo tên DTO REST.
