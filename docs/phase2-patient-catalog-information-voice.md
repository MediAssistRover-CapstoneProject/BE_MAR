# Phase 2 — Patient catalog, information và voice

> Trạng thái: **IMPLEMENTED, VERIFICATION PENDING** (2026-10-15).

## Phạm vi đã triển khai

- Giữ nguyên các luồng Device JWT hiện hữu cho information request và voice lifecycle; compile và unit regression liên quan vẫn đạt.
- Tách toàn bộ anonymous catalog sang `PublicFacilityResponses`, không tái sử dụng DTO quản trị.
- Bổ sung detail API cho hospital information, floor, zone, room và endpoint.
- Bổ sung list/detail/member API cho endpoint group.
- Bổ sung list/detail API cho public hospital article.
- Bổ sung đầy đủ filter đã khóa trong kế hoạch: text, category, language, hospital, map, floor, room, type, department và accessibility.
- Áp dụng `PUBLISHED`, public visibility và khoảng hiệu lực `[effectiveFrom,effectiveUntil)` cho hospital/article; resource không public trả `404` ở detail API.
- Dùng sort ổn định theo display/code/title cho pagination.

## Privacy boundary

Anonymous response không chứa account cập nhật, trạng thái quản trị, row version, timestamps audit, map asset, tọa độ/yaw endpoint, mô tả nội bộ endpoint, polygon/camera/ROI zone, hay selection/fallback policy của endpoint group.

## Kiểm thử và bằng chứng

- `mvn -q -DskipTests compile`: **PASS**.
- `mvn -q -DskipTests package`: **PASS**.
- `DataServiceStepHandlerTest` và `Phase1ScopePolicyTest`: **3/3 PASS**.
- `FacilityIntegrationTest` đã mở rộng để kiểm filter, detail, article, group/member, `404` cho dữ liệu private và field không được phép lộ.
- Lần chạy integration hiện tại dừng trước test body vì không tìm thấy Docker environment cho Testcontainers; kết quả này không phải lỗi assertion hay lỗi ứng dụng. Vì vậy Phase 2 chưa được đánh dấu `COMPLETE`.

## Gate còn lại

Khởi động Docker (MySQL 8.4 Testcontainers) hoặc cấu hình `TEST_DB_URL` trỏ tới database kiểm thử riêng, sau đó chạy `FacilityIntegrationTest` và full Maven regression. Chỉ đổi trạng thái sang `COMPLETE` khi hai gate này xanh.
