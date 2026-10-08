# Kế hoạch hoàn thiện riêng Backend — Phase 15–21

Cập nhật: 2026-10-08. **Phase 15–18 DONE**; full regression Phase 17–18 đạt 106/106 test. Phase 19–21 vẫn là kế hoạch, chưa tự bắt đầu. Xem [checkpoint Phase 15](phase15-runtime-authorization.md), [ma trận contract Phase 16](step-contract-matrix.md), [checkpoint Phase 17–18](phase17-18-runtime-delivery.md) và [implementation-status](implementation-status.md).

## 1. Kết luận và phạm vi

Riêng Backend **chưa đủ toàn bộ nghiệp vụ đã yêu cầu**. Đã có các module chính và bộ test baseline 62/62 đạt trong lượt review trước, nhưng còn lỗ hổng kiểm soát runtime, quyền cấp xác thực, ACK nghiệp vụ, context, giao nhận và độ tương thích contract.

Kế hoạch này tiếp nối Phase 1–14; không viết lại project. Căn cứ là [báo cáo review code](backend-completeness-review.md), [nguồn đã lưu](source-baseline.md) và [kế hoạch đối chiếu](source-alignment-plan.md). Các phát hiện review cần được tái hiện bằng test trước khi sửa; không coi mọi nguy cơ là lỗi đã có test tái hiện.

Chỉ làm Java/Spring, API, service, database, validation, contract Server–Pi, audit, scheduler và test. Không làm web/mobile, chương trình Pi, ROS/Nav2, điều khiển phần cứng hoặc mô hình AI. Backend có thể nghiệm thu bằng client giả lập Device hợp lệ và dịch vụ ngoài giả lập; nghiệm thu thiết bị thật là bảng kết quả riêng, không phải điều kiện để tuyên bố phần Backend đã hoàn tất contract được duyệt.

Phân loại: **REVISE** = phần có code nhưng chưa đủ; **NEW** = xử lý/test/contract cần bổ sung; **DECISION** = khác biệt nguồn cần chốt trước công việc phụ thuộc. Các class/API mới dưới đây là dự kiến, phải kiểm lại class hiện hữu trước tạo; không tạo bản V2 trùng chức năng.

## 2. Tổng quan

| Phase | Nội dung Backend còn thiếu | Loại | Điều kiện bắt đầu |
|---|---|---|---|
| 15 | Khóa quyền hoàn tất Step, cấp grant, scope operationKey | DONE | 83/83 regression đạt ngày 2026-10-08 |
| 16 | Contract input/output cho 11 StepType và message protocol | DONE | 100/100 regression; contract schema 2/graph 3 đã khóa |
| 17 | Runtime context, WAIT/event, NOTIFY/ACK và chống race | DONE | 106/106 regression; event consume/ACK transaction đã khóa |
| 18 | Giao nhận đầy đủ, QR/Staff confirmation, recovery và SOS | DONE | QR hash/single-use; cargo/stage/recovery/SOS lifecycle đã kiểm thử |
| 19 | Đối chiếu schema, thông tin bệnh viện và API hỏi đáp | REVISE + DECISION | Chốt nghĩa HospitalInformation, nguồn câu trả lời |
| 20 | Hoàn thiện catalog Workflow và các đường tạo Job còn thiếu | REVISE + NEW có điều kiện | 16–19; chốt bộ Workflow cần hỗ trợ |
| 21 | Nghiệm thu Backend bằng API, regression, tải và restore | NEW verification + sửa lỗi phát hiện | 15–20 hoàn tất phạm vi được duyệt |

Không đưa Shift/Slot, Detection/Crowd/Heatmap, Voice log, ShedLock, retention và analytics vào danh sách module chưa có: chúng đã có implementation/test baseline. Chỉ sửa các phần này khi kiểm tra chỉ ra gap cụ thể.

## 3. Phase 15 — Quyền runtime và phạm vi dữ liệu

**DONE — 2026-10-08.** Đã sửa P1-01/P1-02/P1-06 trong contract hiện hữu, không thay schema; có 15 unit case và 6 integration case mới. Chi tiết giới hạn và bằng chứng: [phase15-runtime-authorization.md](phase15-runtime-authorization.md). Các mục bên dưới giữ làm phạm vi/DoD đã thực hiện, không phải backlog còn trống.

