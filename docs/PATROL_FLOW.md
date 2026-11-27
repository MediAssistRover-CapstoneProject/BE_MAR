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

`intervalSec` tối thiểu 60 giây. Lịch và số vòng là hai khái niệm riêng: `repeatCount` điều khiển một job; interval điều khiển các lần đề nghị tạo job mới. Bản source ngày 23/11/2026 tạo job patrol `QUEUED` trong DB khi đến hạn, kể cả robot đang delivery/navigation hoặc OFFLINE. Job giữ robot và map của workflow; chưa phát MOVE khi chỉ mới xếp hàng. Khi đã có một patrol chưa kết thúc trên robot, lịch lặp gộp lượt mới vào ý định hiện có, không tích lũy backlog. Lịch ONCE chưa tạo được job giữ `ACTIVE` và cùng `nextRunAt` để thử lại; chỉ kết thúc sau khi có job bền vững hoặc cửa sổ lịch hết hạn.

Trước lượt chạy, BE kiểm tra map/hash, MQTT HELLO/heartbeat, localization, profile đã đồng bộ, không còn mapping/command cần recovery và không bị nhiệm vụ ưu tiên chiếm robot. `PATROL_FALL` cần Pi quảng bá capability `PATROL_FALL`. Crowd/restricted cần AI sensing bật và profile đúng robot, zone, loại sensing, map hash và frame; cấu hình profile qua `PUT /api/v1/admin/monitoring/robots/{robotId}/ai-profile`. Tạo profile không tự triển khai model hoặc producer AI.

### Hẹn giờ sau công việc đang chạy

Ví dụ dưới đây dùng workflow và robot đã đọc trong phiên kiểm tra, không tự tạo hoặc kích hoạt lịch production. Trước khi gửi, kiểm tra workflow vẫn published/acceptsNewJobs và thay `startsAt` bằng thời điểm tương lai thật. `startsAt`/`endsAt` nhận ISO-8601 có `Z` hoặc offset; `2026-11-24T08:00:00+07:00` cùng thời điểm với `2026-11-24T01:00:00Z`. Trường `timezone` dùng để tính CRON, không tự cộng thêm bảy giờ vào một timestamp đã có offset.

`POST /api/v1/schedules`, Bearer token ADMIN:

```json
{
  "workflowId": "f8232350-acbc-4d65-9210-a4153d388b54",
  "robotId": "0e3f1083-2546-4f45-8004-5eda5cb75a6f",
  "scheduleCode": "PATROL_ONCE_20261124_0800",
  "scheduleName": "Tuần tra sau công việc hiện tại",
  "scheduleType": "ONCE",
  "timezone": "Asia/Ho_Chi_Minh",
  "startsAt": "2026-11-24T08:00:00+07:00",
  "inputTemplateJson": {},
  "priority": 5,
  "misfirePolicy": "FIRE_ONCE"
}
```

Create trả HTTP 201 với `scheduleId`, `status=PAUSED`, `nextRunAt=null`. Sau đó gọi `POST /api/v1/schedules/{scheduleId}/activate` không cần body; trả `ACTIVE` và `nextRunAt`. Scheduler mặc định quét mỗi 10 giây (`app.operations.schedule-scan-ms`), nên đây không phải bộ hẹn giờ chính xác từng giây. Tạo lịch hoặc activate trước giờ hẹn chưa tự tạo job.

Khi đến hạn trong delivery/navigation, job mới giữ `QUEUED`, `startedAt=null`; robot vẫn giữ nhiệm vụ hiện tại. DB lưu `scheduleFireAt` bằng giờ hẹn, còn JobResponse hiện chưa trả trường này. Lịch ONCE chuyển `RETIRED` ngay khi job được lưu, không có nghĩa patrol đã chạy xong. Sau khi công việc foreground kết thúc và robot đủ điều kiện thật, cùng job chuyển `RUNNING`, rồi `SUCCEEDED`/`FAILED`/`CANCELLED` hoặc `RECOVERY_REQUIRED`. Khi có recovery/stop chưa xác nhận, chờ xử lý thay vì tự giành robot. Các nguồn VOICE/SOS/ASSIST/alert vẫn chặn theo chính sách hiện có.

