# Kế hoạch đối chiếu nguồn và hoàn thiện nghiệm thu

Ngày: 2026-10-07. Trạng thái: PROPOSED; bước lưu nguồn đã hoàn tất. Kế hoạch này bổ sung [plan hiện tại](plan.md), không đánh số lại hoặc hủy kết quả Phase 1–14. Căn cứ: [source-baseline.md](source-baseline.md), [S1–S6](sources/README.md), [I1–I7](source-images-notes.md).

## A. Khóa nguồn và lựa chọn cần chốt

Đã hoàn tất: lưu sáu snapshot có hash, các ghi chú hình, tóm tắt 11 StepType/10 Workflow, danh sách khác biệt C01–C15. Giữ quyết định trực tiếp trước đây làm baseline cho tới khi có quyết định mới.

Trước công việc phụ thuộc cần chốt một lần:

1. Bộ tám seed hiện hành hay bộ mười S4 là danh mục chuẩn tiếp theo; tên alias, phạm vi sạc thủ công/startup/recovery và WF_NAVIGATION xử lý thế nào?
2. Giữ REST polling/MySQL như đã duyệt hay chủ động mở rộng transport? Chỉ đổi nếu có yêu cầu mới rõ ràng.
3. Contract S3 schema=2 sẽ thay contract cũ hay có adapter/version song song? Chốt /inputs so với /input, payload/data/result và action names.
4. Workflow status giữ PUBLISHED/ARCHIVED hay bổ sung enable/disable riêng? Không tự thêm INACTIVE/RETIRED từ sơ đồ.
5. Team Pi xác nhận profile UNLOCK 0 hay 2400, khả năng sensor khóa và bằng chứng kiểm chốt; backend không quyết định PWM.
6. AI chạy ở đâu; backend chỉ nhận kết quả hay gọi dịch vụ AI riêng; kho FAQ duyệt nội dung lấy từ đâu?
7. ERD full-field nào được phê duyệt để giải quyết Floor/HospitalInformation và Shift; cơ chế nâng FAILED thành RECOVERY_REQUIRED trong delivery là gì?
8. Pi là executor quyết định transition và server chỉ kiểm chứng, hay contract yêu cầu server trả quyết định ở một số bước? Chốt quyền sở hữu runtime trước sửa engine.

Những lựa chọn chưa có câu trả lời được ghi OPEN; không cản lưu tài liệu và kiểm kê read-only.

## B. Các gói việc đề xuất

### B1 — Đối chiếu schema và state machine

- Nguồn: S1/S2/S3, S6 #1/#4/#12/#23/#45.
- Module: Account/Staff, Facility, Workflow/Task/Step/Transition, Request, Job/JobTask/JobStep, Robot, Alert/Emergency, Shift/Slot.
- Công việc: lập matrix từng field PK/FK/null/unique/type/cardinality giữa ERD full-field được chốt, JPA và Flyway. Đọc cả tooltip/nhãn cạnh; tách state kết nối Robot với hoạt động robot; kiểm guard chuyển trạng thái và actor.
- Bàn giao dự kiến: `docs/schema-source-matrix.md`, `docs/state-machine-source-matrix.md`; mỗi dòng MATCH/DIFFERENT/UNSPECIFIED kèm đường dẫn bằng chứng.
- Dependency: A7; phần kiểm kê không thay schema có thể làm ngay.
- Test/DoD: mọi Entity/bảng hiện hữu có dòng đối chiếu, mọi delta có lý do và quyết định; nếu duyệt sửa thì migration forward, MySQL fresh+upgrade, UUID/case/FK/rollback và security tests. Không sửa checksum V1–V15 trên môi trường đã dùng chung.

### B2 — Chốt hợp đồng Pi và schema StepType

- Nguồn: S3/S4, I1/I2, C02/C03/C07/C08/C09/C15, S6 #6/#13/#16/#23/#29/#56/#60.
- Công việc: so từng input/output/action/error code của 11 enum với validator, service registry, compiled command và Pi adapter; ghi rõ field bắt buộc/null, đơn vị, timestamp/freshness và envelope version.
- API/module: các route `/api/v1/pi/**` hiện tại; không thêm URL mới khi chưa đối chiếu controller. HELLO/command/ACK/service/event/heartbeat là message semantics, không mặc nhiên một endpoint mới mỗi tên.
- Security: Device ownership Job/JobStep; grant Staff đúng người/job/purpose; operationKey và message identity ổn định qua retry transport; không log secret/QR nhạy cảm.
- Bàn giao dự kiến: `docs/pi-contract-delta.md`, examples request/response đã che secret, cập nhật contract acceptance; mỗi breaking delta có version/rollout/rollback.
- Dependency: A3/A5/A8 và phần identity của B1.
- Test/DoD: contract replay request→resolved input→output→event→ACK; duplicate/out-of-order/event sớm/timeout/reboot; không nhầm semantic ACK và transport ACK; không tự replay servo; hình thức success không vượt semantic gate. Integration test không đánh dấu hardware PASS.

### B3 — Đối chiếu và chuẩn hóa bộ Workflow

