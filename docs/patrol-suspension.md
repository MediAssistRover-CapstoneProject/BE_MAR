# Tuần tra có thể tạm ngắt — API và vận hành

## Mô hình

Job tuần tra schema 3 giữ nguyên `jobId`, workflow version/hash, dữ liệu và lịch sử khi nhường robot:

`RUNNING → SUSPENDING → SUSPENDED → RUNNING`

`SUSPENDING` vẫn giữ quyền thực thi. Chỉ chuyển sang `SUSPENDED` sau bằng chứng dừng của Pi hoặc xác minh lệnh chưa từng được phát. Dừng một thao tác Pi bằng `CANCEL_STEP` không đồng nghĩa hủy job tuần tra. Nếu không xác minh được dừng, hệ thống giữ `RECOVERY_REQUIRED` và không tự giao chuyển động mới.

Mỗi lần tiếp tục thao tác đã hủy có `jobStepId`/`operationKey`/command ID mới, thuộc cùng job. Step đã thành công được giữ. Nếu MOVE đã thành công đúng lúc yêu cầu dừng, hệ thống giữ kết quả và tiếp tục bước sau; nếu bị ngắt giữa đường, đi tiếp cùng endpoint từ vị trí định vị hiện tại. Cửa sổ quan sát bị ngắt chạy lại đầy đủ.

## Tạo tuyến và bắt đầu

1. Chuẩn bị bản đồ ACTIVE, endpoint active/accessible, phiên dẫn đường và profile đã được Pi xác nhận.
2. ADMIN dùng `POST /api/v1/admin/patrol-workflows/preview`, sau đó `POST /api/v1/admin/patrol-workflows` với danh sách checkpoint theo thứ tự. `repeatCount` là trường tùy chọn: mặc định 1, 1–1000 là số vòng, 0 là liên tục. Các trường checkpoint, sensing và commissioning vẫn được kiểm tra như trước.
3. Dùng API Schedules hiện có: `POST /api/v1/schedules`, sau đó `POST /api/v1/schedules/{id}/activate`. Chọn `robotId` cụ thể, `priority` 0–10, `inputTemplateJson: {}`. Lịch ONCE phù hợp để bắt đầu một phiên nhiều vòng/liên tục; thời gian `startsAt` phải là ISO-8601 thực tế. INTERVAL tối thiểu 60 giây dùng cho các lượt riêng, không phải cách tiếp tục job đang treo.
4. Đọc job được sinh ra từ lịch bằng danh sách Jobs. Lịch đến hạn khi robot chưa sẵn sàng có thể bỏ qua lần đó; không báo đã bắt đầu trước khi job thực sự RUNNING. Không tạo lượt tuần tra mới khi robot còn một job tuần tra chưa kết thúc.

Ví dụ phần cấu hình tuyến:

```json
{
  "name": "Tuần tra tầng 1",
  "mapId": "<UUID bản đồ ACTIVE>",
  "checkpoints": [
    {"endpointId": "<UUID A>", "zoneId": "<UUID vùng A>", "sensingType": "CROWD_DENSITY"},
    {"endpointId": "<UUID B>", "zoneId": "<UUID vùng B>", "sensingType": "CROWD_DENSITY"}
  ],
  "maxSpeedMps": 0.25,
  "moveTimeoutSec": 60,
  "sensingWindowSec": 12,
  "fallDetection": false,
  "repeatCount": 3
}
```

Đây là ví dụ cấu trúc; thay các UUID bằng dữ liệu thật. Sensing yêu cầu producer/profile được commissioning. `PATROL_FALL`/`fallDetection` chỉ hoạt động khi phiên bản Pi thực tế triển khai đúng capability và contract; BE không giả lập kết quả hoặc tự nhận Pi hỗ trợ.

## Điều khiển cùng job

Các endpoint dưới đây cần ADMIN hoặc STAFF có quyền truy cập job. Body: `{"reason":"Lý do thao tác"}`.

