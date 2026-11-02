# Map lưu hoàn toàn trong DB Aiven

Khi BE chạy với `SPRING_PROFILES_ACTIVE=prod,aiven`, `app.mapping.storage-backend=DATABASE`. Flyway V39 tạo bảng `MapBinaryAsset` chứa gói TAR.GZ hoặc file YAML/PGM, SHA-256 và số byte. Metadata Map/endpoint/zone vẫn dùng các bảng hiện tại. Không đổi UUID, kích thước, resolution, origin hay hash map khi chuyển storage.

Luồng bấm **Lưu bản đồ và kết thúc** giữ nguyên: Pi dừng điều khiển, tạo map.yaml/map.pgm/manifest, upload HTTPS; BE kiểm tra archive/hash/scope và ghi blob cùng biên nhận MapArtifact trong cùng transaction. Sau khi transaction commit mới trả receipt thành công. Pi gửi kết quả SAVE, BE chạy COMMIT_MAP_ARTIFACT và STOP như trước. SQL lỗi thì upload không được xác nhận, không tạo một biên nhận thiếu bytes.

Trong DATABASE mode, tải bundle/ảnh/định vị đọc dữ liệu DB; không fallback sang đĩa. Ảnh PNG sinh từ PGM nguyên gốc đã lưu trong DB, không cần lưu ảnh hiển thị riêng hoặc sửa pixel. BE có cache RAM giới hạn 32 MiB cho dữ liệu bất biến; cache mất khi restart nhưng map vẫn còn trên Aiven. Dữ liệu chưa commit/đã rollback không được đưa vào cache.

Chuyển **một map cũ** trên máy còn giữ file gốc, bằng token ADMIN:

```http
POST /api/v1/maps/e571fe71-d3ea-4aa5-a673-ffd857ac9f09/storage/import-local
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

Không cần body. Endpoint chỉ đọc nguồn nội bộ gắn với Map ID, kiểm tra hash/geometry, copy vào DB và giữ nguyên catalog/endpoint/zone. Gọi lại an toàn; không ghi đè dữ liệu khác hash. Không tự chuyển các map khác, không xóa bản sao cũ. Map cũ chưa chuyển sẽ báo thiếu cloud asset khi dùng DATABASE mode.

Kiểm tra từ BE mới chỉ kết nối Aiven, **không cần volume map-artifacts**:

```http
GET /api/v1/maps/e571fe71-d3ea-4aa5-a673-ffd857ac9f09/image.png
GET /api/v1/pi/maps/e571fe71-d3ea-4aa5-a673-ffd857ac9f09/bundle
```

Dùng token ADMIN; Pi dùng DEVICE token và map phải thuộc phiên/quyền robot. BE mới cần cùng phiên bản code/migration, cấu hình Aiven và truststore CA. BE chỉ xem map đặt MQTT/scheduler/dispatch=false; chỉ một BE điều phối robot tại một thời điểm. Pi vẫn chỉ gọi HTTPS/MQTT của BE, không có mật khẩu MySQL.

Giới hạn gói upload vẫn 16 MiB; các phần map/manifest vẫn chịu giới hạn giải nén và kiểm tra đường dẫn. BLOB nằm bảng riêng nên danh sách map, heartbeat và telemetry không tải bytes map. Backup MySQL đầy đủ phải gồm `MapBinaryAsset` (mysqldump `--hex-blob`). Map thô/live preview trong lúc quét chưa bấm Lưu vẫn là dữ liệu runtime, không phải map đã lưu vào cloud.

Tham khảo: [Aiven MySQL max_allowed_packet](https://aiven.io/docs/products/mysql/reference/advanced-params) và [Connector/J binary parameters](https://docs.oracle.com/cd/E17952_01/connector-j-en/connector-j-reference-implementation-notes.html). Giới hạn packet của dịch vụ phải đủ cho gói upload; không tự tăng thông số DB trong bản sửa này.

Đã áp dụng ngày 02/11/2026: chỉ backfill map `e571fe71-d3ea-4aa5-a673-ffd857ac9f09`. Aiven chạy migration 39; archive 2.050 byte có SHA-256 khớp. YAML, PGM, metadata và PNG trước/sau giống nhau. Một BE tạm với root filesystem chỉ đọc, không có volume map, MQTT/dispatch tắt, vẫn tải được ảnh và bundle HTTP 200 từ Aiven; container kiểm tra đã dừng/xóa. Không đổi cấu hình Pi, ID map, endpoint hoặc zone. File cũ được giữ làm bản sao dự phòng, không dùng làm nguồn đọc hoặc ghi trong DATABASE mode.
