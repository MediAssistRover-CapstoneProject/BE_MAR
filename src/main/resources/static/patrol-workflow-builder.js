(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;else root.PatrolWorkflowBuilder=api;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const MIN_CHECKPOINTS=3,MAX_CHECKPOINTS=11;
  const sameId=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.toLowerCase()===b.toLowerCase();
  const normalized=value=>Array.isArray(value)?value.map(normalized):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,normalized(value[key])])):value;
  const validEndpoint=(row,mapId)=>row&&sameId(row.raw.mapId,mapId)&&row.raw.active!==false&&row.raw.accessible!==false&&typeof row.raw.yawRad==='number'&&Number.isFinite(row.raw.yawRad);
  function eligibleGroups(endpointId,mapId,endpoints,groups,members){
    const primary=endpoints.find(row=>sameId(row.id,endpointId));
    if(!primary?.raw.roomId)return [];
    return groups.filter(group=>{
      const raw=group.raw;
      if(!sameId(raw.mapId,mapId)||raw.active===false||raw.allowFallback!==true||raw.selectionPolicy!=='PRIORITY'||!sameId(raw.roomId,primary.raw.roomId))return false;
      const pair=members.filter(member=>sameId(member.endpointGroupId,group.id)&&member.active!==false).sort((a,b)=>a.sequenceNo-b.sequenceNo);
      return pair.length===2&&pair[0].sequenceNo===1&&pair[1].sequenceNo===2&&sameId(pair[0].endpointId,endpointId)&&pair.every(member=>{
        const endpoint=endpoints.find(row=>sameId(row.id,member.endpointId));
        return sameId(member.mapId,mapId)&&validEndpoint(endpoint,mapId)&&sameId(endpoint.raw.roomId,raw.roomId);
      })&&!sameId(pair[0].endpointId,pair[1].endpointId);
    });
  }
  function validGroups(mapId,endpoints,groups,members){
    const ids=new Set();
    for(const endpoint of endpoints)for(const group of eligibleGroups(endpoint.id,mapId,endpoints,groups,members))ids.add(group.id);
    return groups.filter(group=>ids.has(group.id));
  }
  function request(values,checkpoints,endpoints,groups=[],members=[]){
    const count=Number(values.checkpointCount??checkpoints.length),mapId=values.mapId;
    if(!Number.isInteger(count)||count<MIN_CHECKPOINTS||count>MAX_CHECKPOINTS)throw Error('Chọn mẫu từ 3 đến 11 điểm.');
    if(!UUID.test(mapId||''))throw Error('Chọn bản đồ tuần tra.');
    if(checkpoints.length!==count)throw Error('Mẫu '+count+' điểm cần đúng '+count+' điểm theo thứ tự.');
    const allowedGroups=validGroups(mapId,endpoints,groups,members);
    const items=checkpoints.map((point,index)=>{
      if(!!point.endpointId===!!point.endpointGroupId)throw Error('Điểm '+(index+1)+' cần chọn một endpoint hoặc một nhóm cùng phòng.');
      if(point.endpointGroupId){
        if(!UUID.test(point.endpointGroupId)||!allowedGroups.some(row=>sameId(row.id,point.endpointGroupId)))throw Error('Nhóm ở điểm '+(index+1)+' không còn hợp lệ. Cần hai endpoint cùng phòng/map, thứ tự 1 và 2. Tải lại dữ liệu.');
        return {endpoint_group_id:point.endpointGroupId};
      }
      if(!UUID.test(point.endpointId)||!endpoints.some(row=>sameId(row.id,point.endpointId)&&validEndpoint(row,mapId)))throw Error('Endpoint ở điểm '+(index+1)+' không còn hợp lệ trên map đã chọn. Tải lại dữ liệu.');
      return {endpoint_id:point.endpointId};
    });
    const repeat=Number(values.repeatCount),speed=Number(values.maxSpeedMps);
    if(values.repeatCount===''||values.repeatCount==null||!Number.isInteger(repeat)||repeat<0||repeat>1000)throw Error('Số vòng phải là số nguyên 0–1000; 0 là liên tục.');
    if(values.maxSpeedMps===''||values.maxSpeedMps==null||!Number.isFinite(speed)||speed<.05||speed>.35)throw Error('Tốc độ phải từ 0,05 đến 0,35 m/s.');
    return {map_id:mapId,checkpoints:items,patrol_repeat_count:repeat,max_speed_mps:speed};
  }
  function scheduleRequest(values,workflowId,robotId,inputs){
    if(!UUID.test(workflowId||'')||!UUID.test(robotId||''))throw Error('Chọn mẫu workflow và robot hợp lệ trước khi tạo lịch.');
    const code=String(values.scheduleCode||'').trim(),name=String(values.scheduleName||'').trim(),timezone=String(values.timezone||'').trim();
    if(!code||code.length>64||!name||name.length>160)throw Error('Nhập mã lịch (1–64 ký tự) và tên lịch (1–160 ký tự).');
    try{new Intl.DateTimeFormat('en',{timeZone:timezone}).format();}catch{throw Error('Múi giờ IANA không hợp lệ.');}
    if(!timezone)throw Error('Nhập múi giờ IANA.');
    const start=new Date(values.startsAt),end=values.endsAt?new Date(values.endsAt):null;
    if(!values.startsAt||!Number.isFinite(start.getTime()))throw Error('Chọn thời điểm bắt đầu lịch.');
    if(end&&(!Number.isFinite(end.getTime())||end<=start))throw Error('Thời điểm kết thúc phải sau thời điểm bắt đầu.');
    const result={workflowId,robotId,scheduleCode:code,scheduleName:name,scheduleType:values.scheduleType,timezone,startsAt:start.toISOString(),endsAt:end?end.toISOString():null,inputTemplateJson:inputs,priority:5,misfirePolicy:'SKIP'};
    if(values.scheduleType==='INTERVAL'){
      const interval=Number(values.intervalSec);
      if(!Number.isInteger(interval)||interval<60)throw Error('Khoảng lặp lịch tuần tra tối thiểu 60 giây.');
      result.intervalSec=interval;
    }else if(values.scheduleType==='CRON'){
      result.cronExpression=String(values.cronExpression||'').trim();
      if(!result.cronExpression||result.cronExpression.length>120)throw Error('Nhập biểu thức cron Spring gồm 6 trường.');
    }else if(values.scheduleType!=='ONCE')throw Error('Chọn kiểu lịch hợp lệ.');
    return result;
  }
  function mount(options){
    const host=options.element,catalog=RobotDataCatalog.create({getToken:options.getToken}),storageKey='mar.patrol-template-schedule.v1';
    host.innerHTML=`<style>
      #patrolWorkflowBuilder .patrol-point{margin:10px 0;padding:10px;border:1px solid #ddd;border-radius:6px}
      #patrolWorkflowBuilder .patrol-point .fields{grid-template-columns:minmax(140px,1fr) minmax(200px,3fr)}
      #patrolWorkflowBuilder .patrol-point label{margin:0}#patrolWorkflowBuilder .patrol-point small{margin:4px 0}
      @media(max-width:650px){#patrolWorkflowBuilder .patrol-point .fields{grid-template-columns:1fr}}
    </style><h2>Tuần tra theo mẫu 3–11 điểm</h2>
      <p>Chọn số điểm, bản đồ và điểm đến cho lần chạy. Mỗi điểm đi tới nơi rồi quan sát té ngã 3 giây. Mẫu được dùng lại; đổi tuyến không tạo workflow mới. Số vòng 0 là tuần tra liên tục.</p>
      <p>Theo dõi té ngã khi di chuyển/chờ và người trong vùng cấm hoạt động riêng với lượt quan sát tại điểm. Bật hai tùy chọn khi mở phiên robot trên Desktop; camera, đo khoảng cách và vị trí camera trên map cần sẵn sàng.</p>
      <small id="patrolMonitorStatus" role="status">Mở phiên robot để kiểm tra trạng thái theo dõi liên tục.</small>
      <button id="patrolLoad" type="button">Tải mẫu, bản đồ và điểm đến</button><button id="patrolUseMap" type="button">Dùng map đang mở</button>
      <div id="patrolConfig"><div class="fields">
      <label>Mẫu tuần tra<select id="patrolCount">${Array.from({length:9},(_,i)=>'<option value="'+(i+3)+'">Tuần tra '+(i+3)+' điểm</option>').join('')}</select></label>
      <label>Bản đồ<select id="patrolMap"><option value="">Chọn map</option></select></label>
      <label>Tốc độ MOVE (m/s)<input id="patrolSpeed" type="number" min="0.05" max="0.35" step="0.01" value="0.25"></label>
      <label>Số vòng, 0 = liên tục<input id="patrolRepeats" type="number" min="0" max="1000" step="1" value="1"></label>
      </div><small id="patrolTemplateStatus" role="status">Tải danh sách để xem mẫu đã có.</small>
      <ol id="patrolPoints"></ol><small id="patrolMapStatus" role="status">Chọn map để tải endpoint và nhóm cùng phòng.</small></div>
      <button id="patrolPreview" type="button">Xem trước inputs</button><button id="patrolPrepare" type="button">Chuẩn bị mẫu workflow</button><button id="patrolRun" type="button">Chạy tuần tra</button>
      <small>Chuẩn bị mẫu chỉ lưu workflow. Bấm Chạy tuần tra mới tạo job cho robot ở đầu trang; theo dõi, tạm dừng và hủy tại mục Chạy workflow đã xuất bản.</small>
      <details id="patrolSchedule"><summary>Đặt lịch tuần tra</summary><div id="patrolScheduleConfig"><div class="fields">
      <label>Mã lịch<input id="patrolScheduleCode" maxlength="64" placeholder="PATROL_MORNING"></label><label>Tên lịch<input id="patrolScheduleName" maxlength="160" placeholder="Tuần tra buổi sáng"></label>
      <label>Kiểu lịch<select id="patrolScheduleType"><option value="ONCE">Một lần</option><option value="INTERVAL">Lặp theo khoảng thời gian</option><option value="CRON">Cron</option></select></label>
      <label>Múi giờ IANA<input id="patrolTimezone" value="Asia/Ho_Chi_Minh" maxlength="64"></label>
      <label>Bắt đầu (giờ máy tính)<input id="patrolStartsAt" type="datetime-local"></label><label>Kết thúc (tùy chọn, giờ máy tính)<input id="patrolEndsAt" type="datetime-local"></label>
      <label id="patrolIntervalLabel" hidden>Khoảng lặp (giây, tối thiểu 60)<input id="patrolInterval" type="number" min="60" step="1" value="3600"></label>
      <label id="patrolCronLabel" hidden>Cron Spring (6 trường)<input id="patrolCron" placeholder="0 0 8 * * *" maxlength="120"></label>
      </div></div><button id="patrolCreateSchedule" type="button">Tạo lịch tạm dừng</button><button id="patrolActivateSchedule" type="button" disabled>Kích hoạt lịch vừa tạo</button>
      <small>Lịch lưu cùng map và thứ tự điểm bên trên, ưu tiên 5, bỏ qua lượt bị lỡ. Lịch mới ở trạng thái PAUSED; bấm Kích hoạt để cho phép chạy theo thời gian đã chọn.</small><p id="patrolScheduleStatus" role="status"></p></details>
      <p id="patrolStatus" role="status" style="white-space:pre-line"></p><details><summary>Inputs / kết quả API</summary><pre id="patrolJson"></pre></details>`;
    const $=id=>host.querySelector('#'+id);
    const state={mapId:'',endpoints:[],groups:[],members:[],templates:[],points:Array.from({length:3},()=>({})),busy:false,loading:false,generation:0,token:'',closed:false,schedule:null,pendingSchedule:null};
    const token=()=>String(options.getToken()||'').trim().replace(/^Bearer\s+/i,'');
    const count=()=>Number($('patrolCount').value),selectedTemplate=()=>state.templates.find(row=>row.checkpointCount===count()&&row.available&&UUID.test(row.workflowId||''));
    function say(message){$('patrolStatus').textContent=message;}
    function persist(){try{sessionStorage.setItem(storageKey,JSON.stringify({pending:state.pendingSchedule,schedule:state.schedule}));}catch{}}
    try{const saved=JSON.parse(sessionStorage.getItem(storageKey)||'null');if(saved?.pending?.scheduleCode&&UUID.test(saved.pending.workflowId||''))state.pendingSchedule=saved.pending;if(UUID.test(saved?.schedule?.scheduleId||''))state.schedule=saved.schedule;}catch{}
    async function api(path,data){
      const credential=token();if(!credential)throw Error('Nhập access token ADMIN ở đầu trang.');
      const response=await fetch(path,{method:data===undefined?'GET':'POST',headers:{Authorization:'Bearer '+credential,...(data===undefined?{}:{'Content-Type':'application/json'})},body:data===undefined?undefined:JSON.stringify(data),cache:'no-store',signal:AbortSignal.timeout(20000)});
      const raw=await response.text();let result;try{result=raw?JSON.parse(raw):null;}catch{result=null;}
      if(!response.ok){const error=Error(result?.message||result?.detail||'HTTP '+response.status);error.status=response.status;throw error;}
      if(credential!==token())throw Error('Token đã thay đổi. Tải lại danh sách để kiểm tra kết quả.');
      return result;
    }
    function fill(control,rows,placeholder,selected=''){
      control.replaceChildren(new Option(placeholder,''));for(const row of rows)control.add(new Option(row.label,row.id));
      control.value=rows.some(row=>sameId(row.id,selected))?rows.find(row=>sameId(row.id,selected)).id:'';
    }
    function monitorStatus(){
      const nav=options.getNavigation?.(),snapshot=nav?.snapshot;
      if(!snapshot?.mqttConnected||!snapshot.heartbeatFresh||!Number.isFinite(nav.receivedAt)||performance.now()-nav.receivedAt>15000){$('patrolMonitorStatus').textContent='Chưa có xác nhận mới từ robot về theo dõi liên tục. Lượt quan sát 3 giây không tự bật camera AI.';return;}
      const capabilities=snapshot.robot?.capabilitiesJson;
      $('patrolMonitorStatus').textContent=Array.isArray(capabilities)?'Phiên robot hiện tại: té ngã khi di chuyển/chờ '+(capabilities.includes('continuous-fall-v1')?'đã bật':'chưa bật')+' · vùng cấm khi tuần tra '+(capabilities.includes('continuous-restricted-v1')?'đã bật':'chưa bật')+'. Camera chỉ quan sát người trong khung hình.':'Phiên robot đã kết nối; chưa có thông tin về hai chế độ theo dõi liên tục.';
    }
    function render(){
      monitorStatus();const lock=state.busy||state.loading||!!state.pendingSchedule,template=selectedTemplate(),groups=validGroups(state.mapId,state.endpoints,state.groups,state.members);
      for(const el of host.querySelectorAll('#patrolConfig input,#patrolConfig select,#patrolConfig button,#patrolScheduleConfig input,#patrolScheduleConfig select'))el.disabled=lock;
      $('patrolLoad').disabled=$('patrolUseMap').disabled=lock;
      $('patrolPrepare').disabled=$('patrolPreview').disabled=$('patrolRun').disabled=lock;
      $('patrolCreateSchedule').disabled=state.busy||state.loading;
      $('patrolCreateSchedule').textContent=state.pendingSchedule?'Kiểm tra / gửi lại cùng lịch':'Tạo lịch tạm dừng';
      $('patrolActivateSchedule').disabled=lock||!state.schedule||state.schedule.status!=='PAUSED';
      $('patrolTemplateStatus').textContent=template?'Mẫu Tuần tra '+count()+' điểm đã sẵn sàng · '+template.workflowId:'Mẫu Tuần tra '+count()+' điểm chưa được tải hoặc chưa chuẩn bị. Bấm Chuẩn bị mẫu workflow trước khi chạy/đặt lịch.';
      $('patrolIntervalLabel').hidden=$('patrolScheduleType').value!=='INTERVAL';$('patrolCronLabel').hidden=$('patrolScheduleType').value!=='CRON';
      $('patrolPoints').replaceChildren();
      state.points.forEach((point,index)=>{
        const line=document.createElement('li');line.className='patrol-point';const fields=document.createElement('div');fields.className='fields';
        const modeLabel=document.createElement('label'),mode=document.createElement('select');modeLabel.append(document.createTextNode('Điểm '+(index+1)+' · cách chọn'),mode);mode.add(new Option('Một endpoint','endpoint'));mode.add(new Option('Nhóm hai điểm cùng phòng','group'));mode.value=point.mode||'endpoint';
        const targetLabel=document.createElement('label'),target=document.createElement('select');targetLabel.append(document.createTextNode(mode.value==='group'?'Nhóm điểm đến':'Endpoint'),target);
        fill(target,mode.value==='group'?groups:state.endpoints,'Chọn '+(mode.value==='group'?'nhóm':'endpoint'),point.endpointGroupId||point.endpointId);mode.disabled=target.disabled=lock;
        mode.onchange=()=>{state.points[index]={mode:mode.value};render();};target.onchange=()=>{state.points[index]=mode.value==='group'?{mode:'group',endpointGroupId:target.value}:{mode:'endpoint',endpointId:target.value};};
        fields.append(modeLabel,targetLabel);line.append(fields);const help=document.createElement('small');help.textContent=mode.value==='group'?'Robot thử điểm chính, rồi điểm dự phòng khi cần; chỉ quan sát sau khi đã đến nơi.':'Đi tới endpoint → quan sát té ngã 3 giây.';line.append(help);
        for(const [label,delta]of [['↑',-1],['↓',1]]){const button=document.createElement('button');button.type='button';button.textContent=label;button.setAttribute('aria-label',(delta<0?'Đưa lên':'Đưa xuống')+' điểm '+(index+1));button.disabled=lock||index+delta<0||index+delta>=state.points.length;button.onclick=()=>{[state.points[index],state.points[index+delta]]=[state.points[index+delta],state.points[index]];render();};line.append(button);}
        $('patrolPoints').append(line);
      });
      if(state.schedule)$('patrolScheduleStatus').textContent='Lịch '+state.schedule.scheduleCode+' · '+state.schedule.status+' · '+state.schedule.scheduleId;
    }
    async function loadMap(mapId){
      const epoch=++state.generation,credential=token(),previous=state.mapId===mapId?state.points:state.points.map(()=>({}));
      state.loading=true;state.mapId=mapId;state.points=previous;state.endpoints=[];state.groups=[];state.members=[];render();
      try{
        if(!mapId){$('patrolMapStatus').textContent='Chọn map để tải điểm đến.';return true;}
        const result=await Promise.allSettled([catalog.endpoints(mapId),catalog.endpointGroups(mapId),catalog.endpointGroupMembers(mapId)]);
        if(state.closed||epoch!==state.generation||credential!==token())return;
        if(result[0].status==='rejected')throw result[0].reason;
        state.endpoints=result[0].value.filter(row=>validEndpoint(row,mapId));
        state.groups=result[1].status==='fulfilled'?result[1].value:[];state.members=result[2].status==='fulfilled'?result[2].value:[];
        const groups=validGroups(mapId,state.endpoints,state.groups,state.members);let removed=0;
        state.points=previous.map(point=>{if(point.endpointId&&!state.endpoints.some(row=>sameId(row.id,point.endpointId))||point.endpointGroupId&&!groups.some(row=>sameId(row.id,point.endpointGroupId))){removed++;return {mode:point.mode};}return point;});
        const errors=result.slice(1).filter(row=>row.status==='rejected').map(row=>row.reason.message);
        $('patrolMapStatus').textContent=state.endpoints.length+' endpoint · '+groups.length+' nhóm hai điểm hợp lệ.'+(removed?' Đã bỏ '+removed+' điểm không còn hợp lệ; chọn lại trước khi chạy.':'')+(errors.length?' Chưa tải được nhóm: '+errors.join('; '):'');
        return true;
      }catch(error){if(epoch===state.generation)say(error.message);return false;}
      finally{if(epoch===state.generation){state.loading=false;render();}}
    }
    async function loadMaps(preferred){
      if(state.busy||state.pendingSchedule)return;
      const epoch=++state.generation,credential=token();state.loading=true;state.token=credential;render();
      try{
        const result=await Promise.allSettled([catalog.maps(),api('/api/v1/admin/patrol-workflows/templates')]);
        if(state.closed||epoch!==state.generation||credential!==token())return;
        const errors=[];
        if(result[1].status==='fulfilled'&&Array.isArray(result[1].value))state.templates=result[1].value;else{state.templates=[];errors.push('Mẫu workflow: '+(result[1].reason?.message||'phản hồi không hợp lệ'));}
        if(result[0].status==='rejected')throw result[0].reason;
        fill($('patrolMap'),result[0].value.filter(row=>row.raw.status==='ACTIVE'),'Chọn map',preferred||state.mapId||options.getNavigation?.()?.meta?.map_id);
        state.loading=false;const loaded=await loadMap($('patrolMap').value);if(loaded)say(errors.length?errors.join('\n'):'Đã tải danh sách. Chọn đủ '+count()+' điểm rồi chuẩn bị mẫu hoặc chạy.');
      }catch(error){if(epoch===state.generation)say(error.message);}
      finally{if(epoch===state.generation){state.loading=false;render();}}
    }
    function inputs(){
      if(state.token!==token())throw Error('Token đã thay đổi. Tải lại dữ liệu trước khi chạy hoặc tạo lịch.');
      return request({checkpointCount:count(),mapId:$('patrolMap').value,maxSpeedMps:$('patrolSpeed').value,repeatCount:$('patrolRepeats').value},state.points,state.endpoints,state.groups,state.members);
    }
    async function mutate(action){if(state.busy||state.loading)return;state.busy=true;render();try{await action();}catch(error){say(error.message);}finally{state.busy=false;render();}}
    async function prepare(){
      const checkpointCount=count(),result=await api('/api/v1/admin/patrol-workflows/templates',{checkpointCount});
      if(result?.checkpointCount!==checkpointCount||!result.available||!UUID.test(result.workflowId||''))throw Error('BE chưa trả về mẫu workflow hợp lệ. Tải lại danh sách để kiểm tra.');
      state.templates=state.templates.filter(row=>row.checkpointCount!==checkpointCount).concat(result);$('patrolJson').textContent=JSON.stringify(result,null,2);
      say('Mẫu Tuần tra '+checkpointCount+' điểm đã sẵn sàng. Chưa tạo job; bấm Chạy tuần tra để bắt đầu.');return result;
    }
    function requiredTemplate(){const template=selectedTemplate();if(!template)throw Error('Bấm Chuẩn bị mẫu workflow hoặc tải lại mẫu đã có trước.');return template;}
    async function findSchedule(body){
      for(let page=0;page<100;page++){
        const data=await api('/api/v1/schedules?includeDeleted=true&page='+page+'&size=100'),rows=Array.isArray(data)?data:data?.content;
        if(!Array.isArray(rows))throw Error('Danh sách lịch trả về không đúng định dạng.');
        const found=rows.find(row=>String(row.scheduleCode).toLowerCase()===body.scheduleCode.toLowerCase());
        if(found){
          const fields=['scheduleName','scheduleType','timezone','priority','misfirePolicy'];
          const matches=sameId(found.workflowId,body.workflowId)&&sameId(found.robotId,body.robotId)&&JSON.stringify(normalized(found.inputTemplateJson))===JSON.stringify(normalized(body.inputTemplateJson))&&fields.every(key=>found[key]===body[key])&&Date.parse(found.startsAt)===Date.parse(body.startsAt)&&(!found.endsAt&&!body.endsAt||Date.parse(found.endsAt)===Date.parse(body.endsAt))&&(found.intervalSec??null)===(body.intervalSec??null)&&(found.cronExpression??null)===(body.cronExpression??null);
          if(!matches){state.pendingSchedule=null;persist();throw Error('Mã lịch đã được dùng cho cấu hình khác. Chọn mã khác hoặc kiểm tra lịch đã có.');}
          return found;
        }
        if(Array.isArray(data)||data.last===true||!rows.length||Number.isInteger(data.totalPages)&&page+1>=data.totalPages||data.totalPages==null&&rows.length<100)return null;
      }
      throw Error('Chưa tải đủ lịch để đối chiếu yêu cầu cũ.');
    }
    $('patrolLoad').onclick=()=>{catalog.invalidate();loadMaps();};
    $('patrolUseMap').onclick=()=>{const map=options.getNavigation?.()?.meta?.map_id;if(!map){say('Mở bản đồ ở phần dẫn đường trước hoặc chọn từ danh sách.');return;}catalog.invalidate();loadMaps(map);};
    $('patrolMap').onchange=()=>loadMap($('patrolMap').value);
    $('patrolCount').onchange=()=>{const previous=state.points.length;state.points=Array.from({length:count()},(_,index)=>state.points[index]||{});say(previous>count()?'Đã bỏ các điểm sau vị trí '+count()+'. Kiểm tra lại thứ tự tuyến.':'Chọn đủ '+count()+' điểm theo thứ tự.');render();};
    $('patrolScheduleType').onchange=render;
    $('patrolPreview').onclick=()=>mutate(()=>{const value=inputs();$('patrolJson').textContent=JSON.stringify({workflowId:selectedTemplate()?.workflowId||null,inputs:value},null,2);say('Inputs hợp lệ trên dữ liệu vừa tải. Chưa tạo job hoặc lịch.');});
    $('patrolPrepare').onclick=()=>mutate(prepare);
    $('patrolRun').onclick=()=>mutate(async()=>{
      if(options.isMappingActive?.())throw Error('Kết thúc phiên quét bản đồ trước khi chạy tuần tra.');
      if(options.isWorkflowActive?.())throw Error('Đối chiếu yêu cầu chưa xác nhận hoặc kết thúc phiên điều khiển cũ trước khi thêm lượt tuần tra.');
      const value=inputs(),template=requiredTemplate(),nav=options.getNavigation?.();
      if(!sameId(nav?.meta?.map_id,value.map_id))throw Error('Mở đúng map đã chọn ở phần dẫn đường trước khi chạy.');
      if(typeof options.onRun!=='function')throw Error('Mục chạy workflow chưa sẵn sàng. Tải lại trang.');
      await options.onRun(template.workflowId,value);say('Đã chuyển yêu cầu sang mục Chạy workflow đã xuất bản. Xem Job ID, trạng thái hoặc nút gửi lại cùng yêu cầu tại đó.');
    });
    $('patrolCreateSchedule').onclick=()=>mutate(async()=>{
      let body=state.pendingSchedule;
      if(body){const found=await findSchedule(body);if(found){state.schedule=found;state.pendingSchedule=null;persist();say('Đã tìm thấy lịch từ yêu cầu trước. Kiểm tra trạng thái lịch bên trên.');return;}}
      else{
        const value=inputs(),template=requiredTemplate();body=scheduleRequest({scheduleCode:$('patrolScheduleCode').value,scheduleName:$('patrolScheduleName').value,scheduleType:$('patrolScheduleType').value,timezone:$('patrolTimezone').value,startsAt:$('patrolStartsAt').value,endsAt:$('patrolEndsAt').value,intervalSec:$('patrolInterval').value,cronExpression:$('patrolCron').value},template.workflowId,String(options.getRobotId?.()||'').trim(),value);
        state.pendingSchedule=body;persist();
      }
      try{const result=await api('/api/v1/schedules',body);if(!UUID.test(result?.scheduleId||''))throw Error('BE chưa trả về Schedule ID hợp lệ.');state.schedule=result;state.pendingSchedule=null;persist();$('patrolJson').textContent=JSON.stringify(result,null,2);say('Đã tạo lịch '+body.scheduleName+' ở trạng thái '+result.status+'. Bấm Kích hoạt lịch để cho phép chạy.');}
      catch(error){if([400,401,403,404,422].includes(error.status)){state.pendingSchedule=null;persist();}throw Error(error.message+(state.pendingSchedule?' Chưa xác nhận kết quả; bấm Kiểm tra / gửi lại cùng lịch để đối chiếu theo mã, không tạo mã mới.':''));}
    });
    $('patrolActivateSchedule').onclick=()=>mutate(async()=>{if(!state.schedule)throw Error('Tạo lịch trước.');state.schedule=await api('/api/v1/schedules/'+encodeURIComponent(state.schedule.scheduleId)+'/activate',{});persist();$('patrolJson').textContent=JSON.stringify(state.schedule,null,2);say('Lịch '+state.schedule.scheduleCode+' · '+state.schedule.status+'. BE sẽ kiểm tra robot và phiên navigation khi tới lượt chạy.');});
    window.addEventListener('pagehide',()=>{state.closed=true;state.generation++;});window.addEventListener('pageshow',()=>{state.closed=false;});
    if(state.pendingSchedule)say('Có yêu cầu tạo lịch chưa xác nhận từ phiên trước. Bấm Kiểm tra / gửi lại cùng lịch để đối chiếu.');render();
    return {loadMaps,refreshContext:monitorStatus,isActive:()=>!!state.pendingSchedule};
  }
  return {request,scheduleRequest,eligibleGroups,validGroups,MIN_CHECKPOINTS,MAX_CHECKPOINTS,mount};
});