| Endpoint | Ý nghĩa |
|---|---|
| `POST /api/v1/jobs/{id}/suspend` | Thêm lý do giữ MANUAL; trả trạng thái hiện tại, có thể còn SUSPENDING |
| `POST /api/v1/jobs/{id}/resume` | Giải phóng MANUAL; yêu cầu tiếp tục, vẫn đợi các lý do khác và điều kiện thực thi |
| `POST /api/v1/jobs/{id}/cancel` | Kết thúc hẳn ý định tuần tra; schema 3 đợi dừng có xác nhận, không tự resume |
| `GET /api/v1/jobs/{id}` | Job/task/step và trường `patrol` |
| `GET /api/v1/jobs/{id}/stream` | SSE `job-state`, cùng dữ liệu với REST |
| `GET /api/v1/jobs/{id}/events` | Lịch sử yêu cầu treo, xác nhận, tiếp tục và từng vòng |

`patrol` chứa `round`, `repeatCount`, `continuous`, `resumeRequested`, timestamps, selected step, endpoint, phase, `holds` và `resumeBlockedReason`. Trường này null đối với job không phải tuần tra. Robot snapshot tách job thực thi hiện tại khỏi danh sách job tuần tra bị treo.

Resume thủ công không xóa hold SOS/VOICE/MEDIA/JOB. Gọi lại cùng thao tác không phát thêm lệnh di chuyển nếu điều kiện chưa thỏa mãn. Nếu đã hủy hoặc đang RECOVERY_REQUIRED, dùng luồng đối chiếu recovery thay vì cố resume.

## Tự động nhường việc

* Các job foreground được nhận vào hàng đợi và có priority cao hơn tuần tra có thể yêu cầu robot nhường; nhiệm vụ không phải tuần tra không tự bị hủy để nhường.
* Nhiệm vụ gắn phiên dẫn đường giữ đúng robot và kiểm tra lại phiên khi thực thi.
* Các nguồn INCIDENT, MEDIA ASSIST và VOICE tạo lý do giữ riêng theo UUID nguồn. Một nguồn kết thúc chỉ giải phóng chính nó.
* Voice producer phải gọi `POST /api/v1/pi/voice/interactions` khi bắt đầu và `/interactions/{id}/completion` khi toàn bộ cuộc hội thoại, phát âm thanh và chờ hỏi tiếp đã thực sự kết thúc. STT trả text hoặc TTS tạo xong WAV không phải kết thúc phát lời nói.
* Nghi té ngã đi qua luồng staff xác minh hiện có. Đóng giao diện SOS hoặc chỉ kết thúc cuộc gọi không tự xác nhận sự cố đã được giải quyết.
* Sau khi việc ưu tiên xong, bộ điều phối xét lại các lý do giữ, lệnh chưa đối chiếu, hàng đợi, heartbeat, định vị và bản đồ trước khi tiếp tục cùng job. Đóng web không kết thúc tuần tra.

Holds được dựng lại từ dữ liệu nguồn sau khi BE khởi động lại. Callback sau commit giúp tránh đảo thứ tự khóa Job/Robot. Các scheduler `app.operations.scheduler-enabled` phải bật để có điều phối và reconciliation tự động.

## Camera AI và phạm vi hàng đợi

`media-call.html` nhận khung người/nghi té ngã qua topic LiveKit
`mar.ai.overlay.v1` từ đúng participant robot của phiên đang xem. Video gốc
không bị encode lại để vẽ khung. JWT chỉ cấp `canPublishData` cho robot;
staff vẫn không được gửi metadata. AI cần trả trường `overlay` và Pi cần bản
media bridge/fall observer tương ứng; BE/UI mới vẫn xem video bình thường khi
Pi cũ chưa có metadata. Khung cũ hơn 1,5 giây, sai phiên, rỗng hoặc mất video
được xóa. Đây là kết quả từ ảnh AI gần nhất, không phải overlay khớp tuyệt đối
từng frame. Tỷ lệ phần trăm trên khung là độ tin cậy nhận diện người, không
phải xác suất té ngã; nhãn nghi té ngã luôn cần staff xác minh.

Hàng đợi nghiệp vụ nằm trong DB BE. Pi chỉ nhận một operation tại một thời
điểm, giữ journal và trả ACK/kết quả; broker MQTT không giữ hàng đợi nghiệp vụ
thứ hai. Job chưa gán robot có thể chờ bộ dispatch ưu tiên phân robot rảnh.
Sau FAILED/CANCELLED đã có bằng chứng dừng, robot về IDLE và job tiếp theo
được xét; RECOVERY_REQUIRED vẫn chặn chuyển động mới.

