# Giao tiếp MQTT cho BE

## 1 Giao thức

MAR-STEP-2, protocolVersion="1.0", MQTT5/TLS. Pi và BE là hai MQTT client. Broker không chạy workflow. Bộ này chưa sửa BE_MAR-thanh. BE cũ DISPATCH/CANCEL cấp Job không thể đưa nguyên lên topic; BE cần EXECUTE_STEP/CANCEL_STEP cấp step đã resolve tham số.

## 2 Topic

Prefix mar/robots/{robotId}, UUID thật:

| Hậu tố | Hướng | QoS | Retain |
|---|---|---|---|
| commands | BE sang Pi | 1 | false |
| event-acks | BE sang Pi | 1 | false |
| events | Pi sang BE | 1 | false |
| heartbeat | Pi sang BE | 0 | false |
| presence | Pi sang BE | 1 | true |
| mapping/preview | Pi sang BE | 0 | false |

AI theo yêu cầu step trả qua STEP_RESULT. Chưa có producer té ngã/SOS chủ động. Pi dùng clean_start=true, SessionExpiryInterval=0; BE giữ lệnh trong DB, không xếp lệnh vận động cũ trong broker cho Pi offline. Bản tin tối đa1MB. Tuyệt đối không retained command/ACK.

## 3 Handshake

Pi gửi HELLO trên events kèm transportSessionId UUID mới. BE trả HELLO_ACK trong10giây trên event-acks:

```json
{"messageType":"HELLO_ACK","robotId":"UUID_ROBOT","transportSessionId":"UUID_TRONG_HELLO","contract":"MAR-STEP-2","accepted":true}
```

Sau đó BE gửi khoảng3giây/lần:

```json
{"messageType":"SERVER_HEARTBEAT","robotId":"UUID_ROBOT","transportSessionId":"UUID_PHIEN_HIEN_TAI"}
```

Mất dữ liệu BE hợp lệ15giây: Pi đóng phiên và dừng hoạt động đang thực hiện. Broker online không có nghĩa BE online. BE không gửi heartbeat trước HELLO_ACK.

Mọi command/ACK phải có robotId và transportSessionId hiện tại. transportSessionId khác producerSessionId của nhật ký sự kiện. Khi phát lại sự kiện, giữ messageId/producerSessionId/sequenceNo nhưng gắn transportSessionId mới. Gửi lại command cũng đổi transportSessionId theo phiên, giữ nội dung nghiệp vụ.

## 4 Command

Schema trong handoff/mqtt-command.schema.json. EXECUTE_STEP có step{jobId,jobStepId,stepId,stepCode,stepType,visitNo,operationKey,timeoutSec,params}. CANCEL_STEP chỉ có jobId/jobStepId ở cấp command. Envelope gồm protocolVersion, contract, messageType, commandId, correlationId, robotId, transportSessionId, commandType, expiresAt.

expiresAt là hạn bắt đầu, cần đủ cho hai lượt ACK, ví dụ60giây từ lúc gửi. timeoutSec tính từ khi bắt đầu thực thi. File mẫu dùng UUID/timestamp minh họa; BE phải thay bằng dữ liệu thật và thời hạn mới.

## 5 ACK ứng dụng

```text
BE EXECUTE_STEP
Pi COMMAND_ACK accepted=true
BE commit DB rồi EVENT_ACK
Pi STEP_STARTED
BE commit DB rồi EVENT_ACK
Pi thực hiện
Pi STEP_RESULT hoặc STEP_ERROR
BE commit DB rồi EVENT_ACK
```

```json
{"messageType":"EVENT_ACK","robotId":"UUID_ROBOT","transportSessionId":"UUID_PHIEN_HIEN_TAI","messageId":"UUID_SU_KIEN_PI","accepted":true}
```

ACK cả COMMAND_ACK accepted=false sau khi ghi nhận từ chối. Dedup messageId và chuyển trạng thái hợp lệ; sự kiện trùng được ACK lại. MQTT packet ID/PUBACK không thay messageId/EVENT_ACK. HELLO dùng HELLO_ACK; heartbeat/preview không có EVENT_ACK. Sự kiện durable chưa ACK được gửi lại khoảng5giây/lần.

payload.status: SUCCEEDED/FAILED/TIMED_OUT/CANCELLED/RECOVERY_REQUIRED. payload.result: output step. errorCode và physicalStopConfirmed mô tả lỗi/dừng. COMMAND_ACK replayed=true có thể chứa trạng thái đã lưu; accepted=true không phải đã hoàn tất.

## 6 Việc BE cần làm

- Thêm MQTT config/publisher/subscriber; đăng ký events/heartbeat/presence/preview và gửi commands/event-acks.
- Tạo command cấp step, resolve biến/chọn nhánh; không lẫn commandId mới với dispatchCommandId cấp job.
- Tái dùng PiJobRuntimeService/PiIntegrationService qua adapter, không gọi vòng HTTP nội bộ.
- MQTT không tự có AuthenticationPrincipal HTTP. Dùng tài khoản broker và ACL giới hạn topic để định danh robot, đối chiếu robotId trong payload.
- Lưu hàng đợi lệnh bền vững, JobStep/JobEvent; ACK sau commit, chống trùng và cập nhật sai thứ tự.
- Robot dùng MQTT không đồng thời nhận cùng job qua GET commands/next.
- REST admin/staff/catalog/file giữ nguyên; WSS/SSE từ BE ra FE để cập nhật UI. Không ghi mỗi preview vào MySQL.
- AUTHENTICATE staff tiếp tục hoãn. MQTT credentials chỉ xác thực thiết bị.

## 7 Upload map

Endpoint mới cần triển khai, chưa có trong BE hiện tại:

PUT /api/v1/pi/mapping-sessions/{sessionId}/artifacts/{artifactId}

Body application/gzip: tar.gz có map.yaml/map.pgm/manifest.json. Authorization Bearer từ file cục bộ trên Pi; không dùng mật khẩu MQTT. X-Asset-SHA256=SHA256(raw YAML + byte0 + raw PGM), X-Archive-SHA256=SHA256(tar.gz). BE xác minh hash, scope robot/phiên, giới hạn kích thước và đường dẫn archive. Không giải nén đường dẫn tùy ý.

Endpoint idempotent theo artifactId+hash. Sau lưu bền vững trả:

```json
{"stored":true,"artifactId":"UUID_ARTIFACT","assetHash":"SHA256_YAML_NUL_PGM"}
```

Pi không theo redirect. URL upload cấu hình cục bộ, không nhận từ command. Token HTTP phải còn hạn; bản này chưa tự refresh token upload. Có thể cấp token phạm vi upload với thời hạn thích hợp.

DATA_SERVICE/COMMIT_MAP_ARTIFACT tạo FacilityMap từ artifact đã xác minh, cấp mapCode/versionNo theo BE, trả map_id. Không active tự động trước khi Pi đồng bộ và định vị đúng bản đồ mới.

Nguồn: https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html ; https://eclipse.dev/paho/files/paho.mqtt.python/html/client.html .
