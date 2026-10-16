# S3 — 04-MediAssistRover_Dac_ta_11_STEP_TYPE-1-.docx

Nguồn: `C:\Users\Admin\Downloads\04-MediAssistRover_Dac_ta_11_STEP_TYPE-1-.docx`
SHA-256: `878269545E0FA803DDAA402C63B7C0765B8251467F3F1FA76FF9711A67F92DB3`
Ngày đọc: 2026-10-07. Trích xuất văn bản; không phải bản render. Draw.io giữ ID và đầu mút cạnh; DOCX giữ thứ tự đoạn/ô, không bảo toàn bố cục hay hình nhúng.

[1] PART: word/document.xml
[2] Đặc tả STEP TYPE MediAssistRover
[3] Bộ 11 loại bước cho Raspberry Pi 5 và MentorPi M1
[4] Danh mục 11 STEP TYPE
[5] Tất cả field output được liệt kê trong từng bảng đều bắt buộc hiện diện; field cho phép null vẫn phải có khóa. Root input/output không nhận field ngoài danh mục. Các object động như data, metrics và event phải tuân theo hợp đồng nghiệp vụ tương ứng.
[6] Số
[7] STEP TYPE
[8] Nơi xử lý
[9] 1
[10] MOVE
[11] Pi gọi Nav2
[12] 2
[13] INTERACT
[14] Server điều phối màn hình hoặc TTS
[15] 3
[16] DATA_SERVICE
[17] Server
[18] 4
[19] AUTHENTICATE
[20] Server
[21] 5
[22] CONTROL_HARDWARE
[23] Pi gửi ROS tới RRC Lite
[24] 6
[25] HANDLE_PAYLOAD
[26] Server và nhân viên
[27] 7
[28] EXECUTE_SENSING
[29] Pi đọc cảm biến; server xử lý ảnh
[30] 8
[31] NOTIFY
[32] Server
[33] 9
[34] WAIT
[35] Pi chạy timer hoặc chờ sự kiện server
[36] 10
[37] CHECK_CONDITION
[38] Pi
[39] 11
[40] SET_CONTEXT
[41] Pi
[42] 1 Di chuyển tới điểm đã cấu hình
[43] MOVE  |  Pi gọi Nav2
[44] Pi nhận mã bản đồ và điểm đến, tra tọa độ trong cấu hình cục bộ rồi gửi goal NavigateToPose. Robot chỉ báo bước thành công khi action kết thúc với trạng thái SUCCEEDED. Hai US100 và bộ giám sát chuyển động vẫn chạy trong suốt quá trình di chuyển.
[45] Input
[46] Kiểu
[47] Bắt buộc
[48] Ý nghĩa và giá trị
[49] map_id
[50] string
[51] Có
[52] Mã phiên bản bản đồ. Phải khớp bản đồ và hash tài nguyên đã cấu hình trên Pi. Giá trị: độ dài tối đa 2000.
[53] endpoint_id
[54] string
[55] Có
[56] Mã điểm đến. Pi tra tọa độ trên bản đồ đã duyệt; không nhận tọa độ tùy ý từ người dùng. Giá trị: độ dài tối đa 2000.
[57] mode
[58] enum
[59] Có
[60] NORMAL đi thông thường; ESCORT dẫn đường chậm; PATROL đi tới điểm quan sát. Giá trị: NORMAL, ESCORT, PATROL.
[61] max_speed_mps
[62] number
[63] Có
[64] Giới hạn tốc độ tịnh tiến, đơn vị m/s. Ví dụ 0.15; không vượt chính sách cục bộ trên Pi. Giá trị: lớn hơn 0; tối đa 0.3.
[65] Output trong data
[66] Kiểu
[67] Ý nghĩa
[68] reached
[69] boolean
[70] Nav2 action đạt SUCCEEDED.
[71] endpoint_id
[72] string
[73] Mã điểm đến. Pi tra tọa độ trên bản đồ đã duyệt; không nhận tọa độ tùy ý từ người dùng. độ dài tối đa 2000.
[74] action_status
[75] integer
[76] Mã trạng thái ROS action. 4 là SUCCEEDED; goal được chấp nhận chưa phải đã tới đích.
[77] last_feedback_pose
[78] object hoặc null
[79] Feedback cuối dạng {x_m:number, y_m:number, yaw_rad:number} hoặc null; không phải phép đo tư thế cuối độc lập.
[80] distance_remaining_m
[81] number hoặc null
[82] Khoảng còn lại theo feedback cuối; null nếu chưa có.
[83] Các trường input đều bắt buộc. Schema nhận 0 < max_speed_mps ≤ 0.3; Pi vẫn từ chối nếu giá trị vượt trần cục bộ. Giá trị 0.15 m/s trong ví dụ là cấu hình minh họa, cần chạy thử với cả xe đẩy.
[84] Trước khi gửi goal: kiểm hash bản đồ, endpoint thuộc map, TF còn mới, Nav2 sẵn sàng và đường điều khiển vận tốc đi qua nút an toàn. Footprint phải bao gồm robot và xe đẩy.
[85] Tọa độ endpoint dùng {map_id, x_m, y_m, yaw_rad}. Pi đổi yaw_rad sang quaternion khi tạo PoseStamped trong frame map. ROS Humble trả result rỗng; dùng action status và feedback để xác định kết quả.
[86] ESCORT ở phạm vi này là dẫn đường tới đích với tốc độ giới hạn. Theo dõi người đi sau, docking và tự sạc chưa nằm trong hợp đồng này.
[87] Input ví dụ: {"endpoint_id": "EP_305", "map_id": "opd-map-v1", "max_speed_mps": 0.15, "mode": "ESCORT"}
[88] Output data ví dụ: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[89] Lỗi cần xử lý: MAP_NOT_COMMISSIONED, MAP_ASSET_MISMATCH, ENDPOINT_NOT_FOUND, NAV2_UNAVAILABLE, MAP_TF_NOT_READY, SENSORS_NOT_READY, SPEED_POLICY_EXCEEDED, NAV_GOAL_REJECTED, NAV_ABORTED.
[90] 2 Tương tác với người dùng
[91] INTERACT  |  Server điều phối màn hình hoặc TTS
[92] Server chọn nội dung theo prompt_key, đưa dữ liệu lên đúng màn hình hoặc thiết bị phát âm thanh và chờ kết quả. Với yêu cầu xác nhận, việc nhận được câu trả lời và nội dung câu trả lời là hai thông tin riêng.
[93] Input
[94] Kiểu
[95] Bắt buộc
[96] Ý nghĩa và giá trị
[97] action
[98] enum
[99] Có
[100] Hành động trong loại step. Giá trị: SPEAK, DISPLAY, CAPTURE_INPUT, CONFIRM.
[101] language
[102] string
[103] Có
[104] Ngôn ngữ backend/UI/TTS đã hỗ trợ. Giá trị: độ dài tối đa 2000.
[105] text
[106] string
[107] Không
[108] Nội dung tùy chọn thay template. Giá trị: độ dài tối đa 2000.
[109] prompt_key
[110] string
[111] Có
[112] Mã nội dung hoặc biểu mẫu trên server. Bắt buộc kể cả khi có text. Giá trị: độ dài tối đa 2000.
[113] data
[114] object
[115] Có
[116] Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới.
[117] Output trong data
[118] Kiểu
[119] Ý nghĩa
[120] completed
[121] boolean
[122] Tác vụ đã hoàn tất theo adapter. Với CONFIRM, còn phải đọc confirmed để biết người dùng đồng ý hay từ chối.
[123] confirmed
[124] boolean hoặc null
[125] true là đồng ý, false là từ chối; null khi thao tác không hỏi xác nhận.
[126] input_data
[127] object
[128] Dữ liệu người dùng cung cấp trong tương tác.
[129] actor_user_id
[130] string hoặc null
[131] Mã người thao tác do server xác thực. Tương tác không yêu cầu danh tính có thể trả null.
[132] CONFIRM phải trả confirmed là true hoặc false. SPEAK và DISPLAY trả confirmed=null. CAPTURE_INPUT trả dữ liệu trong input_data theo schema của prompt.
[133] Các prompt xác nhận đóng cửa hoặc kiểm khóa phải có actor_user_id của nhân viên đã đăng nhập. SPEAK chỉ completed=true khi phát xong; việc đưa text vào hàng đợi chưa đủ.
[134] data là JSONB động nhưng phải được kiểm theo prompt_key. Không gửi một object tùy ý rồi để UI đoán cách hiển thị.
[135] prompt_key
[136] data cần truyền
[137] CONFIRM_DESTINATION
[138] {endpoint_id:string}
[139] ARRIVED_DESTINATION
[140] {endpoint_id:string} lấy từ output MOVE.
[141] INFORMATION_ANSWER
[142] {answer:string} lấy từ DATA_SERVICE.
[143] STAFF_CLOSE_DOOR_AND_CLEAR_MECHANISM
[144] {delivery_id:string, stage:SENDER|RECEIVER}
[145] STAFF_PHYSICALLY_VERIFY_LOCK
[146] {delivery_id:string, stage:SENDER|RECEIVER}
[147] STAFF_ON_THE_WAY
[148] {incident_id:string}; chỉ dùng sau khi cảnh báo đã được tiếp nhận.
[149] RESTRICTED_AREA_WARNING
[150] {zone_id:string}
[151] Input ví dụ: {"action": "CONFIRM", "data": {"endpoint_id": "EP_305"}, "language": "vi-VN", "prompt_key": "CONFIRM_DESTINATION"}
[152] Output data ví dụ: {"actor_user_id": "USER_01", "completed": true, "confirmed": true, "input_data": {}}
[153] Lỗi cần xử lý: INTERACTION_NOT_COMPLETED, STAFF_ACTOR_REQUIRED, SERVER_UNAVAILABLE, STEP_TIMEOUT.
[154] 3 Gọi service
[155] DATA_SERVICE  |  Server
[156] Bước này gọi một dịch vụ nghiệp vụ đã đăng ký trên server. Dịch vụ có thể tra cứu thông tin bệnh viện hoặc tạo hồ sơ sự cố. Workflow truyền dữ liệu yêu cầu, còn địa chỉ dịch vụ và thông tin truy cập do server quản lý.
[157] Input
[158] Kiểu
[159] Bắt buộc
[160] Ý nghĩa và giá trị
[161] service_key
[162] string
[163] Có
[164] Khóa registry backend. Giá trị: độ dài tối đa 2000.
[165] operation
[166] enum
[167] Có
[168] Thao tác được service registry cho phép. Server phải kiểm cả service_key và resource. Giá trị: QUERY, CREATE, UPDATE.
[169] resource
[170] string
[171] Có
[172] Tài nguyên nghiệp vụ trong registry. Giá trị: độ dài tối đa 2000.
[173] request
[174] object
[175] Có
[176] Dữ liệu gửi dịch vụ nghiệp vụ, đã thay các tham chiếu bằng giá trị thực.
[177] Output trong data
[178] Kiểu
[179] Ý nghĩa
[180] data
[181] object
[182] Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới.
[183] request_id
[184] string
[185] Mã yêu cầu tại dịch vụ nghiệp vụ; dùng để đối chiếu giao dịch. độ dài tối đa 2000.
[186] Đầu ra của loại này có một field data riêng. Vì vậy đường dẫn tới mã sự cố là /outputs/CREATE/data/incident_id; đường dẫn tới câu trả lời là /outputs/QUERY/data/answer.
[187] Server chỉ cho phép các tổ hợp service_key, operation, resource đã đăng ký. Không nhận SQL, URL hay mã Python từ tham số workflow.
[188] CREATE hoặc UPDATE phải chống ghi trùng theo operation_key. Việc tạo incident từ detector còn cần khóa duy nhất hoặc cơ chế upsert theo detection_event_id và loại sự cố, để hai job không tạo hai incident cho cùng một lần phát hiện.
[189] Dịch vụ
[190] request
[191] data trả lại
[192] HOSPITAL_INFORMATION / QUERY / answer
[193] {question:string}
[194] {answer:string}; thiếu câu trả lời hợp lệ thì trả lỗi.
[195] INCIDENT / CREATE / incidents
[196] {detection_event_id:string, type:"SUSPECTED_FALL"}
[197] {incident_id:string}; trả lại mã cũ nếu cùng sự kiện đã được ghi nhận.
[198] Input ví dụ: {"operation": "QUERY", "request": {"question": "Quầy thanh toán ở đâu?"}, "resource": "answer", "service_key": "HOSPITAL_INFORMATION"}
[199] Output data ví dụ: {"data": {"answer": "Quầy thanh toán ở tầng 1, cạnh sảnh chính."}, "request_id": "REQ_QUERY"}
[200] Lỗi cần xử lý: SERVICE_FAILED, INVALID_SERVICE_RESPONSE, SCHEMA_INVALID, SERVER_UNAVAILABLE. Backend cần bổ sung mã nghiệp vụ cho service chưa đăng ký hoặc request không hợp lệ.
[201] 4 Xác thực 
[202] AUTHENTICATE  |  Server
[203] Server kiểm danh tính, quyền và mục đích của lần xác thực. Kết quả thành công tạo một mã verification_id có hạn dùng. Pi lưu mã này trong bộ nhớ của job và kiểm lại trước khi mở khóa.
[204] Input
[205] Kiểu
[206] Bắt buộc
[207] Ý nghĩa và giá trị
[208] method
[209] enum
[210] Có
[211] QR_CODE/STAFF_CONFIRMATION; không có FACE_ID trong bản này. Giá trị: QR_CODE, STAFF_CONFIRMATION.
[212] required_permission
[213] string
[214] Có
[215] Quyền backend phải kiểm. Giá trị: độ dài tối đa 2000.
[216] purpose
[217] string
[218] Có
[219] Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. Giá trị: độ dài tối đa 2000.
[220] Output trong data
[221] Kiểu
[222] Ý nghĩa
[223] authenticated
[224] boolean
[225] Backend đã kiểm danh tính/quyền/phạm vi.
[226] verification_id
[227] string
[228] Mã lần xác thực; mở khóa phải dùng kết quả cùng job, đúng purpose và còn hạn. độ dài tối đa 2000.
[229] actor_user_id
[230] string
[231] Mã nhân viên đã được server xác thực; bắt buộc là string và không được null. độ dài tối đa 2000.
[232] valid_until
[233] string
[234] Thời điểm hết hạn xác thực, ISO 8601 có múi giờ UTC. date-time.
[235] purpose
[236] string
[237] Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. độ dài tối đa 2000.
[238] QR_CODE cần bộ đọc QR hoặc giao diện nhập đã tích hợp. STAFF_CONFIRMATION dùng tài khoản nhân viên có quyền. Phát hiện khuôn mặt đơn thuần không thay thế xác thực danh tính.
[239] required_permission kiểm quyền; purpose xác định phạm vi sử dụng. Mã xác thực của người gửi không được dùng làm mã xác thực người nhận.
[240] Backend trả SERVICE_RESPONSE ok=false, error_code=AUTH_DENIED nếu bị từ chối hoặc không xác định được người dùng. Không tạo actor giả để điền cho đủ schema. Một kết quả authenticated=false có cấu trúc hợp lệ vẫn phải đi qua nhánh từ chối.
[241] Input ví dụ: {"method": "QR_CODE", "purpose": "DELIVERY_SENDER", "required_permission": "DELIVERY_SEND"}
[242] Output data ví dụ: {"actor_user_id": "STAFF_01", "authenticated": true, "purpose": "DELIVERY_SENDER", "valid_until": "2026-09-19T08:02:00Z", "verification_id": "VER_AUTH_SENDER"}
[243] Lỗi cần xử lý: AUTH_DENIED ở backend; INVALID_AUTH_RESPONSE, AUTH_EXPIRED_OR_WRONG_SCOPE tại adapter; STEP_TIMEOUT.
[244]  5 Điều khiển chốt khóa xe đẩy
[245] CONTROL_HARDWARE  |  Pi gửi ROS tới RRC Lite
[246] Cơ cấu đang dùng là MG996R tại cổng S6 của RRC Lite, tương ứng ROS ID 4 trong cấu hình đã thử. Servo kéo hoặc đẩy chốt khóa cửa xe đẩy. Người dùng đã thử position [1500] để đóng khóa và position [0] để rút khóa.
[247] Input
[248] Kiểu
[249] Bắt buộc
[250] Ý nghĩa và giá trị
[251] device_id
[252] enum
[253] Có
[254] cargo_lock, ánh xạ servo MG996R S6 ID4 ở local. Giá trị: cargo_lock.
[255] action
[256] enum
[257] Có
[258] Hành động trong loại step. Giá trị: LOCK, UNLOCK.
[259] verification_mode
[260] enum
[261] Có
[262] COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. Giá trị: COMMAND_ONLY.
[263] verification_id
[264] string
[265] Khi UNLOCK
[266] Mã lần xác thực; mở khóa phải dùng kết quả cùng job, đúng purpose và còn hạn. Giá trị: độ dài tối đa 2000.
[267] purpose
[268] string
[269] Khi UNLOCK
[270] Mục đích xác thực, ví dụ DELIVERY_SENDER hoặc DELIVERY_RECEIVER. Giá trị: độ dài tối đa 2000.
[271] Output trong data
[272] Kiểu
[273] Ý nghĩa
[274] published
[275] hằng true
[276] Đã gửi thông điệp ROS tới topic điều khiển.
[277] controller_ack
[278] hằng false
[279] false: driver hiện tại chưa cung cấp phản hồi xác nhận lệnh PWM.
[280] verified
[281] hằng false
[282] false: chưa có cảm biến xác nhận cửa hoặc chốt khóa.
[283] commanded_state
[284] enum
[285] LOCK/UNLOCK được yêu cầu, không phải trạng thái vật lý đo được. LOCK, UNLOCK.
[286] position_raw
[287] integer
[288] Giá trị gửi nguyên trạng cho driver: LOCK là 1500; UNLOCK là 0. Không dùng làm số đo góc. tối thiểu 0; tối đa 2500.
[289] ros_servo_id
[290] hằng 4
[291] 4 tương ứng S6 trong image/source đã đối chiếu.
[292] UNLOCK bắt buộc có verification_id và purpose. Pi chỉ dùng grant đã nhận từ AUTHENTICATE trong cùng job, còn hạn và đúng mục đích. LOCK không cần hai trường này.
[293] Pi phải xác nhận robot đã dừng từ odometry còn mới trước khi gửi servo. Trong workflow giao nhận, nhân viên đóng cửa và xác nhận vùng cơ cấu đã trống trước lệnh LOCK; sau đó kiểm chốt thực tế trước khi xe chạy.
[294] position_raw là giá trị lệnh, không phải góc hoặc phản hồi vị trí. Giữ nguyên 0 theo thử nghiệm của người dùng; không tự thay bằng 500 hay suy ra góc 0 độ.
[295] published=true, controller_ack=false, verified=false chỉ xác nhận đã gửi lệnh. UI phải hiển thị riêng xác nhận của nhân viên. Không tự gửi LOCK khi job lỗi hoặc mất mạng.
[296] Topic /ros_robot_controller/pwm_servo/set_state
[297] Type ros_robot_controller_msgs/msg/SetPWMServoState
[298] LOCK   {duration: 1.0, state: [{id: [4], position: [1500], offset: []}]}
[299] UNLOCK {duration: 1.0, state: [{id: [4], position: [0], offset: []}]}
[300] Server gửi action=LOCK hoặc UNLOCK. Pi lấy ros_id, position_raw và duration_sec từ cấu hình đã duyệt. Không cho form tạo job nhập PWM tùy ý.
[301] Input ví dụ: {"action": "UNLOCK", "device_id": "cargo_lock", "purpose": "DELIVERY_SENDER", "verification_id": "VER_AUTH_SENDER", "verification_mode": "COMMAND_ONLY"}
[302] Output data ví dụ: {"commanded_state": "UNLOCK", "controller_ack": false, "position_raw": 0, "published": true, "ros_servo_id": 4, "verified": false}
[303] Lỗi cần xử lý: AUTH_GRANT_REQUIRED, AUTH_GRANT_EXPIRED, SERVO_NOT_COMMISSIONED, ROBOT_NOT_CONFIRMED_STATIONARY, PWM_DRIVER_UNAVAILABLE, INVALID_SERVO_PROFILE.
[304]  6 Xác nhận giao nhận vật phẩm
[305] HANDLE_PAYLOAD  |  Server và nhân viên
[306] Nhân viên đối chiếu vật phẩm và số lượng trên biểu mẫu rồi xác nhận. Kết quả lưu người thực hiện và danh sách đã đối chiếu. Cơ cấu hiện tại không đo được khối lượng, nhiệt độ hay tình trạng niêm phong.
[307] Input
[308] Kiểu
[309] Bắt buộc
[310] Ý nghĩa và giá trị
[311] action
[312] enum
[313] Có
[314] Hành động trong loại step. Giá trị: LOAD, UNLOAD, VERIFY.
[315] delivery_id
[316] string
[317] Có
[318] Tham chiếu yêu cầu giao hàng. Giá trị: độ dài tối đa 2000.
[319] items
[320] array
[321] Có
[322] Mảng vật phẩm. Mỗi phần tử có item_code và quantity là số nguyên dương. Giá trị: ít nhất số phần tử 1.
[323] verification_mode
[324] enum
[325] Có
[326] COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. Giá trị: STAFF_CONFIRMED.
[327] Output trong data
[328] Kiểu
[329] Ý nghĩa
[330] confirmed
[331] boolean
[332] true khi nhân viên xác nhận vật phẩm và số lượng đúng; false khi từ chối xác nhận.
[333] verification_mode
[334] enum
[335] COMMAND_ONLY với servo; STAFF_CONFIRMED với kiểm hàng do nhân viên thực hiện. STAFF_CONFIRMED.
[336] actor_user_id
[337] string
[338] Mã nhân viên đã được server xác thực; bắt buộc là string và không được null. độ dài tối đa 2000.
[339] items
[340] array
[341] Mảng vật phẩm. Mỗi phần tử có item_code và quantity là số nguyên dương.
[342] weight_kg
[343] null
[344] null: chưa có cân.
[345] temperature_c
[346] null
[347] null: chưa có sensor nhiệt độ.
[348] items không rỗng. Mỗi phần tử gồm item_code:string và quantity:integer ≥ 1. Server so sánh danh sách xác nhận với danh sách dự kiến; sai khác phải báo PAYLOAD_MISMATCH hoặc trả confirmed=false.
[349] confirmed=true cần actor_user_id. LOAD, UNLOAD và VERIFY đều là xác nhận nghiệp vụ của nhân viên; không suy ra hàng đã nằm trong khoang từ việc servo đã quay.
[350] weight_kg và temperature_c bắt buộc hiện diện nhưng bằng null. Có thể bổ sung số đo sau khi có thiết bị và nâng hợp đồng dữ liệu.
[351] Input ví dụ: {"action": "LOAD", "delivery_id": "DEMO_DELIVERY", "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "verification_mode": "STAFF_CONFIRMED"}
[352] Output data ví dụ: {"actor_user_id": "STAFF_01", "confirmed": true, "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "temperature_c": null, "verification_mode": "STAFF_CONFIRMED", "weight_kg": null}
[353] Lỗi cần xử lý: PAYLOAD_MISMATCH, SCHEMA_INVALID, SERVER_UNAVAILABLE, STEP_TIMEOUT.
[354] 7 Đọc cảm biến và kết quả nhận biết
[355] EXECUTE_SENSING  |  Pi đọc cảm biến; server xử lý ảnh
[356] ROBOT_HEALTH và SIDE_CLEARANCE lấy dữ liệu trên Pi. CROWD_DENSITY và RESTRICTED_AREA_INTRUSION cần dịch vụ xử lý ảnh đã được tích hợp. US100 chỉ đo khoảng cách ở hai bên, không tạo số người hay kết luận có người xâm nhập.
[357] Input
[358] Kiểu
[359] Bắt buộc
[360] Ý nghĩa và giá trị
[361] task_type
[362] enum
[363] Có
[364] Loại đo/nhận biết; schema metrics phụ thuộc task_type. Giá trị: ROBOT_HEALTH, SIDE_CLEARANCE, CROWD_DENSITY, RESTRICTED_AREA_INTRUSION.
[365] zone_id
[366] string
[367] Tác vụ ảnh
[368] Mã vùng quan sát. Dịch vụ hình ảnh phải ánh xạ vùng này với camera và bản đồ. Giá trị: độ dài tối đa 2000.
[369] window_sec
[370] number
[371] Không
[372] Khoảng lấy mẫu cho tác vụ hình ảnh, đơn vị giây. ROBOT_HEALTH và SIDE_CLEARANCE trả snapshot gần nhất. Giá trị: tối thiểu 0; tối đa 30.
[373] Output trong data
[374] Kiểu
[375] Ý nghĩa
[376] task_type
[377] enum
[378] Loại đo/nhận biết; schema metrics phụ thuộc task_type. ROBOT_HEALTH, SIDE_CLEARANCE, CROWD_DENSITY, RESTRICTED_AREA_INTRUSION.
[379] metrics
[380] object
[381] Kết quả đo hoặc phân tích, có cấu trúc riêng theo task_type.
[382] captured_at
[383] string
[384] Thời điểm lấy mẫu hoặc tổng hợp kết quả. Độ mới từng cảm biến xem trong metrics. date-time.
[385] evidence_ref
[386] string hoặc null
[387] Tham chiếu chứng cứ; null nếu không lưu.
[388] Hai cảm biến US100 gắn cố định: trái TRIG BCM17, ECHO BCM18; phải TRIG BCM22, ECHO BCM23. range dùng mét; thông tin pin này nằm trong cấu hình Pi, không gửi lại trong mỗi job.
[389] CROWD_DENSITY và RESTRICTED_AREA_INTRUSION trong bộ workflow mặc định luôn gửi zone_id và window_sec=5. Backend phải yêu cầu zone hợp lệ và dữ liệu camera có thời điểm; không tự lấy một vùng mặc định khi thiếu.
[390] battery_raw giữ giá trị UInt16 từ driver; battery_percent=null cho tới khi xác minh đơn vị và cách quy đổi. sensors_ready chỉ phản ánh các điều kiện cảm biến đã kiểm, không chứng minh mọi dịch vụ của robot đều sẵn sàng.
[391] Mẫu không hợp lệ hoặc quá cũ phải có giá trị null hoặc freshness=false. Không thay dữ liệu thiếu bằng khoảng cách xa. Bộ an toàn đọc cảm biến liên tục, độc lập với bước EXECUTE_SENSING.
[392] task_type
[393] metrics bắt buộc
[394] Field bổ sung cần quản lý
[395] ROBOT_HEALTH
[396] sensors_ready:boolean; battery_raw:integer|null; battery_percent:null.
[397] Adapter hiện tại còn trả left_m, right_m; left_fresh, right_fresh; left_age_sec, right_age_sec; battery_age_sec và battery_unit.
[398] SIDE_CLEARANCE
[399] left_m:number|null; right_m:number|null; left_fresh:boolean; right_fresh:boolean.
[400] left_age_sec, right_age_sec là tuổi mẫu; dùng null khi chưa có mẫu.
[401] CROWD_DENSITY
[402] count:integer ≥ 0.
[403] zone_id, frame_timestamp, model_version và chất lượng kết quả nếu dịch vụ có hỗ trợ. Chưa có diện tích hiệu chuẩn thì chỉ ghi số người, không suy mật độ người/m².
[404] RESTRICTED_AREA_INTRUSION
[405] intrusion_detected:boolean.
[406] zone_id, count và danh sách đối tượng nếu dịch vụ có hỗ trợ. Không tự thêm danh tính người.
[407] Root output chỉ có task_type, metrics, captured_at và evidence_ref. metrics có thể mở rộng theo tác vụ nhưng backend phải đăng ký schema và quy tắc hiển thị cho phần mở rộng. evidence_ref là mã/đường dẫn chứng cứ do dịch vụ quản lý, không chứa ảnh base64 trong JobParam.
[408] Input ví dụ: {"task_type": "CROWD_DENSITY", "window_sec": 5, "zone_id": "ZONE_WAITING"}
[409] Output data ví dụ: {"captured_at": "2026-09-19T08:00:00Z", "evidence_ref": null, "metrics": {"count": 12}, "task_type": "CROWD_DENSITY"}
[410] Lỗi cần xử lý: SCHEMA_INVALID, SERVER_UNAVAILABLE, STEP_TIMEOUT. Dữ liệu cục bộ không sẵn sàng còn được thể hiện bằng freshness=false hoặc sensors_ready=false.
[411]  8 Gửi thông báo
[412] NOTIFY  |  Server
[413] Server tạo thông báo, xác định người nhận và trả kết quả giao thông báo. Khi require_ack bằng true, bước chỉ hoàn tất sau khi có người nhận xác nhận tiếp nhận trong thời hạn.
[414] Input
[415] Kiểu
[416] Bắt buộc
[417] Ý nghĩa và giá trị
[418] notification_type
[419] string
[420] Có
[421] Loại thông báo nghiệp vụ. Giá trị: độ dài tối đa 2000.
[422] severity
[423] enum
[424] Có
[425] INFO/WARNING/CRITICAL. Giá trị: INFO, WARNING, CRITICAL.
[426] recipients
[427] array
[428] Có
[429] Nhóm/người nhận backend resolve và kiểm quyền. Giá trị: ít nhất số phần tử 1.
[430] data
[431] object
[432] Có
[433] Đối tượng dữ liệu theo từng prompt hoặc nghiệp vụ; các cấu trúc cụ thể được liệt kê bên dưới.
[434] require_ack
[435] boolean
[436] Có
[437] Yêu cầu người nhận xác nhận.
[438] Output trong data
[439] Kiểu
[440] Ý nghĩa
[441] notification_id
[442] string
[443] Mã thông báo đã tạo trên server. độ dài tối đa 2000.
[444] delivered
[445] boolean
[446] Kết quả giao thông báo.
[447] acked
[448] boolean
[449] Người nhận đã xác nhận tiếp nhận; khác EVENT_ACK của đường truyền.
[450] data được kiểm theo notification_type. Các thông báo gắn với sự cố phải mang incident_id; thông báo giao nhận mang delivery_id; giám sát mang zone_id và thời điểm lấy mẫu.
[451] recipients chứa mã nhóm hoặc người dùng do server resolve và kiểm quyền. require_ack=true dùng cho các thông báo khẩn cần người tiếp nhận.
[452] Nếu delivered=false hoặc require_ack=true nhưng acked=false, adapter trả lỗi hoặc tiếp tục chờ đến timeout; không cho bước đi nhánh SUCCESS. EVENT_ACK chỉ có nghĩa server đã lưu một event.
[453] Nhóm thông báo
[454] data cần giữ
[455] Giao nhận
[456] delivery_id; khi hoàn tất thêm sender_user_id, receiver_user_id, items và staff_confirmed_lock.
[457] SOS và nghi té ngã
[458] incident_id nếu đã tạo; detection_event_id với nguồn detector; message hoặc sự kiện nhân viên tới. Nhánh tạo incident thất bại chỉ dùng detection_event_id.
[459] Giám sát
[460] zone_id, checkpoint_id; cảnh báo có metrics, captured_at, evidence_ref.
[461] Sạc và phục hồi
[462] charge_request_id hoặc recovery_request_id; robot_id hoặc endpoint_id liên quan.
[463] Khởi động
[464] health snapshot để UI biết điều kiện đã kiểm.
[465] Input ví dụ: {"data": {"delivery_id": "DEMO_DELIVERY", "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "receiver_user_id": "STAFF_02", "sender_user_id": "STAFF_01", "staff_confirmed_lock": true}, "notification_type": "DELIVERY_COMPLETED", "recipients": ["STAFF"], "require_ack": false, "severity": "INFO"}
[466] Output data ví dụ: {"acked": false, "delivered": true, "notification_id": "NTF_COMPLETE"}
[467] Lỗi cần xử lý: NOTIFICATION_NOT_COMPLETED, SERVER_UNAVAILABLE, STEP_TIMEOUT.
[468] 9 Chờ thời gian hoặc sự kiện
[469] WAIT  |  Pi chạy timer hoặc chờ sự kiện server
[470] DURATION dùng đồng hồ trên Pi. EVENT gửi yêu cầu chờ tới server và chỉ nhận sự kiện đúng loại, đúng mã nghiệp vụ và đúng phiên chờ. Mỗi bước đều có timeout để job không bị giữ vô hạn.
[471] Input
[472] Kiểu
[473] Bắt buộc
[474] Ý nghĩa và giá trị
[475] wait_type
[476] enum
[477] Có
[478] DURATION hoặc EVENT. Giá trị: DURATION, EVENT.
[479] duration_sec
[480] number
[481] Theo chế độ
[482] Thời gian chờ trong DURATION. Giá trị: tối thiểu 0; tối đa 3600.
[483] event_type
[484] string
[485] Theo chế độ
[486] Loại event chờ. Giá trị: độ dài tối đa 2000.
[487] correlation_key
[488] string
[489] Theo chế độ
[490] Mã nghiệp vụ ràng buộc sự kiện, ví dụ incident_id hoặc mã yêu cầu sạc. Giá trị: độ dài tối đa 2000.
[491] Output trong data
[492] Kiểu
[493] Ý nghĩa
[494] completed
[495] boolean
[496] Đã hết khoảng chờ DURATION hoặc đã nhận đúng sự kiện EVENT.
[497] wake_reason
[498] string
[499] Nguyên nhân kết thúc chờ. độ dài tối đa 2000.
[500] elapsed_sec
[501] number
[502] Thời gian chờ đo tại executor.
[503] event
[504] object hoặc null
[505] Event đúng type/correlation; null với DURATION.
[506] DURATION bắt buộc duration_sec trong [0,3600] và timeout_sec phải lớn hơn khoảng chờ. EVENT bắt buộc event_type và correlation_key. Không trộn tham số của hai chế độ trong cấu hình mặc định.
[507] Sự kiện server phải có event_id, type, correlation_key, job_id, actor_user_id và occurred_at. Các trường mở rộng này nằm trong event; server kiểm quyền người xác nhận và cửa sổ thời gian.
[508] Mã robot hoặc endpoint có thể được dùng nhiều lần. Vì vậy yêu cầu sạc và phục hồi dùng charge_request_id hoặc recovery_request_id riêng, kèm job_id, để tránh nhận nhầm sự kiện của job trước.
[509] Server lưu sự kiện đến sớm và đối chiếu đúng phiên. Nhận cùng event_id nhiều lần không tạo thêm lần xác nhận.
[510] {
[511]   "completed": true,
[512]   "wake_reason": "STAFF_ARRIVED",
[513]   "elapsed_sec": 42.5,
[514]   "event": {
[515]     "event_id": "EVT_001",
[516]     "type": "STAFF_ARRIVED",
[517]     "correlation_key": "INC_001",
[518]     "job_id": "JOB_001",
[519]     "actor_user_id": "STAFF_01",
[520]     "occurred_at": "2026-09-19T08:01:00Z"
[521]   }
[522] }
[523] Các mã JOB_001, EVT_001 và INC_001 trong ví dụ được rút gọn để đọc; API và bảng dùng UUID ở các cột định danh đã quy định.
[524] Input ví dụ: {"duration_sec": 5, "wait_type": "DURATION"}
[525] Output data ví dụ: {"completed": true, "elapsed_sec": 5, "event": null, "wake_reason": "DURATION"}
[526] Lỗi cần xử lý: EVENT_NOT_MATCHED, SERVER_UNAVAILABLE, STEP_TIMEOUT.
[527] 10 Kiểm tra điều kiện
[528] CHECK_CONDITION  |  Pi
[529] Pi đánh giá một biểu thức và trả về value là boolean. value bằng false vẫn là một lần đánh giá thành công; transitions quyết định bước tiếp theo hoặc trạng thái kết thúc.
[530] Input
[531] Kiểu
[532] Bắt buộc
[533] Ý nghĩa và giá trị
[534] expression
[535] expression
[536] Có
[537] Biểu thức SAFE_EXPR_V1. Dùng var, op, args; không nhận chuỗi mã lập trình.
[538] Output trong data
[539] Kiểu
[540] Ý nghĩa
[541] value
[542] boolean
[543] Kết quả boolean điều kiện.
[544] Các toán tử: eq, ne; gt, ge, lt, le; and, or, not; add. Dùng var để đọc đường dẫn. eq/ne không ép kiểu: số 1 không bằng chuỗi "1".
[545] Biểu thức của CHECK_CONDITION phải trả boolean. Không dùng eval, chuỗi JavaScript hoặc gắn tên JSON_LOGIC cho một cú pháp khác.
[546] Thiếu giá trị gây MISSING_VALUE; sai kiểu gây TYPE_MISMATCH hoặc CONDITION_NOT_BOOLEAN. Mỗi lần đánh giá tối đa 128 node.
[547] Input ví dụ: {"expression": {"var": "/outputs/CONFIRM/confirmed"}}
[548] Output data ví dụ: {"value": true}
[549] Lỗi cần xử lý: MISSING_VALUE, TYPE_MISMATCH, CONDITION_NOT_BOOLEAN, INVALID_OPERATOR, EXPRESSION_LIMIT.
[550] 11 Gán context
[551] SET_CONTEXT  |  Pi
[552] Bước này gán các biến dùng trong một job, chẳng hạn số lần đã đo hoặc kết quả xử lý sự cố. Chỉ vùng /vars được phép thay đổi. Đầu vào job và chính sách trên Pi giữ nguyên trong thời gian chạy.
[553] Input
[554] Kiểu
[555] Bắt buộc
[556] Ý nghĩa và giá trị
[557] assignments
[558] object
[559] Có
[560] Tên biến và giá trị hoặc biểu thức cần gán vào /vars. Mỗi biểu thức đọc trạng thái trước lần gán.
[561] Output trong data
[562] Kiểu
[563] Ý nghĩa
[564] changed_keys
[565] array
[566] Các biến vars đã sửa.
[567] values
[568] object
[569] Giá trị mới.
[570] Tên biến phải là định danh hợp lệ và không bắt đầu bằng dấu gạch dưới. Có thể gán string, number, boolean, null hoặc đọc object/array có sẵn bằng var.
[571] Không đặt object hoặc array literal tùy ý làm biểu thức. Muốn lấy một object đã có, dùng {"var":"/outputs/SENSE/metrics"}.
[572] Các assignment trong cùng bước được đánh giá từ trạng thái trước khi gán. Nếu biến B phụ thuộc giá trị mới của A, tách thành hai bước.
[573] Input ví dụ: {"assignments": {"loops": 0}}
[574] Output data ví dụ: {"changed_keys": ["loops"], "values": {"loops": 0}}
[575] Lỗi cần xử lý: INVALID_CONTEXT_KEY, MISSING_VALUE, INVALID_EXPRESSION, TYPE_MISMATCH, EXPRESSION_LIMIT.
[576] 6 Pi truyền dữ liệu lên server
[577] Bản tin
[578] Hướng
[579] Dữ liệu chính
[580] HELLO
[581] Pi → server
[582] robot_id, runtime_version, protocol_version, simulation, capabilities và các job cần đối soát.
[583] COMMAND
[584] Server → Pi
[585] command_id, robot_id, command, expires_at; job hoặc job_id.
[586] COMMAND_ACK
[587] Pi → server
[588] command_id, accepted, job_id, status hoặc error_code.
[589] SERVICE_REQUEST
[590] Pi → server
[591] request_id, step_type, params đã resolve; job_id, job_step_id, step_code, task_code, operation_key, attempt_no, timeout_sec.
[592] SERVICE_RESPONSE
[593] Server → Pi
[594] request_id, ok, data hoặc error_code; simulation=false với dịch vụ thật.
[595] EVENT
[596] Pi → server
[597] event_id, producer_session_id, sequence_no, job_id, event_type, occurred_at, data.
[598] EVENT_ACK
[599] Server → Pi
[600] event_id đã được lưu bền.
[601] HEARTBEAT
[602] Pi → server
[603] robot_id, active_job_id, telemetry có độ mới và trạng thái cảm biến.
[604] protocol_version của kênh truyền là 1. Hợp đồng input/output của 11 loại giữ schema_version=2. Graph trong tài liệu Workflow dùng version=3. Ba số này chỉ các lớp khác nhau; không được dùng thay cho nhau.
[605] COMMAND_ACK accepted=true xác nhận Pi đã nhận và chấp nhận job. Kết quả nghiệp vụ nằm ở STEP_RESULT và JOB_RESULT. Server lưu event và cập nhật JobStep/Job trong cùng transaction rồi mới gửi EVENT_ACK; event trùng event_id không chạy lại nghiệp vụ.
[606] {
[607]   "protocol_version": 1,
[608]   "type": "SERVICE_REQUEST",
[609]   "request_id": "REQ_AUTH_001",
[610]   "step_type": "AUTHENTICATE",
[611]   "params": {
[612]     "method": "QR_CODE",
[613]     "required_permission": "DELIVERY_SEND",
[614]     "purpose": "DELIVERY_SENDER"
[615]   },
[616]   "job_id": "JOB_001",
[617]   "job_step_id": "JS_002",
[618]   "step_code": "AUTH_SENDER",
[619]   "task_code": "PICKUP",
[620]   "visit_no": 1,
[621]   "operation_key": "JS_002",
[622]   "attempt_no": 1,
[623]   "timeout_sec": 120,
[624]   "simulation": false
[625] }
[626] {
[627]   "protocol_version": 1,
[628]   "type": "SERVICE_RESPONSE",
[629]   "request_id": "REQ_AUTH_001",
[630]   "ok": true,
[631]   "simulation": false,
[632]   "data": {
[633]     "authenticated": true,
[634]     "verification_id": "VER_AUTH_SENDER",
[635]     "actor_user_id": "STAFF_01",
[636]     "valid_until": "2026-09-19T08:02:00Z",
[637]     "purpose": "DELIVERY_SENDER"
[638]   }
[639] }
[640] Trình tự mở khóa: server trả grant xác thực → Pi lưu output AUTH_SENDER → CHECK_AUTH_SENDER đọc authenticated → UNLOCK_SENDER lấy verification_id → Pi kiểm grant và trạng thái dừng → gửi PWM → lưu STEP_RESULT → gửi EVENT lên server. Chỉ giá trị tham số đi qua các bước; server không gửi mã Python để Pi thực thi.
[641] 7 Trạng thái và dữ liệu hiển thị trên UI
[642] Thực thể hoặc kết quả
[643] Trạng thái và cách hiểu
[644] Workflow
[645] DRAFT → PUBLISHED → RETIRED. Bản đã công bố giữ nguyên; sửa tạo version mới.
[646] Job
[647] QUEUED ở server → ACCEPTED trên Pi → RUNNING → SUCCEEDED, FAILED, CANCELLED hoặc RECOVERY_REQUIRED. Lỗi, hủy hoặc gián đoạn có cơ cấu vật lý cần đối soát.
[648] JobTask
[649] PENDING → RUNNING → SUCCEEDED hoặc FAILED/CANCELLED. Nhóm không đi qua có thể hiển thị NOT_VISITED. Backend suy trạng thái từ graph thực tế.
[650] JobStep
[651] PENDING → RUNNING → SUCCEEDED, FAILED hoặc TIMED_OUT; CANCELLED khi job bị ngắt. RETRYING chỉ dùng khi còn attempt hợp lệ. WAITING là trạng thái hiển thị của bước đang chờ, không phải outcome riêng.
[652] Transition
[653] SUCCESS, FAILURE, TIMEOUT là kết quả để chọn nhánh, không phải toàn bộ tập trạng thái của bảng.
[654] Màn hình
[655] Nguồn dữ liệu
[656] Tạo yêu cầu
[657] WorkflowParam.validation_schema_json và ui_config_json sinh form; giá trị đã chọn lưu JobParam.
[658] Chi tiết job
[659] Job và JobParam thể hiện yêu cầu; JobTask và JobStep thể hiện bước đang chạy, lần ghé và kết quả.
[660] Lịch sử xử lý
[661] JobEvent giữ timeline, mã lỗi, người xác nhận và giá trị input/output thực tế.
[662] Khóa xe đẩy
[663] Hiện commanded_state và published; thêm xác nhận nhân viên từ VERIFY_SENDER/RECEIVER. Không hiển thị “cảm biến xác nhận đã khóa”.
[664] Giám sát trực tiếp
[665] HEARTBEAT và telemetry cung cấp vị trí, khoảng cách, độ mới. Dữ liệu chưa có hiển thị “chưa có dữ liệu”.
[666] 8 Cấu hình và giới hạn triển khai
[667] Catalog triển khai trên Pi gồm đúng 11 handler được phép sử dụng. Workflow mới dùng các handler này có thể thay tham số và graph bằng dữ liệu. Thêm một loại hành động mới vẫn cần code handler, schema và kiểm thử tương ứng.
[668] Quy trình công bố: sửa workflow ở trạng thái DRAFT → kiểm input, binding, graph, nhánh lỗi và quyền → tạo version cùng hash → xuất catalog → triển khai khi robot rảnh → khởi động lại bridge → dispatch đúng version và hash. Cơ chế tải nóng catalog qua mạng không thuộc giao thức hiện tại.
[669] Pi lưu journal SQLite cho command, job và event chưa ACK. Khi mất kết nối hoặc tiến trình khởi động lại với job dở dang, trạng thái chuyển RECOVERY_REQUIRED. Người vận hành kiểm robot, cửa và hàng trước khi đối soát; không phát lại một lệnh mở khóa hoặc di chuyển chỉ vì chưa nhận ACK.
[670] Lệnh điều phối hiện có DISPATCH_JOB và CANCEL_JOB. Chưa bật nút Pause hoặc Resume trên UI. Khi có SOS trong lúc robot bận, server gửi thông báo độc lập, xử lý job đang chạy và chỉ dispatch sau khi robot có thể nhận việc.
[671] Bộ an toàn phải chặn đường vận tốc tới motor, kiểm tuổi mẫu US100/LiDAR và thời hạn cho phép chuyển động. Cần chạy thử footprint, khoảng dừng và đường đi với cả xe đẩy. Hai cảm biến bên hông không bao phủ toàn bộ phía trước, phía sau và vùng quét khi quay.
[672] Giao tiếp Pi
[673] Kiểu dữ liệu
[674] Cách dùng trong đặc tả
[675] /navigate_to_pose
[676] nav2_msgs/action/NavigateToPose
[677] MOVE gửi PoseStamped trong frame map, lấy action status và feedback.
[678] /ros_robot_controller/pwm_servo/set_state
[679] ros_robot_controller_msgs/msg/SetPWMServoState
[680] CONTROL_HARDWARE gửi state[] và duration float64; phần tử có id uint16[], position uint16[], offset int16[].
[681] /ultrasonic/left/range và /ultrasonic/right/range
[682] sensor_msgs/msg/Range
[683] US100 trả range bằng mét. Driver cấu hình min_range=0.02, max_range=4.0; mất echo không tạo giá trị đo mới.
[684] /ros_robot_controller/battery
[685] std_msgs/msg/UInt16
[686] Giữ battery_raw và tuổi mẫu; chưa quy đổi thành phần trăm.
[687] /cmd_vel tới /mediassist/cmd_vel_safe
[688] geometry_msgs/msg/Twist
[689] Qua bộ an toàn trước khi tới driver đế. Workflow không gửi vận tốc motor trực tiếp.
[690] PART: word/footnotes.xml
[691] PART: word/endnotes.xml
[692] PART: word/footer1.xml
[693] 1
