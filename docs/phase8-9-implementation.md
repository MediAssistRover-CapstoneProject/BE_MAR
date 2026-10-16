# Bàn giao Phase 8–9: hủy từng operation, recovery và Alert ACK

Ngày **12/10/2026**. Tiếp nối [Phase 5–7](phase5-7-implementation.md) và [kế hoạch tổng](04_Ke_hoach_BE_MQTT_theo_phase.md). Phạm vi là code BE, MySQL test và broker local/simulator; chưa nghiệm thu motor/servo/Pi/UI thật. Năm tài liệu nguồn giữ nguyên.

## Phase 8 — Timeout, cancel và recovery

### Hiện trạng và thay đổi BE

[V22](../src/main/resources/db/migration/V22__mqtt_operation_cancellation_and_recovery.sql) giữ nguyên V21 và bổ sung:

- Outbox có `command_type`, `interruption_reason`, `physical_stop_confirmed`, `result_envelope_json`; unique `(job_step_id,command_type)` cho một EXECUTE_STEP và một CANCEL_STEP của cùng visit.
- JobStep có `interruption_reason`, `interruption_requested_at`; REST/SSE trả thêm hai field này để UI thấy bước đang chờ dừng. Trạng thái step vẫn giữ kết quả Pi báo; deadline BE lưu riêng.
- Bảng `MqttOperationRecoveryReceipt` lưu actor, Idempotency-Key và body/response bất biến cho quyết định staff. Receipt không thay journal hoặc hardware verification.

[MqttOperationControlService](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationControlService.java) được gọi dưới Job lock trước khi orchestrator xử lý hủy/deadline:

| Tình huống | BE xử lý |
|---|---|
| Job hủy, execute chưa từng publish | ABANDONED outbox; đóng step/Job tại BE, không gửi Pi |
| Hủy Job đang thực thi Pi | Ghi `JOB_CANCEL`, tạo CANCEL_STEP; giữ Job/robot đến khi có stop hoặc recovery |
| Step quá timeout từ STEP_STARTED đã commit | Ghi `STEP_TIMEOUT`, tạo CANCEL_STEP; không tạo SUCCEEDED bằng đồng hồ |
| Execute ACK accepted nhưng chưa START đến expiresAt | Ghi STEP_TIMEOUT và CANCEL_STEP; không tự suy handler chưa chạy |
| Job quá maxDuration | Ghi `WORKFLOW_TIMEOUT`, hủy operation; sau stop kết thúc Job FAILED |
| Cancel chỉ accepted, chưa có terminal/stop | Tiếp tục chờ; hết hạn cancel chuyển recovery |
| Cancel bị từ chối, thiếu/false stop hoặc offline | Giữ recovery/robot, không chạy bước kế tiếp |
| Server/UI wait sau Pi operation đã dừng xác nhận | Hủy/timeout tại BE; không gửi lại tác động Pi đã kết thúc |

`cancelCommandId` trong Job vẫn là marker API cấp Job cũ; commandId CANCEL_STEP thật lấy từ outbox/endpoint evidence. Retry giữ ID/body/expiry của mỗi command. Tắt `MQTT_DISPATCH_ENABLED` hoặc bỏ StepType allowlist chặn execute mới/resend, nhưng vẫn cho gửi CANCEL_STEP cho robot enrolled để dừng công việc; tắt toàn client/bỏ enrollment thì không có network publish.

Wire CANCEL_STEP theo contract có `jobId`, `jobStepId` ở cấp command, không có `step`, params hoặc workflow. Không gửi GPIO/PWM/velocity. Publisher chỉ phát command sau commit; cancel có thể publish khi Job RECOVERY_REQUIRED nếu đã có HELLO_ACK/heartbeat mới, không cần dispatchReady cho execute. Re-address cancel sang session mới chỉ thay transportSessionId; không tạo execute operation mới.

[MqttRuntimeEvents](../src/main/java/com/mediassistrover/backend/mqtt/MqttRuntimeEvents.java) xử lý:

- ACK hủy ghép bằng commandId lưu trong outbox; envelope Job/Step null được chấp nhận riêng cho CANCEL_STEP, phải đúng robot/session/correlation. Event khác vẫn cần đầy đủ operation scope.
- STEP_ERROR có thể correlation theo EXECUTE_STEP hoặc CANCEL_STEP đã publish. STEP_ERROR dừng đến trước ACK hủy và ACK hủy đến trước STEP_ERROR đều được ghi/ACK sau commit; không hoàn tất hai lần.
- accepted=true không đủ chứng minh đã dừng. ACK cần terminal state và stop=true; ACK journal SUCCEEDED cần nested output hợp lệ, không tự bịa output thành công.
- Sau timeout BE, Pi có thể báo CANCELLED hoặc SUCCEEDED khi kết quả đua với cancel. Step giữ trạng thái Pi báo; transition dùng TIMEOUT theo `interruption_reason=STEP_TIMEOUT`. Chỉ chọn timeout edge sau stop; `JOB_CANCEL` kết thúc CANCELLED, `WORKFLOW_TIMEOUT` kết thúc FAILED.
- ACK/error muộn cho visit đã dừng được nhận khi graph đã chọn bước kế tiếp, vẫn phải đúng command/operation và không mâu thuẫn kết quả cũ. Không đổi snapshot hoặc khởi chạy lại visit.
- Late stop receipt được lưu khi Job đã recovery, nhưng không tự gỡ quyết định recovery. OPERATOR_RECONCILED lưu bằng chứng Pi local và vẫn chờ staff review BE.

[MqttSessionRegistry](../src/main/java/com/mediassistrover/backend/mqtt/MqttSessionRegistry.java) và scheduler kiểm heartbeat hết hạn ngay cả khi BE mất broker connection; kiểm lại timestamp dưới lock để heartbeat vừa commit không bị scan cũ đóng nhầm. HELLO/journal đối soát theo operation/producer; unknown operation, journal thiếu hoặc Pi process mới giữ recovery. Không tự replay tác động vật lý sau restart/disconnect.

Transaction MQTT xử lý các truy vấn Job theo robot/status dùng READ COMMITTED để tránh gap lock MySQL khi Job RUNNING đổi terminal. Lock thứ tự Job → Robot → Session → receipt; inbox insertion lấy Robot lock trước Session do kiểm FK. Durable state vẫn commit trước ACK; lỗi hạ tầng chưa ACK và được retry. Không đổi isolation toàn ứng dụng.

### API recovery dành cho staff

[Controller](../src/main/java/com/mediassistrover/backend/controller/job/MqttOperationRecoveryController.java) và [service](../src/main/java/com/mediassistrover/backend/mqtt/MqttOperationRecoveryService.java):

```text
GET  /api/v1/jobs/{jobId}/operations/recovery
POST /api/v1/jobs/{jobId}/operations/{jobStepId}/reconcile
Authorization: Bearer <phiên STAFF/ADMIN>
Idempotency-Key: <1–150 ký tự chữ/số/._:->
```

GET trả selected operation, state/identity hai command, stop receipt, HELLO journal và heartbeat; không có credential. STAFF cần được assign vào Job; ADMIN được review theo role. Không chỉ lấy quyền từ việc tạo request hoặc client tự khai actor.

Ví dụ body POST, thay identity bằng giá trị GET:

```json
{
  "commandId": "<EXECUTE_STEP command UUID>",
  "operationKey": "<operationKey đang recovery>",
  "transportSessionId": "<phiên Pi hiện tại>",
  "evidence": "PI_STOP_CONFIRMED",
  "confirmedStopped": true,
  "outcome": "CONFIRM_CANCELLED",
  "note": "Đã kiểm operation và bằng chứng dừng"
}
```

`outcome`: CONFIRM_CANCELLED hoặc RESUME_AS_FAILED (tên enum legacy; endpoint này chỉ đóng Job FAILED, không resume/retry operation). `evidence`:

- PI_STOP_CONFIRMED: BE đã lưu stop=true do Pi báo cho operation.
- STAFF_INSPECTED_STOP: có OPERATOR_RECONCILED đã commit ở Pi→BE, HELLO journal CANCELLED và staff xác nhận kiểm thực tế. Chỉ field confirmedStopped=true từ client không đủ.

Cả hai cần HELLO hiện tại có operation terminal, heartbeat sau HELLO mới trong 15 giây, worker IDLE/activeJobStepId=null, không có mapping session. Journal còn bất kỳ operation ACCEPTED/RUNNING/RECOVERY_REQUIRED, unknown/foreign operation hoặc session cũ đều bị chặn; không giải phóng robot chỉ vì riêng operation được review đã kết thúc. Nếu Pi đã bỏ bản ghi operation khỏi journal giới hạn 30 mục, endpoint giữ recovery; cần đối soát local/producer thay vì tự bỏ điều kiện.

Receipt cùng step/key/actor/body trả nguyên kết quả cũ, kể cả sau terminal; đổi body/actor cùng key trả conflict. Quyết định, JobEvent, audit và wake-up commit cùng transaction. Staff inspection không ghi Pi `physicalStopConfirmed=true`, không sửa hardware `verified` hoặc kết luận đã giao hàng. Outbox operation được retire `RESOLVED_BY_STAFF`; robot về STARTING, phiên bị đóng và cần HELLO/heartbeat mới trước dispatch.