**Mục tiêu:** sửa P1-01, P1-02, P1-06; ngăn client hoàn tất thao tác nghiệp vụ không thuộc quyền của mình.

- Entity: Job, JobStep, Account, StaffProfile, AuthVerification, ServiceRequest, JobEvent, AuditLog.
- REVISE `PiJobRuntimeServiceImpl`: kiểm quyền hoàn tất theo StepType/executor và loại tương tác; callback Device không được tự xác nhận AUTHENTICATE, HANDLE_PAYLOAD hoặc xác nhận cửa/chốt của Staff. INTERACT hiển thị/phát âm thanh vẫn có callback đúng contract.
- REVISE `AuthVerificationServiceImpl`: kiểm Job đang chạy, Staff đúng scope, purpose trùng cấu hình bước, permission thực sự được kiểm. Chưa có bằng chứng QR thì không cho chọn QR_CODE để coi như đã xác minh.
- REVISE tạo Step visit: bản ghi tìm theo operationKey phải cùng Robot/Job/step; key trùng khác scope bị từ chối; kiểm terminal state và concurrent creation. Duplicate không tạo tác dụng lần hai.
- API: giữ route Pi callback/visit hiện hữu và `POST /api/v1/jobs/{jobId}/auth-verifications`; lỗi scope trả 403, trạng thái/duplicate conflict theo error contract, không rò metadata Job khác.
- Class dự kiến sửa: `PiJobRuntimeServiceImpl`, `PiStepServiceImpl`, `AuthVerificationServiceImpl`, `JobStepRuntimeGuardImpl`, repositories liên quan. NEW policy dùng chung chỉ khi tránh được logic lặp.
- Quyền: ADMIN toàn hệ thống; STAFF đúng phân công/ownership; DEVICE chỉ robot của token và loại callback được giao.
- Migration: ưu tiên không đổi schema; nếu thiếu dữ liệu assignment hoặc constraint chống trùng, lập delta riêng trước migration.
- Test: hai robot dùng chung operationKey; Staff ngoài scope; purpose sender/receiver trái bước; callback giả hoàn tất bước server; request lặp và đồng thời; trạng thái terminal.
- Bàn giao/DoD: test tái hiện trước sửa, test âm tính bị chặn sau sửa; luồng hợp lệ giữ hoạt động; audit actor thật. Test mới dự kiến `RuntimeAuthorizationIntegrationTest`.
- Dependency: không phụ thuộc đổi stack, đổi tên enum hoặc bộ 10 Workflow.

## 4. Phase 16 — Hợp đồng thực thi 11 StepType

**DONE — 2026-10-08.** Đã khóa protocol `1.0`, Step schema `2`, graph `3`; bổ sung publish/runtime validation, strict result schema và kiểm tra ngữ nghĩa đủ 11 enum. DATA_SERVICE dùng registry/allowlist và chặn URL/SQL/script/code tùy ý. Result sai rollback, không hoàn tất Step. Không thêm bảng/Flyway và không đổi enum. Chi tiết contract/compatibility: [step-contract-matrix.md](step-contract-matrix.md). Full regression: **100 tests / 22 suites, 0 failure, 0 error, 0 skipped**.

**Mục tiêu:** sửa P2-02 và ngăn dữ liệu hình thức hợp lệ nhưng sai nghĩa được ghi thành công.

