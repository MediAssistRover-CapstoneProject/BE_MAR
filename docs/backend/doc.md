# TASK: HOÀN THIỆN BACKEND MEDIASSIST ROVER — WORKFLOW ENDPOINT A → B → C, REALTIME ROBOT, API CHO FRONTEND 3D

Bạn là Senior Java Spring Boot Engineer, Backend Architect, Workflow Engine Architect, MQTT/ROS 2 Integration Engineer và Realtime Systems Engineer.

Hãy đọc toàn bộ repository `D:\DoAn\BE_MAR` hiện tại và trực tiếp hoàn thiện những chức năng còn thiếu.

**Mục tiêu:** Backend phải hỗ trợ workflow điều hướng theo Endpoint ID, nhiều MOVE tuần tự, realtime trạng thái Job và robot, đồng thời cung cấp API đầy đủ để Frontend Web có thể điều khiển và giám sát hệ thống mà người dùng không cần thao tác bằng terminal.

## NGUYÊN TẮC QUAN TRỌNG NHẤT

**AUDIT TRƯỚC — CHỈ SỬA PHẦN CÒN THIẾU.**

Nếu source Backend hiện tại đã có chức năng nào dưới đây và hoạt động đúng:

- Giữ nguyên implementation.
- Không viết lại.
- Không tạo API trùng chức năng.
- Không thay đổi response contract không cần thiết.
- Chỉ bổ sung test hoặc sửa lỗi có bằng chứng cụ thể.

Không được tạo lại một hệ thống Realtime, Workflow Engine, MQTT Dispatcher hay Telemetry Service nếu source hiện tại đã có cơ chế tương đương.

Hãy phân loại từng chức năng thành:

- `EXISTING_AND_VERIFIED`: Đã có và kiểm thử đạt yêu cầu.
- `EXISTING_BUT_INCOMPLETE`: Đã có nhưng thiếu một số hành vi.
- `MISSING`: Chưa triển khai.
- `ROBOT_SIDE_DEPENDENCY`: Phụ thuộc Adapter/ROS 2, không thể giải quyết hoàn toàn chỉ bằng sửa BE.

Chỉ sửa các trường hợp thật sự cần thiết.

---

# PHẦN I — GIỮ NGUYÊN KIẾN TRÚC STEPTYPE VÀ MQTT

## 1. CHỈ CÓ CHÍNH XÁC 11 STEPTYPE

Giữ nguyên 11 StepType:

1. `MOVE`
2. `INTERACT`
3. `DATA_SERVICE`
4. `AUTHENTICATE`
5. `CONTROL_HARDWARE`
6. `HANDLE_PAYLOAD`
7. `EXECUTE_SENSING`
8. `NOTIFY`
9. `WAIT`
10. `CHECK_CONDITION`
11. `SET_CONTEXT`

Không tạo StepType mới.

Một workflow có ba chặng di chuyển thì sử dụng ba Step có type `MOVE`.

Tên như `MOVE_TO_A`, `MOVE_TO_B`, `MOVE_TO_C`, `RETURN_TO_A` chỉ là `stepCode`, không phải StepType.

Giữ nguyên chính sách phân chia Backend/Staff/UI/Robot hiện có nếu đã đúng.

## 2. MQTT RAILWAY

Giữ nguyên cấu hình:

`MQTT_ENABLED_STEP_TYPES=MOVE,CONTROL_HARDWARE,EXECUTE_SENSING,WAIT`

Không tự mở thêm StepType qua MQTT.

Backend MQTT username: `mar-be`.

ROS 2 Adapter MQTT username: `mar-pi-gazebo`.

Không thay đổi tài khoản, broker, password, robot ID, map ID hoặc MQTT topic nếu không có lý do được kiểm chứng.

Robot hiện mới xác nhận E2E StepType `MOVE`.

Không tự quảng bá các capability chưa được Adapter triển khai.

## 3. GIỮ NGUYÊN MQTT CONTRACT

Giữ tương thích với `MAR-STEP-2`, bao gồm:

- HELLO / HELLO_ACK
- HEARTBEAT
- Navigation Profile ACK
- EXECUTE_STEP
- COMMAND_ACK
- STEP_STARTED
- STEP_RESULT
- STEP_ERROR
- Event ACK
- MQTT outbox/inbox
- operationKey
- commandId
- correlationId
- transportSessionId
- Idempotency
- Timeout
- Recovery
- physicalStopConfirmed

