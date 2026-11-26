(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;else root.PublishedWorkflowRunner=api;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const TERMINAL=new Set(['SUCCEEDED','FAILED','CANCELLED']);
  const own=(object,key)=>Object.prototype.hasOwnProperty.call(object,key);
  const object=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
  const copy=value=>JSON.parse(JSON.stringify(value));
  const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
  function parseInputs(text){
    let value;try{value=JSON.parse(text);}catch{throw Error('Inputs JSON chưa đúng cú pháp.');}
    if(!object(value))throw Error('Inputs phải là một JSON object.');
    return value;
  }
  function initialInputs(workflow){
    const schema=workflow.inputSchemaJson||{},result={};
    for(const [key,field] of Object.entries(schema.properties||{})){
      if(own(field,'default'))Object.defineProperty(result,key,{value:copy(field.default),writable:true,enumerable:true,configurable:true});
      else if(own(field,'const'))Object.defineProperty(result,key,{value:copy(field.const),writable:true,enumerable:true,configurable:true});
    }
    return {...result,...(object(workflow.defaultInputsJson)?copy(workflow.defaultInputsJson):{})};
  }
  function fieldType(field){
    const types=Array.isArray(field.type)?field.type.filter(x=>x!=='null'):[field.type];
    return types.length===1&&types[0]?types[0]:undefined;
  }
  // Only common form constraints are checked here. The server validates the full schema.
  function validateInputs(value,schema,path='inputs',depth=0){
    if(!object(schema)||depth>32)return;
    if(own(schema,'const')&&!same(value,schema.const))throw Error(path+' phải bằng '+JSON.stringify(schema.const)+'.');
    if(Array.isArray(schema.enum)&&!schema.enum.some(x=>same(x,value)))throw Error(path+' chưa thuộc danh sách cho phép.');
    const types=Array.isArray(schema.type)?schema.type:[schema.type];
    const matches={null:value===null,object:object(value),array:Array.isArray(value),string:typeof value==='string',boolean:typeof value==='boolean',number:typeof value==='number'&&Number.isFinite(value),integer:Number.isInteger(value)};
    if(schema.type&&!types.some(type=>matches[type]))throw Error(path+' sai kiểu dữ liệu ('+types.join(' / ')+').');
    if(object(value)){
      for(const key of schema.required||[])if(!own(value,key))throw Error(path+'.'+key+' là trường bắt buộc.');
      for(const [key,item] of Object.entries(value)){
        if(schema.additionalProperties===false&&!own(schema.properties||{},key))throw Error(path+'.'+key+' không được khai báo trong schema.');
        validateInputs(item,schema.properties?.[key],path+'.'+key,depth+1);
      }
    }
    if(Array.isArray(value)){
      if(Number.isFinite(schema.minItems)&&value.length<schema.minItems||Number.isFinite(schema.maxItems)&&value.length>schema.maxItems)throw Error(path+' có số phần tử ngoài giới hạn.');
      value.forEach((item,index)=>validateInputs(item,schema.items,path+'['+index+']',depth+1));
    }
    if(typeof value==='string'){
      if(Number.isFinite(schema.minLength)&&value.length<schema.minLength||Number.isFinite(schema.maxLength)&&value.length>schema.maxLength)throw Error(path+' có độ dài ngoài giới hạn.');
      if(schema.format==='uuid'&&!UUID.test(value))throw Error(path+' phải là UUID.');
    }
    if(typeof value==='number'){
      if(Number.isFinite(schema.minimum)&&value<schema.minimum||Number.isFinite(schema.maximum)&&value>schema.maximum||Number.isFinite(schema.exclusiveMinimum)&&value<=schema.exclusiveMinimum||Number.isFinite(schema.exclusiveMaximum)&&value>=schema.exclusiveMaximum)throw Error(path+' nằm ngoài giới hạn.');
    }
  }
  function settled(detail){
    return TERMINAL.has(detail?.job?.status)&&!['PENDING','RECOVERY_REQUIRED'].includes(detail.job.stopStatus);
  }
  function actions(detail){
    const job=detail?.job,patrol=detail?.patrol,holds=patrol?.holds||[],manual=holds.some(hold=>hold.source==='MANUAL');
    const cancellable=!!job&&!job.cancelRequestedAt&&!TERMINAL.has(job.status)&&job.status!=='RECOVERY_REQUIRED';
    return {cancel:cancellable,suspend:cancellable&&!!patrol&&!manual&&['QUEUED','ACCEPTED','RUNNING'].includes(job.status),
      resume:cancellable&&!!patrol&&manual&&['SUSPENDING','SUSPENDED','RUNNING'].includes(job.status)};
  }
  function checkpointGroups(detail){
    if(!detail?.patrol)return null;
    const groups=new Map(),other=[];
    for(const step of (detail.steps||[]).slice().sort((a,b)=>(a.executionSequenceNo??0)-(b.executionSequenceNo??0))){
      const match=/^(?:CHECKPOINT|GROUP|MOVE|SENSE)_(\d+)(?:_|$)/.exec(step.stepCodeSnapshot||'');
      if(!match){other.push(step);continue;}
      const number=Number(match[1]);
      if(!groups.has(number))groups.set(number,{number,steps:[]});
      groups.get(number).steps.push(step);
    }
    return {groups:[...groups.values()].sort((a,b)=>a.number-b.number).map(group=>{
      const selected=group.steps.find(step=>step.jobStepId===detail.job?.selectedJobStepId),visited=group.steps.filter(step=>step.selectedAt||step.startedAt||step.endedAt||!['PENDING','NOT_VISITED'].includes(step.status)),latest=visited[visited.length-1]||group.steps[0];
      const recentSelection=visited.slice().reverse().find(step=>step.stepCodeSnapshot==='GROUP_'+group.number+'_SELECT');
      const blocked=recentSelection?.resultJson?.data?.checkpoint_status==='BLOCKED'
        && ['GROUP_'+group.number+'_SELECT','GROUP_'+group.number+'_HAS'].includes((selected||latest)?.stepCodeSnapshot);
      return {...group,current:!!selected&&!TERMINAL.has(detail.job?.status),latest:selected||latest,checkpointStatus:blocked?'BLOCKED':null,
        binding:detail.job?.inputJson?.checkpoints?.[group.number-1],snapshot:detail.job?.contextJson?.patrol_checkpoint_snapshot?.checkpoints?.[group.number-1]};
    }),other};
  }
  function mount(options){
    const host=options.element,storageKey='mar.published-workflow-runner.v1';
    host.innerHTML=`<style>
      #publishedWorkflowRunner select,#publishedWorkflowRunner textarea{width:100%;padding:9px;border:1px solid #999;border-radius:4px;background:white;font:inherit}
      #publishedWorkflowRunner .published-fields small{display:block;margin:4px 0 10px;overflow-wrap:anywhere}
      #publishedWorkflowRunner .published-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:0 16px}
      #publishedWorkflowRunner .published-fields label{min-width:0}
      #publishedWorkflowRunner textarea{font:13px ui-monospace,monospace;min-height:160px}
      #publishedWorkflowRunner .published-status{padding:10px;background:#eef4ff;white-space:pre-line}
      #publishedWorkflowRunner .published-error{color:#a51d1d}
      #publishedWorkflowRunner ol{padding-left:24px;line-height:1.7}
    </style>
    <h2>Chạy workflow đã xuất bản</h2>
    <p>Chọn workflow đã publish, điền inputs rồi tạo nhiệm vụ cho Robot ID ở đầu trang.</p>
    <button id="publishedLoad" type="button">Tải workflow đã xuất bản</button>
    <button id="publishedPrevious" type="button" disabled>Trang trước</button><button id="publishedNext" type="button" disabled>Trang sau</button>
    <small id="publishedPage">Chưa tải danh sách.</small>
    <div id="publishedConfiguration">
      <label>Workflow<select id="publishedWorkflow"><option value="">Chọn workflow đã xuất bản</option></select></label>
      <p id="publishedDescription"></p><small id="publishedMetadata"></small>
      <small id="publishedMapContext"></small>
      <p id="publishedDelivery" hidden>Giao hàng cần tạo yêu cầu giao hàng để gắn staff, QR/face. <a href="/delivery-qr.html">Mở trang giao nhận</a>.</p>
      <div id="publishedFields" class="published-fields"></div>
      <button id="publishedReloadCatalog" type="button">Tải lại danh sách dữ liệu</button>
      <label>Độ ưu tiên job (0–100)<input id="publishedPriority" type="number" min="0" max="100" step="1" value="50"></label>
      <details><summary>Inputs JSON nâng cao / schema</summary>
        <small>JSON là toàn bộ inputs gửi đi. Sửa các object, array hoặc trường phức tạp ở đây; các trường đơn giản bên trên sẽ đồng bộ khi rời ô JSON.</small>
        <label>Inputs JSON<textarea id="publishedInputs" spellcheck="false" aria-label="Inputs JSON">{}</textarea></label>
        <button id="publishedDefaults" type="button">Khôi phục inputs mặc định</button>
        <pre id="publishedSchema"></pre>
      </details>
      <details><summary>Điền ID từ map và endpoint đang mở</summary>
        <small id="publishedContext">Tải map ở phần dẫn đường để chọn ID.</small>
        <label>Trường input nhận ID<select id="publishedContextField"></select></label>
        <label>Giá trị từ map hiện tại<select id="publishedContextValue"></select></label>
        <button id="publishedUseContext" type="button">Điền vào input đã chọn</button>
        <small>Với trường array, giá trị được thêm vào danh sách. Chỉ chọn trường phù hợp với ý nghĩa của workflow.</small>
      </details>
      <details><summary>Các bước của workflow</summary><ol id="publishedDefinitionSteps"></ol></details>
    </div>
    <button id="publishedCreate" type="button" disabled>Tạo job từ workflow</button>
    <p id="publishedNotice" class="published-status" role="status" aria-live="polite">Tải danh sách workflow để bắt đầu.</p>
    <label>Job ID<input id="publishedJobId" placeholder="Hoặc dán Job ID để theo dõi"></label>
    <button id="publishedWatch" type="button">Theo dõi Job ID</button><button id="publishedRefresh" type="button" disabled>Cập nhật trạng thái</button><button id="publishedUnwatch" type="button">Ngừng theo dõi trên trang</button>
    <p id="publishedJobStatus" role="status">Chưa theo dõi nhiệm vụ.</p>
    <ol id="publishedJobSteps"></ol>
    <div id="publishedPatrol" hidden><h3>Tuần tra</h3><p id="publishedPatrolStatus"></p><ul id="publishedHolds"></ul><p id="publishedResumeReason"></p></div>
    <label>Lý do thao tác<input id="publishedReason" maxlength="1000" placeholder="Nhập lý do khi hủy, tạm dừng hoặc tiếp tục"></label>
    <button id="publishedCancel" type="button" disabled>Hủy nhiệm vụ</button><button id="publishedSuspend" type="button" hidden disabled>Tạm dừng tuần tra</button><button id="publishedResume" type="button" hidden disabled>Tiếp tục tuần tra</button>
    <details><summary>Chi tiết trạng thái nhiệm vụ</summary><pre id="publishedJobJson"></pre></details>`;
    const $=id=>host.querySelector('#'+id);
    const state={definition:null,page:0,pageCount:0,pending:null,detail:null,busy:false,polling:false,timer:null,closed:false,fields:[],fieldGeneration:0,referenceItems:new Map(),referenceLoads:new Set()};
    const catalog=globalThis.RobotDataCatalog?.create({getToken:options.getToken});
    const tokenIdentity=()=>String(options.getToken()||'').trim().replace(/^Bearer\s+/i,'');
    let catalogToken=tokenIdentity();
    try{
      const restored=JSON.parse(sessionStorage.getItem(storageKey)||'null');
      if(restored&&UUID.test(restored.robotId)&&UUID.test(restored.workflowId)&&UUID.test(restored.key)&&object(restored.body)&&(!restored.jobId||UUID.test(restored.jobId)))state.pending=restored;
    }catch{}
    function persist(){try{sessionStorage.setItem(storageKey,JSON.stringify(state.pending));}catch{}}
    const active=()=>!!state.pending&&!state.pending.done;
    const unknown=()=>active()&&!state.pending.jobId;
    const hasMove=()=>state.definition?.steps?.some(step=>step.enabled!==false&&step.stepTypeCode==='MOVE');
    const isPatrol=()=>state.definition?.workflow?.serviceType==='PATROL';
    const fixedMap=()=>isPatrol()?state.definition.workflow.defaultInputsJson?.map_id:null;
    const delivery=()=>state.definition?.workflow?.serviceType==='DELIVERY'||state.definition?.steps?.some(step=>step.enabled!==false&&step.stepTypeCode==='AUTHENTICATE');
    function say(message,error=false){$('publishedNotice').textContent=message;$('publishedNotice').classList.toggle('published-error',error);}
    async function api(path,body,key){
      const token=String(options.getToken()||'').trim().replace(/^Bearer\s+/i,'');
      if(!token)throw Error('Cần access token ở đầu trang.');
      const response=await fetch(path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(body===undefined?{}:{'Content-Type':'application/json'}),...(key?{'Idempotency-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',signal:AbortSignal.timeout(20000)});
      if(!response.ok){
        const raw=await response.text();let message=raw;
        try{const failure=JSON.parse(raw);message=failure.message||failure.detail||failure.error||raw;}catch{}
        const error=Error(response.status+' '+String(message).slice(0,800));error.status=response.status;throw error;
      }
      return response.status===204?null:response.json();
    }
    function render(){
      const locked=state.busy||unknown(),available=actions(state.detail);
      if(!locked&&state.definition&&(state.referencesDeferred||catalogToken!==tokenIdentity())){state.referencesDeferred=false;renderFields();return;}
      $('publishedLoad').disabled=locked;
      $('publishedPrevious').disabled=locked||state.page===0;
      $('publishedNext').disabled=locked||state.page+1>=state.pageCount;
      for(const input of $('publishedConfiguration').querySelectorAll('input,select,textarea,button'))input.disabled=locked||input.dataset.fixed==='true'||input.dataset.loading==='true';
      $('publishedCreate').disabled=state.busy||(!unknown()&&(!state.definition||delivery()));
      $('publishedCreate').textContent=unknown()?'Gửi lại cùng yêu cầu tạo job':'Tạo job từ workflow';
      $('publishedJobId').disabled=locked;
      $('publishedWatch').disabled=locked;
      $('publishedRefresh').disabled=state.busy||!state.pending;
      $('publishedUnwatch').disabled=state.busy||!state.pending||unknown();
      $('publishedCancel').disabled=state.busy||!available.cancel;
      $('publishedSuspend').hidden=$('publishedResume').hidden=!state.detail?.patrol;
      $('publishedSuspend').disabled=state.busy||!available.suspend;
      $('publishedResume').disabled=state.busy||!available.resume;
    }
    async function mutate(action){
      if(state.busy)return;state.busy=true;render();
      try{await action();}catch(error){say(error.message,true);}finally{state.busy=false;render();}
    }
    function inputs(){return parseInputs($('publishedInputs').value);}
    function setInputs(value,rebuild=true){$('publishedInputs').value=JSON.stringify(value,null,2);if(rebuild)renderFields();}
    const SOURCES={map:'maps',maps:'maps',robot:'robots',robots:'robots',floor:'floors',floors:'floors',room:'rooms',rooms:'rooms',endpoint:'endpoints',endpoints:'endpoints',staff:'staff',account:'staff',accounts:'staff',service_request:'serviceRequests',serviceRequests:'serviceRequests',service_requests:'serviceRequests',shift:'shifts',shifts:'shifts',zone:'zones',zones:'zones',endpoint_group:'endpointGroups',endpointGroups:'endpointGroups',endpoint_groups:'endpointGroups',job:'jobs',jobs:'jobs',incident:'incidents',incidents:'incidents'};
    const MAP_SOURCES=new Set(['endpoints','rooms','zones','endpointGroups','serviceRequests','shifts']);
    const FIELD_LABELS={map_id:'Bản đồ',floor_id:'Tầng',robot_id:'Robot',pickup_endpoint_id:'Điểm lấy hàng',delivery_endpoint_id:'Điểm giao hàng',endpoint_id:'Điểm đến',service_request_id:'Yêu cầu dịch vụ',expected_sender_account_id:'Nhân viên giao',expected_receiver_account_id:'Nhân viên nhận',sender_account_id:'Nhân viên giao',receiver_account_id:'Nhân viên nhận',zone_id:'Vùng quan sát',room_id:'Phòng',endpoint_group_id:'Nhóm điểm đến',compartment_id:'Ngăn hàng',patrol_repeat_count:'Số vòng tuần tra (0 = liên tục)'};
    function referenceSource(key,field,presentation){
      if(fieldType(field)!=='string')return null;
      const configured=presentation.dataSource||presentation.datasource||presentation.source;
      const name=typeof configured==='string'?configured:configured?.type||configured?.resource||configured?.source;
      if(name&&own(SOURCES,name))return SOURCES[name];
      if(key==='map_id')return 'maps';
      if(key==='robot_id')return 'robots';
      if(key==='floor_id')return 'floors';
      if(/(?:^|_)endpoint_id$/.test(key))return 'endpoints';
      if(/(?:^|_)room_id$/.test(key))return 'rooms';
      if(key==='service_request_id')return 'serviceRequests';
      if(/(?:^|_)account_id$/.test(key))return 'staff';
      if(/(?:^|_)shift_id$/.test(key))return 'shifts';
      if(/(?:^|_)zone_id$/.test(key))return 'zones';
      if(/(?:^|_)endpoint_group_id$/.test(key)||key==='delivery_group_id')return 'endpointGroups';
      return null;
    }
    function rowMap(item){return item?.mapId||item?.raw?.mapId||item?.raw?.map_id||item?.raw?.requestDataJson?.mapId||item?.raw?.requestDataJson?.map_id;}
    function changeMap(next,previous){
      if(next.map_id===previous.map_id)return;
      const workflow=state.definition?.workflow,ui=workflow?.inputUiJson||{};
      for(const [key,field] of Object.entries(workflow?.inputSchemaJson?.properties||{})){
        const source=referenceSource(key,field,ui.properties?.[key]||ui.fields?.[key]||{});
        if(!MAP_SOURCES.has(source)||!own(next,key))continue;
        const selected=state.referenceItems.get(key)?.find(item=>item.id===next[key]);
        // Unknown or previous-map references must be explicitly selected again.
        if(!next.map_id||rowMap(selected)!==next.map_id)delete next[key];
      }
    }
    function loadReference(control,help,key,source,field,values,generation){
      const definition=state.definition,token=tokenIdentity(),mapId=values.map_id,robotId=values.robot_id||options.getRobotId?.();
      const requiredMap=MAP_SOURCES.has(source)&&!['serviceRequests'].includes(source);
      const current=own(values,key)?values[key]:undefined;
      function choices(rows,message){
        const selected=control.value;
        control.replaceChildren(new Option(message||'Chưa chọn',''));
        const allowed=Array.isArray(field.enum)?field.enum:own(field,'const')?[field.const]:null;
        const visible=rows.filter(item=>item&&UUID.test(item.id||'')&&(!allowed||allowed.includes(item.id))&&
          (source!=='maps'||!item.raw?.status||item.raw.status==='ACTIVE')&&
          (source!=='endpoints'||item.raw?.active!==false&&item.raw?.accessible!==false)&&
          (!mapId||!MAP_SOURCES.has(source)||!rowMap(item)||rowMap(item)===mapId));
        for(const item of visible)control.add(new Option(item.label||item.id,item.id));
        if(current!==undefined&&!visible.some(item=>same(item.id,current))){
          const known=rows.find(item=>same(item.id,current));
          const option=new Option((known?.label||String(current))+' · giá trị hiện tại không có trong danh sách',typeof current==='string'?current:'__current_json__');
          control.add(option);control.dataset.unavailable='true';
        }else delete control.dataset.unavailable;
        control.value=current===undefined?'':typeof current==='string'?current:'__current_json__';
        if(selected&&control.value===''&&current!==undefined)control.value=selected;
      }
      control.dataset.loading='true';choices([],requiredMap&&!UUID.test(mapId||'')?'Chọn map trước':'Đang tải danh sách…');
      help.textContent=requiredMap&&!UUID.test(mapId||'')?'Chọn map để tải dữ liệu tương ứng.':'Đang tải tên và mã từ hệ thống…';
      const valid=()=>{
        if(unknown()){state.referencesDeferred=true;return false;}
        try{return !state.closed&&generation===state.fieldGeneration&&definition===state.definition&&token===tokenIdentity()&&control.isConnected&&inputs().map_id===mapId;}catch{return false;}
      };
      if(requiredMap&&!UUID.test(mapId||'')){
        delete control.dataset.loading;control.dataset.fixed=control.dataset.fixed||'false';render();return;
      }
      const work=Promise.resolve().then(()=>{
        if(!catalog||typeof catalog[source]!=='function')throw Error('Danh mục chưa sẵn sàng.');
        return catalog[source](source==='jobs'||source==='incidents'?robotId:source==='maps'||source==='robots'||source==='floors'?undefined:mapId);
      }).then(rows=>{
        if(!valid())return;
        rows=Array.isArray(rows)?rows:[];state.referenceItems.set(key,rows);choices(rows,'Chưa chọn');
        const count=control.options.length-1-(control.dataset.unavailable==='true'?1:0);
        help.textContent=(count?'Có '+count+' lựa chọn.':'Không có dữ liệu phù hợp.')+(control.dataset.unavailable==='true'?' Giá trị hiện tại được giữ nguyên; hãy kiểm tra hoặc chọn lại.':'');
      }).catch(error=>{
        if(!valid())return;
        choices([],'Chưa tải được danh sách');help.textContent='Không tải được dữ liệu: '+error.message+' Bấm “Tải lại danh sách dữ liệu” để thử lại.';
      }).finally(()=>{if(valid()){delete control.dataset.loading;render();}});
      state.referenceLoads.add(work);work.then(()=>state.referenceLoads.delete(work),()=>state.referenceLoads.delete(work));
    }
    function renderFields(){
      if(catalogToken!==tokenIdentity()){catalogToken=tokenIdentity();catalog?.invalidate();state.referenceItems.clear();}
      const workflow=state.definition?.workflow,schema=workflow?.inputSchemaJson||{},ui=workflow?.inputUiJson||{},values=inputs();
      const generation=++state.fieldGeneration;
      const container=$('publishedFields');container.replaceChildren();state.fields=[];
      const required=new Set(schema.required||[]);
      for(const [key,field] of Object.entries(schema.properties||{})){
        if(!object(field))continue;
        const type=fieldType(field),enumeration=Array.isArray(field.enum)?field.enum:own(field,'const')?[field.const]:null;
        if(!enumeration&&!['string','integer','number','boolean'].includes(type))continue;
        const presentation=ui.properties?.[key]||ui.fields?.[key]||{},label=document.createElement('label'),source=referenceSource(key,field,presentation);
        label.append(document.createTextNode((presentation.label||presentation.title||field.title||FIELD_LABELS[key]||key)+(required.has(key)?' *':'')));
        const control=document.createElement(source||enumeration||type==='boolean'?'select':'input');
        control.dataset.inputKey=key;
        if(fixedMap()&&key==='map_id')control.dataset.fixed='true';
        if(source){
          control.add(new Option('Đang tải danh sách…',''));
        }else if(enumeration||type==='boolean'){
          const choices=enumeration||(Array.isArray(field.type)&&field.type.includes('null')?[true,false,null]:[true,false]);
          control.add(new Option('Chưa đặt giá trị',''));
          for(const choice of choices)control.add(new Option(typeof choice==='string'?choice:JSON.stringify(choice),JSON.stringify(choice)));
          control.value=own(values,key)?JSON.stringify(values[key]):'';
          if(own(values,key)&&control.value==='')control.add(new Option('Giá trị JSON hiện tại: '+JSON.stringify(values[key]),JSON.stringify(values[key]),true,true));
        }else{
          control.type=['number','integer'].includes(type)?'number':'text';
          if(control.type==='number'){
            control.step=type==='integer'?'1':'any';
            if(Number.isFinite(field.minimum))control.min=field.minimum;
            if(Number.isFinite(field.maximum))control.max=field.maximum;
          }else{
            if(Number.isFinite(field.minLength))control.minLength=field.minLength;
            if(Number.isFinite(field.maxLength))control.maxLength=field.maxLength;
          }
          control.value=own(values,key)&&values[key]!==null?String(values[key]):'';
          if(presentation.placeholder)control.placeholder=presentation.placeholder;
        }
        control.addEventListener('change',()=>{
          try{
            if(state.busy||unknown()||control.dataset.fixed==='true')return;
            const previous=inputs(),next={...previous};
            if(control.value==='')delete next[key];
            else Object.defineProperty(next,key,{value:source?(control.value==='__current_json__'?values[key]:control.value):enumeration||type==='boolean'?JSON.parse(control.value):['number','integer'].includes(type)?Number(control.value):control.value,enumerable:true,configurable:true,writable:true});
            if(key==='map_id')changeMap(next,previous);
            setInputs(next,key==='map_id'||key==='robot_id'||key==='floor_id');
          }catch(error){say(error.message,true);}
        });
        label.append(control);
        if(presentation.description||field.description){const help=document.createElement('small');help.textContent=presentation.description||field.description;label.append(help);}
        container.append(label);state.fields.push(control);
        if(source){const help=document.createElement('small');help.setAttribute('role','status');label.append(help);loadReference(control,help,key,source,field,values,generation);}
        else if(type==='string'&&field.format==='uuid'){const help=document.createElement('small');help.textContent='Chưa có danh mục cho trường này. Nhập UUID hoặc dùng Inputs JSON nâng cao.';label.append(help);}
      }
      if(!state.fields.length){const hint=document.createElement('small');hint.textContent='Workflow này dùng Inputs JSON bên dưới. Các object và array được nhập trong JSON.';container.append(hint);}
      render();
    }
    function contextValues(){
      const nav=options.getNavigation?.(),result=[];
      if(nav?.meta?.map_id)result.push({label:'Map hiện tại · '+nav.meta.map_id,value:nav.meta.map_id});
      const session=nav?.snapshot?.navigationSession?.sessionId;
      if(session)result.push({label:'Phiên navigation · '+session,value:session});
      for(const point of nav?.endpoints||[])if(point.active&&point.accessible)result.push({label:'Endpoint '+point.endpointCode+' · '+point.endpointName,value:point.endpointId});
      return result;
    }
    function refreshContext(){
      if(catalogToken!==tokenIdentity()&&state.definition&&!unknown()){try{renderFields();}catch{}}
      const oldField=$('publishedContextField').value,oldValue=$('publishedContextValue').value;
      $('publishedContextField').replaceChildren();$('publishedContextValue').replaceChildren();
      for(const [key,field] of Object.entries(state.definition?.workflow?.inputSchemaJson?.properties||{})){
        if(fixedMap()&&key==='map_id')continue;
        if(fieldType(field)==='string'||fieldType(field)==='array'&&(!field.items?.type||fieldType(field.items)==='string'))$('publishedContextField').add(new Option(key,key));
      }
      for(const item of contextValues())$('publishedContextValue').add(new Option(item.label,item.value));
      if([...$('publishedContextField').options].some(item=>item.value===oldField))$('publishedContextField').value=oldField;
      if([...$('publishedContextValue').options].some(item=>item.value===oldValue))$('publishedContextValue').value=oldValue;
      const nav=options.getNavigation?.();
      $('publishedContext').textContent=nav?.meta?'Map '+nav.meta.map_id+' · '+(nav.snapshot?.navigationSession?.state||'chưa có phiên navigation READY'):'Tải map ở phần dẫn đường để chọn ID.';
      const fixed=fixedMap();
      $('publishedMapContext').textContent=fixed?'Map cố định của tuần tra: '+fixed+(nav?.meta?.map_id===fixed?' · map đang mở khớp.':' · chọn đúng map này ở phần dẫn đường trước khi tạo job.'):
        hasMove()?'MOVE sử dụng map và phiên navigation đang mở: '+(nav?.meta?.map_id||'chưa chọn map')+' · '+(nav?.snapshot?.navigationSession?.sessionId||'chưa có phiên navigation')+'.':'';
    }
    function clearDefinition(){
      ++state.fieldGeneration;state.referenceItems.clear();state.definition=null;$('publishedFields').replaceChildren();$('publishedDescription').textContent='';$('publishedMetadata').textContent='';$('publishedSchema').textContent='';$('publishedDefinitionSteps').replaceChildren();$('publishedDelivery').hidden=true;setInputs({},false);refreshContext();
    }
    async function loadPage(page){
      const result=await api('/api/v1/workflows/published?page='+page+'&size=100');
      state.page=result.page??page;state.pageCount=result.totalPages??1;
      $('publishedWorkflow').replaceChildren(new Option('Chọn workflow đã xuất bản',''));
      for(const workflow of result.content||[])$('publishedWorkflow').add(new Option(workflow.workflowName+' · '+workflow.workflowCode+' v'+workflow.versionNo+' · '+workflow.serviceType,workflow.workflowId));
      $('publishedPage').textContent=result.totalElements?('Trang '+(state.page+1)+' / '+state.pageCount+' · '+result.totalElements+' workflow'):'Chưa có workflow đã xuất bản nhận job mới.';
      clearDefinition();say('Chọn workflow để tải schema và inputs mặc định.');
    }
    async function loadDefinition(){
      const id=$('publishedWorkflow').value;clearDefinition();if(!id)return;
      const detail=await api('/api/v1/workflows/published/'+encodeURIComponent(id));
      if(detail.workflow?.workflowId!==id)throw Error('Workflow trả về không trùng với lựa chọn.');
      state.definition=detail;const workflow=detail.workflow;
      $('publishedPriority').value=workflow.serviceType==='PATROL'?5:workflow.serviceType==='MAPPING'?1:50;
      $('publishedDelivery').hidden=!delivery();
      $('publishedDescription').textContent=workflow.description||'';
      $('publishedMetadata').textContent=workflow.workflowCode+' v'+workflow.versionNo+' · '+workflow.serviceType+' · '+(detail.steps||[]).filter(step=>step.enabled!==false).length+' bước · Khả năng cần có: '+JSON.stringify(workflow.requiredCapabilitiesJson||[]);
      $('publishedSchema').textContent=JSON.stringify(workflow.inputSchemaJson||{},null,2);
      setInputs(initialInputs(workflow));
      for(const step of (detail.steps||[]).filter(item=>item.enabled!==false).slice().sort((a,b)=>(a.sequenceNo??0)-(b.sequenceNo??0))){
        const item=document.createElement('li');item.textContent=step.stepCode+' · '+step.stepTypeCode+(step.stepName?' · '+step.stepName:'');$('publishedDefinitionSteps').append(item);
      }
      refreshContext();say('Đã tải '+workflow.workflowName+'. Kiểm tra inputs trước khi tạo job.');
    }
    function renderDetail(detail){
      state.detail=detail;const job=detail.job,patrol=detail.patrol;
      $('publishedJobId').value=job.jobId;
      const status=['Job '+(job.jobCode||job.jobId)+' · '+job.status];
      if(job.status==='QUEUED')status.push('Đang xếp hàng. BE sẽ kiểm tra robot, quyền chiếm robot và phiên navigation trước khi thực hiện.');
      if(job.cancelRequestedAt)status.push('Đã yêu cầu hủy. Trạng thái xác nhận dừng: '+job.stopStatus+'.');
      else if(['PENDING','RECOVERY_REQUIRED'].includes(job.stopStatus))status.push('Trạng thái xác nhận dừng: '+job.stopStatus+'.');
      if(job.status==='RECOVERY_REQUIRED'||job.stopStatus==='RECOVERY_REQUIRED')status.push('Cần xử lý recovery và kiểm tra bằng chứng dừng trước khi chạy nhiệm vụ mới.');
      if(job.errorCode||job.failureReason)status.push([job.errorCode,job.failureReason].filter(Boolean).join(' · '));
      $('publishedJobStatus').textContent=status.join('\n');$('publishedJobStatus').style.whiteSpace='pre-line';
      $('publishedJobSteps').replaceChildren();
      function stepItem(step){
        const item=document.createElement('li');item.textContent=step.stepCodeSnapshot+' · '+step.stepTypeSnapshot+' · '+step.status+(step.visitNo>1?' · lượt '+step.visitNo:'')+(step.errorCode?' · '+step.errorCode:'');
        if(step.jobStepId===job.selectedJobStepId)item.style.fontWeight='600';return item;
      }
      const grouped=checkpointGroups(detail);
      if(grouped?.groups.length){
        for(const group of grouped.groups){
          const item=document.createElement('li'),title=document.createElement('strong'),latest=group.latest;
          const phase=latest.stepTypeSnapshot==='EXECUTE_SENSING'?'quan sát':latest.stepTypeSnapshot==='MOVE'?'di chuyển':'điều phối';
          title.textContent=group.checkpointStatus==='BLOCKED'?'Điểm '+group.number+' · Bị chặn ở cả hai điểm · Đã bỏ qua'
            :'Điểm '+group.number+' · '+latest.status+' · '+phase+(group.current?' · hiện tại':'');item.append(title);
          const destination=document.createElement('small'),binding=group.binding;
          const frozen=group.snapshot;
          destination.textContent=frozen?.target_mode==='ENDPOINT_GROUP'?'Nhóm '+frozen.endpoint_group_name+' · '+(frozen.targets||[]).map(point=>point.endpoint_name||point.endpoint_code).join(' → '):frozen?.endpoint?.endpoint_name||frozen?.endpoint?.endpoint_code||(binding?.endpoint_group_id?'Nhóm hai điểm cùng phòng · '+binding.endpoint_group_id:binding?.endpoint_id?'Endpoint · '+binding.endpoint_id:'Đi tới điểm → quan sát tại điểm');item.append(destination);
          const technical=document.createElement('details'),summary=document.createElement('summary'),steps=document.createElement('ol');
          summary.textContent='Chi tiết kỹ thuật · '+group.steps.length+' lần thực thi';technical.append(summary,steps);
          for(const step of group.steps)steps.append(stepItem(step));item.append(technical);$('publishedJobSteps').append(item);
        }
        if(grouped.other.length){const item=document.createElement('li'),details=document.createElement('details'),summary=document.createElement('summary'),steps=document.createElement('ol');summary.textContent='Điều phối tuần tra · '+grouped.other.length+' lần thực thi';for(const step of grouped.other)steps.append(stepItem(step));details.append(summary,steps);item.append(details);$('publishedJobSteps').append(item);}
      }else for(const step of (detail.steps||[]).slice().sort((a,b)=>(a.executionSequenceNo??0)-(b.executionSequenceNo??0)))$('publishedJobSteps').append(stepItem(step));
      $('publishedPatrol').hidden=!patrol;
      if(patrol){
        $('publishedPatrolStatus').textContent='Vòng '+patrol.round+(patrol.continuous?' · liên tục':' / '+patrol.repeatCount)+' · '+(patrol.phase||job.status)+(patrol.resumeRequested?' · đã yêu cầu tiếp tục':'');
        $('publishedHolds').replaceChildren();
        for(const hold of patrol.holds||[]){const item=document.createElement('li');item.textContent='Giữ tuần tra: '+hold.source+(hold.sourceId?' · '+hold.sourceId:'');$('publishedHolds').append(item);}
        $('publishedResumeReason').textContent=patrol.resumeBlockedReason?'Chưa thể tiếp tục: '+patrol.resumeBlockedReason:'Tiếp tục chỉ gỡ yêu cầu tạm dừng thủ công. BE chờ các lý do giữ khác được giải quyết và kiểm tra robot trước khi chạy.';
      }
      $('publishedJobJson').textContent=JSON.stringify(detail,null,2);
      if(state.pending){state.pending.done=settled(detail);persist();}
      render();
    }
    async function refresh(){
      const pending=state.pending;if(!pending)return;
      if(!pending.jobId){
        const found=await api('/api/v1/workflows/'+encodeURIComponent(pending.workflowId)+'/jobs/by-idempotency-key?key='+encodeURIComponent(pending.key));
        if(pending!==state.pending||!found)return;
        if(!UUID.test(found.jobId||''))throw Error('Kết quả tra yêu cầu chưa có Job ID hợp lệ.');
        pending.jobId=found.jobId;persist();$('publishedJobId').value=pending.jobId;
        options.onCreated?.(found,{serviceType:pending.serviceType,workflowId:pending.workflowId});
      }
      const detail=await api('/api/v1/jobs/'+encodeURIComponent(pending.jobId));
      if(pending!==state.pending)return;
      if(detail.job?.jobId!==pending.jobId)throw Error('Job trả về không trùng Job ID đang theo dõi.');
      renderDetail(detail);
    }
    async function poll(){
      clearTimeout(state.timer);if(state.closed||!active()||!state.pending.jobId)return;
      if(state.polling){state.timer=setTimeout(poll,2000);return;}
      state.polling=true;
      try{await refresh();}catch(error){say('Chưa cập nhật được trạng thái: '+error.message,true);}finally{state.polling=false;}
      if(!state.closed&&active())state.timer=setTimeout(poll,2000);
    }
    async function createJob(){
      if(!unknown()){
        if(options.isMappingActive?.())throw Error('Kết thúc phiên quét bản đồ trước khi chạy workflow.');
        const robotId=String(options.getRobotId()||'').trim(),workflow=state.definition?.workflow;
        if(!UUID.test(robotId))throw Error('Robot ID không hợp lệ.');
        if(!workflow||workflow.acceptsNewJobs===false||workflow.status!=='PUBLISHED')throw Error('Chọn workflow đã publish nhận job mới.');
        if(delivery())throw Error('Giao hàng cần tạo yêu cầu giao hàng để gắn staff, QR/face.');
        const value=inputs();validateInputs(value,workflow.inputSchemaJson);
        const unavailable=state.fields.find(field=>field.dataset.unavailable==='true'&&field.value);
        if(unavailable)throw Error((FIELD_LABELS[unavailable.dataset.inputKey]||unavailable.dataset.inputKey)+' đang giữ giá trị không có trong danh sách hợp lệ. Tải lại hoặc chọn giá trị khác trước khi tạo job.');
        if(state.fields.some(field=>field.dataset.loading==='true'))throw Error('Chờ danh sách dữ liệu tải xong trước khi tạo job.');
        const priority=Number($('publishedPriority').value);
        if($('publishedPriority').value===''||!Number.isInteger(priority)||priority<0||priority>100)throw Error('Độ ưu tiên phải là số nguyên từ 0 đến 100.');
        const body={robotId,inputs:value,priority},nav=options.getNavigation?.();
        if(hasMove()&&workflow.serviceType!=='MAPPING'){
          if(!UUID.test(nav?.meta?.map_id||'')||!UUID.test(nav?.snapshot?.navigationSession?.sessionId||''))throw Error('Mở map và phiên navigation cho robot ở phần dẫn đường trước.');
          if(nav.robotId&&nav.robotId!==robotId)throw Error('Phiên navigation đang mở thuộc Robot ID khác. Kiểm tra lại phiên với robot đã chọn.');
          body.mapId=nav.meta.map_id;body.navigationSessionId=nav.snapshot.navigationSession.sessionId;
          if(fixedMap()&&(body.mapId!==fixedMap()||value.map_id!==fixedMap()))throw Error('Tuần tra dùng map cố định '+fixedMap()+'. Mở đúng map này và giữ map_id trong inputs trước khi tạo job.');
          if(isPatrol()&&value.map_id&&body.mapId!==value.map_id)throw Error('Mở đúng map_id của inputs tuần tra ở phần dẫn đường trước khi tạo job.');
        }
        state.pending={key:crypto.randomUUID(),robotId,workflowId:workflow.workflowId,serviceType:workflow.serviceType,body,done:false};state.detail=null;persist();
        $('publishedJobId').value='';$('publishedJobStatus').textContent='Đang gửi yêu cầu tạo job…';$('publishedJobSteps').replaceChildren();$('publishedPatrol').hidden=true;$('publishedJobJson').textContent='';
      }else{
        await refresh();
        if(state.pending.jobId){say('Đã tìm thấy job '+state.pending.jobId+' từ yêu cầu trước.');poll();return;}
      }
      const pending=state.pending;
      say('Đang tạo job. Nếu mất phản hồi, nút gửi lại sẽ giữ nguyên yêu cầu và Idempotency-Key.');
      try{
        const result=await api('/api/v1/workflows/'+encodeURIComponent(pending.workflowId)+'/jobs',pending.body,pending.key);
        const jobId=result?.jobId||result?.job?.jobId;
        if(!UUID.test(jobId||''))throw Error('BE chưa trả về Job ID hợp lệ; hãy gửi lại cùng yêu cầu.');
        pending.jobId=jobId;persist();$('publishedJobId').value=jobId;say('Đã nhận job '+jobId+'.');
        options.onCreated?.(result,{serviceType:pending.serviceType,workflowId:pending.workflowId});
      }catch(error){
        // A timeout or 5xx can occur after admission committed. Keep the frozen request.
        if([400,401,403,404,422].includes(error.status)){state.pending=null;persist();}
        else if(error.status===409){
          // A rejected admission must not lock the form forever. Only release its
          // identity after the authoritative lookup confirms that no job exists.
          let checked=false;try{await refresh();checked=true;}catch{}
          if(pending.jobId){say('Đã tìm thấy job '+pending.jobId+' từ yêu cầu này.');poll();return;}
          if(checked){state.pending=null;persist();}
        }
        if(state.pending&&!state.pending.jobId)say('Chưa xác nhận kết quả tạo job: '+error.message+'. Gửi lại cùng yêu cầu để nhận đúng job cũ.',true);
        throw error;
      }
      try{await refresh();}catch(error){say('Đã nhận Job ID; chưa đọc được trạng thái: '+error.message,true);}
      poll();
    }
    async function jobAction(action){
      if(!state.pending?.jobId)throw Error('Chọn Job ID trước.');
      await refresh();
      if(!actions(state.detail)[action])throw Error('Trạng thái mới nhất không cho phép thao tác này.');
      const reason=$('publishedReason').value.trim();if(!reason)throw Error('Nhập lý do thao tác.');
      const pending=state.pending;
      await api('/api/v1/jobs/'+encodeURIComponent(pending.jobId)+'/'+action,{reason});
      say(action==='cancel'?'Đã yêu cầu hủy. Chờ xác nhận dừng từ BE/Pi.':action==='suspend'?'Đã yêu cầu tạm dừng; chờ xác nhận dừng vật lý.':'Đã gỡ yêu cầu tạm dừng thủ công; BE kiểm tra các điều kiện trước khi tiếp tục.');
      await refresh();poll();
    }
    $('publishedLoad').onclick=()=>mutate(()=>loadPage(0));
    $('publishedPrevious').onclick=()=>mutate(()=>loadPage(state.page-1));
    $('publishedNext').onclick=()=>mutate(()=>loadPage(state.page+1));
    $('publishedWorkflow').onchange=()=>mutate(loadDefinition);
    $('publishedInputs').onchange=()=>{try{if(!unknown())renderFields();}catch(error){say(error.message,true);}};
    $('publishedReloadCatalog').onclick=()=>{
      if(state.busy||unknown())return;
      try{catalog?.invalidate();state.referenceItems.clear();renderFields();}catch(error){say(error.message,true);}
    };
    const tokenChanged=()=>{
      if(!host.isConnected||state.closed||!state.definition||catalogToken===tokenIdentity())return;
      if(state.busy||unknown()){state.referencesDeferred=true;return;}
      try{renderFields();}catch{}
    };
    document.addEventListener('input',tokenChanged,true);
    document.addEventListener('change',tokenChanged,true);
    $('publishedDefaults').onclick=()=>mutate(()=>{if(state.definition)setInputs(initialInputs(state.definition.workflow));});
    $('publishedUseContext').onclick=()=>mutate(()=>{
      refreshContext();const key=$('publishedContextField').value,value=$('publishedContextValue').value;
      if(!key||!value)throw Error('Chọn trường input và giá trị từ map hiện tại.');
      const next=inputs(),field=state.definition.workflow.inputSchemaJson.properties[key];
      const replacement=fieldType(field)==='array'?[...(Array.isArray(next[key])?next[key]:[]),value]:value;
      Object.defineProperty(next,key,{value:replacement,writable:true,enumerable:true,configurable:true});setInputs(next);say('Đã điền '+key+'. Kiểm tra inputs trước khi tạo job.');
    });
    $('publishedCreate').onclick=()=>mutate(createJob);
    $('publishedWatch').onclick=()=>mutate(async()=>{
      if(unknown())throw Error('Tra hoặc gửi lại yêu cầu tạo job chưa xác nhận trước khi đổi Job ID.');
      const id=$('publishedJobId').value.trim();if(!UUID.test(id))throw Error('Job ID không hợp lệ.');
      const detail=await api('/api/v1/jobs/'+encodeURIComponent(id));
      state.pending={key:crypto.randomUUID(),workflowId:detail.job.workflowId,robotId:detail.job.robotId,body:{},jobId:id,done:false};persist();renderDetail(detail);say('Đang theo dõi job '+id+'.');poll();
    });
    $('publishedRefresh').onclick=()=>mutate(refresh);
    $('publishedUnwatch').onclick=()=>{
      if(state.busy||unknown())return;
      clearTimeout(state.timer);state.pending=null;state.detail=null;persist();
      $('publishedJobId').value='';$('publishedJobStatus').textContent='';$('publishedJobSteps').replaceChildren();$('publishedPatrol').hidden=true;$('publishedJobJson').textContent='';
      say('Đã ngừng theo dõi trên trang. Job trên BE vẫn giữ trạng thái hiện tại; dùng Hủy nhiệm vụ để yêu cầu robot dừng.');render();
    };
    for(const action of ['cancel','suspend','resume'])$('published'+action[0].toUpperCase()+action.slice(1)).onclick=()=>mutate(()=>jobAction(action));
    window.addEventListener('pagehide',()=>{state.closed=true;clearTimeout(state.timer);});
    window.addEventListener('pageshow',()=>{state.closed=false;if(active())poll();});
    if(state.pending?.jobId){$('publishedJobId').value=state.pending.jobId;$('publishedJobStatus').textContent='Job từ phiên trước: '+state.pending.jobId+'. Bấm cập nhật để xem trạng thái mới.';poll();}
    else if(unknown())say('Có yêu cầu tạo job chưa xác nhận từ phiên trước. Gửi lại cùng yêu cầu để nhận đúng Job ID.');
    refreshContext();render();
    async function selectWorkflow(id,value){
      if(state.busy||unknown())throw Error('Hoàn tất yêu cầu tạo job hiện tại trước khi chọn tuyến mới.');
      state.busy=true;render();
      try{
        await loadPage(0);
        if(![...$('publishedWorkflow').options].some(option=>option.value===id))$('publishedWorkflow').add(new Option('Tuyến vừa publish',id));
        $('publishedWorkflow').value=id;await loadDefinition();
        if(value!==undefined)setInputs(copy(value));
      }finally{state.busy=false;render();}
    }
    async function runWorkflow(id,value){
      if(unknown())throw Error('Đối chiếu yêu cầu chưa nhận được Job ID trước khi thêm một lượt mới vào hàng đợi.');
      await selectWorkflow(id,value);
      if(state.busy||unknown())throw Error('Hoàn tất yêu cầu hiện tại trước khi tạo job.');
      state.busy=true;render();
      try{await Promise.allSettled([...state.referenceLoads]);await createJob();return state.pending?.jobId||null;}
      catch(error){say(error.message,true);throw error;}
      finally{state.busy=false;render();}
    }
    return {isActive:active,isPendingSubmission:unknown,refresh,refreshContext,selectWorkflow,runWorkflow};
  }
  return {initialInputs,parseInputs,validateInputs,actions,settled,checkpointGroups,mount};
});
