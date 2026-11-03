# Kiểm tra MOVE ngày 03/11/2026

Job `0fc50306-a3fc-47a9-91b4-048704e16eb3`, map `4ce7f7cb-e5e8-4c88-a2ab-5df98c455bd6`, chạy lúc khoảng 01:17 giờ Việt Nam.

## Kết quả đối chiếu log

- Pi nhận lệnh, ACK và STEP_STARTED được BE ghi nhận. Có một lần định vị chuyển AMCL_UNCERTAIN/VERIFYING khoảng 0,8 giây rồi LOCALIZED trở lại trong cùng phiên/revision. Không có reconnect MQTT trong lượt MOVE này.
- Nav2 RPP báo `detected collision ahead` lúc 01:17:20; `Controller patience exceeded` sau khoảng 0,3 giây. Chờ 1 giây rồi thử FollowPath lại vẫn bị chặn, NavigateToPose kết thúc ABORTED. Đây là nguyên nhân trực tiếp của NAV_ABORTED; không phải timeout job từ UI.
- BE có deadlock ở truy vấn khóa dải step RUNNING của scheduler legacy lúc nhận STEP_ERROR. Receipt sau đó đã được xử lý; job là FAILED, có bằng chứng dừng.
- Pi đã dừng stack. Journal đọc chỉ đọc: không có ACCEPTED/RUNNING/RECOVERY_REQUIRED và không có outbox chưa ACK. Giữ nguyên lịch sử FAILED/SUCCEEDED để chống thực hiện lại lệnh.

## Thay đổi

- Saved-map RPP: `failure_tolerance=2.0` giây để có cơ hội nhận 1–2 lần global replan thay vì abort sau 0,3 giây. Nav2 xuất vận tốc bằng 0 trong thời gian controller chưa tính được lệnh hợp lệ. Không tắt collision detection, không xóa costmap/vùng cấm để ép robot chạy.
- Global costmap cập nhật 2 Hz, global replan 2 Hz; FollowPath có tối đa 2 lần thử lại có thời gian chờ. Tiếp tục dùng một NavigateToPose goal và global replan; không gửi mục tiêu bánh xe mới cho từng điểm path.
- Quay căn hướng đầu đường MOVE tối đa 0,8 rad/s; các đoạn cua vẫn đi tới và cua theo RPP. Giữ giới hạn quay phần cứng và cấu hình quét map riêng. Không đổi footprint/padding hay tăng vận tốc tuyến.
- Bán kính điều tiết tốc độ RPP ít nhất `desired_linear_vel / angular_limit`. Giới hạn tiến/quay trong relay dùng cùng hệ số để không biến vòng cung yêu cầu thành một vòng cung rộng hơn khi chạm trần vận tốc quay. Các kiểm tra vật cản/zone vẫn áp dụng.
- BE scheduler tìm ID ứng viên legacy bằng SELECT không khóa; sau đó khóa đúng Job trước đúng Step. Không khóa toàn dải RUNNING của các MOVE schema 3 khi quét timeout legacy.
- Đồng bộ lại các thay đổi đã cài với folder Git: bỏ dấu conflict trong ros_adapter/test; khôi phục cache graph, cửa sổ authorization có giới hạn, hợp đồng sai số vị trí 10 cm, route template reuse và transaction readiness đã kiểm tra trước đó. Giữ chế độ RPP đi tiến và một lần căn hướng cuối khi được yêu cầu. Không push hay tạo nhánh.
- Log NAV_MOTION bổ sung input_at_publish/output_age_sec để phân biệt lệnh đầu vào mới nhất với lệnh xuất của vòng điều khiển trước đó.

## Giới hạn kiểm chứng

Kiểm tra offline và đối chiếu runtime config không thay thế thử chuyển động trong layout thật. Log cũ không đủ xác định costmap collision đó là tường, vật mới hay vùng cấm; không khẳng định mọi lần báo va chạm đều là sai. Sau khi cài cần mở phiên navigation mới để sinh YAML mới, xác minh vị trí rồi thử MOVE ngắn. Không khởi động lại AMCL trước mỗi step.

