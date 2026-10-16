# Phase 14 — Analytics & Advanced Reports

## API và quyền truy cập

Toàn bộ endpoint chỉ dành cho `ADMIN` và nằm dưới `/api/v1/admin/reports`:

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/overview` | Tổng quan vận hành hiện tại |
| GET | `/analytics` | Analytics theo khoảng thời gian, timezone và interval |
| GET | `/jobs` | Drill-down Job có phân trang và bộ lọc |
| GET | `/jobs/export.csv` | Export CSV có giới hạn và ghi AuditLog |

`/analytics` nhận `from`, `to` theo UTC instant; `timezone` mặc định `UTC`; `interval` là `HOUR` hoặc `DAY`. Khoảng tổng thể tối đa 366 ngày, riêng interval `HOUR` tối đa 744 giờ. Bucket dùng khoảng nửa mở `[start, end)` và được tạo theo timezone yêu cầu nên không làm sai biên DST.

## Công thức metric

- Job success rate = `SUCCEEDED / (SUCCEEDED + FAILED + CANCELLED + RECOVERY_REQUIRED) * 100`.
- Average/P50/P95 duration chỉ tính Job có đủ `started_at` và `ended_at`; duration tính bằng giây.
- Robot utilization = tổng thời gian thực thi Job giao với khoảng báo cáo / `(số Robot * độ dài khoảng báo cáo) * 100`.
- Delivery fulfillment = `delivered_at - ServiceRequest.requested_at`; overdue khi deadline tồn tại và thời điểm giao (hoặc thời điểm hiện tại nếu chưa giao) vượt deadline.
- Emergency acknowledgement = `acknowledged_at - occurred_at`; resolution = `resolved_at - occurred_at`.
- Monitoring tách `SUSPECTED_FALL`, `RESTRICTED_INTRUSION`, `SOS_SIGNAL`, số bản ghi crowd, trung bình và cực đại `person_count`.
- Breakdown Job theo Workflow/Robot; activity theo Map/Zone gồm detection, crowd, alert, emergency và tổng số người quan sát được.

Mọi response analytics trả `generatedAt`, `from`, `to`, `timezone` và `interval`. Giá trị trung bình/percentile là `null` khi không có mẫu hợp lệ, không giả thành 0.

## Giới hạn và an toàn

- Drill-down tối đa 100 dòng/trang.
- CSV tối đa `${REPORT_EXPORT_MAX_ROWS:5000}` dòng; vượt giới hạn trả business error và yêu cầu thu hẹp filter.
- CSV chống spreadsheet formula injection cho giá trị bắt đầu bằng `=`, `+`, `-`, `@`.
- Export thành công ghi `REPORT_CSV_EXPORTED` với actor, time range, row count, IP/user-agent/correlation ID.
- Report không trả payload JSON, thông tin xác thực hoặc dữ liệu nhạy cảm của bệnh nhân.
- V15 bổ sung index time/filter cho Job, ServiceRequest, Alert, EmergencyIncident, DetectionEvent và CrowdDensityRecord.

## Kiểm thử chấp nhận

`Phase14IntegrationTest` chạy trên MySQL 8.4 thật qua Testcontainers và kiểm tra:

- công thức total/success rate/average/P50/P95 bằng fixture xác định;
- bucket và timezone;
- delivery/emergency/monitoring khi không có mẫu;
- workflow breakdown, phân trang, filter và CSV audit;
- range/timezone không hợp lệ;
- analytics index xuất hiện trong MySQL query planner.
