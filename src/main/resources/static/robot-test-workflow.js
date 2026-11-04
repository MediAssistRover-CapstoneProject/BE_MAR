(function(root,factory){
  const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.RobotTestWorkflow=api;
})(globalThis,function(){
  'use strict';
  const UUID=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const TERMINAL=new Set(['SUCCEEDED','FAILED','CANCELLED']);
  function stepPayload(step){
    if(!['MOVE','WAIT','OPEN_SERVO','CLOSE_SERVO','WAIT_CONFIRM'].includes(step.kind))throw Error('Loại step không hợp lệ.');
    if(step.kind==='MOVE'){
      if(!UUID.test(step.endpointId||''))throw Error('MOVE thiếu endpoint đã lưu.');
      return {kind:'MOVE',endpointId:step.endpointId};
    }
    if(step.kind==='WAIT'){
      const seconds=Number(step.durationSec);
      if(!Number.isFinite(seconds)||seconds<1||seconds>300)throw Error('WAIT phải từ 1 đến 300 giây.');
      return {kind:'WAIT',durationSec:seconds};
    }
    return {kind:step.kind};
  }
  function body(robotId,context,steps,speed,compartmentId){
    if(!UUID.test(robotId||''))throw Error('Robot ID không hợp lệ.');
    if(!steps.length||steps.length>20)throw Error('Thêm từ 1 đến 20 step.');
    if(!/^[A-Za-z0-9_-]{1,64}$/.test(compartmentId))throw Error('Compartment ID không hợp lệ.');
    const moves=steps.some(s=>s.kind==='MOVE'),result={robotId,compartmentId,steps:steps.map(stepPayload)};
    if(moves){
      if(!UUID.test(context?.mapId||''))throw Error('Tải map và endpoint ở phần dẫn đường trước.');
      if(steps.some(s=>s.kind==='MOVE'&&s.mapId!==context.mapId))throw Error('Endpoint trong danh sách thuộc map khác; sửa danh sách trước.');
      if(!Number.isFinite(speed)||speed<.05||speed>.6)throw Error('Tốc độ MOVE từ 0,05 đến 0,60 m/s.');
      result.mapId=context.mapId;result.navigationSessionId=context.sessionId||null;result.maxSpeedMps=speed;
    }
    return result;
  }
  function waitingConfirmation(detail){
    if(detail?.job?.status!=='RUNNING'||detail.job.cancelRequestedAt)return null;
    return detail.steps?.find(s=>s.jobStepId===detail.job.selectedJobStepId&&s.status==='RUNNING'
      &&s.stepTypeSnapshot==='WAIT'&&s.resolvedInputsJson?.wait_type==='EVENT'
      &&s.resolvedInputsJson?.event_type==='TEST_OPERATOR_CONFIRMED'&&s.resolvedInputsJson?.correlation_key)||null;
  }
  function mount(options){
    const host=options.element;
    host.innerHTML=`<h2>Chạy workflow</h2>
      <p>Thêm các step theo thứ tự rồi chạy trong một job. Ví dụ: MOVE A → WAIT 5 giây → MOVE B → mở servo → chờ xác nhận → đóng servo.</p>
      <small>Dùng Robot ID và token ADMIN ở đầu trang. Kết thúc job quét trước khi thử; MOVE dùng phiên map READY đang mở, không khởi tạo lại AMCL.</small>
      <label>Trần tốc độ MOVE (m/s)<input id="testSpeed" type="number" min="0.05" max="0.6" step="0.05" value="0.4"></label>
      <details><summary>Cấu hình servo</summary><label>Compartment ID<input id="testCompartment" value="CART_MAIN" maxlength="64"></label>
      <button id="testConfig">Kiểm tra cấu hình Pi/BE</button><small id="testConfigStatus">Servo MG996R · S6 / ROS ID 4 · mở 2400 / đóng 1500. Chưa kiểm tra cấu hình.</small>
      <small>Pi xác nhận đã phát lệnh servo; hiện không có cảm biến xác nhận cửa mở/đóng thực tế.</small></details>
      <hr><label>Endpoint MOVE<select id="testEndpoint"><option value="">Tải endpoint từ map ở trên</option></select></label>
      <button id="testLoadEndpoints">Lấy endpoint từ map</button><button id="testAddMove">Thêm MOVE</button>
      <label>WAIT (giây)<input id="testWaitSeconds" type="number" min="1" max="300" step="0.1" value="5"></label>
      <button id="testAddWait">Thêm WAIT</button><button id="testAddOpen">Thêm mở servo</button><button id="testAddClose">Thêm đóng servo</button>
      <button id="testAddConfirm">Thêm chờ xác nhận lấy hàng</button><button id="testClear">Xóa danh sách</button>
      <ol id="testSteps"></ol><button id="testRun">Chạy workflow</button><p id="testReadiness" role="status">Thêm step để bắt đầu.</p>
      <p id="testStatus" class="nav-status" role="status">Chưa gửi lệnh.</p>
      <label>Job ID<input id="testJobId" readonly></label><button id="testRefresh">Cập nhật trạng thái job</button>
      <button id="testConfirm" disabled>Đã lấy hàng / Tiếp tục</button><button id="testCancel" disabled>Hủy workflow</button>
      <small id="testWaiting"></small><details><summary>API và JSON gửi tới BE</summary>
      <p><code>POST /api/v1/test-workflows/run</code> · header <code>Idempotency-Key</code> mới cho mỗi lượt. Dùng token ADMIN để chạy trang kiểm thử.</p>
      <p>Theo dõi <code>GET /api/v1/jobs/{jobId}</code> hoặc <code>/stream</code>; hủy bằng <code>POST /api/v1/jobs/{jobId}/cancel</code>. Workflow này dùng runtime thật của BE/Pi.</p>
      <button id="testPreview">Xem workflow BE dựng từ tham số</button><pre id="testJson"></pre></details>
      <pre id="testProgress">Chưa có job.</pre>`;
    const $=id=>host.querySelector('#'+id),storage='mar-basic-test-workflow';
    const state={steps:[],pending:null,detail:null,busy:false,closed:false,timer:null,points:[],confirmEvent:null,pointSignature:null};
    const say=(message,error=false)=>{$('testStatus').textContent=message;$('testStatus').style.color=error?'#a51d1d':'';};
    const robot=()=>options.getRobotId().trim();
    const context=()=>{const n=options.getNavigation();return {mapId:n?.meta?.map_id,sessionId:n?.snapshot?.navigationSession?.sessionId};};
    function persist(){try{sessionStorage.setItem(storage,JSON.stringify({steps:state.steps,pending:state.pending}));}catch(_){}}
    function render(){
      $('testSteps').replaceChildren();
      state.steps.forEach((step,index)=>{
        const li=document.createElement('li'),label=document.createElement('span');
        label.textContent=step.kind==='MOVE'?'MOVE → '+(step.label||step.endpointId):step.kind==='WAIT'?'WAIT '+step.durationSec+' giây':({OPEN_SERVO:'Mở servo',CLOSE_SERVO:'Đóng servo',WAIT_CONFIRM:'Chờ bấm xác nhận lấy hàng'}[step.kind]);li.append(label);
        for(const [text,delta] of [['↑',-1],['↓',1],['Bỏ',0]]){
          const b=document.createElement('button');b.textContent=text;b.disabled=state.busy||!!state.pending&&!state.pending.done||delta!==0&&(index+delta<0||index+delta>=state.steps.length);
          b.onclick=()=>{if(delta){const next=index+delta;[state.steps[index],state.steps[next]]=[state.steps[next],state.steps[index]];}else state.steps.splice(index,1);persist();render();};li.append(b);
        }
        $('testSteps').append(li);
      });
      const active=state.pending&&!state.pending.done,known=active&&state.pending.jobId,wait=waitingConfirmation(state.detail);
      for(const id of ['testAddMove','testAddWait','testAddOpen','testAddClose','testAddConfirm','testClear'])$(id).disabled=state.busy||!!active;
      $('testRun').textContent=active&&!known?'Gửi lại cùng yêu cầu cũ':'Chạy workflow';
      $('testCancel').disabled=state.busy||!known||state.detail?.job?.status==='RECOVERY_REQUIRED';
      $('testConfirm').disabled=state.busy||!wait;
      $('testJobId').value=state.pending?.jobId||'';
      $('testWaiting').textContent=wait?'Robot đang chờ tại '+wait.stepCodeSnapshot+'. Chỉ bấm xác nhận khi đã lấy hàng xong.':'';
      refreshContext();
    }
    function refreshContext(){
      const nav=options.getNavigation(),snapshot=nav?.snapshot,active=state.pending&&!state.pending.done;
      const points=(nav?.endpoints||[]).filter(p=>p.active&&p.accessible);
      const signature=JSON.stringify([nav?.meta?.map_id,points.map(p=>[p.endpointId,p.endpointCode,p.endpointName])]);
      if(signature!==state.pointSignature){
        state.pointSignature=signature;state.points=points;const selected=$('testEndpoint').value;$('testEndpoint').replaceChildren();
        for(const p of points){const o=document.createElement('option');o.value=p.endpointId;o.textContent=p.endpointCode+' — '+p.endpointName;$('testEndpoint').append(o);}
        if(points.some(p=>p.endpointId===selected))$('testEndpoint').value=selected;
      }
      const reasons=[];
      if(!state.steps.length)reasons.push('chưa thêm step');
      if(options.isMappingActive())reasons.push('job quét chưa kết thúc');
      if(snapshot?.activeJobId&&snapshot.activeJobId!==state.pending?.jobId)reasons.push('robot đang có job khác');
      if(state.steps.some(s=>s.kind==='MOVE')){
        if(!nav?.meta||snapshot?.navigationSession?.state!=='READY')reasons.push('phiên map chưa READY');
        if(state.steps.some(s=>s.kind==='MOVE'&&!points.some(p=>p.endpointId===s.endpointId&&p.mapId===nav?.meta?.map_id)))reasons.push('endpoint đã đổi map hoặc không còn hoạt động');
        if(nav?.zoneAck!==true)reasons.push('Pi chưa xác nhận endpoint/zone');
      }
      const retry=active&&!state.pending.jobId;
      $('testRun').disabled=state.busy||!!(active&&state.pending.jobId)||(!retry&&reasons.length>0);
      $('testReadiness').textContent=active?'Đang theo dõi workflow hiện tại.':reasons.length?'Chưa sẵn sàng: '+reasons.join('; ')+'.':state.steps.length+' step sẵn sàng; thực hiện theo thứ tự bên trên.';
    }
    async function api(path,data,key){
      const token=options.getToken().trim().replace(/^Bearer\s+/i,'');if(!token)throw Error('Cần token ADMIN ở đầu trang.');
      const response=await fetch(path,{method:data===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(data===undefined?{}:{'Content-Type':'application/json'}),...(key?{'Idempotency-Key':key}:{})},body:data===undefined?undefined:JSON.stringify(data),cache:'no-store',signal:AbortSignal.timeout(15000)});
      if(!response.ok){const error=Error(response.status+' '+(await response.text()).slice(0,600));error.status=response.status;throw error;}
      return response.status===204?null:response.json();
    }
    async function mutate(action){if(state.busy)return;state.busy=true;render();try{await action();}catch(e){say(e.message,true);}finally{state.busy=false;render();}}
    function payload(){return body(robot(),context(),state.steps,Number($('testSpeed').value),$('testCompartment').value.trim());}
    function add(step){if(state.steps.length>=20)throw Error('Tối đa 20 step trong một workflow test.');state.steps.push(step);persist();render();}
    function loadEndpoints(){
      const nav=options.getNavigation();if(!nav?.meta)throw Error('Tải map và endpoint ở phần dẫn đường trước.');
      state.points=nav.endpoints.filter(p=>p.active&&p.accessible);$('testEndpoint').replaceChildren();
      for(const p of state.points){const o=document.createElement('option');o.value=p.endpointId;o.textContent=p.endpointCode+' — '+p.endpointName;$('testEndpoint').append(o);}
      say('Đã lấy '+state.points.length+' endpoint từ map '+nav.meta.map_id+'.');
    }
    async function refresh(){
      const pending=state.pending;if(!pending)return;
      if(!pending.jobId){const found=await api('/api/v1/test-workflows/by-idempotency-key?key='+encodeURIComponent(pending.key));if(found){pending.jobId=found.jobId;persist();}else return;}
      const detail=await api('/api/v1/jobs/'+pending.jobId);if(pending!==state.pending)return;
      state.detail=detail;
      $('testProgress').textContent='Job '+detail.job.jobId+' · '+detail.job.status+'\n'+detail.steps.slice().sort((a,b)=>a.stepCodeSnapshot.localeCompare(b.stepCodeSnapshot)).map(s=>s.stepCodeSnapshot+' · '+s.stepTypeSnapshot+' · '+s.status+(s.errorCode?' · '+s.errorCode:'')).join('\n');
      if(TERMINAL.has(detail.job.status)){pending.done=true;persist();say('Job '+detail.job.status+(detail.job.errorCode?' · '+detail.job.errorCode:''),detail.job.status!=='SUCCEEDED');}
      if(detail.job.status==='RECOVERY_REQUIRED')say('Job cần recovery. Kiểm tra bằng chứng dừng trước khi đóng; chưa tạo job mới.',true);
      render();
    }
    async function poll(){
      clearTimeout(state.timer);if(state.closed||!state.pending||state.pending.done)return;
      try{await refresh();}catch(e){say('Chưa đọc được trạng thái: '+e.message,true);}
      if(!state.closed&&state.pending&&!state.pending.done)state.timer=setTimeout(poll,2000);
    }
    async function send(){
      if(options.isMappingActive())throw Error('Kết thúc job quét trước khi chạy workflow test.');
      if(!state.pending||state.pending.done){
        const b=payload();
        if(!UUID.test(robot()))throw Error('Robot ID không hợp lệ.');
        state.pending={key:crypto.randomUUID(),path:'/api/v1/test-workflows/run',body:b,robotId:robot(),done:false};state.detail=null;state.confirmEvent=null;persist();
      }
      const p=state.pending;if(p.jobId)throw Error('Job đang chạy; chờ kết thúc hoặc bấm hủy.');
      say('Đang gửi workflow; yêu cầu gửi lại sẽ dùng cùng Idempotency-Key.');
      $('testJson').textContent=JSON.stringify({api:p.path,idempotencyKey:p.key,body:p.body},null,2);
      try{const result=await api(p.path,p.body,p.key);p.jobId=result.jobId;p.workflowId=result.workflowId;persist();say('Đã tạo job '+p.jobId+'.');await refresh();poll();}
      catch(e){
        // Validation/authorization rejection made no job; transport/server errors
        // keep the frozen identity so a retry cannot execute the servo twice.
        if([400,401,403,404,409,422].includes(e.status)){p.done=true;persist();}
        else poll();
        throw e;
      }
    }
    $('testConfig').onclick=()=>mutate(async()=>{
      const [cfg,snapshot]=await Promise.all([api('/api/v1/robots/'+robot()+'/test-configuration'),api('/api/v1/robots/'+robot()+'/snapshot')]);
      $('testConfigStatus').textContent='BE mở thử: '+(cfg.commissioningUnlockEnabled?'bật':'tắt')+' · Pi unlock: '+cfg.piUnlockPolicy+' · MQTT: '+(snapshot.mqttConnected?'đã nối':'chưa nối')+' · Robot: '+snapshot.robot.status+' · S6/ID4 · mở 2400 / đóng 1500.';
      $('testJson').textContent=JSON.stringify(cfg,null,2);
    });
    $('testLoadEndpoints').onclick=()=>mutate(loadEndpoints);
    $('testAddMove').onclick=()=>mutate(()=>{const point=state.points.find(p=>p.endpointId===$('testEndpoint').value);if(!point)throw Error('Chọn endpoint đã lưu trước.');add({kind:'MOVE',endpointId:point.endpointId,mapId:point.mapId,label:point.endpointCode});});
    $('testAddWait').onclick=()=>mutate(()=>{const step=stepPayload({kind:'WAIT',durationSec:$('testWaitSeconds').value});add(step);});
    $('testAddOpen').onclick=()=>mutate(()=>add({kind:'OPEN_SERVO'}));$('testAddClose').onclick=()=>mutate(()=>add({kind:'CLOSE_SERVO'}));
    $('testAddConfirm').onclick=()=>mutate(()=>add({kind:'WAIT_CONFIRM'}));
    $('testClear').onclick=()=>mutate(()=>{state.steps=[];persist();render();});
    $('testPreview').onclick=()=>mutate(async()=>{$('testJson').textContent=JSON.stringify(await api('/api/v1/test-workflows/preview',payload()),null,2);say('Đã dựng JSON workflow. Chưa gửi lệnh tới robot.');});
    $('testRun').onclick=()=>mutate(()=>send());$('testRefresh').onclick=()=>mutate(async()=>{await refresh();poll();});
    $('testCancel').onclick=()=>mutate(async()=>{await api('/api/v1/jobs/'+state.pending.jobId+'/cancel',{reason:'Người vận hành hủy workflow test từ HTML'});say('Đã yêu cầu hủy; chờ BE/Pi xác nhận kết thúc.');await refresh();poll();});
    $('testConfirm').onclick=()=>mutate(async()=>{
      const wait=waitingConfirmation(state.detail);if(!wait)throw Error('Job chưa tới bước chờ xác nhận.');
      if(state.confirmEvent?.step!==wait.jobStepId)state.confirmEvent={step:wait.jobStepId,body:{eventId:crypto.randomUUID(),eventType:'TEST_OPERATOR_CONFIRMED',correlationKey:wait.resolvedInputsJson.correlation_key,occurredAt:new Date().toISOString(),data:{source:'ROBOT_TEST_UI'}}};
      await api('/api/v1/jobs/'+state.pending.jobId+'/events',state.confirmEvent.body);say('Đã xác nhận; BE chuyển sang step tiếp theo.');await refresh();poll();
    });
    try{const saved=JSON.parse(sessionStorage.getItem(storage)||'null');if(saved){if(Array.isArray(saved.steps)&&saved.steps.length<=20)state.steps=saved.steps;if(saved.pending?.key&&saved.pending.robotId===robot())state.pending=saved.pending;}}catch(_){}
    window.addEventListener('pagehide',()=>{state.closed=true;clearTimeout(state.timer);});render();
    // No command on page load. Resume read-only tracking after an explicit click.
    return {state,refresh,refreshContext,isActive:()=>!!state.pending&&!state.pending.done};
  }
  return {stepPayload,body,waitingConfirmation,mount};
});
