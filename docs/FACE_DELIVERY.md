# Giao hàng QR hoặc khuôn mặt

## Hành vi

Staff gửi chọn `QR_CODE` hoặc `FACE_VERIFICATION` lúc tạo đơn. Mặc định và đơn cũ là QR; phương thức được lưu trên DeliveryRequest và `authentication_method` trong input job. Backend chọn workflow published có phương thức AUTHENTICATE phù hợp. Không tự đổi phương thức khi lỗi.

Mỗi chặng vẫn giữ: MOVE → AUTH → UNLOCK → xác nhận hàng → xác nhận đóng nắp → LOCK → xác nhận đã khóa. Người gửi ở chặng SENDER, người nhận ở chặng RECEIVER. Face so 1–1 với avatar do server chọn. AI không có quyền mở servo.

## Bật trên môi trường thử nghiệm

1. Triển khai code BE và migration Flyway **V61** sau V60. Migration thêm phương thức đơn, mẫu/avatar revision, phiên face và mở rộng CHECK của AuthVerification. Avatar cũ ở trạng thái PENDING, không tự duyệt.
2. Chạy `AI_FACE` theo README của service; kiểm tra `/health/ready` trước khi bật.
3. Đặt biến môi trường BE: `FACE_ENABLED=true`, `FACE_BASE_URL=http://127.0.0.1:8095` (hoặc URL riêng của máy AI), `FACE_SERVICE_KEY` trùng service. Mặc định FACE_ENABLED=false. Không đặt khóa vào frontend/Pi.
4. Cập nhật Pi, bật `staff_face_enabled: true` ở cấu hình worker, giữ `unlock_policy: STAFF_VERIFIED`, camera enabled, ROS image topic đúng và servo đã commissioning. Restart worker để HELLO công bố `staff-face-verify-v1` và `secure-unlock-v1` sau probe phần cứng.
5. Admin gọi `POST /api/v1/admin/workflows/staff-face-delivery-draft`, sau đó validate/publish draft theo luồng workflow hiện hành. Giữ workflow QR published. Không sửa các workflow đang chạy.
6. Trong quản lý staff, mở hồ sơ và mục **Xác thực khuôn mặt**, xem ảnh, bấm **Xác nhận đúng người — Duyệt ảnh**. Nếu thay avatar, lưu và mở lại hồ sơ trước khi duyệt. Duyệt cả người gửi/người nhận; ảnh phải đúng một mặt rõ và tối đa 2 MiB cho enrollment.
7. Staff tạo đơn tại `/delivery-qr.html` hoặc form giao hàng FE, chọn **Khuôn mặt**, chọn người nhận đang trực và gửi robot. Camera chỉ xử lý khi robot đứng yên và bước face đang hoạt động.

Pi gửi tối đa một request mỗi giây, một request tại một thời điểm; frame phải mới trong phiên. BE gọi AI timeout 5 giây, Pi chờ HTTP 6 giây; auth window không vượt 120 giây và deadline bước/workflow. Hết hạn hoặc lỗi giữ khóa; nhân viên xử lý trạng thái thất bại theo quy trình hiện có.

## Hợp đồng API

- Tạo đơn `/api/v1/delivery-requests`: thêm `authenticationMethod`; bỏ qua trường này dùng QR. Response trả lại phương thức. Idempotency key dùng lại với phương thức khác bị từ chối.
- `GET /api/v1/staff/{accountId}/face-status`: STAFF/ADMIN; trả accountId, avatarVersion, status, avatarUrl và enabled. Không trả template.
- `POST /api/v1/admin/staff/{accountId}/face-review`: ADMIN; JSON `{ "avatarVersion": "UUID đang xem", "approve": true }`. **accountId là ID tài khoản, không phải staffProfileId**. `approve:false` từ chối/vô hiệu hóa mẫu. Kết quả AI đến sau khi avatar thay đổi không được ghi lại.
- `GET /api/v1/pi/jobs/{jobId}/face-auth`: Device JWT của robot được giao; trả phiên hiện hành hoặc 204. Cửa sổ còn được gửi qua MQTT SERVER_HEARTBEAT trong `staffFaceScan`.
- `POST /api/v1/pi/jobs/{jobId}/face-auth/verify`: JSON gồm `sessionId`, `authJobStepId`, `transportSessionId`, `image` (JPEG base64). Server không nhận `matched`, staff đích hoặc template từ Pi. Kết quả `{verified,reason}`; không trả mẫu hay điểm AI.

Mẫu được lưu riêng trong StaffProfile cùng model version; DTO chỉ trả trạng thái/revision. Mọi lần đổi/xóa URL avatar qua staff hoặc admin đều xóa mẫu, thay revision. Quyền đã xác thực nhưng chưa mở ngăn cũng bị chặn khi revision thay đổi. Session đã sử dụng/hết hạn không được gia hạn bằng retry. Reconnect Pi làm transport session cũ không còn hợp lệ; tạo lại nhiệm vụ theo quy trình sau thất bại.

## Quan sát và tắt

BE ghi `FACE_COMPARE` (chỉ kết quả so sánh, chưa phải quyền mở) và `FACE_VERIFY ... AI_UNAVAILABLE`, với job/step/elapsedMs; sự kiện hoàn tất `FACE_VERIFICATION_SUCCEEDED` được lưu cùng giao dịch cấp quyền. Pi ghi `STAFF_FACE_VERIFIED` hoặc mã retry. Không ghi ảnh, template hoặc khóa service vào log.

Tắt `FACE_ENABLED` chặn đơn/phiên face và quyền face chưa dùng; đơn face không chuyển QR. QR tiếp tục hoạt động. Nếu ngăn đã mở, thực hiện bàn giao/đóng khóa hoặc phục hồi theo quy trình hiện hành, không tự chạy một đơn khác.

## Kiểm chứng và phần còn cần nghiệm thu

- Backend: `mvn '-Dmaven.test.skip=false' test`; test bao gồm sender/receiver, người sai, phiên dùng lại/hết hạn, sai robot/transport/bước, hủy job, AI lỗi, đổi avatar khi đang so sánh và kết quả duyệt đến muộn.
- AI: `python -m pytest -q` trong AI_FACE với model/fixtures đã tải. Model thật đã chạy smoke test; cần thêm tập ảnh được staff cho phép và camera thật.
- Pi: `python pi/tools/test_staff_face.py`, `test_mqtt_bridge.py`, `test_camera_config.py`, `test_safety_shutdown.py` từ PI_MAR; không điều khiển robot.
- FE: `npm run build`; kiểm tra lựa chọn phương thức, trạng thái mẫu và duyệt đúng revision.

**Chưa chạy V61 trên MySQL triển khai, upload S3 thật, publish workflow lên hệ thống thật hoặc thử servo/camera robot.** Nghiệm thu còn lại: một đơn QR và một đơn face với hai staff; sai người ở cả hai chặng; thay avatar giữa phiên; mất mạng; kiểm tra khóa trước khi di chuyển; ghi false accept/false reject và độ trễ. Chưa có chống ảnh/video giả, chỉ sử dụng bản demo có giám sát.

Kết quả kiểm tra cục bộ ngày 18/11/2026: backend 162 test, 0 lỗi, 1 skipped; AI_FACE 11 test qua (có model thật); Pi 30 test qua ở bốn bộ test nêu trên; Vite production build qua. Không thay thế nghiệm thu phần cứng/MySQL thật.
