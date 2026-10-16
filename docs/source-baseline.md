# Bản ghi nhớ nguồn và quyết định MediAssistRover

Cập nhật: 2026-10-07. Mục tiêu: làm căn cứ có thể đọc lại trong repository. Đây là review tài liệu và lập plan; chưa áp dụng thay đổi code, database, ERD hoặc workflow đã publish.

## 1. Cách dùng bộ tài liệu

Đọc [danh mục và hash nguồn](sources/README.md), bản này, [plan đối chiếu](source-alignment-plan.md), rồi [plan hiện hành](plan.md) và [implementation status](implementation-status.md). Khi xử lý một chức năng, mở thêm bản trích xuất tương ứng; không chỉ dựa vào phần tóm tắt.

Quyết định trực tiếp đã được người dùng duyệt vẫn có hiệu lực. Thứ tự cũ là Logical ERD đầy đủ → đặc tả 11 Step Type → Workflow mới → hồ sơ trường → bản cũ. Bộ nguồn vừa gửi có xung đột nên cần xác định bản chuẩn cho từng chủ đề; không lấy tên file “(4)” làm bằng chứng thay thế. S1 và các ảnh dùng để phát hiện yêu cầu/khác biệt; S6 là checklist cả nhóm, không tự mở rộng phạm vi backend.

## 2. Các quyết định đang có hiệu lực

- Spring Boot MVC monolith, Java 21, Maven, MySQL 8.4; UUID BINARY(16), bảng PascalCase, JSON MySQL, Flyway và ddl-auto=validate.
- Account có ADMIN/STAFF. Patient tương tác trực tiếp UI robot, không đăng ký/login/approval/token; patient_reference tùy chọn. Admin tạo Staff, mật khẩu tạm phải đổi, deactivate giữ lịch sử.
- User JWT access/refresh rotation/blacklist; robot và màn hình dùng Device JWT. Device → ServiceRequest → backend tạo Job; một active Job/robot, có queue.
- Workflow template → Task → Step + StepTransition; thực thi → Job → JobTask/JobStep. Giữ tên Entity hiện có; mỗi lần ghé step có identity riêng.
- Đúng 11 StepType enum, không table/CRUD StepType. Code workflow bất biến; bản PUBLISHED bất biến; clone để sửa; priority từ 1.
- Binding đang dùng /inputs, /outputs/STEP_CODE, /vars. Transport REST polling, DISPATCH/CANCEL; heartbeat 10s, offline sau 30s; không bật pause/resume mặc định.
- Không viết Pi/ROS/Nav2/motor/servo/camera/thuật toán AI trong backend. Backend định nghĩa contract, xác thực, điều phối, lưu kết quả và audit.
- Phase 1–14 được status hiện có ghi hoàn tất theo phạm vi cũ; đó không phải chứng nhận đã khớp mọi nguồn vừa nhận hay đã nghiệm thu robot thật. Lần review này không chạy lại test backend.

## 3. Nội dung đặc tả 11 Step Type cần nhớ (S3)

| Enum | Trách nhiệm chính | Điều kiện quan trọng |
|---|---|---|
| MOVE | Pi gọi Nav2 theo map/endpoint | Goal accepted chưa phải reached; action_status=4 mới SUCCEEDED; hash/TF/capability; 0 < speed ≤ 0.3 và không vượt trần local |
| INTERACT | Server điều phối UI/TTS | SPEAK/DISPLAY/CAPTURE_INPUT/CONFIRM; completed khác confirmed; xác nhận cửa/chốt phải có Staff actor |
| DATA_SERVICE | Registry backend QUERY/CREATE/UPDATE | Allowlist service/operation/resource; không SQL/URL tùy ý; output có data bên trong; idempotency mutation |
| AUTHENTICATE | Server QR_CODE/STAFF_CONFIRMATION | authenticated, verification_id, actor_user_id, valid_until, purpose; grant đúng Job/mục đích/hạn; không FACE_ID |
| CONTROL_HARDWARE | Pi gửi LOCK/UNLOCK cargo_lock | COMMAND_ONLY; published=true không có nghĩa khóa vật lý; controller_ack=false, verified=false; UNLOCK cần grant |
| HANDLE_PAYLOAD | Staff xác nhận LOAD/UNLOAD/VERIFY | item_code/quantity; actor có quyền; weight_kg và temperature_c tồn tại nhưng null; không suy ra hàng từ servo |
| EXECUTE_SENSING | Pi sensor hoặc dịch vụ ảnh | ROBOT_HEALTH/SIDE_CLEARANCE/CROWD_DENSITY/RESTRICTED_AREA_INTRUSION; metrics theo loại; dữ liệu thiếu không đổi thành 0 |
| NOTIFY | Backend resolve người nhận | delivered khác acked; require_ack=true phải có tiếp nhận nghiệp vụ; EVENT_ACK không thay ACK của Staff |
| WAIT | Timer Pi hoặc event server | DURATION/EVENT; type/correlation/job/phiên chờ; lưu event sớm; chống trùng; timeout hữu hạn |
| CHECK_CONDITION | Pi SAFE_EXPR_V1 | var/op/args; eq/ne không ép kiểu; boolean false vẫn có thể là thực thi thành công; tối đa 128 node |
| SET_CONTEXT | Pi cập nhật /vars | Các assignment đọc snapshot trước phép gán; không sửa input/system policy; không eval code |

