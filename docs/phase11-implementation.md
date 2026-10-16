# Bàn giao Phase 11: chọn endpoint tại BE và xác nhận profile Pi

Ngày **12/10/2026**. Tiếp nối [Phase 10](phase10-implementation.md) và [kế hoạch tổng](04_Ke_hoach_BE_MQTT_theo_phase.md).

**Phạm vi:** phần BE của Phase 11. BE chọn endpoint, lưu quyết định, kiểm profile trước MOVE và cấp catalog zone qua REST. Chưa xác nhận code/client đồng bộ profile trên Pi/AI hoặc nghiệm thu navigation trên robot thật. Không sửa năm tài liệu nguồn; giữ protocol MQTT **1.0 / MAR-STEP-2**, schema BE **3**, graph **3**. Phase 12–14 chưa triển khai; UNLOCK thật vẫn chờ dependency Phase 10.

## Hiện trạng và thay đổi BE

Trước phase này, RESOLVE_TARGETS lọc member/endpoint active, SELECT_TARGET sao chép phần tử đầu vào, CHECK_TARGET kiểm active/accessible; chưa lưu ràng buộc lựa chọn với MOVE. Dispatch chỉ đối chiếu map UUID và asset hash, chưa biết Pi đã áp dụng tọa độ endpoint nào.

[NavigationTargetService](../src/main/java/com/mediassistrover/backend/service/NavigationTargetService.java) nối vào [DATA_SERVICE](../src/main/java/com/mediassistrover/backend/service/impl/DataServiceStepHandler.java), [resolver](../src/main/java/com/mediassistrover/backend/service/impl/JobStepInputResolverImpl.java) và kết quả [MQTT runtime](../src/main/java/com/mediassistrover/backend/mqtt/MqttRuntimeEvents.java):

- Schema 3 chỉ chạy target service cho step RUNNING đang được BE chọn trong Job RUNNING, không bị hủy và có map ACTIVE. Schema 2 giữ handler cũ.
- RESOLVE_TARGETS kiểm group/map, kiểm quan hệ member–group–endpoint cùng map; chỉ đưa member active và endpoint active/accessible vào snapshot.
- PRIORITY dùng sequenceNo của member. NEAREST_REACHABLE xếp theo khoảng cách tọa độ với pose cùng map, fresh trong hai giây; thứ tự priority được giữ khi khoảng cách bằng nhau. Đây là **thứ tự ứng viên theo khoảng cách**, chưa chứng minh đường đi reachable; MOVE/Pi quyết định khả năng đi tới thật. Thiếu pose hợp lệ bị chặn.
- SELECT_TARGET chỉ nhận đúng danh sách đã lưu trong output RESOLVE_TARGETS trước đó của Job. Kiểm lại tọa độ, group membership, active/accessibility và map; không nhận danh sách tự khai từ client làm nguồn lựa chọn.
- Lưu `JobNavigationSelection`: resolve visit, selector visit, group, endpoint, index và target snapshot. Resolver gắn quyết định này với đúng một MOVE visit. Endpoint đổi tọa độ sau resolve/select bị chặn, không sửa snapshot operation đã có.
- Yêu cầu destination group không thể gửi MOVE từ endpoint client/context để bỏ qua SELECT_TARGET. Workflow group dùng mapping trực tiếp `/outputs/<selector>/data/endpoint_id`; delivery pickup vẫn là endpoint cụ thể.
- Một lần thử mới dùng selector/MOVE visit và operationKey mới. Chỉ chuyển ứng viên khi lần trước FAILED với `PATH_UNAVAILABLE`, command đã publish/COMPLETED, robot/Job/operation đúng và `physicalStopConfirmed=true` đã commit. `NAV_ABORTED`, timeout, cancel hoặc thiếu stop không cấp quyền fallback.
- Tôn trọng `allowFallback`. Index đi từ 0 và tăng đúng một; không lặp endpoint đã thử trong cùng Job/group. Hết targets trả `has_target=false`; maxVisits/maxTotalVisits/maxDuration của orchestrator vẫn giới hạn graph.
- CHECK_TARGET kiểm catalog trong scope Job map, không dùng để khẳng định đường đi vật lý.
- Delivery group chỉ lưu `resolvedDeliveryEndpointId` sau kết quả MOVE hợp lệ, `reached=true`, stop đã xác nhận, Job còn RUNNING và operation không bị interruption. Một lần thử thất bại không ghi điểm giao đã tới.

