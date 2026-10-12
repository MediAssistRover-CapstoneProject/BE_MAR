# Phase 12 — Mapping và artifact tại BE

Ngày đối chiếu: 12/10/2026. **Phần BE đã được triển khai; Pi SLAM/upload, UI và robot thật chưa nghiệm thu.** Protocol `1.0`, contract `MAR-STEP-2`, step schema `3`, graph version `3`; schema `2` giữ runtime cũ. Schema Pi vẫn là `RECONSTRUCTED_UNVERIFIED_PI`.

## Luồng thực thi

[V26](../src/main/resources/db/migration/V26__mapping_session_and_artifact.sql) thêm `MappingSession`, `MapArtifact` và cờ `JobStep.mapping_cleanup`. [V27](../src/main/resources/db/migration/V27__mapping_workflow_draft.sql) tạo `WF_MAPPING` ở DRAFT, không nhận Job mới. Initializer hiện có compile template, không tự publish.

```text
START_MAPPING tại Pi
→ WAIT_SAVE / MAPPING_SAVE_REQUESTED tại BE
→ SAVE_MAPPING tại Pi và HTTP upload
→ COMMIT_MAP tại BE
→ STOP_MAPPING tại Pi
→ kết thúc Job
```

[MappingLifecycleService](../src/main/java/com/mediassistrover/backend/mapping/MappingLifecycleService.java) cấp session/artifact UUID khi BE chọn START, đặt các ID vào `/vars/mapping_session_id` và `/vars/artifact_id`. ID có scope Job/robot, không cấp lại START cho phiên đã thực thi. SET_CONTEXT không sửa hai identity này. START/SAVE/STOP dùng JobStep, operationKey và outbox riêng; MQTT chỉ chứa action đã resolve.

Phiên được đánh dấu **potentially_open** trong cùng transaction ghi lần publish START đầu tiên, trước khi gửi qua mạng. Đây là trạng thái bảo thủ: nếu crash ngay sau commit, BE giữ phiên để đối soát. Kết quả START không đóng phiên. SAVE giữ phiên mở, kể cả `SAVED_LOCAL` hoặc `SAVED`.

BE kiểm `mappingEnabled`, action cố định, `maxAttempts=1`, timeout và UUID được cấp. START timeout tối thiểu `startTimeoutSec+10`, với profile mặc định 45s và template 60s; SAVE tối thiểu 60s, template 90s; STOP tối thiểu 15s, template 20s. Validator input dùng floor tối thiểu START 55s; gate cấu hình kiểm thêm profile BE. Cấu hình BE phải khớp `start_timeout_sec` thật của Pi. Không gửi launch argv, shell, GPIO hoặc velocity qua params.

Khi phiên mở, BE chặn các action Pi ngoài MAPPING/SAVE hoặc STOP. Map/asset hash đang quét không được lấy từ map cũ; mapping pose không ghi vào lịch sử vị trí của map cũ.

## API và thao tác UI

| API | Quyền và hành vi |
|---|---|
| `POST /api/v1/mapping-jobs` | ADMIN; body `workflowId`, `robotId`, `floorId`, `mapName`; workflow MAPPING schema 3 phải PUBLISHED và acceptsNewJobs, floor active, robot IDLE/enrolled và không giữ Job/phiên khác |
| `GET /api/v1/jobs/{jobId}/mapping-session` | ADMIN hoặc staff có scope Job; trả session/artifact ID, state, potentially_open và preview mới nhất hoặc null |
| `POST /api/v1/jobs/{jobId}/events` | API event hiện có; Save gửi eventType=`MAPPING_SAVE_REQUESTED`, correlationKey=session UUID, eventId UUID, occurredAt và data object |
| `POST /api/v1/jobs/{jobId}/cancel` | API cancel hiện có; body note; cancel Job kích hoạt đóng phiên bằng STOP |
| `GET /api/v1/jobs/{jobId}/stream` | SSE hiện có; thêm event `mapping-preview`, giữ `job-state` và kiểm lại session/quyền mỗi lần push |
| `PUT /api/v1/pi/mapping-sessions/{sessionId}/artifacts/{artifactId}` | DEVICE Bearer HTTP; body application/gzip và hai SHA-256 header |
| `GET /api/v1/pi/mapping-artifacts/{artifactId}` | DEVICE là chủ phiên hoặc robot được gán map tương ứng; tải gzip đã kiểm lại hash, không lộ đường dẫn storage |

Create Job dùng [JobExecutionServiceImpl](../src/main/java/com/mediassistrover/backend/service/impl/JobExecutionServiceImpl.java), tạo JobTask/JobStep và wakeup theo runtime hiện có; không thêm ServiceRequestType hoặc StepType. Job mapping không gán map cũ làm map đang quét. Session được cấp lúc chọn START, vì vậy GET session trước thời điểm đó trả not found. Admin có thể assign staff bằng API Job hiện có.

