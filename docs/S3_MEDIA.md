# Amazon S3 media setup

Bucket: `mediassistrover-media-2026`, region: `ap-southeast-1`.

Giữ Object Ownership ở `Bucket owner enforced` và không dùng ACL. Tắt Block Public Access cho bucket chỉ sau khi đã xem kỹ policy. Bucket policy dưới đây chỉ cho phép đọc bốn prefix public; `documents/` vẫn private.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "PublicReadMedia",
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": [
      "arn:aws:s3:::mediassistrover-media-2026/images/*",
      "arn:aws:s3:::mediassistrover-media-2026/videos/*",
      "arn:aws:s3:::mediassistrover-media-2026/maps/*",
      "arn:aws:s3:::mediassistrover-media-2026/audio/*"
    ]
  }]
}
```

IAM principal của backend cần policy tối thiểu:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::mediassistrover-media-2026/*"
  }]
}
```

## Swagger smoke test

1. `POST /api/v1/auth/login`, copy access token và bấm **Authorize**.
2. `POST /api/v1/media`, chọn JPG/PNG/WebP hoặc MP4/WebM/MOV trong field `file`.
3. Copy `objectKey`, gọi `GET /api/v1/media/url` và mở `url` trong cửa sổ ẩn danh.
4. Với ảnh staff, truyền `url` trả về vào create/update staff rồi kiểm tra `GET /api/v1/staff/me`.
5. Gỡ URL khỏi dữ liệu nghiệp vụ trước khi ADMIN gọi `DELETE /api/v1/media`; object đang được tham chiếu trả `409`.

Railway dùng cùng bốn biến AWS trong `deploy/railway-variables.example`. Không đặt credentials vào Docker image hoặc repository.