Tham khảo mã nguồn Nav2 Humble: [controller failure tolerance](https://github.com/ros-navigation/navigation2/blob/humble/nav2_controller/src/controller_server.cpp), [RPP collision và điều tiết tốc độ](https://github.com/ros-navigation/navigation2/blob/humble/nav2_regulated_pure_pursuit_controller/src/regulated_pure_pursuit_controller.cpp).


## Giảm chờ UI → BE → Pi

- UI kiểm tra map, endpoint, workflow và snapshot song song; không xếp các GET độc lập thành nhiều lượt chờ tuần tự. BE vẫn kiểm tra quyền và trạng thái thực khi nhận POST.
- Khi outbox EXECUTE_STEP hoặc CANCEL_STEP được tạo, BE đánh thức publisher sau commit ngay. Vẫn giữ worker định kỳ để phục hồi/retry khi restart hoặc hàng đợi bận; cùng commandId được chống gửi lặp qua lastPublishAt.
- Heartbeat không ghi lại orchestration work cho cùng một step Pi đang RUNNING hoặc đã có lệnh hợp lệ đang chờ bắt đầu. Kết quả/lỗi/hủy vẫn đánh thức graph bằng sự kiện.
- Scheduler deadline đọc một projection nhẹ, không lấy khóa/ghi generation mỗi giây cho MOVE còn thời gian. Hết hạn command, step, workflow hoặc có ý định hủy/skip vẫn được xử lý. Server WAIT tiếp tục được kiểm tra đúng hạn.
- Khi Pi gửi global plan mới qua HTTPS, BE thông báo SSE sau commit ngay, thay vì chờ heartbeat tiếp theo. Pose trực tiếp vẫn tách khỏi DB và không cấp quyền di chuyển.
- Không bỏ hai mốc xác nhận đã ghi bền COMMAND_ACK/STEP_STARTED trước khi có tác động vật lý; giảm truy vấn và tranh chấp khóa xung quanh các mốc đó. Chưa có số đo MOVE thực tế sau lần sửa này; không cam kết độ trễ 0 ms.

## Dọn file kiểm thử trên robot

Đã sao lưu `pi/tools/test_*.py` về laptop, kiểm tra hash rồi xóa 43 file test không có tham chiếu từ code vận hành; cùng cache test giải phóng 502.936 byte. Giữ nguyên các công cụ mạng, gồm `tools/test_pi_dns.py`, journal, log, cấu hình TLS/CA/IP. Các test vẫn được giữ trong folder source Git để nhóm tự chạy trên laptop/CI.


## Sửa tiếp độ trễ 03/11/2026

Lượt 1b219d55…: queued 19:00:22.466 UTC, outbox tạo 27.740, publish 29.828, Pi COMMAND_ACK 30.381, BE xử lý ACK 33.236, Pi STEP_STARTED 33.848, BE xử lý STARTED 35.659, Nav2 goal 37.163. Khoảng 14,7 giây queued → goal; chưa tính thời gian tạo request.

Pi khi kiểm tra: RAM dùng 891 MiB/7,9 GiB, swap 0, throttled=0x0. Phần chậm chính trước Nav2 nằm ở BE/DB và lượt orchestration, không phải bằng chứng Pi quá tải.

- Lưu inbox và áp dụng COMMAND_ACK/STEP_STARTED trong một transaction, ACK chỉ sau commit. Rollback/replay, scope, hash/sequence và nhánh durable rejection giữ nguyên.
- InnoDB xác nhận deadlock secondary index ix_job_robot_status với PRIMARY khi đổi status. Đổi thành tìm ID không khóa, khóa PRIMARY từng job rồi kiểm tra lại trạng thái/robot.
- Chọn Pi step và enqueue command khi đủ điều kiện trong cùng transaction; không chờ lượt scheduler trung gian. Publisher sau commit và durable retry giữ nguyên.
- Refresh current navigation row và MQTT link sau khi khóa Robot, trước khi pin proof của route transaction. Tránh NOT_LOCALIZED giả do heartbeat cũ trong persistence context/REPEATABLE_READ khi tạo graph mất thời gian.
- Pi bổ sung STEP_START_TIMING cho từng lượt business ACK và NAV_PLAN_TIMING cho ROS compute/BE confirm. Không thêm sleep/goal vào log timing.
- Chỉ đọc source Hiwonder trong /home/ubuntu/ros2_ws/src/navigation và peripherals/joystick_control.py. Không chép launcher, bật thêm service hay bringup hãng. Launcher hãng có TimerAction 10 giây lúc khởi động; không phải nguyên nhân chờ mỗi MOVE của dự án.

Kiểm tra cách ly bằng MySQL: replay receipt không nhân đôi trạng thái/event; đổi status trong khi heartbeat chờ không deadlock; refresh heartbeat sau graph preparation. Fixture không chạy trên DB sản xuất.


## Kết quả kiểm thử sau tối ưu

- Thử MOVE ngắn khoảng 30 cm, 0,15 m/s: job `41d26c9b-54b4-4040-9647-52aed97ec3cb` SUCCEEDED. Lượt xác nhận tiếp `8175740b-36d7-458e-8de2-9de8ae62b537` cũng SUCCEEDED.
- Bản cuối phát snapshot command đã đóng băng ngay sau commit tạo outbox. Ghi PUBLISHING/sendAttempts=1 tại boundary này; fallback/retry vẫn dùng đầy đủ MqttPublicationPlans.command. Không ACK hoặc phát lệnh trước commit. Trạng thái vật lý chưa rõ sau crash vẫn phải recovery như trước.
- Bản cuối: job `a2dc2685-1999-43c2-9839-ea9f754fe9ce` SUCCEEDED. Queue 19:43:52.730355 UTC, Nav2 goal 19:44:03.884406 UTC: 11,15 giây, so với 14,70 giây của lượt baseline (giảm khoảng 24%). Đây là queue → Nav2, không phải thời gian toàn bộ quãng đường.
- Route API dùng workflow có sẵn: 2,875 giây (prepare 555 ms, tạo request 1333 ms trước commit). Lần tạo template đầu lâu hơn; không tạo lại template hay reset AMCL trước mỗi MOVE.
- Pi compute path 135 ms; HTTPS/BE xác nhận plan 889 ms. Hai business ACK 3562 ms + 2339 ms còn là phần chờ lớn. Không khẳng định đã đạt độ trễ bằng 0 hay dưới 1–2 giây.
- 60 mẫu pose trực tiếp: capturedAt Pi → receivedAt BE trung vị 465,4 ms, cao nhất 651,1 ms. Đây chưa tính thêm render trình duyệt. Không dùng số này thay cho trạng thái quyền chạy.
- Không thấy reconnect MQTT trong các lượt MOVE thử. Khởi tạo AMCL chỉ đầu phiên; GLOBAL ở góc nhìn thứ hai chưa hội tụ, nên sau khi người vận hành xác nhận robot chưa bị di chuyển đã dùng verified_pose của lượt thành công làm SEED và kiểm tra LiDAR lại. Không dùng tọa độ bất kỳ để ép READY.
- Sau thử: đóng phiên, dừng stack; BE không còn QUEUED/ACCEPTED/RUNNING/RECOVERY_REQUIRED. Pi journal không còn unresolved step hoặc outbox chưa ACK. Có 2 zombie zsh thuộc PID 1 tail từ trước; không phải node ROS đang chạy và không tiêu thụ CPU. Không kill PID 1/restart container chỉ để xóa hai entry đó.

Giới hạn kiến trúc còn lại: BE trên laptop phải thực hiện nhiều SQL qua đường mạng tới AWS cho mỗi transaction. Nếu cần thời gian nhận lệnh ở mức 1–2 giây thì nên đặt BE gần DB (cùng EC2/region) và đo lại; chỉ tăng timeout hoặc tắt heartbeat/AMCL không loại bỏ chi phí round-trip này.


## Giảm truy vấn lặp bổ sung

- COMMAND_ACK/STEP_STARTED mới: INSERT receipt ở dạng PROCESSED/response cuối trong cùng transaction với runtime.apply. Bỏ SELECT receipt vừa insert và UPDATE receipt sau đó (hai lượt SQL cho mỗi receipt mới). Nếu runtime sai, toàn bộ transaction rollback; Pi chưa nhận ACK. Nếu INSERT gặp trùng, dùng lại nhánh khóa/hash/producer-sequence cũ, không bỏ chống thực hiện trùng.
- MqttRuntimeEvents dùng schema/type/Job đã xác thực để chọn executor; không tải lazy JobTask chỉ để quay về cùng Job.
- JDBC `useLocalSessionState=true` tránh đọc/lập lại trạng thái autocommit/isolation đã do Spring/Hikari quản lý. Không bật useLocalTransactionState; không bỏ commit/rollback. Không đổi TLS/CA hoặc datasource.
- Pi dùng một telemetry snapshot cho context, chất lượng định vị và zone trong cùng một lần lặp MOVE/Spin. Không giữ snapshot sang tick sau, không tăng tuổi dữ liệu được chấp nhận; các vòng chạy sau vẫn lấy scan/TF mới. Các caller cũ tiếp tục hoạt động.
- Không thay bootstrap AMCL, lifecycle/map cache duration, vận tốc, footprint, ngưỡng vật cản hoặc luật zone trong lần tối ưu này.

Chú ý số đo: processed_at của receipt native mới là thời điểm stage trong transaction; dùng elapsed transaction tại MqttInboundRouter và STEP_START_TIMING trên Pi để so sánh hiệu năng, không dùng received_at → processed_at làm thời gian commit.

Tham khảo JDBC: [MySQL Connector/J Performance Extensions](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-performance-extensions.html).


### Đo thực tế sau giảm query

Job `0f8873ca-d622-4264-b6ee-b7887befc3d5` SUCCEEDED. Queue 19:58:08.789766 UTC → Nav2 goal 19:58:19.183350: **10,39 giây**, so với 11,15 giây của bản ngay trước (giảm khoảng 0,76 giây trong một lượt đo, không phải SLA).

Pi: acceptance ACK 3249 ms, started ACK 1973 ms, tổng 5222 ms; compute path 142 ms, BE confirm 815 ms. 60 mẫu pose có trung vị Pi → BE 437,8 ms, lớn nhất 634,4 ms. Không thấy MQTT reconnect trong MOVE thử. Đây là kiểm tra khởi động ngắn gần endpoint, không chứng minh đường dài hoặc nhiều endpoint đều mượt.

Probe bắt thêm deadlock của legacy dispatch-expiry `status IN (...) … FOR UPDATE`. Sau probe đã bỏ cả hai range lock Job còn lại (timeout dispatch và assignment); chỉ lấy ID không khóa rồi khóa theo PRIMARY/recheck. Unit kiểm tra timeout/assignment 7/7 qua. Thêm INSERT lịch sử vị trí heartbeat idempotent bằng message ID, bỏ exists + merge SELECT trước INSERT; regression trên MySQL xác nhận phát lại cùng heartbeat chỉ có một location record. Hai phần cuối đã qua kiểm thử riêng và được cài, chưa đo thêm một lượt chuyển động sau đó.

Độ trễ còn lớn nhất vẫn là các transaction xác nhận và độ trễ đường mạng tới DB. Không bỏ ACK bền hoặc thay telemetry giả để đạt số đẹp. Code cập nhật trong folder hiện tại, không tạo branch/commit/push; không bật launcher Hiwonder và không khởi tạo lại AMCL trước từng step.

## Một lần commit để chấp nhận và bắt đầu MOVE

Pi 0.14.1 và BE thương lượng capability `move-start-commit-v1`. Chỉ MOVE có navigationContext và capability đúng mới được gửi `startMode=ACCEPT_AND_START`. Pi ghi journal ACCEPTED, gửi một COMMAND_ACK với start metadata rồi chờ một EVENT_ACK. BE kiểm tra scope, producer HELLO, selected step, cancellation và frozen params; chuyển command STARTED và step RUNNING cùng transaction. ACK chỉ được gửi sau commit. Không còn lần chờ STEP_STARTED riêng trong mode mới. Peer cũ không bị diễn giải lại: vẫn dùng hai lượt xác nhận.

Giữ chống chạy lặp khi ACK mất, rollback khi identity/params sai, TTL trước tác động vật lý và bằng chứng dừng khi hủy. Không bỏ localization/TF/odom/LiDAR hoặc xác nhận plan. Pi chỉ dùng AMCL để bootstrap phiên và tiếp tục theo dõi pose; không khởi tạo lại AMCL trước mỗi MOVE.

Kiểm tra MySQL cách ly: ACK gộp và duplicate replay, sai producer rollback, dispatch servo commissioning không giả STAFF_VERIFIED đều qua. Các case trước về metadata thiếu/sai, unsolicited start, replay và legacy cũng qua. Pi có 7 kiểm tra ACK gating/cancel/TTL/outbox/pose qua. Đây là kiểm chứng giao thức; chưa có số đo chuyển động mới sau ACK gộp. Servo chưa cắm nên không phát lệnh thử servo.