Save event kiểm scope Job, correlation session, trạng thái MAPPING và ý định cancel. Event đến sau START nhưng trước WAIT được cơ chế workflow event hiện có giữ lại để WAIT tiêu thụ. Nút Hủy gọi cancel; CANCEL_STEP chỉ hủy action đang chạy, không thay thế STOP của phiên SLAM.

## Upload và commit

[MappingArchiveReader](../src/main/java/com/mediassistrover/backend/mapping/MappingArchiveReader.java) nhận tối đa 16 MiB gzip và 12 MiB dữ liệu giải nén theo mặc định. Archive chỉ chứa đúng ba file thường ở root: `map.yaml`, `map.pgm`, `manifest.json`; không nhận symlink, hardlink, directory, PAX/GNU extension, prefix path hoặc tên `./map.yaml`. Pi cần tạo tar regular/USTAR với các tên này. Không dùng tên trong archive làm đường dẫn giải nén.

- `X-Archive-SHA256`: SHA-256 toàn bộ bytes gzip nhận được.
- `X-Asset-SHA256`: SHA-256(raw YAML + một byte NUL + raw PGM).
- Manifest kiểm theo bundled `common.schema.json`/artifact, đủ metadata và không dư field; artifact ID/hash phải khớp request.
- YAML dùng SafeConstructor, chặn duplicate key, collection alias và object tag; image phải là `map.pgm`. Kiểm resolution, origin ba số, negate, threshold và mode.
- PGM hỗ trợ P5/P2 uint8 (`maxval=255`), tối đa 4 triệu cells; kiểm đủ raster/sample, không nuốt bytes pixel là whitespace. YAML/PGM/manifest phải khớp kích thước, resolution, origin và giới hạn DB.

[MappingArtifactService](../src/main/java/com/mediassistrover/backend/mapping/MappingArtifactService.java) khóa Job rồi robot/session, kiểm device token version/thời hạn, UUID được cấp và SAVE đã publish/STARTED. Upload lần đầu phải còn trong SAVE được chọn và Job chưa cancel. Retry đúng body/hash được trả lại receipt, kể cả sau khi phiên đã đóng. Đổi archive hash hoặc asset hash cho cùng artifact ID bị conflict; Pi cần retry cùng file gzip, không đóng gói lại với gzip timestamp khác.

[MappingArtifactStorage](../src/main/java/com/mediassistrover/backend/mapping/MappingArtifactStorage.java) lưu blob immutable theo UUID/hash, force file và atomic rename trước khi ghi receipt DB. Unix force thêm directory; Windows dùng file force và atomic rename. Storage phải là ổ lưu bền vững và hỗ trợ atomic move. DB rollback có thể để lại blob orphan; retry tái dùng blob nếu hash đúng, không tạo receipt giả. Việc dọn orphan chưa tự động; cần đối chiếu DB và lưu trữ khi vận hành. Không trả stored=true khi file bị mất/sai hash.

Sau transaction commit, HTTP trả đúng:

```json
{"stored":true,"artifactId":"UUID_BE_DA_CAP","assetHash":"SHA256_YAML_NUL_PGM"}
```

SAVE `uploaded=true` yêu cầu đã có receipt DB. Kết quả SAVE phải khớp manifest đã lưu. `uploaded=false` là kết quả lưu local hợp lệ nhưng chưa đủ COMMIT; template sẽ fail COMMIT và đóng phiên nếu chưa có upload. Muốn chờ upload muộn cần cấu hình WAIT riêng trước COMMIT, không tự suy file đã ở BE.

[MappingCommitStepHandler](../src/main/java/com/mediassistrover/backend/mapping/MappingCommitStepHandler.java) xử lý DATA_SERVICE/COMMIT_MAP_ARTIFACT, tách khỏi generic DATA_SERVICE handler. Kiểm Job/session/artifact, SAVE SUCCEEDED, file thật và hash, floor active/đúng input Job. BE cấp mapCode từ artifact UUID và versionNo=1 cho map mới, giữ Map DRAFT. Commit lại cùng artifact trả cùng map_id với created=false; đổi floor/name bị conflict. Output giữ request_id và data `{map_id,artifact_id,asset_hash,created}`. Không active Map hoặc đổi robot currentMap tự động.

## Hủy, timeout và recovery

- Khi action đang chạy, cơ chế CANCEL_STEP hiện có yêu cầu stop confirmation trước khi tiến tới STOP phiên.
- Khi đang WAIT/UI mà phiên mở, orchestrator chọn visit STOP PENDING chưa từng thực thi, gắn mapping_cleanup và giữ ý định terminal gốc. Không quét mọi PENDING.
- Trong phiên mở, orchestrator dành một slot maxTotalVisits cho STOP. Cleanup tái dùng visit STOP đầu tiên, giữ maxVisits và operationKey đã cấp; không phát lại một STOP đã thất bại.
- Job giữ RUNNING và robot BUSY tới khi STOP SUCCEEDED, output stopped=true và physicalStopConfirmed=true. Cleanup vẫn được phép khi dispatchEnabled đã tắt hoặc deadline Job đã qua; enabled/enrollment, phiên MQTT và stop timeout vẫn được kiểm.
- STOP lỗi/timeout, thiếu xác nhận dừng, phiên lạ, process Pi thay đổi hoặc mất session đã thiết lập dẫn tới RECOVERY_REQUIRED. Mất mạng trong WAIT cũng giữ Job/robot vì SLAM có thể còn mở.
- Heartbeat `mappingSession={}` không tự làm robot BUSY; session_open=false không thay thế receipt STOP. Recovery dùng API đối soát operation hiện có, journal terminal và heartbeat/HELLO không báo phiên mở; staff review không đổi hardware verified thành true.

