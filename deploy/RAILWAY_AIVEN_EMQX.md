# Deploy MediAssistRover: Railway + Aiven MySQL + EMQX Cloud

Tài liệu này dành cho Windows PowerShell. Không commit mật khẩu, token hoặc file `.env`.

## 0. Sơ đồ triển khai

```text
Frontend --HTTPS/JWT--> Railway (Spring Boot) --TLS--> Aiven MySQL
                               |
                               +--MQTT/TLS 8883--> EMQX Cloud <--MQTT/TLS-- Raspberry Pi
```

- Railway chạy backend.
- Aiven lưu dữ liệu MySQL.
- Flyway trong backend tạo/cập nhật schema Aiven từ V1 đến V30.
- EMQX là broker chuyển message giữa backend và Pi.
- Nginx và Docker Compose trong repo không dùng cho phương án Railway này.

## 1. Việc phải làm ngay

Credential Aiven đã từng được đặt trong `application.properties`. Hãy vào Aiven Console và reset/rotate mật khẩu trước khi deploy. Không dùng lại mật khẩu cũ.

Code hiện tại đã được đổi để production chỉ nhận credential qua biến môi trường. Không đặt credential thật vào `application.properties`, `.env.example` hoặc tài liệu này.

## 2. Tạo hai JWT secret

Mở PowerShell và chạy nguyên khối sau:

```powershell
function New-RandomSecret {
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    [Convert]::ToBase64String($bytes)
}

$JwtSecret = New-RandomSecret
$DeviceJwtSecret = New-RandomSecret
"JWT_SECRET=$JwtSecret"
"DEVICE_JWT_SECRET=$DeviceJwtSecret"
```

Lưu hai dòng kết quả vào password manager. Hai giá trị phải khác nhau.

## 3. Chuẩn bị Aiven MySQL

1. Đăng nhập Aiven Console và mở MySQL service hiện có.
2. Vào **Connect > Databases**.
3. Chọn **Create database**.
4. Tạo database tên `backend_robot_v1`.
5. Vào **Connect > Users** và tạo service user tên `mediassist_app`.
6. Lưu password mới của user này.
7. Trong **Overview/Quick connect**, ghi lại Host và Port.

Chuỗi JDBC dùng trên Railway:

```text
jdbc:mysql://AIVEN_HOST:AIVEN_PORT/backend_robot_v1?sslMode=REQUIRED&serverTimezone=UTC
```

Không tạo bảng bằng tay và không đổi `DDL_AUTO` thành `update`. Lần khởi động đầu tiên, Flyway sẽ tự chạy V1 đến V30. Nên dùng database trống để tránh lỗi `non-empty schema without schema history`.

## 4. Push cấu hình an toàn lên GitHub

Đứng tại thư mục repo và chạy:

```powershell
git status --short
git diff -- src/main/resources/application.properties src/main/resources/application-prod.properties .env.example deploy/railway-variables.example deploy/RAILWAY_AIVEN_EMQX.md
git add src/main/resources/application.properties src/main/resources/application-prod.properties .env.example deploy/railway-variables.example deploy/RAILWAY_AIVEN_EMQX.md
git commit -m "chore: prepare Railway Aiven and EMQX deployment"
git push origin thanh
```

Trước khi push, đọc `git diff` và bảo đảm không còn mật khẩu thật.

## 5. Tạo backend trên Railway

1. Đăng nhập Railway.
2. Chọn **New Project > Deploy from GitHub repo**.
3. Chọn repo backend và branch `thanh`.
4. Railway sẽ tự nhận `Dockerfile` ở thư mục gốc.
5. Mở service backend, vào **Variables > Raw Editor**.
6. Copy nội dung `deploy/railway-variables.example` vào Raw Editor.
7. Thay tất cả `CHANGE_ME...` bằng giá trị thật.

Đợt deploy đầu giữ nguyên:

```env
MQTT_ENABLED=false
MQTT_DISPATCH_ENABLED=false
MQTT_ROBOT_IDS=
MAPPING_ENABLED=false
```

Không cần tự khai báo `PORT`; Railway cấp biến này và backend đã đọc `${PORT:8080}`.

Nếu frontend chưa có domain, tạm thời dùng:

```env
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Khi frontend đã deploy, đổi thành domain chính xác, không có dấu `/` cuối:

```env
CORS_ALLOWED_ORIGINS=https://ten-frontend.example.com
```

Nhiều origin được phân cách bằng dấu phẩy:

```env
CORS_ALLOWED_ORIGINS=https://ten-frontend.example.com,http://localhost:5173
```

## 6. Domain và health check Railway

1. Vào **Settings > Networking > Public Networking**.
2. Chọn **Generate Domain**.
3. Vào phần deploy/health check và đặt path:

```text
/actuator/health
```

4. Đặt health-check timeout khoảng `300` giây cho lần chạy Flyway đầu tiên.
5. Chỉ chạy **1 replica** trong giai đoạn đầu.

Kiểm tra bằng PowerShell:

```powershell
$BackendUrl = "https://CHANGE_ME_BACKEND_DOMAIN"
Invoke-RestMethod "$BackendUrl/actuator/health"
```

Kết quả đúng:

```json
{"status":"UP"}
```

Swagger:

```text
https://CHANGE_ME_BACKEND_DOMAIN/swagger-ui.html
```

Trong log Railway cần thấy Flyway hoàn tất migration và Spring Boot started. Nếu health check fail, xem lỗi đầu tiên trong Deploy Logs; không xóa database và không bật `DDL_AUTO=update` để chữa tạm.

## 7. Test đăng nhập admin

Thay URL và password rồi chạy:

```powershell
$BackendUrl = "https://CHANGE_ME_BACKEND_DOMAIN"
$LoginBody = @{
    username = "admin"
    password = "CHANGE_ME_ADMIN_PASSWORD"
    deviceInfo = "PowerShell deployment check"
} | ConvertTo-Json

$Login = Invoke-RestMethod `
    -Method Post `
    -Uri "$BackendUrl/api/v1/auth/login" `
    -ContentType "application/json" `
    -Body $LoginBody

$AccessToken = $Login.accessToken
$AccessToken
```

Không gửi `$AccessToken` cho người khác.

## 8. Tạo broker EMQX Cloud

1. Đăng nhập EMQX Cloud.
2. Tạo Project nếu chưa có.
3. Chọn **New Deployment > Serverless**.
4. Chọn region gần Railway/Pi và hoàn tất **Deploy**.
5. Chờ trạng thái **Running**.
6. Trong **Overview**, copy `Address`. Serverless sử dụng MQTT TLS port `8883`.
7. Download CA certificate và giữ file này để cấu hình trên Raspberry Pi nếu hệ điều hành/Python không nhận CA hệ thống.

Không dùng port `1883`. Backend yêu cầu URI dạng:

```text
ssl://EMQX_ADDRESS:8883
```

### 8.1 Tạo tài khoản broker cho backend

Vào **Access Control > Authentication > Add**:

```text
Username: mar-backend
Password: một mật khẩu ngẫu nhiên mạnh
```

Lưu mật khẩu vào password manager.

### 8.2 ACL cho backend

Vào **Access Control > Authorization/Client Authorization > Username**. Tạo các rule cho username `mar-backend`:

| Permission | Action | Topic |
|---|---|---|
| Allow | Subscribe | `mar/robots/+/events` |
| Allow | Subscribe | `mar/robots/+/heartbeat` |
| Allow | Subscribe | `mar/robots/+/presence` |
| Allow | Subscribe | `mar/robots/+/map-preview` |
| Allow | Publish | `mar/robots/+/commands` |
| Allow | Publish | `mar/robots/+/event-acks` |

Trong tab **All Users**, tạo rule cuối cùng:

```text
Topic: #
Action: Publish and Subscribe
Permission: Deny
```

Serverless mặc định giống blacklist; rule `Deny #` biến cấu hình thành whitelist. Hãy tạo các Allow rule trước rồi mới tạo deny-all.

