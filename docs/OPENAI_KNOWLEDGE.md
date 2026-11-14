# Upload tài liệu cho AI voice

API chạy trong `BE_MAR`. Robot gọi dịch vụ `AI_VOICE/src/web/robot_api.py` để hỏi đáp,
sau đó dùng luồng TTS hiện có. Vector store dùng chung cho robot MAR của dự án.

## Cấu hình cục bộ

Trong môi trường chạy **BE_MAR**, đặt các biến sau bằng cơ chế cấu hình bí mật hiện có.

```dotenv
KNOWLEDGE_ENABLED=true
OPENAI_API_KEY=<API key của project chứa vector store>
OPENAI_VECTOR_STORE_ID=vs_6af8249d9d44819182d2ad94213eacfa
```

Khởi động BE với database phát triển. Flyway chạy `V60__knowledge_documents.sql`.
Tính năng mặc định tắt cho tới khi đặt `KNOWLEDGE_ENABLED=true` và có API key.
Worker dùng scheduler riêng, nhận tối đa bốn tài liệu mỗi lượt, kiểm tra lại trạng thái sau 30 giây.
MySQL cần `max_allowed_packet` lớn hơn 20 MB để nhận file tối đa; 64 MB đáp ứng giới hạn này.
Giới hạn multipart của BE đang là 20 MB/file và 21 MB/request.

Trong môi trường chạy **AI_VOICE**, đặt cùng `OPENAI_API_KEY` và `OPENAI_VECTOR_STORE_ID`.
Giữ `AI_ROBOT_API_TOKEN`, `AI_RESPONSE_MODEL`, `AI_INTENT_MODEL`, cấu hình TTS hiện tại.
Giá trị vector store bật truy xuất OpenAI cho `/api/robot/chat`. Để trống biến này sử dụng
luồng kiến thức đã có. `/health` báo `knowledge_source=OPENAI_VECTOR_STORE` khi bật;
health kiểm tra cấu hình cục bộ, quyền truy cập OpenAI được kiểm tra khi có request thật.

Kho dùng chung có thể trả lời cho robot đang gửi `hospital_information_id` của map hiện tại.
Quản trị viên chỉ upload tài liệu phù hợp để robot đọc cho người dùng. Khi bật kho này,
câu hỏi kiến thức tra vector store và bỏ qua FAQ/kho bài viết cũ. Các nhánh chào hỏi,
SOS và đề xuất điều hướng tiếp tục chạy như trước.

## Gọi API bằng PowerShell

Lấy JWT ADMIN qua luồng đăng nhập hiện có. `$adminJwt` là access token, `$baseUrl` là BE cục bộ.

```powershell
$baseUrl = 'http://localhost:8080'
$adminJwt = '<ADMIN access token>'
curl.exe -X POST "$baseUrl/api/v1/knowledge/documents" `
  -H "Authorization: Bearer $adminJwt" `
  -F 'file=@C:/documents/quy-trinh.pdf'

curl.exe "$baseUrl/api/v1/knowledge/documents?page=0&size=20" `
  -H "Authorization: Bearer $adminJwt"

$documentId = '<id từ kết quả upload>'
curl.exe "$baseUrl/api/v1/knowledge/documents/$documentId" `
  -H "Authorization: Bearer $adminJwt"
```

POST trả HTTP 202 và bản ghi gồm `id`, `vectorStoreId`, `filename`, `sha256`, `sizeBytes`,
`uploadedBy`, `openaiFileId`, `status`, `errorCode`, `createdAt`, `updatedAt`.
Danh sách trả `content`, `page`, `size`, `totalElements`. Dùng response envelope hiện có của BE nếu được bật.
Swagger có nhóm **Knowledge documents**, đăng nhập bằng user bearer scheme để thử upload.

Mỗi lần nhận một file PDF có văn bản, DOCX, TXT hoặc MD. TXT/MD phải là UTF-8.
PDF/DOCX được kiểm tra định dạng sơ bộ; OpenAI kiểm tra và trích xuất nội dung khi indexing.
PDF scan cần chuyển thành văn bản trước, dịch vụ này không có bước OCR.
Tài liệu chỉ sẵn sàng khi `status=COMPLETED`.

