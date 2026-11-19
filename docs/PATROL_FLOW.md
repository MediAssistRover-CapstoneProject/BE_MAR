# Luồng tuần tra MAR

Tuần tra dùng workflow schema 3, scheduler, hàng đợi và MQTT hiện có. Admin chọn map đã lưu và danh sách checkpoint theo thứ tự; robot không tự suy ra rằng phải ghé mọi endpoint của map.

## Cấu trúc và vòng lặp

Mỗi checkpoint tạo hai step: `MOVE` với `mode=PATROL`, sau đó `EXECUTE_SENSING`. Có 1–5 checkpoint trong một workflow.

| Cấu hình | Ý nghĩa |
| --- | --- |
| `mapId` | Map ACTIVE, có asset hash và frame. Mọi endpoint/zone phải thuộc map này. |
| `checkpoints[].endpointId` | Điểm đứng quan sát, phải active, accessible và có hướng `yawRad`. |
| `sensingType=PATROL_FALL` | Quan sát nghi té ngã; không truyền `zoneId`. |
| `sensingType=RESTRICTED_ZONE` | Nhận diện người trong zone KEEP_OUT; điểm đứng của robot ở ngoài vùng cấm. |
| `sensingType=CROWD_DENSITY` | Đo mật độ trong zone đã cấu hình. |
| `fallDetection=true` | Bật thêm quan sát té ngã trong các cửa sổ sensing của tuyến. |
| `repeatCount` | Bỏ trống: một vòng; 1–1000: số vòng; 0: liên tục đến khi hủy. |
| `maxSpeedMps` | Mặc định 0,25 m/s; vẫn chịu giới hạn phần cứng và các guard trên Pi. |
| `moveTimeoutSec` | Mặc định 60 giây mỗi MOVE, nhận 15–120. |
| `sensingWindowSec` | Mặc định 12 giây; cần ít nhất 3 giây khi bật quan sát té ngã. |

Các vòng dùng cùng `jobId`; mỗi lần chạy lại step có operation key mới. Thời hạn workflow tính riêng từng vòng, cộng phần thời gian đã tạm dừng. Không phát lại operation cũ để tiếp tục.

`fallDetection` của workflow áp dụng cho các step sensing. Module giám sát liên tục khi IDLE/MOVE là cấu hình riêng của Pi; không được suy ra rằng chỉ cần bật cờ workflow là module đó đã chạy.

## Các API và cách chuẩn bị

Tạo tuyến bằng tài khoản ADMIN:

1. `POST /api/v1/admin/patrol-workflows/preview`: kiểm tra map/checkpoint, trả graph để xem trước; chưa tạo job.
2. `POST /api/v1/admin/patrol-workflows`: tạo, validate và publish workflow; trả `workflowId`. Tuyến published tương ứng có thể được tái sử dụng.
3. `POST /api/v1/schedules`: tạo lịch PAUSED với workflow trên, robot cụ thể, `priority` 0–10 và `inputTemplateJson={}`.
4. Khi đã xem đúng tuyến và robot sẵn sàng, `POST /api/v1/schedules/{id}/activate`.

Body preview/create dưới đây là mẫu; thay các UUID theo map và endpoint đã chọn:

```json
{
  "name": "Tuần tra quan sát",
  "mapId": "<MAP_ID>",
  "checkpoints": [
    {"endpointId": "<ENDPOINT_ID_1>", "sensingType": "PATROL_FALL"},
    {"endpointId": "<ENDPOINT_ID_2>", "sensingType": "PATROL_FALL"}
  ],
  "maxSpeedMps": 0.25,
  "moveTimeoutSec": 60,
  "sensingWindowSec": 12,
  "fallDetection": true,
  "repeatCount": 1
}
```

Body tạo lịch mẫu; thay UUID và `startsAt` bằng thời gian UTC mong muốn:

```json
{
  "workflowId": "<WORKFLOW_ID>",
  "robotId": "<ROBOT_ID>",
  "scheduleCode": "PATROL_DEMO_01",
  "scheduleName": "Tuần tra theo lịch",
  "scheduleType": "INTERVAL",
  "timezone": "Asia/Ho_Chi_Minh",
  "startsAt": "<UTC_ISO_TIMESTAMP>",
  "intervalSec": 300,
  "inputTemplateJson": {},
  "priority": 1,
  "misfirePolicy": "FIRE_ONCE"
}
```

