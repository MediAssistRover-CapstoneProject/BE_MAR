# Phase 19 — Schema nghiệp vụ và dịch vụ thông tin bệnh viện

Ngày hoàn tất: 2026-10-09. Trạng thái: **DONE — Backend**.

## 1. Quyết định dữ liệu đã khóa

- `HospitalInformation` giữ vai trò hồ sơ bệnh viện duy nhất của MVP; `Floor.hospital_information_id` tiếp tục tham chiếu bảng này.
- Nội dung hỏi đáp đã được bệnh viện phê duyệt không nhồi vào hồ sơ và không thay quan hệ Floor. Bảng mới `HospitalInformationArticle` lưu FAQ, vị trí, quy trình và hướng dẫn.
- Đây là delta schema đã được người dùng duyệt. File ERD Draw.io không bị tự sửa.
- Backend dùng tra cứu deterministic trên nội dung đã duyệt. Phase này không triển khai mô hình AI, STT, TTS hoặc RAG bên ngoài.

## 2. Schema và lifecycle

Migration forward `V17__create_hospital_information_article.sql` tạo `HospitalInformationArticle` với:

- PK/FK UUID đồng nhất `BINARY(16)`; tên bảng PascalCase và cột giữ snake_case.
- FK tới `HospitalInformation` và Account cập nhật gần nhất.
- Unique `(hospital_information_id, article_code, language_code)`.
- Nội dung: category, canonical question, title, answer, language và `keywords_json` kiểu MySQL JSON.
- Trạng thái `DRAFT → PUBLISHED → RETIRED`; sửa bài đang publish đưa lại `DRAFT` để phải duyệt lại.
- Chỉ bài `PUBLISHED`, `is_public=true`, đúng language và trong effective window được runtime truy vấn.
- `row_version` dùng optimistic locking; effective window có CHECK constraint.

Không sửa migration V1–V16, không thay `HospitalInformation`, không đổi FK của Floor.

## 3. API quản trị

Base path: `/api/v1/hospital-information/{hospitalId}/articles`.

| Method | Path | Role | Mục đích |
|---|---|---|---|
| GET | `/` | ADMIN, STAFF | Danh sách phân trang; lọc `status` |
| GET | `/{articleId}` | ADMIN, STAFF | Xem chi tiết trong đúng hospital |
| POST | `/` | ADMIN | Tạo bài DRAFT |
| PUT | `/{articleId}` | ADMIN | Sửa và đưa bài về DRAFT |
| POST | `/{articleId}/publish` | ADMIN | Duyệt/publish bài DRAFT |
| POST | `/{articleId}/retire` | ADMIN | Ngừng sử dụng, giữ lịch sử |

Device không được gọi CRUD này. Mỗi mutation ghi AuditLog; Staff không thể tạo/sửa/publish/retire.

## 4. Contract truy vấn runtime

`DATA_SERVICE` với `service_key=QUERY_HOSPITAL_INFORMATION` nhận `question` và `language_code`. Kết quả chuẩn:

- Có dữ liệu: `found=true`, `answer`, `source` gồm article id/code/category/title/language/updated time.
- Không có dữ liệu phù hợp: `found=false`, câu trả lời fallback an toàn, `fallback_code=INFORMATION_UNAVAILABLE`, `source=null`.
- Giữ `items` và `count` trong `data` để tương thích các Workflow graph schema 2 đang dùng `/outputs/QUERY/data/items`.

Tra cứu chuẩn hóa chữ hoa/thường, dấu tiếng Việt và chấm điểm theo token đầy đủ. Không dùng substring ngắn vì có thể khớp nhầm nội dung không liên quan. Nội dung DRAFT, RETIRED, private hoặc hết hiệu lực không bao giờ là candidate.

## 5. Voice và ranh giới trách nhiệm

Voice lifecycle/ownership đã có từ Phase 12 và kiểm tra cross-robot vẫn được giữ. Phase 19 chỉ cung cấp nguồn trả lời được duyệt cho DATA_SERVICE; việc thu âm, nhận dạng giọng nói, phát âm thanh và mô hình AI vẫn thuộc Pi/dịch vụ ngoài. Log Voice không được coi là bằng chứng STT/TTS đã chạy.

## 6. Verification

- `Phase19IntegrationTest`: quyền Admin/Staff, lifecycle duyệt, bài private/hết hạn/DRAFT bị loại, fallback, source metadata, DATA_SERVICE compatibility, UUID `BINARY(16)` và quan hệ Hospital–Floor.
- `DataServiceStepHandlerTest`: constructor/contract mới không làm hỏng service handler.
- Fresh MySQL 8.4: Flyway V1–V17 và Hibernate `ddl-auto=validate` thành công.
- Clean full regression Java 21 + MySQL 8.4 Testcontainers: **110 tests / 25 suites, 0 failure, 0 error, 0 skipped**.

## 7. Ngoài Phase 19

- Không tự động sinh câu trả lời bằng AI và không gọi URL/SQL/script tùy ý.
- Không sửa catalog Workflow canonical; thuộc Phase 20.
- Không nghiệm thu Pi/ROS2/hardware thật; thuộc checklist riêng.
- Đối chiếu inventory schema hiện hành nằm tại [schema-source-matrix.md](schema-source-matrix.md).