## 9. Kết nối Railway với EMQX nhưng chưa điều khiển robot

Trong Railway Variables, sửa:

```env
MQTT_ENABLED=true
MQTT_DISPATCH_ENABLED=false
MQTT_BROKER_URI=ssl://CHANGE_ME_EMQX_ADDRESS:8883
MQTT_CLIENT_ID=mar-backend-prod
MQTT_USERNAME=mar-backend
MQTT_PASSWORD=CHANGE_ME_EMQX_BACKEND_PASSWORD
MQTT_ROBOT_IDS=
MQTT_ENABLED_STEP_TYPES=WAIT,MOVE,CONTROL_HARDWARE,EXECUTE_SENSING
```

Railway sẽ redeploy. Trong EMQX Console, trang Clients/Sessions phải xuất hiện client `mar-backend-prod`. Nếu không kết nối:

- kiểm tra host và port 8883;
- kiểm tra username/password;
- giữ hostname EMQX gốc, không thay bằng IP hay CNAME;
- xem Railway logs tìm `MQTT initial connection failed` hoặc `SUBACK denied`.

## 10. Tạo robot trong backend

Sau khi có `$AccessToken` từ bước 7:

```powershell
$Headers = @{ Authorization = "Bearer $AccessToken" }
$RobotBody = @{
    robotCode = "ROBOT001"
    robotName = "MediAssist Rover 01"
    model = "Raspberry Pi Rover"
    serialNumber = "PI-001"
    currentMapId = $null
    protocolVersion = 1
    capabilitiesJson = @{}
} | ConvertTo-Json -Depth 10

$Robot = Invoke-RestMethod `
    -Method Post `
    -Uri "$BackendUrl/api/v1/robots" `
    -Headers $Headers `
    -ContentType "application/json" `
    -Body $RobotBody

$RobotId = $Robot.robotId
$RobotId
```

Cấp device secret dùng cho REST authentication của Pi:

```powershell
$Provision = Invoke-RestMethod `
    -Method Post `
    -Uri "$BackendUrl/api/v1/robots/$RobotId/provision-secret" `
    -Headers $Headers

$PiDeviceSecret = $Provision.deviceSecret
$PiDeviceSecret
```

Secret này chỉ trả về một lần. Nó khác với mật khẩu EMQX.

Pi lấy REST device token bằng:

```powershell
$PiAuthBody = @{
    robotCode = "ROBOT001"
    deviceSecret = $PiDeviceSecret
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "$BackendUrl/api/v1/pi/auth/token" `
    -ContentType "application/json" `
    -Body $PiAuthBody
```

## 11. Tạo tài khoản EMQX cho robot

Trong **Authentication > Add**, tạo một user riêng cho Pi:

```text
Username: giá trị UUID trong $RobotId
Password: một mật khẩu MQTT riêng cho robot
```

Ví dụ username phải là UUID thuần, chữ thường; không thêm `robot-` phía trước.

Tạo Allow rule theo **Username**, thay `ROBOT_UUID` bằng `$RobotId`:

| Permission | Action | Topic |
|---|---|---|
| Allow | Publish | `mar/robots/ROBOT_UUID/events` |
| Allow | Publish | `mar/robots/ROBOT_UUID/heartbeat` |
| Allow | Publish | `mar/robots/ROBOT_UUID/presence` |
| Allow | Publish | `mar/robots/ROBOT_UUID/map-preview` |
| Allow | Subscribe | `mar/robots/ROBOT_UUID/commands` |
| Allow | Subscribe | `mar/robots/ROBOT_UUID/event-acks` |

Không cho robot truy cập topic của robot khác. Command và ACK không được publish với retain; presence được phép retain.