`intervalSec` tối thiểu 60 giây. Lịch và số vòng là hai khái niệm riêng: `repeatCount` điều khiển một job; interval điều khiển các lần đề nghị tạo job mới. Scheduler bỏ lượt nếu robot chưa sẵn sàng hoặc đã có patrol chưa kết thúc, không dồn hàng đợi sau thời gian mất kết nối. Với lịch ONCE, lượt không đủ điều kiện vẫn kết thúc lịch mà không tạo job; dùng INTERVAL nếu muốn thử ở lượt sau.

Trước lượt chạy, BE kiểm tra map/hash, MQTT HELLO/heartbeat, localization, profile đã đồng bộ, không còn mapping/command cần recovery và không bị nhiệm vụ ưu tiên chiếm robot. `PATROL_FALL` cần Pi quảng bá capability `PATROL_FALL`. Crowd/restricted cần AI sensing bật và profile đúng robot, zone, loại sensing, map hash và frame; cấu hình profile qua `PUT /api/v1/admin/monitoring/robots/{robotId}/ai-profile`. Tạo profile không tự triển khai model hoặc producer AI.

## Nhường nhiệm vụ và tiếp tục

Trạng thái đang thực thi là `RUNNING`, robot `BUSY`. Khi có tác vụ ưu tiên, voice, incident hoặc phiên media ASSIST, patrol chuyển `SUSPENDING`, yêu cầu dừng operation qua MQTT, rồi chuyển `SUSPENDED` sau bằng chứng dừng vật lý. `SUSPENDED` giữ tuyến và robot được gán nhưng không chiếm motor.

Delivery có ưu tiên cao hơn patrol có thể vào hàng đợi; chỉ bắt đầu sau khi patrol xác nhận dừng. BE giữ các lý do độc lập `JOB`, `VOICE`, `MEDIA`, `INCIDENT`, `MANUAL`. Hoàn tất một lý do không xóa các lý do còn lại. MONITOR xem camera không tạo hold hội thoại; ASSIST có tạo hold.

Khi mọi hold đã được giải phóng, không còn tác vụ chờ/đang chạy và MQTT/localization/profile/map còn hợp lệ, patrol tự tiếp tục. Nếu dừng giữa MOVE, robot đi lại tới cùng checkpoint bằng operation mới. Nếu MOVE đã thành công trước khi nhận dừng, chuyển tới SENSE thay vì đi lại endpoint. Đổi map/hash hoặc còn stop chưa xác nhận sẽ chặn tiếp tục.

| API | Hành vi |
| --- | --- |
| `GET /api/v1/jobs?robotId=...&workflowId=...` | Tìm job của tuyến. |
| `GET /api/v1/jobs/{id}` | Trạng thái authoritative, các step và trường `patrol`: vòng, checkpoint, phase, holds, lý do chặn resume. |
| `GET /api/v1/jobs/{id}/events` | Nhật ký chuyển step, vòng, suspend/resume. |
| `POST /api/v1/jobs/{id}/suspend` | Body `{"reason":"..."}`; thêm manual hold, chờ xác nhận dừng. |
| `POST /api/v1/jobs/{id}/resume` | Body `{"reason":"..."}`; chỉ bỏ manual hold, không vượt qua SOS/voice/delivery. |
| `POST /api/v1/jobs/{id}/cancel` | Hủy vĩnh viễn job, tắt tự resume; vẫn cần settle/recovery nếu stop chưa xác nhận. |
| `POST /api/v1/schedules/{id}/pause` | Dừng các lượt lịch tương lai; không tự dừng job patrol đang chạy. |

Trong lúc SENSE, `patrol.targetEndpointId` vẫn là checkpoint đã đến của đúng vòng hiện tại; việc đọc trạng thái không phát lệnh chuyển động.

## Cảnh báo và staff SOS

Pi gửi nghi té ngã trong checkpoint qua `POST /api/v1/pi/patrol-fall-events`; BE kiểm tra device/job/step/map/session, lưu detection và incident rồi chuyển vào luồng staff review. Nhánh phát hiện người nằm sẵn cần depth/TF đồng bộ để chứng minh gần sàn. Không cần QR delivery để gửi cảnh báo này.