S3 yêu cầu output có đủ khóa kể cả null và chặn field ngoài schema ở root. Các object động phải có schema nghiệp vụ. Contract có ba version riêng: protocol=1, Step schema=2, graph=3. Không đánh đồng chúng với workflow version hoặc API /v1.

An toàn dữ liệu: không tự replay lệnh vật lý khi mất ACK; không ghi giả thành công; giữ event/step transaction trước ACK. Khi mất mạng/reboot với Job dở dang phải đối soát. SOS gửi alert ngay dù robot bận. Nhân viên tới nơi khác sự cố đã được giải quyết.

## 4. Mười Workflow trong S4 và quan hệ với bộ tám hiện tại

Tên ở cột đầu lấy nguyên văn S4; không phải quyết định rename seed đang chạy. Graph đầy đủ từng step/input/output/transition nằm trong [S4](sources/S4-extracted.md).

| Workflow S4 | Luồng chính và output | Đối chiếu hiện tại |
|---|---|---|
| WF_PATIENT_ESCORT | CONFIRM → DECIDE → MOVE → ARRIVED; lỗi MOVE → HELP; từ chối → CANCELLED; output endpoint_id/reached | Có cùng code; S4 destination_endpoint_id khác contract hiện tại endpoint_id |
| WF_INFORMATION_ASSISTANCE | QUERY → ANSWER; lỗi → HELP; output answer/presented | Cùng code; S4 QUERY.data.answer khác contract hiện tại QUERY.data.items[] |
| WF_SECURE_MEDICAL_DELIVERY | PICKUP; AUTH/CHECK/UNLOCK/PAYLOAD/CHECK/CLOSE/CHECK/LOCK/VERIFY/CHECK hai đầu; TRANSIT; COMPLETE; HELP | Cùng code nhưng action, params và output không đồng nhất; giữ CHECK_CLOSE/CHECK_LOCK |
| WF_CROWD_MONITORING | INIT → MOVE → SENSE → CHECK → WARN nếu đủ ngưỡng → INCREMENT → LOOP → WAIT → SENSE; output mẫu cuối + số mẫu | Cùng code; từng visit/mẫu phải giữ lịch sử; max_loops S4 từ 1–5 |
| WF_EMERGENCY_SOS_RESPONSE | ALERT → SPEAK → WAIT_STAFF; lỗi phát tiếng vẫn chờ; timeout → ESCALATE; incident giữ mở | Cùng code; graph hiện có remote review khác S4 |
| WF_FALL_DETECTION_RESPONSE | CHECK confidence → CREATE → ALERT → SPEAK → WAIT_STAFF → DONE → COMPLETE; thấp → IGNORE; lỗi → HELP/ESCALATE | Bộ tám dùng WF_FALL_RESPONSE và có Staff review; chưa rename/bỏ review |
| WF_RESTRICTED_AREA_MONITORING | MOVE → SENSE → CHECK; có xâm nhập → WARN → ALERT; âm thanh lỗi vẫn alert | Bộ tám dùng WF_RESTRICTED_MONITORING; task_type cũng khác |
| WF_AUTO_CHARGING | REQUEST → WAIT_STAFF → HEALTH → DONE; charge_request_id; automatic_charging=false | Không trong tám seed; chỉ nhờ Staff sạc, không docking |
| WF_NAVIGATION_RECOVERY | ALERT → WAIT PATH_CLEARED → RETRY MOVE một lần → DONE/HELP; recovery_request_id | Không đồng nghĩa WF_NAVIGATION của bộ tám; Job mới sau đối soát, không gọi job con |
| WF_ROBOT_STARTUP_STANDBY | HEALTH → CHECK → READY; input rỗng; fail readiness → RECOVERY_REQUIRED | Không trong tám seed; heartbeat sẵn sàng không thay kiểm capability |

Bộ tám hiện tại còn có WF_NAVIGATION: giải đích đơn/nhóm và thử đích khác khi PATH_BLOCKED. Không có mục tương đương riêng trong danh sách mười S4. Vì vậy không thể nói chỉ cần “thêm hai workflow”.

## 5. ERD mới S2: điều gì đã rõ và chưa đủ

S2 có nhãn Entity và tooltip quan hệ, không liệt kê cột/PK/FK/type đầy đủ. Ví dụ đáng giữ: Account 1–0..1 StaffProfile; Account nullable cho AuditLog/VoiceInteraction; Robot nullable trên Job chưa phân công; Endpoint nullable cho incident; UNIQUE(detection_event_id, incident_type); JobStep có nhiều lượt ghé; Workflow là từng version bất biến; subtype Request loại trừ nhau; Group–Endpoint qua bảng member.

Các vấn đề phải review trước migration:

