(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;else root.PatrolWorkflowBuilder=api;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const MAX_CHECKPOINTS=5;
  function request(values,checkpoints,endpoints,zones){
    const number=(name,min,max,integer=false)=>{
      const raw=values[name],n=Number(raw);
      if(raw===''||raw==null||!Number.isFinite(n)||n<min||n>max||integer&&!Number.isInteger(n))throw Error('Tham số '+name+' phải trong khoảng '+min+'–'+max+'.');
      return n;
    };
    const name=String(values.name||'').trim(),mapId=values.mapId;
    if(!name||name.length>160)throw Error('Tên tuyến cần 1–160 ký tự.');
    if(!UUID.test(mapId||''))throw Error('Chọn bản đồ tuần tra.');
    if(!checkpoints.length||checkpoints.length>MAX_CHECKPOINTS)throw Error('Chọn từ 1 đến '+MAX_CHECKPOINTS+' điểm tuần tra.');
    const items=checkpoints.map(point=>{
      const endpoint=endpoints.find(row=>row.id===point.endpointId);
      if(!endpoint||endpoint.raw.mapId!==mapId||endpoint.raw.active===false||endpoint.raw.accessible===false||endpoint.raw.yawRad==null)throw Error('Endpoint không còn hợp lệ trên map đã chọn. Tải lại dữ liệu.');
      if(!['PATROL_FALL','RESTRICTED_ZONE','CROWD_DENSITY'].includes(point.sensingType))throw Error('Chọn kiểu quan sát hợp lệ.');
      const item={endpointId:point.endpointId,sensingType:point.sensingType};
      if(point.sensingType!=='PATROL_FALL'){
        const zone=zones.find(row=>row.id===point.zoneId);
        if(!zone||zone.raw.mapId!==mapId||zone.raw.active===false||point.sensingType==='RESTRICTED_ZONE'&&zone.raw.zoneType!=='KEEP_OUT')throw Error('Chọn vùng đang bật trên đúng map; quan sát vùng cấm cần vùng KEEP_OUT.');
        item.zoneId=point.zoneId;
      }
      return item;
    });
    const sensingWindowSec=number('sensingWindowSec',1,30,true),fallDetection=!!values.fallDetection;
    if(sensingWindowSec<3&&(fallDetection||items.some(p=>p.sensingType==='PATROL_FALL')))throw Error('Quan sát té ngã cần ít nhất 3 giây tại điểm.');
    return {name,mapId,checkpoints:items,maxSpeedMps:number('maxSpeedMps',.05,.60),moveTimeoutSec:number('moveTimeoutSec',15,120,true),sensingWindowSec,fallDetection,repeatCount:number('repeatCount',0,1000,true)};
  }
  function mount(options){
    const host=options.element,catalog=RobotDataCatalog.create({getToken:options.getToken});
    host.innerHTML=`<h2>Cấu hình tuyến tuần tra</h2>
      <p>Chọn 1–5 điểm theo thứ tự. Mỗi điểm gồm MOVE → quan sát. Số vòng 0 là tuần tra liên tục; chỉ tạo job khi bạn bấm ở mục chạy workflow.</p>
      <button id="patrolLoad" type="button">Tải map và điểm tuần tra</button><button id="patrolUseMap" type="button">Dùng map đang mở</button>
      <div id="patrolConfig"><div class="fields">
      <label>Bản đồ<select id="patrolMap"><option value="">Chọn map</option></select></label>
      <label>Tên tuyến<input id="patrolName" maxlength="160" placeholder="Ví dụ: Tuần tra hành lang và các phòng"></label>
      <label>Endpoint sẽ đi tới<select id="patrolEndpoint"><option value="">Chọn map trước</option></select></label>
      <label>Quan sát tại điểm<select id="patrolSensing"><option value="PATROL_FALL">Người nghi té ngã</option><option value="RESTRICTED_ZONE">Người trong vùng cấm</option><option value="CROWD_DENSITY">Mật độ người</option></select></label>
      <label>Vùng quan sát<select id="patrolZone"><option value="">Không cần vùng cho té ngã</option></select></label>
      <label>Tốc độ MOVE (m/s)<input id="patrolSpeed" type="number" min="0.05" max="0.60" step="0.01" value="0.25"></label>
      <label>Timeout MOVE mỗi điểm (giây)<input id="patrolMoveTimeout" type="number" min="15" max="120" step="1" value="60"></label>
      <label>Quan sát tại mỗi điểm (giây)<input id="patrolWindow" type="number" min="1" max="30" step="1" value="3"></label>
      <label>Số vòng, 0 = liên tục<input id="patrolRepeats" type="number" min="0" max="1000" step="1" value="1"></label>
      </div><label><input id="patrolFall" type="checkbox" checked style="width:auto"> Bật kiểm tra té ngã tại các điểm quan sát</label>
      <button id="patrolAdd" type="button">Thêm điểm vào tuyến</button><ol id="patrolPoints"></ol>
      <small id="patrolMapStatus" role="status">Chọn map để tải endpoint và zone. Không tự tạo vùng cấm.</small></div>
      <button id="patrolPreview" type="button">Xem trước workflow</button><button id="patrolPublish" type="button">Lưu và publish tuyến</button>
      <p id="patrolStatus" role="status" style="white-space:pre-line"></p><details><summary>Workflow xem trước</summary><pre id="patrolJson"></pre></details>`;
    const $=id=>host.querySelector('#'+id);
    const state={mapId:'',endpoints:[],zones:[],points:[],busy:false,loading:false,generation:0,pending:null,token:'',closed:false};
    const token=()=>String(options.getToken()||'').trim().replace(/^Bearer\s+/i,'');
    function say(message){$('patrolStatus').textContent=message;}
    function fill(control,rows,placeholder,selected=''){
      control.replaceChildren(new Option(placeholder,''));for(const row of rows)control.add(new Option(row.label,row.id));
      control.value=rows.some(row=>row.id===selected)?selected:'';
    }
    function render(){
      const lock=state.busy||state.loading||!!state.pending;
      for(const el of $('patrolConfig').querySelectorAll('input,select,button'))el.disabled=lock;
      $('patrolLoad').disabled=$('patrolUseMap').disabled=lock;
      const kind=$('patrolSensing').value;
      const zones=state.zones.filter(row=>row.raw.active!==false&&(kind!=='RESTRICTED_ZONE'||row.raw.zoneType==='KEEP_OUT'));
      const current=$('patrolZone').value;fill($('patrolZone'),zones,kind==='PATROL_FALL'?'Không cần vùng cho té ngã':'Chọn vùng quan sát',current);
      $('patrolZone').disabled=lock||kind==='PATROL_FALL';
      $('patrolAdd').disabled=lock||state.points.length>=MAX_CHECKPOINTS||!$('patrolEndpoint').value||kind!=='PATROL_FALL'&&!zones.length;
      $('patrolPreview').disabled=state.busy||state.loading||!!state.pending||!state.points.length;
      $('patrolPublish').disabled=state.busy||state.loading||!state.points.length;
      $('patrolPublish').textContent=state.pending?'Gửi lại yêu cầu lưu tuyến cũ':'Lưu và publish tuyến';
      $('patrolPoints').replaceChildren();
      state.points.forEach((point,index)=>{
        const line=document.createElement('li'),ep=state.endpoints.find(row=>row.id===point.endpointId),z=state.zones.find(row=>row.id===point.zoneId);
        const label={PATROL_FALL:'Té ngã',RESTRICTED_ZONE:'Vùng cấm',CROWD_DENSITY:'Mật độ người'}[point.sensingType];
        line.append(document.createTextNode((ep?.label||point.endpointId)+' → '+label+(z?' · '+z.label:'')+' '));
        for(const [text,offset]of [['↑',-1],['↓',1],['Xóa',0]]){
          const button=document.createElement('button');button.type='button';button.textContent=text;
          button.disabled=lock||offset!==0&&(index+offset<0||index+offset>=state.points.length);
          button.onclick=()=>{if(offset===0)state.points.splice(index,1);else[state.points[index],state.points[index+offset]]=[state.points[index+offset],state.points[index]];render();};line.append(button);
        }
        $('patrolPoints').append(line);
      });
    }
    async function loadMap(mapId){
      const epoch=++state.generation,credential=token(),previousPoints=state.mapId===mapId?state.points:[],previousEndpoint=$('patrolEndpoint').value;
      state.loading=true;state.mapId=mapId;state.points=previousPoints;state.endpoints=[];state.zones=[];render();
      fill($('patrolEndpoint'),[],'Đang tải endpoint…');
      try{
        const [endpoints,zones]=await Promise.all([catalog.endpoints(mapId),catalog.zones(mapId)]);
        if(state.closed||epoch!==state.generation||credential!==token())return;
        state.endpoints=endpoints.filter(row=>row.raw.active!==false&&row.raw.accessible!==false&&row.raw.yawRad!=null);state.zones=zones.filter(row=>row.raw.active!==false);
        state.points=previousPoints.filter(point=>state.endpoints.some(row=>row.id===point.endpointId)&&(point.sensingType==='PATROL_FALL'||state.zones.some(row=>row.id===point.zoneId)));
        fill($('patrolEndpoint'),state.endpoints,'Chọn điểm sẽ đi tới',previousEndpoint);
        $('patrolMapStatus').textContent=state.endpoints.length+' endpoint · '+state.zones.length+' zone. '+(state.zones.some(row=>row.raw.zoneType==='KEEP_OUT')?'Có vùng cấm KEEP_OUT.':'Chưa có vùng KEEP_OUT trên map này; cần tạo vùng cấm trước khi kiểm tra xâm nhập.')+(state.points.length<previousPoints.length?' Đã bỏ điểm/vùng không còn hợp lệ; kiểm tra lại tuyến.':'');
      }catch(error){if(epoch===state.generation&&credential===token())say(error.message);}
      finally{if(epoch===state.generation){state.loading=false;render();}}
    }
    async function loadMaps(preferred){
      if(state.busy||state.pending)return;
      const epoch=++state.generation,credential=token();state.token=credential;state.loading=true;render();
      try{
        const rows=(await catalog.maps()).filter(row=>row.raw.status==='ACTIVE');
        if(state.closed||epoch!==state.generation||credential!==token())return;
        fill($('patrolMap'),rows,'Chọn map',preferred||state.mapId||options.getNavigation?.()?.meta?.map_id);
        state.loading=false;
        if($('patrolMap').value)await loadMap($('patrolMap').value);else{state.mapId='';state.points=[];state.endpoints=[];state.zones=[];say('Chọn map và thêm từng endpoint vào tuyến.');}
      }catch(error){if(epoch===state.generation)say(error.message);}
      finally{if(epoch===state.generation){state.loading=false;render();}}
    }
    function body(){
      if(state.token!==token())throw Error('Token đã thay đổi. Tải lại danh sách trước khi lưu tuyến.');
      return request({name:$('patrolName').value,mapId:$('patrolMap').value,maxSpeedMps:$('patrolSpeed').value,moveTimeoutSec:$('patrolMoveTimeout').value,sensingWindowSec:$('patrolWindow').value,repeatCount:$('patrolRepeats').value,fallDetection:$('patrolFall').checked},state.points,state.endpoints,state.zones);
    }
    async function post(path,data){
      const credential=token();if(!credential)throw Error('Nhập access token ADMIN ở đầu trang.');
      const response=await fetch(path,{method:'POST',headers:{Authorization:'Bearer '+credential,'Content-Type':'application/json'},body:JSON.stringify(data),signal:AbortSignal.timeout(20000)});
      const result=await response.json();
      if(!response.ok){const error=Error(result.message||'HTTP '+response.status);error.status=response.status;throw error;}
      if(credential!==token())throw Error('Đăng nhập đã thay đổi. Tải lại danh sách workflow để kiểm tra tuyến vừa lưu.');
      return result;
    }
    async function execute(publish){
      if(state.busy||state.loading)return;
      let data;try{data=publish&&state.pending?state.pending:body();}catch(error){say(error.message);return;}
      state.busy=true;if(publish)state.pending=data;render();
      try{
        const result=await post('/api/v1/admin/patrol-workflows'+(publish?'':'/preview'),data);
        $('patrolJson').textContent=JSON.stringify(result,null,2);
        if(publish){state.pending=null;catalog.invalidate();say('Đã publish tuyến '+data.name+' gồm '+data.checkpoints.length+' điểm. Chưa tạo job, robot chưa di chuyển. Chọn workflow ở phần chạy để bắt đầu.');try{await options.onPublished?.(result);}catch(error){say('Đã publish tuyến '+result.workflowId+'. Tải lại danh sách workflow để chọn: '+error.message);}}
        else say('Workflow xem trước hợp lệ. Chưa lưu tuyến hoặc tạo job.');
      }catch(error){if(publish&&error.status&&error.status<500)state.pending=null;say(error.message+(state.pending?' Có thể đã lưu tuyến; bấm gửi lại giữ nguyên yêu cầu, không đổi dữ liệu.':''));}
      finally{state.busy=false;render();}
    }
    $('patrolLoad').onclick=()=>{catalog.invalidate();loadMaps();};
    $('patrolUseMap').onclick=()=>{const map=options.getNavigation?.()?.meta?.map_id;if(!map){say('Mở bản đồ ở phần dẫn đường trước hoặc chọn từ danh sách map.');return;}loadMaps(map);};
    $('patrolMap').onchange=()=>loadMap($('patrolMap').value);
    $('patrolSensing').onchange=render;$('patrolEndpoint').onchange=render;
    $('patrolAdd').onclick=()=>{if(state.points.length>=MAX_CHECKPOINTS)return;const p={endpointId:$('patrolEndpoint').value,sensingType:$('patrolSensing').value};if(p.sensingType!=='PATROL_FALL')p.zoneId=$('patrolZone').value;try{request({name:'Kiểm tra điểm',mapId:state.mapId,maxSpeedMps:.25,moveTimeoutSec:60,sensingWindowSec:3,repeatCount:1,fallDetection:false},[p],state.endpoints,state.zones);state.points.push(p);render();}catch(error){say(error.message);}};
    $('patrolPreview').onclick=()=>execute(false);$('patrolPublish').onclick=()=>execute(true);
    window.addEventListener('pagehide',()=>{state.closed=true;state.generation++;});window.addEventListener('pageshow',()=>{state.closed=false;});render();
    return {loadMaps};
  }
  return {request,MAX_CHECKPOINTS,mount};
});