Thông số Pi cần lưu cục bộ:

```env
BACKEND_URL=https://CHANGE_ME_BACKEND_DOMAIN
ROBOT_ID=CHANGE_ME_ROBOT_UUID
ROBOT_CODE=ROBOT001
DEVICE_SECRET=CHANGE_ME_REST_DEVICE_SECRET
MQTT_HOST=CHANGE_ME_EMQX_ADDRESS
MQTT_PORT=8883
MQTT_USERNAME=CHANGE_ME_ROBOT_UUID
MQTT_PASSWORD=CHANGE_ME_ROBOT_MQTT_PASSWORD
MQTT_CLIENT_ID=pi-CHANGE_ME_ROBOT_UUID
MQTT_CA_FILE=/etc/mediassist/emqx-ca.crt
```

## 12. Enroll robot vào MQTT

Chỉ làm bước này khi chương trình MQTT trên Pi đã hỗ trợ contract `MAR-STEP-2`, gửi HELLO/heartbeat và xử lý ACK/command.

Đổi Railway Variable:

```env
MQTT_ROBOT_IDS=CHANGE_ME_ROBOT_UUID
```

Từ thời điểm đó robot này được backend xem là MQTT-managed; REST heartbeat, location và REST command polling cũ sẽ không còn là đường chạy chính cho robot đó.

Ban đầu vẫn giữ:

```env
MQTT_DISPATCH_ENABLED=false
```

Khi Pi đã handshake ổn định, thấy HELLO_ACK/SERVER_HEARTBEAT và đã test workflow schema 3, mới đổi:

```env
MQTT_DISPATCH_ENABLED=true
```

Không chuyển rollout sang `MQTT_ONLY` ngay. Thứ tự an toàn là `LEGACY_COMPATIBLE`, sau đó `DRAINING`, cuối cùng mới `MQTT_ONLY`. Chuyển đổi này không thể đảo ngược bằng API và yêu cầu toàn bộ legacy job đã hoàn tất/reconcile.

## 13. Frontend gọi backend

Frontend chỉ cần base URL:

```env
VITE_API_BASE_URL=https://CHANGE_ME_BACKEND_DOMAIN
```

Request đăng nhập:

```javascript
const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    username: "admin",
    password: "YOUR_ADMIN_PASSWORD",
    deviceInfo: navigator.userAgent
  })
});

const tokens = await response.json();
```

Các request bảo vệ gửi:

```text
Authorization: Bearer ACCESS_TOKEN
```

Nếu browser báo CORS, kiểm tra `CORS_ALLOWED_ORIGINS` có đúng scheme + hostname + port của frontend và không có dấu `/` cuối.

## 14. Mapping và volume

Hiện để `MAPPING_ENABLED=false`. Nếu sau này bật upload map, gắn Railway persistent volume vào:

```text
/app/data/map-artifacts
```

Sau đó đặt:

```env
MAPPING_ENABLED=true
MAPPING_STORAGE_DIR=/app/data/map-artifacts
```

Không bật mapping mà không có volume vì filesystem container Railway không phải nơi lưu artifact lâu dài.

## 15. Checklist hoàn tất

- [ ] Đã rotate mật khẩu Aiven cũ.
- [ ] Database `backend_robot_v1` trống được tạo trên Aiven.
- [ ] Railway dùng profile `prod` và health check trả `UP`.
- [ ] Flyway V1-V30 chạy thành công.
- [ ] Admin đăng nhập được.
- [ ] Frontend domain nằm trong `CORS_ALLOWED_ORIGINS`.
- [ ] EMQX dùng TLS 8883 và có backend user riêng.
- [ ] ACL có allow cụ thể và deny-all cuối cùng.
- [ ] Mỗi Pi có username/password MQTT riêng.
- [ ] `MQTT_DISPATCH_ENABLED=false` cho đến khi Pi handshake thành công.
- [ ] Railway chỉ chạy một replica trong giai đoạn đầu.