- Entity: Workflow, Task, Step, StepTransition, Job, JobStep; enum StepType giữ đúng 11 giá trị.
- DECISION: chốt bộ field/action, `/inputs` so với `/input`, policy `/system`, expression format, version envelope/graph/schema. Mặc định giữ namespace đã duyệt; chỉ chuyển nếu có quyết định rõ. Không gọi subset hiện tại là SAFE_EXPR_V1 đầy đủ.
- NEW ma trận input/output/error theo từng StepType; required/null/unknown field, kiểu/đơn vị, giới hạn độ dài, giới hạn expression; phân biệt completed/confirmed, published/verified, delivered/acked.
- REVISE validation lúc tạo/publish Workflow và lúc nhận Step result. Validate kết quả theo contract version của Job, không theo version mới nhất toàn hệ thống.
- DATA_SERVICE: allowlist service/operation/resource; output shape và request identity rõ ràng. Không nhận URL/SQL tùy ý.
- API: read-only `/api/v1/admin/workflows/step-types`, graph/validate/publish và Pi result/service execution hiện hữu. Bổ sung OpenAPI examples theo version; không tạo CRUD StepType.
- Class dự kiến sửa: `WorkflowStepContractValidator`, `WorkflowJsonValidator`, `WorkflowValidationServiceImpl`, `JobRuntimePayloads`, `PiJobRuntimeServiceImpl`, các ServerStepHandler. NEW `StepResultValidator` nếu chưa có chức năng tương đương.
- Quyền: ADMIN cấu hình graph; DEVICE nộp kết quả được kiểm; STAFF xác nhận nghiệp vụ theo Phase 15.
- Migration: không sửa V1–V15; schema/catalog delta nếu cần chỉ forward sau duyệt. Workflow đã publish dùng version cũ hoặc bị chặn dispatch rõ ràng nếu runtime không hỗ trợ; không sửa âm thầm.
- Test: fixture hợp lệ/không hợp lệ đủ 11 loại; null bắt buộc, field lạ, thiếu actor, số sai kiểu, MOVE chưa reached, false confirmation, expression quá giới hạn và unknown version.
- Bàn giao/DoD: `docs/step-contract-matrix.md`, validator và examples thống nhất; test dự kiến `StepContractIntegrationTest`; không còn thành công chỉ vì JSON khác null.

## 5. Phase 17 — Context, event chờ và ACK nghiệp vụ

**DONE — 2026-10-08.** Chi tiết implementation, API, migration và test: [phase17-18-runtime-delivery.md](phase17-18-runtime-delivery.md).

**Mục tiêu:** sửa P1-03, P1-04 và hoàn thiện event lifecycle phía Server.

- Entity: Job.contextJson, JobStep, JobEvent, Alert, AlertRecipient; tận dụng bảng có sẵn trước khi đề xuất bảng mới.
- REVISE SET_CONTEXT: nhận/kiểm kết quả theo assignment đã cấu hình; chỉ cập nhật /vars; đánh giá từ context trước assignment; lưu context+result+event cùng transaction; duplicate không tăng biến lần hai.
- REVISE input resolver/transition validation để Pi và Server cùng nhìn thấy snapshot đã chốt; chốt Pi chọn nhánh, Server kiểm chứng, không hai bên tự chuyển graph độc lập.
- NEW/REVISE WAIT EVENT: kiểm type, correlation, Job, lượt chờ, actor, thời gian; event sớm lưu bền; event trùng hoặc thuộc Job cũ không đánh thức bước mới. DURATION có deadline và không kết thúc sớm.
- REVISE NOTIFY: dùng severity/recipients/require_ack thật; lưu Alert chưa phải delivered/acked; ACK theo policy ANY/ALL và đúng người nhận; timeout có nhánh rõ. Chốt delivered nghĩa gì với transport đang dùng; không giả đã giao thông báo ngoài hệ thống.
- API: nối API acknowledge Alert/Incident đang có vào đúng Step; bổ sung API sự kiện Staff theo contract nếu route hiện hữu chưa biểu diễn arrival/path-cleared. Tên route mới chốt sau kiểm kê controller.
- Class dự kiến sửa: `NotifyStepHandler`, `AlertServiceImpl`, `EmergencyServiceImpl`, `JobStepInputResolverImpl`, `WorkflowRuntimeTransitionServiceImpl`, `PiJobRuntimeServiceImpl`, timeout scheduler; NEW `WorkflowEventService`/Impl nếu cần tách nghiệp vụ chờ.
- Quyền: Staff đúng recipient/scope; Device chỉ event cho robot được cấp. Audit và correlation được giữ xuyên suốt.
- Migration: chỉ khi bảng hiện tại thiếu khóa đối soát/dữ liệu event sớm; duyệt field/constraint trước thêm.
- Test: INIT→INCREMENT→LOOP thật; nhiều assignment đọc snapshot cũ; ACK một/nhiều người; ACK trễ; event trước WAIT; event sai type/job; timeout đua với callback; retry sau mất ACK không tác động lần hai.
- Bàn giao/DoD: `RuntimeContextEventIntegrationTest` dự kiến; cả loop và notify/event flow chạy bằng API với dữ liệu lưu đúng; không cần Pi thật.
- Dependency: Phase 15–16.

