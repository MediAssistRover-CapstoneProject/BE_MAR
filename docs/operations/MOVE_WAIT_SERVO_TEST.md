# Thử các step MOVE, WAIT và CONTROL_HARDWARE

Mẫu: `MOVE_A → WAIT 5 giây → MOVE_B → OPEN_SERVO → WAIT 5 giây → CONFIRM_COLLECTION → CLOSE_SERVO`.

Definition dùng các step type hiện có, một workflow/job và cùng phiên navigation. Không khởi tạo lại AMCL giữa các MOVE. Hai MOVE có trần 0,4 m/s; giới hạn Pi, zone và LiDAR tiếp tục áp dụng.

## Tình trạng phần cứng

Servo chưa cắm khi cập nhật bản này: **chưa thử đóng/mở vật lý**. Cấu hình hiện tại MG996R, cổng S6, ROS ID 4, LOCK 1500, UNLOCK 2400, duration 1 giây. Không có cảm biến xác nhận cửa/khóa; kết quả `published=true, controller_ack=false, verified=false` chỉ xác nhận phần mềm đã phát lệnh. Không suy ra người nhận đã lấy hàng từ thời gian chờ hoặc từ kết quả servo.

Mẫu này là **COMMISSIONING_ONLY**, do ADMIN tạo và đánh dấu `defaultInputsJson.hardware_test_mode`. BE phải bật `MQTT_ALLOW_COMMISSIONING_UNLOCK=true`, Pi phải có `unlock_policy=COMMISSIONING_ONLY`, `CONTROL_HARDWARE` được bật và `CART_MAIN` ánh xạ `cargo_lock`. Không nhận cờ commissioning từ request để cấp quyền mở cửa. Workflow giao hàng chính thức vẫn cần luồng xác thực STAFF_VERIFIED; mẫu này chưa thay thế luồng xác thực đó.

## Chuẩn bị definition bằng Swagger

Đã chuẩn bị trên DB AWS hiện tại: workflow `85475023-28d6-4012-a199-ef9781993b12`, trạng thái PUBLISHED, 7 step/22 transition. Chưa tạo request/job chạy. Có thể dùng ID này ngay khi phần cứng sẵn sàng; không cần tạo lại definition trên cùng DB.

File JSON nằm trong `src/main/resources/static/navigation-servo-demo-workflow.json`; tải qua `/navigation-servo-demo-workflow.json` cần Bearer token. Các bước dưới dùng khi chuẩn bị trên DB khác.

1. `POST /api/v1/admin/workflows/graph`, body là toàn bộ file JSON trên.
2. Lấy `workflow.workflowId`, gọi `POST /api/v1/admin/workflows/{id}/validate`, rồi `/publish`.
3. Chỉ sau khi servo được lắp, robot có phiên navigation READY, chọn endpoint A/B trên cùng map để tạo request. Chuẩn bị/publish definition không làm robot chạy.

## Tạo và chạy một job

`POST /api/v1/service-requests/navigation`, header `Idempotency-Key` là UUID mới cho mỗi lần chạy. Điền UUID thật, không giữ các nhãn minh họa bên dưới:

```json
{
  "mapId": "MAP_UUID",
  "workflowId": "WORKFLOW_UUID",
  "robotId": "0e3f1083-2546-4f45-8004-5eda5cb75a6f",
  "navigationSessionId": "CURRENT_SESSION_UUID",
  "destinationEndpointId": "ENDPOINT_B_UUID",
  "targetMode": "ENDPOINT",
  "navigationMode": "STANDARD",
  "passengerCount": 1,
  "priority": 50,
  "note": "Test MOVE WAIT servo; commissioning only",
  "data": {"first_endpoint_id": "ENDPOINT_A_UUID"}
}
```

Lấy `request.serviceRequestId` rồi `POST /api/v1/service-requests/{id}/submit`. Không dùng `/navigation-routes` cho mẫu hỗn hợp này: API đó tạo graph chỉ gồm MOVE.

Theo dõi `GET /api/v1/jobs/{jobId}` hoặc SSE `/api/v1/jobs/{jobId}/stream`. Khi `CONFIRM_COLLECTION` là step đang RUNNING, lấy `jobStepId` của đúng lượt đó và gọi:

`POST /api/v1/job-steps/{jobStepId}/responses`, header `Idempotency-Key` mới:

```json
{
  "responseType": "INTERACTION_CONFIRMED",
  "data": {"confirmed": true},
  "note": "Đã lấy hàng và rút tay khỏi cơ cấu cửa"
}
```

Chỉ kết quả `confirmed=true` mới chuyển sang CLOSE_SERVO. Gửi `false`, hết hạn xác nhận hoặc lỗi sau bước mở cửa sẽ vào RECOVERY_REQUIRED; không tự đóng cửa. Gọi API xác nhận trước khi CONFIRM_COLLECTION được chọn sẽ bị từ chối. Phát lại cùng key/body chỉ ghi nhận một lần, không đóng servo thêm lần nữa. Nếu hủy khi cửa đang mở, cần kiểm tra cơ cấu và đóng có chủ đích; không xóa journal để bỏ qua trạng thái này.

## MOVE nhận lệnh qua một lượt ACK

Pi 0.14.1 quảng bá `move-start-commit-v1` trong HELLO của phiên navigation. BE đóng băng `startMode=ACCEPT_AND_START` trong command MOVE, Pi trả một COMMAND_ACK kèm yêu cầu start, BE commit acceptance và RUNNING trong cùng transaction rồi gửi một EVENT_ACK. Pi chỉ bắt đầu sau ACK này; không gửi STEP_STARTED riêng cho mode mới. Peer cũ dùng luồng hai lượt như trước. STEP_RESULT, hủy/dừng, kiểm tra path và telemetry vẫn giữ riêng; “một ACK” không có nghĩa bỏ kết quả hoặc kiểm tra vật cản.

Không phát lệnh servo hoặc chạy mẫu workflow này trong quá trình cài bản vá. Chưa đo lại thời gian MOVE vật lý của bản ACK gộp; không dùng thời gian test giả lập làm số đo vận hành.
