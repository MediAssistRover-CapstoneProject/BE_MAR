(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.RobotDataCatalog=api;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const PAGE_SIZE=100,MAX_PAGES=100,CACHE_TTL_MS=30000;
  const text=value=>value==null?'':String(value).trim();
  const validId=value=>UUID.test(text(value));
  const sameId=(a,b)=>text(a).toLowerCase()===text(b).toLowerCase();
  const optionalId=value=>value==null||text(value)==='';
  function label(parts,id){
    const unique=[...new Set(parts.map(text).filter(Boolean))];
    return (unique.length?unique.join(' · '):'Không có tên')+' · '+id.slice(0,8);
  }
  function state(row){return row.status||(typeof row.active==='boolean'?(row.active?'ACTIVE':'INACTIVE'):'');}
  function choices(rows,idKey,parts,mapId){
    const seen=new Set(),result=[];
    for(const raw of rows){
      const id=text(raw?.[idKey]);
      if(!validId(id)||seen.has(id.toLowerCase()))continue;
      seen.add(id.toLowerCase());
      const item={id,label:label(parts(raw),id),raw};
      const map=typeof mapId==='function'?mapId(raw):mapId;
      if(validId(map))item.mapId=text(map);
      result.push(item);
    }
    return result;
  }
  function requestMap(row){return row.mapId||row.requestDataJson?.map_id||row.requestDataJson?.mapId;}
  function create(options){
    if(typeof options?.getToken!=='function')throw new TypeError('RobotDataCatalog cần getToken.');
    const cache=new Map(),pending=new Map();
    let currentToken='',generation=0;
    function token(){return text(options.getToken()).replace(/^Bearer\s+/i,'').trim();}
    function invalidate(){generation++;cache.clear();pending.clear();}
    function scope(){
      const value=token();
      if(value!==currentToken){invalidate();currentToken=value;}
      if(!value)throw Error('Nhập Bearer token để tải danh sách dữ liệu.');
      return {token:value,generation};
    }
    function assertCurrent(context){
      if(context.generation!==generation||context.token!==token())throw Error('Phiên đăng nhập hoặc danh sách đã thay đổi. Hãy tải lại dữ liệu.');
    }
    async function get(path,context){
      assertCurrent(context);
      const response=await fetch(path,{method:'GET',headers:{Accept:'application/json',Authorization:'Bearer '+context.token},cache:'no-store',credentials:'same-origin',signal:AbortSignal.timeout(15000)});
      assertCurrent(context);
      if(!response.ok){
        const error=Error('Không tải được danh sách dữ liệu (HTTP '+response.status+').');
        error.status=response.status;
        if(response.status===401)invalidate();
        throw error;
      }
      const data=await response.json();
      assertCurrent(context);
      return data;
    }
    function remembered(key,context,load){
      assertCurrent(context);
      const cached=cache.get(key);
      if(cached&&cached.expiresAt>Date.now())return Promise.resolve(cached.value);
      if(pending.has(key))return pending.get(key);
      const work=(async()=>{
        try{
          const value=await load();
          assertCurrent(context);
          cache.set(key,{value,expiresAt:Date.now()+CACHE_TTL_MS});
          return value;
        }catch(error){
          if(context.generation===generation)cache.delete(key);
          throw error;
        }finally{if(pending.get(key)===work)pending.delete(key);}
      })();
      pending.set(key,work);
      return work;
    }
    function list(path,context,query={}){
      const base=new URLSearchParams();
      for(const [key,value] of Object.entries(query))if(value!=null&&text(value)!=='')base.set(key,text(value));
      const key=path+'?'+base.toString();
      return remembered(key,context,async()=>{
        const all=[];
        for(let page=0;page<MAX_PAGES;page++){
          const params=new URLSearchParams(base);
          params.set('page',String(page));params.set('size',String(PAGE_SIZE));
          const data=await get(path+'?'+params.toString(),context);
          if(Array.isArray(data)){all.push(...data);return all;}
          if(!data||!Array.isArray(data.content))throw Error('Danh sách dữ liệu trả về không đúng định dạng.');
          all.push(...data.content);
          if(data.last===true||data.content.length===0||Number.isInteger(data.totalPages)&&page+1>=data.totalPages)return all;
          if(data.last==null&&data.totalPages==null&&data.content.length<PAGE_SIZE)return all;
        }
        throw Error('Danh sách vượt quá 10.000 mục. Hãy thu hẹp bộ lọc và tải lại.');
      });
    }
    function me(context){return remembered('/api/v1/auth/me',context,()=>get('/api/v1/auth/me',context));}
    function mapRows(path,mapId,context){return list(path,context).then(rows=>rows.filter(row=>sameId(row.mapId,mapId)));}
    function duty(mapId,context){return list('/api/v1/staff/on-duty',context,validId(mapId)?{mapId}:{});}
    async function maps(){
      return choices(await list('/api/v1/maps',scope()),'mapId',r=>[r.mapName,r.mapCode,'v'+r.versionNo,state(r)],r=>r.mapId);
    }
    async function robots(){
      return choices(await list('/api/v1/robots',scope()),'robotId',r=>[r.robotCode,r.robotName,state(r)],r=>r.currentMapId);
    }
    async function floors(){
      return choices(await list('/api/v1/floors',scope()),'floorId',r=>[r.floorCode,r.floorName,state(r)]);
    }
    async function endpoints(mapId){
      if(!validId(mapId))return [];
      return choices(await mapRows('/api/v1/endpoints',mapId,scope()),'endpointId',r=>[r.endpointCode,r.endpointName,r.endpointType,state(r),r.accessible===false?'Không tiếp cận được':''],mapId);
    }
    async function rooms(mapId){
      if(!optionalId(mapId)&&!validId(mapId))return [];
      const context=scope();
      const rows=await list('/api/v1/rooms',context);
      if(optionalId(mapId))return choices(rows,'roomId',r=>[r.roomCode,r.roomName,r.departmentCode,state(r)]);
      // A Room belongs to a floor, not directly to a map. Its active endpoints
      // establish whether it can be used on the selected map.
      const endpoints=await mapRows('/api/v1/endpoints',mapId,context);
      const ids=new Set(endpoints.filter(r=>r.active!==false&&validId(r.roomId)).map(r=>r.roomId.toLowerCase()));
      return choices(rows.filter(r=>ids.has(text(r.roomId).toLowerCase())),'roomId',r=>[r.roomCode,r.roomName,r.departmentCode,state(r)],mapId);
    }
    async function staff(mapId){
      if(!optionalId(mapId)&&!validId(mapId))return [];
      const context=scope();
      let rows;
      try{
        rows=await list('/api/v1/admin/staff',context);
        // The workflow expects Account IDs, never StaffProfile IDs.
        return choices(rows.map(r=>({...r,accountId:r.account?.accountId})),'accountId',r=>[r.staffCode,r.account?.fullName,r.account?.username,r.account?.status,r.active===false?'INACTIVE':'']);
      }catch(error){if(error.status!==403)throw error;}
      // STAFF cannot read the admin staff catalog. Their visible choices are
      // the current staff account and the map's on-duty staff.
      const [onDuty,current]=await Promise.all([duty(mapId,context),me(context)]);
      const self=current.role==='STAFF'?[{accountId:current.accountId,account:current,fullName:current.fullName}]:[];
      return choices([...onDuty,...self],'accountId',r=>[r.fullName,r.roomCode,r.account?.username,r.account?.status||'ON_DUTY']);
    }
    async function shifts(mapId){
      if(!optionalId(mapId)&&!validId(mapId))return [];
      return choices(await duty(mapId,scope()),'shiftId',r=>[r.fullName,r.roomCode,r.roomName,r.startsAt,r.endsAt,'ON_DUTY',r.deliverable===false?r.reason:''],mapId);
    }
    async function serviceRequests(mapId){
      if(!optionalId(mapId)&&!validId(mapId))return [];
      const rows=await list('/api/v1/service-requests',scope());
      return choices(rows.filter(r=>optionalId(mapId)||!validId(requestMap(r))||sameId(requestMap(r),mapId)),'serviceRequestId',r=>[r.requestCode,r.requestType,state(r)],requestMap);
    }
    async function jobs(robotId){
      if(!optionalId(robotId)&&!validId(robotId))return [];
      const rows=await list('/api/v1/jobs',scope(),validId(robotId)?{robotId}:{});
      return choices(rows.filter(r=>optionalId(robotId)||sameId(r.robotId,robotId)),'jobId',r=>[r.jobCode,state(r)],r=>r.mapId);
    }
    async function incidents(robotId){
      if(!optionalId(robotId)&&!validId(robotId))return [];
      // EmergencyController has no robotId filter; apply it after pagination.
      const rows=await list('/api/v1/emergency-incidents',scope());
      return choices(rows.filter(r=>optionalId(robotId)||sameId(r.robotId,robotId)),'incidentId',r=>[r.incidentType,r.severity,state(r),r.occurredAt],r=>r.mapId);
    }
    async function zones(mapId){
      if(!validId(mapId))return [];
      return choices(await mapRows('/api/v1/zones',mapId,scope()),'zoneId',r=>[r.zoneCode,r.zoneName,r.zoneType,state(r)],mapId);
    }
    async function endpointGroups(mapId){
      if(!validId(mapId))return [];
      return choices(await mapRows('/api/v1/endpoint-groups',mapId,scope()),'endpointGroupId',r=>[r.groupCode,r.groupName,r.selectionPolicy,state(r)],mapId);
    }
    async function endpointGroupMembers(mapId){
      if(!validId(mapId))return [];
      return mapRows('/api/v1/endpoint-group-members',mapId,scope());
    }
    async function workflows(serviceType){
      return choices(await list('/api/v1/workflows/published',scope(),{serviceType}),'workflowId',r=>[r.workflowName,r.workflowCode,'v'+r.versionNo,r.serviceType,r.status]);
    }
    return Object.freeze({maps,robots,floors,rooms,endpoints,staff,shifts,serviceRequests,jobs,incidents,zones,endpointGroups,endpointGroupMembers,workflows,invalidate,invalidateCache:invalidate});
  }
  return Object.freeze({create});
});
