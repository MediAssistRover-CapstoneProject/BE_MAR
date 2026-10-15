# Phase 5 — Staff: ca trực, dữ liệu tham chiếu, voice và workflow

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## Ca trực và Facility read-only

- Giữ `GET /api/v1/staff/shifts` và `GET /api/v1/staff/shifts/{shiftId}` theo ownership của Staff hiện tại.
- Khi Staff truy cập shift của người khác, API trả `403` thay vì che thành `404`.
- Đã rà soát các route Facility và hospital article nội bộ: Staff/Admin được đọc; mọi mutation vẫn có `hasRole('ADMIN')`.
- Map artifact endpoint chỉ trả metadata/reference hiện hữu, không sinh download credential hay secret.

## Voice history và privacy

- `GET /api/v1/voice/interactions` trả `InteractionMetadataResponse`; DTO list không có account, transcript hoặc audio URI.
- `GET /api/v1/voice/commands` trả `CommandMetadataResponse`; DTO list không có raw text, normalized text hoặc parameters. `serviceRequestId` chỉ xuất hiện khi actor có quyền xem raw data.
- `rawDataAvailable` cho UI biết actor có thể mở detail hay không mà không đưa nội dung nhạy cảm vào list.
- Detail interaction của Admin giữ đầy đủ dữ liệu audit. Với Staff, transcript/audio/account bị redact trừ khi ít nhất một command liên kết đến ServiceRequest thuộc operational scope của actor.
- Detail command yêu cầu command liên kết đến ServiceRequest thuộc operational scope; Staff ngoài scope hoặc command không có request nhận `403`. Admin giữ quyền audit đầy đủ.
- Time filter tiếp tục dùng khoảng nửa mở `[from,to)` và validation từ phải nhỏ hơn to.

## Published Workflow catalog

- Thêm `GET /api/v1/workflows/step-types` cho Staff/Admin.
- Thêm `GET /api/v1/workflows/published?serviceType=&page=&size=` trả page summary, chỉ gồm workflow `PUBLISHED` và `acceptsNewJobs=true`.
- Thêm `GET /api/v1/workflows/published/{id}` trả graph detail; draft, archived hoặc workflow không nhận job trả `404`.
- Các route tương thích dưới `/api/v1/admin/workflows/step-types` và `/api/v1/admin/workflows/published/**` vẫn hoạt động và được đánh dấu deprecated trong OpenAPI.
- Summary canonical không trả compiled definition, definition hash, publisher account, input UI/default inputs hoặc row/audit metadata.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests package`: **PASS**, bao gồm compile main và toàn bộ test source.
- `Phase1ScopePolicyTest`, `DataServiceStepHandlerTest`, `RuntimeAuthorizationTest`: **18/18 PASS**.
- Mở rộng `Phase10IntegrationTest` cho cross-staff shift `403`.
- Mở rộng `Phase12IntegrationTest` cho metadata-only list, interaction redaction, command scope `403` và Admin audit detail.
- Mở rộng `WorkflowIntegrationTest` cho canonical step types, published filtering/summary, detail và archived `404`.
- Lần chạy ba integration suite phát hiện 9 test, 0 assertion failure và 9 lỗi khởi tạo môi trường vì Testcontainers không tìm thấy Docker. Phase chỉ nên chuyển sang `COMPLETE` sau khi MySQL integration và full regression chạy xanh trên môi trường có Docker.
