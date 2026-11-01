# Cloud DB và trạng thái robot — 02/11/2026

BE tiếp tục chạy local trên laptop và dùng Aiven MySQL `defaultdb` với
`VERIFY_IDENTITY`. MQTT/dispatch/scheduler bật. Không chuyển Pi sang truy vấn DB.

## Sửa trong nhánh codex/cloud-realtime-voice-audit

- Snapshot vị trí UI không chờ request/job detail hay đồng bộ zone hoàn thành.
  Poll kết quả job chỉ có một request nhóm đang chạy, không xếp chồng.
- Lỗi HTTP lấy trạng thái không được gán nhãn thành MQTT disconnect và không xóa
  ngay vị trí cuối. Timestamp/freshness vẫn hết hạn bình thường; dữ liệu cũ không
  được dùng để cho phép MOVE.
- RobotRealtimeViewService chia sẻ snapshot hiển thị tối đa 1 giây giữa các
  request/viewer; event sau commit làm mất hiệu lực cache. Cache không dùng cho
  dispatch, không đổi observedAt/capturedAt và không giữ stale như mới.
- UI dùng fetch SSE với Authorization header (không đặt token trong URL), nhận
  robot-state đã xác thực qua kết nối mở. REST 1 Hz chỉ dự phòng khi không có
  stream mới trong 6 giây. Đổi map/đóng trang hủy stream cũ; lỗi stream không
  tự kết luận MQTT robot mất kết nối. Response tắt buffering nginx bằng header.
- SSE gửi snapshot trong executor bounded riêng, gộp thông báo theo robot, một
  snapshot cho mọi viewer. Luồng commit heartbeat không đọc DB và gửi SSE cho
  từng browser như trước. JWT expiry vẫn kết thúc subscription.
- Gồm các bản trước đó đã có trong source: PNG đi cùng metadata đúng revision;
  scheduler heartbeat riêng; retry tối đa hai lần giao dịch heartbeat bị rollback
  do lỗi khóa tạm thời. Không replay physical command trong retry này.

## Những gì không đổi

Nav2 và vòng điều khiển/gate khoảng 20 Hz ở Pi, dùng localization/scan cục bộ.
Heartbeat Pi vẫn 3 giây. Không tăng tần số ghi cloud DB để che UI lag. Các điều kiện
stop, profile/map/session identity và zone giữ nguyên. Không cần thay code Pi cho
những thay đổi BE/UI trên. Không thay tốc độ/footprint trong đợt này.

Không có bảo đảm zero latency. HTTP, broker, WAN/DB và vòng lấy mẫu heartbeat đều
có độ trễ; cập nhật UI không có nghĩa AMCL mất vị trí. Muốn mũi tên cập nhật 5–10 Hz
thì cần telemetry lane chỉ hiển thị, rate-limit và cache riêng không ghi DB mỗi
mẫu, tách hoàn toàn khỏi heartbeat/dispatch. Đợt này không thêm protocol lane mới.

## Kiểm tra và vận hành

Kiểm thử: lookup job chậm không chặn pose, HTTP lỗi không giả MQTT mất kết nối,
cache invalidation trong lúc đọc, snapshot dùng chung, callback SSE không chặn
heartbeat dù 100 state events tới khi một đọc DB chậm; scheduler isolation và
heartbeat retry. Khởi động BE với Aiven để xác nhận schema/TLS/MQTT.

Sau khi deploy: Ctrl+F5 trang HTML; chạy lại Pi; mở navigation session mới rồi
khởi tạo vị trí. Không tái dùng ID phiên đã đóng. Dùng Use-MarCloudDb.ps1
-EnableDispatch để restart; lệnh sẽ từ chối nếu còn job/session mở.

DB cloud ghi nghiệp vụ. Asset map vẫn trong volume laptop. RAG/voice không được
đưa inference vào MQTT callback, scheduler hay vòng Nav2. Đánh giá AI chi tiết ở
AI_VOICE/docs/LOCAL_VOICE_READINESS_2026_11_02.md.
