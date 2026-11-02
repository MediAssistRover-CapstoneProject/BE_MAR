const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/mapping.html'),'utf8');
const start=html.indexOf('async function requestSaveMap()');
const code=html.slice(start,html.indexOf("$('save').onclick",start));
function page(api,stop){
  const notices=[],storage=new Map();
  const ctx={job:'job-a',session:'session-a',saveIntent:null,busy:false,mapping:true,buttons:()=>{},api,
    autoMapping:{cancel:()=>{}},jog:{stop},notice:x=>notices.push(x),crypto:{randomUUID:()=> 'event-a'},
    sessionStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)}};
  vm.createContext(ctx);vm.runInContext(code,ctx);return {ctx,notices,storage};
}
test('a STOP request that never returns cannot suppress the durable SAVE event',async()=>{
  const calls=[];const p=page(async(...args)=>{calls.push(args);return {};},()=>new Promise(()=>{}));
  await p.ctx.requestSaveMap();
  assert.equal(calls.length,1);assert.equal(calls[0][1].eventType,'MAPPING_SAVE_REQUESTED');
  assert.equal(calls[0][3].timeoutMs,30000);assert.equal(p.ctx.saveIntent.accepted,true);
});
test('HTTP timeout and user retry preserve the exact same event identity and body',async()=>{
  const calls=[];const p=page(async(...args)=>{calls.push(JSON.stringify(args[1]));if(calls.length===1)throw Error('timeout');return {};},async()=>{throw Error('STOP timeout');});
  await p.ctx.requestSaveMap();assert.equal(p.ctx.mapping,true);assert.equal(p.ctx.busy,false);
  await p.ctx.requestSaveMap();assert.equal(calls[0],calls[1]);assert.equal(p.ctx.mapping,false);
});
test('an in-flight save cannot be double-submitted',async()=>{
  let finish,calls=0;const p=page(()=>{calls++;return new Promise(resolve=>finish=resolve)},async()=>{});
  const pending=p.ctx.requestSaveMap();await p.ctx.requestSaveMap();assert.equal(calls,1);
  finish({});await pending;
});
