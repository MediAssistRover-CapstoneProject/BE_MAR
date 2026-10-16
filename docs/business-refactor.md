# Business refactor — 2026-10-05

## Kết luận review
Không rewrite kiến trúc. Dùng StaffProfile đã có, không tạo Staff/Patient/WorkflowStep table mới. Account chỉ ADMIN/STAFF. Workflow → Task → Step(11 enum) → execution Job/JobStep.
Các quyết định 1–16 đã được áp dụng vào phần liên quan; không còn câu hỏi chặn refactor. Core runtime Phase 6 đã hoàn tất; các handler Delivery/Alert/Emergency/AuthVerification thuộc Phase 7. Không coi seed hoặc server contract là chương trình điều khiển robot.

## File, lý do, logic cũ và logic mới
Đường dẫn Java tương đối từ src/main/java/com/mediassistrover/backend.
| File/module | Lý do sửa | Logic cũ | Logic mới |
|---|---|---|---|
| entity/Account.java; enums/Role.java, AccountStatus.java | Patient không Account | PATIENT/approval | ADMIN/STAFF + mustChangePassword, bỏ approval fields |
| dto/account/response/AccountResponse.java | Không expose approval lỗi thời | approval fields | mustChangePassword |
| dto/auth/response/TokenResponse.java | UI cần biết mật khẩu tạm | Token only | Thêm mustChangePassword |
| dto/auth/request/PatientRegistrationRequest.java; dto/account/request/RejectionRequest.java | Xóa Patient onboarding | Registration/rejection DTO | Xóa file |
| controller/auth/AuthController.java; service/AuthService.java; service/impl/AuthServiceImpl.java | Bỏ register Patient | Register/login/refresh | Login ADMIN/STAFF; đổi password tắt cờ |
| controller/account/AdminAccountController.java; service/AdminAccountService.java; service/impl/AdminAccountServiceImpl.java | Bỏ phê duyệt Patient | pending/approve/reject | Giữ lock/unlock/reset; reset bật cờ |
| security/JwtAuthenticationFilter.java | Giới hạn mật khẩu tạm | Token ACTIVE đủ gọi API | Chỉ PUT change-password khi cờ true |
| service/impl/RefreshTokenServiceImpl.java | Refresh không vượt password gate | Refresh phiên ACTIVE | Từ chối refresh đến khi đổi password |
| config/SecurityConfig.java, OpenApiConfig.java | Đồng bộ security contract | Patient public register | Xóa permit đăng ký; bearer ADMIN/STAFF |
| service/impl/AdminBootstrapServiceImpl.java | Bỏ approval initializer | Admin approvedAt | Giữ startup env bootstrap |
| dto/account/request/StaffRequests.java; dto/account/response/StaffResponse.java | Admin CRUD Staff | Thiếu DTO liên kết | Create/update/read, không secret response |
| mapper/StaffMapper.java | Entity→DTO | Thiếu mapper | MapStruct component |
| service/StaffService.java; service/impl/StaffServiceImpl.java | Transaction nhân sự | Chưa atomic CRUD | Account+profile atomic, BCrypt, duplicate check, deactivate |
| controller/account/AdminStaffController.java; repository/StaffProfileRepository.java | Admin-only CRUD | Thiếu route/lookup | /admin/staff, unique staffCode |
| entity/Step.java; enums/StepType.java | Cố định StepType | FK catalog | @Enumerated STRING 11 enum, metadata getter |
| entity/StepType.java; repository/StepTypeRepository.java | Bỏ table source | Database handlers | Xóa file, không write API |
| dto/workflow/request/WorkflowRequests.java; response/WorkflowResponses.java | Aggregate graph + read enum | UUID granular/enable flag | Graph DTO codes, positive priority, bỏ write catalog |
| mapper/WorkflowMapper.java | Metadata enum | Entity type mapping | Enum getter mapping |
| service/WorkflowService.java; impl/WorkflowServiceImpl.java | Admin graph/lịch sử | Granular only | Aggregate atomic replace; immutable code/version; Job-reference guard |
| validation/WorkflowJsonValidator.java; service/impl/WorkflowValidationServiceImpl.java | Graph contract | Validation cơ bản | JSON shapes/reference namespace/conditions, code/order/priority checks |
| controller/workflow/WorkflowController.java | Admin routing | /workflows | /admin/workflows; aggregate graph + granular giữ nguyên |
| service/ServiceRequestService.java; impl/ServiceRequestServiceImpl.java | Anonymous robot interaction | Account actor bắt buộc ở entry | Shared creation actor, robot ownership, null Account, submit server Job |
| controller/pi/PiRequestController.java | Screen credential dùng chung | Chưa entry device request | Device-only information/navigation/escort |
| controller/request/ServiceRequestController.java; controller/job/JobController.java; service/impl/JobServiceImpl.java | Bỏ Patient token permission | PATIENT access | ADMIN/STAFF ownership |
| enums/RequestSourceType.java, JobEventProducerType.java | Actor mới | Source không đúng ERD/PATIENT producer | STAFF_UI/ROBOT_UI…; no PATIENT producer |
| repository/JobStepRepository.java | Truy cập Step ngoài persistence context | Lazy association unloaded | Fetch Step trong JobStep list |
| resources/db/migration/V1,V4,V5,V6,V7 | Baseline DEV được duyệt | Patient/table StepType/4 seed/priority0 | Schema mới, 8 DRAFT, priority1 |
| test/AuthFlowIntegrationTest.java | Auth regression | Patient onboarding | Admin Staff create/password gate/deactivate/token rotation |
| test/WorkflowGraphIntegrationTest.java | Aggregate rollback regression | Chưa có | Invalid graph rollback, immutable code, enum, eight seed/table type |
| test/RequestJobIntegrationTest.java | No Patient dependency | Account Patient fixtures | Staff fixtures + anonymous device idempotency |
| test/AdminBootstrapServiceTest.java; WorkflowIntegrationTest.java; FacilityIntegrationTest.java; RobotPiIntegrationTest.java | Model/API đổi | approval/type repository/routes cũ | Fixture và route mới |
| docs/plan.md, implementation-status.md | Tránh status cũ sai | Patient approval/43 entities/Phase6 chưa có | Phạm vi mới; Phase6 core DONE có giới hạn rõ; không overclaim hardware |