Không tạo giao thức mới nếu giao thức hiện tại đủ dùng.

---

# PHẦN II — WORKFLOW ĐIỀU HƯỚNG BẰNG ENDPOINT ID

## 4. MỤC TIÊU NAVIGATION

Hệ thống phải hỗ trợ:

- Endpoint A → Endpoint B.
- Endpoint A → Endpoint B → Endpoint C.
- Endpoint A → Endpoint B → Endpoint A.
- Workflow có nhiều StepType xen giữa các MOVE.
- Staff chọn Endpoint trên FE.
- Admin thiết kế workflow qua FE.
- Robot thực thi từng MOVE theo thứ tự.
- Job chỉ SUCCEEDED khi hoàn thành toàn bộ workflow.

Không hardcode tọa độ Endpoint trong Java hoặc Frontend.

Endpoint ID được resolve bằng dữ liệu hợp lệ trong database và Navigation Profile.

Nav2 vẫn sử dụng tọa độ nội bộ để điều khiển robot, nhưng FE chỉ làm việc với Endpoint ID.

## 5. NAVIGATION REQUEST A/B

Audit các file:

- `ServiceRequestRequests.java`
- `ServiceRequestResponses.java`
- `NavigationRequest.java`
- `EscortRequest.java`
- `ServiceRequestServiceImpl.java`
- `RequestJobInputSnapshotFactory.java`
- `NavigationTargetService.java`
- `Endpoint.java`
- `EndpointRepository.java`

Kiểm tra `NavigationCreateRequest` đã hỗ trợ:

- `originEndpointId`
- `destinationEndpointId`
- `mapId`
- target mode và navigation mode hiện có.

Nếu đã hỗ trợ đầy đủ thì không sửa lại.

Nếu thiếu, bổ sung tương thích ngược.

Yêu cầu validation:

- Endpoint tồn tại.
- Endpoint ACTIVE.
- Endpoint thuộc map hợp lệ.
- A và B cùng map.
- Robot được phép hoạt động trên map.
- Endpoint không bị thay thế bằng tọa độ client tự gửi.
- Giữ nguyên cơ chế Idempotency.
- Không làm hỏng request Navigation cũ chỉ có đích.

Nếu cần thay đổi database, tạo Flyway migration mới; không sửa migration đã deploy.

## 6. JOB INPUT SNAPSHOT

Kiểm tra `RequestJobInputSnapshotFactory`.

Đối với Navigation Request A/B, Job phải có dữ liệu đã được validate tương đương:

```json
{
  "map_id": "<MAP_UUID>",
  "origin_endpoint_id": "<ENDPOINT_A_UUID>",
  "endpoint_id": "<ENDPOINT_B_UUID>",
  "target_mode": "ENDPOINT"
}
```

Không tin tưởng dữ liệu Endpoint được ghi đè qua client `data`.

Dữ liệu Job phải được đóng băng tại thời điểm tạo, có thể audit và replay theo chính sách hiện tại.

Nếu source đã triển khai đúng thì chỉ bổ sung regression tests.

## 7. NHIỀU MOVE TRONG MỘT WORKFLOW

Ví dụ:

`CHECK_CONDITION → MOVE_TO_A → INTERACT → MOVE_TO_B → MOVE_TO_C → SUCCEEDED`

Yêu cầu:

- Mỗi MOVE sử dụng một Endpoint ID hợp lệ.
- Mỗi MOVE có JobStep và execution identity riêng.
- Backend dispatch từng MOVE tuần tự.
- Chỉ chuyển MOVE tiếp theo khi MOVE trước thành công hợp lệ.
- Không phát nhiều MOVE song song cho cùng robot.
- Không tự động bỏ qua MOVE lỗi.
- Không hoàn thành Job trước khi đi hết workflow.
- Không tạo lại lệnh nguy hiểm khi restart.
- Không gửi lệnh trùng do retry hoặc reconnect.

Kiểm tra:

- `WorkflowOrchestratorService`
- `WorkflowRuntimeTransitionServiceImpl`
- `MqttStepDispatchService`
- `NavigationTargetService`
- Job/JobStep runtime services
- Các transition validator.

Nếu workflow engine đã hỗ trợ nhiều MOVE tuần tự đúng thì giữ nguyên, chỉ viết tests chứng minh.

## 8. VỊ TRÍ XUẤT PHÁT THỰC TẾ

