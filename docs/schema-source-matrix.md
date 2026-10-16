# Ma trận schema–source Backend tại checkpoint Phase 19

Ngày kiểm: 2026-10-09. Ma trận này ghi trạng thái schema **đang được Backend thực thi**, không tự sửa Logical ERD Draw.io.

## 1. Cách kiểm chứng

- Tên bảng/cột/PK/FK/index/constraint lấy từ Flyway V1–V17.
- Mapping lấy từ JPA entity; clean integration test dùng `ddl-auto=validate` trên MySQL 8.4.
- UUID của delta Phase 19 được kiểm trực tiếp qua `information_schema`: PK/FK đều `BINARY(16)`.
- Quyết định đã duyệt: `HospitalInformation` là hồ sơ bệnh viện; `HospitalInformationArticle` là kho nội dung hỏi đáp riêng.

## 2. Inventory từng bảng

`MATCH` nghĩa là Flyway và mapping JPA hiện hành khớp qua Hibernate validate. `OPS` là bảng vận hành không cần entity nghiệp vụ. `APPROVED DELTA` là phần bổ sung đã được người dùng duyệt.

| Bảng | Nhóm/source | Kết quả |
|---|---|---|
| Account | Identity/security | MATCH |
| StaffProfile | Identity/security | MATCH |
| RefreshToken | Security extension | MATCH |
| RevokedToken | Security extension | MATCH |
| AuditLog | Audit | MATCH |
| SystemSetting | Configuration | MATCH |
| HospitalInformation | Facility profile | MATCH — giữ làm hồ sơ bệnh viện |
| HospitalInformationArticle | Approved information content | APPROVED DELTA — V17, MATCH |
| Floor | Facility | MATCH — FK tới HospitalInformation giữ nguyên |
| Map | Facility | MATCH |
| Zone | Facility | MATCH |
| Room | Facility | MATCH |
| Endpoint | Facility | MATCH |
| EndpointGroup | Facility | MATCH |
| EndpointGroupMember | Facility | MATCH |
| Robot | Robot/device | MATCH |
| RobotStatusHistory | Robot history | MATCH |
| RobotLocationHistory | Robot history | MATCH |
| Workflow | Workflow definition | MATCH |
| Task | Workflow definition | MATCH |
| Step | Workflow definition; StepType enum string | MATCH |
| StepTransition | Workflow definition | MATCH |
| ServiceRequest | Request | MATCH |
| NavigationRequest | Request subtype | MATCH |
| EscortRequest | Request subtype | MATCH |
| DeliveryRequest | Request subtype | MATCH |
| DeliveryItem | Delivery | MATCH |
| Schedule | Scheduling | MATCH |
| Job | Runtime | MATCH |
| JobTask | Runtime snapshot | MATCH |
| JobStep | Runtime snapshot | MATCH |
| JobEvent | Runtime history | MATCH |
| AuthVerification | Runtime authorization | MATCH |
| QrChallenge | Security/delivery extension | MATCH |
| Alert | Alerting | MATCH |
| AlertRecipient | Alerting | MATCH |
| EmergencyIncident | Emergency | MATCH |
| Shift | Staffing | MATCH |
| Slot | Staffing | MATCH |
| DetectionEvent | Monitoring | MATCH |
| CrowdDensityRecord | Monitoring | MATCH |
| HeatmapRecord | Monitoring | MATCH |
| VoiceInteraction | Voice lifecycle log | MATCH |
| VoiceCommand | Voice lifecycle log | MATCH |
| ShedLock | Scheduler operation | OPS — Flyway-managed |
| RetentionArchive | Retention operation | MATCH |

Tổng: **46 bảng** = 44 domain/security + 2 bảng vận hành. Không có bảng `Patient` hoặc `StepType`; Patient không phải Account, StepType là enum source code cố định.

## 3. Quan hệ được kiểm trọng điểm

| Quan hệ/constraint | Kết quả |
|---|---|
| HospitalInformation 1–N Floor | Giữ nguyên, Hibernate validate và Phase19IntegrationTest đạt |
| HospitalInformation 1–N HospitalInformationArticle | V17 FK `BINARY(16)`, delete không cascade |
| Article updatedBy N–1 Account | V17 FK `BINARY(16)`, không lưu credential trong content |
| Article code unique trong hospital+language | Unique DB và pre-check service |
| Article lifecycle/effective window | CHECK DB + service validation + integration test |
| Workflow–Task–Step–Transition | Không đổi trong Phase 19 |
| Workflow–Job–JobStep snapshot | Không đổi trong Phase 19; compatibility `items/count` được giữ |
| Voice interaction/command robot ownership | Không đổi; cross-robot guard từ Phase 12 vẫn qua regression |

## 4. Khác biệt còn mở

- Draw.io chưa được chỉnh để thêm `HospitalInformationArticle`; đây là tài liệu thiết kế cần cập nhật thủ công sau khi chủ dự án duyệt hình thức hiển thị, còn migration/code đã theo quyết định schema được phê duyệt.
- Danh mục/tên/graph Workflow canonical giữa các nguồn vẫn thuộc Phase 20; không phải schema gap của Phase 19.
- Hardware/UI/AI acceptance không thể suy ra từ Hibernate validate và nằm ngoài phạm vi Backend.
- Nếu chuyển từ một sang nhiều bệnh viện, information query phải nhận hospital scope rõ ràng; MVP hiện đã chốt một bệnh viện.