Job đã xếp hàng lưu hash/frame của map tại thời điểm tạo. Robot phải trở lại đúng map và đúng revision, MQTT/định vị/profile phải sẵn sàng trước khi bắt đầu. Không ghim `navigation_session_id` cũ vào job lịch: phiên hiện tại được kiểm tra lại khi bắt đầu. Thay map asset hoặc frame sẽ giữ job chờ; hủy ý định cũ và tạo lịch/job phù hợp sau khi kiểm tra lại tuyến. `endsAt` giới hạn thời điểm tạo lượt mới, không tự hủy một job `QUEUED` đã được tạo.

- Xem lịch ONCE đã kết thúc: `GET /api/v1/schedules/{scheduleId}?includeDeleted=true` bằng ADMIN. Mặc định API ẩn `RETIRED`, kể cả lịch kết thúc tự nhiên.
- Tìm job: `GET /api/v1/jobs?robotId=...&workflowId=...`; đối chiếu `scheduleId` trong từng job và `lastRunAt` của lịch. API danh sách job hiện không có query `scheduleId`; JobResponse chưa trả `scheduleFireAt`.
- Dừng lượt tương lai: `POST /api/v1/schedules/{scheduleId}/pause`. Job đã được tạo cần hủy riêng bằng `POST /api/v1/jobs/{jobId}/cancel` với `{"reason":"..."}`.

Biến thể INTERVAL: thay `scheduleType` bằng `INTERVAL`, thêm `intervalSec: 300`; không gửi `cronExpression`. Biến thể CRON chạy 08:00 mỗi ngày: `scheduleType=CRON`, `cronExpression="0 0 8 * * *"`, không gửi `intervalSec`. CRON Spring có sáu trường, bắt đầu bằng giây; `startsAt` là cận dưới, lượt đầu phải khớp CRON trong `timezone` đã chọn. ONCE không được gửi interval hoặc cron.

`misfirePolicy` nhận đúng `SKIP` hoặc `FIRE_ONCE`. Khi activate sau `startsAt`, `FIRE_ONCE` thử một lượt ngay; `SKIP` chọn lượt kế tiếp, ONCE đã quá giờ chuyển `RETIRED`. Trong lượt scan trễ sau khi lịch đã ACTIVE, source hiện chưa áp dụng lại policy cho từng misfire: patrol gộp tối đa một lượt rồi tính kỳ sau từ thời điểm scan hiện tại. Vì vậy INTERVAL có thể trôi pha khi scan trễ; không diễn giải `SKIP` là cam kết bỏ mọi lượt trễ trong lúc BE ngừng chạy.

Bản source còn dùng một transaction cho cả batch tối đa 50 lịch; lỗi chưa được xử lý riêng của một lịch/workflow có thể rollback batch và chờ scan sau. Chưa bổ sung lịch sử lỗi/skip riêng cho schedule. Các giới hạn này độc lập với việc lưu job `QUEUED`; cần theo dõi log nếu một lịch giữ due lâu mà chưa có job. Thay đổi này chưa được triển khai chỉ bằng việc cập nhật source.

### Bằng chứng vận hành đã đọc

Kiểm tra bổ sung ngày 23/11/2026 nâng tổng lên 120 kiểm tra trong 16 lớp hiện có, không lỗi/bỏ qua. Dispatcher luôn thử batch ưu tiên đầu tiên; nếu toàn bộ batch đầy chưa gán được robot, nó kiểm tra thêm một batch phía sau và luân phiên qua các lượt scan. Nhờ vậy nhiều job chờ robot/map khác không giữ mãi các job có thể chạy ở phía sau. Thay đổi dispatcher vẫn ở source BE, cần push/deploy.