Endpoint A được Staff chọn không đồng nghĩa robot đang ở A.

Backend cần kiểm tra dữ liệu vị trí robot đáng tin:

- Robot ID.
- Map ID.
- Pose trong frame `map`.
- Thời điểm đo.
- Độ mới dữ liệu.
- Sai số vị trí.
- Tolerance phù hợp.
- Trạng thái robot và navigation session.

Nếu robot chưa ở A, workflow có thể MOVE tới A.

Nếu robot đã ở A, việc bỏ qua MOVE chỉ được thực hiện khi policy và bằng chứng vị trí cho phép, không tự tạo STEP_RESULT giả.

Nếu telemetry không có hoặc quá cũ, không tự kết luận robot đang ở Endpoint A.

---

# PHẦN III — REALTIME ROBOT VÀ DIGITAL TWIN 3D

## 9. AUDIT REALTIME HIỆN TẠI TRƯỚC KHI SỬA

Đây là yêu cầu bổ sung quan trọng.

Hãy kiểm tra Backend hiện tại đã có những cơ chế nào:

- MQTT heartbeat.
- MQTT telemetry.
- `telemetry.map_pose`.
- Robot current status.
- `RobotLocationHistory`.
- Robot current location hoặc latest pose.
- Job events.
- Job SSE.
- Robot status event stream.
- Navigation Profile.
- Map/Endpoint catalog.
- Đường đi hoặc navigation progress.
- Các API phục vụ Digital Twin.

Kiểm tra code trong:

- `MqttInboundRouter`
- MQTT event/heartbeat DTOs và validators
- Robot entity, repository, services và controllers
- `RobotLocationHistory`
- Robot location mapper/response
- Job Event services
- SSE controllers
- Navigation Profile services
- Map/Endpoint controllers
- Các thành phần telemetry khác được tìm thấy trong repository.

**Nếu Backend đã có cơ chế Realtime đầy đủ và được tests xác nhận thì không triển khai lại.**

Không tạo nhiều SSE service hoặc nhiều bảng lịch sử vị trí cho cùng một chức năng.

## 10. REALTIME TRẠNG THÁI ROBOT

Frontend cần theo dõi:

- ONLINE / OFFLINE.
- IDLE / BUSY.
- Robot đang thực thi Job nào.
- Map hiện tại.
- Kết nối MQTT.
- Heartbeat freshness.
- Navigation Profile/session readiness.
- Thông tin pin nếu có dữ liệu thật.
- Cảnh báo và trạng thái lỗi.
- Thời điểm cập nhật cuối.

Hãy xác định dữ liệu nào Backend đang có thực tế.

Nếu đã có API GET trạng thái robot thì giữ nguyên.

Nếu thiếu khả năng cập nhật realtime cho FE, ưu tiên mở rộng cơ chế SSE/event hiện có.

Không tạo WebSocket mới chỉ vì cần realtime nếu SSE hiện tại đáp ứng tốt yêu cầu.

Trường hợp không nhận heartbeat trong khoảng timeout theo policy, FE phải có cách nhận biết robot đã OFFLINE hoặc mất kết nối.

Không giữ robot ở trạng thái ONLINE vĩnh viễn sau khi mất MQTT.

## 11. REALTIME VỊ TRÍ ROBOT CHO BẢN ĐỒ 3D

Frontend 3D cần nhận được vị trí robot thay đổi khi Nav2 đang điều hướng.

Dữ liệu cần có về mặt ngữ nghĩa:

- `robotId`
- `mapId`
- `xM`
- `yM`
- `yawRad`
- `frameId`
- `capturedAt`
- `receivedAt` nếu cần
- `fresh` hoặc thông tin tương đương
- `source`
- Thông tin chất lượng vị trí nếu hệ thống hiện có.

Đây là yêu cầu dữ liệu, không áp đặt tên DTO mới nếu Backend đã có contract tương đương.

Robot phải dùng pose thực từ ROS 2, điển hình TF `map → base_link` hoặc nguồn localization hợp lệ.

Không sử dụng riêng `/odom` như tọa độ map nếu chưa transform đúng frame.

Không tự suy ra tọa độ robot từ Endpoint đích.

Không lấy vị trí đã lập kế hoạch làm vị trí thực tế.

Không phát sinh tọa độ giả chỉ để Digital Twin hiển thị robot đang di chuyển.

