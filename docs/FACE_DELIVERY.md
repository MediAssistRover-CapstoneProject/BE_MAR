# Giao hàng QR hoặc khuôn mặt

## Trạng thái triển khai ngày 19/11/2026

BE production `9e0d273` đã bật `FACE_ENABLED=true` trong `/etc/mediassistrover/face.env`, restart thành công và health UP. BE gọi AI_FACE qua private `172.31.35.139:8095`, model READY. Workflow `4ce15b1f-8557-43e4-932c-e90d635e3926` (`WF_STAFF_FACE_DELIVERY_8CB60746D2B7`) đã VALIDATED/PUBLISHED, không có cảnh báo; AUTH_SENDER là QR và AUTH_RECEIVER là FACE. Không tạo job hoặc mở servo khi cấu hình.

Pi `192.168.101.162` đã bật scanner face, giữ STAFF_VERIFIED, đồng bộ `step_store.py` và `step_worker.py` theo `774f306` sau 39 kiểm tra RAM không lỗi. Journal đã backup; không đưa file test lên Pi. Stack MAR đang dừng; cấu hình gốc vẫn mapping, chưa có navigation_session. Mở navigation mới rồi chạy lệnh BE sinh và localize trước khi tạo job.

`staff02` có ảnh PENDING, chưa được admin duyệt; `staff2` chưa có ảnh. Chỉ staff nhận hàng quan trọng cần mẫu READY. Việc duyệt ảnh phải do admin đối chiếu đúng người; không tự duyệt hoặc enroll trong lượt kiểm tra này. Khung khuôn mặt chưa được nối vào WebRTC và liveness vẫn false.

## Hành vi

Staff gửi đánh dấu **Hàng quan trọng** khi tạo đơn (`items[].sensitive=true`). Chỉ cần một món quan trọng thì BE yêu cầu `FACE_VERIFICATION` ở chặng người nhận; người giao luôn dùng `QR_CODE`. Tất cả món thông thường dùng QR ở cả hai chặng. Ghi chú `deliveryNote` chỉ mô tả hàng, không được dò từ khóa để quyết định mở khóa. Nếu bỏ `authenticationMethod`, BE suy ra từ danh sách món; gửi phương thức trái chính sách bị từ chối. Phương thức của người nhận được lưu trên DeliveryRequest và `authentication_method` trong input job. Backend chọn workflow published có phương thức AUTHENTICATE phù hợp. Không tự đổi sang QR khi face lỗi hoặc chưa có mẫu staff được duyệt.

Chính sách được kiểm tra khi tạo, submit và lúc cấp/dispatch quyền UNLOCK. Grant phải có đúng phương thức và đúng tài khoản staff của chặng giao/nhận. Các draft cũ có phương thức trái cờ hàng cần tạo lại đúng chính sách; không tự đổi workflow hoặc hạ cấp xác thực của job đang chạy.

Mỗi chặng vẫn giữ: MOVE → AUTH → UNLOCK → xác nhận hàng → xác nhận đóng nắp → LOCK → xác nhận đã khóa. Người gửi ở chặng SENDER, người nhận ở chặng RECEIVER. Face so 1–1 với avatar do server chọn. AI không có quyền mở servo.

## Bật trên môi trường thử nghiệm

