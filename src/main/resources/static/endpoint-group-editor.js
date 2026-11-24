(function(root,factory){
  const editor=factory();
  if(typeof module==='object'&&module.exports)module.exports=editor;else root.EndpointGroupEditor=editor;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
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
      fallbackEndpointId:values.fallbackEndpointId,fallbackRadiusM,setAsRoomDefault:!!values.setAsRoomDefault};
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
      <label>Bán kính nhận điểm ngoài cửa (m)<input id="roomPairRadius" type="number" min="0.1" max="2" step="0.05" value="0.75"></label>
      <label><input id="roomPairDefault" type="checkbox" checked style="width:auto"> Dùng nhóm này làm điểm đến mặc định của phòng</label></div>
      <small>Robot ưu tiên điểm 1. Điểm 2 chỉ được chọn khi có bằng chứng đường vào bị chặn, robot đã dừng và đang ở trong bán kính của điểm ngoài cửa.</small>
      <button id="roomPairSave" type="button" disabled>Lưu nhóm hai điểm</button>
      <p id="roomPairStatus" role="status" style="white-space:pre-line">Tải danh sách bằng token ADMIN ở đầu trang. Chưa có thay đổi nào được lưu.</p>
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
    async function api(path,context,body){
      current(context);
      const response=await fetch(path,{method:body===undefined?'GET':'POST',headers:{Accept:'application/json',Authorization:'Bearer '+context.token,
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
      if(!groups.length){list.textContent=mapId?'Chưa có nhóm trên map này.':'Chọn map để xem nhóm.';return;}
      const table=document.createElement('table');table.style.cssText='border-collapse:collapse;width:100%;font-size:14px';
      const header=table.createTHead().insertRow();
      for(const label of ['Nhóm','Phòng','Thứ tự endpoint','Bán kính','Trạng thái']){const cell=document.createElement('th');cell.textContent=label;cell.style.cssText='text-align:left;padding:8px;border-bottom:1px solid #bbb';header.append(cell);}
      for(const group of groups){
        const room=state.rooms.find(row=>row.roomId===group.roomId);
        const members=state.members.filter(row=>row.endpointGroupId===group.endpointGroupId).sort((a,b)=>a.sequenceNo-b.sequenceNo);
        const points=members.map(member=>{
          const endpoint=state.endpoints.find(row=>row.endpointId===member.endpointId);
          return member.sequenceNo+'. '+(endpoint?.endpointCode||member.endpointId)+(member.active?'':' (tắt)');
        }).join(' → ')||'Chưa có thành viên';
        const values=[group.groupName+' · '+group.groupCode,room?(room.roomCode+' · '+room.roomName+(room.defaultEndpointGroupId===group.endpointGroupId?' (mặc định)':'')):'Nhóm chung',
          points,group.roomId?Number(group.fallbackRadiusM??.75).toLocaleString('vi-VN')+' m':'—',
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
          primaryEndpointId:$('roomPairPrimary').value,fallbackEndpointId:$('roomPairFallback').value,fallbackRadiusM:$('roomPairRadius').value,setAsRoomDefault:$('roomPairDefault').checked},state.endpoints);
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
    $('roomPairLoad').onclick=()=>load();
    $('roomPairUseMap').onclick=()=>{const mapId=options.getNavigation?.()?.meta?.map_id;if(!mapId){say('Mở bản đồ ở mục dẫn đường hoặc chọn map từ danh sách.');return;}load(mapId);};
    $('roomPairMap').onchange=()=>rooms();$('roomPairRoom').onchange=endpoints;
    $('roomPairPrimary').onchange=endpoints;$('roomPairFallback').onchange=endpoints;$('roomPairSave').onclick=save;
    window.addEventListener('pagehide',()=>{state.closed=true;state.generation++;});window.addEventListener('pageshow',()=>{state.closed=false;});controls();
    return {load};
  }
  return {pairRequest,mount};
});
