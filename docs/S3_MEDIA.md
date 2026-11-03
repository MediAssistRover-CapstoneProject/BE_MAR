# Amazon S3 media setup

Bucket: `mediassistrover-media-2026`, region: `ap-southeast-1`.

Backend uses AWS SDK Java v2 with `DefaultCredentialsProvider`. Configure `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `AWS_REGION` and `AWS_S3_BUCKET`; never commit real credentials.

Keep Object Ownership as `Bucket owner enforced`. Do not use object ACLs. The bucket policy below only
publishes the selected prefixes; `documents/` remains private.

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

Backend IAM policy:

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

1. Login, copy an ADMIN/STAFF access token and click **Authorize**.
2. Call `POST /api/v1/media` with multipart field `file`.
3. Copy `objectKey` into `GET /api/v1/media/url` and open the returned URL.
4. Put an uploaded image URL into staff create/update and verify `GET /api/v1/staff/me`.
5. Remove business references before ADMIN calls `DELETE /api/v1/media`; referenced objects return `409`.

The frontend media library can call `GET /api/v1/media?size=20`. Filter with
`mediaType=IMAGE` or `mediaType=VIDEO`; pass the opaque `nextCursor` value to the next request.
The response contains fixed public URLs plus media type and content type, so the frontend can
choose an `<img>` or `<video controls>` element without guessing from the URL.

Images are limited to 10 MB, videos to 100 MB and Pi WAV uploads to 5 MB.