1. Triển khai code BE và migration Flyway **V62** sau V61. Migration thêm phương thức đơn, mẫu/avatar revision, phiên face và mở rộng CHECK của AuthVerification. Avatar cũ ở trạng thái PENDING, không tự duyệt.
2. Chạy `AI_FACE` theo README của service; kiểm tra `/health/ready` trước khi bật.
3. Đặt biến môi trường BE: `FACE_ENABLED=true`, `FACE_BASE_URL=http://127.0.0.1:8095` (hoặc URL riêng của máy AI), `FACE_SERVICE_KEY` trùng service. Mặc định FACE_ENABLED=false. Không đặt khóa vào frontend/Pi.
4. Cập nhật Pi, bật `staff_face_enabled: true` ở cấu hình worker, giữ `unlock_policy: STAFF_VERIFIED`, camera enabled, ROS image topic đúng và servo đã commissioning. Restart worker để HELLO công bố `staff-face-verify-v1` và `secure-unlock-v1` sau probe phần cứng.
5. Admin gọi `POST /api/v1/admin/workflows/staff-face-delivery-draft`, sau đó validate/publish draft theo luồng workflow hiện hành. Giữ workflow QR published. Không sửa các workflow đang chạy.
6. Trong quản lý staff, mở hồ sơ và mục **Xác thực khuôn mặt**, xem ảnh, bấm **Xác nhận đúng người — Duyệt ảnh**. Nếu thay avatar, lưu và mở lại hồ sơ trước khi duyệt. Duyệt staff nhận hàng; ảnh phải đúng một mặt rõ và tối đa 2 MiB cho enrollment.
7. Staff tạo đơn tại `/delivery-qr.html`, đánh dấu **Hàng quan trọng**, chọn người nhận đang trực và gửi robot. Form gửi `sensitive:true`; BE bắt buộc face. Người gửi luôn dùng QR; bỏ đánh dấu thì người nhận cũng dùng QR. Camera chỉ xử lý khi robot đứng yên và bước face đang hoạt động.

Pi gửi tối đa một request mỗi giây, một request tại một thời điểm; frame phải mới trong phiên. BE gọi AI timeout 5 giây, Pi chờ HTTP 6 giây; auth window không vượt 120 giây và deadline bước/workflow. Hết hạn hoặc lỗi giữ khóa; nhân viên xử lý trạng thái thất bại theo quy trình hiện có.

## Hợp đồng API

- Tạo đơn `/api/v1/delivery-requests`: `items[].sensitive` quyết định phương thức. `authenticationMethod` tùy chọn phải khớp `FACE_VERIFICATION` nếu có món quan trọng, hoặc `QR_CODE` nếu không. Response trả lại phương thức. Idempotency key dùng lại với phương thức khác bị từ chối.
- `GET /api/v1/staff/{accountId}/face-status`: STAFF/ADMIN; trả accountId, avatarVersion, status, avatarUrl và enabled. Không trả template.
- `POST /api/v1/admin/staff/{accountId}/face-review`: ADMIN; JSON `{ "avatarVersion": "UUID đang xem", "approve": true }`. **accountId là ID tài khoản, không phải staffProfileId**. `approve:false` từ chối/vô hiệu hóa mẫu. Kết quả AI đến sau khi avatar thay đổi không được ghi lại.
- `GET /api/v1/pi/jobs/{jobId}/face-auth`: Device JWT của robot được giao; trả phiên hiện hành hoặc 204. Cửa sổ còn được gửi qua MQTT SERVER_HEARTBEAT trong `staffFaceScan`.
- `POST /api/v1/pi/jobs/{jobId}/face-auth/verify`: JSON gồm `sessionId`, `authJobStepId`, `transportSessionId`, `image` (JPEG base64). Server không nhận `matched`, staff đích hoặc template từ Pi. Kết quả `{verified,reason}`; không trả mẫu hay điểm AI.

Mẫu được lưu riêng trong StaffProfile cùng model version; DTO chỉ trả trạng thái/revision. Mọi lần đổi/xóa URL avatar qua staff hoặc admin đều xóa mẫu, thay revision. Quyền đã xác thực nhưng chưa mở ngăn cũng bị chặn khi revision thay đổi. Session đã sử dụng/hết hạn không được gia hạn bằng retry. Nếu MQTT reconnect trong cùng bước face còn hạn, BE thay challenge chưa dùng sang transport mới, vô hiệu challenge cũ và giữ nguyên deadline ban đầu; không cấp lại quyền đã xác minh.

## Quan sát và tắt

BE ghi `FACE_COMPARE` (chỉ kết quả so sánh, chưa phải quyền mở) và `FACE_VERIFY ... AI_UNAVAILABLE`, với job/step/elapsedMs; sự kiện hoàn tất `FACE_VERIFICATION_SUCCEEDED` được lưu cùng giao dịch cấp quyền. Pi ghi `STAFF_FACE_VERIFIED` hoặc mã retry. Không ghi ảnh, template hoặc khóa service vào log.