Nếu source đã có `telemetry.map_pose` và `RobotLocationHistory`, kiểm tra toàn bộ đường đi dữ liệu:

`ROS 2 Adapter → MQTT heartbeat/telemetry → Backend validation → Robot location storage/current state → FE API`

Nếu pipeline đã hoàn chỉnh thì giữ nguyên.

Nếu thiếu một đoạn, chỉ bổ sung đoạn còn thiếu.

Nếu ROS 2 Adapter hiện chưa phát pose định kỳ, hãy ghi rõ đó là `ROBOT_SIDE_DEPENDENCY`; chỉ chuẩn bị contract/validator phía BE trong task này, không tự nhận Backend đã có dữ liệu realtime thật.

## 12. REALTIME API CHO FE 3D

FE cần hai khả năng:

**A. Lấy snapshot hiện tại**

Khi mở trang hoặc refresh:

- Lấy robot.
- Lấy map.
- Lấy Endpoint.
- Lấy vị trí gần nhất.
- Xác minh dữ liệu còn fresh.

**B. Nhận cập nhật liên tục**

Trong quá trình robot chạy, FE cần nhận được các update mới mà không phải gọi lại toàn bộ danh sách robot liên tục.

Hãy ưu tiên tái sử dụng API/SSE đã có.

Nếu chưa có robot location/status stream phù hợp, thiết kế bổ sung tối thiểu theo convention `/api/v1/`, ví dụ một endpoint stream scoped theo robot hoặc nhóm robot, nhưng chỉ khi thực sự cần.

Đừng tự tạo endpoint trùng chức năng với API đang có.

Yêu cầu Realtime:

- Có authentication/authorization.
- Không lộ MQTT credentials.
- Event có robotId và mapId.
- Hỗ trợ reconnect an toàn.
- FE có thể lấy lại snapshot sau reconnect.
- Không nhân bản dữ liệu khi subscriber reconnect.
- Không phát vị trí quá cũ như một vị trí mới.
- Có chính sách bounded buffer/backpressure.
- Có heartbeat/keepalive cho SSE nếu cần.
- Không làm quá tải database do lưu tất cả update tần suất cao.
- Không giảm độ an toàn của MQTT ingestion.
- Thực hiện theo tần suất cấu hình phù hợp với hệ thống hiện tại.

Phân biệt:

- Realtime Job status.
- Realtime robot status.
- Realtime robot pose.
- Navigation planned path.

Bốn loại dữ liệu này không được đánh đồng.

Nếu Backend mới chỉ có Job SSE nhưng chưa có robot pose stream, phải báo rõ phần nào còn thiếu.

## 13. ĐƯỜNG ĐI NAV2 VÀ TIẾN ĐỘ DI CHUYỂN

Frontend 3D cần phân biệt:

1. Robot đang đứng ở đâu.
2. Robot đang hướng về đâu.
3. Endpoint mục tiêu.
4. Đường đi Nav2 đã tính toán.
5. Tiến độ nhiệm vụ.

Audit Backend hiện có dữ liệu planned path hoặc progress hay chưa.

Nếu chưa có, không được giả lập rằng đường nối thẳng Endpoint A/B chính là tuyến Nav2 thật.

Đề xuất contract tối thiểu để sau này Adapter có thể gửi đường đi thực tế:

- Robot ID.
- Job ID/JobStep ID.
- Map ID.
- Các waypoint/poses phù hợp.
- Timestamp.
- Thời điểm plan được cập nhật.
- Version hoặc sequence nếu cần.

Chỉ bổ sung BE implementation nếu nó thực sự cần và không có cơ chế tương đương.

Nếu Adapter hiện chưa publish Nav2 path/feedback, đánh dấu `ROBOT_SIDE_DEPENDENCY`.

Không bắt buộc hoàn tất streaming planned path để xác nhận tính đúng đắn của MOVE; phải phân biệt tính năng cơ bản và tính năng Digital Twin nâng cao.

## 14. TELEMETRY PERIODIC VÀ HIỆU NĂNG

Backend cần sẵn sàng tiếp nhận telemetry định kỳ từ Robot/Pi.

Audit contract hiện tại trước khi bổ sung.

Yêu cầu:

- Telemetry chứa timestamp hợp lệ.
- Có cơ chế chống replay và dữ liệu cũ.
- Đúng robotId.
- Đúng session/map.
- Validation các giá trị số hữu hạn và nằm trong phạm vi hợp lệ.
- Xử lý dữ liệu mất thứ tự.
- Không để một heartbeat lỗi làm sai current pose.
- Không ghi liên tục hàng nghìn bản ghi không cần thiết.
- Có cơ chế retention hoặc downsampling nếu lưu history.
- Không khóa luồng MQTT ingestion bởi truy vấn DB nặng.
- Có tests cho duplicate, stale, out-of-order và map mismatch.

Không sửa giao thức MQTT hiện có nếu không cần thiết.

Không phát sinh dịch vụ cloud trả phí mới chỉ để làm telemetry khi hệ thống hiện tại có thể đáp ứng.

---

# PHẦN IV — REALTIME WORKFLOW/JOB CHO FRONTEND

## 15. REALTIME JOB STATUS

Frontend cần xem Job theo thời gian thực:

- QUEUED
- ACCEPTED
- RUNNING
- RECOVERY_REQUIRED
- SUCCEEDED
- FAILED
- CANCELLED
- Và các trạng thái hợp lệ khác của source hiện tại.

Kiểm tra `GET /api/v1/jobs/{id}/stream` cùng REST GET Job và Job Events.

Nếu SSE đã hoạt động đầy đủ thì giữ nguyên.

Nếu thiếu thông tin Step hiện tại, bổ sung vào snapshot hoặc event một cách tương thích ngược.

Frontend cần xác định:

- Step đang được chọn.
- StepType.
- StepCode.
- Execution state.
- StartedAt.
- Timeout/deadline.
- Endpoint mục tiêu của MOVE hiện tại nếu được phép hiển thị.
- Kết quả hoặc errorCode.
- Thời điểm Job cập nhật.
- Job terminal status.

Không tạo trạng thái Job mới nếu enum hiện tại đã đủ.

## 16. REALTIME INTERACT/DISPLAY

Trước đây hệ thống đã gặp tình huống:

`INTERACT(DISPLAY)` timeout 60 giây, khiến Job chuyển `RECOVERY_REQUIRED`.

Frontend Web phải nhận biết và hiển thị bước này đúng thời điểm.

Yêu cầu Backend hỗ trợ FE lấy được:

- JobStep ID đang chờ.
- `prompt_key`.
- Nội dung/ngữ cảnh cần hiển thị theo quyền truy cập.
- `startedAt`.
- `timeoutSec`.
- Deadline chính xác.
- `responseType` hợp lệ.
- Trạng thái Step hiện tại.

Kiểm tra API:

`POST /api/v1/job-steps/{id}/responses`

Đảm bảo API có validation và idempotency đúng.

Không tự gửi `UI_DISPLAYED` chỉ vì có SSE event.

FE phải thật sự hiển thị thông tin cho người dùng và gửi phản hồi hợp lệ trong deadline.

Nếu toàn bộ cơ chế này đã tồn tại thì không sửa lại; chỉ cung cấp hướng dẫn tích hợp FE và tests.

## 17. REALTIME AUTHORIZATION

Áp dụng phân quyền cho cả REST và SSE.

Admin được quản lý workflow theo role hiện có.

Staff được tạo và theo dõi nhiệm vụ trong phạm vi được phép.

Không để một user bất kỳ xem telemetry/Job không thuộc phạm vi được cấp quyền.

Không đưa MQTT user/password, Device Secret hay token nội bộ vào response của FE.

Nếu SSE sử dụng Bearer JWT, phải có hướng dẫn client tương thích với trình duyệt.

Không dùng token trong URL query một cách tùy tiện.

Kiểm tra CORS cho domain FE nhưng không hardcode mở toàn bộ origin ở production.

---

# PHẦN V — API HOÀN CHỈNH CHO 5 MÀN HÌNH FRONTEND

## 18. MÀN HÌNH 1 — DASHBOARD ROBOT

FE cần:

- Login.
- Danh sách robot.
- Status ONLINE/OFFLINE/BUSY/IDLE.
- Job hiện tại.
- Map đang dùng.
- Vị trí gần nhất.
- Telemetry freshness.
- Các cảnh báo cần thiết.
- Realtime status update.

Audit các API đã có và liệt kê đủ.

Chỉ bổ sung API còn thiếu.

## 19. MÀN HÌNH 2 — TẠO NHIỆM VỤ A → B

FE cần:

- Danh sách Map.
- Danh sách Endpoint theo Map.
- Danh sách Workflow đã Publish.
- Chọn Workflow/version phù hợp.
- Chọn Endpoint A/B.
- Chọn Robot nếu Backend thực sự hỗ trợ.
- Tạo Navigation Request.
- Submit Service Request.
- Nhận Job ID.
- Xử lý Idempotency và request duplicate.
- Xử lý robot chưa sẵn sàng.

Phải kiểm tra Backend có cho phép chọn workflow cụ thể khi tạo/submit Job hay không.

Nếu Backend tự động chọn workflow mới nhất và điều này không đáp ứng yêu cầu FE, hãy thiết kế bổ sung tối thiểu có authorization, compatibility và tests.

Không thay đổi ngữ nghĩa workflow selection cũ một cách âm thầm.

## 20. MÀN HÌNH 3 — DIGITAL TWIN 3D REALTIME

FE cần API để:

- Lấy Map.
- Lấy Endpoint.
- Lấy vị trí robot.
- Nhận robot pose updates.
- Nhận robot status updates.
- Xem endpoint mục tiêu hiện tại.
- Xem đường đi thực tế khi đã có nguồn dữ liệu.
- Xem progress.
- Xem Job hiện tại.
- Phát hiện telemetry stale/offline.

Backend không cần render mô hình 3D.

Backend chỉ cung cấp dữ liệu và contract.

Frontend sẽ sử dụng Three.js/React Three Fiber hoặc công nghệ FE phù hợp.

Không nhầm ROS occupancy map với mô hình bệnh viện 3D.

Nếu chưa có tài nguyên 3D, ghi rõ FE asset dependency; không tạo asset giả trong Backend.

## 21. MÀN HÌNH 4 — WORKFLOW DESIGNER

Admin phải có thể:

- Lấy đúng 11 StepType.
- Tạo Draft.
- Clone version.
- Thêm/sửa Step.
- Thêm/sửa Transition.
- Cấu hình inputMapping.
- Dùng Endpoint ID từ workflow input.
- Validate.
- Publish.
- Xem workflow graph/version.

Audit toàn bộ `/api/v1/admin/workflows/...`.

Nếu API đã đủ thì không tạo API mới.

Nếu thiếu action metadata hoặc input schema để FE tự xây form, ưu tiên sử dụng metadata/contract đã có; chỉ bổ sung phần thiếu theo cách tương thích ngược.

Không tự publish workflow production.

## 22. MÀN HÌNH 5 — JOB MONITORING

FE phải:

- Mở Job Detail.
- Hiển thị workflow timeline.
- Hiển thị Step hiện tại.
- Nhận SSE.
- Reconnect và đồng bộ lại state.
- Hiển thị INTERACT prompt.
- Cho người dùng xác nhận.
- Hiển thị MOVE đang chạy.
- Hiển thị Job SUCCEEDED/FAILED.
- Cho phép cancel khi được phân quyền.
- Hiển thị recovery status mà không tự reconcile Job nguy hiểm.

Không tự thực hiện recovery hoặc retry lệnh robot ngoài chính sách Backend hiện tại.

---

# PHẦN VI — WORKFLOW VERSION, MIGRATION VÀ COMPATIBILITY

## 23. WORKFLOW VERSION

Production đã có:

`WF_NAVIGATION`, version 2, schema 3, PUBLISHED.

Không sửa trực tiếp workflow đã Publish.

Sử dụng clone/versioning hiện có để xây workflow mới.

Không tự Publish hoặc kích hoạt workflow mới nếu người vận hành chưa đồng ý.

Các Job cũ phải giữ nguyên dữ liệu và trạng thái.

## 24. DATABASE MIGRATION

Chỉ tạo migration mới khi thật sự cần thay đổi schema.

Ưu tiên tái sử dụng bảng hiện có cho RobotLocationHistory, Job Events, Workflow và MQTT.

Không tạo bảng realtime mới chỉ vì muốn lưu dữ liệu đang có sẵn.

Nếu cần index cho truy vấn latest robot location, thêm migration phù hợp và đo tác động hiệu năng.

Không sửa migration đã áp dụng ở production.

## 25. TƯƠNG THÍCH NGƯỢC

Giữ:

- Navigation request cũ chỉ có destination.
- Job schema 2.
- Workflow schema 3.
- WF_NAVIGATION hiện tại.
- Endpoint catalog.
- MQTT MAR-STEP-2.
- Robot Profile ACK.
- Job SSE và REST hiện có.
- Chính xác 11 StepType.

