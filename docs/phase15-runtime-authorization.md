# Phase 15 — Runtime authorization

Ngày thực hiện: 2026-10-07; kiểm tra tiếp ngày 2026-10-08. Phạm vi: Backend, không thay đổi schema, enum StepType, transport hoặc các Workflow đã publish.

## Thay đổi

| File | Logic cũ | Logic mới / lý do |
|---|---|---|
| `PiJobRuntimeServiceImpl` | Device gửi result/error cho mọi Step RUNNING; lookup operationKey toàn cục trả ngay | Chặn callback cho nghiệp vụ Server/Staff; kiểm RUNNING của Job trước thay đổi mới; key phải đúng Robot/Job/Step; scope sai trả 403 |
| `AuthVerificationServiceImpl` | Chưa kiểm Staff scope, purpose cấu hình, proof QR; retry sau thành công lỗi | ADMIN hoặc STAFF owner/assigned; Account ACTIVE và đã đổi mật khẩu; purpose đúng cấu hình; chỉ STAFF_CONFIRMATION; retry cùng actor trả grant cũ còn hiệu lực |
| `AuthenticateStepHandler` | Suy đoán purpose từ tên step khi thiếu config | Bắt buộc purpose cấu hình, dùng cùng policy với cấp grant |
| `PiStepServiceImpl` | Execute song song có thể tranh Step | Khóa Job trước đọc/thực thi Step; sai device scope trả 403 |
| `JobRepository` | Chưa có khóa một Job cho runtime | Thêm `lockById` với PESSIMISTIC_WRITE, không đổi bảng |
| `RuntimeAuthorizationPolicy` | Quyền callback/grant không được diễn đạt thống nhất | Policy thuần, không I/O; kiểm scope, trạng thái và authority của callback |

## API giữ nguyên

- `POST /api/v1/jobs/{jobId}/auth-verifications`: ADMIN/STAFF; request hiện hữu `authJobStepId`, `method`, `purpose`, `validForSeconds`. Scope không hợp lệ trả 403; purpose/method/state không hợp lệ theo business error contract. `QR_CODE` bị từ chối tới khi có proof verifier Phase 18.
- Pi result/error/step-service/visit routes không đổi. Device JWT chỉ được thao tác Job của robot đó.
- `GET /api/v1/auth-verifications/{id}` giữ quyền Admin hoặc actor đã được cấp.

| Method | Route dưới `/api/v1/pi` | Quyền / mục đích |
|---|---|---|
| POST | `/job-steps/{jobStepId}/result` | DEVICE đúng Robot/Job/operationKey; chỉ hoàn tất loại tương tác được giao |
| POST | `/job-steps/{jobStepId}/errors` | DEVICE đúng scope; không dùng lỗi để rẽ nhánh thay Server/Staff |
| POST | `/jobs/{jobId}/steps/{stepId}/visits` | DEVICE đúng scope; tạo visit có kiểm transition, giới hạn và concurrency |
| POST | `/step-services/execute` | DEVICE yêu cầu Server chạy handler trong transaction, không tự cấp quyền Staff |
| GET | `/job-steps/{jobStepId}/service-result` | DEVICE chỉ đọc Step thuộc robot của token |

Body các POST Pi vẫn là envelope protocol 1.0 với payload đã có; không thêm endpoint hoặc đổi DTO trong Phase 15.

`DELIVERY_SEND` và `DELIVERY_RECEIVE` không phải permission động: được kiểm bằng role ADMIN/STAFF, ownership/assignment của Job và purpose tương ứng. Nếu `required_permission` cấu hình khác capability hỗ trợ thì từ chối; không coi một chuỗi permission là bằng chứng đã phân quyền.

## Quyền kết thúc Step

- PI/MIXED: callback Device vẫn theo executor đã chốt; kiểm Job RUNNING. Validation output đầy đủ thuộc Phase 16.
- SERVER: Device không được báo thành công hoặc báo lỗi để tự chọn nhánh thay cho Server.
- Ngoại lệ INTERACT: DISPLAY/SPEAK/CAPTURE_INPUT thông thường trả kết quả từ Device; các prompt `CONFIRM_COMPARTMENT_CLOSED`, `VERIFY_COMPARTMENT_LOCKED`, `STAFF_REVIEW`, `REMOTE_REVIEW`, `CONFIRM_DISPATCH`, hoặc actor/required_role STAFF/ADMIN bị chặn. Kiểm cả params gốc lẫn resolved inputs: inputMapping không được hạ quyền của cấu hình gốc.
- Không tự tạo API xác nhận cửa/chốt trong phase này. Những bước bị bảo vệ phải chờ nối luồng Staff ở Phase 18; không được dùng callback Device để demo vượt qua.
- Đây là policy cho contract hiện hữu; không tự giải nghĩa mọi prompt tùy ý thành quyền Staff. Contract actor/action tổng quát và validation graph sẽ được chốt ở Phase 16.

## Idempotency và concurrency

- Khóa hàng Job trong transaction trước runtime write, execute Server Step và cấp grant. Các yêu cầu cùng Job được tuần tự hóa; unique operation_key và optimistic version hiện hữu vẫn giữ.
- Cùng operationKey và cùng Job/Robot/Step trả visit cũ, không tạo event lần hai; khác Job/Robot trả 403, khác Step trả conflict.
- Job terminal không tạo visit mới; retry đọc visit đã tồn tại đúng scope không đổi trạng thái nên vẫn cho phép.
- Grant cùng auth Step/actor/method/purpose trả bản cũ nếu chưa revoked/expired; không gia hạn TTL khi retry. Actor khác không được nhận grant cũ.
- Khóa này không tuyên bố xử lý toàn bộ race scheduler/context/event; phần đó còn Phase 17/18. Không bổ sung bảng command hoặc migration.

## Kiểm thử

- `RuntimeAuthorizationTest`: chín case ban đầu thất bại trên code cũ vì thao tác trái quyền không bị chặn; sửa xong đạt. Tổng 15 case gồm terminal Job, inactive Account, permission ngoài allowlist, Admin không cần assignment, inputMapping hạ quyền và key cùng Job nhưng khác Step.
- `RuntimeAuthorizationIntegrationTest`: MySQL 8.4 Testcontainers; hai request cùng key chỉ một visit/event, hai key tranh target chỉ một thành công, terminal state, hai request cấp grant chỉ một grant/event/audit actor, callback giả auth/error bị chặn, callback Patient hợp lệ và idempotent.
- Fixture authorization thiết lập trạng thái trực tiếp để kiểm tra biên quyền. Không coi đây là nghiệm thu toàn graph delivery; E2E delivery thuộc Phase 18.
- Không chạy hoặc sửa database demo, không publish Workflow, không thay đổi file secret.

**DONE — 2026-10-08:** regression toàn bộ bằng Java 21/MySQL 8.4 Testcontainers đạt 83/83 test, 21 suites, không failure/error/skipped, Maven exit 0. Lệnh: `mvn -q -l target/phase15-final-regression.log test`. Surefire reports nằm trong `target/surefire-reports`; `git diff --check` đạt. Lượt đầu thiếu Docker đã được khắc phục và chạy lại, không skip test. Đây không phải nghiệm thu các phần Phase 16–21.
