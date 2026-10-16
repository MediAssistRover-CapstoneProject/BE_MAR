# S2 — MediAssistRover_ERD (1) (2).drawio

Nguồn: `C:\Users\Admin\Downloads\MediAssistRover_ERD (1) (2).drawio`
SHA-256: `8F3803B538BE33BD10021066CC62F20F96041B91A2B2007B2B87C7E995650805`
Ngày đọc: 2026-10-07. Trích xuất văn bản; không phải bản render. Draw.io giữ ID, tooltip/cardinality và đầu mút cạnh; DOCX giữ thứ tự đoạn/ô, không bảo toàn bố cục hay hình nhúng.

[1] DIAGRAM: ERD - A4 ngang
[2] OBJECT: label=; tooltip=Account [1] — [0..1] StaffProfile. ; id=rel-01
[3] OBJECT: label=; tooltip=Account [0..1] — [0..n] AuditLog. Account là người thao tác; log hệ thống có thể không có account.; id=rel-02
[4] OBJECT: label=; tooltip=Account [0..1] — [0..n] VoiceInteraction. ; id=rel-03
[5] OBJECT: label=; tooltip=VoiceInteraction [1] — [0..n] VoiceCommand. ; id=rel-04
[6] OBJECT: label=; tooltip=Robot [1] — [0..n] VoiceInteraction. ; id=rel-05
[7] OBJECT: label=; tooltip=Robot [1] — [0..n] RobotStatusHistory. ; id=rel-06
[8] OBJECT: label=; tooltip=Robot [1] — [0..n] RobotLocationHistory. ; id=rel-07
[9] OBJECT: label=; tooltip=Robot [1] — [0..n] CrowdDensityRecord. ; id=rel-08
[10] OBJECT: label=; tooltip=Robot [0..1] — [0..n] Job. Một Robot có nhiều Job theo thời gian; Job có thể chưa được gán Robot.; id=rel-11
[11] OBJECT: label=; tooltip=Zone [1] — [0..n] CrowdDensityRecord. ; id=rel-12
[12] OBJECT: label=; tooltip=EmergencyIncident [0..1] — [0..n] Alert. ; id=rel-13
[13] OBJECT: label=; tooltip=DetectionEvent [0..1] — [0..n] EmergencyIncident. UNIQUE(detection_event_id, incident_type); incident từ SOS không bắt buộc có detection.; id=rel-14
[14] OBJECT: label=; tooltip=Endpoint [0..1] — [0..n] EmergencyIncident. Endpoint gần sự cố là tùy chọn; sự cố có thể xảy ra ngoài endpoint.; id=rel-15
[15] OBJECT: label=; tooltip=HospitalInformation [1] — [0..n] Map. ; id=rel-16
[16] OBJECT: label=; tooltip=Map [1] — [0..n] Zone. ; id=rel-18
[17] OBJECT: label=; tooltip=Map [1] — [0..n] HeatmapRecord. ; id=rel-19
[18] OBJECT: label=; tooltip=Map [1] — [0..n] Endpoint. ; id=rel-21
[19] OBJECT: label=; tooltip=Zone [0..1] — [0..n] Endpoint. ; id=rel-22
[20] OBJECT: label=; tooltip=EndpointGroup [1] — [0..n] EndpointGroupMember. ; id=rel-23
[21] OBJECT: label=; tooltip=Endpoint [1] — [0..n] EndpointGroupMember. ; id=rel-24
[22] OBJECT: label=; tooltip=Room [0..1] — [0..n] Endpoint. Mỗi Room có 0..n Endpoint; Endpoint có thể không thuộc Room.; id=rel-25
[23] OBJECT: label=; tooltip=EndpointGroup [0..1] — [0..n] Room. Giữ quy tắc: một Room thuộc tối đa một nhóm chức năng. Không thay thế EndpointGroupMember.; id=rel-26
[24] OBJECT: label=; tooltip=StaffProfile [1] — [0..n] Shift. ; id=rel-27
[25] OBJECT: label=; tooltip=Shift [1] — [0..n] Slot. ; id=rel-29
[26] OBJECT: label=; tooltip=Room [1] — [0..n] EscortRequest. ; id=rel-34
[27] OBJECT: label=; tooltip=Room [1] — [0..n] EscortRequest. ; id=rel-35
[28] OBJECT: label=SystemSetting; tooltip=Cấu hình chung; không bắt buộc có quan hệ với bảng khác.; id=SystemSetting
[29] OBJECT: label=VoiceCommand; tooltip=Bản ghi lệnh đã nhận trong một phiên VoiceInteraction; không phải danh mục lệnh mẫu.; id=VoiceCommand
[30] OBJECT: label=Zone; tooltip=Thiết kế chọn mỗi Endpoint thuộc tối đa một Zone; Zone thuộc đúng một Map.; id=Zone
[31] OBJECT: label=Alert; tooltip=Cảnh báo có thể không gắn EmergencyIncident, ví dụ lỗi điều hướng hoặc đường bị chặn.; id=Alert
[32] OBJECT: label=EmergencyIncident; tooltip=DetectionEvent và Endpoint là tùy chọn. UNIQUE(detection_event_id, incident_type) cho nguồn detector. SOS có thể không có detection.; id=EmergencyIncident
[33] OBJECT: label=Job; tooltip=Có thể chưa gán Robot. Schedule và ServiceRequest là tùy chọn; Job luôn gắn đúng phiên bản Workflow.; id=Job
[34] OBJECT: label=JobStep; tooltip=Một lượt ghé bước có job_step_id riêng; không UNIQUE(job_id, step_id) nếu có vòng lặp.; id=JobStep
[35] OBJECT: label=Task; tooltip=Task là cấu hình thuộc một phiên bản Workflow, không phải thư viện Task dùng chung.; id=Task
[36] OBJECT: label=Workflow; tooltip=Mỗi bản ghi đại diện phiên bản workflow; bản đã công bố bất biến.; id=Workflow
[37] OBJECT: label=ServiceRequest; tooltip=Một yêu cầu có thể tạo nhiều Job theo thời gian. request_type quyết định loại bảng con. Delivery/Navigation/Escort loại trừ nhau; loại khác có thể không có ba bảng con này.; id=ServiceRequest
[38] OBJECT: label=Floor; tooltip=Giả định đây là hồ sơ bệnh viện; không phải bảng bài viết/FAQ.; id=HospitalInformation
[39] OBJECT: label=Shift; tooltip=Giả định mỗi Shift là một lần phân công một nhân viên tại một phòng; không phải ca chung nhiều nhân viên.; id=Shift
[40] OBJECT: label=EndpointGroupMember; tooltip=Bảng trung gian n–n giữa EndpointGroup và Endpoint. UNIQUE(endpoint_group_id, endpoint_id).; id=EndpointGroupMember
[41] OBJECT: label=EndpointGroup; tooltip=Nhóm điểm lựa chọn. Quan hệ Room chỉ là nhóm chức năng tùy chọn; thành viên điểm luôn do EndpointGroupMember xác định.; id=EndpointGroup
[42] OBJECT: label=Endpoint; tooltip=Thuộc một Map; thuộc tối đa một Zone và tối đa một Room. Nếu có Zone thì phải cùng Map.; id=Endpoint
[43] OBJECT: label=Room; tooltip=Một phòng có nhiều Endpoint. Quan hệ EndpointGroup giữ nghĩa một phòng thuộc tối đa một nhóm chức năng.; id=Room
[44] OBJECT: label=HospitalInformation; tooltip=Giả định đây là hồ sơ bệnh viện; không phải bảng bài viết/FAQ.; id=pPEoN-BSPd6aUG21HaPH-2
[45] id=; parent=1; source=Account; target=StaffProfile; edge=1; label=
[46] id=; parent=1; source=Account; target=; edge=1; label=
[47] id=; parent=1; source=Account; target=VoiceInteraction; edge=1; label=
[48] id=; parent=1; source=VoiceInteraction; target=VoiceCommand; edge=1; label=
[49] id=; parent=1; source=Robot; target=VoiceInteraction; edge=1; label=
[50] id=; parent=1; source=Robot; target=RobotStatusHistory; edge=1; label=
[51] id=; parent=1; source=Robot; target=RobotLocationHistory; edge=1; label=
[52] id=; parent=1; source=Robot; target=CrowdDensityRecord; edge=1; label=
[53] id=; parent=1; source=Robot; target=Job; edge=1; label=
[54] id=; parent=1; source=Zone; target=CrowdDensityRecord; edge=1; label=
[55] id=; parent=1; source=; target=Alert; edge=1; label=
[56] id=; parent=1; source=DetectionEvent; target=; edge=1; label=
[57] id=; parent=1; source=Endpoint; target=EmergencyIncident; edge=1; label=
[58] id=; parent=1; source=HospitalInformation; target=Map; edge=1; label=
[59] id=; parent=1; source=Map; target=Zone; edge=1; label=
[60] id=; parent=1; source=Map; target=HeatmapRecord; edge=1; label=
[61] id=; parent=1; source=Map; target=Endpoint; edge=1; label=
[62] id=; parent=1; source=; target=; edge=1; label=
[63] id=; parent=1; source=EndpointGroup; target=EndpointGroupMember; edge=1; label=
[64] id=; parent=1; source=Endpoint; target=EndpointGroupMember; edge=1; label=
[65] id=; parent=1; source=Room; target=Endpoint; edge=1; label=
[66] id=; parent=1; source=EndpointGroup; target=Room; edge=1; label=
[67] id=; parent=1; source=; target=; edge=1; label=
[68] id=; parent=1; source=Shift; target=Slot; edge=1; label=
[69] id=; parent=1; source=Room; target=EscortRequest; edge=1; label=
[70] id=; parent=1; source=; target=EscortRequest; edge=1; label=
[71] id=AuditLog; parent=1; source=; target=; edge=; label=AuditLog
[72] id=VoiceInteraction; parent=1; source=; target=; edge=; label=VoiceInteraction
[73] id=RobotStatusHistory; parent=1; source=; target=; edge=; label=RobotStatusHistory
[74] id=RobotLocationHistory; parent=1; source=; target=; edge=; label=RobotLocationHistory
[75] id=JobEvent; parent=1; source=; target=; edge=; label=JobEvent
[76] id=Oce_jpeZVBzFi0TI9umG-6; parent=1; source=JobTask; target=JobStep; edge=1; label=
[77] id=JobTask; parent=1; source=; target=; edge=; label=JobTask
[78] id=StaffProfile; parent=1; source=; target=; edge=; label=StaffProfile
[79] id=iQREA-tmr3qiNFEObzNk-1; parent=1; source=Account; target=ServiceRequest; edge=1; label=
[80] id=Account; parent=1; source=; target=; edge=; label=Account
[81] id=Oce_jpeZVBzFi0TI9umG-34; parent=1; source=Robot; target=EmergencyIncident; edge=1; label=
[82] id=Oce_jpeZVBzFi0TI9umG-35; parent=1; source=Robot; target=DetectionEvent; edge=1; label=
[83] id=Robot; parent=1; source=; target=; edge=; label=Robot
[84] id=CrowdDensityRecord; parent=1; source=; target=; edge=; label=CrowdDensityRecord
[85] id=DetectionEvent; parent=1; source=; target=; edge=; label=DetectionEvent
[86] id=Oce_jpeZVBzFi0TI9umG-15; parent=1; source=; target=; edge=1; label=
[87] id=Oce_jpeZVBzFi0TI9umG-16; parent=1; source=Job; target=JobTask; edge=1; label=
[88] id=Oce_jpeZVBzFi0TI9umG-19; parent=1; source=Job; target=ServiceRequest; edge=1; label=
[89] id=Oce_jpeZVBzFi0TI9umG-20; parent=1; source=Job; target=Schedule; edge=1; label=
[90] id=Oce_jpeZVBzFi0TI9umG-17; parent=1; source=Step; target=JobStep; edge=1; label=
[91] id=Oce_jpeZVBzFi0TI9umG-36; parent=1; source=Step; target=Task; edge=1; label=
[92] id=Step; parent=1; source=; target=; edge=; label=Step
[93] id=Oce_jpeZVBzFi0TI9umG-13; parent=1; source=Task; target=JobTask; edge=1; label=
[94] id=Schedule; parent=1; source=; target=; edge=; label=Schedule
[95] id=Oce_jpeZVBzFi0TI9umG-10; parent=1; source=Workflow; target=Schedule; edge=1; label=
[96] id=Oce_jpeZVBzFi0TI9umG-11; parent=1; source=Workflow; target=Task; edge=1; label=
[97] id=Oce_jpeZVBzFi0TI9umG-12; parent=1; source=Workflow; target=Job; edge=1; label=
[98] id=Oce_jpeZVBzFi0TI9umG-30; parent=1; source=ServiceRequest; target=; edge=1; label=
[99] id=Map; parent=1; source=; target=; edge=; label=Map
[100] id=Slot; parent=1; source=; target=; edge=; label=Slot
[101] id=HeatmapRecord; parent=1; source=; target=; edge=; label=HeatmapRecord
[102] id=Oce_jpeZVBzFi0TI9umG-23; parent=1; source=Room; target=HospitalInformation; edge=1; label=
[103] id=Oce_jpeZVBzFi0TI9umG-24; parent=1; source=Room; target=Shift; edge=1; label=
[104] id=Oce_jpeZVBzFi0TI9umG-27; parent=1; source=Room; target=NavigationRequest; edge=1; label=
[105] id=DeliveryItem; parent=1; source=; target=; edge=; label=DeliveryItem
[106] id=Oce_jpeZVBzFi0TI9umG-21; parent=1; source=DeliveryRequest; target=Room; edge=1; label=
[107] id=Oce_jpeZVBzFi0TI9umG-22; parent=1; source=DeliveryRequest; target=Room; edge=1; label=
[108] id=Oce_jpeZVBzFi0TI9umG-25; parent=1; source=DeliveryRequest; target=DeliveryItem; edge=1; label=
[109] id=DeliveryRequest; parent=1; source=; target=; edge=; label=DeliveryRequest
[110] id=NavigationRequest; parent=1; source=; target=; edge=; label=NavigationRequest
[111] id=EscortRequest; parent=1; source=; target=; edge=; label=EscortRequest
[112] id=hKkcnfK7L3ToHOJE5ngH-3; parent=1; source=ServiceRequest; target=DeliveryRequest; edge=1; label=
[113] id=hKkcnfK7L3ToHOJE5ngH-4; parent=1; source=ServiceRequest; target=NavigationRequest; edge=1; label=
[114] id=pPEoN-BSPd6aUG21HaPH-3; parent=1; source=HospitalInformation; target=pPEoN-BSPd6aUG21HaPH-2; edge=1; label=
