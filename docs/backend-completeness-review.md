# Kiểm tra mức độ hoàn thiện Backend

Ngày: 2026-10-07. Phạm vi: review source hiện tại với source-baseline/source-alignment-plan và S1–S6. Chưa sửa nghiệp vụ/schema trong lần review. Không phải kiểm toán toàn bộ method hoặc chứng nhận phần cứng.

## Kết luận

Checkpoint sau review: Phase 15 đã bổ sung kiểm soát cho P1-01/P1-02/P1-06, test tái hiện và concurrency MySQL. Xem [chi tiết thay đổi](phase15-runtime-authorization.md) và kết quả trong [implementation-status](implementation-status.md). Các phát hiện bên dưới mô tả baseline trước khi sửa; không có nghĩa toàn bộ các gap khác đã hoàn tất.

**Chưa hoàn thiện toàn bộ yêu cầu Backend.** Các module và test baseline đã có, nhưng runtime còn thiếu kiểm soát quan trọng ngay trong phạm vi cũ; đồng thời contract mới S3/S4 chưa tương thích. Có đủ Entity/controller không đồng nghĩa đủ nghiệp vụ. Những nhãn DONE trước đây phải hiểu theo phạm vi test đã thực hiện.

## Các phát hiện ưu tiên

Đường dẫn bên dưới tương đối từ thư mục docs. Các phát hiện là từ đọc code; chưa chạy kịch bản khai thác hoặc thêm regression test tái hiện riêng.

### P1-01 — Device có thể hoàn tất bước SERVER đang chờ bằng callback chung

