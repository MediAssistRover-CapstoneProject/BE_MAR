/* One staff participant; the backend owns scope, assignment and call lifetime. */
(()=>{'use strict';
const $=id=>document.getElementById(id),sdk=window.LivekitClient;
let room=null,current=null,refreshTimer=null,lastFrame=0,frameHandle=null;
let accessToken='',signingIn=false,connecting=false,listPending=false;
let patrolAlertsPending=false;
let fallIncident=null,reviewPending=false;
let aiOverlay=null,overlaySequence=0,overlayAt=0;
// Sender and viewer clocks need not agree. Expire optional drawing only by
// elapsed time since this browser received the latest authenticated packet.
const overlayIdleMs=3000;
const overlayStates=new Set(['PERSON','OBSERVING','SUSPECTED_FALL','PERSON_ON_FLOOR']);
function clearOverlay(text='Chưa có dữ liệu nhận diện.'){
  aiOverlay=null;overlayAt=0;
  const canvas=$('aiOverlay');canvas.getContext('2d').clearRect(0,0,canvas.width,canvas.height);
  $('overlayStatus').textContent=text;
}
function receiveOverlay(payload,participant,topic,session){
  if(topic!=='mar.ai.overlay.v1'||participant?.identity!=='robot-'+session.robotId
    ||!ArrayBuffer.isView(payload)||!payload.byteLength||payload.byteLength>1200)return;
  try{
    const value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(payload));
    if(value.v!==1||value.media_session_id!==session.sessionId||!Number.isSafeInteger(value.seq)||value.seq<=overlaySequence
      ||!Number.isInteger(value.width)||!Number.isInteger(value.height)||value.width<32||value.width>640||value.height<32||value.height>640
      ||!['OK','PAUSED','DEGRADED'].includes(value.status)||!Array.isArray(value.detections)||value.detections.length>8)return;
    for(const d of value.detections){
      if(!d||!Array.isArray(d.bbox)||d.bbox.length!==4||d.bbox.some(n=>typeof n!=='number'||!Number.isFinite(n)||n<0||n>1)
        ||d.bbox[2]<=0||d.bbox[3]<=0||d.bbox[0]+d.bbox[2]>1.0002||d.bbox[1]+d.bbox[3]>1.0002
        ||typeof d.confidence!=='number'||!Number.isFinite(d.confidence)||d.confidence<0||d.confidence>1
        ||!overlayStates.has(d.state)||(d.track_id!==null&&(!Number.isSafeInteger(d.track_id)||d.track_id<0||d.track_id>1e9))
        ||d.distance_m!==undefined&&(typeof d.distance_m!=='number'||!Number.isFinite(d.distance_m)||d.distance_m<=0))return;
    }
    overlaySequence=value.seq;overlayAt=performance.now();
    aiOverlay=value;drawOverlay();
  }catch{/* A malformed optional display packet never interrupts the call. */}
}
function drawOverlay(){
  const canvas=$('aiOverlay'),video=$('video'),ctx=canvas.getContext('2d');
  const w=video.clientWidth,h=video.clientHeight,scale=Math.min(window.devicePixelRatio||1,2);
  if(canvas.width!==Math.round(w*scale)||canvas.height!==Math.round(h*scale)){
    canvas.width=Math.round(w*scale);canvas.height=Math.round(h*scale);
  }
  ctx.setTransform(scale,0,0,scale,0,0);ctx.clearRect(0,0,w,h);
  if(!aiOverlay)return;
  const age=performance.now()-overlayAt;
  if(age>overlayIdleMs){clearOverlay('Đang chờ kết quả AI mới; video vẫn hoạt động.');return;}
  if(!liveVideo()){clearOverlay('Chờ camera và kết quả AI mới.');return;}
  if(aiOverlay.status!=='OK'){
    $('overlayStatus').textContent=aiOverlay.status==='PAUSED'?'AI đang tạm nhường tác vụ ưu tiên.':'AI chưa phản hồi; video vẫn hoạt động.';return;
  }
  const n=aiOverlay.detections.length;
  $('overlayStatus').textContent=(n?`${n} người · `:'Chưa nhận diện người · ')+`đã nhận kết quả ${Math.max(0,age/1000).toFixed(1)} giây trước`;
  if(!$('overlayEnabled').checked||!video.videoWidth||!video.videoHeight)return;
  const ratio=video.videoWidth/video.videoHeight;
  if(Math.abs(ratio-aiOverlay.width/aiOverlay.height)>.02){$('overlayStatus').textContent='Kích thước ảnh AI chưa khớp camera.';return;}
  const fit=Math.min(w/video.videoWidth,h/video.videoHeight),vw=video.videoWidth*fit,vh=video.videoHeight*fit;
  const ox=(w-vw)/2,oy=(h-vh)/2;
  ctx.lineWidth=2;ctx.font='14px system-ui,sans-serif';
  const labels={PERSON:'Người',OBSERVING:'Đang quan sát',SUSPECTED_FALL:'Nghi té ngã',PERSON_ON_FLOOR:'Nghi nằm trên sàn'};
  for(const d of aiOverlay.detections){
    const [x,y,bw,bh]=d.bbox,left=ox+($('mirrorVideo').checked?1-x-bw:x)*vw,top=oy+y*vh;
    ctx.strokeStyle=d.state==='PERSON'?'#35d98b':d.state==='OBSERVING'?'#ffda65':'#ff6060';
    ctx.strokeRect(left,top,bw*vw,bh*vh);
    const text=labels[d.state]+(d.track_id===null?'':` #${d.track_id}`)
      +(['PERSON','OBSERVING'].includes(d.state)?` · người ${Math.round(d.confidence*100)}%`:'')
      +(d.distance_m===undefined?'':` · ${d.distance_m.toFixed(1)} m`);
    const tw=ctx.measureText(text).width+10,tx=Math.max(0,Math.min(left,w-tw)),ty=Math.max(0,top-23);
    ctx.fillStyle='rgba(0,0,0,.8)';ctx.fillRect(tx,ty,Math.min(tw,w),23);
    ctx.fillStyle=ctx.strokeStyle;ctx.fillText(text,tx+5,ty+17);
  }
}
$('overlayEnabled').addEventListener('change',drawOverlay);
$('mirrorVideo').addEventListener('change',()=>{
  $('video').style.transform=$('mirrorVideo').checked?'scaleX(-1)':'';
  drawOverlay();
});
new ResizeObserver(drawOverlay).observe($('videoStage'));
setInterval(()=>{if(room)drawOverlay();},200);
function message(value,error=false){$('status').textContent=value;$('status').className=error?'error':'';}
async function request(path,body,token='',keepalive=false){
  const result=await fetch(path,{
    method:body===undefined?'GET':'POST',
    headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body!==undefined?{'Content-Type':'application/json'}:{})},
    body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',keepalive,
    ...(keepalive?{}:{signal:AbortSignal.timeout(8000)})
  });
  const value=await result.json().catch(()=>null);
  if(!result.ok)throw Object.assign(Error(value?.message||('HTTP '+result.status)),{status:result.status});
  return value?.data??value;
}
function api(path,body,keepalive=false){
  if(!accessToken)throw Error('Cần đăng nhập tài khoản Staff/Admin.');
  return request(path,body,accessToken,keepalive);
}
function buttons(){
  const active=!!room,busy=active||connecting;
  $('create').disabled=busy||!accessToken;
  $('refresh').disabled=busy||listPending||!accessToken;
  $('signOut').disabled=busy;
  $('loginButton').disabled=$('tokenSignIn').disabled=signingIn;
  $('end').disabled=!active||connecting;
  $('mic').disabled=!active||connecting||current?.mode!=='ASSIST';
  $('enableAudio').disabled=!active||connecting||current?.mode!=='ASSIST';
  reviewButtons();
}
function liveVideo(){return !!room&&lastFrame>0&&performance.now()-lastFrame<3000;}
function reviewButtons(){
  $('confirmFall').disabled=reviewPending||connecting||!liveVideo();
  $('confirmFall').hidden=fallIncident?.status==='IN_PROGRESS';
  $('showResponseRoute').hidden=fallIncident?.status!=='IN_PROGRESS';
  $('showResponseRoute').disabled=reviewPending;
}
function clearReview(){
  fallIncident=null;
  $('fallReview').hidden=true;
  $('responseMap').hidden=true;
  $('routeStatus').textContent='';
}
async function loadReview(session){
  if(session.mode!=='MONITOR'||!session.incidentId)return;
  const incident=await api('/api/v1/emergency-incidents/'+session.incidentId);
  if(current?.sessionId!==session.sessionId||incident.robotId!==session.robotId||incident.incidentType!=='AI_SUSPECTED_FALL')return;
  fallIncident=incident;
  $('fallReview').hidden=false;
  $('fallReviewStatus').textContent=incident.status==='IN_PROGRESS'?'Staff đã xác nhận.':'Quan sát camera trực tiếp trước khi xác nhận.';
  reviewButtons();
}
async function drawResponseRoute(incident){
  const route=await api('/api/v1/emergency-incidents/'+incident.incidentId+'/response-route');
  if(fallIncident?.incidentId!==incident.incidentId)return;
  if(route.status!=='FOUND'&&(!route.mapId||!route.patientLocation&&!route.robotObservationLocation)){
    $('responseMap').hidden=true;
    $('routeStatus').textContent='Chưa có đường đi · '+route.reason;
    return;
  }
  const mapUrl='/api/v1/maps/'+route.mapId;
  const [meta,imageResponse]=await Promise.all([api(mapUrl+'/image-metadata'),fetch(mapUrl+'/image.png',{
    headers:{Authorization:'Bearer '+accessToken,Accept:'image/png'},cache:'no-store',signal:AbortSignal.timeout(8000)
  })]);
  const assetHash=route.mapAssetHash?.toLowerCase();
  if(!imageResponse.ok||!assetHash||meta.map_id!==route.mapId||meta.asset_hash?.toLowerCase()!==assetHash||
      imageResponse.headers.get('X-Map-Asset-Hash')?.toLowerCase()!==assetHash)throw Error('Ảnh bản đồ không khớp đường đi đã tính.');
  const mapGeometry=window.SavedMapNavigation.geometry(meta);
  const bitmap=await createImageBitmap(await imageResponse.blob());
  try{
    if(fallIncident?.incidentId!==incident.incidentId)return;
    if(bitmap.width!==meta.width_px||bitmap.height!==meta.height_px)throw Error('Kích thước ảnh không khớp bản đồ.');
    const canvas=$('responseMap');canvas.width=bitmap.width;canvas.height=bitmap.height;
    const ctx=canvas.getContext('2d');ctx.drawImage(bitmap,0,0);
    const points=route.points.map(p=>window.SavedMapNavigation.mapToPixel(mapGeometry,p.xCm/100,p.yCm/100));
    if(points.length>1){
      ctx.lineWidth=2;ctx.strokeStyle='#065fd4';ctx.lineJoin='round';ctx.beginPath();
      points.forEach(({u,v},i)=>i?ctx.lineTo(u,v):ctx.moveTo(u,v));ctx.stroke();
    }
    const markers=[[route.origin,'#065fd4'],[route.patientLocation||route.destination,'#af1717'],
      [route.robotObservationLocation,'#d87800'],[route.robotApproach?.position,'#15803d']];
    for(const [position,color] of markers){
      if(!position)continue;
      const point=window.SavedMapNavigation.mapToPixel(mapGeometry,position.xCm/100,position.yCm/100);
      ctx.fillStyle=color;ctx.beginPath();ctx.arc(point.u,point.v,3,0,Math.PI*2);ctx.fill();
    }
    canvas.hidden=false;
    const target=route.patientLocation?'người nghi té ngã (đỏ)':'điểm robot quan sát';
    let summary=route.status==='FOUND'?'Phòng SOS (xanh dương) → '+target+' · '+(route.distanceCm/100).toFixed(1)+' m'
      :'Đã đánh dấu vị trí. Chưa có đường đi · '+route.reason;
    if(route.patientLocation){
      summary+=' · Robot lúc quan sát (cam)';
      if(Number.isFinite(route.cameraRangeM))summary+=' · Depth tại hông '+route.cameraRangeM.toFixed(2)+' m';
    }
    if(route.robotApproach)summary+=' · Điểm gần người (xanh lá) '+(route.robotApproach.endpointName||route.robotApproach.endpointCode);
    if(route.destinationCapturedAt)summary+=' · Ghi nhận '+new Date(route.destinationCapturedAt).toLocaleTimeString('vi-VN');
    $('routeStatus').textContent=summary;
  }finally{bitmap.close();}
}
async function reviewOperation(confirm){
  if(reviewPending||!fallIncident||confirm&&!liveVideo())return;
  const incident=fallIncident;reviewPending=true;reviewButtons();
  try{
    if(confirm){
      const verified=await api('/api/v1/emergency-incidents/'+incident.incidentId+'/confirm',{});
      if(fallIncident?.incidentId!==incident.incidentId)return;
      fallIncident=verified;
      $('fallReviewStatus').textContent='Staff đã xác nhận.';
    }
    await drawResponseRoute(fallIncident);
  }catch(error){if(fallIncident?.incidentId===incident.incidentId)$('routeStatus').textContent=error.message;}
  finally{reviewPending=false;reviewButtons();}
}
function authView(label){
  $('staffLogin').hidden=$('tokenTools').hidden=!!accessToken;
  $('signOut').hidden=$('callPanel').hidden=!accessToken;
  $('authStatus').textContent=label;
  buttons();
}
function clearSignIn(label){
  accessToken='';
  $('sessions').replaceChildren();
  $('patrolAlerts').replaceChildren();
  $('patrolAlertStatus').textContent='';
  clearReview();
  authView(label);
}
async function signIn(loadToken){
  if(signingIn||accessToken)return;
  signingIn=true;buttons();
  try{
    const token=await loadToken();
    const account=await request('/api/v1/auth/me',undefined,token);
    if(account.mustChangePassword)throw Error('Đổi mật khẩu trước khi nhận SOS.');
    if(!['STAFF','ADMIN'].includes(account.role))throw Error('Cần tài khoản Staff/Admin.');
    accessToken=token;
    $('staffLogin').reset();$('token').value='';
    authView('Đã đăng nhập · '+(account.fullName||account.username));
    message('Đang chờ phiên được giao.');
    await refreshPanel();
  }catch(e){authView(e.message);}
  finally{signingIn=false;buttons();}
}
async function leave(){
  clearOverlay();overlaySequence=0;
  const previous=room;room=null;current=null;clearInterval(refreshTimer);refreshTimer=null;
  if(frameHandle!=null&&$('video').cancelVideoFrameCallback)$('video').cancelVideoFrameCallback(frameHandle);frameHandle=null;
  if(previous){previous.removeAllListeners();await previous.disconnect();}
  $('video').srcObject=null;$('audio').replaceChildren();buttons();
}
async function connect(openSession){
  if(!sdk)throw Error('Chưa tải được WebRTC SDK.');
  if(room||connecting)return;
  connecting=true;buttons();
  try{
    clearReview();
    const requested=await openSession();
    const session=await api('/api/v1/media-sessions/'+requested.sessionId+'/accept',{});
    const join=await api('/api/v1/media-sessions/'+session.sessionId+'/join-token',{});
    current=session;lastFrame=0;const active=new sdk.Room({adaptiveStream:true,dynacast:true});room=active;
    overlaySequence=0;clearOverlay();
    active.on(sdk.RoomEvent.DataReceived,(payload,participant,kind,topic)=>{
      if(room===active)receiveOverlay(payload,participant,topic,session);
    });
    active.on(sdk.RoomEvent.TrackSubscribed,(track,publication,participant)=>{
      if(participant.identity!=='robot-'+session.robotId)return;
      if(track.kind===sdk.Track.Kind.Video){track.attach($('video'));message('Đã nhận camera robot.');}
      else if(session.mode==='ASSIST'&&track.kind===sdk.Track.Kind.Audio){const el=track.attach();el.autoplay=true;$('audio').appendChild(el);}
    });
    active.on(sdk.RoomEvent.TrackUnsubscribed,track=>track.detach().forEach(el=>{
      if(el===$('video')){lastFrame=0;clearOverlay('Chờ camera và kết quả AI mới.');reviewButtons();}
      else el.remove();
    }));
    active.on(sdk.RoomEvent.Reconnecting,()=>{lastFrame=0;clearOverlay('Đang kết nối lại; khung AI đã xóa.');reviewButtons();message('Đang kết nối lại cuộc gọi…');});
    active.on(sdk.RoomEvent.Reconnected,()=>message('Đã kết nối lại.'));
    active.on(sdk.RoomEvent.Disconnected,()=>{if(room===active){leave().catch(()=>{});message('Cuộc gọi đã ngắt. Kiểm tra trạng thái phiên trước khi mở lại.');}});
    message('Đang kết nối WebRTC; chờ camera robot…');buttons();
    await active.connect(join.url,join.token,{rtcConfig:{iceTransportPolicy:$('relay').checked?'relay':'all'}});
    if(session.mode==='ASSIST'){
      try{await active.localParticipant.setMicrophoneEnabled(true,{echoCancellation:true,noiseSuppression:true,autoGainControl:true});}
      catch(e){message('Đã kết nối. Chưa bật được micro staff; bấm nút micro để thử lại.',true);}
    }
    if(session.mode==='ASSIST')await active.startAudio().catch(()=>message('Bấm “Bật âm thanh” để nghe micro robot.'));
    if($('video').requestVideoFrameCallback){const seen=()=>{if(room!==active)return;lastFrame=performance.now();reviewButtons();frameHandle=$('video').requestVideoFrameCallback(seen);};frameHandle=$('video').requestVideoFrameCallback(seen);}
    loadReview(session).catch(e=>message(e.message,true));
    refreshTimer=setInterval(async()=>{
      try{const state=await api('/api/v1/media-sessions/'+session.sessionId);
        if(room!==active)return;
        reviewButtons();
        if(['ENDED','FAILED','EXPIRED'].includes(state.state)){await leave();message('Phiên '+state.state+' · '+(state.endReason||''));return;}
        if(lastFrame&&performance.now()-lastFrame>3000)message('Camera chưa có frame mới; kiểm tra đường truyền. Âm thanh có thể vẫn đang hoạt động.');
      }catch(e){if(room===active)message('Chưa nhận được trạng thái BE: '+e.message,true);}
    },5000);
    buttons();
  }catch(e){
    await leave();
    if(e.status===401)clearSignIn('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    throw e;
  }finally{connecting=false;buttons();}
}
async function refresh(){
  if(!accessToken||room||connecting||listPending)return;
  const token=accessToken;
  listPending=true;buttons();
  try{
    const list=await api('/api/v1/media-sessions');
    if(token!==accessToken||room||connecting)return;
    const sessions=$('sessions');
    sessions.replaceChildren();
    for(const session of list){
      const li=document.createElement('li'),text=document.createElement('span'),button=document.createElement('button');
      text.textContent=session.sessionId+' · '+session.mode+' · '+session.state+' ';
      button.textContent='Nhận / Kết nối';
      button.onclick=()=>connect(()=>session).catch(e=>message(e.message,true));
      li.append(text,button);sessions.append(li);
    }
    if(!list.length){
      const li=document.createElement('li');
      li.textContent='Không có phiên đang mở trong phạm vi tài khoản.';
      sessions.append(li);
    }
  }catch(e){
    if(token===accessToken){
      if(e.status===401)clearSignIn('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      else message(e.message,true);
    }
  }finally{listPending=false;buttons();}
}
async function refreshPatrolAlerts(){
  if(!accessToken||patrolAlertsPending)return;
  const token=accessToken;
  const list=$('patrolAlerts'),status=$('patrolAlertStatus');
  patrolAlertsPending=true;
  try{
    const page=await api('/api/v1/alerts?type=PATROL_RESTRICTED_INTRUSION&status=OPEN&size=10');
    if(token!==accessToken)return;
    const items=page.content||[];
    status.textContent=items.length?'':'Không có cảnh báo tuần tra đang mở.';
    list.replaceChildren(...items.map(alert=>{
      const item=document.createElement('li'), label=document.createElement('span'), acknowledge=document.createElement('button');
      const time=new Date(alert.dataJson?.detected_at||alert.createdAt).toLocaleString('vi-VN');
      label.textContent=alert.message+' · '+time;
      if(alert.dataJson?.routing_status&&alert.dataJson.routing_status!=='ASSIGNED')
        label.textContent+=' · Chưa phân công staff ('+alert.dataJson.routing_status+')';
      acknowledge.textContent='Đã nhận';
      acknowledge.onclick=async()=>{
        acknowledge.disabled=true;
        try{
          await api('/api/v1/alerts/'+alert.alertId+'/acknowledge',{});
          if(token===accessToken){item.remove();await refreshPatrolAlerts();}
        }catch(error){if(token===accessToken)status.textContent=error.message;}
        finally{acknowledge.disabled=false;}
      };
      item.append(label,acknowledge);
      return item;
    }));
  }catch(error){
    if(token===accessToken)status.textContent='Chưa tải được cảnh báo tuần tra: '+error.message;
  }finally{patrolAlertsPending=false;}
}
function refreshPanel(){return Promise.all([refresh(),refreshPatrolAlerts()]);}
$('staffLogin').addEventListener('submit',event=>{
  event.preventDefault();
  const username=$('username').value.trim(),password=$('password').value;
  $('password').value='';
  return signIn(async()=>{
    const credentials=await request('/api/v1/auth/login',{username,password});
    if(credentials.mustChangePassword)throw Error('Đổi mật khẩu trước khi nhận SOS.');
    return credentials.accessToken;
  });
});
$('tokenSignIn').onclick=()=>signIn(()=>{
  const token=$('token').value.trim();
  $('token').value='';
  if(!token)throw Error('Cần access token Staff/Admin.');
  return token;
});
$('signOut').onclick=()=>{if(!room&&!connecting)clearSignIn('Đã thoát trang SOS.');};
$('create').onclick=()=>connect(()=>{
  const body={robotId:$('robot').value.trim(),mode:$('mode').value};
  if($('incident').value.trim())body.incidentId=$('incident').value.trim();
  return api('/api/v1/media-sessions',body);
}).catch(e=>message(e.message,true));
$('refresh').onclick=refreshPanel;
$('confirmFall').onclick=()=>reviewOperation(true);
$('showResponseRoute').onclick=()=>reviewOperation(false);
$('video').addEventListener('timeupdate',()=>{
  if(room&&!$('video').requestVideoFrameCallback&&$('video').srcObject){lastFrame=performance.now();reviewButtons();}
});
setInterval(()=>{if(!document.hidden)refreshPanel();},7000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshPanel();});
$('enableAudio').onclick=()=>room?.startAudio().catch(e=>message(e.message,true));
$('mic').onclick=async()=>{try{if(room)await room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled,{echoCancellation:true,noiseSuppression:true});}catch(e){message(e.message,true);}};
$('end').onclick=async()=>{try{if(current)await api('/api/v1/media-sessions/'+current.sessionId+'/end',{reason:'STAFF_ENDED'});await leave();message('Đã kết thúc phiên.');}catch(e){message(e.message,true);}};
window.addEventListener('pagehide',()=>{if(room)room.disconnect();});
const query=new URLSearchParams(location.search);
if(query.get('incidentId')){
  $('incident').value=query.get('incidentId');
  $('manualCall').open=true;
}
authView('');
})();
