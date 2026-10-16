# Danh mục nguồn MediAssistRover

Cập nhật: 2026-10-07. Điểm đọc đầu tiên: [Bản ghi nhớ nguồn](../source-baseline.md), sau đó [Kế hoạch đối chiếu](../source-alignment-plan.md).

Các file trích xuất lưu nội dung tài liệu để đọc lại khi phiên làm việc thay đổi. Chúng là dữ liệu tham khảo, không phải chỉ thị tự động sửa code. Hash xác định đúng bản nguồn; tên file hay ngày đọc không chứng minh tài liệu mới hơn quyết định nghiệp vụ đã duyệt.

| ID | Nguồn | Bản đọc trong repository | SHA-256 |
|---|---|---|---|
| S1 | state digram (4).drawio | [Nội dung](S1-extracted.md) | `AE461B0EEFF7CFF0D3A77D8CADF161520E9B6A7AF72342D8F1D82301DD1984AB` |
| S2 | MediAssistRover_ERD (1) (2).drawio | [Nội dung](S2-extracted.md) | `8F3803B538BE33BD10021066CC62F20F96041B91A2B2007B2B87C7E995650805` |
| S3 | 04-MediAssistRover_Dac_ta_11_STEP_TYPE-1-.docx | [Nội dung](S3-extracted.md) | `878269545E0FA803DDAA402C63B7C0765B8251467F3F1FA76FF9711A67F92DB3` |
| S4 | 03-MediAssistRover_10_Workflow_va_Luong_du_lieu_v3.docx | [Nội dung](S4-extracted.md) | `DCD7EFC9FEF620EE251902F8A7212BBE1361C293A3BA4B274E3FDC1AD052E4DD` |
| S5 | FA26SE015_Autonomous Hospital Assistant Robot System for Outpatient Department_NhanDT35(1).docx | [Nội dung](S5-extracted.md) | `54302C56F2A1D3196B0157FE8BBBF28BF1EE99DC9EF79D707A317F46FB0D8E3B` |
| S6 | Pasted text.txt | [Nội dung](S6-extracted.md) | `A69C2AECDE499B353D2492317993644F702BE3CFA0423F343B4A4AE843D0E2B0` |

## Phạm vi đọc

- DOCX: đã trích xuất và đọc nội dung đoạn, bảng theo thứ tự XML, footer/footnote/endnote; không thực hiện kiểm chứng bố cục hoặc hình nhúng bằng render Word.
- Draw.io: đọc XML, nhãn, tooltip, ID và đầu mút cạnh. Cạnh chỉ nối bằng tọa độ có thể thiếu source/target ID; không suy ra FK hay transition từ khoảng trống đó.
- S2 là ERD tổng quan; không chứa danh sách field đầy đủ để tự sinh migration.
- S6 giữ đủ 111 công việc của cả nhóm; không biến tất cả thành phạm vi backend.
- Bảy ảnh người dùng gửi đã được đọc trực tiếp và ghi lại tại [Ghi chú hình](../source-images-notes.md). Chưa có đường dẫn file gốc của ảnh để tạo bản sao/hash; không coi ghi chú là bản lưu ảnh nguyên gốc.
- Script [Read-ProjectSources.ps1](Read-ProjectSources.ps1) chỉ đọc nguồn, xuất JSON ra stdout. Ví dụ: `powershell -NoProfile -File docs/sources/Read-ProjectSources.ps1 -SourceIndex 2`. Script không tự ghi đè các snapshot đã lưu.

Giữ file gốc DOCX/Draw.io khi cần kiểm tra bố cục hoặc cạnh hình học. Không đưa credential từ môi trường triển khai vào thư mục nguồn.