[PiJobRuntimeServiceImpl.java:36](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java#L36): stepResult kiểm identity/device và RUNNING, sau đó gán SUCCEEDED và lưu JSON do Pi gửi. Không kiểm loại executor, schema output, Staff actor hoặc grant. [JobRuntimePayloads.java](../src/main/java/com/mediassistrover/backend/dto/pi/request/JobRuntimePayloads.java) chỉ yêu cầu result khác null.

AUTHENTICATE/HANDLE_PAYLOAD được server handler đưa vào RUNNING chờ Staff. Do đó callback device có thể cung cấp boolean thành công để đi tiếp graph mà không qua xác nhận nghiệp vụ tương ứng. Cần tách quyền hoàn tất từng StepType: một số INTERACT cần callback device, nhưng xác thực/kiểm hàng/xác nhận cửa-chốt phải chứng minh actor và nghiệp vụ server. Thêm test device không được tự hoàn tất AUTHENTICATE/HANDLE_PAYLOAD; MOVE thiếu reached/action status và WAIT sai event phải bị từ chối.

### P1-02 — Cấp AuthVerification chưa kiểm đầy đủ quyền theo dữ liệu và purpose

[AuthVerificationController.java](../src/main/java/com/mediassistrover/backend/controller/verification/AuthVerificationController.java) chỉ chặn theo ADMIN/STAFF. [AuthVerificationServiceImpl.java:26](../src/main/java/com/mediassistrover/backend/service/impl/AuthVerificationServiceImpl.java#L26) kiểm JobStep thuộc Job và RUNNING nhưng không kiểm Staff được phân công/đúng người giao nhận; purpose và method lấy từ request, không đối chiếu purpose cấu hình của step. requiredPermission được gán thành chuỗi, chưa là kiểm tra quyền nghiệp vụ.

DTO Create chỉ có authJobStepId/method/purpose/validForSeconds; không có QR token/proof. Chọn method QR_CODE chưa chứng minh đã xác minh QR. Cần test Staff ngoài scope, purpose receiver trên sender step, QR sai/hết hạn/sai lần giao. requireUsable hiện có kiểm hạn/purpose/request ở bước sau nhưng không thay kiểm quyền khi cấp grant.

### P1-03 — NOTIFY đánh dấu thành công trước ACK nghiệp vụ

[NotifyStepHandler.java:19](../src/main/java/com/mediassistrover/backend/service/impl/NotifyStepHandler.java#L19) lưu Alert rồi trả ServerStepExecution(result,true). Severity được suy từ tên; không sử dụng đầy đủ severity/recipients/require_ack đã khai báo. Output chỉ alert_id/notification_type/persisted, khác notification_id/delivered/acked của S3.

Theo [PiStepServiceImpl.java](../src/main/java/com/mediassistrover/backend/service/impl/PiStepServiceImpl.java), completed=true làm Step SUCCEEDED. Vì vậy lưu Alert đã được coi là hoàn tất ngay cả thông báo cần người tiếp nhận. Cần giữ pending/running đến ACK đúng scope hoặc timeout; việc persist/transport ACK không phải Staff đã nhận.

### P1-04 — SET_CONTEXT chưa đồng bộ vào runtime context của server

[JobStepInputResolverImpl.java:43](../src/main/java/com/mediassistrover/backend/service/impl/JobStepInputResolverImpl.java#L43) đọc /vars từ Job.contextJson. Tìm setContextJson trong service chỉ thấy JOB_STARTED; stepResult chỉ lưu resultJson. Khi Pi thực hiện SET_CONTEXT, /vars phía server không được cập nhật theo output của step.

Nhánh dùng biến vòng lặp/chọn endpoint tiếp theo có thể resolve thiếu hoặc dùng giá trị cũ. Cần chốt semantics assignment/snapshot và cập nhật context trong transaction với event; test INIT→INCREMENT→LOOP và vòng chọn target, không chỉ kiểm tạo visit mới.

### P1-05 — Lỗi/hủy delivery có thể trả robot về IDLE khi chưa đối soát

[PiJobRuntimeServiceImpl.java:30](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java#L30) accepted cancel đặt CANCELLED rồi releaseRobot; jobError đặt FAILED rồi releaseRobot, không phân biệt delivery đã mở khoang/tác động vật lý. Điều này chưa đáp ứng S3/S4 yêu cầu RECOVERY_REQUIRED cho delivery chưa xác định an toàn.

Cần guard dựa trạng thái thực thi/thao tác vật lý và quyền reconcile; test lỗi sau UNLOCK và hủy sau LOAD không tự cho robot nhận Job khác. Chốt chính sách delivery FAILED/RECOVERY_REQUIRED do S4 cũng có nhánh mâu thuẫn.

### P1-06 — Lookup visit theo operationKey chưa ràng buộc Job/Robot của kết quả

[PiJobRuntimeServiceImpl.java:45](../src/main/java/com/mediassistrover/backend/service/impl/PiJobRuntimeServiceImpl.java#L45) createStepVisit kiểm quyền trên Job trong URL, rồi tìm operationKey toàn cục và trả stepCommand(existing) ngay. Chưa kiểm existing thuộc chính Job/Robot/target step đó. Nếu device biết key của Job khác, có thể nhận metadata step ngoài scope. Cần scope-check trước trả duplicate, test hai Robot/Job dùng cùng key; kiểm Job còn active và locking khi tạo visit đồng thời.

### P2-01 — Giao nhận chưa nối đủ xác nhận hàng với JobStep

[DeliveryServiceImpl.java:46](../src/main/java/com/mediassistrover/backend/service/impl/DeliveryServiceImpl.java#L46) confirmPickup/confirmDelivery cập nhật account/timestamp và audit; chưa hoàn tất HANDLE_PAYLOAD JobStep, đối chiếu từng item/quantity hoặc phát output Staff-confirmed theo S3. Người được phép hiện giới hạn creator/Admin nên cần kiểm yêu cầu người nhận khác người tạo. confirmDelivery không kiểm request IN_PROGRESS như confirmPickup.

[Phase7IntegrationTest.java:40](../src/test/java/com/mediassistrover/backend/Phase7IntegrationTest.java#L40) tự đặt Job và AUTH Step thành RUNNING rồi gọi service xác nhận; đây chưa là E2E toàn graph giao nhận qua các API. Cần test pickup→auth→unlock→load→close/check→lock/check→move→receiver→unload đầy đủ, kể cả mismatch/cancel và Staff khác nhau.

### P2-02 — Contract của đủ 11 enum chưa khớp đặc tả mới

[WorkflowStepContractValidator.java](../src/main/java/com/mediassistrover/backend/validation/WorkflowStepContractValidator.java), [WorkflowJsonValidator.java](../src/main/java/com/mediassistrover/backend/validation/WorkflowJsonValidator.java): sensing_type so với task_type; operations so với assignments; LOCK_COMPARTMENT/UNLOCK_COMPARTMENT so với LOCK/UNLOCK; expression path/operator/value so với SAFE_EXPR_V1 var/op/args; chỉ cho /inputs thay vì /input và /system của S4. AUTH output dùng verified và thiếu actor_user_id/authenticated. Không thể import nguyên graph S4 vào runtime hiện tại.

Cần chốt version/adapter và bảng schema 11 loại, không đổi enum hay rename field âm thầm. Enum có đủ không chứng minh contract input/output đã đủ.

### P2-03 — Bộ seed và nguồn thông tin chưa đáp ứng toàn bộ S4

Tám seed hiện tại có WF_NAVIGATION/WF_FALL_RESPONSE/WF_RESTRICTED_MONITORING. Chưa tìm thấy WF_AUTO_CHARGING, WF_NAVIGATION_RECOVERY, WF_ROBOT_STARTUP_STANDBY trong src/main; code tên fall/restricted cũng khác S4. Không chỉ là thiếu hai seed.

[DataServiceStepHandler.java](../src/main/java/com/mediassistrover/backend/service/impl/DataServiceStepHandler.java): QUERY_HOSPITAL_INFORMATION trả tối đa 20 nội dung public/effective, không xử lý câu hỏi thành data.answer theo S4; service registry không nhận HOSPITAL_INFORMATION/QUERY/answer trực tiếp. Voice có lưu lifecycle nhưng chưa chứng minh có pipeline hỏi đáp AI. AI implementation vẫn ngoài scope; cần contract và fallback phía server rõ ràng.

### P2-04 — HospitalInformation khác nghĩa ERD đã gửi

[HospitalInformation.java](../src/main/java/com/mediassistrover/backend/entity/HospitalInformation.java) là bản ghi nội dung với category/title/content/language/keywords/publication. S2 mô tả hồ sơ bệnh viện và Floor tham chiếu bệnh viện. Đây là khác biệt model thực, không chỉ thiếu FAQ. Không tự đổi schema; chốt nghĩa hồ sơ bệnh viện và bài thông tin, rồi lập delta PK/FK cùng dữ liệu chuyển đổi.

## Ma trận mức độ bao phủ

| Nhóm | Bằng chứng hiện có | Kết luận trong lần review |
|---|---|---|
| Java/Spring/MySQL/Flyway/DTO/OpenAPI | Cấu trúc source, migrations, ArchitectureSmokeTest | Có nền tảng và kiểm thử baseline |
| ADMIN/STAFF, Device JWT, rotation/blacklist | AuthFlowIntegrationTest, RobotPiIntegrationTest | Có; không đồng nghĩa quyền chi tiết mọi API đã đúng (P1-02) |
| Staff/Facility/Robot | Service/controller và integration tests | Có; nghĩa HospitalInformation cần chốt |
| Workflow version/graph/11 enum | WorkflowGraphIntegrationTest, validator/compiler | Có nền tảng; contract S3/S4 chưa khớp |
| Request/queue/dispatch/runtime | RequestJobIntegrationTest, Job runtime | Có; còn P1-01/04/05/06 |
| Delivery/AuthVerification/Alert/Emergency | Phase7IntegrationTest, services | Có module nhưng chưa đầy đủ nghiệp vụ/semantic ACK |
| Shift/Slot | Phase10IntegrationTest | Đã có baseline; tooltip ERD Shift cần thống nhất |
| Detection/Crowd/Heatmap | Phase11IntegrationTest | Có ingestion/storage; không chứng nhận model AI hoặc robot thật |
| Voice | Phase12IntegrationTest | Có log lifecycle; không phải STT/RAG/TTS hoàn chỉnh |
| Scheduler/ShedLock/Retention | Phase8/13 tests và SchedulerLock/ConditionalOnProperty | Có baseline; retention chạy thật theo cấu hình riêng |
| Reports/Analytics | Phase14IntegrationTest | Có baseline báo cáo; chưa đo hiệu năng hoặc nghiệm thu số liệu thực tế ở lần này |
| ERD full-field mới | S2 chỉ nhãn/quan hệ, có ID/label không khớp | Chưa đủ căn cứ xác nhận mọi field/PK/FK khớp |
| Pi/Hardware/Client acceptance | Checklist NOT RUN và docs demo cũ | Không thể dùng test Backend thay thế |

## Kiểm thử lần này

- Lần đầu Maven dùng Java 17, không chạy được class Java 21 (65.0/61.0). Đây là lỗi môi trường runtime kiểm thử.
- Đã chọn JDK 21.0.10 cho tiến trình Maven và chạy lại `mvn -q test` với MySQL 8.4 Testcontainers riêng. Không dùng database demo để test.
- Kết quả cuối: Maven exit code 0; 19 test suites, 62 tests, 0 failures, 0 errors, 0 skipped (Surefire XML trong target/surefire-reports). Test baseline xanh không xóa các phát hiện review trên vì chưa có negative tests tương ứng. Không chạy browser/Swagger thủ công, load test hoặc hardware test trong lần này.

## Thứ tự xử lý đề xuất

1. Khóa quyền hoàn tất Step/cấp grant/lookup operationKey (P1-01/02/06).
2. Sửa semantic ACK, đồng bộ context, trạng thái delivery recovery (P1-03/04/05); thêm test tái hiện trước khi sửa.
3. Hoàn thiện E2E giao nhận và xác nhận Staff; kiểm WAIT event/type/correlation/time window.
4. Chốt delta S3/S4 rồi nâng contract/catalog theo version; không sửa workflow đã publish.
5. Chốt schema HospitalInformation và ERD full-field; chỉ migration forward nếu duyệt delta.
6. Chạy lại regression/API acceptance, sau đó tích hợp Pi thật và kiểm load/restore.

Yêu cầu hiện tại là kiểm tra; báo cáo không tự phê duyệt thay đổi nghiệp vụ hoặc schema.