Sau job `FAILED` thông thường đã có bằng chứng dừng, robot trở về IDLE khi không còn chủ chuyển động khác; các job chờ được xét tiếp theo ưu tiên, cùng ưu tiên theo thời gian gửi. `RECOVERY_REQUIRED`, stop chưa chốt, cargo chưa xử lý hoặc incident đang mở tiếp tục giữ robot. Job chờ gắn phiên navigation đã hết hạn/đổi không tự chuyển sang phiên mới: cần hủy/đối soát job cũ và tạo yêu cầu trong phiên đúng. Không đóng phiên navigation sau mỗi job vì các job tiếp theo còn dùng phiên đó.

Giới hạn Pi còn lại ngoài hàng đợi: pending cảnh báo giám sát té ngã liên tục hiện giữ trong RAM với cửa sổ retry khoảng 10 giây. Mất mạng dài có thể làm cảnh báo hết hạn trước khi BE nhận; độ bền của cảnh báo này khác SQLite outbox lưu receipt step. Muốn lưu bền sự kiện cần đồng bộ chính sách nhận sự kiện trễ/chống trùng của BE, chưa thuộc bản sửa hàng đợi này.

Kiểm tra source ngày 23/11/2026: 110 kiểm tra trong 15 lớp hiện có đã qua, không lỗi/bỏ qua; gồm lưu ORM H2 và truy vấn phân trang lịch, lịch đến hạn khi bận, hold cho patrol chưa chạy, MQTT map scope, ba delivery nối tiếp, stop/recovery, nhường và tiếp tục cùng patrol. Đây là kiểm tra cô lập, không tạo lịch/job hoặc chạy bánh trên production. Không cần migration hoặc bản vá Pi mới cho các thay đổi hàng đợi này; source BE vẫn cần push/deploy. Hiện DB chưa có Schedule nào để tự kích hoạt theo giờ.

Trong phiên kiểm tra ngày 23/11/2026 chưa có bản ghi Schedule production, nên chưa có bằng chứng phần cứng cho đường hẹn giờ mới. Nhật ký hiện có xác nhận một đường nhường voice riêng: patrol `3315625a…` bắt đầu 17:42:00 (UTC+7), nhận VOICE hold 17:42:44.845 và SUSPENDED 17:42:45.953; job voice `6b0c9c59…` chạy 17:43:20.771–17:43:53.386, hold giải phóng 17:43:53.785, cùng patrol tiếp tục 17:43:54.857 và hoàn tất 17:46:35.527. Đây là bằng chứng nhường/tiếp tục cùng job trong log, chưa chứng minh lịch đến hạn khi delivery đang chạy hoặc độ an toàn dừng vật lý.

## Nhường nhiệm vụ và tiếp tục

Trạng thái đang thực thi là `RUNNING`, robot `BUSY`. Khi có tác vụ ưu tiên, voice, incident hoặc phiên media ASSIST, patrol chuyển `SUSPENDING`, yêu cầu dừng operation qua MQTT, rồi chuyển `SUSPENDED` sau bằng chứng dừng vật lý. `SUSPENDED` giữ tuyến và robot được gán nhưng không chiếm motor.

Patrol chưa bắt đầu giữ `QUEUED` kể cả đang có hold voice/delivery/manual; chưa có operation để yêu cầu dừng. Khi nguồn ưu tiên kết thúc, BE bỏ đúng hold đó rồi xét admission. Manual resume cũng dùng được cho patrol `QUEUED` để bỏ manual hold. Job chờ này không ép map lên heartbeat hiện tại hoặc cản mở/đóng phiên navigation và recovery của nhiệm vụ khác. Các lượt scan lịch đi luân phiên qua các batch due để lịch ONCE bị chặn không giữ mãi đầu danh sách; `nextRunAt` gốc của lượt đó vẫn được giữ.

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

