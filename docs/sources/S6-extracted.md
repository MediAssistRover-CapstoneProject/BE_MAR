# S6 — Pasted text.txt

Nguồn: `C:\Users\Admin\.codex\attachments\7e8e06d3-f039-426e-a36d-c1232b88c500\Pasted text.txt`
SHA-256: `A69C2AECDE499B353D2492317993644F702BE3CFA0423F343B4A4AE843D0E2B0`
Ngày đọc: 2026-10-07. Trích xuất văn bản; không phải bản render. Draw.io giữ ID, tooltip/cardinality và đầu mút cạnh; DOCX giữ thứ tự đoạn/ô, không bảo toàn bố cục hay hình nhúng.

[1] 1	Hoàn thiện sơ đồ dữ liệu	Kiểm tra từng bảng, trường dữ liệu và đường liên kết trong ERD (sơ đồ dữ liệu). Chốt cách lưu tham số của quy trình và nhiệm vụ bằng JSON (dữ liệu có cấu trúc).
[2] 2	Chuẩn bị máy chủ và cơ sở dữ liệu	Thiết lập dự án Spring Boot, PostgreSQL và môi trường chạy chung. Làm đăng nhập, đăng xuất và cách trả lỗi để web, ứng dụng và robot cùng sử dụng.
[3] 3	Bàn giao cấu hình phần cứng	Ghi lại dây nối, nguồn điện, cơ cấu khóa và kết quả thử thiết bị. Servo MG996R cắm cổng S6 trên bo RRC Lite, dùng ID 4 (mã servo trong phần mềm ROS điều khiển robot). Hoàn thiện dần layout thực tế.
[4] 4	Tạo các bảng dữ liệu ban đầu	Tạo bảng, khóa liên kết và dữ liệu mẫu theo sơ đồ đã chốt. Cùng Thành kiểm tra mã nguồn trước khi ghép vào nhánh dùng chung của nhóm.
[5] 5	Cài phần mềm và kiểm tra thiết bị trên Pi	Đưa bộ mã hiện có lên Raspberry Pi 5. Kiểm tra động cơ, camera, micro, loa và hai cảm biến US100 bằng lệnh terminal. Mở RViz (công cụ xem bản đồ và dữ liệu cảm biến).
[6] 6	Hoàn thiện tài liệu các loại bước	Mô tả 11 loại bước (Step Type): nhận dữ liệu gì, làm việc gì và trả kết quả gì. Chốt tham số mặc định, giới hạn điều khiển và cách xử lý lỗi.
[7] 7	Làm khung trang web quản trị	Tạo màn đăng nhập, thanh điều hướng và bố cục các trang quản lý. Kết nối đăng nhập với máy chủ và hiển thị rõ lỗi sai tài khoản, hết phiên đăng nhập.
[8] 8	Cập nhật slide kiến trúc hệ thống	Cập nhật phân công, tiến độ, sơ đồ dữ liệu và cách web, máy chủ, Pi trao đổi dữ liệu. Dùng cùng tên chức năng với tài liệu của nhóm.
[9] 9	Làm khung ứng dụng nhân viên	Tạo màn đăng nhập, danh sách yêu cầu và chi tiết nhiệm vụ. Kết nối tài khoản nhân viên và chặn các thao tác chỉ dành cho quản trị viên.
[10] 10	Làm màn hình tương tác trên robot	Thiết kế màn chọn điểm đến, hỏi thông tin và gọi trợ giúp. Thống nhất cách nhận câu hỏi, hiển thị hướng dẫn và gửi câu trả lời của người dùng.
[11] 11	Kiểm tra mô hình có sẵn của Hiwonder	Kiểm tra chương trình phát hiện té ngã, mô hình nhận diện người và bộ xử lý giọng nói trên bản Pi đang dùng. Xác nhận ảnh màu, ảnh độ sâu và cấu hình camera hoạt động.
[12] 12	Quản lý tài khoản và quyền sử dụng	Làm chức năng tạo, sửa, khóa tài khoản và phân quyền quản trị viên, nhân viên. Ghi lại ai thay đổi dữ liệu và kiểm tra người dùng không xem hoặc sửa vượt quyền.
[13] 13	Quản lý quy trình và giao việc cho robot	Lưu mẫu quy trình, phiên bản, tham số và từng bước thực hiện. Tạo nhiệm vụ, chọn robot rảnh, xếp hàng và gửi lệnh thử xuống Pi qua WebSocket bảo mật (kết nối hai chiều).
[14] 14	Quản lý bản đồ, khu vực và điểm đến	Làm chức năng thêm, sửa bản đồ, khu vực, điểm đến và nhóm điểm. Mỗi yêu cầu chọn một điểm riêng hoặc một nhóm điểm, có thứ tự thử các điểm trong nhóm.
[15] 15	Tạo kho thông tin bệnh viện	Lưu thông tin khoa phòng, quy trình khám và câu hỏi thường gặp. Cho phép sửa, duyệt nội dung và liên kết khoa phòng với điểm đến trên bản đồ.
[16] 16	Kết nối Pi với máy chủ	Pi gửi mã robot, vị trí, trạng thái và phản hồi đã nhận lệnh. Thử mất mạng rồi kết nối lại. Cùng Thành kiểm tra một lệnh gửi lại không làm robot thực hiện hai lần.
[17] 17	Kiểm tra dừng xe và giới hạn chuyển động	Thử dừng khi có vật cản, mất dữ liệu cảm biến hoặc mất kết nối. Đo khoảng trống cần thiết khi robot kéo xe, đặt giới hạn tốc độ, quay, lùi và đi ngang.
[18] 18	Làm trang tài khoản và phân quyền	Hoàn thiện danh sách tài khoản, thêm sửa, khóa mở và gán quyền. Kiểm tra các thông báo lỗi và quyền truy cập theo tài khoản đăng nhập.
[19] 19	Làm trang bản đồ và điểm phục vụ	Hiển thị bản đồ, thêm sửa điểm đến, nhóm điểm và khu vực. Cho phép sắp xếp các điểm trong nhóm, đánh dấu vùng hạn chế và chọn phiên bản bản đồ.
[20] 20	Tạo yêu cầu dẫn đường và giao nhận	Làm biểu mẫu chọn loại việc, điểm đến hoặc nhóm điểm, người giao nhận và danh sách hàng. Kiểm tra dữ liệu bắt buộc và hiển thị trạng thái yêu cầu.
[21] 21	Đưa giao diện lên màn hình robot	Cùng Hiếu chạy màn chọn điểm đến, hỏi thông tin và gọi trợ giúp trên robot. Mỗi câu trả lời phải gắn đúng câu hỏi và nhiệm vụ đang thực hiện.
[22] 22	Nhận diện và theo dõi nhiều người	Chọn mô hình có lớp người và gán mã theo dõi qua các khung hình để hạn chế đếm lặp. Đo tốc độ trên Pi và máy chủ, chọn nơi chạy phù hợp.
[23] 23	Theo dõi từng bước của nhiệm vụ	Lưu trạng thái, thời gian, dữ liệu vào ra và lỗi của từng bước, kèm lịch sử vị trí robot. Xử lý hủy việc, quá thời gian chờ, lệnh trùng và kết nối lại.
[24] 24	Làm xử lý giao nhận và xác thực QR	Lưu hàng giao, số lượng, người gửi và người nhận. Xác thực QR đúng người, đúng lần giao và còn hạn trước khi cho phép mở khóa. Ghi người xác nhận ở mỗi đầu giao nhận.
[25] 25	Làm phần máy chủ trao đổi dữ liệu AI	Làm đường nhận ảnh, âm thanh và kết quả nhận diện. Kiểm tra mã robot, thời điểm và kích thước dữ liệu. Ghép với phần chạy mô hình do Hiếu thực hiện.
[26] 26	Kết nối nhận giọng nói và hỏi đáp	Tận dụng mã mô hình có sẵn để đổi giọng nói thành chữ, tìm thông tin đã duyệt và đọc câu trả lời. Câu hỏi ngoài dữ liệu của bệnh viện được hướng dẫn liên hệ nhân viên.
[27] 27	Xác định người thuộc khu vực nào	Ghép ảnh màu và độ sâu đúng thời điểm. Dùng thông số camera, vị trí và hướng robot để đổi vị trí người sang bản đồ, rồi kiểm tra người thuộc khu vực nào.
[28] 28	Cho robot đi đến điểm riêng và nhóm điểm	Nối lệnh di chuyển với Nav2 (bộ điều hướng robot). Nếu không tìm được đường tới một điểm, thử điểm phù hợp tiếp theo trong nhóm. Báo lại điểm đã chọn, kết quả và lỗi.
[29] 29	Hoàn thiện xử lý 11 loại bước trên Pi	Nối từng loại bước với chức năng đã cài: di chuyển, tương tác, gọi máy chủ, xác thực, khóa cửa, nhận hàng, quan sát, thông báo, chờ, kiểm tra điều kiện và lưu dữ liệu dùng tiếp.
[30] 30	Theo dõi robot và nhiệm vụ trên web	Hiển thị robot đang kết nối hay mất kết nối, vị trí trên bản đồ và bước đang chạy. Bổ sung danh sách quy trình, tham số, lịch sử nhiệm vụ và phiên bản cấu hình.
[31] 31	Làm trang thông tin bệnh viện	Làm màn quản lý câu hỏi, khoa phòng và hướng dẫn khám. Quản trị viên duyệt nội dung trước khi robot sử dụng để trả lời người bệnh.
[32] 32	Theo dõi vị trí và tiến độ trên ứng dụng	Hiển thị vị trí, robot rảnh hay bận và bước đang thực hiện. Khi mất mạng, báo dữ liệu chưa cập nhật. Khi kết nối lại, lấy lại trạng thái từ máy chủ.
[33] 33	Làm màn xác thực và xác nhận hàng	Làm thao tác quét QR, kiểm tra người giao nhận, đối chiếu hàng và số lượng. Bổ sung các màn xác nhận đóng cửa, rời cơ cấu khóa và kiểm tra chốt.
[34] 34	Làm xử lý cảnh báo và gọi trợ giúp	Tạo sự cố từ nút gọi trợ giúp, giọng nói hoặc phát hiện bất thường. Giao nhân viên xử lý, lưu xác nhận đã nhận, đã đến và đã xử lý. Chuyển cảnh báo khi chưa có người phản hồi.
[35] 35	Làm dữ liệu báo cáo hoạt động	Tính tỷ lệ hoàn tất, thời gian phục vụ, quãng đường và thời gian phản hồi sự cố. Cho phép lọc theo thời gian, robot và quy trình. Kiểm tra số liệu với dữ liệu gốc.
[36] 36	Lưu số người và tổng hợp bản đồ mật độ	Nhận số người Hiếu tính được, kèm khu vực quan sát, thời gian, vị trí robot và chất lượng dữ liệu. Tổng hợp theo từng khu vực để tạo heatmap (bản đồ màu thể hiện mức đông người).
[37] 37	Tích hợp phát hiện té ngã và vùng hạn chế	Tận dụng MediaPipe (mô hình xác định các khớp cơ thể) trong ví dụ MentorPi để kiểm tra tư thế qua nhiều ảnh. Gửi sự kiện nghi té ngã hoặc người vào vùng hạn chế về máy chủ.
[38] 38	Chạy hỏi đáp và lệnh bằng giọng nói trên robot	Nối micro, loa và màn hình với phần xử lý trên máy chủ. Thử gọi robot, hỏi thông tin, chọn điểm đến, yêu cầu dừng và gọi trợ giúp. Robot phản hồi bằng lời nói hoặc màn hình.
[39] 39	Chạy mở khóa và khóa xe đẩy	Sau khi xác thực, điều khiển MG996R qua cổng S6 của RRC Lite. Dùng position [2400] để mở, [1500] để khóa theo cấu hình đã thử. Chỉ chuyển bước sau khi nhân viên xác nhận cửa và chốt.
[40] 40	Làm trang cảnh báo và mật độ người	Hiển thị mức đông người theo khu vực, thời điểm cập nhật, sự cố và nhân viên xử lý. Phân biệt khu vực chưa quan sát với số người bằng 0. Cho phép lọc lịch sử và xem ảnh minh chứng.
[41] 41	Hoàn thiện báo cáo và cấu hình quản trị	Hiển thị các chỉ số hoạt động, lọc và xuất báo cáo. Làm phần cấu hình robot, ngưỡng cảnh báo và người nhận thông báo. Ghi người sửa và thời điểm thay đổi.
[42] 42	Hoàn thiện giao diện dẫn bệnh nhân	Cho nhân viên hoặc bệnh nhân chọn đích, xác nhận bắt đầu, xem hướng dẫn và yêu cầu dừng. Khi tới nơi, gửi xác nhận hoàn tất về máy chủ.
[43] 43	Làm nhận cảnh báo và xử lý sự cố	Thông báo khi có gọi trợ giúp, té ngã hoặc người vào vùng hạn chế. Nhân viên nhận việc, xác nhận đã đến và ghi kết quả xử lý. Thử nhận thông báo khi ứng dụng ở nền.
[44] 44	Chạy đủ các quy trình trên máy chủ	Chạy 8 quy trình cùng Pi, web và ứng dụng. Kiểm tra việc tạo nhiệm vụ, truyền tham số, lưu kết quả, chọn robot và kết thúc đúng trạng thái. Sửa các lỗi làm gián đoạn luồng.
[45] 45	Kiểm tra dữ liệu và quyền của các luồng	Kiểm tra quyền người giao nhận, yêu cầu bị hủy, sự cố chưa xử lý và lỗi khi hai người giao việc cùng lúc. Đảm bảo dữ liệu trên web và ứng dụng thống nhất.
[46] 46	Kiểm tra dữ liệu đám đông từ robot	Phối hợp Hiếu cho robot dừng tại điểm quan sát, đếm người theo khu vực và gửi kết quả. Kiểm tra máy chủ lưu đúng thời điểm, tránh bản ghi trùng và web hiển thị đúng dữ liệu.
[47] 47	Kiểm tra luồng nhận sự kiện và cảnh báo	Nhận kết quả té ngã và vùng hạn chế từ phần xử lý của Hiếu. Cùng Trung kiểm tra tạo sự cố, gửi thông báo, nhận việc và kết thúc xử lý. Thử sự kiện lặp và mất kết nối.
[48] 48	Chạy dẫn bệnh nhân và đi theo nhóm điểm	Chạy đủ chọn đích, hướng dẫn, di chuyển và xác nhận tới nơi. Thử điểm riêng, điểm đầu trong nhóm bị chặn và toàn bộ nhóm không có đường đi.
[49] 49	Chạy trọn quy trình giao nhận	Chạy từ nhận yêu cầu, tới điểm lấy, xác thực, mở cửa, nhận hàng, khóa, di chuyển và giao hàng. Thử sai người, thiếu hàng và chưa xác nhận chốt.
[50] 50	Chạy hỏi đáp và gọi trợ giúp	Thử hỏi thông tin, ra lệnh bằng giọng nói và gọi trợ giúp qua nút hoặc giọng nói. Kiểm tra phản hồi trên robot, thông báo tới nhân viên và cách dừng nhiệm vụ đang chạy.
[51] 51	Kiểm tra đủ chức năng quản trị	Chạy quản lý robot, tài khoản, quyền, bản đồ, khu vực, quy trình, cảnh báo và báo cáo. Đối chiếu dữ liệu hiển thị với máy chủ và sửa các màn còn thiếu.
[52] 52	Sửa lỗi web khi chạy các luồng chính	Sửa lỗi biểu mẫu, cập nhật trạng thái, phân quyền và số liệu báo cáo phát hiện trong lần chạy chung. Kiểm tra lại chức năng liên quan sau khi sửa.
[53] 53	Kiểm tra đủ chức năng nhân viên và bệnh nhân	Chạy giao việc, theo dõi robot, giao nhận, dẫn bệnh nhân, gọi trợ giúp và xử lý sự cố. Kiểm tra thao tác trên điện thoại và màn hình robot bằng dữ liệu thật của bản thử.
[54] 54	Sửa lỗi ứng dụng trong lần chạy chung	Sửa các nút xác nhận sai trạng thái, thao tác lặp, thông báo chậm và mất dữ liệu sau khi mở lại ứng dụng. Kiểm tra lại trên thiết bị của nhóm.
[55] 55	Kiểm tra mốc hoàn thành tuần 8	Cùng cả nhóm chạy 8 quy trình và ghi đạt hoặc chưa đạt từng luồng. Lưu danh sách lỗi, người sửa và video kết quả. Xử lý lỗi chặn luồng trước khi kết thúc tuần.
[56] 56	Kiểm thử mất mạng và lệnh gửi lặp	Thử lệnh hết hạn, gửi lại, robot mất kết nối và nhiều yêu cầu cùng lúc. Kiểm tra máy chủ không giao trùng robot hoặc tự kết thúc công việc chưa rõ kết quả.
[57] 57	Kiểm thử quyền và bảo vệ dữ liệu	Thử tài khoản sai quyền, QR hết hạn, QR sai lần giao và dữ liệu không hợp lệ. Che thông tin đăng nhập trong nhật ký, giới hạn người xem ảnh sự cố và lịch sử thao tác.
[58] 58	Đánh giá kết quả nhận diện	Dùng video và số người đếm thủ công để kiểm tra té ngã, đám đông, vùng hạn chế. Đo số lần đúng, sai, bỏ sót và độ trễ. Thử người bị che, đổi hướng camera và đi qua ranh giới khu vực.
[59] 59	Đánh giá hỏi đáp và nhận giọng nói	Thử giọng nói trong môi trường có tiếng ồn, câu hỏi có trong dữ liệu và ngoài phạm vi. Sửa nội dung, cách tìm câu trả lời và hướng dẫn chuyển nhân viên.
[60] 60	Thử lỗi cảm biến và khởi động lại Pi	Thử US100 không phản hồi, chương trình dừng, mất mạng và khởi động lại giữa nhiệm vụ. Kiểm tra robot dừng, giữ nhật ký và không tự mở khóa hoặc chạy lại lệnh cũ.
[61] 61	Kiểm tra đường đi khi kéo xe	Thử hành lang hẹp, vật cản và quay đầu với xe kéo. Đo thời gian, quãng đường và khoảng dừng. Kiểm tra lại lựa chọn điểm thay thế trong nhóm.
[62] 62	Kiểm tra web khi lỗi mạng và sai quyền	Kiểm tra trang tải lại, mất kết nối, hết phiên đăng nhập, dữ liệu trống và lỗi máy chủ. Hiển thị hướng dẫn rõ ràng và chặn thao tác không được phép.
[63] 63	Đối chiếu biểu đồ và báo cáo	Đối chiếu số nhiệm vụ, tỷ lệ hoàn tất, thời gian, quãng đường và sự cố với dữ liệu máy chủ. Thử các bộ lọc, xuất báo cáo và chữ tiếng Việt.
[64] 64	Thử ứng dụng khi mất mạng và thao tác lặp	Thử đóng mở ứng dụng, mất kết nối, quét QR nhiều lần và bấm xác nhận liên tục. Kiểm tra không ghi nhận giao hàng hoặc hoàn tất nhiệm vụ hai lần.
[65] 65	Hoàn thiện màn xử lý nhiệm vụ bị lỗi	Hiển thị lý do dừng và yêu cầu nhân viên kiểm tra vị trí, cửa, hàng trước khi xử lý tiếp. Gửi người xác nhận và lý do về máy chủ để lưu lịch sử.
[66] 66	Kiểm tra tốc độ và độ ổn định máy chủ	Cho nhiều người dùng thử tạo yêu cầu và theo dõi cùng lúc. Đo thời gian phản hồi, kiểm tra truy vấn chậm và sửa lỗi làm hệ thống mất kết nối.
[67] 67	Sửa lỗi máy chủ sau kiểm thử	Ưu tiên lỗi sai trạng thái, sai quyền, trùng nhiệm vụ và mất dữ liệu. Kiểm tra lại các quy trình có liên quan trước khi ghép mã nguồn vào bản chạy chung.
[68] 68	Giảm cảnh báo sai và kiểm tra tải xử lý ảnh	Điều chỉnh số ảnh gửi, ngưỡng nhận diện và khoảng cách giữa các cảnh báo. Kiểm tra máy chủ xử lý liên tục không làm chậm việc điều khiển robot.
[69] 69	Thử bàn giao sự cố cho nhân viên	Thử nhân viên không phản hồi, phản hồi muộn, báo nhầm và mất mạng. Kiểm tra sự cố chỉ đóng khi đã có kết quả xử lý hoặc lý do xác nhận báo nhầm.
[70] 70	Cho robot chạy nhiều lượt liên tục	Chạy các tuyến và thao tác khóa trong điều kiện sử dụng dự kiến. Theo dõi kết nối, nguồn điện, cảm biến và nhiệt độ thiết bị. Ghi lỗi cùng thời điểm xảy ra.
[71] 71	Sửa lỗi phần mềm Pi và chạy lại	Sửa lỗi giao tiếp, điều hướng, loa, camera và xử lý từng bước được phát hiện khi chạy liên tục. Kiểm tra lại cả tình huống bình thường và tình huống lỗi.
[72] 72	Sửa lỗi và thống nhất các màn quản trị	Sửa lỗi hiển thị, thông báo, biểu mẫu và cập nhật dữ liệu. Dùng thống nhất tên trạng thái và thuật ngữ trên toàn bộ trang web.
[73] 73	Viết hướng dẫn sử dụng web	Viết các bước quản lý tài khoản, robot, điểm đến, quy trình và cảnh báo. Chụp màn hình bản đang chạy và giải thích cách xem báo cáo.
[74] 74	Sửa lỗi và thống nhất các màn ứng dụng	Sửa lỗi nhận thông báo, camera, micro và chuyển trạng thái nhiệm vụ. Kiểm tra lại chữ tiếng Việt và quyền thiết bị trên điện thoại, màn hình robot.
[75] 75	Viết hướng dẫn cho nhân viên và bệnh nhân	Mô tả cách tạo yêu cầu, giao nhận, xác nhận chốt, dẫn đường và xử lý cảnh báo. Chuẩn bị hướng dẫn ngắn trên màn hình robot.
[76] 76	Triển khai bản dùng để nghiệm thu	Đưa máy chủ và cơ sở dữ liệu lên môi trường dùng chung. Cấu hình địa chỉ kết nối, mã truy cập và cách quay về bản trước khi triển khai có lỗi.
[77] 77	Tổ chức chạy nghiệm thu toàn hệ thống	Cùng nhóm chạy kịch bản bệnh nhân, nhân viên và quản trị viên trên bản triển khai. Ghi kết quả từng chức năng, người kiểm tra và lỗi còn lại.
[78] 78	Thử sao lưu và khôi phục dữ liệu	Sao lưu cơ sở dữ liệu, bản đồ và cấu hình cần thiết. Khôi phục trên môi trường thử, kiểm tra đăng nhập, danh sách nhiệm vụ và dữ liệu báo cáo.
[79] 79	Kiểm tra lại thiết bị sau chạy dài	Kiểm tra nguồn, dây nối, gá cảm biến, xe kéo và khóa cửa cùng Hiếu. Xác nhận cấu hình phần cứng thực tế khớp phần mềm và tài liệu.
[80] 80	Đóng gói chương trình chạy trên Pi	Chốt phiên bản thư viện và cấu hình. Thiết lập thứ tự khởi động, lưu nhật ký và cách dừng chương trình. Thử khởi động lại mà không tự chạy lại thao tác cũ.
[81] 81	Nghiệm thu các quy trình trên robot thật	Cùng Thiên chạy 8 quy trình, kiểm tra dữ liệu từng bước và ghi hình kết quả. Đo lại các giới hạn vận hành với xe kéo ở bản phần mềm nghiệm thu.
[82] 82	Triển khai và kiểm tra web quản trị	Đưa web lên địa chỉ dùng chung. Kiểm tra đăng nhập, kết nối máy chủ, bản đồ, cảnh báo và xuất báo cáo trên các trình duyệt nhóm sử dụng.
[83] 83	Nghiệm thu các thao tác quản trị	Chạy lại từng quyền, chức năng quản lý và báo cáo theo kịch bản. Ghi kết quả, ảnh màn hình và lỗi cần sửa vào bảng kiểm thử.
[84] 84	Đóng gói và cài ứng dụng	Tạo bản cài trên điện thoại và cập nhật giao diện robot. Kiểm tra địa chỉ máy chủ, quyền camera, micro và thông báo sau khi cài mới.
[85] 85	Nghiệm thu thao tác nhân viên và bệnh nhân	Chạy kịch bản tạo yêu cầu, dẫn bệnh nhân, giao nhận và xử lý sự cố trên thiết bị. Lưu kết quả từng bước và ghi lỗi sử dụng còn gặp.
[86] 86	Sửa các lỗi còn lại sau nghiệm thu	Tổng hợp lỗi từ Pi, web và ứng dụng. Phân người xử lý, kiểm tra lại lỗi đã sửa và chốt những hạn chế phải ghi rõ trong báo cáo.
[87] 87	Hoàn thiện tài liệu thiết kế hệ thống	Cập nhật yêu cầu, kiến trúc, sơ đồ dữ liệu, trạng thái và cách trao đổi dữ liệu theo bản chạy thực tế. Đối chiếu các chức năng trong Major Features với phần đã làm.
[88] 88	Hoàn thiện hồ sơ phần cứng	Cập nhật sơ đồ dây, danh sách thiết bị, cấu hình servo và cảm biến theo bản đã nghiệm thu. Ghi cách kiểm tra nguồn, cơ cấu khóa và kết nối với Raspberry Pi.
[89] 89	Chuẩn bị dữ liệu cho buổi trình bày	Chuẩn bị tài khoản theo vai trò, khoa phòng, bản đồ, nhóm điểm và hàng giao mẫu. Kiểm tra cách đưa dữ liệu về trạng thái ban đầu sau mỗi lần chạy thử.
[90] 90	Hoàn thiện hướng dẫn Pi và tích hợp AI	Mô tả tệp chương trình, cấu hình, lệnh thử thiết bị và dữ liệu gửi nhận. Ghi mô hình sử dụng, nguồn mã, nơi chạy, cách cài và kết quả thử nhận diện, giọng nói.
[91] 91	Sửa lỗi robot sau nghiệm thu	Sửa các lỗi còn mở của chương trình Pi. Chạy lại tuyến, thao tác khóa và phản hồi từng bước. Lưu cấu hình đã kiểm tra để dùng thống nhất ở buổi bảo vệ.
[92] 92	Hoàn thiện hướng dẫn và hình giao diện web	Cập nhật hướng dẫn quản trị theo bản triển khai. Thay hình cũ, kiểm tra tên chức năng và bổ sung ví dụ đọc số liệu trên trang báo cáo.
[93] 93	Soạn slide báo cáo đầy đủ	Tổng hợp bài toán, kiến trúc, sơ đồ dữ liệu, các quy trình và kết quả thử. Dùng số liệu thật và chia rõ nội dung trình bày của từng thành viên.
[94] 94	Hoàn thiện hướng dẫn sử dụng ứng dụng	Cập nhật ảnh và các bước thao tác theo bản cài nghiệm thu. Viết rõ cách kiểm tra hàng, cửa, chốt và xử lý khi robot bị dừng giữa nhiệm vụ.
[95] 95	Tổng hợp báo cáo kiểm thử	Gộp kịch bản, kết quả đạt hoặc chưa đạt, ảnh, video và danh sách lỗi. Mỗi chức năng có người kiểm tra và bằng chứng của bản phần mềm đang dùng.
[96] 96	Chốt phiên bản phần mềm cuối	Chốt mã nguồn máy chủ, Pi, web, ứng dụng và cơ sở dữ liệu cùng phiên bản. Kiểm tra cài đặt lại, khôi phục dữ liệu và chạy các luồng trước khi bàn giao.
[97] 97	Kiểm tra và chốt bộ báo cáo	Cùng nhóm kiểm tra tài liệu yêu cầu, thiết kế, sơ đồ, các loại bước và kết quả thử. Sửa nội dung không khớp phần mềm. Ghi nhận góp ý của thầy rồi thống nhất nội dung cuối.
[98] 98	Hoàn thiện bộ cài và bản sao lưu bàn giao	Tập hợp dữ liệu mẫu, bản đồ, cấu hình máy chủ, mô hình và các bản sao lưu. Kiểm tra có thể khôi phục theo hướng dẫn.
[99] 99	Chuẩn bị robot và thiết bị dự phòng	Kiểm tra dây, nguồn, xe kéo, khóa và cảm biến trước tổng duyệt. Chuẩn bị thiết bị kết nối, sơ đồ dây và cách xử lý các lỗi phần cứng thường gặp.
[100] 100	Chạy xác nhận bản cuối trên robot	Chạy lại đủ 8 quy trình bằng bộ dữ liệu trình bày. Kiểm tra thao tác dừng, mất mạng và lỗi cảm biến. Ghi hình các luồng đã chạy để dùng làm minh chứng.
[101] 101	Chuẩn bị phần trình bày robot	Chọn tuyến và các thao tác minh họa được khả năng robot. Chuẩn bị lời giải thích dữ liệu nhận vào, kết quả trả về và cách xử lý lỗi của từng loại bước.
[102] 102	Chốt slide và nội dung quản trị	Cập nhật slide theo báo cáo cuối, kiểm tra hình, chữ và số liệu. Chuẩn bị thứ tự thao tác trên web trong buổi trình bày.
[103] 103	Tổng duyệt phần web quản trị	Chạy thử tài khoản, bản đồ, nhóm điểm, mật độ người, cảnh báo và báo cáo trên bản cuối. Sửa lỗi trình bày phát hiện trong lần tổng duyệt.
[104] 104	Chốt kết quả kiểm thử và hướng dẫn	Kiểm tra mọi lỗi đã đóng có kết quả chạy lại. Chốt báo cáo kiểm thử, hướng dẫn sử dụng và hình ảnh theo phiên bản bàn giao.
[105] 105	Tổng duyệt phần nhân viên và bệnh nhân	Chạy giao việc, quét QR, xác nhận hàng, dẫn đường và gọi trợ giúp theo kịch bản. Phối hợp Hiếu căn thời gian giữa thao tác ứng dụng và robot.
[106] 106	Tổng duyệt và phân chia phần bảo vệ	Cùng nhóm trình bày thử theo thời lượng được thông báo. Kiểm tra tệp báo cáo, slide, video và người trả lời từng phần của hệ thống.
[107] 107	Kiểm tra thiết bị trước buổi bảo vệ	Kiểm tra nguồn, cơ cấu, cảm biến, mạng và máy chủ. Chuẩn bị bản sao dữ liệu, thiết bị cần mang và phần giải thích cấu hình phần cứng.
[108] 108	Chuẩn bị và vận hành phần trình diễn robot	Kiểm tra bản đồ, vị trí bắt đầu, kết nối và tuyến trình diễn. Phụ trách robot, giải thích phần xử lý trên Pi và cách tích hợp các mô hình AI.
[109] 109	Chuẩn bị và trình bày phần web	Mở đúng tài khoản quản trị, dữ liệu mẫu và slide cuối. Trình bày các chức năng quản trị, báo cáo và kết quả kiểm thử liên quan.
[110] 110	Chuẩn bị và trình bày phần ứng dụng	Kiểm tra điện thoại, màn hình robot và tài khoản nhân viên. Trình bày các thao tác người dùng, phối hợp điều khiển kịch bản cùng Hiếu.
[111] 111	Bảo vệ và tổng hợp góp ý	Cả nhóm tham gia bảo vệ theo lịch của trường. Ghi lại nhận xét, câu hỏi và nội dung cần chỉnh sửa, sau đó trao đổi với thầy trước khi chốt thay đổi.
