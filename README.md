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
- GET /api/v1/pi/maps/{mapId}/bundle: YAML/PGM/metadata; DEVICE chỉ tải map được gán.

Giữ 11 step types. BE xử lý nghiệp vụ; Pi chạy handler robot. MOVE cần map đã nạp và profile ACK. Tạo workflow không tự tạo handler phần cứng mới.

Lệnh upload Pi, cấu hình HTTPS và script API nằm trong README duy nhất của gói MAR_OpenAI_Final_2026_10_20 trong Downloads. Source BE này đã được sửa; container đang chạy chỉ đổi sau khi recreate.