### Bổ sung ngày 26/11/2026: chuyển điểm dự phòng và dừng quan sát

Hai tính năng cần triển khai đồng bộ BE, AI và Pi. Migration V70 thêm chính
sách/ngân sách endpoint group; V71 lưu lease quan sát. Migration V72 thống nhất
nhóm endpoint dùng một chính sách `ON_PATH_UNAVAILABLE`, giữ nguyên cờ
`allowFallback` và ngân sách đã cấu hình. Triển khai BE với migration trước,
sau đó AI và Pi lúc stack dừng, rồi mở phiên mới để thương lượng capability.
Catalog MAR-STEP-2 đã pin không đổi. Không sửa các migration đã áp dụng.

Migration V73 bỏ loại endpoint chờ chưa có handler riêng, chuyển các bản ghi
đó về `WAYPOINT`, giữ nguyên ID, tọa độ, room và thành viên group. Danh mục
endpoint còn `WAYPOINT`, `PARK`, `CHARGER`. Nếu cần điểm chờ nghiệp vụ, dùng
`WAYPOINT` cùng step `WAIT`, không cần loại endpoint riêng.

**Nhóm endpoint:** các API tạo/cập nhật nhóm và `room-pair` nhận thêm:

```json
{
  "fallbackTrigger": "ON_PATH_UNAVAILABLE",
  "pathSearchBudgetSec": 10
}
```

Đây là các field bổ sung, không phải toàn bộ body PUT. Khi cập nhật qua
`PUT /api/v1/endpoint-groups/{id}`, giữ các field map, tên, mã, phòng, trạng
thái và selection policy hiện tại. `ON_PATH_UNAVAILABLE` là giá trị duy nhất
của `fallbackTrigger`, dùng mặc định khi API không gửi field này. Không yêu cầu
robot đã tới trong bán kính cửa. `fallbackRadiusM` còn trong DTO/DB để giữ tương
thích dữ liệu nhưng không quyết định chuyển điểm. Ngân sách nhận số nguyên
5–30 giây, mặc định 10. Trang `mapping.html` chỉ cấu hình ngân sách, không còn
lựa chọn cơ chế gần cửa. `allowFallback=false` vẫn tắt chuyển điểm dự phòng.
Tạo job mới sau cập nhật; snapshot job đã ghim chính sách cũ bị từ chối rõ ràng
thay vì tự đổi ý nghĩa của nhiệm vụ đang lưu.

Job phải chọn group, không chọn riêng endpoint 1. BE gửi Pi một đích cụ thể
kèm `path_search` sau khi kiểm tra quyền chọn đích. Pi có capability
`group-path-budget-v1`, tối đa một lần phục hồi trong cùng ngân sách, lưu thời
gian theo selection UUID qua reconnect/tiếp tục tuần tra. Không tính thời gian
đi bình thường hoặc tạm ngưng vào ngân sách. Timeout MOVE phải lớn hơn hoặc
bằng ngân sách +5 giây. Chỉ sau lỗi đường có bằng chứng mới và xác nhận dừng
mới chọn thành viên kế tiếp. Lỗi planner không phản hồi, scan/TF/localization
không được đổi thành lỗi cửa bị chặn. Không giảm footprint/inflation để ép path.

Mẫu tuần tra mới do `POST /api/v1/admin/patrol-workflows/templates` với body
`{"checkpointCount":3}` (3–11) tạo định nghĩa V2. V2 ghi checkpoint `BLOCKED`
và bỏ sensing của checkpoint đó khi hết điểm trong group, rồi sang checkpoint
kế tiếp. Mẫu V1 và job cũ giữ định nghĩa cũ. Lịch và job mới dùng workflowId
V2 trả về, thông qua các API lịch/job đang có; không tạo một hàng đợi riêng.