## 6. Phase 18 — Giao nhận và xử lý sự cố hoàn chỉnh

**DONE — 2026-10-08.** Đã chốt QR challenge TTL/scope/single-use, confirmation vật lý và recovery policy; chi tiết: [phase17-18-runtime-delivery.md](phase17-18-runtime-delivery.md).

**Mục tiêu:** sửa P1-05, P2-01; nối nghiệp vụ Staff với runtime, không chỉ lưu timestamp.

- Entity: DeliveryRequest, DeliveryItem, AuthVerification, ServiceRequest, Job/JobStep, Robot, Alert/Recipient, EmergencyIncident, AuditLog/JobEvent.
- REVISE xác nhận load/unload/verify: đối chiếu item_code/quantity, stage và actor, hoàn tất đúng HANDLE_PAYLOAD Step; không cho thao tác khi Request/Job sai trạng thái.
- NEW/REVISE xác nhận đóng cửa/kiểm chốt: Staff đã đăng nhập, đúng delivery/stage/Step, có audit; tách commanded state và xác nhận vật lý do Staff. Không suy ra khóa vật lý từ servo report.
- DECISION QR: chốt nội dung token/challenge, TTL, scope và replay policy. Backend phát/kiểm proof đúng phiên giao nhận; không coi enum QR_CODE là bằng chứng. Không xây camera scanner hoặc OTP ngoài phạm vi.
- REVISE cancel/error/timeout sau thao tác vật lý chưa rõ: RECOVERY_REQUIRED và robot không được dispatch mới cho tới reconcile hợp lệ. Job chưa có tác động vật lý có policy riêng được duyệt.
- Emergency: tiếp nhận, tới nơi, giải quyết, đóng/báo nhầm là hành vi riêng; cảnh báo khẩn không phụ thuộc robot rảnh; kiểm quyền và retry/idempotency. Không tự đóng incident từ Job SUCCEEDED.
- API: nối confirm pickup/delivery, auth-verifications, cancel/reconcile, Alert ACK và Incident lifecycle đang có; bổ sung xác nhận stage/QR proof/arrival nếu thiếu. Mọi DTO cần ghi Step/Job hoặc server suy ra không mơ hồ.
- Class dự kiến sửa: `DeliveryServiceImpl`, `AuthVerificationServiceImpl`, `HandlePayloadStepHandler`, `InteractStepHandler`, `EmergencyServiceImpl`, `JobServiceImpl`, `PiJobRuntimeServiceImpl`, DTO/controller/mapper liên quan.
- Migration: delta grant/proof/confirmation nếu thiếu, phải duyệt; không tạo status DeliveryRequest trùng ServiceRequest.
- Test: người gửi và người nhận khác nhau; sai hàng, thiếu hàng, grant hết hạn/sai scope, gửi lặp; lỗi sau UNLOCK/LOAD; cancel ACK; reconcile sai quyền; SOS khi robot bận; chạy toàn graph qua API không tự set status bằng repository để bỏ qua bước.
- Bàn giao/DoD: `DeliveryWorkflowAcceptanceTest`, `EmergencyWorkflowAcceptanceTest` dự kiến; event timeline, trạng thái, grant và hàng nhất quán từ đầu đến cuối bằng Device simulator.
- Dependency: Phase 15–17 và quyết định QR/recovery; không phụ thuộc viết driver phần cứng.

## 7. Phase 19 — Schema nghiệp vụ và dịch vụ thông tin

**Mục tiêu:** sửa P2-04, phần information của P2-03; xác nhận dữ liệu Backend biểu diễn đúng nghiệp vụ.