Request delivery/voice ghim robot đang chạy nhiệm vụ thông thường có thể bị
từ chối vì tác vụ không thể ngắt; tiếp nhận để nhường hiện được thiết kế cho
patrol schema 3. Companion mới nghe wake trong patrol khi BE cho phép, mở
VOICE hold rồi chờ cả trạng thái Pi IDLE và xác nhận BE đã dừng trước khi hội
thoại. SOS/fall qua API tạo INCIDENT hold. Không coi kiểm thử BE/mock là bằng
chứng các trường hợp này đã chạy trên robot thật.

## Wake và nhận diện người dùng chung (bản source 20/11/2026)

- `GET /api/v1/pi/voice/availability?voiceInteractionId=...` tách `wakeAllowed`
  khỏi `conversationReady`; không đổi BUSY thành IDLE. Pi giới hạn truy vấn
  một lần/giây khi cần xin nhường hoặc đang hội thoại. SOS/ASSIST, cảnh báo
  vùng cấm, job foreground và operation chưa chốt đều có quyền chặn.
- Companion mới gửi `leaseSupported=true` khi mở voice; gửi
  `POST /api/v1/pi/voice/interactions/{id}/heartbeat` mỗi 10 giây.
  `app.voice.interaction-lease-sec` mặc định 45 giây. BE hết hạn phiên bị bỏ
  lại và giải phóng riêng VOICE hold; không hủy job dẫn đường đã được tạo.
  Companion cũ không gửi cờ này vẫn giữ contract hoàn tất cũ.
- EC2 `/v1/fall/frame` thêm `ground_people_version=1` và tối đa tám người với
  track ID, độ tin cậy và hai điểm cổ chân từ cùng lượt suy luận. Không tải
  model thứ hai lên Pi. Khung bao chạm sàn không thay thế điểm chân quan sát.
- Pi dùng RGB-D đã đăng ký, TF tại thời điểm ảnh, cùng session/map/hash và
  revision định vị để chiếu điểm chân lên bản đồ. Người phải vào trong vùng
  KEEP_OUT liên tục qua nhiều mẫu; mặc định xác nhận 0,75 giây, inset 8 cm,
  cooldown 30 giây. Điều chỉnh tại `ai.patrol_fall.restricted_monitor`.
  Thiếu chân/depth/TF cho kết quả chưa xác định, không báo vùng đã trống.
- `POST /api/v1/pi/restricted-zone-events`: `eventId`, `jobId`, `jobStepId`,
  `detectedAt`, `algorithm`, `confidence`, `zoneId`, `trackId`, `personLocation`.
  Person location gồm xM/yM/rangeM/capturedAt/navigationSessionId/mapId/
  mapAssetHash/initializationRevision/depthSkewMs/floorHeightM. BE kiểm tra
  đúng step PATROL đang chạy, map, zone và vị trí ground trước khi lưu.
  Retry cùng event/body trả receipt cũ dù job đã tạm dừng.
- Cảnh báo `PATROL_RESTRICTED_INTRUSION` tạo nguồn giữ `RESTRICTED_ALERT`,
  gửi staff SOS được phân công. Không có staff vẫn lưu để admin xử lý.
  ACK giữ nguyên hold; `POST /api/v1/alerts/{id}/resolve` hoặc `/false-alarm`
  với `{ "reason": "..." }` giải phóng. Trang `media-call.html` giữ cả OPEN
  và ACKNOWLEDGED trong danh sách, cho xem camera và kết luận.
- Checkpoint RESTRICTED_ZONE dùng cùng stream. Nó lấy profile đóng băng từ
  `GET /api/v1/pi/restricted-zone-events/profile?zoneId=...`; model phải khớp
  profile đã cấu hình. Chỉ trả negative khi depth xác nhận toàn vùng đang thấy.

