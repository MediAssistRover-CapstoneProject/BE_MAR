/* One staff participant; the backend owns scope, assignment and call lifetime. */
(()=>{'use strict';
const $=id=>document.getElementById(id),sdk=window.LivekitClient;
let room=null,current=null,refreshTimer=null,lastFrame=0,frameHandle=null;
let accessToken='',signingIn=false,connecting=false,listPending=false;
let patrolAlertsPending=false;
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
  $('enableAudio').disabled=!active||connecting;
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
    const requested=await openSession();
    const session=await api('/api/v1/media-sessions/'+requested.sessionId+'/accept',{});
    const join=await api('/api/v1/media-sessions/'+session.sessionId+'/join-token',{});
    current=session;lastFrame=0;const active=new sdk.Room({adaptiveStream:true,dynacast:true});room=active;
    active.on(sdk.RoomEvent.TrackSubscribed,(track,publication,participant)=>{
      if(participant.identity!=='robot-'+session.robotId)return;
      if(track.kind===sdk.Track.Kind.Video){track.attach($('video'));message('Đã nhận camera robot.');}
      else if(track.kind===sdk.Track.Kind.Audio){const el=track.attach();el.autoplay=true;$('audio').appendChild(el);}
    });
    active.on(sdk.RoomEvent.TrackUnsubscribed,track=>track.detach().forEach(el=>{if(el!==$('video'))el.remove();}));
    active.on(sdk.RoomEvent.Reconnecting,()=>message('Đang kết nối lại cuộc gọi…'));
    active.on(sdk.RoomEvent.Reconnected,()=>message('Đã kết nối lại.'));
    active.on(sdk.RoomEvent.Disconnected,()=>{if(room===active){leave().catch(()=>{});message('Cuộc gọi đã ngắt. Kiểm tra trạng thái phiên trước khi mở lại.');}});
    message('Đang kết nối WebRTC; chờ camera robot…');buttons();
    await active.connect(join.url,join.token,{rtcConfig:{iceTransportPolicy:$('relay').checked?'relay':'all'}});
    if(session.mode==='ASSIST'){
      try{await active.localParticipant.setMicrophoneEnabled(true,{echoCancellation:true,noiseSuppression:true,autoGainControl:true});}
      catch(e){message('Đã kết nối. Chưa bật được micro staff; bấm nút micro để thử lại.',true);}
    }
    await active.startAudio().catch(()=>message('Bấm “Bật âm thanh” để nghe micro robot.'));
    if($('video').requestVideoFrameCallback){const seen=()=>{lastFrame=performance.now();if(room===active)frameHandle=$('video').requestVideoFrameCallback(seen);};frameHandle=$('video').requestVideoFrameCallback(seen);}
    refreshTimer=setInterval(async()=>{
      try{const state=await api('/api/v1/media-sessions/'+session.sessionId);
        if(room!==active)return;
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