- DECISION: HospitalInformation là hồ sơ bệnh viện hay nội dung FAQ; source hiện tại đang có title/content/category trong khi Floor tham chiếu bảng này. ERD tổng quan mới không đủ field để tự chuyển schema.
- REVISE/NEW matrix từng Entity–table–field–FK–unique–null với ERD full-field được duyệt; kiểm Floor/Map/Room, Shift/Slot, Request subtype, incident dedup và những bảng security/operations đã bổ sung. Ghi đúng gap, không mặc định tạo lại các module đã có.
- REVISE QUERY_HOSPITAL_INFORMATION: câu hỏi được xử lý qua nguồn nội dung được duyệt, trả answer/source metadata theo contract chốt; không trả danh sách 20 bài rồi coi là đã trả lời đúng câu hỏi.
- Có thể dùng tra cứu nội dung xác định được cho phạm vi demo; nếu dùng dịch vụ AI ngoài thì chỉ triển khai adapter/backend timeout/auth/fallback, không xây model. Nguồn câu trả lời và cách truy vấn cần duyệt trước.
- VoiceInteraction/VoiceCommand: liên kết request/question/answer đúng phiên; kiểm dữ liệu và fallback; không biến log lifecycle thành bằng chứng có STT/TTS.
- API/module: Facility/HospitalInformation hiện hữu, DATA_SERVICE, Voice ingestion; API duyệt nội dung chỉ thêm nếu nghiệp vụ đã chốt và source hiện tại chưa hỗ trợ đủ.
- Class dự kiến sửa: `HospitalInformation`, `Floor`, `FacilityServiceImpl`, repository/DTO/mapper bị ảnh hưởng, `DataServiceStepHandler`, `VoiceServiceImpl`; NEW information query interface/adapter khi cần.
- Quyền: Admin quản lý/duyệt; Device chỉ đọc nội dung công khai có hiệu lực; không lộ tài liệu nội bộ qua câu trả lời.
- Migration: forward có kế hoạch giữ dữ liệu/FK; tên version cụ thể lấy số tiếp theo thực tế lúc triển khai, không đặt trước V16 cho nhiều phase. Tuyệt đối chưa tách bảng khi chưa duyệt ERD delta.
- Test: fresh+upgrade migration, Hospital–Floor đúng relation, nội dung nháp/hết hiệu lực không bị trả về, câu hỏi không có dữ liệu phải fallback, request voice chéo robot bị chặn.
- Bàn giao/DoD: schema-source matrix, migration đã duyệt nếu cần, information contract/test; mọi khác biệt chưa chốt ghi rõ OPEN.
- Dependency: quyết định schema/nội dung; có thể kiểm kê song song Phase 15–18.

## 8. Phase 20 — Hoàn thiện bộ Workflow Backend

**Mục tiêu:** sửa phần catalog P2-03 bằng danh sách nghiệp vụ đã duyệt, không tự thêm hành vi robot.

- DECISION: giữ tám seed hay nhận bộ mười S4; WF_FALL_RESPONSE/WF_RESTRICTED_MONITORING khác code S4; WF_NAVIGATION vẫn có chức năng riêng. Không thể chỉ thêm hai seed là xong.
- REVISE graph hiện hữu theo contract Phase 16–18: input/default/mapping/output, start/priority, nhánh success/failure/timeout/cancel, loop/visit budget và capability.
- NEW có điều kiện: Workflow yêu cầu Staff sạc, phục hồi điều hướng sau đối soát, startup check; chỉ khi được chọn vào phạm vi. Đây là template/dispatch/result contract Backend, không auto-docking, không thực thi Nav2.
- Kiểm entry point cho crowd/fall/restricted qua Request/Schedule hoặc event hợp lệ; không coi có seed là đã có đủ đường tạo Job. Device không được tạo Job trực tiếp.
- API: Admin graph/clone/validate/publish, Request/Schedule hiện hữu; thêm request type/DTO/route chỉ khi nguồn đã chọn yêu cầu và được duyệt; giữ `/api/v1`.
- Class dự kiến sửa: `WorkflowServiceImpl`, `WorkflowValidationServiceImpl`, `ServiceRequestServiceImpl`, `ScheduleServiceImpl`, `JobExecutionServiceImpl`, `JobDispatchServiceImpl`, registry; migration catalog version DRAFT mới.
- Entity: Workflow/Task/Step/StepTransition, Request subtypes, Schedule, Job; không thêm StepType table/CALL_WORKFLOW.
- Quyền: Admin publish riêng; Staff tạo yêu cầu đúng scope; Device gửi interaction/event rồi Server quyết định Job.
- Migration: version DRAFT mới, không sửa graph đang publish hoặc xóa lịch sử. Migration không tự publish.
- Test: mỗi workflow được chọn có API E2E success và nhánh lỗi; capability/hash sai bị chặn; loop có giới hạn; thông tin từ request tới resolvedInputs/output đúng; không fake success.
- Bàn giao/DoD: `docs/workflow-source-matrix.md`, catalog và acceptance tests tương ứng; công bố rõ tổng số workflow thực sự được duyệt/kiểm thử.
- Dependency: Phase 16–19 và danh mục canonical; các template không liên quan schema information có thể triển khai khi dependency của riêng chúng đã đủ.