Triển khai theo thứ tự: BE cùng migration `V64__voice_interaction_lease.sql`,
AI camera, rồi source Pi. Bật `--restricted-monitor` trong lệnh phiên navigation
(hoặc `ai.patrol_fall.restricted_monitor.enabled=true` khi stack dừng); mặc định
nhánh mới tắt để Pi cũ/mới không gọi BE chưa cập nhật. Depth đăng ký phải bật.
Giữ workflow đã publish bất biến: tạo tuyến đúng map/zone/checkpoint, thử bằng
lịch PAUSED, chỉ kích hoạt khi có người vận hành. Thay đổi source không tự
thay tuyến cũ, không tự bật lịch và không phát lệnh bánh.

## Triển khai và kiểm chứng

* Migration mới `V61__patrol_job_suspension.sql` thêm trạng thái, dữ liệu vòng/treo và bảng holds. Triển khai cùng phiên bản BE tương ứng; không triển khai code cũ vào schema/trạng thái mới mà chưa có kế hoạch tương thích.
* Mặc định POM bỏ qua test. Chạy thực sự bằng `mvn.cmd -Dmaven.test.skip=false test`.
* Test dùng mock/H2 xác minh trạng thái, dữ liệu, chuyển giao và định danh. H2 ORM/query validation không thay thế chạy migration MySQL 8 trên DB staging.
* Trước khi vận hành robot: kiểm tra MQTT thật cho MOVE → suspend → stop confirmed → priority job → cùng job resume; thêm mất ACK, mất kết nối, SOS đến lúc hội thoại kết thúc và hủy khi đang treo. Không dùng trạng thái HTTP thành công làm bằng chứng motor đã dừng.

## Kết quả kiểm tra trong workspace

Đã chạy `mvn.cmd -o clean package -Dmaven.test.skip=false`: 216 test, 215 đạt, 0 lỗi, 1 bỏ qua. Test bị bỏ qua là kiểm tra schema MySQL độc lập cần `MAP_SCHEMA_TEST_JDBC_URL`; chưa áp dụng migration lên DB vận hành. Artifact là `target/backend-0.0.1-SNAPSHOT.jar`.

Các test mới bao gồm chuyển giao giữa service thật với repository giả lập, giữ cùng job/endpoint/vòng, timeout loại trừ thời gian treo, nguồn giữ độc lập, khôi phục holds, API HTTP/quyền truy cập, ORM và truy vấn trên H2, chặn phát lệnh trong khoảng chờ callback, ACK lịch sử đến muộn. Chưa gửi lệnh MQTT thật hoặc xác nhận phần cứng. Web tuần tra hiện có và tiến trình voice/fall chạy trên Pi phải sử dụng các contract này trước khi kiểm thử toàn hệ thống.


## Bản đã cài, kiểm tra sau nửa đêm 21/11/2026 (UTC+7)

- BE artifact SHA-256: `a42fe6eded5a85e7ec736af3144c9eca28a1865820aa2186a24eaaad9a1afeb6`.
  Service UP; Flyway MySQL 8 đã áp dụng V64. Đây là artifact cài trực tiếp từ
  workspace, không phải commit đã push. Cần push source để CI tiếp theo giữ bản này.
- AI camera release `9799e4cf99585e9efca5574d908f7a827e7d2edc`:
  fall-ai active, READY, ground_people_version=1; AI voice vẫn active.
- Pi `192.168.1.140`: cài và xác minh hash 10 file production; không cài test.
  Continuous fall/depth/shared restricted và voice đều enabled. Stack giữ dừng.
- Sao lưu source trên Pi:
  `/home/ubuntu/mediassist-mqtt-2026-10-08/pi/state/patch-backups/navigation-continuity-20261120T165936606441Z`.
  Cấu hình:
  `/home/ubuntu/mediassist-mqtt-2026-10-08/pi/state/patch-backups/shared-patrol-config-20261120T165936Z`.
- Hậu kiểm: không có job queued/running/suspended/recovery; journal Pi không có
  ACCEPTED/RUNNING/RECOVERY_REQUIRED; outbox chưa ACK=0. Phiên navigation CLOSED.
  Availability trả ROBOT_NOT_CONNECTED do stack đang dừng, không còn lỗi API 404.
