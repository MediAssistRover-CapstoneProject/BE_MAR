(function(root,factory){
  const editor=factory();
  if(typeof module==='object'&&module.exports)module.exports=editor;else root.EndpointGroupEditor=editor;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  function policyRequest(values){
    const fallbackTrigger=values.fallbackTrigger??'ON_PATH_UNAVAILABLE';
    if(!['ON_PATH_UNAVAILABLE','NEAR_DOOR_OBSTRUCTION'].includes(fallbackTrigger))throw Error('Chọn cách chuyển sang điểm dự phòng.');
    const raw=values.pathSearchBudgetSec??10,pathSearchBudgetSec=Number(raw);
    if(raw===''||!Number.isInteger(pathSearchBudgetSec)||pathSearchBudgetSec<5||pathSearchBudgetSec>30)
      throw Error('Thời gian tìm đường phải là số nguyên từ 5 đến 30 giây.');
    return {fallbackTrigger,pathSearchBudgetSec};
  }
  function updatePolicyRequest(group,values){
    if(!group||!UUID.test(group.endpointGroupId||'')||!UUID.test(group.mapId||'')||!UUID.test(group.roomId||'')
      ||group.selectionPolicy!=='PRIORITY')throw Error('Chọn nhóm hai điểm thuộc cùng một phòng.');
    const policy=policyRequest(values);
    return {mapId:group.mapId,groupCode:group.groupCode,groupName:group.groupName,selectionPolicy:group.selectionPolicy,
      allowFallback:policy.fallbackTrigger==='ON_PATH_UNAVAILABLE'?true:!!group.allowFallback,description:group.description??null,
      active:!!group.active,roomId:group.roomId,fallbackRadiusM:group.fallbackRadiusM??.75,...policy};
  }
  function pairRequest(values,endpoints){
    for(const field of ['mapId','roomId','primaryEndpointId','fallbackEndpointId'])
      if(!UUID.test(values[field]||''))throw Error('Chọn bản đồ, phòng và hai endpoint từ danh sách.');
    if(values.primaryEndpointId===values.fallbackEndpointId)throw Error('Điểm bên trong và điểm ngoài cửa phải khác nhau.');
    for(const id of [values.primaryEndpointId,values.fallbackEndpointId]){
      const endpoint=endpoints.find(row=>row.endpointId===id);
      if(!endpoint||endpoint.mapId!==values.mapId||endpoint.roomId!==values.roomId||!endpoint.active||!endpoint.accessible)
        throw Error('Hai endpoint phải đang bật, có thể tiếp cận và thuộc cùng phòng trên map đã chọn.');
    }
    const groupCode=String(values.groupCode||'').trim(),groupName=String(values.groupName||'').trim();
    if(!groupCode||groupCode.length>64||!groupName||groupName.length>160)throw Error('Nhập mã nhóm (tối đa 64 ký tự) và tên nhóm (tối đa 160 ký tự).');
    const fallbackRadiusM=Number(values.fallbackRadiusM);
    if(values.fallbackRadiusM===''||!Number.isFinite(fallbackRadiusM)||fallbackRadiusM<.1||fallbackRadiusM>2)
      throw Error('Bán kính điểm ngoài cửa phải từ 0,1 đến 2 m.');
    return {mapId:values.mapId,roomId:values.roomId,groupCode,groupName,primaryEndpointId:values.primaryEndpointId,
      fallbackEndpointId:values.fallbackEndpointId,fallbackRadiusM,setAsRoomDefault:!!values.setAsRoomDefault,...policyRequest(values)};
  }
  function mount(options){
    const host=options.element;
    host.innerHTML=`<h2>Hai điểm đến cho cùng một phòng</h2>
      <p>Đặt và lưu hai endpoint trên bản đồ: điểm chính trong phòng, điểm dự phòng ngoài cửa. Gán cả hai vào cùng phòng ở mục endpoint, rồi chọn tại đây.</p>
      <button id="roomPairLoad" type="button">Tải map, phòng và nhóm</button><button id="roomPairUseMap" type="button">Dùng map đang mở</button>
      <div id="roomPairFields" class="fields">
      <label>Bản đồ / lọc nhóm<select id="roomPairMap"><option value="">Chọn map</option></select></label>
      <label>Phòng<select id="roomPairRoom"><option value="">Chọn map trước</option></select></label>
      <label>Mã nhóm<input id="roomPairCode" maxlength="64" placeholder="Ví dụ: PHONG_01_ARRIVAL"></label>
      <label>Tên nhóm<input id="roomPairName" maxlength="160" placeholder="Ví dụ: Điểm đến phòng 01"></label>
      <label>1. Điểm chính bên trong<select id="roomPairPrimary"><option value="">Chọn phòng trước</option></select></label>
      <label>2. Điểm đến dự phòng ngoài cửa<select id="roomPairFallback"><option value="">Chọn phòng trước</option></select></label>
      <label>Chuyển sang điểm dự phòng khi<select id="roomPairTrigger"><option value="ON_PATH_UNAVAILABLE">Không tìm được đường sau thời gian đã chọn</option><option value="NEAR_DOOR_OBSTRUCTION">Bị chặn khi đã tới gần cửa (cách cũ)</option></select></label>
      <label>Thời gian tìm đường cho mỗi điểm (giây)<input id="roomPairBudget" type="number" min="5" max="30" step="1" value="10"></label>
      <label>Bán kính gần cửa, chỉ dùng cho cách cũ (m)<input id="roomPairRadius" type="number" min="0.1" max="2" step="0.05" value="0.75"></label>
      <label><input id="roomPairDefault" type="checkbox" checked style="width:auto"> Dùng nhóm này làm điểm đến mặc định của phòng</label></div>
      <small id="roomPairPolicyHint">Robot ưu tiên điểm 1. Nếu không tìm được đường trong 10 giây, robot dừng xác nhận rồi thử điểm 2. Lỗi cảm biến hoặc mất định vị không tự chuyển điểm.</small>
      <button id="roomPairSave" type="button" disabled>Lưu nhóm hai điểm</button>
      <p id="roomPairStatus" role="status" style="white-space:pre-line">Tải danh sách bằng token ADMIN ở đầu trang. Chưa có thay đổi nào được lưu.</p>
      <h3>Đổi cách chuyển điểm cho nhóm đã lưu</h3><label>Nhóm hai điểm cùng phòng<select id="roomPairExisting"><option value="">Chọn nhóm đã lưu</option></select></label>
      <small>Chọn nhóm để xem cấu hình hiện tại. Chỉnh cách chuyển điểm và thời gian ở phía trên, rồi bấm cập nhật khi không có nhiệm vụ đang dùng nhóm. Không đổi hai endpoint hoặc điểm mặc định của phòng.</small>
      <button id="roomPairApplyPolicy" type="button" disabled>Cập nhật cách chuyển điểm cho nhóm đã chọn</button>
      <h3>Nhóm endpoint trên map đã chọn</h3><div id="roomPairList" style="overflow-x:auto"></div>`;
    const $=id=>host.querySelector('#'+id);
    const state={maps:[],rooms:[],endpoints:[],groups:[],members:[],token:'',busy:false,loading:false,generation:0,closed:false};
    const token=()=>String(options.getToken()||'').trim().replace(/^Bearer\s+/i,'');
    const say=message=>{$('roomPairStatus').textContent=message;};
    function fill(control,rows,key,label,placeholder,selected=''){
      control.replaceChildren(new Option(placeholder,''));
      for(const row of rows)control.add(new Option(label(row),row[key]));
      control.value=rows.some(row=>row[key]===selected)?selected:'';
    }
    function current(context){
      if(state.closed||context.generation!==state.generation||context.token!==token())throw Error('Phiên đăng nhập hoặc map đã đổi. Tải lại danh sách.');
    }
    async function api(path,context,body,method=body===undefined?'GET':'POST'){
      current(context);
      const response=await fetch(path,{method,headers:{Accept:'application/json',Authorization:'Bearer '+context.token,
        ...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),
        credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(15000)});
      const text=await response.text();let result;try{result=text?JSON.parse(text):null;}catch{result=null;}
      current(context);
      if(!response.ok)throw Error(result?.message||'Không xử lý được yêu cầu (HTTP '+response.status+').');
      return result;
    }
    async function pages(path,context){
      const rows=[];
      for(let page=0;page<100;page++){
        const result=await api(path+(path.includes('?')?'&':'?')+'page='+page+'&size=100',context);
        if(!Array.isArray(result?.content))throw Error('Danh sách API không đúng định dạng.');
        rows.push(...result.content);
        if(result.last===true||result.content.length===0||Number.isInteger(result.totalPages)&&page+1>=result.totalPages||result.totalPages==null&&result.content.length<100)return rows;
      }
      throw Error('Danh sách quá lớn; cần thu hẹp dữ liệu trước khi cấu hình.');
    }
    function renderList(){
      const list=$('roomPairList'),mapId=$('roomPairMap').value;list.replaceChildren();
      const groups=state.groups.filter(group=>group.mapId===mapId);
      fill($('roomPairExisting'),groups.filter(group=>group.roomId&&group.selectionPolicy==='PRIORITY'),'endpointGroupId',
        group=>group.groupName+' · '+group.groupCode,'Chọn nhóm đã lưu',$('roomPairExisting').value);
      if(!groups.length){list.textContent=mapId?'Chưa có nhóm trên map này.':'Chọn map để xem nhóm.';return;}
      const table=document.createElement('table');table.style.cssText='border-collapse:collapse;width:100%;font-size:14px';
      const header=table.createTHead().insertRow();
      for(const label of ['Nhóm','Phòng','Thứ tự endpoint','Cách chuyển điểm','Thời gian tìm đường','Bán kính gần cửa','Trạng thái']){const cell=document.createElement('th');cell.textContent=label;cell.style.cssText='text-align:left;padding:8px;border-bottom:1px solid #bbb';header.append(cell);}
      for(const group of groups){
        const room=state.rooms.find(row=>row.roomId===group.roomId);
        const members=state.members.filter(row=>row.endpointGroupId===group.endpointGroupId).sort((a,b)=>a.sequenceNo-b.sequenceNo);
        const points=members.map(member=>{
          const endpoint=state.endpoints.find(row=>row.endpointId===member.endpointId);
          return member.sequenceNo+'. '+(endpoint?.endpointCode||member.endpointId)+(member.active?'':' (tắt)');
        }).join(' → ')||'Chưa có thành viên';
        const values=[group.groupName+' · '+group.groupCode,room?(room.roomCode+' · '+room.roomName+(room.defaultEndpointGroupId===group.endpointGroupId?' (mặc định)':'')):'Nhóm chung',
          points,group.fallbackTrigger==='ON_PATH_UNAVAILABLE'?'Không tìm được đường':'Bị chặn gần cửa (cách cũ)',
          group.fallbackTrigger==='ON_PATH_UNAVAILABLE'?String(group.pathSearchBudgetSec??10)+' giây / điểm':'—',
          group.roomId&&group.fallbackTrigger!=='ON_PATH_UNAVAILABLE'?Number(group.fallbackRadiusM??.75).toLocaleString('vi-VN')+' m':'Không áp dụng',
          (group.active?'Đang bật':'Đã tắt')+' · '+group.selectionPolicy+(group.allowFallback?' · dự phòng':'')];
        const row=table.insertRow();for(const value of values){const cell=row.insertCell();cell.textContent=value;cell.style.cssText='padding:8px;border-bottom:1px solid #ddd;vertical-align:top';}
      }
      list.append(table);
    }
    function controls(){
      const locked=state.busy||state.loading;
      for(const input of $('roomPairFields').querySelectorAll('input,select'))input.disabled=locked;
      $('roomPairLoad').disabled=$('roomPairUseMap').disabled=locked;
      $('roomPairSave').disabled=locked||!$('roomPairMap').value||!$('roomPairRoom').value||!$('roomPairPrimary').value||!$('roomPairFallback').value;
      $('roomPairExisting').disabled=locked;
      $('roomPairApplyPolicy').disabled=locked||!$('roomPairExisting').value;
      $('roomPairRadius').disabled=locked||$('roomPairTrigger').value==='ON_PATH_UNAVAILABLE';
      $('roomPairBudget').disabled=locked||$('roomPairTrigger').value!=='ON_PATH_UNAVAILABLE';
      const onPath=$('roomPairTrigger').value==='ON_PATH_UNAVAILABLE';
      $('roomPairPolicyHint').textContent=onPath
        ?'Robot ưu tiên điểm 1. Nếu không tìm được đường trong '+$('roomPairBudget').value+' giây, robot dừng xác nhận rồi thử điểm 2. Lỗi cảm biến hoặc mất định vị không tự chuyển điểm.'
        :'Robot chỉ thử điểm 2 khi có bằng chứng lối vào bị chặn, đã dừng và đang trong bán kính gần điểm ngoài cửa. Thời gian tìm đường phía trên không áp dụng cho cách cũ.';
    }
    function endpoints(){
      const rows=state.endpoints.filter(row=>row.mapId===$('roomPairMap').value&&row.roomId===$('roomPairRoom').value&&row.active&&row.accessible);
      const primary=$('roomPairPrimary').value,fallback=$('roomPairFallback').value;
      fill($('roomPairPrimary'),rows.filter(row=>row.endpointId!==fallback),'endpointId',row=>row.endpointCode+' · '+row.endpointName,'Chọn điểm chính',primary);
      fill($('roomPairFallback'),rows.filter(row=>row.endpointId!==$('roomPairPrimary').value),'endpointId',row=>row.endpointCode+' · '+row.endpointName,'Chọn điểm ngoài cửa',fallback);
      controls();
    }
    function rooms(preferred){
      const map=state.maps.find(row=>row.mapId===$('roomPairMap').value),selected=preferred||$('roomPairRoom').value;
      fill($('roomPairRoom'),state.rooms.filter(row=>row.active&&map&&row.floorId===map.floorId),'roomId',row=>row.roomCode+' · '+row.roomName,'Chọn phòng',selected);
      endpoints();renderList();
    }
    async function load(preferred){
      if(state.busy||state.loading)return;
      const context={generation:++state.generation,token:token()};
      if(!context.token){say('Nhập access token ADMIN ở đầu trang rồi tải danh sách.');return;}
      state.loading=true;controls();say('Đang tải map, phòng, endpoint và nhóm…');
      try{
        const [maps,roomRows,endpointRows,groups,members]=await Promise.all([
          pages('/api/v1/maps',context),pages('/api/v1/rooms?includeDeleted=true',context),pages('/api/v1/endpoints?includeDeleted=true',context),
          pages('/api/v1/endpoint-groups?includeDeleted=true',context),pages('/api/v1/endpoint-group-members',context)]);
        current(context);Object.assign(state,{maps,rooms:roomRows,endpoints:endpointRows,groups,members,token:context.token});
        fill($('roomPairMap'),maps,'mapId',row=>row.mapName+' · '+row.mapCode+' · '+row.status,'Chọn map',preferred||$('roomPairMap').value||options.getNavigation?.()?.meta?.map_id);
        rooms();say('Đã tải danh sách. Chọn phòng và hai endpoint đã gán cùng phòng để lưu một nhóm.');
      }catch(error){say(error.message);}
      finally{state.loading=false;controls();}
    }
    async function save(){
      if(state.busy||state.loading)return;
      let body;try{
        if(state.token!==token())throw Error('Token đã đổi. Tải lại danh sách trước khi lưu.');
        body=pairRequest({mapId:$('roomPairMap').value,roomId:$('roomPairRoom').value,groupCode:$('roomPairCode').value,groupName:$('roomPairName').value,
          primaryEndpointId:$('roomPairPrimary').value,fallbackEndpointId:$('roomPairFallback').value,fallbackRadiusM:$('roomPairRadius').value,setAsRoomDefault:$('roomPairDefault').checked,
          fallbackTrigger:$('roomPairTrigger').value,pathSearchBudgetSec:$('roomPairBudget').value},state.endpoints);
      }catch(error){say(error.message);return;}
      state.busy=true;controls();say('Đang lưu nhóm và hai endpoint…');
      try{
        const result=await api('/api/v1/endpoint-groups/room-pair',{generation:state.generation,token:state.token},body);
        state.groups.push(result);
        state.members.push({endpointGroupId:result.endpointGroupId,endpointId:body.primaryEndpointId,sequenceNo:1,active:true},
          {endpointGroupId:result.endpointGroupId,endpointId:body.fallbackEndpointId,sequenceNo:2,active:true});
        if(body.setAsRoomDefault){const room=state.rooms.find(row=>row.roomId===body.roomId);if(room)room.defaultEndpointGroupId=result.endpointGroupId;}
        renderList();say('Đã lưu nhóm '+result.groupName+' với 2 điểm'+(body.setAsRoomDefault?' và đặt làm mặc định của phòng.':'.'));
      }catch(error){say(error.message+' Nếu kết nối gián đoạn, tải lại danh sách để kiểm tra nhóm trước khi lưu lại.');}
      finally{state.busy=false;controls();}
    }
    async function updatePolicy(){
      if(state.busy||state.loading)return;
      const id=$('roomPairExisting').value,mapId=$('roomPairMap').value,context={generation:state.generation,token:state.token};
      let policy;try{
        if(state.token!==token())throw Error('Token đã đổi. Tải lại danh sách trước khi cập nhật.');
        if(!state.groups.some(group=>group.endpointGroupId===id&&group.mapId===mapId))throw Error('Chọn nhóm trên map hiện tại.');
        policy=policyRequest({fallbackTrigger:$('roomPairTrigger').value,pathSearchBudgetSec:$('roomPairBudget').value});
      }catch(error){say(error.message);return;}
      state.busy=true;controls();say('Đang cập nhật cách chuyển điểm của nhóm đã chọn…');
      try{
        // Read current catalog fields before PUT so the policy button does not replay an old name/status.
        const group=await api('/api/v1/endpoint-groups/'+encodeURIComponent(id)+'?includeDeleted=true',context);
        if(group.mapId!==mapId)throw Error('Nhóm đã đổi map. Tải lại danh sách trước khi cập nhật.');
        const result=await api('/api/v1/endpoint-groups/'+encodeURIComponent(id),context,updatePolicyRequest(group,policy),'PUT');
        const index=state.groups.findIndex(row=>row.endpointGroupId===id);if(index>=0)state.groups[index]=result;
        renderList();say('Đã cập nhật cách chuyển điểm cho nhóm '+result.groupName+'. Tạo nhiệm vụ mới để dùng cấu hình vừa đổi.');
      }catch(error){say(error.message+' Nếu kết nối gián đoạn, tải lại danh sách để kiểm tra kết quả.');}
      finally{state.busy=false;controls();}
    }
    $('roomPairLoad').onclick=()=>load();
    $('roomPairUseMap').onclick=()=>{const mapId=options.getNavigation?.()?.meta?.map_id;if(!mapId){say('Mở bản đồ ở mục dẫn đường hoặc chọn map từ danh sách.');return;}load(mapId);};
    $('roomPairMap').onchange=()=>rooms();$('roomPairRoom').onchange=endpoints;
    $('roomPairPrimary').onchange=endpoints;$('roomPairFallback').onchange=endpoints;$('roomPairSave').onclick=save;
    $('roomPairTrigger').onchange=controls;$('roomPairBudget').oninput=controls;
    $('roomPairExisting').onchange=()=>{
      const group=state.groups.find(row=>row.endpointGroupId===$('roomPairExisting').value);
      if(group){$('roomPairTrigger').value=group.fallbackTrigger||'NEAR_DOOR_OBSTRUCTION';$('roomPairBudget').value=group.pathSearchBudgetSec??10;$('roomPairRadius').value=group.fallbackRadiusM??.75;}
      controls();
    };
    $('roomPairApplyPolicy').onclick=updatePolicy;
    window.addEventListener('pagehide',()=>{state.closed=true;state.generation++;});window.addEventListener('pageshow',()=>{state.closed=false;});controls();
    return {load};
  }
  return {pairRequest,policyRequest,updatePolicyRequest,mount};
});