Tắt `FACE_ENABLED` chặn đơn/phiên face và quyền face chưa dùng; đơn face không chuyển QR. QR tiếp tục hoạt động. Nếu ngăn đã mở, thực hiện bàn giao/đóng khóa hoặc phục hồi theo quy trình hiện hành, không tự chạy một đơn khác.

## Kiểm chứng và phần còn cần nghiệm thu

- Backend: `mvn '-Dmaven.test.skip=false' test`; test bao gồm sender/receiver, người sai, phiên dùng lại/hết hạn, sai robot/transport/bước, hủy job, AI lỗi, đổi avatar khi đang so sánh và kết quả duyệt đến muộn.
- AI: `python -m pytest -q` trong AI_FACE với model/fixtures đã tải. Model thật đã chạy smoke test; cần thêm tập ảnh được staff cho phép và camera thật.
- Pi: `python pi/tools/test_staff_face.py`, `test_mqtt_bridge.py`, `test_camera_config.py`, `test_safety_shutdown.py` từ PI_MAR; không điều khiển robot.
- FE: `npm run build`; kiểm tra lựa chọn phương thức, trạng thái mẫu và duyệt đúng revision.

Kiểm tra production ngày 18/11/2026 xác nhận V62 đã áp dụng và BE có các class face. Điều đó chưa xác nhận đã bật feature hay cài service AI_FACE; cần kiểm tra `/health/ready`, cấu hình key, workflow published, mẫu đã duyệt và HELLO Pi trước khi test. Nghiệm thu còn lại: một đơn QR và một đơn hàng quan trọng bằng face với hai staff; QR sai người gửi và face sai người nhận; thay avatar giữa phiên; mất mạng; kiểm tra khóa trước khi di chuyển; ghi false accept/false reject và độ trễ. Chưa có chống ảnh/video giả, chỉ sử dụng bản demo có giám sát.

Kết quả kiểm tra cục bộ ngày 18/11/2026: backend 255 test, 0 lỗi, 1 skipped (MySQL schema integration cần DB kiểm thử); AI_FACE 20 test qua với model thật. Pi cần nghiệm thu lại với bản đang cài. Không thay thế nghiệm thu phần cứng/MySQL thật.

Triển khai service AI ngày 18/11/2026: `face-ai.service` trên EC2 media, private `172.31.35.139:8095`; BE đã kiểm tra readiness và xác thực service. Cấu hình BE `/etc/mediassistrover/face.env` vẫn `FACE_ENABLED=false`, chưa restart BE hoặc publish workflow mới. Service dùng khoảng 155 MiB sau warmup/ảnh trống VGA; đây không phải benchmark xác minh staff hay tải đồng thời. Bản sửa chính sách QR người gửi/face người nhận hiện còn ở checkout chờ push CI.

Cập nhật source và Pi ngày 18/11/2026: upload avatar tối đa 2 MiB; upload lưu S3 và chuyển PENDING, không tự tạo mẫu. Admin mở Quản lý nhân viên → Xem ảnh và duyệt mẫu, đối chiếu đúng người rồi duyệt bằng avatarVersion. Đổi ảnh vô hiệu mẫu; sửa hồ sơ không gửi trường url giữ ảnh/mẫu. Avatar cũ quá lớn, ảnh không có mặt/nhiều mặt/mờ, model đổi và AI bận có thông báo riêng. BE 260 kiểm tra qua (1 integration skipped).

Pi `192.168.1.134` đã cài scanner face và bật `staff_face_enabled=true`; giữ STAFF_VERIFIED. Giám sát té ngã liên tục kèm depth được bật cho navigation, nhường QR/face trong lúc bàn giao; bản gốc hiện vẫn mapping, mở navigation mới để có runtime face. Không cài model/test lên Pi và không phát lệnh bánh/servo khi đồng bộ. BE/FE source mới chưa push/deploy, FACE_ENABLED trên BE vẫn false.