## Mapping tám Workflow
Đây là graph executable DRAFT seed, không khẳng định toàn bộ cấu hình runtime đã hoàn chỉnh. Các bước kỹ thuật CHECK_CLOSE/CHECK_LOCK vẫn giữ. Không bổ sung StepType mới.

| Workflow | Step code → enum theo nhánh chính | Input/output contract | Nhánh/error |
|---|---|---|---|
| WF_PATIENT_ESCORT | CONFIRM INTERACT → DECIDE CHECK_CONDITION → MOVE MOVE → ARRIVED INTERACT; HELP NOTIFY | Destination/map + patient_reference nullable; confirmation bool, navigation result, arrival | Không đồng ý CANCELLED; MOVE fail/timeout HELP; không patient-following |
| WF_INFORMATION_ASSISTANCE | QUERY DATA_SERVICE → ANSWER INTERACT; HELP NOTIFY | question/filter → data/answer → acknowledgement | Query fail/timeout HELP; terminal FAILED khi không phục vụ được |
| WF_SECURE_MEDICAL_DELIVERY | PICKUP MOVE → AUTH_SENDER AUTHENTICATE → CHECK_AUTH_SENDER CHECK_CONDITION → UNLOCK_SENDER CONTROL_HARDWARE → PAYLOAD_SENDER HANDLE_PAYLOAD → CHECK_PAYLOAD_SENDER CHECK_CONDITION → CLOSE_SENDER INTERACT → CHECK_CLOSE_SENDER CHECK_CONDITION → LOCK_SENDER CONTROL_HARDWARE → VERIFY_SENDER INTERACT → CHECK_LOCK_SENDER CHECK_CONDITION → COMPLETE_PICKUP NOTIFY → TRANSIT MOVE; lặp chuỗi AUTH/CHECK/UNLOCK/PAYLOAD/CLOSE/CHECK/LOCK/VERIFY/CHECK cho RECEIVER → COMPLETE NOTIFY; HELP NOTIFY | delivery id/endpoints/staff verification → auth result/payload confirmation/closed/locked → handover confirmation | Safety check false → FAILED hoặc RECOVERY_REQUIRED; operation errors HELP; max_attempts1 |
| WF_EMERGENCY_SOS_RESPONSE | ALERT NOTIFY → REMOTE_REVIEW INTERACT → CHECK_STAFF_CONFIRM CHECK_CONDITION → CONFIRM_DISPATCH INTERACT → SPEAK INTERACT → WAIT_STAFF WAIT; ESCALATE NOTIFY | SOS location/event → staff confirmation → dispatched responder/arrived | Không xác nhận dừng; fail/timeout escalation RECOVERY_REQUIRED; review video chỉ metadata/hợp đồng |
| WF_CROWD_MONITORING | INIT SET_CONTEXT → MOVE MOVE → SENSE EXECUTE_SENSING → CHECK_DENSITY CHECK_CONDITION → WARN NOTIFY → INCREMENT SET_CONTEXT → CHECK_LOOP CHECK_CONDITION → WAIT WAIT → SENSE | zone/map, warning_count10,max_loops5 → count/capturedAt → warning + loop vars | Không vượt ngưỡng bỏ WARN; bounded loop; failures/timeouts terminal theo transitions |
| WF_FALL_RESPONSE | CHECK_CONFIDENCE CHECK_CONDITION → STAFF_REVIEW INTERACT → CHECK_CONFIRMED CHECK_CONDITION → CREATE_INCIDENT DATA_SERVICE → ALERT NOTIFY → SPEAK INTERACT → WAIT_STAFF WAIT → DONE NOTIFY → COMPLETE SET_CONTEXT | detection confidence/location → staff confirmed → incident/arrival | Low confidence/không xác nhận dừng không tạo incident; fail/timeout recovery; không AI backend |
| WF_RESTRICTED_MONITORING | MOVE MOVE → SENSE EXECUTE_SENSING → CHECK_INTRUSION CHECK_CONDITION → WARN INTERACT → ALERT NOTIFY | restricted zone/map → detection/unauthorized → warning/notification | Không intrusion thành công không alert; errors theo default routes |
| WF_NAVIGATION | IS_GROUP CHECK_CONDITION; group: RESOLVE_TARGETS DATA_SERVICE → INIT SET_CONTEXT → SELECT_TARGET SET_CONTEXT → HAS_TARGET CHECK_CONDITION → SPEAK_GROUP INTERACT → NAVIGATE_GROUP MOVE; NEXT_TARGET SET_CONTEXT quay SELECT_TARGET. Single: CHECK_TARGET DATA_SERVICE → IS_VALID CHECK_CONDITION → SPEAK_SINGLE INTERACT → NAVIGATE_SINGLE MOVE; HELP NOTIFY | destination/group,map → chosen endpoint/index → navigation result/error | PATH_BLOCKED thử điểm tiếp; hết điểm HELP; timeout/error khác HELP; HELP recovery |

