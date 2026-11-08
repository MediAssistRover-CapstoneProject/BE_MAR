/* One staff participant; the backend owns scope, assignment and call lifetime. */
(()=>{'use strict';
const $=id=>document.getElementById(id),sdk=window.LivekitClient;
let room=null,current=null,refreshTimer=null,lastFrame=0,frameHandle=null;
function message(value,error=false){$('status').textContent=value;$('status').className=error?'error':'';}
async function api(path,body,keepalive=false){
  const token=$('token').value.trim();if(!token)throw Error('Cần access token Staff/Admin.');
  const result=await fetch(path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(body!==undefined?{'Content-Type':'application/json'}:{})},
    body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',keepalive});
  const value=await result.json();if(!result.ok)throw Error(value.message||('HTTP '+result.status));return value;
}
function buttons(){const active=!!room;$('create').disabled=active;$('end').disabled=!active;$('mic').disabled=!active||current?.mode!=='ASSIST';$('enableAudio').disabled=!active;}
async function leave(){
  const previous=room;room=null;current=null;clearInterval(refreshTimer);refreshTimer=null;
  if(frameHandle!=null&&$('video').cancelVideoFrameCallback)$('video').cancelVideoFrameCallback(frameHandle);frameHandle=null;
  if(previous){previous.removeAllListeners();await previous.disconnect();}
  $('video').srcObject=null;$('audio').replaceChildren();buttons();
}
async function connect(session){
  if(!sdk)throw Error('Chưa tải được WebRTC SDK.');
  if(room)throw Error('Kết thúc phiên hiện tại trước.');
  session=await api('/api/v1/media-sessions/'+session.sessionId+'/accept',{});
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
  try{
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
  }catch(e){await leave();throw e;}
}
async function refresh(){
  const list=await api('/api/v1/media-sessions');$('sessions').replaceChildren();
  for(const session of list){const li=document.createElement('li'),text=document.createElement('span'),button=document.createElement('button');
    text.textContent=session.sessionId+' · '+session.mode+' · '+session.state+' ';button.textContent='Nhận / Kết nối';
    button.onclick=()=>connect(session).catch(e=>message(e.message,true));li.append(text,button);$('sessions').append(li);}
  if(!list.length){const li=document.createElement('li');li.textContent='Không có phiên đang mở trong phạm vi tài khoản.';$('sessions').append(li);}
}
$('create').onclick=async()=>{try{const body={robotId:$('robot').value.trim(),mode:$('mode').value};
  if($('incident').value.trim())body.incidentId=$('incident').value.trim();
  const session=await api('/api/v1/media-sessions',body);await connect(session);
}catch(e){message(e.message,true);}};
$('refresh').onclick=()=>refresh().catch(e=>message(e.message,true));
$('enableAudio').onclick=()=>room?.startAudio().catch(e=>message(e.message,true));
$('mic').onclick=async()=>{try{if(room)await room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled,{echoCancellation:true,noiseSuppression:true});}catch(e){message(e.message,true);}};
$('end').onclick=async()=>{try{if(current)await api('/api/v1/media-sessions/'+current.sessionId+'/end',{reason:'STAFF_ENDED'});await leave();message('Đã kết thúc phiên.');}catch(e){message(e.message,true);}};
window.addEventListener('pagehide',()=>{if(current)api('/api/v1/media-sessions/'+current.sessionId+'/end',{reason:'STAFF_PAGE_CLOSED'},true).catch(()=>{});if(room)room.disconnect();});
const query=new URLSearchParams(location.search);if(query.get('incidentId'))$('incident').value=query.get('incidentId');
buttons();
})();
