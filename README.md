# MediAssistRover Backend

Java 21, Spring Boot 3.5, MySQL 8.4, MQTT/TLS. Giữ luồng robot từ Downloads và gộp nghiệp vụ GitHub main `0dc215c58b8308ca3dda7290c14b131e2e2046df`.

## Chạy

Giữ `.env` và chứng chỉ hiện tại. Môi trường mới dùng `.env.example`, điền database/JWT/MQTT và tạo chứng chỉ HTTPS.

```powershell
docker compose config --quiet
docker compose --profile production up -d --build mysql backend reverse-proxy
docker compose --profile production restart reverse-proxy
docker compose logs --tail 80 backend
```

Swagger: http://localhost:8080/swagger-ui/index.html. UI: http://localhost:8080/mapping.html. Sao lưu DB trước khi áp dụng V31–V34; không xóa volume database.

## OpenAI / bản đồ

Trong `.env`: TTS_ENABLED=true, OPENAI_API_KEY=<key riêng>, OPENAI_TTS_MODEL=gpt-4o-mini-tts, OPENAI_TTS_VOICE=coral, OPENAI_TTS_SPEED=1.0. Key chỉ ở BE. WAV cache nằm trong volume speech-cache.

- POST /api/v1/pi/speech/prepare: ADMIN chuẩn bị âm thanh, body text/languageCode/speedWpm.
- POST /api/v1/speech-jobs: tạo job phát loa; Pi tải WAV từ BE qua HTTPS.
- POST /api/v1/maps/import: multipart floorId/mapCode/versionNo/mapName/yaml/pgm; map DRAFT.
- POST/PUT /api/v1/endpoints: cấu hình endpoint.
- GET /api/v1/maps/{id}/deletion-impact: ADMIN xem số `Map`, `Endpoint`, `Room`, `Zone`, nhóm điểm, bản vẽ/dữ liệu quét sẽ xóa; `sharedRooms` là số phòng giữ lại và `blockers` liệt kê ràng buộc.
- DELETE /api/v1/maps/{id}/force: ADMIN xóa cứng map, endpoint, phòng chỉ thuộc map, vùng, nhóm điểm, bản vẽ và dữ liệu quét trong database. Bắt buộc header `X-Confirm-Hard-Delete: true` và body `{ "reason": "Lý do từ 5 đến 500 ký tự" }`. Phòng còn liên kết với map khác được giữ, kể cả qua bản nháp hoặc map lưu trữ. Xóa cấu hình SOS của phòng bị xóa, gỡ map khỏi robot đang rảnh và trả số bản ghi đã xóa trong `deleted`.
- Toàn bộ lần xóa map chạy trong một giao dịch. Job, yêu cầu giao hàng, ca trực và các tham chiếu bên ngoài dữ liệu của map sẽ chặn xóa với 409; robot BUSY/STARTING hoặc phiên điều hướng chưa đóng cũng chặn. Backend kiểm tra lại khi thực thi và rollback toàn bộ nếu có lỗi. API `DELETE /maps/{id}`/`POST /maps/{id}/archive` vẫn chỉ lưu trữ; UI dùng `/force` cho nút “Xóa bản đồ”.
- GET /api/v1/pi/maps/{mapId}/bundle: YAML/PGM/metadata; DEVICE chỉ tải map được gán.
- DELETE /api/v1/admin/workflows/{id}/force: ADMIN xóa cứng workflow ở mọi trạng thái cùng Job/Schedule và runtime liên quan; trả 409 nếu còn Job QUEUED, ACCEPTED hoặc RUNNING. ServiceRequest/NavigationRequest gốc được giữ lại.

Giữ 11 step types. BE xử lý nghiệp vụ; Pi chạy handler robot. MOVE cần map đã nạp và profile ACK. Tạo workflow không tự tạo handler phần cứng mới.

Kiểm thử xóa map: `mvn '-Dmaven.test.skip=false' '-DskipTests=false' '-Dtest=MapCascadeDeleteTest,MapCascadeHttpTest' test`.
Kiểm tra SQL, khóa ngoại và rollback dùng H2 riêng trong RAM, không kết nối database ứng dụng.

## Amazon S3 media

Cấu hình `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION=ap-southeast-1` và
`AWS_S3_BUCKET=mediassistrover-media-2026`. Xem `docs/S3_MEDIA.md` để cấu hình IAM, bucket policy và test Swagger.

Lệnh upload Pi, cấu hình HTTPS và script API nằm trong README duy nhất của gói MAR_OpenAI_Final_2026_10_20 trong Downloads. Source BE này đã được sửa; container đang chạy chỉ đổi sau khi recreate.

## Staff delivery and attendance

See [STAFF_DELIVERY.md](docs/STAFF_DELIVERY.md) for check-in/out, on-duty recipients, profile/S3 avatars, delivery notifications and SMTP email deployment.
