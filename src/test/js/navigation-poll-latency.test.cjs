const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/mapping.html'),'utf8');
const code=html.slice(html.indexOf('    let progressInFlight=false;'),html.indexOf('    async function template()'));
function page(trackRequest,snapshot){
  const fields={};const state={closed:false,generation:1,meta:{},pending:{done:false},snapshot:{mqttConnected:true}};
  const ctx={state,trackRequest,snapshot,ensureRobotStream:()=>{},streamLastAt:-Infinity,performance:{now:()=>0},setTimeout:()=>0,controls:()=>{},draw:()=>{},$:id=>fields[id]??=( {})};
  vm.createContext(ctx);vm.runInContext(code,ctx);return {ctx,state,fields};
}
test('an arbitrarily slow job detail request never blocks the next robot snapshot or creates replay traffic',async()=>{
  let detailCalls=0,poseCalls=0;
  const p=page(()=>{detailCalls++;return new Promise(()=>{});},async()=>{poseCalls++;});
  await p.ctx.poll(1);await p.ctx.poll(1);
  assert.equal(poseCalls,2);assert.equal(detailCalls,1);
});
test('job lookup failure does not erase a valid pose or imply MQTT disconnection',async()=>{
  const p=page(async()=>{throw Error('job DB slow');},async()=>{});
  await p.ctx.poll(1);await new Promise(setImmediate);
  assert.equal(p.state.snapshot.mqttConnected,true);
  assert.match(p.fields.navReceipt.textContent,/job DB slow/);
});
test('a snapshot transport error preserves the last observed timestamp for grey display',async()=>{
  const p=page(async()=>{},async()=>{throw Error('HTTP timeout');});
  await p.ctx.poll(1);
  assert.equal(p.state.snapshot.mqttConnected,true);
  assert.match(p.fields.navRobotStatus.textContent,/chưa kết luận mất MQTT/);
});