- Nguồn: S4 đầy đủ mười workflow; S1 và I3–I7 dùng review khác biệt; S6 #13/#14/#28/#44/#48/#49/#50.
- Công việc: map bộ tám hiện tại với danh mục đã chọn; mỗi step ghi code/type/executor/input/output/transition success/failure/timeout/cancel/visit budget; chốt JSON Pointer và SAFE_EXPR_V1 so với condition engine hiện tại.
- Module: WorkflowService, validator/compiler, input resolver/runtime, registry server, seed/catalog và API admin graph đang có. Giữ StepType enum cố định, granular + aggregate transactional.
- Bàn giao dự kiến: `docs/workflow-source-matrix.md`, catalog DRAFT version mới nếu được duyệt; không sửa version đã PUBLISHED hoặc tự publish.
- Dependency: A1/A3/A8, B1/B2.
- Test/DoD: toàn bộ workflow đã chọn validate graph/schema/semantics; test false confirmation, auth denied, missing output, loop cap, blocked endpoint group, notification timeout, emergency escalation và recovery; giữ snapshot Job đang chạy. Không gắn DONE cho số workflow chưa được chọn.

### B4 — Kiểm tra tính đủ của nghiệp vụ và tích hợp AI

- Nguồn: S5 Major Features, S6 #15/#24–27/#34–36/#46/#47/#57/#59/#63/#69, I3/I5/I6.
- Công việc: kiểm API Staff giao nhận/QR, xác nhận cửa/chốt, Alert recipient/ack/arrival/resolve/false alarm; sensor freshness và evidence permission; crowd chưa quan sát khác count=0; analytics truy ngược được dữ liệu gốc.
- Voice: kiểm log lifecycle riêng với tích hợp STT/intent/RAG/TTS; thông tin ngoài kho phải fallback; câu trả lời đúng interaction/job; không tự coi lưu VoiceCommand là đã có AI voice hoàn chỉnh.
- FAQ: kiểm HospitalInformation/profile có đủ nguồn nội dung được duyệt không; mọi bảng mới chỉ là đề xuất có field/PK/FK/delta riêng.
- Bàn giao dự kiến: `docs/feature-coverage-matrix.md`, danh sách gap và contract tích hợp; AI/model/UI thuộc đội phụ trách, backend giữ quyền/metadata/result/storage.
- Dependency: A6, B1/B2; phần review có thể đi cùng B3.
- Test/DoD: mỗi Major Feature có trạng thái backend, client/Pi dependency, test và evidence; kiểm người sai quyền, QR hết hạn/sai delivery, xác nhận trùng, phát hiện trùng, không Staff phản hồi; không tự đóng incident khi Job SUCCEEDED.

### B5 — Nghiệm thu tích hợp, triển khai và bàn giao

- Nguồn: S6 #44–78/#81/#85–111; docs backend-demo-acceptance và pi-contract-and-hardware-acceptance hiện có.
- Công việc: khóa image/commit/catalog/map hash; chạy demo API rồi contract với Pi, web/mobile và robot thật; test load có mục tiêu được thống nhất; backup/restore vào môi trường thử và kiểm login/job/report sau restore.
- Bàn giao: acceptance matrix có ID case, expected/actual, người kiểm, UTC time, version, log/video, defect/retest; hướng dẫn deploy/restore/demo và hạn chế đã biết. Không thực thi deploy, xóa data hoặc restore lên DB đang dùng chỉ vì đã ghi trong plan.
- Dependency: B1–B4, thiết bị và đội Pi/UI/AI sẵn sàng.
- Test/DoD: security+unit+integration+Swagger pass ở cùng bản; API PASS và hardware PASS tách cột; các workflow được chọn chạy đủ success/failure/cancel/timeout/recovery; không còn lỗi chặn luồng; báo cáo chỉ ghi bằng chứng thật.

## C. Theo dõi tiến độ

| Gói | Trạng thái hiện tại | Đầu ra đã có / cần làm |
|---|---|---|
| A lưu nguồn | DONE — documentation | Sáu trích xuất, hash, ghi chú bảy hình, baseline và conflict register |
| A khóa mọi khác biệt | OPEN | Cần quyết định cho các mục ảnh hưởng schema/protocol/catalog |
| B1 | PARTIAL | Phase 19 đã kiểm inventory/JPA/Flyway và hoàn tất delta Hospital profile/content được duyệt tại `schema-source-matrix.md`; Draw.io và state-machine source matrix tổng thể chưa cập nhật |
| B2 | PLANNED | Đã phát hiện delta; chưa triển khai protocol mới |
| B3 | PLANNED | Đã tổng hợp mười nguồn và tám hiện tại; chưa thay seed |
| B4 | PARTIAL | Phase 18 đã đóng delivery/emergency; Phase 19 đã đóng kho information được duyệt và fallback; AI/feature coverage tổng thể còn mở |
| B5 | PLANNED | Kế thừa bằng chứng demo cũ; cần chạy lại sau delta và nghiệm thu thật |

Theo yêu cầu tiếp theo của người dùng, các gap Backend đã được chia thành [Phase 15–21](backend-remaining-phases.md): Phase 15–19 đã triển khai và có checkpoint/test; B3/Phase 20 và nghiệm thu B5/Phase 21 còn mở. Hardware/UI/AI acceptance vẫn ngoài plan Backend. Hoàn tất một phase Backend không đồng nghĩa tự động hoàn tất mọi đầu việc liên đội trong B1–B5.