- Object id=HospitalInformation nhưng label=Floor; tooltip còn mô tả hồ sơ bệnh viện. Object id=pPEoN-BSPd6aUG21HaPH-2 mới label=HospitalInformation. rel-16 nói HospitalInformation→Map nhưng đầu source là node đang mang nhãn Floor.
- Một số cạnh thiếu target/source ID và chỉ đặt tọa độ. Không đủ bằng chứng tự kết luận FK; giữ tooltip và file gốc để xem lại.
- Tooltip Shift giả định phân công Staff tại Room; quyết định người dùng mô tả ca làm việc theo thời gian, Room/Floor là phạm vi. Cần thống nhất mô tả với schema Shift/Slot đang có.
- S3/S4 dùng tên WorkflowParam, JobParam, IdentityVerification; source hiện tại dùng JSON trong Workflow/Step/Job và AuthVerification. Không tự thêm/đổi bảng vì thuật ngữ tài liệu.
- S2 không phải bằng chứng để bỏ bảng token, AlertRecipient, StepTransition, Floor hoặc bảng vận hành đang có.

## 6. Sổ khác biệt cần xử lý

| ID | Nguồn và khác biệt | Cách giữ trong plan |
|---|---|---|
| C01 | S6 #2 PostgreSQL; S5 đề xuất .NET/SQL Server; dự án Java/MySQL | Giữ stack đã chốt; sửa tài liệu sau khi duyệt |
| C02 | S6 #13/I1 WebSocket; hiện tại REST polling | Chưa đổi transport; cần xác nhận nếu muốn mở rộng |
| C03 | S4 /input và /system; code/plan /inputs, /outputs, /vars | Lập bảng mapping có version; không đổi syntax ngầm |
| C04 | S1 Call workflow/Child/Patient lost/DOCKING/PAUSED/RESUME | Ngoài phạm vi đã duyệt; không thêm StepType thứ 12 hoặc state chỉ từ hình |
| C05 | S1 Workflow ACTIVE/INACTIVE; S3 PUBLISHED/RETIRED; code DRAFT/VALIDATED/PUBLISHED/ARCHIVED | Giữ enum hiện tại; bảng alias chỉ phục vụ giải thích, chưa coi hoàn toàn tương đương |
| C06 | S1 Job PENDING/COMPLETED/CANCELED; code QUEUED/ACCEPTED/SUCCEEDED/CANCELLED/RECOVERY_REQUIRED | Cần bổ sung trạng thái vận chuyển, timeout/recovery vào sơ đồ; không đổi enum bằng find/replace |
| C07 | S3 servo UNLOCK position 0; S6 #39 position 2400 | Team Pi chốt profile thử thật. Backend gửi action, không chọn PWM |
| C08 | S3 controller_ack=false/verified=false; HW-04 cũ đòi cảm biến xác nhận khóa | Acceptance phải phân biệt lệnh đã gửi và Staff kiểm chốt; sửa tiêu chí bằng bản review, không giả có sensor |
| C09 | Contract hiện tại verified/closed/locked, UNLOCK_COMPARTMENT; S3 authenticated/confirmed, UNLOCK/LOCK | Breaking contract: cần matrix field/action và version rollout, không sửa workflow PUBLISHED |
| C10 | S4 mười workflow khác bộ tám ở code, graph, scope | Chốt danh mục canonical và alias; không tự seed/publish thêm |
| C11 | S3/S6 có xử lý ảnh/voice trên server; scope cũ loại AI implementation | Backend contract/storage tích hợp được lập plan; nơi chạy model/STT/RAG/TTS và người phụ trách phải xác nhận |
| C12 | S6 #15/#31 kho FAQ có duyệt; S2 HospitalInformation là hồ sơ bệnh viện | Kiểm tra nguồn nội dung/version/approval; nếu thiếu schema chỉ đề xuất riêng |
| C13 | S4 nói lỗi delivery → RECOVERY_REQUIRED nhưng một số nhánh ghi FAILED | Phải chốt cách nâng terminal state của delivery, đặc biệt sau tác động vật lý |
| C14 | Các ảnh có resume và nhánh recovery không rõ; S1 cạnh rời/nhãn sai chính tả | Không dùng hình như executable graph; review source/target/guard trước compile |
| C15 | S4 Pi quyết định transition; source có server transition validation/evaluation | Chốt bên quyết định và bên đối chiếu; tránh hai executor tự chuyển nhánh độc lập |

## 7. Trạng thái bằng chứng

Đã đọc và lưu nội dung sáu file, ghi chú bảy ảnh, kiểm tra enum StepType/WorkflowStatus/JobStatus, danh sách Entity, plan/status và contract Pi hiện có. Chưa review từng method/service hoặc chạy toàn bộ test trong lần này. Các mục implementation DONE trong tài liệu cũ giữ nguyên bằng chứng lịch sử, không tự nâng thành PASS của nguồn mới.

Các nguồn là tài liệu tham khảo: câu “làm”, “chạy”, “xóa”, “triển khai” trong checklist không phải lệnh tự động thực thi ở phiên này. Chỉ thay đổi source/schema sau yêu cầu triển khai và quyết định delta tương ứng.