API `/jobs/{id}/reconcile` cũ tiếp tục dành cho legacy/server-only; Job BE có MQTT command phải dùng operation recovery. Recovery này đóng Job; chạy lại cần Job/visit mới theo quyết định nghiệp vụ, không sửa params/operationKey cũ.

### Phối hợp ngoài repo và tiêu chí

Pi cần cancel adapter, terminal/stop receipt, journal và OPERATOR_RECONCILED đúng contract. Khi mất mạng, BE không thể truyền lệnh dừng tức thì; watchdog/local stop của Pi vẫn là dependency thực tế. Mapping session đang mở nhưng không active step cần STOP theo Phase 12; sensing MAPPING/UNLOCK vẫn bị chặn nên chưa đưa vào nghiệm thu Phase 8.

Test MySQL và simulator MQTT/TLS đã kiểm timeout/cancel/restart giữ đúng graph và robot, thứ tự ACK/error, stale session, quyền staff, duplicate/concurrent recovery và rollback. Chưa có bằng chứng dừng motor/servo thật, kill/reboot Pi thật hoặc NTP/profile robot sản xuất. HA nhiều BE cùng quản lý một robot chưa nghiệm thu.

## Phase 9 — NOTIFY lưu Alert, WAIT/EVENT chờ ACK

### Hiện trạng và thay đổi BE

[NotifyStepHandler](../src/main/java/com/mediassistrover/backend/service/impl/NotifyStepHandler.java) schema 3 tôn trọng `severity`, `data`, `require_ack`, tạo Alert và recipient snapshot rồi trả completed=true:

```json
{
  "alert_id": "<Alert UUID>",
  "notification_type": "STAFF_REVIEW",
  "persisted": true,
  "requires_ack": true
}
```

Output không đồng nghĩa đã delivered/acked. Schema 2 giữ output notification_id/delivered/acked và chờ đọc/ACK theo handler cũ. Alert schema 3 có cửa sổ ACK đến hạn workflow, không lấy timeout ngắn của thao tác lưu NOTIFY làm hạn WAIT ACK.

BE config `ack_policy` (ANY mặc định, hoặc ALL) và `recipients` tùy chọn ở params/mapping workflow; recipients là UUID account hoặc ADMIN/STAFF, mặc định active ADMIN/STAFF. Chỉ dữ liệu `data` đi vào Alert data schema 3; recipients/policy là metadata BE. [BE contract](schemas/be-step-contracts.json) và bản đóng gói ghi thêm hai field tùy chọn này; không thay contract Pi MAR-STEP-2. Những selectors được resolve thành account snapshot khi tạo Alert; không thay danh sách người nhận giữa operation.

Workflow cần chờ đọc/xác nhận cấu hình riêng:

```json
{
  "paramsJson": {
    "wait_type": "EVENT",
    "event_type": "ALERT_ACKNOWLEDGED"
  },
  "inputMappingJson": {
    "correlation_key": { "$ref": "/outputs/NOTIFY_STEP/alert_id" }
  }
}
```

Thay NOTIFY_STEP bằng stepCode thực tế. BE resolver giữ rule mapping ghi đè params. WAIT/EVENT chạy BE; NOTIFY và WAIT không gửi Pi. Alert ACK dùng API Alert hiện có qua phiên account recipient.

[AlertServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/AlertServiceImpl.java) giữ recipient/ANY/ALL và cửa sổ ACK; cả ADMIN cũng phải là recipient để ACK. Role/status/token version hiện tại được kiểm. Đọc Alert chỉ cập nhật delivered, không phát event ACK. Lock Job trước Alert/recipient; ACK dùng READ COMMITTED để hai recipient ALL đồng thời đọc đủ kết quả đã commit.

[WorkflowEventServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/WorkflowEventServiceImpl.java) phát event nội bộ một lần khi đạt policy:

- `event_type=ALERT_ACKNOWLEDGED`, `correlation_key=alert_id`, producer SERVER; actor là recipient ACK thật.
- Data ghi alert_id, ack_policy, số recipient đã ACK/tổng và actor account; eventId ổn định theo Alert để chống tạo trùng.
- ACK đến trước WAIT lưu event unconsumed; WAIT tương ứng tiêu thụ đúng một lần. ACK sau WAIT hoàn tất nếu WAIT chưa hết hạn; completion/wake-up cùng transaction.
- Job đã terminal/cancel requested không được ACK đánh thức lại. WAIT đã quá deadline không bị biến thành SUCCEEDED; orchestrator chọn TIMEOUT. Alert vẫn có thể ghi ACK nghiệp vụ trong cửa sổ riêng.
- Generic `POST /jobs/{id}/events` schema 3 chặn eventType ALERT_ACKNOWLEDGED để staff không tự bỏ qua recipient policy. Các workflow event khác và schema 2 giữ contract hiện có.