Pi tiếp tục nhận đúng **một** endpoint trong MOVE: `map_id`, `endpoint_id`, `mode`, `max_speed_mps`. Group và target decision nằm tại BE; không nhét danh sách endpoint hoặc group ID vào params Pi.

## Profile qua REST, gắn với phiên MQTT

[NavigationProfileController](../src/main/java/com/mediassistrover/backend/controller/pi/NavigationProfileController.java) dùng Bearer token **DEVICE** hiện có; Pi lấy token theo cơ chế device registration/login của dự án. Username/password broker chỉ dùng kết nối MQTT, không thay device token REST.

| API | Mục đích |
|---|---|
| `GET /api/v1/pi/catalog/navigation-profile` | Lấy profile map/endpoint của robot đăng nhập |
| `POST /api/v1/pi/catalog/navigation-profile/ack` | Xác nhận Pi đã áp dụng profile trong phiên MQTT hiện tại |
| `GET /api/v1/pi/maps/{mapId}/zones/config` | Lấy config zone cùng map/frame cho adapter/producer AI |

GET catalog manifest, map metadata và download cũ tiếp tục dùng được. Hash catalog cũ trong Robot không được dùng làm bằng chứng profile MQTT: nó có cách tính và dữ liệu khác. Không thêm MQTT SYNC command hoặc field heartbeat ngoài contract.

Ví dụ response profile; UUID/hash dưới đây chỉ minh họa:

```json
{
  "profileVersion": "BE-NAV-PROFILE-1",
  "robotId": "11111111-1111-1111-1111-111111111111",
  "profileHash": "<sha256 của profile do BE tính>",
  "profile": {
    "map_id": "22222222-2222-2222-2222-222222222222",
    "map_asset_sha256": "<sha256 map asset đã lưu tại BE>",
    "frame_id": "map",
    "map_version": 1,
    "endpoints": {
      "33333333-3333-3333-3333-333333333333": {
        "map_id": "22222222-2222-2222-2222-222222222222",
        "x_m": 1,
        "y_m": 2,
        "yaw_rad": 0
      }
    }
  }
}
```

[NavigationProfileService](../src/main/java/com/mediassistrover/backend/service/NavigationProfileService.java) tạo profile từ map ACTIVE có SHA-256/frame hợp lệ. Chỉ có endpoint active/accessible. `profileHash` tính từ JSON profile: key object sắp tăng dần, JSON compact UTF-8, số tọa độ BigDecimal bỏ zero cuối; không bao gồm robotId/profileHash. Pi cần giữ chính profile/hash BE phát hành, kiểm map bytes và cấu hình tọa độ thực đã áp dụng trước ACK; không chỉ gọi ACK ngay sau download.

Body ACK:

```json
{
  "transportSessionId": "<UUID phiên kết nối Pi gửi trong HELLO>",
  "producerSessionId": "<UUID tiến trình Pi gửi trong HELLO>",
  "mapId": "<UUID map trong profile>",
  "mapAssetHash": "<64 ký tự hex SHA-256 map asset>",
  "profileHash": "<64 ký tự hex từ manifest đã áp dụng>"
}
```

BE kiểm:

1. Device token đúng robotCode/tokenVersion và còn hạn; robot đã enrolled MQTT.
2. HELLO được chấp nhận, HELLO_ACK đã publish, heartbeat mới trong 15 giây, transport/producer khớp phiên hiện tại.
3. Với profile mới, Pi phải báo IDLE, không active JobStep và không mapping session. Không áp dụng profile mới khi MOVE đã publish còn unresolved.
4. Map UUID, map asset hash và profile hash khớp catalog BE và heartbeat Pi.

BE lưu `RobotNavigationProfile` và wake-up các BE Job đang giữ robot trong cùng transaction. ACK lặp đúng profile/phiên trả `duplicate=true` và timestamp cũ; ACK lặp profile đang chạy không bắt Pi áp dụng lại. Phiên transport/producer hoặc tokenVersion đổi làm xác nhận cũ không đủ cho dispatch mới.

**ACK này là xác nhận từ client Pi đã xác thực**, không phải BE tự quan sát file trên Pi. Client Pi phải triển khai kiểm bytes/config và gửi ACK trung thực; code Pi chưa có trong workspace để kiểm chứng.

## Chặn MOVE và giữ snapshot

[Dispatch](../src/main/java/com/mediassistrover/backend/mqtt/MqttStepDispatchService.java) tiếp tục yêu cầu map/hash khớp, session MQTT sẵn sàng và không có operation Pi unresolved. Nếu chưa có profile ACK hợp lệ, selected MOVE giữ PENDING và robot vẫn thuộc Job; **chưa tạo command**. Profile ACK sẽ đánh thức Job. Hủy Job/maxDuration dùng cơ chế Phase 8; không coi việc chờ profile là MOVE đã thực thi.

