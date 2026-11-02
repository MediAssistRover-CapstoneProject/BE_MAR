# MOVE và thời gian chờ — 02/11/2026

BE tiếp tục dùng Aiven. Chưa chuyển dữ liệu hoặc cấu hình sang EC2 theo yêu cầu người vận hành. File `mar-ec2-key.pem` là khóa SSH, không phải CA MySQL. Việc chuyển DB sau này phải giữ UUID robot, map, endpoint, zone, workflow, device registration và bảng MapBinaryAsset; chỉ đổi JDBC URL không chuyển dữ liệu.

Mạng nhà đã xác minh: BE `192.168.1.63`, Pi `192.168.1.65`. HTTPS, DEVICE auth và URL upload/TTS của Pi đã được cập nhật; CA LAN được giữ nguyên. CA Aiven độc lập với HTTPS LAN. Không lưu IP/khóa/mật khẩu thật vào mã nguồn hay tạo lại Robot ID khi chỉ đổi mạng.

## Các thay đổi giảm chờ

- Tuyến cùng số điểm, tốc độ và phiên bản template tái sử dụng workflow đã PUBLISHED. Endpoint/map/session vẫn lấy từ request mới. Lượt đầu tạo/validate/publish; các lượt sau bỏ phần này. Mẫu bị archive/disable không tự được bật lại.
- BE lấy danh sách endpoint trong một truy vấn; UI kiểm tra endpoint song song thay vì nối tiếp từng HTTP request. Kiểm tra trạng thái và map hash vẫn áp dụng.
- Xác nhận READY ở đầu giao dịch tạo tuyến được dùng lại trong chính giao dịch đang giữ khóa Robot. Trước đây các lần kiểm tra sau có thể tự hết hạn 5 giây do heartbeat phải chờ khóa này. Bằng chứng không tồn tại sau commit/rollback, không đi vào giao dịch REQUIRES_NEW, không dùng lại nếu session/map/revision/transport/producer thay đổi. Pi vẫn kiểm tra cảm biến và pose hiện tại trước khi chạy.
- Pi hỏi quyền sử dụng phiên qua HTTPS mỗi 2 giây. Một xác nhận đã kiểm chứng có thể được giữ tối đa 15 giây khi timeout/HTTP 5xx; lỗi TLS, 401/403, đóng phiên hoặc đổi identity vẫn vô hiệu ngay. Điều này không tăng tuổi pose/LiDAR được phép dùng.
- UI hiển thị giai đoạn kiểm tra, chuẩn bị BE và tổng thời gian gửi tuyến. Log BE có `Navigation route prepared` với `reusedWorkflow`, `prepareMs`, `requestMs`, `totalMs` (không bao gồm thời gian commit sau khi method trả về).

## Nhịp hiện tại

| Phần | Giá trị / cách hoạt động |
|---|---|
| Điều khiển Nav2 + velocity smoother trên Pi | 20 Hz |
| Vòng chuyển lệnh/lease của MOVE trên Pi | Khoảng 50 ms |
| Heartbeat Pi → BE | 3 giây; giữ nguyên, không tăng tần suất ghi DB |
| SERVER_HEARTBEAT BE → Pi | Scheduler 3 giây |
| BE đánh dấu MQTT mất liên lạc | 15 giây không có heartbeat hợp lệ |
| Timeout business ACK trên Pi | 15 giây; đây là giới hạn chờ, không phải sleep bắt buộc |
| Gửi lại durable event Pi | Sau 8 giây nếu chưa ACK; không phát lại hành động vật lý |
| Scheduler durable inbox/outbox BE | 250 ms sau lượt trước, không phải đảm bảo end-to-end 250 ms |
| HTTPS hỏi phiên | Mỗi 2 giây sau request; timeout request 4 giây, xác nhận cũ tối đa 15 giây |
| Chờ pose tạm hồi phục trước MOVE | Tối đa 2 giây; có dữ liệu thì đi tiếp ngay |
| Pose không đủ tin cậy giữa MOVE | Giữ bánh dừng, chờ tối đa 5 giây; không tự khởi tạo lại AMCL |
| Chuẩn bị lifecycle/action Nav2 | Tối đa 15 giây; node đã ACTIVE thì không chờ hết hạn |
| Đứng yên trước quay cuối | 0,2 giây bằng chứng odometry; cửa sổ tối đa 2 giây |
| Ổn định pose trước quay cuối | 0,3 giây; cửa sổ tối đa 10 giây; không có vòng ổn định thứ hai sau Spin |

AMCL chỉ khởi tạo khi bắt đầu phiên hoặc người dùng gửi revision khởi tạo mới. Các MOVE A→B→C không tự phát lại initial pose. AMCL/TF/odom vẫn phải cập nhật tọa độ đang di chuyển; cố định tọa độ lúc đầu sẽ làm robot mất khả năng bám bản đồ chính xác.

Pi gửi path Nav2 đầu tiên và đợi BE chấp nhận trước khi bắt đầu navigation. Controller bám path hiện hành; khi gặp vật cản, Nav2 được phép tính lại đường và Pi chuyển path mới lên UI ở nền. Đường ban đầu không phải cam kết bất biến: không ép robot chạy theo đường cũ xuyên vật cản mới. UI hoặc DB chậm không nằm trong vòng điều khiển bánh.

## Kết quả kiểm tra và giới hạn

- Không có job BE QUEUED/ACCEPTED/RUNNING/RECOVERY_REQUIRED lúc kiểm tra. Journal Pi không có step ACCEPTED/RUNNING/RECOVERY_REQUIRED hoặc outbox chưa ACK. Giữ lịch sử và bản đồ, không xóa journal.
- Trước deploy, HTTP snapshot đo được khoảng 344–765 ms; đọc trạng thái phiên khoảng 641 ms. Đây là mẫu đọc khi robot đang dừng, không phải SLA khi chạy.
- 15 ca Java về tuyến/transaction readiness và 12 ca JS hiển thị/poll qua; 11 ca Pi về phiên và lỗi truyền liên quan qua. Không phát lệnh MOVE trong các kiểm tra này.
- Một assertion cũ về reactive mapping (`test_nav2_candidate_slows_before_obstacles_and_preserves_a_valid_turn`) vẫn lỗi ở hành vi yaw; bản này không thay thuật toán mapping để sửa ngoài phạm vi. Không coi toàn bộ bộ test mapping đã qua.
- Chưa đo được số giây giảm trên robot thực sau deploy. Lượt tạo shape mới vẫn tốn thời gian publish workflow; bước nhận lệnh còn phải ghi/ACK COMMAND_ACK và STEP_STARTED trước khi có hiệu ứng vật lý.

Chạy lại: mở phiên map mới trên UI, trong container dùng `cd /home/ubuntu/mediassist-mqtt-2026-10-08` rồi `zsh tools/navigation_session.zsh`. Khi READY, thử tuyến ngắn với 0,30 m/s; không reset initial pose ở mỗi endpoint. Nếu cần tạo map mới thì dùng `zsh tools/robot.zsh run` sau khi đã đóng/dừng navigation.