## Trạng thái và retry

| Trạng thái | Ý nghĩa |
|---|---|
| QUEUED | File được lưu bền vững trong MySQL, chờ upload |
| UPLOADING | Đang gửi OpenAI Files |
| ATTACHING | Có OpenAI file ID, đang gắn vào vector store |
| PROCESSING | OpenAI đang indexing |
| COMPLETED | Có thể truy xuất |
| FAILED / CANCELLED | Xử lý kết thúc với lỗi hoặc bị hủy |
| RECONCILIATION_REQUIRED | Upload có kết quả chưa xác định, cần đối soát |

Unique key `(vector_store_id, sha256)` chống tạo trùng kể cả request đồng thời.
Upload lại cùng bytes trả bản ghi hiện tại, kể cả trạng thái lỗi.
File chờ upload được lưu trong `payload`, được giải phóng sau khi lưu OpenAI file ID hoặc lỗi kết thúc.
Worker nhận việc bằng lease 180 giây, tiếp tục `QUEUED`, `ATTACHING`, `PROCESSING` sau restart.
Lỗi tạm thời được retry tối đa 20 lần liên tiếp; polling indexing thành công đặt lại bộ đếm lỗi.

Nếu worker dừng giữa upload, timeout hoặc OpenAI báo 5xx trước khi lưu file ID,
bản ghi chuyển `RECONCILIATION_REQUIRED` để tránh upload thêm bản sao. `document-<uuid>.<extension>`
là tên file vận chuyển trên OpenAI; thuộc tính `title` giữ tên tài liệu gốc khi attach.
Lỗi trả mã cố định, ví dụ `OPENAI_HTTP_401`, `OPENAI_HTTP_429`, `OPENAI_INDEXING_FAILED`.

Đối soát do người vận hành thực hiện. Tra file trên OpenAI project theo thời gian/tên upload,
kiểm tra nội dung và gắn `openai_file_id` chính xác vào bản ghi trước khi chuyển về `ATTACHING`.
Với file chắc chắn chưa tạo, người vận hành có thể xóa bản ghi lỗi bằng ID rồi upload lại.
Chỉ thực hiện thao tác sửa trạng thái khi tài liệu không còn lease đang chạy.

## Hỏi đáp

AI gọi Vector Store Search, lấy tối đa 5 đoạn với score tối thiểu 0.2, đưa tối đa 2.400 ký tự/đoạn
vào bước sinh câu trả lời hiện có. `sources` chứa tên tài liệu, score và `file_id`.
`speech_text` tiếp tục tuân theo giới hạn 500 ký tự của Pi/TTS.
Câu hỏi mới luôn tìm lại tài liệu; retry cùng `session_id` và `turn_id` giữ kết quả lần đầu trong 10 phút.
Không có đoạn phù hợp thì trả lời “Tôi không trả lời thông tin này được.”
OpenAI lỗi trả HTTP 503 với mã ứng dụng đã loại thông tin nhạy cảm.

## Kiểm thử chính

```powershell
# Từ BE_MAR
mvn '-Dmaven.test.skip=false' '-Dtest=KnowledgeDocumentTest,KnowledgeDocumentAuthorizationTest,KnowledgeDocumentWorkerTest' test
mvn '-Dmaven.test.skip=true' package

# Từ AI_VOICE, trong môi trường Python đã cài requirements phù hợp
python -m pytest -q tests/test_openai_knowledge.py tests/test_robot_api_latency.py tests/test_intent.py
```

Các kiểm thử dùng mock OpenAI và HTTP server cục bộ. Kiểm tra thật upload → indexing → voice → âm thanh TTS,
quyền API key đối với vector store và triển khai EC2 thực hiện ở giai đoạn tích hợp sau theo yêu cầu.

Tham khảo giao thức [OpenAI Retrieval](https://developers.openai.com/api/docs/guides/retrieval).