Outbox lưu thêm `navigation_profile_hash` bên ngoài wire. [Publisher plan](../src/main/java/com/mediassistrover/backend/mqtt/MqttPublicationPlans.java) kiểm lại:

- Trước lần publish đầu, profile xác nhận và catalog hiện tại phải khớp hash của outbox. Catalog đổi hoặc ACK profile khác thì giữ lệnh cũ, không sửa params hoặc hash; command có thể hết hạn trước publish và đi timeout.
- Resend của command đã publish giữ body/commandId/operationKey/expiry và profile đã gắn với operation. Không đổi target theo catalog BE vừa sửa. Profile/token/session không còn khớp đưa operation đã publish vào recovery; CANCEL vẫn dùng cơ chế Phase 8, không bị gate profile cản.
- Heartbeat đổi map asset hash trong MOVE đang bay khiến [session registry](../src/main/java/com/mediassistrover/backend/mqtt/MqttSessionRegistry.java) giữ recovery, không chạy bước vận động tiếp.

REST heartbeat/location cũ bị chặn với robot enrolled MQTT trong [PiIntegrationServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/PiIntegrationServiceImpl.java), để các đường telemetry không ghi đè map/trạng thái của phiên MQTT. REST catalog/map/profile vẫn giữ. Robot schema 2 chưa enrolled tiếp tục cách chạy cũ.

## Migration và workflow

- [V24](../src/main/resources/db/migration/V24__navigation_profile_and_group_selection.sql) tạo profile/selection và field outbox. Draft WF_NAVIGATION do migration tạo đổi điều kiện failure fallback thành `/error/errorCode == PATH_UNAVAILABLE`; failure khác đi HELP.
- [V25](../src/main/resources/db/migration/V25__schema3_delivery_group_routing.sql) thêm bảy bước GROUP_MODE/RESOLVE/INIT/SELECT/HAS/MOVE/NEXT vào delivery schema 3 DRAFT do migration tạo. Sau COMPLETE_PICKUP, graph chọn TRANSIT trực tiếp hoặc chuỗi group; group MOVE thành công tới AUTH_RECEIVER. Không biến group thành danh sách lệnh Pi.
- Delivery input schema thêm target_mode/delivery_group_id và oneOf endpoint/group; snapshot request vẫn do DB nghiệp vụ cấp.
- Hai migration chỉ đổi draft có marker nguồn migration. Recompile/hash lại các draft đó bằng initializer; không tự publish, không đổi schema 2, workflow published hoặc draft do admin tự soạn.
- Delivery vẫn DRAFT vì UNLOCK thiếu STAFF_VERIFIED. Việc thêm graph group không chứng minh chạy được giao hàng thật.

Các workflow patrol/escort khác chưa được tự viết lại để thêm group subgraph; phần nghiệm thu/nối các workflow còn lại nằm tại Phase 13. Nếu một workflow schema 3 nhận request destination group nhưng MOVE bỏ qua SELECT_TARGET, BE sẽ chặn thay vì chọn endpoint từ dữ liệu client.

Khi nâng BE có Job MQTT MOVE đang hoạt động, cần dừng dispatch và đối soát trước rollout. Outbox cũ không có profile hash **không được tự cấp quyền publish**; operation đã publish thiếu profile evidence sẽ cần recovery. Không chuyển transport hoặc phát lại tác động vật lý để vượt gate.

## Zone và phối hợp ngoài repo

Zone config trả `BE-ZONE-CONFIG-1`, configHash, map UUID/version/asset hash/frame và các zone active: UUID/type/polygon/capacity/calibrated area/camera source/ROI. Device chỉ đọc map được gán. Dữ liệu polygon/frame giữ nguyên giá trị catalog; BE không tự suy polygon camera thành polygon trên map, không tự chứng minh calibration đã đúng.

Checklist Pi/AI/UI còn cần:

- Pi tích hợp GET/apply/ACK profile sau HELLO và heartbeat, giữ đúng UUID/hash/tọa độ/frame; reconnect cần ACK theo phiên mới, chỉ áp dụng khi idle và không thay profile trong operation đang chạy.
- Dùng REST map metadata/download hiện có để lấy asset; đối chiếu SHA-256 thực trên Pi. Chưa xác minh URI map hoặc asset triển khai thật.
- AI adapter/producer đọc zone config theo đúng map/frame/version, xác minh nguồn camera/ROI/calibration. Không giả định worker đã hỗ trợ MQTT sync polygon.
- UI hoàn tất instruction hiện có để WF_NAVIGATION tiến đến MOVE. SPEAK/audio còn gate riêng; không biến một DISPLAY thành đã phát âm thanh thật.
- Nghiệm thu robot thật: một endpoint, hai ứng viên với PATH_UNAVAILABLE/stop, NAV_ABORTED, reconnect, timeout/cancel và cập nhật profile. Không thay nghiệm thu bằng test DB hoặc broker simulator.

## Kiểm thử

[MqttNavigationGroupIntegrationTest](../src/test/java/com/mediassistrover/backend/MqttNavigationGroupIntegrationTest.java) dùng DB, orchestrator, inbox/outbox và transport mock: thiếu profile, ACK đúng/sai/session/token, REST Device auth, duplicate, đổi catalog trước publish, giữ command/hash, reconnect, fallback/new operation, abort, thiếu stop, hết ứng viên/allowFallback và endpoint delivery đã tới.

[NavigationTargetScopeIntegrationTest](../src/test/java/com/mediassistrover/backend/NavigationTargetScopeIntegrationTest.java) kiểm active/accessibility, FK member cùng map, scope group, fresh/stale pose, injected/changed target, một selection một MOVE, chống bypass bằng client/context và zone/frame.

[Phase24NavigationMigrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase24NavigationMigrationIntegrationTest.java) nâng schema tạm từ V23 → V25, kiểm schema 2/transition/draft authored được giữ, marker/hash recompile và graph group mới. Chỉ chạy với TEST_DB_URL local prefix `be_mar_phase11_`; schema UUID riêng được xóa trong finally, không clean DB của caller.

Kết quả Java 21/MySQL local **9.5**, DB test riêng `be_mar_phase11_verify_20261012`:

- **106/106** test trọng tâm đạt: target/profile, scope/zone, MQTT runtime, cancel/recovery, legacy robot/Pi, resolver, DATA_SERVICE, schema 3 migration và delivery authorization Phase 10. Log `target/phase11-final-targeted-tests.log`.
- Sau đó **22/22** test đạt để xác nhận catalog workflow mới và chặn REST heartbeat của robot MQTT. Log `target/phase11-final-catalog-tests.log`; có overlap với nhóm 106, không cộng hai số thành số test độc lập. Suite group cuối **19/19**, scope **11/11**, migration V23 → V25 **1/1**.
- Toàn suite trước các chỉnh sửa cuối có **378 test: 374 đạt, 2 fail, 2 skip**. Log `target/phase11-full-tests.log`. Một failure là assertion catalog cũ đòi 25 bước cho delivery schema 3; đã cập nhật đúng 32 bước và chạy lại WorkflowIntegrationTest **3/3 đạt**. Schema 2 vẫn kiểm 25 bước. Failure còn lại là analytics MySQL 9.5 `Phase14IntegrationTest.exposesAnalyticsIndexesToTheMysqlQueryPlanner` (`possible_keys=null` thay vì `IX_Job_queued_at`), đã có trước Phase 11 và được đối chứng ở [Phase 8–9](phase8-9-implementation.md). Không sửa analytics trong phase này.
- Hai skip của lượt full: `Phase19MigrationTest` cần Docker daemon; `Phase23MigrationIntegrationTest` chỉ cho phép DB test prefix `be_mar_phase10_`, không chạy trên DB prefix phase11. Bài V23 đã đạt ở lượt Phase 10; bài V23 → V25 của Phase 11 chạy riêng trên schema UUID tạm và đạt. Chưa nghiệm thu Docker/MySQL 8.4.
- Broker TLS local/simulator **2/2 đạt** trong lượt full, cho execute/cancel WAIT; chưa test MOVE/group trên robot thật. Các nhóm mới dùng transport mock, DB và MQTT event contracts thật của BE.
- Offline checker **9 schema / 131 fixture / 11 StepType** đạt. Wire Pi giữ nguyên; Markdown UTF-8/local links và `git diff --check` đã kiểm.

Build dùng thư mục riêng `target/phase11-verification` tránh IDE ghi lại target/classes lúc test; POM tạm đã bỏ sau kiểm thử. Các thay đổi trước Phase 11 trong workspace được giữ; chưa tạo commit. Phase 11 đã có phần BE và bằng chứng kiểm thử, còn cần client Pi/AI và nghiệm thu robot để hoàn tất tích hợp.