InputMapping chính xác dùng {"$ref":"/inputs/..."}, /outputs/STEP_CODE/... hoặc /vars/...; bảng trên mô tả ý nghĩa, không dùng JSONPath.
Migration V13 đã điền contract còn thiếu của bốn graph cũ và semantic validation chặn cấu hình StepType/service key/reference không chạy được. Chi tiết input/output/action và phần cần xác nhận trên Pi thật nằm tại `pi-contract-and-hardware-acceptance.md`.
All seeds DRAFT, accepts_new_jobs=false. Không tự publish. Không tự chạy servo/video/detection từ backend.
CANCEL là protocol-level command/ACK, không tạo StepType CANCEL. Retry biểu diễn bằng graph rõ ràng; không tự phát lại hardware.

## API và manual Swagger acceptance
Xem ma trận endpoint/request/role trong plan.md. Với Swagger:
1. Login Admin, Authorize userBearer; POST admin/staff; response không password.
2. Login Staff tạm: GET me bị403; PUT change-password thành công; login lại; Admin routes403.
3. Device token dùng deviceBearer; screen request với Idempotency-Key; same key chỉ một request/Job; khác robot key bị409.
4. POST admin/workflows/graph; GET detail; PUT graph lỗi source/target/priority/ref phải400/422 và graph cũ còn nguyên.
5. Validate/publish; update published bị chặn; clone tạo Draft; archive giữ history.
6. Enum GET có11; CALL_WORKFLOW không hợp lệ; không StepType write.
7. Logout access bị blacklist; refresh cũ rotation reuse401; account inactive không login.
Không coi checklist Swagger đã được bấm thủ công: hiện kiểm chứng bằng MockMvc/Testcontainers.

## Rủi ro/chưa hoàn chỉnh
- Phase6 core runtime đã enforce resolver, transition, visit budget và snapshot. Hardware acceptance trên Raspberry Pi thật vẫn phải thực hiện trước demo robot.
- SOS/Delivery/Emergency domain APIs chưa được hoàn thiện bởi lần refactor này.
- Staff phân công Floor/Room/ca trực chưa triển khai đầy đủ; không khẳng định scope routing khẩn cấp đã xong.
- baseline checksum cũ sẽ đổi; không xóa DB/volume đang dùng. Chỉ recreate DEV khi đã backup và kiểm tra target.
- Flyway đang cảnh báo MySQL8.4 support tested tới8.1: migration tests chạy được nhưng cần đánh giá upgrade Flyway tương thích riêng trước triển khai lâu dài.