- Chưa chạy thử bánh hoặc kịch bản tuần tra trên robot thật. Mở phiên navigation
  mới và chạy lệnh BE sinh để kiểm thử có người vận hành.


## Kiểm tra vận hành ngày 22/11/2026 và cách thử

Kết quả đọc API trực tiếp: robot `0e3f1083-2546-4f45-8004-5eda5cb75a6f`
đang chọn map `f5448b68-db7c-4240-8215-d3de3c8d2934`; map này có 0 zone
trong navigation profile. Không có Schedule và không có staff on-duty đã
check-in khi kiểm tra. Workflow patrol đang publish `9b6f776e-3052-4615-b56b-e85f8e514c44`
vẫn tham chiếu map cũ `bdf6503a-d38e-4bde-9a67-763e91668593`, gồm một checkpoint
CROWD_DENSITY và một PATROL_FALL; nó không phải tuyến vùng cấm của map hiện tại.
Phòng nhận SOS đang cấu hình là `9aa7ada8-30cc-4537-9544-8fd413a0e442` (`SOS_05`).
Đây là snapshot, không phải các ID hardcode trong triển khai.

Pi `192.168.1.148` đang dừng stack; continuous fall, registered depth, shared
restricted monitoring, voice và media đều bật trong base config. MOVE được
thêm trong generated navigation worker, không cần bật MOVE trên base mapping
worker. Một request chẩn đoán ảnh trống chỉ tới server AI (không gửi sự kiện
cho BE) trả `yolov8n-pose-fall-v5`, `ground_people_version=1`.

Bản sửa source ngày 22/11 chuyển kết quả inference checkpoint cho shared
restricted monitor khi `fall_detection=true`; trước đó checkpoint có thể đợi
mẫu mãi dù AI vẫn chạy. Khi resume thẳng ở SENSE sau khi observer khởi tạo lại,
Pi chỉ nhận job làm patrol sau khi BE xác thực profile của đúng checkpoint;
đổi active step trong lúc HTTP đang chạy thì hủy mẫu đó. Không tạo model,
camera owner hay hàng đợi job thứ hai trên Pi.

Trình tự thử có người vận hành, sau khi triển khai source tương ứng:

1. Tạo polygon KEEP_OUT bằng `POST /api/v1/zones`, dùng đúng `mapId` của phiên.
   Chọn endpoint quan sát nằm ngoài polygon, hướng camera nhìn thấy vùng và
   chân người; robot không đi vào vùng KEEP_OUT. API danh sách `/zones` hiện
   không có query filter mapId: client phải lọc trường mapId trong dữ liệu.
2. Kiểm tra `GET /api/v1/robots/{robotId}/navigation-zones`: đúng map,
   restrictedZoneCount > 0 và profileAcknowledged=true sau khi Pi kết nối idle.
3. ADMIN tạo/chọn đúng ca trực bằng `/api/v1/admin/shifts`; staff SOS thực hiện
   `POST /api/v1/staff/shifts/{shiftId}/check-in`. Xác nhận
   `GET /api/v1/staff/on-duty?mapId={mapId}&roomId={sosRoomId}` có đúng một
   staff. Chỉ tạo ca chưa đủ; nhiều staff cùng check-in sẽ AMBIGUOUS_SHIFT.
4. Với checkpoint RESTRICTED_ZONE, cấu hình profile bằng
   `PUT /api/v1/admin/monitoring/robots/{robotId}/ai-profile`:
   `zoneId`, `sensingType=RESTRICTED_ZONE`, `modelVersion=yolov8n-pose-fall-v5`,
   `producerRef=SHARED_POSE_RGBD`, `enabled=true`,
   `personPositionsEnabled=false`, và commissioningNote mô tả phép thử thực tế.
   Profile gắn map asset hash/frame; đổi map phải cấu hình lại. Crowd producer
   cũ là nhánh riêng, không coi shared restricted là producer CROWD_DENSITY.
5. Preview rồi publish bằng `/api/v1/admin/patrol-workflows/preview` và
   `/api/v1/admin/patrol-workflows`. Checkpoints theo thứ tự:
   `{endpointId,zoneId,sensingType:RESTRICTED_ZONE}`; đặt fallDetection=true,
   maxSpeedMps=0.25, sensingWindowSec=10 và repeatCount=0 để tuần tra liên tục.
   Giới hạn 1–5 checkpoints; hệ thống không tự đi qua mọi endpoint.