## Preview và phối hợp ngoài repo

[MappingPreviewService](../src/main/java/com/mediassistrover/backend/mapping/MappingPreviewService.java) chỉ giữ một frame mới nhất mỗi robot, TTL 10s, đúng session BE/transport/robot. Kiểm timestamp, revision, zlib/base64, grid tối đa 4 triệu cells và occupancy -1..100. Frame không qua inbox durable, không có EVENT_ACK và không ghi từng frame xuống MySQL. Topic giữ contract Phase 0: `mar/robots/{robotId}/map-preview`, QoS 0, retain=false. SSE phát mapping-preview; REST session hỗ trợ lấy lại frame mới nhất khi reconnect.

Cần Pi/UI xác nhận trước khi bật:

- Pi chạy SLAM/TF/safety, dừng Nav2 khi START; gửi mappingSession đúng Job/session, currentMapId/mapAssetHash null khi quét. SAVE upload bằng HTTP Bearer riêng, dùng USTAR và kiểm receipt; STOP thực sự đóng phiên.
- UI tạo Job bằng workflow đã publish, subscribe SSE/render occupancy bottom-up, gửi Save event/cancel và xử lý recovery. Remote W/A/D từ UI vẫn chưa có contract; không gửi velocity bằng EXECUTE_STEP.
- Pi dùng REST/profile Phase 11 để tải và đối chiếu asset mới; operator định vị/commission trước khi active Map.
- Broker host, TLS/ACL/credential, robot/floor/map thật, code Pi/UI, file thật và storage production chưa được xác nhận trong workspace.

## Cấu hình và kiểm thử

Mapping và MQTT/dispatch mặc định **tắt**. Sau khi cấu hình và nghiệm thu dependency, dùng `MAPPING_ENABLED`, `MAPPING_STORAGE_DIR`, `MAPPING_START_TIMEOUT_SEC` cùng cấu hình MQTT hiện có; enabledStepTypes phải có EXECUTE_SENSING. Storage có thể đặt trên persistent volume, không lưu secret vào repo. Giới hạn bytes dùng `MAPPING_MAX_ARCHIVE_BYTES`/`MAPPING_MAX_UNCOMPRESSED_BYTES`.

- [MappingArchiveReaderTest](../src/test/java/com/mediassistrover/backend/MappingArchiveReaderTest.java): hash/raw pixels, archive path/link/duplicate, YAML tag/duplicate, metadata/raster/size và storage retry/integrity.
- [MappingRuntimeIntegrationTest](../src/test/java/com/mediassistrover/backend/MappingRuntimeIntegrationTest.java): luồng mapping, HTTP device auth/receipt, upload duplicate/conflict/scope, SAVE local/receipt mismatch, Map DRAFT/commit idempotent, cancel idle/active, timeout, disconnect/process/session recovery, preview transient và correlation UI.
- [Phase26MappingMigrationIntegrationTest](../src/test/java/com/mediassistrover/backend/Phase26MappingMigrationIntegrationTest.java): nâng V25 → V27 trên schema UUID tạm, giữ graph hiện có, thêm draft và validate migration. Chỉ chạy với TEST_DB_URL local prefix `be_mar_phase12_`; không clean DB ứng dụng.

Kiểm Java 21/MySQL local 9.5 trên DB test riêng `be_mar_phase12_test_20261012`: nhóm cuối **186/186 test đạt**, log `target/phase12-final-targeted-tests.log`. Gồm 12 archive, 25 mapping runtime, 1 migration và hồi quy MQTT/runtime/cancel/group/orchestration/resolver/contract/schema/delivery; input validator có 36 cases, gồm timeout/action/UUID/schema mapping mới. Suite trước sửa cuối có 182 test đạt; không cộng hai nhóm vì có overlap.

Kiểm contract offline **9 schema/131 fixture đạt** (88 positive, 43 negative); 11 StepType giữ nguyên. Đã kiểm UTF-8, liên kết file và diff. Chưa chạy lại toàn suite trong Phase 12; lỗi analytics MySQL 9.5 và skip Docker đã ghi tại Phase 11 không được xem là đã sửa ở đây. Không thay nghiệm thu robot bằng unit/DB/transport mock. Năm tài liệu nguồn được giữ nguyên.