Readiness đã mở NOTIFY schema 3; các draft migration không tự publish. Workflow muốn chờ ACK cần thêm WAIT/EVENT và mapping mới qua admin API; không tự chèn bước vào mọi workflow cũ vì xác nhận đã đọc khác xác nhận staff tới nơi/giao hàng.

### Phối hợp ngoài repo và tiêu chí

UI cần hiển thị Alert, phân biệt trạng thái lưu/đọc/ACK và gửi API ACK bằng đúng recipient; SSE/REST Job hiện có nhận instruction/status sau commit. Không có frontend ngoài repo để xác nhận luồng hiển thị thật. Pi không xử lý NOTIFY/ACK recipient.

Test đã kiểm NOTIFY hoàn tất trước delivered, ANY/ALL, ACK trước/sau WAIT, ACK sai recipient/correlation, event giả, duplicate/concurrent ALL, rollback và ACK muộn. Runtime schema 2 vẫn có test chờ delivered/ACK cũ.

## Kiểm thử, migration và bàn giao

- [MqttCancellationRecoveryIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttCancellationRecoveryIntegrationTest.java): timeout/expiry/cancel, thứ tự receipt, mất broker/reconnect, staff scope/idempotency/concurrency và rollback.
- [MqttTlsBrokerIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttTlsBrokerIntegrationTest.java): luồng WAIT thành công và luồng CANCEL_STEP → STEP_ERROR trước cancel ACK qua broker MQTT 5/TLS/password/ACL thật với simulator.
- [NotificationAckWorkflowIntegrationTest](../src/test/java/com/mediassistrover/backend/NotificationAckWorkflowIntegrationTest.java): persistence khác delivered/ACK, event sớm, policy/recipient/correlation, concurrency và deadline.
- [RuntimeContextEventIntegrationTest](../src/test/java/com/mediassistrover/backend/RuntimeContextEventIntegrationTest.java) giữ regression NOTIFY schema 2; tái chạy toàn suite graph, authorization, input/output và migration.

Contract offline hiện có **9 schema, 131 fixture (88 positive/43 negative), 11 StepType**; 2 fixture mới kiểm recipient/policy BE. Tám schema đóng gói cần khớp tài liệu (bỏ root comment). Không đổi năm tài liệu nguồn hoặc V19–V21 đã áp dụng.

**Kết quả xác minh (Java 21, MySQL 9.5):**

- Toàn suite trên DB mới `be_mar_phase89_test_20261012`: **326 test, 324 đạt, 1 fail baseline analytics, 1 skip Docker**. Log `target/phase8-9-fresh-db-tests.log`, output/report ở `target/phase567-verification`. V1–V22 đã chạy từ DB trống; không còn lỗi do code Phase 8–9 trong lượt này.
- 20 test cancellation/recovery (có HTTP/JWT, rollback và concurrent receipt), 8 test notification workflow và 2 test broker TLS đạt. Test broker thực sự chạy cả luồng thành công lẫn hủy; không skip. Không có deadlock trong lượt xác minh DB mới sau đổi isolation/lock order.
- Lỗi cũ: `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner` trên MySQL 9.5 trả `possible_keys=null`; đã tái hiện với baseline V18 ở lượt Phase 1–2. Không sửa analytics/index để che lỗi. Skip: `Phase19MigrationTest` vì Docker daemon không chạy; chưa kiểm MySQL 8.4 qua Docker.
- Sau lượt full, kiểm thêm ranh giới input NOTIFY schema 3: metadata legacy phải nằm trong data, message đúng kiểu và recipients đúng selector. Nhóm input/notification/legacy/migration đạt **47/47**, log `target/phase8-9-final-input-tests.log`.
- Kiểm thêm recovery không giải phóng robot khi journal còn operation khác chưa kết thúc/không rõ scope; nhóm recovery cuối đạt **21/21**, log `target/phase8-9-final-recovery-tests.log`.
- Lượt chạy chồng trước đó không dùng làm bằng chứng; đã dừng và tạo DB mới để chạy tuần tự. Mọi kết quả nghiệm thu ở trên dựa trên log DB mới.

Migration V22 ban đầu kiểm trên DB test Phase 5–7, sau đó chạy lại V1–V22 trên DB mới nêu trên; chưa áp dụng DB ứng dụng. Build sử dụng POM tạm/output riêng tránh IDE ghi đè class; POM tạm được bỏ sau kiểm tra. Log/report/cert broker nằm trong target, không thay nghiệm thu robot thật.

**Phase tiếp theo:** Phase 10 cần authorization UNLOCK thật gắn robot/operation/purpose/expiry và milestone delivery sau kiểm đóng/khóa. Phase 11–14 vẫn là kế hoạch; không tự mở UNLOCK hoặc publish toàn bộ workflow draft.
