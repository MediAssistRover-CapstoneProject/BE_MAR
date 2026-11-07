(function (root, factory) {
  const navigation = factory();
  if (typeof module === 'object' && module.exports) module.exports = navigation;
  else root.SavedMapNavigation = navigation;
})(globalThis, function () {
  'use strict';
  const DEFAULT_MAP = 'e571fe71-d3ea-4aa5-a673-ffd857ac9f09';
  const UUID = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const angle = value => Math.atan2(Math.sin(value), Math.cos(value));
  const stable = value => JSON.stringify(value, function (key, item) {
    return item && typeof item === 'object' && !Array.isArray(item)
      ? Object.keys(item).sort().reduce((out, name) => (out[name] = item[name], out), {}) : item;
  });
  function geometry(meta) {
    if (!meta || !UUID.test(meta.map_id || '') || !/^[a-f0-9]{64}$/i.test(meta.asset_hash || '') ||
        ![meta.width_px, meta.height_px].every(v => Number.isInteger(v) && v > 0) ||
        ![meta.resolution_m_per_pixel, meta.origin_x_m, meta.origin_y_m, meta.origin_yaw_rad].every(Number.isFinite) ||
        meta.resolution_m_per_pixel <= 0 || meta.width_px * meta.height_px > 4000000 || !meta.frame_id) {
      throw Error('Metadata map thiếu hoặc không hợp lệ.');
    }
    return meta;
  }
  // u/v are continuous PNG coordinates measured from the top-left image edge.
  // A click snaps to a cell centre (column + .5, row + .5), including rotated origins.
  function pixelToMap(meta, u, v) {
    const r = meta.resolution_m_per_pixel, c = Math.cos(meta.origin_yaw_rad), s = Math.sin(meta.origin_yaw_rad);
    const x = u * r, y = (meta.height_px - v) * r;
    return {xM: meta.origin_x_m + c * x - s * y, yM: meta.origin_y_m + s * x + c * y};
  }
  function mapToPixel(meta, xM, yM) {
    const c = Math.cos(meta.origin_yaw_rad), s = Math.sin(meta.origin_yaw_rad);
    const x = xM - meta.origin_x_m, y = yM - meta.origin_y_m, r = meta.resolution_m_per_pixel;
    return {u: (c * x + s * y) / r, v: meta.height_px - (-s * x + c * y) / r};
  }
  function inside(meta, x, y) {
    if (![x, y].every(Number.isFinite)) return false;
    const p = mapToPixel(meta, x, y);
    return p.u >= 0 && p.v >= 0 && p.u < meta.width_px && p.v < meta.height_px;
  }
  // Display pixels are independent of occupancy cells and metric coordinates.
  function renderSize(meta, cssWidth, deviceRatio, originalCells) {
    const width = meta.width_px, height = meta.height_px;
    const ratio = Number.isFinite(deviceRatio) ? Math.max(1, Math.min(2, deviceRatio)) : 1;
    const requested = originalCells ? 1 : (cssWidth > 0 ? cssWidth : width) * ratio / width;
    const scale = Math.min(requested, Math.sqrt(4000000 / (width * height)), 8192 / width, 8192 / height);
    return {width: Math.max(1, Math.floor(width * scale)), height: Math.max(1, Math.floor(height * scale))};
  }
  // Rotate only the view. Both pointer picking and markers share this transform.
  function viewTransform(width, height, degrees) {
    const radians = (Number.isFinite(degrees) ? degrees : 0) * Math.PI / 180;
    const c = Math.cos(radians), s = Math.sin(radians);
    const w = Math.abs(c) * width + Math.abs(s) * height;
    const h = Math.abs(s) * width + Math.abs(c) * height;
    return {width:w, height:h, radians,
      forward(u,v) { const x=u-width/2,y=v-height/2; return {u:c*x-s*y+w/2,v:s*x+c*y+h/2}; },
      inverse(u,v) { const x=u-w/2,y=v-h/2; return {u:c*x+s*y+width/2,v:-s*x+c*y+height/2}; }
    };
  }
  function freshPose(snapshot, meta, receivedAgeMs) {
    const pose = snapshot?.latestPose, robot = snapshot?.robot;
    const age = Date.parse(snapshot?.observedAt) - Date.parse(pose?.capturedAt);
    const session=snapshot?.navigationSession;
    if(session && session.state!=='NONE' && (session.state!=='READY'||session.mapId!==meta.map_id||session.mapAssetHash!==meta.asset_hash))return null;
    if (!snapshot?.mqttConnected || !snapshot.heartbeatFresh || pose?.fresh !== true ||
        pose.robotId !== robot?.robotId || robot.currentMapId !== meta.map_id ||
        robot.mapAssetHash?.toLowerCase() !== meta.asset_hash.toLowerCase() ||
        pose.mapId !== meta.map_id || pose.frameId !== meta.frame_id ||
        !Number.isFinite(age) || age < -1000 || age > 5000 || receivedAgeMs < 0 || receivedAgeMs > 5000 ||
        Math.max(0, age) + receivedAgeMs > 5000 ||
        ![pose.xM, pose.yM, pose.yawRad].every(Number.isFinite) || !inside(meta, pose.xM, pose.yM)) return null;
    return pose;
  }
  function poseView(snapshot, meta, receivedAgeMs, nowMs, memory) {
    const session=snapshot?.navigationSession;
    const scope=snapshot ? stable([snapshot.robot?.robotId,meta.map_id,meta.asset_hash,meta.frame_id,
      session?.sessionId??null,session?.initialization?.revision??0]) : null;
    if(memory && (memory.mapId!==meta.map_id || memory.hash!==meta.asset_hash || memory.frame!==meta.frame_id ||
        scope!==null && memory.scope!==scope || session && ['NONE','CLOSED','EXPIRED'].includes(session.state)))memory=null;
    const current=freshPose(snapshot,meta,receivedAgeMs);
    if(current){
      const ageMs=Math.max(0,Date.parse(snapshot.observedAt)-Date.parse(current.capturedAt))+receivedAgeMs;
      memory={scope,mapId:meta.map_id,hash:meta.asset_hash,frame:meta.frame_id,pose:{...current},at:nowMs-ageMs};
      return {memory,pose:current,live:true,ageMs};
    }
    const ageMs=memory ? nowMs-memory.at : Infinity;
    if(!Number.isFinite(ageMs)||ageMs<0||ageMs>600000)memory=null;
    return {memory,pose:memory?.pose??null,live:false,ageMs};
  }
  function plannedPath(snapshot, meta) {
    const plan=snapshot?.navigationPlan, session=snapshot?.navigationSession;
    if (!plan || !session || plan.sessionId!==session.sessionId ||
        plan.mapId!==meta.map_id || plan.mapAssetHash?.toLowerCase()!==meta.asset_hash.toLowerCase() ||
        plan.frameId!==meta.frame_id || plan.initializationRevision!==session.initialization?.revision ||
        plan.jobId!==snapshot.activeJobId || plan.jobStepId!==snapshot.selectedJobStepId ||
        snapshot.selectedStepType!=='MOVE' || !Array.isArray(plan.points) ||
        plan.points.length<2 || plan.points.length>256 ||
        !plan.points.every(p=>Number.isFinite(p.xM)&&Number.isFinite(p.yM)&&inside(meta,p.xM,p.yM)))return null;
    return plan;
  }
  function liveNavigationPose(sample,meta,snapshot,receivedAgeMs){
    const session=snapshot?.navigationSession,pose=sample?.pose;
    const age=Date.parse(sample?.receivedAt)-Date.parse(sample?.capturedAt)+receivedAgeMs;
    if(!sample||sample.messageType!=='NAVIGATION_POSE'||sample.robotId!==snapshot?.robot?.robotId
      ||session?.state!=='READY'||sample.navigationSessionId!==session.sessionId
      ||sample.initializationRevision!==session.initialization?.revision
      ||sample.mapId!==meta.map_id||sample.mapAssetHash!==meta.asset_hash
      ||session.mapId!==meta.map_id||session.mapAssetHash!==meta.asset_hash
      ||!Number.isFinite(age)||age< -1000||age>1500||receivedAgeMs<0
      ||![pose?.xM,pose?.yM,pose?.yawRad].every(Number.isFinite)||!inside(meta,pose.xM,pose.yM))return null;
    return {pose:{...pose,robotId:sample.robotId,mapId:sample.mapId,frameId:meta.frame_id,capturedAt:sample.capturedAt},ageMs:Math.max(0,age)};
  }
  function currentNavigationPose(snapshot,meta,snapshotAgeMs,sample,sampleAgeMs){
    // The UI must not wait for location-history SQL while the authenticated
    // stream already carries a newer pose in the same localized session.
    // This selects an observation for UI preflight only: it cannot renew the
    // heartbeat, change READY/dispatchReady or authorize physical motion.
    if(snapshot?.mqttConnected&&snapshot.heartbeatFresh&&snapshot.robot?.currentMapId===meta.map_id
        &&snapshot.robot.mapAssetHash?.toLowerCase()===meta.asset_hash.toLowerCase()){
      const live=liveNavigationPose(sample,meta,snapshot,sampleAgeMs);
      if(live)return live.pose;
    }
    return freshPose(snapshot,meta,snapshotAgeMs);
  }
  async function consumeRobotEvents(response,onSnapshot,eventName='robot-state',maxLength=1048576){
    if(!response.body)throw Error('Trình duyệt không hỗ trợ stream trạng thái.');
    const reader=response.body.getReader(),decoder=new TextDecoder();let pending='';
    try{while(true){const {value,done}=await reader.read();if(done)break;
      pending+=decoder.decode(value,{stream:true});
      if(pending.length>maxLength)throw Error('Stream trạng thái vượt giới hạn.');
      let match;while((match=/\r?\n\r?\n/.exec(pending))){
        const block=pending.slice(0,match.index);pending=pending.slice(match.index+match[0].length);
        const lines=block.split(/\r?\n/),event=lines.find(x=>x.startsWith('event:'))?.slice(6).trim();
        const data=lines.filter(x=>x.startsWith('data:')).map(x=>x.slice(5).replace(/^ /,'')).join('\n');
        if((Array.isArray(eventName)?eventName.includes(event):event===eventName)&&data)onSnapshot(JSON.parse(data),event);
      }
    }}finally{await reader.cancel().catch(()=>{});reader.releaseLock();}
  }
  function mount(options) {
    const host = options.element;
    host.innerHTML = `
      <h2>Endpoint và dẫn đường trên map đã lưu</h2>
      <p>Chọn vị trí và hướng trên map. BE thực thi workflow có step <code>MOVE</code> qua MQTT; Pi dùng Nav2 để đi tới đích.</p>
      <div class="nav-inline"><label>Map ID<input id="navMapId" value="${DEFAULT_MAP}"></label>
      <button id="navLoad">Tải map và endpoint</button><button id="navUseSaved">Dùng map vừa lưu ở trên</button></div>
      <small id="navMapInfo">Dùng token ADMIN ở đầu trang. Xem và đặt điểm không tự chạy robot.</small>
      <button id="navActivate" disabled>Kích hoạt map trên BE</button>
      <button id="navNormalize" disabled>Tạo bản map sửa ô xám cho Nav2</button>
      <button id="navAssign" disabled>Mở phiên dùng map tạm cho robot</button>
      <button id="navCloseSession" disabled>Đóng phiên map tạm</button>
      <button id="navRecheck">Kiểm tra lại phiên và job</button>
      <small id="navSessionReadiness" role="status"></small>
      <small>Map và endpoint nằm trong thư viện; robot dùng map này trong phiên 2 giờ, ràng buộc với một lần chạy Pi. Đổi nơi test: đóng phiên rồi chọn map khác.</small>
      <pre id="navPiCommand">Mở phiên để lấy lệnh khởi động Pi.</pre>
      <p id="navSessionStatus" role="status">Chưa có phiên định vị.</p>
      <h3>Khởi tạo vị trí robot</h3>
      <label><input type="checkbox" id="navSeedMode"> Bấm/kéo trên map để đặt vị trí và hướng KHỞI TẠO, thay vì endpoint</label>
      <div class="fields"><label>X dự kiến (m)<input id="navSeedX" type="number" step="any"></label><label>Y dự kiến (m)<input id="navSeedY" type="number" step="any"></label>
      <label>Hướng dự kiến (độ)<input id="navSeedYaw" type="number" min="-180" max="180" step="1" value="0"></label></div>
      <button id="navSendSeed" disabled>Gửi vị trí khởi tạo cho AMCL</button><button id="navGlobal" disabled>Yêu cầu tự tìm vị trí toàn cục</button>
      <small>Mũi tên khởi tạo là ước lượng do người dùng đặt. Chỉ mũi tên xanh khi Pi xác minh READY mới là vị trí robot. Phòng đối xứng hoặc ít đặc trưng có thể cần đặt vị trí khởi tạo.</small>
      <div class="nav-layout"><div>
        <label class="nav-render-option"><input id="navOriginalCells" type="checkbox"> Xem ô bản đồ gốc</label>
        <small>Nét hiển thị mượt mặc định. Tọa độ và dữ liệu dẫn đường dùng bản đồ gốc.</small>
        <label>Góc xoay hiển thị (độ)<input id="navViewAngle" type="number" min="-180" max="180" step="1" value="0"></label>
        <small>Xoay để căn tường theo chiều ngang. Chỉ đổi góc nhìn; tọa độ endpoint, hướng robot và file map giữ nguyên.</small>
        <div class="nav-map-wrap">
          <canvas id="navCanvas" width="640" height="360" hidden aria-label="Bản đồ chỉ đường trong nhà"></canvas>
          <div id="navGuidance" class="nav-guidance" role="status" hidden>
            <span id="navGuidanceIcon" class="nav-guidance-icon" aria-hidden="true">↑</span>
            <div><strong id="navGuidanceText"></strong><small id="navGuidanceDestination"></small></div>
          </div>
          <div id="navMapTools" class="nav-map-tools" hidden>
            <button id="navZoomIn" aria-label="Phóng to bản đồ">＋</button>
            <button id="navZoomOut" aria-label="Thu nhỏ bản đồ">−</button>
            <button id="navFollow">Về vị trí robot</button>
          </div>
        </div>
        <div class="nav-legend"><span style="--marker:#0879ee">Robot còn dữ liệu mới</span><span style="--marker:#777">Vị trí cuối đang chờ định vị</span><span style="--marker:#157347">Endpoint đã lưu</span><span style="--marker:#dc7400">Điểm đang đặt</span><span style="--marker:#2563eb">Đường Nav2 dự kiến</span></div>
        <small id="navPlanStatus">Chưa có đường Nav2 cho MOVE hiện tại.</small>
        <div id="navPose" class="nav-status" role="status">Chưa có vị trí robot trên map này.</div>
        <small id="navRobotStatus">Chưa theo dõi robot.</small>
      </div><div>
        <label>Endpoint đã lưu<select id="navEndpoints"><option value="">Chọn endpoint</option></select></label>
        <button id="navNewPoint">Đặt endpoint mới</button>
        <small>Đặt điểm mới: bấm trên vùng sàn đã quét, kéo rồi thả để chọn hướng. Chọn endpoint trong danh sách để sửa; thêm MOVE ở phần workflow để chạy.</small>
        <label>Mã endpoint<input id="navPointCode" maxlength="64" placeholder="DEMO_POINT_A"></label>
        <label>Tên endpoint<input id="navPointName" maxlength="160" placeholder="Điểm trước cửa phòng"></label>
        <label>Loại endpoint<select id="navPointType"><option value="WAYPOINT">Waypoint — điểm đến</option><option value="PARK">Parking — vị trí đỗ / khởi tạo</option><option value="CHARGER">Manual Charger — vị trí sạc thủ công</option><option value="STAGING" hidden>Staging — điểm chờ cũ</option></select></label>
        <label>Zone gắn với endpoint<select id="navPointZone"><option value="">Không gắn zone</option></select></label>
        <div class="fields"><label>X (m)<input id="navX" type="number" step="any"></label><label>Y (m)<input id="navY" type="number" step="any"></label></div>
        <label>Hướng thân robot trên map (độ)<input id="navYaw" type="number" step="1" min="-180" max="180" value="0"></label>
        <small>0° theo +X map; 90° theo +Y. Mũi tên thể hiện hướng thân robot; cần hiệu chuẩn góc lắp camera.</small>
        <button id="navSavePoint" disabled>Lưu endpoint</button>
        <button id="navDeletePoint" disabled>Xóa endpoint đã chọn</button>
        <small>Xóa sẽ ngưng sử dụng và ẩn endpoint khỏi map/danh sách MOVE; lịch sử nhiệm vụ được giữ lại.</small>
        <small id="navPointId">CHARGER chỉ là điểm đến để cắm sạc thủ công; không tự xác nhận đang sạc.</small>
        <label class="nav-render-option"><input id="navAtParking" type="checkbox"> Robot thật đang ở đúng parking đã chọn</label>
        <button id="navParkingSeed" disabled>Dùng parking làm vị trí khởi tạo</button>
      </div></div>
      <h3>Zone trên map</h3>
      <p>Vẽ đa giác bằng cách bấm từng đỉnh trên map. Restricted zone cấm toàn bộ thân/thùng đi vào; Speed limit zone giới hạn vận tốc tịnh tiến của MOVE, kể cả khi dịch ngang thoát góc.</p>
      <div class="nav-inline"><label>Zone đã lưu<select id="navZones"><option value="">Chọn zone</option></select></label><button id="navNewZone">Zone mới</button><button id="navReloadZones">Tải lại zone</button></div>
      <label class="nav-render-option"><input id="navZoneMode" type="checkbox"> Bấm map để vẽ zone (tạm ngừng đặt endpoint)</label>
      <div class="fields"><label>Mã zone<input id="navZoneCode" maxlength="64"></label><label>Tên zone<input id="navZoneName" maxlength="160"></label>
      <label>Rule<select id="navZoneType"><option value="SPEED">Speed limit zone</option><option value="KEEP_OUT">Restricted zone</option></select></label>
      <label>Giới hạn tốc độ (m/s)<input id="navZoneSpeed" type="number" min="0.01" max="0.8" step="0.01" value="0.15"></label></div>
      <label class="nav-render-option"><input id="navZoneActive" type="checkbox" checked> Bật zone — với zone đã lưu, thay đổi được gửi ngay tới BE</label>
      <label>Các đỉnh [x,y] theo mét trên map<textarea id="navZonePoints" rows="3" style="width:100%" placeholder="[[0,0],[1,0],[1,1],[0,1]]"></textarea></label>
      <button id="navZoneUndo">Bỏ đỉnh cuối</button><button id="navSaveZone">Lưu zone</button><button id="navToggleZone">Bật/tắt zone đã chọn</button>
      <small id="navZoneStatus">Tải map để cấu hình zone. Rule được Pi đồng bộ khi rảnh, trước MOVE tiếp theo.</small>
      <small id="navZoneSync">Chưa có xác nhận cấu hình từ Pi.</small>
      <details><summary>JSON zone để dùng Swagger</summary><pre id="navZoneSwagger">Chưa chọn zone.</pre></details>
      <button id="navRefresh">Cập nhật vị trí robot</button>
      <small>Chọn và lưu endpoint/zone tại đây, rồi thêm MOVE vào workflow bên dưới. Có thể lặp lại endpoint để robot quay về.</small>
      <p id="navAction" class="nav-status" role="status">Tải map để bắt đầu.</p>
      <details><summary>JSON endpoint để dùng Swagger</summary><pre id="navSwagger">Chọn vị trí trên map để lấy JSON.</pre></details>`;
    const $ = id => host.querySelector('#' + id), canvas = $('navCanvas'), ctx = canvas.getContext('2d');
    const state = {meta: null, map: null, image: null, imageUrl: null, endpoints: [], draft: null, editing: null,
      snapshot: null, receivedAt: 0, robotId: '', busy: false, generation: 0, timer: null, closed: false,
      dragging: null,seed:null,route:null,routeAt:0,follow:true,zoom:1.8,viewCenter:null,pan:null,routeKey:null,
      zones:[],zoneEditing:null,zonePoints:[],zoneSyncAt:0,zoneAck:null};
    const say = (message, error = false) => { $('navAction').textContent = message; $('navAction').classList.toggle('nav-error', error); };
    const active = () => !!state.snapshot?.activeJobId || !!options.isWorkflowActive?.();
    function robotId() {
      const selectedId=options.getRobotId().trim(),id=state.robotId||selectedId;
      if(selectedId!==id&&!active())throw Error('Robot ID đã đổi; tải lại map để theo dõi đúng robot.');
      if (!UUID.test(id)) throw Error('Robot ID không hợp lệ.'); return id;
    }
    function mapId() { if (!state.meta || $('navMapId').value.trim() !== state.meta.map_id) throw Error('Tải lại Map ID đã chọn trước.'); return state.meta.map_id; }
    async function api(path, {method = 'GET', body, raw = false, headers = {}} = {}) {
      const token = options.getToken().trim().replace(/^Bearer\s+/i, '');
      if (!token) throw Error('Cần access token ADMIN ở đầu trang.');
      const response = await fetch(path, {method, headers: {Authorization: 'Bearer ' + token,
        ...(body === undefined ? {} : {'Content-Type': 'application/json'}), ...headers},
        body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store', signal: AbortSignal.timeout(10000)});
      if (!response.ok) { const error = Error(response.status + ' ' + (await response.text()).slice(0, 500)); error.status = response.status; throw error; }
      return raw ? response : response.status === 204 ? null : response.json();
    }
    async function pages(path) {
      const result = [];
      for (let page = 0; page < 100; page++) {
        const data = await api(path + (path.includes('?') ? '&' : '?') + 'page=' + page + '&size=100');
        if (!Array.isArray(data.content)) throw Error('BE trả danh sách không hợp lệ.');
        result.push(...data.content);
        if (data.last === true || page + 1 >= data.totalPages) return result;
      }
      throw Error('Danh sách quá lớn; chưa tải đủ để thao tác chính xác.');
    }
    function selected() { return state.endpoints.find(e => e.endpointId === $('navEndpoints').value); }
    function pose() { return state.meta ? currentNavigationPose(state.snapshot,state.meta,performance.now()-state.receivedAt,
      state.livePose?.value,performance.now()-(state.livePose?.at||0)) : null; }
    function controls() {
      const loaded = !!state.meta && $('navMapId').value.trim() === state.meta.map_id;
      const locked = state.busy || !!active();
      $('navLoad').disabled = state.busy || !!(active() && state.meta);
      $('navUseSaved').disabled = locked;
      $('navMapId').disabled = locked;
      $('navActivate').disabled = !loaded || locked || state.map?.status === 'ACTIVE';
      $('navAssign').disabled = !loaded || locked || state.map?.status !== 'ACTIVE';
      const session = state.snapshot?.navigationSession;
      const sessionOpen = !!session && ['WAITING_FOR_PI', 'LOCALIZING', 'READY'].includes(session.state);
      const open = sessionOpen && session.mapId === state.meta?.map_id;
      $('navAssign').disabled ||= sessionOpen || state.meta?.navigation_reload_safe===false;
      $('navNormalize').disabled=!loaded||locked||state.meta?.navigation_reload_safe!==false;
      $('navCloseSession').disabled=locked||!sessionOpen;
      $('navRecheck').disabled=state.busy;
      $('navSessionReadiness').textContent=state.busy?'Đang xử lý yêu cầu trên BE…':active()?
        'Robot đang có workflow/job. Theo dõi hoặc hủy ở phần Chạy workflow bên dưới.':
        sessionOpen&&!open?'Robot đang mở phiên map khác. Bấm Đóng phiên map tạm trước khi mở map này.':
        !loaded?'Tải map để mở phiên.':open?'Phiên đang mở; dùng lệnh Pi bên dưới.':
        state.meta?.navigation_reload_safe===false?'Map cần sửa ô xám trước khi mở phiên.':
        state.map?.status!=='ACTIVE'?'Kích hoạt map trước khi mở phiên.':'Có thể mở phiên mới; phiên cũ đã đóng hoặc chưa có phiên.';
      $('navSendSeed').disabled=locked||!open||!state.seed;
      $('navGlobal').disabled=locked||!open;
      for(const id of ['navSeedMode','navSeedX','navSeedY','navSeedYaw'])$(id).disabled=!loaded||locked;
      for (const id of ['navNewPoint', 'navEndpoints', 'navPointCode', 'navPointName', 'navX', 'navY', 'navYaw']) $(id).disabled = !loaded || locked;
      $('navSavePoint').disabled = !loaded || locked || !state.draft;
      $('navDeletePoint').disabled = !loaded || locked || !selected()?.active;
      for(const id of ['navPointType','navPointZone','navAtParking','navNewZone','navReloadZones','navZoneMode','navZones','navZoneCode','navZoneName','navZoneType','navZoneActive','navZonePoints','navZoneUndo','navSaveZone'])$(id).disabled=!loaded||locked;
      $('navZoneSpeed').disabled=!loaded||locked||$('navZoneType').value!=='SPEED';
      $('navToggleZone').disabled=!loaded||locked||!state.zoneEditing;
      $('navParkingSeed').disabled=!loaded||locked||selected()?.endpointType!=='PARK'||!$('navAtParking').checked;
      options.onContextChange?.(state);
    }
    async function mutate(operation) {
      if (state.busy) return;
      state.busy = true; controls();
      try { await operation(); } catch (error) { say(error.message, true); }
      finally { state.busy = false; controls(); }
    }
    function endpointBody() {
      mapId();
      if (['navX', 'navY', 'navYaw'].some(id => $(id).value.trim() === '')) throw Error('Cần X, Y và hướng endpoint.');
      const xM = Number($('navX').value), yM = Number($('navY').value), degrees = Number($('navYaw').value);
      if (!Number.isFinite(degrees) || degrees < -180 || degrees > 180 || !inside(state.meta, xM, yM)) throw Error('Điểm phải nằm trong map; hướng từ -180° đến 180°.');
      const old = state.editing;
      return {mapId: state.meta.map_id, zoneId: $('navPointZone').value || null, roomId: old?.roomId ?? null,
        endpointCode: $('navPointCode').value.trim(), endpointName: $('navPointName').value.trim(),
        endpointType: $('navPointType').value, xM, yM, yawRad: angle(degrees * Math.PI / 180),
        active: old?.active ?? true, accessible: old?.accessible ?? true, publicVisible: old?.publicVisible ?? false,
        description: old?.description ?? 'Đặt trên map đã lưu qua trang điều khiển'};
    }
    function updateDraft() {
      if (!state.meta) return;
      try { const body = endpointBody(); state.draft = body; $('navSwagger').textContent = JSON.stringify(body, null, 2); }
      catch (error) { state.draft = null; $('navSwagger').textContent = error.message; }
      controls(); draw();
    }
    function arrow(x, y, yaw, color, label, radius) {
      const p = mapToPixel(state.meta, x, y), direction = -(yaw - state.meta.origin_yaw_rad);
      ctx.save(); ctx.translate(p.u, p.v); ctx.rotate(direction);
      ctx.fillStyle = color; ctx.strokeStyle = '#fff'; ctx.lineWidth = .6;
      ctx.beginPath(); ctx.arc(0, 0, radius, 0, 2 * Math.PI); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(radius * 2, 0); ctx.lineTo(radius * .2, -radius * .7); ctx.lineTo(radius * .2, radius * .7); ctx.closePath(); ctx.fill(); ctx.stroke();
      ctx.restore();
      if (label) { ctx.fillStyle = color; ctx.font = Math.max(3, radius) + 'px system-ui'; ctx.fillText(label, p.u + radius + 1, p.v - radius - 1); }
    }
    function draw() {
      if (!state.image || !state.meta) return;
      const original = $('navOriginalCells').checked;
      const transform = viewTransform(state.meta.width_px,state.meta.height_px,Number($('navViewAngle').value));
      canvas.style.width = 'min(100%, ' + Math.max(180,650*transform.width/transform.height) + 'px)';
      const size = renderSize({width_px:transform.width,height_px:transform.height}, canvas.getBoundingClientRect().width, window.devicePixelRatio, original);
      if (canvas.width !== size.width || canvas.height !== size.height) {
        canvas.width = size.width; canvas.height = size.height;
      }
      canvas.style.imageRendering = original ? 'pixelated' : 'auto';
      ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.setTransform(canvas.width / transform.width, 0, 0, canvas.height / transform.height, 0, 0);
      const guidance=currentGuidance();
      const plan=guidance?.plan||plannedPath(state.snapshot,state.meta);
      const navigating=!!plan;
      const zoom=navigating?state.zoom:1;
      const currentPose=pose();
      if(navigating&&state.follow&&currentPose){
        const pixel=mapToPixel(state.meta,currentPose.xM,currentPose.yM);
        state.viewCenter=transform.forward(pixel.u,pixel.v);
      }
      const center=navigating&&state.viewCenter?state.viewCenter:{u:transform.width/2,v:transform.height/2};
      state.camera={zoom,center,transform};
      ctx.translate(transform.width/2,transform.height/2);ctx.scale(zoom,zoom);ctx.translate(-center.u,-center.v);
      ctx.translate(transform.width/2,transform.height/2); ctx.rotate(transform.radians);
      ctx.translate(-state.meta.width_px/2,-state.meta.height_px/2);
      ctx.imageSmoothingEnabled = !original; ctx.imageSmoothingQuality = 'high';
      ctx.lineJoin = 'round'; ctx.lineCap = 'round';
      ctx.drawImage(state.image, 0, 0, state.meta.width_px, state.meta.height_px);
      function polygon(points,type,label,draft=false){
        if(!Array.isArray(points)||!points.length)return;
        ctx.beginPath();points.forEach((point,i)=>{const p=mapToPixel(state.meta,Number(point[0]),Number(point[1]));if(!i)ctx.moveTo(p.u,p.v);else ctx.lineTo(p.u,p.v);});
        if(points.length>=3)ctx.closePath();ctx.fillStyle=type==='KEEP_OUT'?'rgba(210,30,30,.22)':'rgba(240,170,0,.23)';
        if(points.length>=3)ctx.fill();ctx.strokeStyle=draft?'#7427bd':type==='KEEP_OUT'?'#c22':'#aa7400';ctx.lineWidth=Math.max(.8,.025/state.meta.resolution_m_per_pixel);ctx.stroke();
        const p=mapToPixel(state.meta,points[0][0],points[0][1]);ctx.font='4px system-ui';ctx.fillStyle=ctx.strokeStyle;ctx.fillText(label,p.u,p.v-2);
      }
      for(const z of state.zones)if(z.active&&z.zoneId!==state.zoneEditing?.zoneId)polygon(z.polygonJson?.points||z.polygonJson,z.zoneType,z.zoneCode+(z.zoneType==='SPEED'?' '+z.speedLimitMps+' m/s':''));
      polygon(state.zonePoints,$('navZoneType').value,'Zone đang sửa',true);
      if(plan){
        const line=(points,color,width)=>{
          if(points.length<2)return;
          ctx.beginPath();points.forEach((point,index)=>{const pixel=mapToPixel(state.meta,point.xM,point.yM);
            if(index===0)ctx.moveTo(pixel.u,pixel.v);else ctx.lineTo(pixel.u,pixel.v);});
          ctx.strokeStyle=color;ctx.lineWidth=width;ctx.stroke();
        };
        const width=Math.max(2,.055/state.meta.resolution_m_per_pixel);
        line(plan.points,'#fff',width+2/zoom);
        line(plan.points,'#a9bfd8',width);
        if(guidance?.state==='TRACKING'){
          line([guidance.projection,...plan.points.slice(guidance.segmentIndex+1)],'#1877ed',width);
        }else if(guidance?.state!=='ARRIVED')line(plan.points,'#1877ed',width);
        const destination=plan.points[plan.points.length-1],pin=mapToPixel(state.meta,destination.xM,destination.yM);
        ctx.fillStyle='#d94b3d';ctx.strokeStyle='#fff';ctx.lineWidth=1/zoom;
        ctx.beginPath();ctx.arc(pin.u,pin.v,Math.max(3,.065/state.meta.resolution_m_per_pixel),0,Math.PI*2);ctx.fill();ctx.stroke();
        ctx.font=Math.max(4,12*transform.width/canvas.width/zoom)+'px system-ui';
        ctx.fillStyle='#172c43';ctx.fillText(plan.endpointName||'Điểm đến',pin.u+5/zoom,pin.v-5/zoom);
        if(guidance?.state==='TRACKING'&&guidance.turn){
          const turn=mapToPixel(state.meta,guidance.turn.xM,guidance.turn.yM);
          const radius=Math.max(5,17*transform.width/canvas.width/zoom);
          ctx.fillStyle='#075dc4';ctx.beginPath();ctx.arc(turn.u,turn.v,radius,0,Math.PI*2);ctx.fill();
          ctx.save();ctx.translate(turn.u,turn.v);ctx.rotate(-transform.radians);
          ctx.fillStyle='#fff';ctx.textAlign='center';ctx.textBaseline='middle';ctx.font='bold '+radius*1.5+'px system-ui';
          ctx.fillText(guidanceSymbol(guidance.direction),0,0);ctx.restore();
        }
      }
      $('navGuidance').hidden=!plan;$('navMapTools').hidden=!plan;
      $('navGuidanceText').textContent=guidance?.text||'Đang chờ vị trí robot';
      $('navGuidanceIcon').textContent=guidance?.state==='ARRIVED'?'✓':guidanceSymbol(guidance?.direction);
      $('navGuidanceDestination').textContent=plan?(plan.endpointName||'Điểm đến')+(guidance?.state==='TRACKING'?' · Còn '+guidance.remainingCm+' cm':''):'';
      $('navFollow').classList.toggle('following',state.follow);
      $('navPlanStatus').textContent=plan?'Đường xanh tới đích · đoạn nhạt đã đi · khoảng cách theo cm':'Chưa có đường Nav2 được xác nhận cho MOVE hiện tại.';
      const radius = Math.max(1.5, .07 / state.meta.resolution_m_per_pixel);
      for (const e of state.endpoints) if (e.endpointId!==plan?.endpointId && inside(state.meta, e.xM, e.yM)) arrow(e.xM, e.yM, e.yawRad, e.endpointId === selected()?.endpointId ? '#7427bd' : '#157347', e.endpointCode, radius);
      if (state.draft) arrow(state.draft.xM, state.draft.yM, state.draft.yawRad, '#dc7400', '', radius);
      if(state.seed)arrow(state.seed.xM,state.seed.yM,state.seed.yawRad,'#bd7515','Ước lượng khởi tạo',radius*1.4);
      const view=poseView(state.snapshot,state.meta,performance.now()-state.receivedAt,performance.now(),state.remembered);
      // High-rate observations affect drawing only; they cannot enable MOVE or renew a session.
      const live=liveNavigationPose(state.livePose?.value,state.meta,state.snapshot,performance.now()-(state.livePose?.at||0));
      if(live){view.pose=live.pose;view.live=true;view.ageMs=live.ageMs;view.memory={scope:view.memory?.scope,
        mapId:state.meta.map_id,hash:state.meta.asset_hash,frame:state.meta.frame_id,pose:live.pose,at:performance.now()-live.ageMs};}
      state.remembered=view.memory;
      const current = view.pose;
      if (current) {
        arrow(current.xM, current.yM, current.yawRad, view.live?'#0879ee':'#777', '', radius * 1.6);
        $('navPose').textContent = `Robot ${current.robotId}\nX=${current.xM.toFixed(3)} m · Y=${current.yM.toFixed(3)} m · hướng ${(current.yawRad * 180 / Math.PI).toFixed(1)}°`;
        if(live)$('navPose').textContent+=`\nLuồng vị trí trực tiếp · tuổi mẫu ${Math.round(live.ageMs)} ms (chỉ quan sát).`;
        if(!view.live)$('navPose').textContent+=`\nVị trí cuối ${Math.ceil(view.ageMs/1000)} giây trước; đang chờ định vị mới. Chỉ để xem, MOVE đang khóa.`;
      } else $('navPose').textContent = 'Chưa có vị trí mới, đúng map và hash đã chọn. Cần Pi nạp map, định vị AMCL và gửi pose mới.';
    }
    function guidanceSymbol(direction){return {LEFT:'↰',RIGHT:'↱',UTURN:'↶',STRAIGHT:'↑'}[direction]||'…';}
    function currentGuidance(){
      const route=state.route,plan=route?.plan,session=state.snapshot?.navigationSession;
      if(!plan||!session||plan.sessionId!==session.sessionId||plan.mapId!==state.meta?.map_id
        ||plan.mapAssetHash?.toLowerCase()!==state.meta.asset_hash.toLowerCase()
        ||plan.initializationRevision!==session.initialization?.revision
        ||plan.frameId!==state.meta.frame_id||!Array.isArray(plan.points)||plan.points.length<2
        ||!plan.points.every(p=>Number.isFinite(p.xM)&&Number.isFinite(p.yM)&&inside(state.meta,p.xM,p.yM)))return null;
      if(route.state!=='ARRIVED'&&(plan.jobId!==state.snapshot.activeJobId||plan.jobStepId!==state.snapshot.selectedJobStepId))return null;
      if(route.state==='ARRIVED'&&state.snapshot.activeJobId&&plan.jobId!==state.snapshot.activeJobId)return null;
      const snapshotPlan=state.snapshot.navigationPlan;
      if(snapshotPlan?.jobStepId===plan.jobStepId&&Date.parse(snapshotPlan.plannedAt)>Date.parse(plan.plannedAt))return null;
      if(route.state==='ARRIVED'&&state.snapshot.selectedStepType==='MOVE'&&plan.jobStepId!==state.snapshot.selectedJobStepId)return null;
      if(!['TRACKING','WAITING_FOR_POSE','ARRIVED'].includes(route.state))return null;
      const poseAge=Date.parse(route.observedAt)-Date.parse(route.capturedAt)+performance.now()-state.routeAt;
      if(route.state==='TRACKING'&&(performance.now()-state.routeAt>1200||!pose()||poseAge>1500))
        return {...route,state:'WAITING_FOR_POSE',direction:null,text:'Đang chờ vị trí robot'};
      return route;
    }
    async function refreshGuidance(generation){
      if(!state.snapshot?.navigationSession?.sessionId){state.route=null;return;}
      const requestedRobot=robotId(),started=performance.now();
      const route=await api('/api/v1/robots/'+requestedRobot+'/navigation-guidance');
      if(generation!==state.generation||requestedRobot!==robotId())return;
      if(route.plan?.jobStepId&&route.plan.jobStepId!==state.routeKey){
        state.routeKey=route.plan.jobStepId;state.follow=true;state.zoom=1.8;state.viewCenter=null;
      }
      state.route=route;state.routeAt=started;
    }
    function showEndpoints(id = '') {
      $('navEndpoints').replaceChildren(new Option('Chọn endpoint', ''));
      for (const e of state.endpoints) $('navEndpoints').add(new Option(e.endpointCode + ' — ' + e.endpointName + (e.active && e.accessible ? '' : ' (chưa khả dụng)'), e.endpointId));
      $('navEndpoints').value = id;
    }
    function clearPoint() {
      state.draft = state.editing = null; $('navEndpoints').value = '';
      $('navPointType').value='WAYPOINT';$('navPointZone').value='';$('navAtParking').checked=false;
      for (const id of ['navX', 'navY', 'navPointCode', 'navPointName']) $(id).value = '';
      $('navYaw').value = '0'; $('navPointId').textContent = 'Đang đặt endpoint mới. Bấm rồi kéo trên map để chọn hướng.';
      $('navSwagger').textContent = 'Chọn vị trí trên map để lấy JSON.'; controls(); draw();
    }
    async function endpoints(id = '') {
      state.endpoints = (await pages('/api/v1/endpoints')).filter(e => e.mapId === state.meta.map_id && e.active);
      state.zoneAck=null;state.zoneSyncAt=0;
      showEndpoints(id); draw();
    }
    function clearZone(){state.zoneEditing=null;state.zonePoints=[];$('navZones').value='';$('navZoneCode').value='';$('navZoneName').value='';$('navZoneType').value='SPEED';$('navZoneSpeed').value='.15';$('navZoneActive').checked=true;zoneDraft();}
    function zoneBody(){
      mapId();const points=JSON.parse($('navZonePoints').value||'[]');
      if(!Array.isArray(points)||points.length<3||points.length>256||points.some(p=>!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)||!inside(state.meta,p[0],p[1])))throw Error('Cần 3–256 đỉnh [x,y] nằm trong map.');
      const type=$('navZoneType').value,speed=Number($('navZoneSpeed').value);
      if(type==='SPEED'&&(!Number.isFinite(speed)||speed<=0||speed>.8))throw Error('Tốc độ zone phải lớn hơn 0 và không quá 0,8 m/s.');
      const old=state.zoneEditing;
      return {mapId:mapId(),zoneCode:$('navZoneCode').value.trim(),zoneName:$('navZoneName').value.trim(),zoneType:type,polygonJson:{points},speedLimitMps:type==='SPEED'?speed:null,
        active:$('navZoneActive').checked,publicVisible:old?.publicVisible??false,capacityLimit:old?.capacityLimit??null,calibratedAreaM2:old?.calibratedAreaM2??null,cameraSourceRef:old?.cameraSourceRef??null,cameraRoiJson:old?.cameraRoiJson??null,description:old?.description??'Thiết lập rule dẫn đường trên map'};
    }
    function zoneDraft(fromText=false){
      if(fromText){try{const p=JSON.parse($('navZonePoints').value);if(Array.isArray(p)&&p.every(a=>Array.isArray(a)&&a.length===2&&a.every(Number.isFinite)))state.zonePoints=p;}catch(_){}}
      else $('navZonePoints').value=JSON.stringify(state.zonePoints);
      try{$('navZoneSwagger').textContent=JSON.stringify(zoneBody(),null,2);}catch(e){$('navZoneSwagger').textContent=e.message;}
      controls();draw();
    }
    async function zones(id=''){
      state.zones=(await pages('/api/v1/zones')).filter(z=>z.mapId===mapId()&&['SPEED','KEEP_OUT'].includes(z.zoneType));
      $('navZones').replaceChildren(new Option('Chọn zone',''));const bound=$('navPointZone').value;
      $('navPointZone').replaceChildren(new Option('Không gắn zone',''));
      for(const z of state.zones){$('navZones').add(new Option(z.zoneCode+' — '+z.zoneName+(z.active?'':' (đã tắt)'),z.zoneId));$('navPointZone').add(new Option(z.zoneCode,z.zoneId));}
      $('navPointZone').value=bound;$('navZones').value=id;state.zoneSyncAt=0;state.zoneAck=null;
      $('navZoneStatus').textContent=state.zones.length+' zone đã lưu; '+state.zones.filter(z=>z.active).length+' đang bật trên BE. Các ô hình học/tốc độ cần bấm Lưu; bật/tắt zone đã lưu gửi ngay. Pi áp dụng khi robot rảnh.';draw();
    }
    async function setSavedZoneActive(enabled){
      const z=state.zoneEditing;if(!z)throw Error('Chọn zone đã lưu trước.');
      const draft=Object.fromEntries(['navZoneCode','navZoneName','navZoneType','navZoneSpeed','navZonePoints'].map(id=>[id,$(id).value]));
      try{
        const saved=await api('/api/v1/zones/'+z.zoneId+(enabled?'/activate':'/deactivate'),{method:'POST',body:{}});
        if(saved.zoneId!==z.zoneId||saved.active!==enabled)throw Error('BE chưa xác nhận trạng thái zone yêu cầu.');
        z.active=saved.active;
        await zones(z.zoneId);$('navZones').onchange();
        for(const [id,value] of Object.entries(draft))$(id).value=value;zoneDraft(true);
        say('BE đã '+(enabled?'bật':'tắt')+' zone '+z.zoneCode+'. Chờ Pi ACK profile mới trước MOVE.');
      }catch(e){$('navZoneActive').checked=z.active;zoneDraft();throw e;}
    }
    async function loadMap() {
      const id = $('navMapId').value.trim(); if (!UUID.test(id)) throw Error('Map ID không hợp lệ.');
      if(active() && state.meta && state.meta.map_id!==id)throw Error('Chờ workflow kết thúc hoặc hủy trước khi đổi map.');
      const generation = ++state.generation; clearTimeout(state.timer);
      state.seed=null;state.dragging=null;state.remembered=null;
      for(const field of ['navSeedX','navSeedY'])$(field).value='';
      state.meta = state.map = state.image = state.snapshot = null; canvas.hidden = true; clearPoint();
      if (state.imageUrl) URL.revokeObjectURL(state.imageUrl); state.imageUrl = null;
      const [map, meta, response] = await Promise.all([api('/api/v1/maps/' + id), api('/api/v1/maps/' + id + '/image-metadata'),
        api('/api/v1/maps/' + id + '/image.png', {raw: true, headers: {Accept: 'image/png'}})]);
      geometry(meta);
      if (meta.map_id !== id || map.mapId !== id || map.assetHash?.toLowerCase() !== meta.asset_hash.toLowerCase() ||
          response.headers.get('X-Map-Asset-Hash')?.toLowerCase() !== meta.asset_hash.toLowerCase() ||
          response.headers.get('Content-Type')?.split(';')[0] !== 'image/png') throw Error('Ảnh và metadata không cùng map/hash.');
      const url = URL.createObjectURL(await response.blob()), image = new Image();
      try { image.src = url; await image.decode(); } catch (error) { URL.revokeObjectURL(url); throw error; }
      if (generation !== state.generation) { URL.revokeObjectURL(url); return; }
      if (image.naturalWidth !== meta.width_px || image.naturalHeight !== meta.height_px) { URL.revokeObjectURL(url); throw Error('Kích thước PNG khác metadata.'); }
      state.meta = meta; state.map = map; state.image = image; state.imageUrl = url;
      state.robotId = options.getRobotId().trim();
      canvas.width = meta.width_px; canvas.height = meta.height_px;
      canvas.style.width = 'min(100%, ' + Math.max(180, 650 * meta.width_px / meta.height_px) + 'px)'; canvas.hidden = false;
      $('navMapInfo').textContent = `${map.mapName} · ${map.status} · ${meta.width_px} × ${meta.height_px} ô · ${(meta.resolution_m_per_pixel * 100).toFixed(1)} cm/ô · frame ${meta.frame_id}`;
      clearZone();$('navZoneMode').checked=false;await zones();await endpoints();
      say('Đã tải map thật. Chọn endpoint đã lưu hoặc bấm/kéo để đặt điểm mới.');
      controls(); draw(); poll(generation);
    }
    async function snapshot() {
      const generation=state.generation,requestedRobot=robotId(),started=performance.now();
      const value = await api('/api/v1/robots/' + requestedRobot + '/snapshot');
      if(generation!==state.generation||requestedRobot!==robotId())return value;
      if (value.robot?.robotId !== robotId()) throw Error('Snapshot không đúng robot.');
      return acceptSnapshot(value,started,generation,requestedRobot);
    }
    function acceptSnapshot(value,started,generation,requestedRobot){
      if(generation!==state.generation||requestedRobot!==robotId()||value.robot?.robotId!==requestedRobot)return;
      if(state.snapshot&&Date.parse(value.observedAt)<Date.parse(state.snapshot.observedAt))return state.snapshot;
      state.snapshot = value; state.receivedAt = started;
      const session=value.navigationSession;
      $('navSessionStatus').textContent=session?.sessionId?`Phiên ${session.sessionId} · ${session.state} · ${session.localization?.reason||'chờ Pi nạp map và xác minh vị trí'} · hết hạn ${session.expiresAt}`:'Chưa mở phiên map tạm.';
      $('navPiCommand').textContent=session?.sessionId&&session.mapId===state.meta?.map_id&&['WAITING_FOR_PI','LOCALIZING','READY'].includes(session.state)?'Trong container, sau khi dừng robot.zsh run:\ncd /home/ubuntu/mediassist-mqtt-2026-10-08\nzsh tools/navigation_session.zsh --session-id '+session.sessionId:'Mở phiên để lấy lệnh khởi động Pi.';
      $('navRobotStatus').textContent = `Robot theo dõi: ${value.robot.robotId} · ${value.robot.status} · MQTT ${value.mqttConnected ? 'đã nối' : 'chưa nối'} · dispatch ${value.dispatchReady ? 'sẵn sàng' : 'chưa sẵn sàng'}${value.sessionFailureReason ? ' · ' + value.sessionFailureReason : ''}`;
      controls();draw(); syncZoneStatus(value,generation,requestedRobot).catch(()=>{}); return value;
    }
    async function syncZoneStatus(value,generation,requestedRobot) {
      if(value.navigationSession?.sessionId&&performance.now()-state.zoneSyncAt>5000){
        state.zoneSyncAt=performance.now();
        try{const sync=await api('/api/v1/robots/'+requestedRobot+'/navigation-zones');if(generation!==state.generation||requestedRobot!==robotId())return;state.zoneAck=sync.mapId===mapId()&&sync.profileAcknowledged;
          $('navZoneSync').textContent=state.zoneAck?(typeof sync.speedZoneCount==='number'?
            'Pi đã xác nhận '+sync.restrictedZoneCount+' vùng cấm và '+sync.speedZoneCount+' vùng giới hạn tốc độ.':
            'Pi đã xác nhận profile chứa '+sync.zoneCount+' zone.'):'BE đang bật '+sync.restrictedZoneCount+' vùng cấm và '+sync.speedZoneCount+' vùng giảm tốc; Pi chưa ACK profile này. Chờ robot rảnh và kết nối.';}
        catch(e){if(generation!==state.generation)return;state.zoneAck=null;$('navZoneSync').textContent='Chưa kiểm tra được đồng bộ zone: '+e.message;}
      }
      controls();
    }
    let streamController=null,streamGeneration=-1,streamLastAt=-Infinity,streamRetryAt=0;
    function stopRobotStream(){streamController?.abort();streamController=null;streamGeneration=-1;streamLastAt=-Infinity;}
    function ensureRobotStream(generation){
      if(streamController&&streamGeneration!==generation)stopRobotStream();
      if(streamController||performance.now()<streamRetryAt)return;
      const token=options.getToken().trim().replace(/^Bearer\s+/i,''),requestedRobot=robotId();if(!token)return;
      const controller=new AbortController();streamController=controller;streamGeneration=generation;
      (async()=>{try{
        const response=await fetch('/api/v1/robots/'+requestedRobot+'/stream',{headers:{Authorization:'Bearer '+token},cache:'no-store',signal:controller.signal});
        if(!response.ok)throw Error('Stream HTTP '+response.status);
        await consumeRobotEvents(response,(value,event)=>{
          if(controller.signal.aborted||generation!==state.generation||requestedRobot!==robotId())return;
          if(event==='robot-pose'){
            if(value.robotId===requestedRobot&&(!state.livePose||value.transportSessionId!==state.livePose.value.transportSessionId||value.sequenceNo>state.livePose.value.sequenceNo)){
              state.livePose={value,at:performance.now()};controls();draw();
            }
            return;
          }
          acceptSnapshot(value,performance.now(),generation,requestedRobot);streamLastAt=performance.now();
        },['robot-state','robot-pose']);
      }catch(error){/* REST fallback below remains active; stream failure is not robot disconnection. */}
      finally{if(streamController===controller){streamController=null;streamRetryAt=performance.now()+5000;}}})();
    }
    async function poll(generation) {
      if (state.closed || generation !== state.generation || !state.meta) return;
      const started=performance.now();ensureRobotStream(generation);
      let delay=500;
      try { if(performance.now()-streamLastAt>6000)await snapshot(); await refreshGuidance(generation); }
      catch (error) { if(generation!==state.generation)return;
        $('navRobotStatus').textContent = 'UI chưa nhận trạng thái mới; chưa kết luận mất MQTT: ' + error.message; controls();draw();
        if (error.status === 401 || error.status === 403) {stopRobotStream();delay=5000;
          say('Token đã hết hạn hoặc không đủ quyền. Đăng nhập/dán token ADMIN mới rồi bấm Kiểm tra lại phiên và job.',true);} }
      draw();controls();
      if (!state.closed && generation === state.generation) state.timer = setTimeout(() => poll(generation), Math.max(100,delay-(performance.now()-started)));
    }
    $('navLoad').onclick = () => mutate(loadMap);
    $('navUseSaved').onclick = () => mutate(async () => { const id = options.getSavedMapId(); if (!UUID.test(id)) throw Error('Xem map đã lưu của job ở trên trước.'); $('navMapId').value = id; await loadMap(); });
    $('navNewPoint').onclick = clearPoint;
    $('navEndpoints').onchange = () => {
      const point = selected(); if (!point) return clearPoint();
      state.editing = point;
      $('navPointType').value=point.endpointType;$('navPointZone').value=point.zoneId||'';$('navAtParking').checked=false;
      if(point.zoneId&&!Array.from($('navPointZone').options).some(o=>o.value===point.zoneId)){$('navPointZone').add(new Option('Zone đã gắn: '+point.zoneId,point.zoneId));$('navPointZone').value=point.zoneId;}
      for (const [id, value] of Object.entries({navX: point.xM, navY: point.yM, navYaw: point.yawRad * 180 / Math.PI, navPointCode: point.endpointCode, navPointName: point.endpointName})) $(id).value = value;
      $('navPointId').textContent = 'Endpoint ID: ' + point.endpointId; updateDraft();
    };
    for (const id of ['navX', 'navY', 'navYaw', 'navPointCode', 'navPointName','navPointType','navPointZone']) $(id).addEventListener('input', updateDraft);
    $('navAtParking').onchange=controls;
    $('navParkingSeed').onclick=()=>{const p=selected();if(!p||p.endpointType!=='PARK'||!$('navAtParking').checked)return;for(const [id,v] of Object.entries({navSeedX:p.xM,navSeedY:p.yM,navSeedYaw:p.yawRad*180/Math.PI}))$(id).value=v;updateSeed();say('Đã điền vị trí parking. Bấm Gửi vị trí khởi tạo cho AMCL khi phiên map sẵn sàng; chờ Pi xác minh.');};
    $('navNewZone').onclick=()=>{clearZone();$('navZoneMode').checked=true;$('navSeedMode').checked=false;};
    $('navReloadZones').onclick=()=>mutate(()=>zones());
    $('navZoneMode').onchange=()=>{if($('navZoneMode').checked)$('navSeedMode').checked=false;};
    $('navSeedMode').onchange=()=>{if($('navSeedMode').checked)$('navZoneMode').checked=false;};
    $('navZones').onchange=()=>{const z=state.zones.find(z=>z.zoneId===$('navZones').value);if(!z)return clearZone();state.zoneEditing=z;state.zonePoints=z.polygonJson?.points||z.polygonJson;for(const [id,v] of Object.entries({navZoneCode:z.zoneCode,navZoneName:z.zoneName,navZoneType:z.zoneType,navZoneSpeed:z.speedLimitMps??.15}))$(id).value=v;$('navZoneActive').checked=z.active;zoneDraft();};
    for(const id of ['navZoneCode','navZoneName','navZoneType','navZoneSpeed','navZoneActive'])$(id).addEventListener('input',()=>zoneDraft());
    $('navZonePoints').addEventListener('input',()=>zoneDraft(true));
    $('navZoneUndo').onclick=()=>{state.zonePoints=state.zonePoints.slice(0,-1);zoneDraft();};
    $('navSaveZone').onclick=()=>mutate(async()=>{const body=zoneBody();if(!body.zoneCode||!body.zoneName)throw Error('Cần mã và tên zone.');const saved=await api('/api/v1/zones'+(state.zoneEditing?'/'+state.zoneEditing.zoneId:''),{method:state.zoneEditing?'PUT':'POST',body});await zones(saved.zoneId);$('navZones').onchange();$('navZoneMode').checked=false;say('Đã lưu zone. Chờ Pi ACK profile mới khi rảnh trước khi thử MOVE.');});
    $('navZoneActive').onchange=()=>{if(state.zoneEditing)return mutate(()=>setSavedZoneActive($('navZoneActive').checked));zoneDraft();};
    $('navToggleZone').onclick=()=>mutate(async()=>{const z=state.zoneEditing;if(!z)throw Error('Chọn zone trước.');await setSavedZoneActive(!z.active);});
    $('navSavePoint').onclick = () => mutate(async () => {
      if (active()) throw Error('Không sửa endpoint trong khi yêu cầu đang chạy.');
      const body = endpointBody(); if (!body.endpointCode || !body.endpointName) throw Error('Cần mã và tên endpoint.');
      await api('/api/v1/maps/'+mapId()+'/validate-navigation-point',{method:'POST',body:{xM:body.xM,yM:body.yM}});
      const existing = state.editing;
      const saved = await api('/api/v1/endpoints' + (existing ? '/' + existing.endpointId : ''), {method: existing ? 'PUT' : 'POST', body});
      await endpoints(saved.endpointId); $('navEndpoints').onchange();
      say('Đã lưu endpoint ' + saved.endpointId + '. Chưa gửi MOVE; Nav2 sẽ kiểm tra khả năng đi tới khi chạy.');
    });
    $('navDeletePoint').onclick = () => mutate(async () => {
      const point = selected();
      if (!point?.active) throw Error('Chọn endpoint đã lưu trước khi xóa.');
      if (active() || options.isMappingActive()) throw Error('Kết thúc hoặc hủy nhiệm vụ đang chạy trước khi xóa endpoint.');
      if (!window.confirm('Xóa endpoint ' + point.endpointCode + ' — ' + point.endpointName + '? Điểm sẽ không còn dùng để MOVE; lịch sử nhiệm vụ vẫn được giữ.')) return;
      await snapshot();
      if (active() || options.isMappingActive()) throw Error('Robot vừa có nhiệm vụ mới; chưa xóa endpoint.');
      await api('/api/v1/endpoints/' + point.endpointId + '/deactivate', {method:'POST',body:{}});
      state.endpoints = state.endpoints.filter(e => e.endpointId !== point.endpointId);
      state.zoneAck = null; state.zoneSyncAt = 0;
      showEndpoints(); clearPoint();
      say('Đã xóa endpoint ' + point.endpointCode + ' khỏi map và danh sách MOVE. Workflow nháp còn dùng điểm này cần bỏ hoặc thay step MOVE; lịch sử job được giữ lại.');
    });
    $('navActivate').onclick = () => mutate(async () => {
      if (options.isMappingActive()) throw Error('Kết thúc phiên quét trước khi đổi map active.');
      await api('/api/v1/maps/' + mapId() + '/activate', {method: 'POST', body: {}});
      await loadMap(); say('Map đã ACTIVE trên BE. Cần gán map và nạp vào Pi trước khi chạy.');
    });
    $('navAssign').onclick = () => mutate(async () => {
      if (options.isMappingActive()) throw Error('Kết thúc phiên quét trước khi gán map.');
      state.robotId = options.getRobotId().trim(); const id = robotId();
      const current = await api('/api/v1/robots/' + id + '/snapshot');
      if (current.activeJobId) throw Error('Robot còn job đang chạy/khôi phục. Chưa đổi map.');
      await api('/api/v1/robots/'+id+'/navigation-session',{method:'POST',body:{mapId:mapId(),durationSec:7200}});
      await snapshot();say('Đã mở phiên map tạm. Chạy lệnh Pi bên trên, rồi chọn vị trí khởi tạo hoặc tìm toàn cục.');
    });
    $('navNormalize').onclick=()=>mutate(async()=>{
      const copy=await api('/api/v1/maps/'+mapId()+'/navigation-copy',{method:'POST',body:{}});
      $('navMapId').value=copy.mapId;await loadMap();say('Đã tạo bản map có ngưỡng unknown đúng và sao chép endpoint. Kích hoạt map mới trước khi mở phiên.');
    });
    $('navCloseSession').onclick=()=>mutate(async()=>{
      const session=state.snapshot?.navigationSession;if(!session?.sessionId)throw Error('Không có phiên đang mở.');
      await api('/api/v1/robots/'+robotId()+'/navigation-session/close',{method:'POST',body:{sessionId:session.sessionId}});
      await snapshot();say('Đã đóng phiên. Dừng terminal navigation; robot.zsh run bình thường vẫn dùng cấu hình quét map ban đầu.');
    });
    $('navRecheck').onclick=()=>mutate(async()=>{
      if(!state.meta)await loadMap();
      await snapshot();
      clearTimeout(state.timer);state.timer=setTimeout(()=>poll(state.generation),1000);
      if(!active())say('Đã đối chiếu BE. '+(['CLOSED','EXPIRED','NONE'].includes(state.snapshot?.navigationSession?.state)?'Có thể mở phiên mới.':'Xem trạng thái phiên bên trên.'));
    });
    function updateSeed(){
      const values=['navSeedX','navSeedY','navSeedYaw'].map(id=>$(id).value.trim());
      const [x,y,yaw]=values.map(Number);state.seed=values.every(Boolean)&&inside(state.meta||{},x,y)&&Number.isFinite(yaw)&&Math.abs(yaw)<=180?{xM:x,yM:y,yawRad:yaw*Math.PI/180}:null;
      controls();draw();
    }
    for(const id of ['navSeedX','navSeedY','navSeedYaw'])$(id).addEventListener('input',updateSeed);
    async function initialize(mode){
      const session=state.snapshot?.navigationSession;if(!session?.sessionId||session.mapId!==mapId())throw Error('Mở phiên của đúng map trước.');
      await api('/api/v1/robots/'+robotId()+'/navigation-session/initialization',{method:'POST',body:{sessionId:session.sessionId,mode,...(mode==='SEED'?state.seed:{})}});
      state.snapshot=null;state.remembered=null;draw();await snapshot();say('Đã yêu cầu khởi tạo; chờ AMCL xác minh. Chưa gửi MOVE.');
    }
    $('navSendSeed').onclick=()=>mutate(()=>initialize('SEED'));
    $('navGlobal').onclick=()=>mutate(()=>initialize('GLOBAL'));
    $('navRefresh').onclick = () => mutate(async () => {
      mapId(); await snapshot();
      clearTimeout(state.timer); state.timer = setTimeout(() => poll(state.generation), 1000);
      say(pose() ? 'Đã nhận vị trí mới đúng map.' : 'Chưa có pose hợp lệ trên map đã chọn; xem trạng thái robot.');
    });
    $('navOriginalCells').onchange = draw;
    $('navViewAngle').oninput = draw;
    $('navFollow').onclick=()=>{state.follow=true;draw();};
    const zoomBy=factor=>{state.zoom=Math.max(1,Math.min(8,state.zoom*factor));draw();};
    $('navZoomIn').onclick=()=>zoomBy(1.3);
    $('navZoomOut').onclick=()=>zoomBy(1/1.3);
    canvas.addEventListener('wheel',event=>{if(!state.camera||$('navMapTools').hidden)return;
      event.preventDefault();zoomBy(event.deltaY<0?1.12:1/1.12);},{passive:false});
    const pointer = event => {
      const box = canvas.getBoundingClientRect();
      const transform = viewTransform(state.meta.width_px,state.meta.height_px,Number($('navViewAngle').value));
      const camera=state.camera||{zoom:1,center:{u:transform.width/2,v:transform.height/2}};
      return transform.inverse(((event.clientX-box.left)*transform.width/box.width-transform.width/2)/camera.zoom+camera.center.u,
        ((event.clientY-box.top)*transform.height/box.height-transform.height/2)/camera.zoom+camera.center.v);
    };
    canvas.addEventListener('pointerdown', event => {
      if(state.meta&&event.button===0&&!$('navMapTools').hidden){
        state.follow=false;state.pan={x:event.clientX,y:event.clientY,center:{...state.camera.center}};
        canvas.setPointerCapture(event.pointerId);draw();return;
      }
      if (!state.meta || state.busy || active() || event.button !== 0) return;
      const p = pointer(event); if (p.u < 0 || p.v < 0 || p.u >= state.meta.width_px || p.v >= state.meta.height_px) return;
      if($('navZoneMode').checked){if(state.zonePoints.length>=256)return;const point=pixelToMap(state.meta,Math.floor(p.u)+.5,Math.floor(p.v)+.5);state.zonePoints=[...state.zonePoints,[point.xM,point.yM]];zoneDraft();return;}
      if($('navSeedMode').checked){
        const point=pixelToMap(state.meta,Math.floor(p.u)+.5,Math.floor(p.v)+.5);
        $('navSeedX').value=point.xM;$('navSeedY').value=point.yM;
        state.dragging={point,x:event.clientX,y:event.clientY,seed:true};canvas.setPointerCapture(event.pointerId);updateSeed();return;
      }
      clearPoint(); const point = pixelToMap(state.meta, Math.floor(p.u) + .5, Math.floor(p.v) + .5);
      $('navX').value = point.xM; $('navY').value = point.yM;
      state.dragging = {point, x: event.clientX, y: event.clientY}; canvas.setPointerCapture(event.pointerId); updateDraft();
    });
    canvas.addEventListener('pointermove', event => {
      if(state.pan){
        const box=canvas.getBoundingClientRect(),camera=state.camera;
        state.viewCenter={u:state.pan.center.u-(event.clientX-state.pan.x)*camera.transform.width/box.width/camera.zoom,
          v:state.pan.center.v-(event.clientY-state.pan.y)*camera.transform.height/box.height/camera.zoom};
        draw();return;
      }
      if (!state.dragging) return;
      if (Math.hypot(event.clientX - state.dragging.x, event.clientY - state.dragging.y) < 6) return;
      const p = pointer(event), end = pixelToMap(state.meta, p.u, p.v), start = state.dragging.point;
      if(state.dragging.seed){$('navSeedYaw').value=(Math.atan2(end.yM-start.yM,end.xM-start.xM)*180/Math.PI).toFixed(2);updateSeed();return;}
      $('navYaw').value = (Math.atan2(end.yM - start.yM, end.xM - start.xM) * 180 / Math.PI).toFixed(2); updateDraft();
    });
    const release = () => { state.dragging = null;state.pan=null; };
    canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release);
    $('navMapId').addEventListener('input', () => {
      stopRobotStream();state.generation++; clearTimeout(state.timer); state.meta = state.image = state.snapshot = null; canvas.hidden = true;
      state.endpoints = []; showEndpoints(); clearPoint(); $('navMapInfo').textContent = 'Map ID đã đổi; tải map trước khi thao tác.';
      state.zones=[];clearZone();$('navZoneMode').checked=false;$('navZoneSync').textContent='Tải map mới để kiểm tra zone.';
      state.seed=null;for(const id of ['navSeedX','navSeedY'])$(id).value='';
      $('navPose').textContent = 'Chưa tải map mới.'; controls();
    });
    const expiryTimer = setInterval(()=>{controls();draw();}, 250);
    window.addEventListener('pagehide', () => { stopRobotStream();state.closed = true; state.generation++; clearTimeout(state.timer); clearInterval(expiryTimer); if (state.imageUrl) URL.revokeObjectURL(state.imageUrl); });
    controls();
    return {loadMap, state, refresh:snapshot};
  }
  return {geometry, pixelToMap, mapToPixel, inside, renderSize, viewTransform, freshPose, poseView, liveNavigationPose, currentNavigationPose, plannedPath, consumeRobotEvents, mount};
});