6. Mở phiên navigation mới trên đúng map, dùng lệnh BE sinh; thêm
   `--voice-chat --fall-monitor --restricted-monitor`. Chọn `--load-profile`
   theo thân robot thực tế. Chờ LOCALIZED và profile ACK. Chưa tạo chuyển động
   từ thao tác đọc source hay preview workflow.
7. Tạo lịch ONCE bằng `POST /api/v1/schedules` với workflowId, robotId,
   scheduleCode/name, timezone=Asia/Bangkok, startsAt là thời điểm thật,
   inputTemplateJson={}, priority thấp hơn job foreground (ví dụ 5), và
   misfirePolicy hợp lệ trong API; activate bằng `/schedules/{id}/activate`.
8. Khi patrol RUNNING, một người đi vào polygon trong khung camera. Pi cần
   RGB-D đồng bộ, TF tại thời điểm ảnh và tọa độ chân nằm trong vùng; khung
   người trên video chưa đủ chứng minh người nằm trong polygon. Đọc log
   RESTRICTED_REVIEW_REQUESTED; staff đọc `/api/v1/alerts?status=OPEN` và mở
   MONITOR bằng `/api/v1/media-sessions` theo robotId. Đây là cảnh báo vùng
   cấm, không tự tạo cuộc gọi ASSIST hay tự bật micro của staff.
9. `POST /api/v1/alerts/{id}/acknowledge` chỉ ghi nhận đã nhận; patrol vẫn giữ.
   Sau khi kiểm tra, gọi `/resolve`, hoặc `/false-alarm` với reason. Chỉ khi
   các hold còn lại đã hết và kết nối/định vị hợp lệ mới resume.
10. Trong patrol, nói Mờ A/Em A. Kiểm tra job cũ SUSPENDING → SUSPENDED, Pi
    dừng trước khi nói. Hỏi thông tin rồi kết thúc hội thoại; cùng patrol job
    RUNNING trở lại. Thử yêu cầu dẫn đường, xác nhận đích: job foreground chạy
    trước, hoàn tất rồi patrol tiếp tục. Đang delivery hoặc SOS ASSIST không
    được giành robot bằng wake. GET /jobs/{id}, /events hoặc /stream cho biết
    holds và resumeBlockedReason; RECOVERY_REQUIRED cần xử lý bằng chứng dừng.

API điều khiển: `/api/v1/jobs/{id}/suspend`, `/resume`, `/cancel`, body reason.
Resume chỉ giải phóng hold MANUAL; không giải phóng incident/voice/alert khác.
Hủy patrol là kết thúc ý định tuần tra, không phải tạm dừng. Queue nằm trong
DB BE; Pi nhận một operation. MQTT vận chuyển lệnh và ACK, không xếp thêm một
hàng đợi nghiệp vụ độc lập.

Camera nhiều người: source mới hỗ trợ MONITOR tối đa bốn người xem khi robot
đang kết nối (LiveKit room có robot + 4 viewers), cấu hình
`MEDIA_MONITOR_MAX_VIEWERS`. Dùng
`POST /api/v1/media-sessions/{id}/observer-token` với viewerId UUID riêng cho
mỗi trình duyệt; token chỉ nhận video/metadata. Rời camera trên UI chỉ ngắt
người xem đó. ASSIST vẫn robot + người nhận SOS được chỉ định. Pi phát một
luồng video, EC2 LiveKit phân phối các subscriber; không chạy thêm model hay
encode camera riêng cho từng người xem. FE_WEB_MAR chưa có LiveKit client;
client đang được cập nhật ở static media-call.html/media-call.js của BE.

Các thay đổi này đang ở source workspace; chưa deploy BE hoặc cài ba file
production Pi trong lượt audit này. Ca trực, polygon và lịch chưa được tạo
vì cần chọn hình học vùng cấm cùng lịch trực thực tế. Không tự kích hoạt
lịch hoặc phát lệnh di chuyển để kiểm tra phần cứng.