Nếu bắt buộc thay đổi contract, phải đưa ra cách versioning và migration an toàn.

---

# PHẦN VII — TESTS BẮT BUỘC

## 26. NAVIGATION VÀ MULTI-MOVE TESTS

Kiểm tra:

- Endpoint A/B hợp lệ.
- Endpoint khác map.
- Endpoint inactive.
- Origin optional để tương thích cũ.
- Idempotency conflict khi đổi A/B.
- Workflow A → B.
- Workflow A → B → C.
- MOVE tuần tự.
- Không dispatch MOVE thứ hai quá sớm.
- Không cho Job SUCCEEDED khi thiếu kết quả.
- Không tự xác nhận INTERACT.
- Recovery và retry an toàn.
- Không thêm StepType.

## 27. REALTIME TELEMETRY TESTS

Kiểm tra:

- MQTT heartbeat hợp lệ.
- `map_pose` hợp lệ nếu contract hỗ trợ.
- Robot pose được lưu/cập nhật đúng.
- Map mismatch bị từ chối.
- Frame mismatch xử lý an toàn.
- Dữ liệu cũ không ghi đè dữ liệu mới.
- Timestamp ngoài phạm vi.
- Duplicate telemetry.
- Missing telemetry.
- Telemetry out-of-order.
- Robot offline/stale.
- Robot pose không bị giả lập từ Endpoint đích.
- Dữ liệu realtime không làm hỏng Job execution.

## 28. REALTIME API/SSE TESTS

Kiểm tra:

- JWT authorization.
- CORS.
- Snapshot/latest location.
- Stream chỉ phát dữ liệu đúng robot.
- Multiple FE clients.
- Reconnect.
- Event ordering/deduplication.
- Backpressure.
- Server restart.
- Không gửi dữ liệu không được phân quyền.
- Không rò rỉ secrets.
- Job status updates chính xác.
- INTERACT deadline.
- Stream kết thúc hoặc tiếp tục đúng chính sách khi Job terminal.

## 29. WORKFLOW DESIGNER TESTS

Kiểm tra:

- CRUD/Clone/Validate/Publish.
- StepType metadata đúng 11 loại.
- Endpoint inputMapping.
- Workflow version isolation.
- Published workflow bất biến.
- Job cũ không bị ảnh hưởng.

Chạy Maven build và tất cả automated tests liên quan.

Không xóa test hoặc giảm validation để khiến build PASS.

Không tuyên bố realtime hoạt động thật khi mới chỉ test bằng mock.

---

# PHẦN VIII — MÔI TRƯỜNG VÀ ROBOT ADAPTER

## 30. MÔI TRƯỜNG HIỆN TẠI

Backend Railway:

`https://bemar-production.up.railway.app`

Robot:

`MAR-GAZEBO-01`

Robot ID:

`6353fea7-9c1e-4931-9a3d-4ba1b248b2fe`

Map v2 ACTIVE:

`28ea152c-aeb2-4d91-ae73-9cdae9ca592f`

Endpoint đã kiểm thử:

`EP_GAZEBO_NORTH_04`

ROS 2 Jazzy / Gazebo Harmonic / Ubuntu 24.04.

ROS_DOMAIN_ID:

`72`

MQTT Backend username:

`mar-be`

MQTT Robot username:

`mar-pi-gazebo`

Không hardcode credentials.

Không reset Gazebo, robot, Map hoặc Navigation Profile.

## 31. GIỚI HẠN PHẠM VI ROBOT

Đây là task sửa **SRC Backend**.

Không tự sửa file ROS 2 Adapter trên WSL.

Tuy nhiên, phải audit và cung cấp contract chính xác để sau này Adapter hỗ trợ:

- Persistent MQTT session.
- Auto reconnect.
- Multi-MOVE sequential processing.
- Telemetry định kỳ.
- Real `map_pose`.
- Nav2 path/feedback nếu cần.
- Idempotency journal.
- Restart/recovery.
- Cancel và stop confirmation.

Nếu Backend đã hỗ trợ những dữ liệu này, ghi rõ Adapter chỉ cần thực hiện theo contract đang có.

Nếu Backend thiếu ingestion/validation/FE exposure, chỉ sửa phần Backend còn thiếu.

