# Giao hàng theo staff đang trực

## Luồng sử dụng

1. Admin tạo staff/profile và phân ca có phòng, giờ bắt đầu/kết thúc bằng API admin hiện có.
2. Staff mở `/delivery-qr.html`, đăng nhập, mở **Ca trực và hồ sơ cá nhân**, bấm tải và check-in.
3. Staff A mở **Tạo yêu cầu giao hàng**, chọn bản đồ, điểm lấy hàng và staff B đang trực. Danh sách hiện tên, phòng và giờ hết ca; avatar hiện khi chọn staff.
4. A tạo yêu cầu rồi bấm **Gửi robot đi lấy hàng**. Chỉ bước gửi này mới tạo job; lúc đó server tạo Alert cho B.
5. B mở trang bằng tài khoản của mình: thông báo tự xuất hiện và bấm vào sẽ mở job, không cần nhập Job ID. QR và xác nhận giao nhận giữ nguyên.
6. B nhận email tại địa chỉ trong hồ sơ staff; thông báo vẫn tự xuất hiện trong trang kể cả khi SMTP chưa cấu hình hoặc gửi email lỗi.
7. Checkout hoặc hết giờ ca sẽ loại staff khỏi danh sách đang trực. Job đã tạo giữ nguyên người nhận/phòng; không tự chuyển cho người khác.

Luồng bệnh nhân hỏi phòng/dẫn đường không thay đổi. Điểm lấy hàng A vẫn được chọn thủ công.

## API

Tất cả API staff dùng Bearer token hiện hành. Admin tiếp tục dùng CRUD staff/ca trực hiện có.

| Method và path | Hành vi |
|---|---|
| `POST /api/v1/staff/shifts/{id}/check-in` | Chỉ chủ ca, trong giờ ca PLANNED/ACTIVE. Ghi checkedInAt, chuyển ACTIVE. Gọi lặp không thay thời điểm. |
| `POST /api/v1/staff/shifts/{id}/check-out` | Chỉ chủ ca đã check-in. Ghi checkedOutAt; cho phép checkout muộn. Không check-in lại ca đã checkout. |
| `GET /api/v1/staff/on-duty?mapId=...&roomId=...&page=0&size=20` | ADMIN/STAFF; PageResponse, tối đa 100 mỗi trang. Không cache danh sách. |
| `PATCH /api/v1/staff/me` | Các trường tùy chọn fullName, email, phone, contactExtension; trường không gửi được giữ nguyên. Chuỗi rỗng xóa phone/contactExtension; tên/email không được rỗng. |
| `POST /api/v1/staff/me/avatar` | Multipart `file`; JPEG/PNG/WebP, kiểm tra chữ ký/MIME/đuôi file và giới hạn ảnh hiện hành 20 MiB. Upload qua MediaService/S3 hiện có. |
| `DELETE /api/v1/staff/me/avatar` | Gỡ tham chiếu avatar; không xóa object dùng chung. Admin quản lý media không còn tham chiếu qua API media hiện có. |
| `GET /api/v1/staff/notifications/events` | SSE với Authorization header; event `alerts` là danh sách thông báo giao hàng gửi đích danh staff, tự reconnect phía trang. |
| `GET /api/v1/alerts` | API lịch sử có phân trang và filter `type` hiện có. |

On-duty yêu cầu account/profile hoạt động, role STAFF, không buộc đổi mật khẩu, ca ACTIVE đã check-in, chưa checkout, phòng hoạt động và `startsAt <= now < endsAt`. API trả staffProfileId, accountId, fullName, avatarUrl, shiftId, startsAt, endsAt, checkedInAt, roomId, roomCode, roomName, endpointId, deliverable, reason. Nếu không có mapId, vẫn liệt kê staff nhưng chưa đánh giá được điểm giao. Staff trong phòng thiếu endpoint vẫn hiện với deliverable=false khi lọc đúng tầng của bản đồ.

### Tạo giao hàng

API `POST /api/v1/delivery-requests` giữ `Idempotency-Key` và trường cũ. Cách mới bổ sung `receiverShiftId`, gửi expectedReceiverAccountId, mapId, pickupEndpointId, expectedSenderAccountId và items. Không cần gửi deliveryEndpointId, deliveryRoomId hoặc recipientName. Khi có receiverShiftId, server ghi đè các giá trị đích/tên từ ca và account; staff đăng nhập phải là sender.

Server chọn endpoint active/accessible trong phòng trên đúng map. Có default endpoint group thì dùng thành viên hợp lệ đầu tiên theo sequenceNo; không có group thì bắt buộc có đúng một endpoint. Thiếu/mơ hồ trả lỗi nghiệp vụ. API cũ không có receiverShiftId vẫn nhận endpoint/group như trước.