## 9. Phase 21 — Nghiệm thu riêng Backend

**Mục tiêu:** chứng minh yêu cầu phía Server bằng API/data thật, không dựa vào số Entity hoặc dòng DONE.

- Không tạo module nghiệp vụ mới mặc định; sửa lỗi chỉ khi kiểm thử phát hiện. Entity/API: toàn bộ phần đã chọn ở Phase 15–20 và regression Phase 1–14.
- Unit: validator, permission, state guard, mapping/context, grant, timeout/ACK.
- Integration MySQL 8.4: Flyway fresh và upgrade, UUID/case/JSON/FK/unique, transaction rollback, race/idempotency; không chạy destructive test trên DB demo.
- API acceptance: Swagger Bearer cho ADMIN/STAFF/DEVICE, valid/invalid request, 401/403, pagination/filter/sort, lỗi thống nhất và không lộ secret; Device simulator chạy contract, không cần code Pi.
- E2E Backend: Request → queue/dispatch → command ACK → step/server action/Staff confirmation → result → audit/report; đủ reject/cancel/offline/reboot/recovery.
- Operations: đo tải với số request/concurrency và ngưỡng được chốt trước; không tự tuyên bố đạt một SLA chưa yêu cầu. Backup/restore vào môi trường thử, kiểm dữ liệu/version sau khôi phục; ShedLock/concurrency và retention dry-run regression.
- File dự kiến: mở rộng các integration tests hiện hữu; `docs/backend-acceptance-final.md`, bảng yêu cầu→API/service/test/evidence; CI workflow chỉ sửa phần thiếu sau đối chiếu.
- Migration: không mặc định; dùng các migration đã duyệt ở phase trước.
- Quyền: kiểm cả ownership/assignment/recipient, không chỉ role.
- Bàn giao/DoD: tất cả ca bắt buộc PASS, không skip test âm thầm; không còn P1; mọi phạm vi chưa nhận có ghi rõ; docs, OpenAPI, schema và contract cùng version. Test hardware/UI/model AI nằm ngoài kết luận Backend.
- Dependency: 15–20 trong phạm vi duyệt; không chờ phần cứng thật để đóng nghiệm thu Backend bằng simulator.

## 10. Thứ tự làm và điểm chờ

1. Phase 15 đã hoàn tất kiểm soát quyền runtime.
2. Phase 16 đã khóa và kiểm thử contract 11 StepType.
3. Phase 17–18 đã hoàn tất runtime event/ACK và nghiệp vụ giao nhận/recovery.
4. Bước kế tiếp là Phase 19 với delta đã duyệt; Phase 20 theo danh mục được chọn.
5. Phase 21 nghiệm thu riêng Backend và cập nhật trạng thái có bằng chứng.

Bảy phase là cách chia công việc đề xuất, không phải bảy module còn trống. Phase 15/17/18 chủ yếu sửa phần đã có; Phase 16/19/20 có quyết định nguồn cần chốt. Không tự thêm ngày hoàn thành, không hứa thời điểm hết token; kết thúc mỗi phase cần checkpoint file/class/test/gap còn lại.

Khi người dùng yêu cầu triển khai một phase, đọc lại baseline và source tương ứng, thêm test tái hiện gap, sửa đúng module, chạy kiểm thử phù hợp rồi cập nhật implementation-status. Chỉ ghi DONE nếu đạt DoD của phase đó.
