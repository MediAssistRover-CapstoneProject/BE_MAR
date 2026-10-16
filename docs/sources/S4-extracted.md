# S4 — 03-MediAssistRover_10_Workflow_va_Luong_du_lieu_v3.docx

Nguồn: `C:\Users\Admin\Downloads\03-MediAssistRover_10_Workflow_va_Luong_du_lieu_v3.docx`
SHA-256: `DCD7EFC9FEF620EE251902F8A7212BBE1361C293A3BA4B274E3FDC1AD052E4DD`
Ngày đọc: 2026-10-07. Trích xuất văn bản; không phải bản render. Draw.io giữ ID và đầu mút cạnh; DOCX giữ thứ tự đoạn/ô, không bảo toàn bố cục hay hình nhúng.

[1] PART: word/document.xml
[2] Workflow mặc định MediAssistRover
[3] Mười luồng nghiệp vụ và dữ liệu truyền giữa các bước
[4] 1 Cách đọc luồng và biến
[5] Ký hiệu trong mô tả
[6] Ý nghĩa
[7] JSON khi triển khai
[8] input.x
[9] Giá trị JobParam đã chốt.
[10] {"$ref":"/input/x"}
[11] outputs.A.field
[12] Field trong data của bước A.
[13] {"$ref":"/outputs/A/field"}
[14] vars.x
[15] Biến được SET_CONTEXT gán.
[16] {"$ref":"/vars/x"}
[17] system.max_speed_mps
[18] Trần tốc độ cục bộ của Pi.
[19] {"$ref":"/system/max_speed_mps"}
[20] Biểu thức A ≥ B
[21] SAFE_EXPR_V1 dùng var/op/args.
[22] {"op":"ge","args":[{"var":"/input/confidence"},{"var":"/input/min_confidence"}]}
[23] Ví dụ “verification_id = outputs.AUTH_SENDER.verification_id → VER_AUTH_SENDER” nghĩa là bước nhận lấy giá trị VER_AUTH_SENDER do AUTH_SENDER vừa trả. Phần bên phải dấu mũi tên là giá trị minh họa; không được ghi cứng mã đó trong workflow.
[24] Bước sau có thể đọc JobParam ban đầu mà không cần bước trước chuyển tiếp lại toàn bộ dữ liệu. Output chỉ cần truyền khi bước sau thực sự sử dụng. Mọi output, kể cả output không có bước nào đọc tiếp, vẫn được lưu trong JobStep và JobEvent.
[25] Các ví dụ output dưới đây mô tả một lần chạy thành công. Với bước nằm ở nhánh lỗi, ví dụ chỉ minh họa phản hồi của chính bước xử lý lỗi. Thời điểm, mã thông báo và mã xác thực được tạo khi chạy thật; mỗi vòng lặp có job_step_id mới.
[26] 2 Quy tắc chung cho 10 workflow
[27] Mỗi bước dùng max_attempts=1, retry_delay_sec=0 và retry_codes=[]. Bước RETRY trong phục hồi điều hướng là một lần thử mới có chủ ý trong graph. Không tự phát lại lệnh servo hoặc tạo giao dịch lần nữa khi mất phản hồi.
[28] Pi là bên quyết định bước tiếp theo. SUCCESS chỉ nghĩa handler hoàn tất đúng hợp đồng. CHECK_CONDITION có thể SUCCESS với value=false; CONFIRM có thể completed=true với confirmed=false. Luồng phải xét các field đó trước khi cho robot hành động.
[29] Mỗi graph có start, giới hạn thời gian và số lần ghé bước. Server phải triển khai đúng catalog version/hash trên Pi trước dispatch. Khi mất mạng, reboot, hủy hoặc hết hạn cả job, robot dừng tác vụ và yêu cầu đối soát theo trạng thái RECOVERY_REQUIRED.
[30] Ở giao nhận, thất bại hoặc hủy được chuyển thành RECOVERY_REQUIRED để nhân viên kiểm cơ cấu. Với các workflow khác, nhánh nghiệp vụ có thể kết thúc FAILED hoặc CANCELLED như bảng chuyển bước. Dừng do an toàn có quyền chặn chuyển động ngay cả khi nghiệp vụ đang chạy.
[31] Số
[32] Mã workflow
[33] Chức năng
[34] 1
[35] WF_PATIENT_ESCORT
[36] Dẫn bệnh nhân tới điểm đến
[37] 2
[38] WF_INFORMATION_ASSISTANCE
[39] Tra cứu thông tin bệnh viện
[40] 3
[41] WF_SECURE_MEDICAL_DELIVERY
[42] Giao nhận vật phẩm bằng xe đẩy
[43] 4
[44] WF_CROWD_MONITORING
[45] Giám sát số người tại một khu vực
[46] 5
[47] WF_EMERGENCY_SOS_RESPONSE
[48] Tiếp nhận yêu cầu SOS
[49] 6
[50] WF_FALL_DETECTION_RESPONSE
[51] Xử lý sự kiện nghi té ngã
[52] 7
[53] WF_RESTRICTED_AREA_MONITORING
[54] Giám sát khu vực hạn chế
[55] 8
[56] WF_AUTO_CHARGING
[57] Yêu cầu nhân viên sạc robot
[58] 9
[59] WF_NAVIGATION_RECOVERY
[60] Phục hồi sau lỗi di chuyển
[61] 10
[62] WF_ROBOT_STARTUP_STANDBY
[63] Kiểm tra robot khi khởi động
[64] 3 Dẫn bệnh nhân tới điểm đến
[65] WF_PATIENT_ESCORT  |  version 3
[66] Bệnh nhân chạm vào màn hình hoặc kích hoạt phiên giọng nói bằng từ khóa đã cấu hình. Server nhận ý định dẫn đường, hỏi đủ điểm đến, tra Endpoint và tạo NavigationRequest hoặc EscortRequest theo chức năng UI. Chỉ sau khi có map_id và destination_endpoint_id hợp lệ mới tạo job.
[67] Robot chưa theo dõi người đi sau trong luồng này. Nếu cần phát hiện người bệnh dừng hoặc mất dấu, phải thêm dịch vụ nhận biết và quy tắc dừng riêng trước khi công bố tính năng.
[68] Bắt đầu tại CONFIRM. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: nav2, interaction, notification.
[69] Đầu vào của job
[70] Tham số
[71] Kiểu
[72] Giá trị ví dụ
[73] Nguồn và kiểm tra
[74] map_id
[75] string
[76] "opd-map-v1"
[77] Server lấy từ map đang áp dụng; không tự nhập tên file bản đồ.
[78] destination_endpoint_id
[79] string
[80] "EP_305"
[81] Người dùng chọn phòng/điểm đến; server tra Endpoint.
[82] Tất cả tham số được liệt kê đều bắt buộc. Endpoint phải thuộc map đã chọn; quyền và quan hệ nghiệp vụ được server kiểm trước khi tạo JobParam.
[83] Các bước và dữ liệu truyền tiếp
[84] Bước 1
[85] CONFIRM  |  INTERACT  |  Task MAIN  |  30 giây
[86] Xác nhận điểm đến.
[87] Nhận: action = "CONFIRM"; language = "vi-VN"; prompt_key = "CONFIRM_DESTINATION"; data = {endpoint_id: input.destination_endpoint_id → "EP_305"}.
[88] Trả về: {"actor_user_id": "USER_01", "completed": true, "confirmed": true, "input_data": {}}
[89] Dùng tiếp: DECIDE đọc confirmed.
[90] Chuyển bước: SUCCESS → DECIDE; FAILURE hoặc TIMEOUT → FAILED.
[91] Bước 2
[92] DECIDE  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[93] Kiểm tra outputs.CONFIRM.confirmed.
[94] Nhận: expression = outputs.CONFIRM.confirmed.
[95] Trả về: {"value": true}
[96] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[97] Chuyển bước: SUCCESS nếu result.value = true → MOVE; SUCCESS → CANCELLED; FAILURE hoặc TIMEOUT → FAILED.
[98] Bước 3
[99] MOVE  |  MOVE  |  Task MAIN  |  180 giây
[100] Di chuyển tới điểm đã chọn.
[101] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.destination_endpoint_id → "EP_305"; mode = "ESCORT"; max_speed_mps = system.max_speed_mps → 0.15.
[102] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[103] Dùng tiếp: ARRIVED đọc endpoint_id; kết quả cuối job đọc output này.
[104] Chuyển bước: SUCCESS → ARRIVED; FAILURE hoặc TIMEOUT → HELP.
[105] Bước 4
[106] ARRIVED  |  INTERACT  |  Task MAIN  |  30 giây
[107] Thông báo đã tới đích.
[108] Nhận: action = "SPEAK"; language = "vi-VN"; prompt_key = "ARRIVED_DESTINATION"; data = {endpoint_id: outputs.MOVE.endpoint_id → "EP_305"}.
[109] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": null, "input_data": {}}
[110] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[111] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[112] Bước 5
[113] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[114] Gửi thông báo NAVIGATION_FAILED.
[115] Nhận: notification_type = "NAVIGATION_FAILED"; severity = "WARNING"; recipients = ["STAFF"]; data = {endpoint_id: input.destination_endpoint_id}; require_ack = false.
[116] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": false}
[117] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[118] Chuyển bước: SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[119] Kết quả cuối job và UI
[120] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[121] Field kết quả
[122] Nguồn
[123] endpoint_id
[124] outputs.MOVE.endpoint_id
[125] reached
[126] outputs.MOVE.reached
[127] Ví dụ output: {"endpoint_id": "EP_305", "reached": true}
[128] 4 Tra cứu thông tin bệnh viện
[129] WF_INFORMATION_ASSISTANCE  |  version 3
[130] Người dùng mở chức năng hỏi thông tin hoặc nói từ khóa để bắt đầu phiên giọng nói. Server thu câu hỏi, xử lý ngôn ngữ rồi tạo job với question. Khả năng nhận dạng và phát tiếng Việt phải được tích hợp trước khi dùng kênh thoại.
[131] Câu trả lời lấy từ nguồn thông tin bệnh viện đã duyệt. Câu hỏi không có dữ liệu hoặc lỗi dịch vụ chuyển thành thông báo cần nhân viên hỗ trợ.
[132] Bắt đầu tại QUERY. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: interaction, data_service, notification.
[133] Đầu vào của job
[134] Tham số
[135] Kiểu
[136] Giá trị ví dụ
[137] Nguồn và kiểm tra
[138] question
[139] string
[140] "Quầy thanh toán ở đâu?"
[141] Nội dung nhập trên UI hoặc câu thoại đã nhận dạng và xác nhận.
[142] Tất cả tham số được liệt kê đều bắt buộc. Server kiểm định dạng, nguồn dữ liệu, quyền và quan hệ nghiệp vụ trước khi chốt giá trị trong JobParam.
[143] Các bước và dữ liệu truyền tiếp
[144] Bước 1
[145] QUERY  |  DATA_SERVICE  |  Task MAIN  |  30 giây
[146] Tra cứu câu trả lời.
[147] Nhận: service_key = "HOSPITAL_INFORMATION"; operation = "QUERY"; resource = "answer"; request = {question: input.question → "Quầy thanh toán ở đâu?"}.
[148] Trả về: {"data": {"answer": "Quầy thanh toán ở tầng 1, cạnh sảnh chính."}, "request_id": "REQ_QUERY"}
[149] Dùng tiếp: ANSWER đọc data.answer; kết quả cuối job đọc output này.
[150] Chuyển bước: SUCCESS → ANSWER; FAILURE hoặc TIMEOUT → HELP.
[151] Bước 2
[152] ANSWER  |  INTERACT  |  Task MAIN  |  30 giây
[153] Đọc hoặc hiển thị câu trả lời.
[154] Nhận: action = "SPEAK"; language = "vi-VN"; prompt_key = "INFORMATION_ANSWER"; data = {answer: outputs.QUERY.data.answer → "Quầy thanh toán ở tầng 1, cạnh sảnh chính."}.
[155] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": null, "input_data": {}}
[156] Dùng tiếp: kết quả cuối job đọc output này.
[157] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[158] Bước 3
[159] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[160] Gửi thông báo INFORMATION_UNAVAILABLE.
[161] Nhận: notification_type = "INFORMATION_UNAVAILABLE"; severity = "WARNING"; recipients = ["STAFF"]; data = {question: input.question}; require_ack = false.
[162] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": false}
[163] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[164] Chuyển bước: SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[165] Kết quả cuối job và UI
[166] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[167] Field kết quả
[168] Nguồn
[169] answer
[170] outputs.QUERY.data.answer
[171] presented
[172] outputs.ANSWER.completed
[173] Ví dụ output: {"answer": "Quầy thanh toán ở tầng 1, cạnh sảnh chính.", "presented": true}
[174] 5 Giao nhận vật phẩm bằng xe đẩy
[175] WF_SECURE_MEDICAL_DELIVERY  |  version 3
[176] Nhân viên tạo yêu cầu giao nhận trên UI, chọn điểm lấy, điểm giao và danh sách vật phẩm. Server lưu ServiceRequest, DeliveryRequest và DeliveryItem, kiểm quyền, sau đó chốt các giá trị thành JobParam. Robot nhận job khi rảnh và khi cơ cấu xe đẩy đã được nghiệm thu.
[177] Cả hai đầu giao nhận đều phải xác thực, đối chiếu hàng, đóng cửa và kiểm khóa. Xác nhận của nhân viên không được đổi thành verified=true của servo. Lỗi hoặc hủy trong workflow này dẫn tới RECOVERY_REQUIRED theo quy tắc hiện tại của runtime.
[178] Bắt đầu tại PICKUP. Thời gian toàn job: 3600 giây. Giới hạn 100 lượt ghé bước. Capability cần có: nav2, cargo_lock, identity, payload_staff, interaction, notification.
[179] Đầu vào của job
[180] Tham số
[181] Kiểu
[182] Giá trị ví dụ
[183] Nguồn và kiểm tra
[184] map_id
[185] string
[186] "opd-map-v1"
[187] Server lấy từ map đang áp dụng; không tự nhập tên file bản đồ.
[188] pickup_endpoint_id
[189] string
[190] "EP_PICKUP"
[191] Nhân viên chọn điểm lấy từ Endpoint thuộc map.
[192] delivery_endpoint_id
[193] string
[194] "EP_305"
[195] Nhân viên chọn điểm giao từ Endpoint thuộc map.
[196] delivery_id
[197] string
[198] "DEMO_DELIVERY"
[199] Server lấy từ DeliveryRequest đã lưu.
[200] items
[201] array
[202] [{"item_code": "DEMO_DOCUMENT", "quantity": 1}]
[203] Server chụp danh sách DeliveryItem; item_code:string, quantity:integer ≥ 1.
[204] Tất cả tham số được liệt kê đều bắt buộc. Endpoint phải thuộc map đã chọn; quyền và quan hệ nghiệp vụ được server kiểm trước khi tạo JobParam.
[205] Các bước và dữ liệu truyền tiếp
[206] Bước 1
[207] PICKUP  |  MOVE  |  Task PICKUP  |  180 giây
[208] Di chuyển tới điểm lấy hàng.
[209] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.pickup_endpoint_id → "EP_PICKUP"; mode = "NORMAL"; max_speed_mps = system.max_speed_mps → 0.15.
[210] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_PICKUP", "last_feedback_pose": null, "reached": true}
[211] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[212] Chuyển bước: SUCCESS → AUTH_SENDER; FAILURE hoặc TIMEOUT → HELP.
[213] Bước 2
[214] AUTH_SENDER  |  AUTHENTICATE  |  Task PICKUP  |  120 giây
[215] Xác thực người gửi.
[216] Nhận: method = "QR_CODE"; required_permission = "DELIVERY_SEND"; purpose = "DELIVERY_SENDER".
[217] Trả về: {"actor_user_id": "STAFF_01", "authenticated": true, "purpose": "DELIVERY_SENDER", "valid_until": "2026-09-19T08:02:00Z", "verification_id": "VER_AUTH_SENDER"}
[218] Dùng tiếp: CHECK_AUTH_SENDER đọc authenticated; UNLOCK_SENDER đọc verification_id; COMPLETE đọc actor_user_id.
[219] Chuyển bước: SUCCESS → CHECK_AUTH_SENDER; FAILURE hoặc TIMEOUT → HELP.
[220] Bước 3
[221] CHECK_AUTH_SENDER  |  CHECK_CONDITION  |  Task PICKUP  |  30 giây
[222] Kiểm tra outputs.AUTH_SENDER.authenticated.
[223] Nhận: expression = outputs.AUTH_SENDER.authenticated.
[224] Trả về: {"value": true}
[225] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[226] Chuyển bước: SUCCESS nếu result.value = true → UNLOCK_SENDER; SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[227] Bước 4
[228] UNLOCK_SENDER  |  CONTROL_HARDWARE  |  Task PICKUP  |  30 giây
[229] Rút chốt khóa.
[230] Nhận: device_id = "cargo_lock"; action = "UNLOCK"; verification_mode = "COMMAND_ONLY"; verification_id = outputs.AUTH_SENDER.verification_id → "VER_AUTH_SENDER"; purpose = "DELIVERY_SENDER".
[231] Trả về: {"commanded_state": "UNLOCK", "controller_ack": false, "position_raw": 0, "published": true, "ros_servo_id": 4, "verified": false}
[232] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[233] Chuyển bước: SUCCESS → PAYLOAD_SENDER; FAILURE hoặc TIMEOUT → HELP.
[234] Bước 5
[235] PAYLOAD_SENDER  |  HANDLE_PAYLOAD  |  Task PICKUP  |  180 giây
[236] Nhân viên xác nhận đã xếp hàng.
[237] Nhận: action = "LOAD"; delivery_id = input.delivery_id → "DEMO_DELIVERY"; items = input.items → [{"item_code": "DEMO_DOCUMENT", "quantity": 1}]; verification_mode = "STAFF_CONFIRMED".
[238] Trả về: {"actor_user_id": "STAFF_01", "confirmed": true, "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "temperature_c": null, "verification_mode": "STAFF_CONFIRMED", "weight_kg": null}
[239] Dùng tiếp: CHECK_PAYLOAD_SENDER đọc confirmed.
[240] Chuyển bước: SUCCESS → CHECK_PAYLOAD_SENDER; FAILURE hoặc TIMEOUT → HELP.
[241] Bước 6
[242] CHECK_PAYLOAD_SENDER  |  CHECK_CONDITION  |  Task PICKUP  |  30 giây
[243] Kiểm tra outputs.PAYLOAD_SENDER.confirmed.
[244] Nhận: expression = outputs.PAYLOAD_SENDER.confirmed.
[245] Trả về: {"value": true}
[246] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[247] Chuyển bước: SUCCESS nếu result.value = true → CLOSE_SENDER; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[248] Bước 7
[249] CLOSE_SENDER  |  INTERACT  |  Task PICKUP  |  120 giây
[250] Yêu cầu đóng cửa và kiểm vùng cơ cấu.
[251] Nhận: action = "CONFIRM"; language = "vi-VN"; prompt_key = "STAFF_CLOSE_DOOR_AND_CLEAR_MECHANISM"; data = {delivery_id: input.delivery_id → "DEMO_DELIVERY", stage: "SENDER"}.
[252] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": true, "input_data": {}}
[253] Dùng tiếp: CHECK_CLOSE_SENDER đọc confirmed.
[254] Chuyển bước: SUCCESS → CHECK_CLOSE_SENDER; FAILURE hoặc TIMEOUT → HELP.
[255] Bước 8
[256] CHECK_CLOSE_SENDER  |  CHECK_CONDITION  |  Task PICKUP  |  30 giây
[257] Kiểm tra outputs.CLOSE_SENDER.confirmed.
[258] Nhận: expression = outputs.CLOSE_SENDER.confirmed.
[259] Trả về: {"value": true}
[260] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[261] Chuyển bước: SUCCESS nếu result.value = true → LOCK_SENDER; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[262] Bước 9
[263] LOCK_SENDER  |  CONTROL_HARDWARE  |  Task PICKUP  |  30 giây
[264] Đẩy chốt về vị trí khóa.
[265] Nhận: device_id = "cargo_lock"; action = "LOCK"; verification_mode = "COMMAND_ONLY".
[266] Trả về: {"commanded_state": "LOCK", "controller_ack": false, "position_raw": 1500, "published": true, "ros_servo_id": 4, "verified": false}
[267] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[268] Chuyển bước: SUCCESS → VERIFY_SENDER; FAILURE hoặc TIMEOUT → HELP.
[269] Bước 10
[270] VERIFY_SENDER  |  INTERACT  |  Task PICKUP  |  120 giây
[271] Yêu cầu nhân viên kiểm tra chốt khóa.
[272] Nhận: action = "CONFIRM"; language = "vi-VN"; prompt_key = "STAFF_PHYSICALLY_VERIFY_LOCK"; data = {delivery_id: input.delivery_id → "DEMO_DELIVERY", stage: "SENDER"}.
[273] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": true, "input_data": {}}
[274] Dùng tiếp: CHECK_LOCK_SENDER đọc confirmed.
[275] Chuyển bước: SUCCESS → CHECK_LOCK_SENDER; FAILURE hoặc TIMEOUT → HELP.
[276] Bước 11
[277] CHECK_LOCK_SENDER  |  CHECK_CONDITION  |  Task PICKUP  |  30 giây
[278] Kiểm tra outputs.VERIFY_SENDER.confirmed.
[279] Nhận: expression = outputs.VERIFY_SENDER.confirmed.
[280] Trả về: {"value": true}
[281] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[282] Chuyển bước: SUCCESS nếu result.value = true → TRANSIT; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[283] Bước 12
[284] TRANSIT  |  MOVE  |  Task TRANSIT  |  180 giây
[285] Di chuyển tới điểm giao hàng.
[286] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.delivery_endpoint_id → "EP_305"; mode = "NORMAL"; max_speed_mps = system.max_speed_mps → 0.15.
[287] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[288] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[289] Chuyển bước: SUCCESS → AUTH_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[290] Bước 13
[291] AUTH_RECEIVER  |  AUTHENTICATE  |  Task HANDOVER  |  120 giây
[292] Xác thực người nhận.
[293] Nhận: method = "QR_CODE"; required_permission = "DELIVERY_RECEIVE"; purpose = "DELIVERY_RECEIVER".
[294] Trả về: {"actor_user_id": "STAFF_02", "authenticated": true, "purpose": "DELIVERY_RECEIVER", "valid_until": "2026-09-19T08:02:00Z", "verification_id": "VER_AUTH_RECEIVER"}
[295] Dùng tiếp: CHECK_AUTH_RECEIVER đọc authenticated; UNLOCK_RECEIVER đọc verification_id; COMPLETE đọc actor_user_id; kết quả cuối job đọc output này.
[296] Chuyển bước: SUCCESS → CHECK_AUTH_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[297] Bước 14
[298] CHECK_AUTH_RECEIVER  |  CHECK_CONDITION  |  Task HANDOVER  |  30 giây
[299] Kiểm tra outputs.AUTH_RECEIVER.authenticated.
[300] Nhận: expression = outputs.AUTH_RECEIVER.authenticated.
[301] Trả về: {"value": true}
[302] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[303] Chuyển bước: SUCCESS nếu result.value = true → UNLOCK_RECEIVER; SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[304] Bước 15
[305] UNLOCK_RECEIVER  |  CONTROL_HARDWARE  |  Task HANDOVER  |  30 giây
[306] Rút chốt khóa.
[307] Nhận: device_id = "cargo_lock"; action = "UNLOCK"; verification_mode = "COMMAND_ONLY"; verification_id = outputs.AUTH_RECEIVER.verification_id → "VER_AUTH_RECEIVER"; purpose = "DELIVERY_RECEIVER".
[308] Trả về: {"commanded_state": "UNLOCK", "controller_ack": false, "position_raw": 0, "published": true, "ros_servo_id": 4, "verified": false}
[309] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[310] Chuyển bước: SUCCESS → PAYLOAD_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[311] Bước 16
[312] PAYLOAD_RECEIVER  |  HANDLE_PAYLOAD  |  Task HANDOVER  |  180 giây
[313] Nhân viên xác nhận đã nhận hàng.
[314] Nhận: action = "UNLOAD"; delivery_id = input.delivery_id → "DEMO_DELIVERY"; items = input.items → [{"item_code": "DEMO_DOCUMENT", "quantity": 1}]; verification_mode = "STAFF_CONFIRMED".
[315] Trả về: {"actor_user_id": "STAFF_02", "confirmed": true, "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "temperature_c": null, "verification_mode": "STAFF_CONFIRMED", "weight_kg": null}
[316] Dùng tiếp: CHECK_PAYLOAD_RECEIVER đọc confirmed; COMPLETE đọc items; kết quả cuối job đọc output này.
[317] Chuyển bước: SUCCESS → CHECK_PAYLOAD_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[318] Bước 17
[319] CHECK_PAYLOAD_RECEIVER  |  CHECK_CONDITION  |  Task HANDOVER  |  30 giây
[320] Kiểm tra outputs.PAYLOAD_RECEIVER.confirmed.
[321] Nhận: expression = outputs.PAYLOAD_RECEIVER.confirmed.
[322] Trả về: {"value": true}
[323] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[324] Chuyển bước: SUCCESS nếu result.value = true → CLOSE_RECEIVER; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[325] Bước 18
[326] CLOSE_RECEIVER  |  INTERACT  |  Task HANDOVER  |  120 giây
[327] Yêu cầu đóng cửa và kiểm vùng cơ cấu.
[328] Nhận: action = "CONFIRM"; language = "vi-VN"; prompt_key = "STAFF_CLOSE_DOOR_AND_CLEAR_MECHANISM"; data = {delivery_id: input.delivery_id → "DEMO_DELIVERY", stage: "RECEIVER"}.
[329] Trả về: {"actor_user_id": "STAFF_02", "completed": true, "confirmed": true, "input_data": {}}
[330] Dùng tiếp: CHECK_CLOSE_RECEIVER đọc confirmed.
[331] Chuyển bước: SUCCESS → CHECK_CLOSE_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[332] Bước 19
[333] CHECK_CLOSE_RECEIVER  |  CHECK_CONDITION  |  Task HANDOVER  |  30 giây
[334] Kiểm tra outputs.CLOSE_RECEIVER.confirmed.
[335] Nhận: expression = outputs.CLOSE_RECEIVER.confirmed.
[336] Trả về: {"value": true}
[337] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[338] Chuyển bước: SUCCESS nếu result.value = true → LOCK_RECEIVER; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[339] Bước 20
[340] LOCK_RECEIVER  |  CONTROL_HARDWARE  |  Task HANDOVER  |  30 giây
[341] Đẩy chốt về vị trí khóa.
[342] Nhận: device_id = "cargo_lock"; action = "LOCK"; verification_mode = "COMMAND_ONLY".
[343] Trả về: {"commanded_state": "LOCK", "controller_ack": false, "position_raw": 1500, "published": true, "ros_servo_id": 4, "verified": false}
[344] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[345] Chuyển bước: SUCCESS → VERIFY_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[346] Bước 21
[347] VERIFY_RECEIVER  |  INTERACT  |  Task HANDOVER  |  120 giây
[348] Yêu cầu nhân viên kiểm tra chốt khóa.
[349] Nhận: action = "CONFIRM"; language = "vi-VN"; prompt_key = "STAFF_PHYSICALLY_VERIFY_LOCK"; data = {delivery_id: input.delivery_id → "DEMO_DELIVERY", stage: "RECEIVER"}.
[350] Trả về: {"actor_user_id": "STAFF_02", "completed": true, "confirmed": true, "input_data": {}}
[351] Dùng tiếp: CHECK_LOCK_RECEIVER đọc confirmed; COMPLETE đọc confirmed; kết quả cuối job đọc output này.
[352] Chuyển bước: SUCCESS → CHECK_LOCK_RECEIVER; FAILURE hoặc TIMEOUT → HELP.
[353] Bước 22
[354] CHECK_LOCK_RECEIVER  |  CHECK_CONDITION  |  Task HANDOVER  |  30 giây
[355] Kiểm tra outputs.VERIFY_RECEIVER.confirmed.
[356] Nhận: expression = outputs.VERIFY_RECEIVER.confirmed.
[357] Trả về: {"value": true}
[358] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[359] Chuyển bước: SUCCESS nếu result.value = true → COMPLETE; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[360] Bước 23
[361] COMPLETE  |  NOTIFY  |  Task FINALIZE  |  60 giây
[362] Gửi thông báo DELIVERY_COMPLETED.
[363] Nhận: notification_type = "DELIVERY_COMPLETED"; severity = "INFO"; recipients = ["STAFF"]; data = {delivery_id: input.delivery_id → "DEMO_DELIVERY", sender_user_id: outputs.AUTH_SENDER.actor_user_id → "STAFF_01", receiver_user_id: outputs.AUTH_RECEIVER.actor_user_id → "STAFF_02", items: outputs.PAYLOAD_RECEIVER.items → [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], staff_confirmed_lock: outputs.VERIFY_RECEIVER.confirmed → true}; require_ack = false.
[364] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_COMPLETE"}
[365] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[366] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[367] Bước 24
[368] HELP  |  NOTIFY  |  Task FINALIZE  |  60 giây
[369] Gửi thông báo DELIVERY_REQUIRES_STAFF.
[370] Nhận: notification_type = "DELIVERY_REQUIRES_STAFF"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {delivery_id: input.delivery_id}; require_ack = true.
[371] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": true}
[372] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[373] Chuyển bước: SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[374] Kết quả cuối job và UI
[375] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[376] Field kết quả
[377] Nguồn
[378] delivery_id
[379] input.delivery_id
[380] items
[381] outputs.PAYLOAD_RECEIVER.items
[382] receiver_user_id
[383] outputs.AUTH_RECEIVER.actor_user_id
[384] staff_confirmed_lock
[385] outputs.VERIFY_RECEIVER.confirmed
[386] lock_sensor_verified
[387] false
[388] Ví dụ output: {"delivery_id": "DEMO_DELIVERY", "items": [{"item_code": "DEMO_DOCUMENT", "quantity": 1}], "receiver_user_id": "STAFF_02", "staff_confirmed_lock": true, "lock_sensor_verified": false}
[389] UI giao nhận phải hiện người gửi, người nhận, vật phẩm, thời điểm xác nhận và xác nhận khóa bằng nhân viên. lock_sensor_verified=false được giữ nguyên kể cả staff_confirmed_lock=true.
[390] 6 Giám sát số người tại một khu vực
[391] WF_CROWD_MONITORING  |  version 3
[392] Lịch giám sát hoặc nhân viên tạo một lượt đo tại checkpoint. Server chọn endpoint thuộc map và zone tương ứng. Nếu chọn EndpointGroup, server giải nhóm thành các checkpoint cụ thể trước khi tạo từng job.
[393] max_loops giới hạn từ 1 đến 5. Sau mỗi lần đo đều tăng loops; vòng tiếp theo quay lại SENSE để lấy mẫu mới. Robot chỉ MOVE một lần tới checkpoint. Mọi mẫu đều lưu trong JobEvent; kết quả cuối job chỉ chứa mẫu cuối.
[394] Bắt đầu tại INIT. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: nav2, ai_crowd, notification.
[395] Đầu vào của job
[396] Tham số
[397] Kiểu
[398] Giá trị ví dụ
[399] Nguồn và kiểm tra
[400] map_id
[401] string
[402] "opd-map-v1"
[403] Server lấy từ map đang áp dụng; không tự nhập tên file bản đồ.
[404] checkpoint_id
[405] string
[406] "EP_305"
[407] Server chọn endpoint quan sát từ lịch hoặc nhóm EndpointGroup.
[408] zone_id
[409] string
[410] "ZONE_WAITING"
[411] Server lấy vùng giám sát gắn với checkpoint và camera.
[412] warning_count
[413] integer
[414] 10
[415] Ngưỡng số người do nhân viên quản trị cấu hình; integer ≥ 0.
[416] max_loops
[417] integer
[418] 2
[419] Số lần lấy mẫu trong một job, integer từ 1 đến 5.
[420] Tất cả tham số được liệt kê đều bắt buộc. Endpoint phải thuộc map đã chọn; quyền và quan hệ nghiệp vụ được server kiểm trước khi tạo JobParam.
[421] Các bước và dữ liệu truyền tiếp
[422] Bước 1
[423] INIT  |  SET_CONTEXT  |  Task MAIN  |  30 giây
[424] Gán loops = 0.
[425] Nhận: assignments = {loops: 0}.
[426] Trả về: {"changed_keys": ["loops"], "values": {"loops": 0}}
[427] Dùng tiếp: vars.loops được INCREMENT, LOOP, kết quả cuối job đọc.
[428] Chuyển bước: SUCCESS → MOVE; FAILURE hoặc TIMEOUT → FAILED.
[429] Bước 2
[430] MOVE  |  MOVE  |  Task MAIN  |  180 giây
[431] Di chuyển tới điểm đã chọn.
[432] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.checkpoint_id → "EP_305"; mode = "PATROL"; max_speed_mps = system.max_speed_mps → 0.15.
[433] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[434] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[435] Chuyển bước: SUCCESS → SENSE; FAILURE hoặc TIMEOUT → HELP.
[436] Bước 3
[437] SENSE  |  EXECUTE_SENSING  |  Task MAIN  |  30 giây
[438] Lấy số người trong vùng.
[439] Nhận: task_type = "CROWD_DENSITY"; zone_id = input.zone_id → "ZONE_WAITING"; window_sec = 5.
[440] Trả về: {"captured_at": "2026-09-19T08:00:00Z", "evidence_ref": null, "metrics": {"count": 12}, "task_type": "CROWD_DENSITY"}
[441] Dùng tiếp: CHECK đọc metrics.count; WARN đọc metrics, captured_at, evidence_ref; kết quả cuối job đọc output này.
[442] Chuyển bước: SUCCESS → CHECK; FAILURE hoặc TIMEOUT → HELP.
[443] Bước 4
[444] CHECK  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[445] Kiểm tra outputs.SENSE.metrics.count ≥ input.warning_count.
[446] Nhận: expression = outputs.SENSE.metrics.count ≥ input.warning_count.
[447] Trả về: {"value": true}
[448] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[449] Chuyển bước: SUCCESS nếu result.value → WARN; SUCCESS → INCREMENT; FAILURE hoặc TIMEOUT → FAILED.
[450] Bước 5
[451] WARN  |  NOTIFY  |  Task MAIN  |  60 giây
[452] Gửi thông báo CROWD_WARNING.
[453] Nhận: notification_type = "CROWD_WARNING"; severity = "WARNING"; recipients = ["STAFF"]; data = {metrics: outputs.SENSE.metrics → {"count": 12}, zone_id: input.zone_id → "ZONE_WAITING", checkpoint_id: input.checkpoint_id → "EP_305", captured_at: outputs.SENSE.captured_at → "2026-09-19T08:00:00Z", evidence_ref: outputs.SENSE.evidence_ref}; require_ack = false.
[454] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_WARN"}
[455] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[456] Chuyển bước: SUCCESS → INCREMENT; FAILURE hoặc TIMEOUT → FAILED.
[457] Bước 6
[458] INCREMENT  |  SET_CONTEXT  |  Task MAIN  |  30 giây
[459] Gán loops = vars.loops + 1.
[460] Nhận: assignments = {loops: vars.loops + 1}.
[461] Trả về: {"changed_keys": ["loops"], "values": {"loops": 1}}
[462] Dùng tiếp: vars.loops được INCREMENT, LOOP, kết quả cuối job đọc.
[463] Chuyển bước: SUCCESS → LOOP; FAILURE hoặc TIMEOUT → FAILED.
[464] Bước 7
[465] LOOP  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[466] Kiểm tra vars.loops < input.max_loops.
[467] Nhận: expression = vars.loops < input.max_loops.
[468] Trả về: {"value": true}
[469] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[470] Chuyển bước: SUCCESS nếu result.value → WAIT; SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[471] Bước 8
[472] WAIT  |  WAIT  |  Task MAIN  |  30 giây
[473] Chờ 5 giây.
[474] Nhận: wait_type = "DURATION"; duration_sec = 5.
[475] Trả về: {"completed": true, "elapsed_sec": 5, "event": null, "wake_reason": "DURATION"}
[476] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[477] Chuyển bước: SUCCESS → SENSE; FAILURE hoặc TIMEOUT → FAILED.
[478] Bước 9
[479] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[480] Gửi thông báo MONITORING_UNAVAILABLE.
[481] Nhận: notification_type = "MONITORING_UNAVAILABLE"; severity = "WARNING"; recipients = ["STAFF"]; data = {zone_id: input.zone_id, checkpoint_id: input.checkpoint_id}; require_ack = false.
[482] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": false}
[483] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[484] Chuyển bước: SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[485] Kết quả cuối job và UI
[486] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[487] Field kết quả
[488] Nguồn
[489] zone_id
[490] input.zone_id
[491] samples_taken
[492] vars.loops
[493] last_metrics
[494] outputs.SENSE.metrics
[495] captured_at
[496] outputs.SENSE.captured_at
[497] Ví dụ output: {"zone_id": "ZONE_WAITING", "samples_taken": 2, "last_metrics": {"count": 12}, "captured_at": "2026-09-19T08:00:00Z"}
[498] JobEvent giữ từng lần SENSE và INCREMENT. UI biểu đồ đọc toàn bộ mẫu theo occurred_at/captured_at; output.last_metrics chỉ phục vụ tóm tắt lần đo cuối.
[499] 7 Tiếp nhận yêu cầu SOS
[500] WF_EMERGENCY_SOS_RESPONSE  |  version 3
[501] Nút SOS trên UI hoặc chức năng nhận giọng nói đã xác định yêu cầu hỗ trợ tạo EmergencyIncident trên server trước khi dispatch. Khi robot đang bận, server gửi cảnh báo cho nhân viên ngay; việc xếp job lên robot không được trì hoãn cảnh báo khẩn.
[502] acked=true ở ALERT nghĩa là đã có người tiếp nhận thông báo. STAFF_ARRIVED là một sự kiện khác xác nhận có người tới. Lỗi phát tiếng không bỏ qua bước chờ nhân viên. ESCALATE giữ incident mở và yêu cầu xử lý tiếp.
[503] Bắt đầu tại ALERT. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: notification, interaction, event_wait.
[504] Đầu vào của job
[505] Tham số
[506] Kiểu
[507] Giá trị ví dụ
[508] Nguồn và kiểm tra
[509] incident_id
[510] string
[511] "INC_DEMO"
[512] Server tạo hoặc tra EmergencyIncident trước khi tạo job.
[513] message
[514] string
[515] "Cần staff hỗ trợ"
[516] Nội dung SOS từ UI hoặc phiên giọng nói, đã giới hạn độ dài.
[517] Tất cả tham số được liệt kê đều bắt buộc. Server kiểm định dạng, nguồn dữ liệu, quyền và quan hệ nghiệp vụ trước khi chốt giá trị trong JobParam.
[518] Các bước và dữ liệu truyền tiếp
[519] Bước 1
[520] ALERT  |  NOTIFY  |  Task MAIN  |  60 giây
[521] Gửi thông báo SOS.
[522] Nhận: notification_type = "SOS"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {incident_id: input.incident_id → "INC_DEMO", message: input.message → "Cần staff hỗ trợ"}; require_ack = true.
[523] Trả về: {"acked": true, "delivered": true, "notification_id": "NTF_ALERT"}
[524] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[525] Chuyển bước: SUCCESS → SPEAK; FAILURE hoặc TIMEOUT → ESCALATE.
[526] Bước 2
[527] SPEAK  |  INTERACT  |  Task MAIN  |  30 giây
[528] Thông báo đã có nhân viên tiếp nhận.
[529] Nhận: action = "SPEAK"; language = "vi-VN"; prompt_key = "STAFF_ON_THE_WAY"; data = {incident_id: input.incident_id → "INC_DEMO"}.
[530] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": null, "input_data": {}}
[531] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[532] Chuyển bước: SUCCESS → WAIT_STAFF; FAILURE hoặc TIMEOUT → WAIT_STAFF.
[533] Bước 3
[534] WAIT_STAFF  |  WAIT  |  Task MAIN  |  300 giây
[535] Chờ STAFF_ARRIVED.
[536] Nhận: wait_type = "EVENT"; event_type = "STAFF_ARRIVED"; correlation_key = input.incident_id → "INC_DEMO".
[537] Trả về: {"completed": true, "elapsed_sec": 42.5, "event": {"actor_user_id": "STAFF_01", "correlation_key": "INC_DEMO", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "STAFF_ARRIVED"}, "wake_reason": "STAFF_ARRIVED"}
[538] Dùng tiếp: kết quả cuối job đọc output này.
[539] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → ESCALATE.
[540] Bước 4
[541] ESCALATE  |  NOTIFY  |  Task MAIN  |  60 giây
[542] Gửi thông báo SOS_ESCALATION.
[543] Nhận: notification_type = "SOS_ESCALATION"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {incident_id: input.incident_id}; require_ack = true.
[544] Trả về: {"notification_id": "NTF_ESCALATE", "delivered": true, "acked": true}
[545] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[546] Chuyển bước: SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → RECOVERY_REQUIRED.
[547] Kết quả cuối job và UI
[548] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[549] Field kết quả
[550] Nguồn
[551] incident_id
[552] input.incident_id
[553] staff_arrival_event
[554] outputs.WAIT_STAFF.event
[555] Ví dụ output: {"incident_id": "INC_DEMO", "staff_arrival_event": {"type": "STAFF_ARRIVED", "correlation_key": "INC_DEMO", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "actor_user_id": "STAFF_01", "occurred_at": "2026-09-19T08:01:00Z"}}
[556] 8 Xử lý sự kiện nghi té ngã
[557] WF_FALL_DETECTION_RESPONSE  |  version 3
[558] Detector hình ảnh chạy nền tạo DetectionEvent. Server kiểm nguồn sự kiện, loại phát hiện, confidence và ngưỡng đã cấu hình rồi tạo job. Người bệnh không nhập confidence hoặc min_confidence. Workflow này xử lý thông báo và tiếp nhận sự cố; không chẩn đoán tình trạng bệnh nhân.
[559] Các bước ALERT, SPEAK và WAIT_STAFF nằm trong cùng job phát hiện ngã. Không tạo job con, không có child_job_id hoặc parent_job_id trong luồng. Nhánh confidence thấp kết thúc bình thường với accepted=false và incident_id=null.
[560] Bắt đầu tại CHECK. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: data_service, event_wait, interaction, notification.
[561] Đầu vào của job
[562] Tham số
[563] Kiểu
[564] Giá trị ví dụ
[565] Nguồn và kiểm tra
[566] detection_event_id
[567] string
[568] "DET_DEMO"
[569] Server lấy từ DetectionEvent đã xác thực nguồn.
[570] confidence
[571] number
[572] 0.91
[573] Giá trị detector của sự kiện, number trong [0,1].
[574] min_confidence
[575] number
[576] 0.85
[577] Ngưỡng chính sách đã chốt vào JobParam, number trong [0,1].
[578] Tất cả tham số được liệt kê đều bắt buộc. Server kiểm định dạng, nguồn dữ liệu, quyền và quan hệ nghiệp vụ trước khi chốt giá trị trong JobParam.
[579] Các bước và dữ liệu truyền tiếp
[580] Bước 1
[581] CHECK  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[582] Kiểm tra input.confidence ≥ input.min_confidence.
[583] Nhận: expression = input.confidence ≥ input.min_confidence.
[584] Trả về: {"value": true}
[585] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[586] Chuyển bước: SUCCESS nếu result.value = true → CREATE; SUCCESS → IGNORE; FAILURE hoặc TIMEOUT → FAILED.
[587] Bước 2
[588] CREATE  |  DATA_SERVICE  |  Task MAIN  |  30 giây
[589] Tạo hồ sơ sự cố.
[590] Nhận: service_key = "INCIDENT"; operation = "CREATE"; resource = "incidents"; request = {detection_event_id: input.detection_event_id → "DET_DEMO", type: "SUSPECTED_FALL"}.
[591] Trả về: {"data": {"incident_id": "INC_001"}, "request_id": "REQ_CREATE"}
[592] Dùng tiếp: ALERT đọc data.incident_id; SPEAK đọc data.incident_id; WAIT_STAFF đọc data.incident_id; DONE đọc data.incident_id; COMPLETE đọc data.incident_id; ESCALATE đọc data.incident_id.
[593] Chuyển bước: SUCCESS → ALERT; FAILURE hoặc TIMEOUT → HELP.
[594] Bước 3
[595] ALERT  |  NOTIFY  |  Task MAIN  |  60 giây
[596] Gửi thông báo SOS.
[597] Nhận: notification_type = "SOS"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {incident_id: outputs.CREATE.data.incident_id → "INC_001", detection_event_id: input.detection_event_id → "DET_DEMO", message: "Có sự kiện nghi té ngã cần nhân viên kiểm tra"}; require_ack = true.
[598] Trả về: {"acked": true, "delivered": true, "notification_id": "NTF_ALERT"}
[599] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[600] Chuyển bước: SUCCESS → SPEAK; FAILURE hoặc TIMEOUT → ESCALATE.
[601] Bước 4
[602] SPEAK  |  INTERACT  |  Task MAIN  |  30 giây
[603] Thông báo đã có nhân viên tiếp nhận.
[604] Nhận: action = "SPEAK"; language = "vi-VN"; prompt_key = "STAFF_ON_THE_WAY"; data = {incident_id: outputs.CREATE.data.incident_id → "INC_001"}.
[605] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": null, "input_data": {}}
[606] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[607] Chuyển bước: SUCCESS → WAIT_STAFF; FAILURE hoặc TIMEOUT → WAIT_STAFF.
[608] Bước 5
[609] WAIT_STAFF  |  WAIT  |  Task MAIN  |  300 giây
[610] Chờ STAFF_ARRIVED.
[611] Nhận: wait_type = "EVENT"; event_type = "STAFF_ARRIVED"; correlation_key = outputs.CREATE.data.incident_id → "INC_001".
[612] Trả về: {"completed": true, "elapsed_sec": 42.5, "event": {"actor_user_id": "STAFF_01", "correlation_key": "INC_001", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "STAFF_ARRIVED"}, "wake_reason": "STAFF_ARRIVED"}
[613] Dùng tiếp: DONE đọc event.
[614] Chuyển bước: SUCCESS → DONE; FAILURE hoặc TIMEOUT → ESCALATE.
[615] Bước 6
[616] DONE  |  NOTIFY  |  Task MAIN  |  60 giây
[617] Gửi thông báo FALL_RESPONSE_COMPLETED.
[618] Nhận: notification_type = "FALL_RESPONSE_COMPLETED"; severity = "INFO"; recipients = ["STAFF"]; data = {incident_id: outputs.CREATE.data.incident_id → "INC_001", staff_arrival_event: outputs.WAIT_STAFF.event → {"actor_user_id": "STAFF_01", "correlation_key": "INC_001", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "STAFF_ARRIVED"}}; require_ack = false.
[619] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_DONE"}
[620] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[621] Chuyển bước: SUCCESS → COMPLETE; FAILURE hoặc TIMEOUT → RECOVERY_REQUIRED.
[622] Bước 7
[623] COMPLETE  |  SET_CONTEXT  |  Task MAIN  |  30 giây
[624] Gán accepted = true, incident_id = outputs.CREATE.data.incident_id, staff_arrived = true.
[625] Nhận: assignments = {accepted: true, incident_id: outputs.CREATE.data.incident_id, staff_arrived: true}.
[626] Trả về: {"changed_keys": ["accepted", "incident_id", "staff_arrived"], "values": {"accepted": true, "incident_id": "INC_001", "staff_arrived": true}}
[627] Dùng tiếp: vars.accepted được kết quả cuối job đọc; vars.incident_id được kết quả cuối job đọc; vars.staff_arrived được kết quả cuối job đọc.
[628] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[629] Bước 8
[630] IGNORE  |  SET_CONTEXT  |  Task MAIN  |  30 giây
[631] Gán accepted = false, incident_id = null, staff_arrived = false.
[632] Nhận: assignments = {accepted: false, incident_id: null, staff_arrived: false}.
[633] Trả về: {"changed_keys": ["accepted", "incident_id", "staff_arrived"], "values": {"accepted": false, "incident_id": null, "staff_arrived": false}}
[634] Dùng tiếp: vars.accepted được kết quả cuối job đọc; vars.incident_id được kết quả cuối job đọc; vars.staff_arrived được kết quả cuối job đọc.
[635] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[636] Bước 9
[637] ESCALATE  |  NOTIFY  |  Task MAIN  |  60 giây
[638] Gửi thông báo FALL_RESPONSE_ESCALATION.
[639] Nhận: notification_type = "FALL_RESPONSE_ESCALATION"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {incident_id: outputs.CREATE.data.incident_id, detection_event_id: input.detection_event_id}; require_ack = true.
[640] Trả về: {"notification_id": "NTF_ESCALATE", "delivered": true, "acked": true}
[641] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[642] Chuyển bước: SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → RECOVERY_REQUIRED.
[643] Bước 10
[644] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[645] Gửi thông báo FALL_RESPONSE_FAILURE.
[646] Nhận: notification_type = "FALL_RESPONSE_FAILURE"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {detection_event_id: input.detection_event_id}; require_ack = true.
[647] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": true}
[648] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[649] Chuyển bước: SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → RECOVERY_REQUIRED.
[650] Kết quả cuối job và UI
[651] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[652] Field kết quả
[653] Nguồn
[654] detection_event_id
[655] input.detection_event_id
[656] accepted
[657] vars.accepted
[658] incident_id
[659] vars.incident_id
[660] staff_arrived
[661] vars.staff_arrived
[662] Ví dụ output: {"detection_event_id": "DET_DEMO", "accepted": true, "incident_id": "INC_001", "staff_arrived": true}
[663] Nhánh dưới ngưỡng: {"detection_event_id": "DET_DEMO", "accepted": false, "incident_id": null, "staff_arrived": false}
[664] UI hiển thị “sự kiện chưa đạt ngưỡng xử lý”, không hiển thị “đã hỗ trợ xong”. SUCCEEDED ở đây nghĩa workflow đã xử lý xong quyết định, không phải đã có nhân viên tới.
[665] 9 Giám sát khu vực hạn chế
[666] WF_RESTRICTED_AREA_MONITORING  |  version 3
[667] Lịch giám sát hoặc nhân viên yêu cầu kiểm tra một zone hạn chế. Server chọn checkpoint có góc quan sát phù hợp và kiểm cấu hình vùng camera. Mỗi job chỉ thực hiện một lượt kiểm tra hữu hạn.
[668] Không có xâm nhập thì CHECK cho kết thúc. Có xâm nhập thì WARN và ALERT chạy; nếu lời nhắc âm thanh lỗi, hệ thống vẫn gửi ALERT. Nhân viên tiếp nhận cảnh báo không đồng nghĩa người đó đã rời khỏi vùng.
[669] Bắt đầu tại MOVE. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: nav2, ai_intrusion, interaction, notification.
[670] Đầu vào của job
[671] Tham số
[672] Kiểu
[673] Giá trị ví dụ
[674] Nguồn và kiểm tra
[675] map_id
[676] string
[677] "opd-map-v1"
[678] Server lấy từ map đang áp dụng; không tự nhập tên file bản đồ.
[679] checkpoint_id
[680] string
[681] "EP_305"
[682] Server chọn endpoint quan sát từ lịch hoặc nhóm EndpointGroup.
[683] zone_id
[684] string
[685] "ZONE_RESTRICTED"
[686] Server lấy vùng giám sát gắn với checkpoint và camera.
[687] Tất cả tham số được liệt kê đều bắt buộc. Endpoint phải thuộc map đã chọn; quyền và quan hệ nghiệp vụ được server kiểm trước khi tạo JobParam.
[688] Các bước và dữ liệu truyền tiếp
[689] Bước 1
[690] MOVE  |  MOVE  |  Task MAIN  |  180 giây
[691] Di chuyển tới điểm đã chọn.
[692] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.checkpoint_id → "EP_305"; mode = "PATROL"; max_speed_mps = system.max_speed_mps → 0.15.
[693] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[694] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[695] Chuyển bước: SUCCESS → SENSE; FAILURE hoặc TIMEOUT → HELP.
[696] Bước 2
[697] SENSE  |  EXECUTE_SENSING  |  Task MAIN  |  30 giây
[698] Kiểm tra có người trong vùng hạn chế.
[699] Nhận: task_type = "RESTRICTED_AREA_INTRUSION"; zone_id = input.zone_id → "ZONE_RESTRICTED"; window_sec = 5.
[700] Trả về: {"captured_at": "2026-09-19T08:00:00Z", "evidence_ref": null, "metrics": {"intrusion_detected": true}, "task_type": "RESTRICTED_AREA_INTRUSION"}
[701] Dùng tiếp: CHECK đọc metrics.intrusion_detected; ALERT đọc metrics, captured_at, evidence_ref; kết quả cuối job đọc output này.
[702] Chuyển bước: SUCCESS → CHECK; FAILURE hoặc TIMEOUT → HELP.
[703] Bước 3
[704] CHECK  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[705] Kiểm tra outputs.SENSE.metrics.intrusion_detected.
[706] Nhận: expression = outputs.SENSE.metrics.intrusion_detected.
[707] Trả về: {"value": true}
[708] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[709] Chuyển bước: SUCCESS nếu result.value = true → WARN; SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[710] Bước 4
[711] WARN  |  INTERACT  |  Task MAIN  |  30 giây
[712] Nhắc người dùng rời khu vực hạn chế.
[713] Nhận: action = "SPEAK"; language = "vi-VN"; prompt_key = "RESTRICTED_AREA_WARNING"; data = {zone_id: input.zone_id → "ZONE_RESTRICTED"}.
[714] Trả về: {"actor_user_id": "STAFF_01", "completed": true, "confirmed": null, "input_data": {}}
[715] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[716] Chuyển bước: SUCCESS → ALERT; FAILURE hoặc TIMEOUT → ALERT.
[717] Bước 5
[718] ALERT  |  NOTIFY  |  Task MAIN  |  60 giây
[719] Gửi thông báo INTRUSION.
[720] Nhận: notification_type = "INTRUSION"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {zone_id: input.zone_id → "ZONE_RESTRICTED", checkpoint_id: input.checkpoint_id → "EP_305", metrics: outputs.SENSE.metrics → {"intrusion_detected": true}, captured_at: outputs.SENSE.captured_at → "2026-09-19T08:00:00Z", evidence_ref: outputs.SENSE.evidence_ref}; require_ack = true.
[721] Trả về: {"acked": true, "delivered": true, "notification_id": "NTF_ALERT"}
[722] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[723] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[724] Bước 6
[725] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[726] Gửi thông báo MONITORING_UNAVAILABLE.
[727] Nhận: notification_type = "MONITORING_UNAVAILABLE"; severity = "WARNING"; recipients = ["STAFF"]; data = {zone_id: input.zone_id, checkpoint_id: input.checkpoint_id}; require_ack = false.
[728] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": false}
[729] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[730] Chuyển bước: SUCCESS → FAILED; FAILURE hoặc TIMEOUT → FAILED.
[731] Kết quả cuối job và UI
[732] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[733] Field kết quả
[734] Nguồn
[735] zone_id
[736] input.zone_id
[737] intrusion_detected
[738] outputs.SENSE.metrics.intrusion_detected
[739] Ví dụ output: {"zone_id": "ZONE_RESTRICTED", "intrusion_detected": true}
[740] 10 Yêu cầu nhân viên sạc robot
[741] WF_AUTO_CHARGING  |  version 3
[742] Nhân viên yêu cầu sạc từ UI hoặc server tạo yêu cầu từ một nguồn trạng thái pin đã được xác minh. Server cấp charge_request_id riêng cho phiên sạc và gắn với robot_id. Tên mã WF_AUTO_CHARGING được giữ để đối chiếu tài liệu cũ; chức năng hiện tại là nhờ nhân viên sạc.
[743] Robot không tự docking và không xác nhận dòng điện sạc. Kết quả ghi rõ nhân viên báo đã hoàn tất sạc; battery_percent vẫn null. HEALTH chỉ lấy snapshot để nhân viên xem tiếp, không tự suy ra đã đầy pin.
[744] Bắt đầu tại REQUEST. Thời gian toàn job: 2100 giây. Giới hạn 100 lượt ghé bước. Capability cần có: notification, event_wait, sensors.
[745] Đầu vào của job
[746] Tham số
[747] Kiểu
[748] Giá trị ví dụ
[749] Nguồn và kiểm tra
[750] robot_id
[751] string
[752] "mentorpi-m1-01"
[753] Server chọn robot nhận yêu cầu; phải khớp robot trong COMMAND.
[754] charge_request_id
[755] string
[756] "CHARGE_001"
[757] Mã ServiceRequest của phiên yêu cầu sạc, không tái dùng cho phiên mới.
[758] Tất cả tham số được liệt kê đều bắt buộc. Server kiểm định dạng, nguồn dữ liệu, quyền và quan hệ nghiệp vụ trước khi chốt giá trị trong JobParam.
[759] Các bước và dữ liệu truyền tiếp
[760] Bước 1
[761] REQUEST  |  NOTIFY  |  Task MAIN  |  60 giây
[762] Gửi thông báo MANUAL_CHARGE_REQUIRED.
[763] Nhận: notification_type = "MANUAL_CHARGE_REQUIRED"; severity = "WARNING"; recipients = ["STAFF"]; data = {robot_id: input.robot_id → "mentorpi-m1-01", charge_request_id: input.charge_request_id → "CHARGE_001"}; require_ack = false.
[764] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_REQUEST"}
[765] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[766] Chuyển bước: SUCCESS → WAIT_STAFF; FAILURE hoặc TIMEOUT → FAILED.
[767] Bước 2
[768] WAIT_STAFF  |  WAIT  |  Task MAIN  |  1800 giây
[769] Chờ STAFF_CHARGE_COMPLETED.
[770] Nhận: wait_type = "EVENT"; event_type = "STAFF_CHARGE_COMPLETED"; correlation_key = input.charge_request_id → "CHARGE_001".
[771] Trả về: {"completed": true, "elapsed_sec": 42.5, "event": {"actor_user_id": "STAFF_01", "correlation_key": "CHARGE_001", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "STAFF_CHARGE_COMPLETED"}, "wake_reason": "STAFF_CHARGE_COMPLETED"}
[772] Dùng tiếp: DONE đọc event; kết quả cuối job đọc output này.
[773] Chuyển bước: SUCCESS → HEALTH; FAILURE hoặc TIMEOUT → FAILED.
[774] Bước 3
[775] HEALTH  |  EXECUTE_SENSING  |  Task MAIN  |  30 giây
[776] Lấy trạng thái cảm biến.
[777] Nhận: task_type = "ROBOT_HEALTH".
[778] Trả về: {"captured_at": "2026-09-19T08:00:00Z", "evidence_ref": null, "metrics": {"battery_percent": null, "battery_raw": 12000, "sensors_ready": true}, "task_type": "ROBOT_HEALTH"}
[779] Dùng tiếp: DONE đọc metrics; kết quả cuối job đọc output này.
[780] Chuyển bước: SUCCESS → DONE; FAILURE hoặc TIMEOUT → FAILED.
[781] Bước 4
[782] DONE  |  NOTIFY  |  Task MAIN  |  60 giây
[783] Gửi thông báo STAFF_REPORTED_CHARGE_COMPLETED.
[784] Nhận: notification_type = "STAFF_REPORTED_CHARGE_COMPLETED"; severity = "INFO"; recipients = ["STAFF"]; data = {robot_id: input.robot_id → "mentorpi-m1-01", charge_request_id: input.charge_request_id → "CHARGE_001", staff_event: outputs.WAIT_STAFF.event → {"actor_user_id": "STAFF_01", "correlation_key": "CHARGE_001", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "STAFF_CHARGE_COMPLETED"}, health: outputs.HEALTH.metrics → {"battery_percent": null, "battery_raw": 12000, "sensors_ready": true}}; require_ack = false.
[785] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_DONE"}
[786] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[787] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[788] Kết quả cuối job và UI
[789] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[790] Field kết quả
[791] Nguồn
[792] charge_request_id
[793] input.charge_request_id
[794] staff_event
[795] outputs.WAIT_STAFF.event
[796] health
[797] outputs.HEALTH.metrics
[798] automatic_charging
[799] false
[800] Ví dụ output: {"charge_request_id": "CHARGE_001", "staff_event": {"type": "STAFF_CHARGE_COMPLETED", "correlation_key": "CHARGE_001", "event_id": "EVT_WAIT_STAFF", "job_id": "JOB_001", "actor_user_id": "STAFF_01", "occurred_at": "2026-09-19T08:01:00Z"}, "health": {"sensors_ready": true, "battery_raw": 12000, "battery_percent": null}, "automatic_charging": false}
[801] UI dùng nhãn “nhân viên xác nhận hoàn tất sạc”. automatic_charging=false và battery_percent=null không được đổi thành trạng thái tự sạc thành công hoặc pin 100%.
[802] 11 Phục hồi sau lỗi di chuyển
[803] WF_NAVIGATION_RECOVERY  |  version 3
[804] Sau lỗi điều hướng, nhân viên kiểm tra robot và tạo yêu cầu thử lại. Server chốt đích cũ hoặc đích đã chọn lại, cấp recovery_request_id và dispatch khi job trước đã được đối soát. Đây là job mới; không tự gọi hoặc tự tiếp tục job cũ.
[805] Chỉ thử MOVE một lần sau PATH_CLEARED đúng yêu cầu. Không tự quay hoặc chạy lùi vì phía sau robot đang kéo xe đẩy. Nếu tiếp tục lỗi, HELP yêu cầu nhân viên xử lý.
[806] Bắt đầu tại ALERT. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: nav2, notification, event_wait.
[807] Đầu vào của job
[808] Tham số
[809] Kiểu
[810] Giá trị ví dụ
[811] Nguồn và kiểm tra
[812] map_id
[813] string
[814] "opd-map-v1"
[815] Server lấy từ map đang áp dụng; không tự nhập tên file bản đồ.
[816] destination_endpoint_id
[817] string
[818] "EP_305"
[819] Người dùng chọn phòng/điểm đến; server tra Endpoint.
[820] recovery_request_id
[821] string
[822] "RECOVERY_001"
[823] Mã ServiceRequest của phiên phục hồi, không tái dùng cho phiên mới.
[824] Tất cả tham số được liệt kê đều bắt buộc. Endpoint phải thuộc map đã chọn; quyền và quan hệ nghiệp vụ được server kiểm trước khi tạo JobParam.
[825] Các bước và dữ liệu truyền tiếp
[826] Bước 1
[827] ALERT  |  NOTIFY  |  Task MAIN  |  60 giây
[828] Gửi thông báo PATH_BLOCKED.
[829] Nhận: notification_type = "PATH_BLOCKED"; severity = "WARNING"; recipients = ["STAFF"]; data = {recovery_request_id: input.recovery_request_id → "RECOVERY_001", endpoint_id: input.destination_endpoint_id → "EP_305"}; require_ack = false.
[830] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_ALERT"}
[831] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[832] Chuyển bước: SUCCESS → WAIT; FAILURE hoặc TIMEOUT → FAILED.
[833] Bước 2
[834] WAIT  |  WAIT  |  Task MAIN  |  120 giây
[835] Chờ PATH_CLEARED.
[836] Nhận: wait_type = "EVENT"; event_type = "PATH_CLEARED"; correlation_key = input.recovery_request_id → "RECOVERY_001".
[837] Trả về: {"completed": true, "elapsed_sec": 42.5, "event": {"actor_user_id": "STAFF_01", "correlation_key": "RECOVERY_001", "event_id": "EVT_WAIT", "job_id": "JOB_001", "occurred_at": "2026-09-19T08:01:00Z", "type": "PATH_CLEARED"}, "wake_reason": "PATH_CLEARED"}
[838] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[839] Chuyển bước: SUCCESS → RETRY; FAILURE hoặc TIMEOUT → FAILED.
[840] Bước 3
[841] RETRY  |  MOVE  |  Task MAIN  |  180 giây
[842] Di chuyển tới điểm đã chọn.
[843] Nhận: map_id = input.map_id → "opd-map-v1"; endpoint_id = input.destination_endpoint_id → "EP_305"; mode = "NORMAL"; max_speed_mps = system.max_speed_mps → 0.15.
[844] Trả về: {"action_status": 4, "distance_remaining_m": 0.0, "endpoint_id": "EP_305", "last_feedback_pose": null, "reached": true}
[845] Dùng tiếp: kết quả cuối job đọc output này.
[846] Chuyển bước: SUCCESS → DONE; FAILURE hoặc TIMEOUT → HELP.
[847] Bước 4
[848] DONE  |  NOTIFY  |  Task MAIN  |  60 giây
[849] Gửi thông báo NAVIGATION_RECOVERED.
[850] Nhận: notification_type = "NAVIGATION_RECOVERED"; severity = "INFO"; recipients = ["STAFF"]; data = {recovery_request_id: input.recovery_request_id → "RECOVERY_001", endpoint_id: input.destination_endpoint_id → "EP_305"}; require_ack = false.
[851] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_DONE"}
[852] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[853] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[854] Bước 5
[855] HELP  |  NOTIFY  |  Task MAIN  |  60 giây
[856] Gửi thông báo NAVIGATION_REQUIRES_STAFF.
[857] Nhận: notification_type = "NAVIGATION_REQUIRES_STAFF"; severity = "CRITICAL"; recipients = ["STAFF"]; data = {recovery_request_id: input.recovery_request_id, endpoint_id: input.destination_endpoint_id}; require_ack = true.
[858] Trả về: {"notification_id": "NTF_HELP", "delivered": true, "acked": true}
[859] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[860] Chuyển bước: SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[861] Kết quả cuối job và UI
[862] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[863] Field kết quả
[864] Nguồn
[865] recovery_request_id
[866] input.recovery_request_id
[867] endpoint_id
[868] outputs.RETRY.endpoint_id
[869] reached
[870] outputs.RETRY.reached
[871] Ví dụ output: {"recovery_request_id": "RECOVERY_001", "endpoint_id": "EP_305", "reached": true}
[872] 12 Kiểm tra robot khi khởi động
[873] WF_ROBOT_STARTUP_STANDBY  |  version 3
[874] Sau khi các tiến trình cần thiết khởi động và Pi kết nối server, scheduler tạo một job kiểm tra ngắn. Bridge duy trì kết nối chờ việc sau khi job kết thúc. Không dùng một bước WAIT vô hạn để giữ robot ở trạng thái chờ.
[875] sensors_ready=true không đồng nghĩa mọi workflow đều chạy được. Scheduler còn phải kiểm capability của Nav2, TTS, nhận dạng, xác thực và cơ cấu trước từng job.
[876] Bắt đầu tại HEALTH. Thời gian toàn job: 1200 giây. Giới hạn 100 lượt ghé bước. Capability cần có: sensors, notification.
[877] Đầu vào của job
[878] input = {}. Job không nhận tham số từ người dùng. robot_id và các thông tin điều phối thuộc COMMAND/Job, không lặp lại trong input.
[879] HEALTH đọc cấu hình và dữ liệu cảm biến cục bộ đã khởi động.
[880] Các bước và dữ liệu truyền tiếp
[881] Bước 1
[882] HEALTH  |  EXECUTE_SENSING  |  Task MAIN  |  30 giây
[883] Lấy trạng thái cảm biến.
[884] Nhận: task_type = "ROBOT_HEALTH".
[885] Trả về: {"captured_at": "2026-09-19T08:00:00Z", "evidence_ref": null, "metrics": {"battery_percent": null, "battery_raw": 12000, "sensors_ready": true}, "task_type": "ROBOT_HEALTH"}
[886] Dùng tiếp: CHECK đọc metrics.sensors_ready; READY đọc metrics; kết quả cuối job đọc output này.
[887] Chuyển bước: SUCCESS → CHECK; FAILURE hoặc TIMEOUT → FAILED.
[888] Bước 2
[889] CHECK  |  CHECK_CONDITION  |  Task MAIN  |  30 giây
[890] Kiểm tra outputs.HEALTH.metrics.sensors_ready.
[891] Nhận: expression = outputs.HEALTH.metrics.sensors_ready.
[892] Trả về: {"value": true}
[893] Dùng tiếp: transition của bước hiện tại đọc result.value để chọn nhánh.
[894] Chuyển bước: SUCCESS nếu result.value = true → READY; SUCCESS → RECOVERY_REQUIRED; FAILURE hoặc TIMEOUT → FAILED.
[895] Bước 3
[896] READY  |  NOTIFY  |  Task MAIN  |  60 giây
[897] Gửi thông báo ROBOT_STARTUP_CHECK_PASSED.
[898] Nhận: notification_type = "ROBOT_STARTUP_CHECK_PASSED"; severity = "INFO"; recipients = ["STAFF"]; data = {health: outputs.HEALTH.metrics → {"battery_percent": null, "battery_raw": 12000, "sensors_ready": true}}; require_ack = false.
[899] Trả về: {"acked": false, "delivered": true, "notification_id": "NTF_READY"}
[900] Dùng tiếp: Lưu JobStep và JobEvent; bước sau chỉ cần trạng thái hoàn tất.
[901] Chuyển bước: SUCCESS → SUCCEEDED; FAILURE hoặc TIMEOUT → FAILED.
[902] Kết quả cuối job và UI
[903] Khi job SUCCEEDED, output được lấy theo các ánh xạ sau. Khi FAILED hoặc RECOVERY_REQUIRED, backend giữ lịch sử bước và mã lỗi; không dựng một output thành công từ dữ liệu còn thiếu.
[904] Field kết quả
[905] Nguồn
[906] sensors_ready
[907] outputs.HEALTH.metrics.sensors_ready
[908] health
[909] outputs.HEALTH.metrics
[910] Ví dụ output: {"sensors_ready": true, "health": {"sensors_ready": true, "battery_raw": 12000, "battery_percent": null}}
[911] 13 Ví dụ dữ liệu đi từ xác thực tới mở khóa
[912] Ví dụ này dùng các bước AUTH_SENDER, CHECK_AUTH_SENDER và UNLOCK_SENDER của workflow giao nhận. input.delivery_id là DL_001; thông tin người gửi chỉ có sau khi backend xác thực.
[913] Nguồn
[914] Đích nhận
[915] Giá trị minh họa
[916] AUTH_SENDER.authenticated
[917] CHECK_AUTH_SENDER.expression
[918] true
[919] CHECK_AUTH_SENDER.value
[920] transition SUCCESS của CHECK_AUTH_SENDER
[921] true → UNLOCK_SENDER
[922] AUTH_SENDER.verification_id
[923] UNLOCK_SENDER.params.verification_id
[924] VER_AUTH_SENDER
[925] AUTH_SENDER.purpose và valid_until
[926] Pi kiểm grant trước UNLOCK
[927] DELIVERY_SENDER; thời điểm hiện tại trước hạn.
[928] UNLOCK_SENDER.action
[929] Pi tra cấu hình servo
[930] UNLOCK → position [0], ID [4], S6.
[931] Kết quả publish của Pi
[932] JobStep và UI
[933] published=true; verified=false.
[934] input.delivery_id và input.items
[935] PAYLOAD_SENDER
[936] Mã đơn và danh sách vật phẩm đã chốt.
[937] verification_id không đi vào ROS. Driver chỉ nhận lệnh servo do Pi tạo sau khi kiểm grant. Danh tính và quyền được kiểm trên server; giới hạn cơ cấu và trạng thái robot được kiểm tại Pi.
[938] 14 Ví dụ dữ liệu sau khi bỏ bước gọi workflow
[939] Bước tạo dữ liệu
[940] Bước sử dụng
[941] Giá trị và mục đích
[942] JobParam.detection_event_id
[943] CREATE.request.detection_event_id
[944] DET_001 để tạo hoặc tra đúng sự cố.
[945] CREATE.data.incident_id
[946] ALERT.data.incident_id
[947] INC_001 để gắn cảnh báo với hồ sơ sự cố.
[948] CREATE.data.incident_id
[949] SPEAK.data.incident_id
[950] INC_001 để đồng bộ nội dung thông báo.
[951] CREATE.data.incident_id
[952] WAIT_STAFF.correlation_key
[953] INC_001 để chỉ chờ nhân viên tới đúng sự cố.
[954] WAIT_STAFF.event
[955] DONE.data.staff_arrival_event
[956] Sự kiện có actor_user_id, occurred_at và job_id.
[957] CREATE.data.incident_id
[958] COMPLETE.assignments.incident_id
[959] Gán vars.incident_id cho kết quả cuối job.
[960] ALERT, SPEAK, WAIT_STAFF và các nhánh lỗi dùng chung job_id với CHECK và CREATE. SOS vẫn có workflow độc lập cho yêu cầu từ nút SOS; hai workflow dùng các step tương tự nhưng không gọi lẫn nhau.
[961] 15 Quy tắc lưu dữ liệu nghiệp vụ
[962] Dữ liệu thực thi
[963] Bảng hoặc màn hình nghiệp vụ
[964] Input form
[965] WorkflowParam khai báo; JobParam chụp giá trị. Bảng ServiceRequest và subtype vẫn là nguồn nghiệp vụ chính.
[966] DATA_SERVICE tạo incident
[967] Server lưu EmergencyIncident và liên kết DetectionEvent; dùng idempotency để không tạo trùng.
[968] Kết quả crowd hoặc intrusion
[969] JobEvent giữ kết quả bước. Backend cập nhật CrowdDensityRecord, DetectionEvent hoặc Alert theo nghiệp vụ và chống trùng event_id.
[970] Xác thực và kiểm hàng
[971] IdentityVerification, audit và trạng thái DeliveryRequest được backend cập nhật theo người thực hiện và kết quả bước.
[972] Hoàn tất job
[973] Job.result_json lưu output_mapping; không dùng riêng status=SUCCEEDED để kết luận mọi nghiệp vụ đều đã xảy ra.
[974] Dữ liệu map trực tiếp
[975] RobotLocationHistory và telemetry; không ghi liên tục tọa độ vào JobParam.
[976] PART: word/footnotes.xml
[977] PART: word/endnotes.xml
[978] PART: word/footer1.xml
[979] 1