**Quan sát nghi ngã:** AI trả thêm ứng viên cùng người duy trì một giây, dùng
chung inference. Pi chuyển yêu cầu bất đồng bộ trước kiểm tra depth/TF hoặc
LOCALIZED. Chỉ MOVE tuần tra có cơ chế này; không tự tạm ngưng delivery/escort.
Capability `fall-observation-v1` và monitor CONTINUOUS phải có. Không dùng màu
vàng hoặc person confidence riêng lẻ làm bằng chứng té ngã.

| API | Quyền / ý nghĩa |
|---|---|
| `POST /api/v1/pi/fall-observations` | DEVICE, xin tạm ngưng đúng MOVE đang chạy |
| `GET /api/v1/pi/fall-observations/{id}` | DEVICE, đọc xác nhận dừng và cửa sổ quan sát |
| `POST /api/v1/pi/fall-observations/{id}/complete` | DEVICE, kết thúc riêng hold quan sát |
| `GET /api/v1/robots/{id}/fall-observation` | ADMIN/STAFF, trạng thái mới nhất; 404 nếu chưa có |
| `POST /api/v1/pi/fall-events` | DEVICE, sự kiện đủ bằng chứng; thêm `observationId` tùy chọn |

Body yêu cầu quan sát:

```json
{
  "observationId": "<UUID mới, giữ nguyên khi retry>",
  "monitoringSessionId": "<phiên giám sát Pi>",
  "trackingRevision": "<UUID tracker AI>",
  "jobId": "<job tuần tra>",
  "jobStepId": "<MOVE đang chạy>",
  "operationKey": "<operation hiện tại>",
  "detectedAt": "<ISO timestamp của ảnh>",
  "trackId": 1,
  "candidateReason": "LOW_HORIZONTAL_POSTURE",
  "continuousDurationSec": 1.0,
  "captureId": "<định danh ảnh>"
}
```

Hai reason khác là `PARTIAL_LOW_HORIZONTAL_POSTURE`, `END_ON_LOWER_BODY`.
BE đối chiếu robot/job/operation/map/transport, chống trùng, giới hạn yêu cầu
và lưu lease ban đầu tối đa 20 giây. Dùng quy trình tạm ngưng và MQTT hiện có:
`SUSPENDING → physicalStopConfirmed → STOP_CONFIRMED → OBSERVING`.
Camera ổn định 0,5 giây, sau đó quan sát tĩnh 3 giây. Pi đặt lại thời gian quan
sát nếu có chuyển động mới, trong cùng lease không gia hạn vô hạn. BE chừa
tối đa 12 giây sau cửa sổ quan sát cho báo cáo, vẫn bị giới hạn lease ban đầu.

Complete body `{"outcome":"CLEAR"}` hoặc `{"outcome":"INCONCLUSIVE"}`.
Kết quả `INCIDENT` cần `incidentId` thuộc đúng robot/map và incident nghi ngã
đang mở. Ưu tiên gửi sự kiện có `observationId` để BE thiết lập INCIDENT hold
trước khi giải phóng observation hold trong cùng giao dịch. Lease hết hạn
không được làm mất một sự kiện té ngã độc lập còn hợp lệ. Reconciler giải
phóng riêng nguồn observation khi hết hạn/đổi context; các hold voice, media,
incident hoặc job ưu tiên vẫn có hiệu lực. Chưa xác nhận dừng vẫn đi recovery.

Depth/TF dùng sau lúc dừng để xác minh gần sàn/vị trí người. Thiếu tọa độ không
được ghi vị trí robot thành vị trí bệnh nhân. Nhánh camera-only đủ điều kiện
vẫn có thể yêu cầu staff xem camera, với vị trí người chưa biết. Không có staff
SOS phù hợp thì incident vẫn lưu và admin đang hoạt động nhận thông báo.

