# Ghi chú bảy hình người dùng cung cấp

Ngày đọc: 2026-10-07. Nguồn I1–I7 là ảnh trong hội thoại theo thứ tự dưới đây. Đây là bản diễn giải nội dung nhìn thấy, không phải bản sao ảnh hoặc đặc tả đã phê duyệt. Các chữ nhỏ/đường giao nhau cần đối chiếu file gốc trước khi xây graph thực thi.

| ID | Hình | Nội dung cần giữ | Ý nghĩa cho backend |
|---|---|---|---|
| I1 | System Architecture | Staff/Admin clients → HTTPS/WebSocket → Backend; Data Layer; Wi-Fi LAN → Raspberry Pi 5/ROS2; Nav2, SLAM, executor, sensor fusion, voice/LLM bridge, safety; UI bệnh nhân gắn robot; UART RRC Lite, USB LiDAR/camera, audio | Backend điều phối/auth/lưu dữ liệu/cảnh báo. Nav2, motor, sensor, speech runtime là dependency của đội Pi/AI. Nhãn WebSocket chưa tự thay quyết định REST polling |
| I2 | Context Diagram Version 1 | Patient, Hospital Staff, Administrator, Hardware, AI Services trao đổi với toàn hệ thống | Sơ đồ bao trùm sản phẩm, không có nghĩa Java backend trực tiếp điều khiển motor. Patient là actor không cần Account; AI có transcript/intent/answer/TTS contract |
| I3 | AI Voice Assistance Flow | Thu tiếng → kiểm capture → yêu cầu nói lại; transcript/intent/context → confidence → hỏi rõ; truy xuất thông tin được duyệt → câu trả lời hoặc fallback → TTS/play | Cần trạng thái thiếu dữ liệu, correlation câu hỏi/câu trả lời, fallback và nguồn nội dung được duyệt. Log voice đã có không chứng minh STT/RAG/TTS đã tích hợp |
| I4 | Patient Guidance & Escort Flow | Thu điểm đến → đọc lại → xác nhận → server kiểm đích/map → tạo mission → Pi kiểm localization/sensors/battery → lập đường → hướng dẫn → tới nơi → log | Device gửi request, backend tạo Job; báo robot/path unavailable cho Staff. Nhánh destination invalid và node “Create Mission” ở lane Patient cần làm rõ, không diễn giải thành Patient tạo Job trực tiếp |
| I5 | Delivery Task Flow | Staff tạo delivery → validate → queue/assign → xác thực QR gửi → mở khoang → load → đóng/khóa → di chuyển → QR/PIN nhận → unload → đóng/khóa → delivered/proof | Kiểm quyền hai đầu, snapshot hàng, audit. Nhãn pickup authorization lặp ở đoạn nhận và các node unlock cần review; giữ bước xác nhận cửa/chốt rõ ràng theo S3 |
| I6 | Emergency SOS | SOS thủ công hoặc phát hiện bất thường → dừng an toàn/chụp vị trí → snapshot nếu có → incident ưu tiên cao → Staff review → dispatch responder hoặc false alarm → resolved/escalate → lưu kết quả | Không chờ robot rảnh mới báo khẩn; phân biệt tiếp nhận, tới nơi, giải quyết, đóng. Node Continue Mission chưa đủ điều kiện để tự resume Job |
| I7 | Obstacle Recovery | Move → obstacle decision → recovery/replan → success tiếp tục hoặc báo lỗi → notify Staff → remove obstacle/resume | Nhãn Yes/No quanh Stop Robot có vẻ cần kiểm tra lại. Recovery Nav2 cục bộ khác Job phục hồi trên Server. Không tự bật PAUSE/RESUME hoặc CALL_WORKFLOW |

## Điều kiện khi chuyển hình thành yêu cầu triển khai

1. Chốt diagram/version được dùng và tên enum thật; “Navigate”, “Confirm”, “Load” là hành động, không phải StepType mới.
2. Mỗi nhánh phải có điều kiện, trạng thái đích, timeout, quyền người xác nhận và correlation.
3. Không suy ra cảm biến xác nhận khóa, patient-following, docking hoặc AI đã có từ một hộp trên sơ đồ.
4. Test API/mock chỉ chứng minh phần server. Ghi riêng kết quả chạy Pi, giao diện và phần cứng thật.