Restricted intrusion tạo alert `PATROL_RESTRICTED_INTRUSION` thông qua cùng `SosResponderRouting`, có cooldown theo zone. Đây là cảnh báo giám sát, không tự biến thành incident té ngã hoặc bắt đầu video call.

Phòng giám sát lấy từ setting `sos.monitoring-room-id` hoặc cấu hình override của restricted alert. Router chọn đúng một staff đang trực phù hợp; không phát đồng loạt cho toàn bộ staff. Thiếu phòng/ca trực hoặc có nhiều responder trùng ca sẽ lưu trạng thái routing chưa gán để xử lý, không coi là đã có người nhận.

Staff được gán xem camera, xác nhận incident rồi xử lý/đóng qua luồng emergency hiện có. `GET /api/v1/emergency-incidents/{id}/location` trả vị trí ghi nhận; `/response-route` dựng đường từ phòng SOS sau xác nhận, ưu tiên tọa độ người từ RGB-D hợp lệ. Nếu thiếu tọa độ người, API phải nói rõ fallback robot viewpoint. Incident chưa kết thúc tiếp tục giữ patrol; kết thúc cuộc xem camera đơn thuần không đồng nghĩa đã xử lý xong incident.

## Trạng thái kiểm tra và nghiệm thu

Đọc production ngày 18/11/2026 ghi nhận chưa có workflow, lịch hoặc job PATROL và chưa có `AiSensingProfile`. Không tự tạo tuyến trên map bất kỳ. Sau khi admin chọn tuyến, cần nghiệm thu robot thật: một vòng, lặp nhiều vòng, tạm dừng/tiếp tục giữa MOVE, delivery ưu tiên, voice, cảnh báo té ngã, restricted alert, mất MQTT và đổi map khi đang tạm dừng. Các kiểm thử tự động không thay cho đo dừng vật lý và kiểm tra camera/AI trên robot.

## Giao diện và bản Pi đã đồng bộ ngày 18/11/2026

Trang `/admin/patrol` có phần **Tuần tra robot** lấy map, endpoint và robot từ server. Admin chọn tối đa 5 điểm theo thứ tự, kiểm tra rồi lưu workflow. Lưu lịch tạo PAUSED; bấm Kích hoạt mới cho phép chạy. Phần xem trước/Gazebo phía dưới vẫn độc lập với điều khiển robot. Giao diện theo dõi job/vòng/checkpoint/hold, phân biệt dừng lịch với suspend/cancel job.

Pi `192.168.1.134` đã cài 6 file hỗ trợ face và bản sửa nhường giám sát té ngã khi đang xác thực face, không cài model hoặc file test. `staff_face_enabled=true`, `unlock_policy=STAFF_VERIFIED`, `ai.patrol_fall.enabled=true`, mode CONTINUOUS, depth enabled. Cấu hình gốc đang là mapping, chưa có navigation_session: mở phiên navigation mới rồi chạy lệnh BE sinh và khởi tạo vị trí. Face và monitor chỉ được tạo trong phiên navigation; không dùng phiên quét map để mở thùng. Không phát lệnh bánh/servo trong lượt cài.

Kiểm tra: BE 260 test, không lỗi, 1 skipped vì thiếu MySQL kiểm thử cục bộ; 9 kiểm tra face chạy bằng RAM trên Pi và kiểm tra nhường face qua; UI thử trình duyệt bằng API giả kiểm chứng tạo tuyến/lưu PAUSED/kích hoạt rõ ràng và modal duyệt ảnh đúng revision. Source BE/FE vẫn chờ push/deploy; chưa kích hoạt lịch production hoặc tự chọn tuyến thay admin. Restricted/crowd cần AI sensing profile và provider còn thiếu trong DB; tuyến fall-only có thể cấu hình trước.

Kiểm tra tiến trình Pi: không có component MAR đang chạy; vendor bringup khởi động sẵn có một cây riêng, supervisor MAR sẽ xử lý cây đó trước khi mở driver dự án để tránh chạy trùng. Có 5 zombie boot cũ do PID1 `tail` giữ; không phải worker hay job còn chạy. Dọn/chuyển init container cần thao tác bảo trì riêng, không kill zombie hoặc PID1 trong lúc chạy.