Cooldown báo cáo gắn định danh người thực sự tạo sự kiện. Observation có cặp
tracking revision/trackId và giới hạn toàn robot; reset tracker không gắn nhầm
người. Báo cáo gửi lỗi không đặt cooldown chung 60 giây cho mọi người. Incident
đã CLOSED/RESOLVED/FALSE_ALARM không che cảnh báo mới.

Khi tuần tra tiếp tục, dùng cùng endpoint selection và ngân sách đã dùng;
thời gian quan sát không tính thành path failure, không tăng index group.
Giữ nguyên vận tốc, planner/controller, footprint và inflation.

Kiểm tra offline không thay thế nghiệm thu phần cứng: chặn cửa endpoint 1,
kiểm tra stop receipt rồi endpoint 2; giả lập tư thế thấp khi patrol đang MOVE,
kiểm tra lease, cửa sổ tĩnh, incident/camera và tiếp tục sau khi staff xử lý.
Không xác nhận độ chính xác AI hoặc thời gian dừng thực tế chỉ từ unit test.

Đọc production ngày 18/11/2026 ghi nhận chưa có workflow, lịch hoặc job PATROL và chưa có `AiSensingProfile`. Không tự tạo tuyến trên map bất kỳ. Sau khi admin chọn tuyến, cần nghiệm thu robot thật: một vòng, lặp nhiều vòng, tạm dừng/tiếp tục giữa MOVE, delivery ưu tiên, voice, cảnh báo té ngã, restricted alert, mất MQTT và đổi map khi đang tạm dừng. Các kiểm thử tự động không thay cho đo dừng vật lý và kiểm tra camera/AI trên robot.

## Giao diện và bản Pi đã đồng bộ ngày 18/11/2026

Trang `/admin/patrol` có phần **Tuần tra robot** lấy map, endpoint và robot từ server. Admin chọn tối đa 5 điểm theo thứ tự, kiểm tra rồi lưu workflow. Lưu lịch tạo PAUSED; bấm Kích hoạt mới cho phép chạy. Phần xem trước/Gazebo phía dưới vẫn độc lập với điều khiển robot. Giao diện theo dõi job/vòng/checkpoint/hold, phân biệt dừng lịch với suspend/cancel job.

Pi `192.168.1.134` đã cài 6 file hỗ trợ face và bản sửa nhường giám sát té ngã khi đang xác thực face, không cài model hoặc file test. `staff_face_enabled=true`, `unlock_policy=STAFF_VERIFIED`, `ai.patrol_fall.enabled=true`, mode CONTINUOUS, depth enabled. Cấu hình gốc đang là mapping, chưa có navigation_session: mở phiên navigation mới rồi chạy lệnh BE sinh và khởi tạo vị trí. Face và monitor chỉ được tạo trong phiên navigation; không dùng phiên quét map để mở thùng. Không phát lệnh bánh/servo trong lượt cài.

Kiểm tra: BE 260 test, không lỗi, 1 skipped vì thiếu MySQL kiểm thử cục bộ; 9 kiểm tra face chạy bằng RAM trên Pi và kiểm tra nhường face qua; UI thử trình duyệt bằng API giả kiểm chứng tạo tuyến/lưu PAUSED/kích hoạt rõ ràng và modal duyệt ảnh đúng revision. Source BE/FE vẫn chờ push/deploy; chưa kích hoạt lịch production hoặc tự chọn tuyến thay admin. Restricted/crowd cần AI sensing profile và provider còn thiếu trong DB; tuyến fall-only có thể cấu hình trước.

Kiểm tra tiến trình Pi: không có component MAR đang chạy; vendor bringup khởi động sẵn có một cây riêng, supervisor MAR sẽ xử lý cây đó trước khi mở driver dự án để tránh chạy trùng. Có 5 zombie boot cũ do PID1 `tail` giữ; không phải worker hay job còn chạy. Dọn/chuyển init container cần thao tác bảo trì riêng, không kill zombie hoặc PID1 trong lúc chạy.