Không được giả lập telemetry để che giấu Adapter chưa triển khai.

Không kết luận hệ thống đã chạy không cần terminal nếu Robot Adapter chưa trở thành dịch vụ thường trực.

---

# PHẦN IX — DEPLOY VÀ BÀN GIAO

## 32. KHÔNG TỰ DEPLOY PRODUCTION

Không tự:

- Push GitHub.
- Merge main.
- Deploy Railway.
- Publish workflow.
- Tạo Job production.
- Gửi MQTT command.
- Reset robot.
- Reconcile Job.

Trừ khi tôi cho phép rõ ràng.

## 33. ĐẦU RA BẮT BUỘC

Sau khi hoàn tất, cung cấp:

1. Báo cáo audit các chức năng đã có, còn thiếu và phụ thuộc Adapter.
2. Danh sách file đã sửa.
3. Diff những thay đổi quan trọng.
4. Migration mới nếu có.
5. DTO/API contract cuối cùng.
6. Ma trận API cho đủ 5 màn hình FE.
7. Danh sách API Realtime đã có và API mới bổ sung.
8. Event schema cho robot status, robot pose và Job updates.
9. Cách FE lấy snapshot rồi subscribe realtime.
10. Cách FE xác nhận `INTERACT(DISPLAY)` đúng deadline.
11. Contract telemetry Adapter cần gửi.
12. Contract multi-MOVE Adapter cần thực thi.
13. Kết quả build/Maven tests thực tế.
14. Kết quả test realtime, validation, permissions.
15. API example request/response để FE tích hợp.
16. Railway Variables cần thay đổi nếu có, còn không ghi rõ KEEP CURRENT.
17. Hướng dẫn deploy an toàn một lần.
18. Hướng dẫn rollback.
19. Hướng dẫn commit/push PowerShell phù hợp branch hiện tại.
20. Danh sách phần chưa thể kiểm thử thực tế vì phụ thuộc Gazebo/ROS Adapter.

## 34. PHÂN BIỆT TRẠNG THÁI HOÀN THÀNH

Báo cáo riêng:

- `BACKEND_AUDIT_COMPLETE`
- `BACKEND_CODE_READY`
- `BACKEND_TESTS_PASSED`
- `BACKEND_REALTIME_API_READY`
- `RAILWAY_DEPLOYED`
- `ROBOT_TELEMETRY_PUBLISHING`
- `ROBOT_MULTI_MOVE_READY`
- `FRONTEND_3D_CONNECTED`
- `END_TO_END_MULTI_MOVE_PASSED`

Không đánh đồng các trạng thái này.

## 35. THỨ TỰ THỰC HIỆN

1. Đọc toàn bộ SRC hiện tại.
2. Kiểm tra chức năng đã có.
3. Chỉ ra phần thiếu.
4. Thiết kế sửa tối thiểu.
5. Trực tiếp sửa source Backend.
6. Cập nhật migration nếu bắt buộc.
7. Viết/cập nhật automated tests.
8. Chạy build và tests.
9. Kiểm tra Git diff.
10. Chuẩn bị API contract cho 5 màn hình FE.
11. Chuẩn bị contract cho Realtime Telemetry và ROS Adapter.
12. Tổng hợp Railway Variables.
13. Dừng để tôi kiểm tra trước khi deploy.

**ĐIỀU KIỆN HOÀN THÀNH CUỐI CÙNG**

Backend phải sẵn sàng phục vụ ứng dụng Web MediAssist Rover gồm:

- Dashboard Robot Realtime.
- Tạo Navigation Job theo Endpoint A/B.
- Giám sát Robot trên bản đồ 3D Realtime.
- Workflow Designer với đúng 11 StepType.
- Job Monitoring, SSE và INTERACT confirmation.
- Workflow có nhiều MOVE tuần tự.
- Khả năng lấy vị trí robot thực từ ROS 2 qua MQTT khi Adapter cung cấp dữ liệu.
- Không hardcode tọa độ.
- Không phá API/Job đã hoạt động trước đó.
- Không viết lại tính năng đang hoạt động đúng.
- Không tuyên bố đã hoàn thành các chức năng Robot/FE chưa triển khai.

**Hãy bắt đầu bằng audit thực tế, sau đó sửa toàn bộ phần Backend còn thiếu trong một đợt, chạy đầy đủ tests và xuất báo cáo để tôi xem trước khi deploy.**