Ca người nhận được lưu bằng FK DeliveryRequest.receiverShift và snapshot JSON. Lúc submit tạo job, server kiểm tra lại check-in, giờ ca, phòng và endpoint. Nếu ca hết hiệu lực/đổi điểm, tạo lại yêu cầu sau khi tải lại danh sách. Job đã tạo giữ snapshot; không tự đổi tuyến theo các chỉnh sửa ca sau đó.

## Thông báo

- `DELIVERY_CREATED`: sau khi tạo job trong giao dịch submit, không phải lúc tạo bản nháp.
- `DELIVERY_APPROACHING`: chặng MOVE_RECEIVER hoặc TRANSIT của job giao hàng đang chạy, vị trí/path/session còn hợp lệ, khoảng cách dọc đường đi và khoảng cách vị trí tới đích đều <= 5 cm. Không dùng số cm đã làm tròn.
- `DELIVERY_ARRIVED`: MOVE tới người nhận thành công, reached=true; trên MQTT còn yêu cầu physicalStopConfirmed=true và không có interruption.
- Nếu không có mẫu <= 5 cm trước khi hoàn tất MOVE thì chỉ có ARRIVED, không ép robot di chuyển thêm.

Alert lưu trong giao dịch nghiệp vụ với operation key `DELIVERY:{jobId}:{type}`; `delivery_email_outbox` có unique `(alert_id,recipient_id)`. Worker chỉ đọc dữ liệu đã commit, claim một hàng bằng SKIP LOCKED và lease 30 giây trong giao dịch ngắn, sau đó gửi SMTP ngoài giao dịch trên scheduler riêng; retry tối đa 6 lần. Không giữ khóa DB trong lúc chờ mạng. Lỗi email không rollback job. Worker khởi động lại tiếp tục xử lý PENDING và SENDING đã hết lease. Kết quả từ worker cũ chỉ được ghi nếu vẫn sở hữu lease.

Không gửi email khi tài khoản không có email hợp lệ, bị khóa/vô hiệu hóa hoặc job kết thúc/hủy; email tạo job hết hạn sau 1 giờ, email gần tới/đã tới sau 2 phút. ARRIVED vô hiệu hóa APPROACHING còn chờ trong outbox. Alert lịch sử vẫn tồn tại và SSE trong trang không phụ thuộc SMTP.

## Triển khai

- Chạy Flyway V56 (điểm danh, optimistic version, FK ca người nhận) và V57 (email outbox). Dữ liệu ca cũ có checkedInAt=null: staff phải check-in, không tự coi là đã tới ca.
- Đặt `DELIVERY_EMAIL_ENABLED=true`, `DELIVERY_EMAIL_FROM`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`. Dùng secret store cho mật khẩu SMTP. Đặt `OPERATIONS_SCHEDULER_ENABLED=true` (hoặc `CLOUD_OPERATIONS_SCHEDULER_ENABLED=true` với overlay Aiven) để chạy kiểm tra sắp tới và worker email. Nếu SMTP chưa cấu hình, Alert/SSE trong trang vẫn hoạt động.
- S3 giữ các biến AWS/bucket hiện có, xem S3_MEDIA.md. Avatar chỉ cập nhật sau upload thành công; ảnh cũ vẫn giữ khi upload lỗi. File đã upload nhưng giao dịch DB lỗi có thể còn trên S3 và cần quản lý qua quy trình media hiện có.
- Theo dõi `delivery_email_outbox.state`, `attempts`, `last_error`; FAILED cho lỗi quá số retry, EXPIRED cho thông báo không còn phù hợp. Không ghi mật khẩu SMTP hoặc nội dung nhạy cảm vào log.

## Kiểm chứng

`mvn '-Dmaven.test.skip=false' test` chạy cả test cũ và test staff/delivery mới. Test bao gồm hiệu lực ca, idempotent check-in/out, chủ ca, endpoint mơ hồ, ghi đè đích giả mạo, kiểm tra lại ca lúc submit, profile/avatar, ngưỡng 5 cm, cảnh báo đúng người, retry email và email hết hạn.

Browser smoke test dùng API giả lập trên localhost để kiểm tra form, check-in/out, sửa profile, chọn B và payload receiverShiftId, hiển thị thông báo và mở job. Đây không phải kiểm thử E2E với database/robot thật.

Chưa chạy migration trên DB triển khai, upload S3 thật, gửi email SMTP thật hoặc chạy robot thật. Kiểm thử chấp nhận khi triển khai: đăng nhập A/B trên hai thiết bị, B check-in, A tạo và gửi job, B nhận CREATED trong trang và email; theo dõi APPROACHING nếu có mẫu <=5 cm, ARRIVED khi MOVE kết thúc; mở job và hoàn tất QR/giao hàng. Thử lại khi B checkout/hết ca trước submit và khi SMTP tạm thời không gửi được.
