const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/mapping.html'),'utf8');
function page(age=0){
  const fields=new Map();
  const preview={mapping_session_id:'s1',revision:101,width:100,height:100,resolution:.025,origin:[-1,-1,0],
    captured_at:new Date(Date.now()-age*1000).toISOString(),pose:{fresh:true,age_sec:0,value:{x_m:0,y_m:0,yaw_rad:0}}};
  const ctx={mapFrameRevision:101,mapFrameMetadata:preview,mapLoaded:true,currentMapState:null,
    TextDecoder,Uint8Array,atob,$:id=>{if(!fields.has(id))fields.set(id,{hidden:false,attrs:{},setAttribute(k,v){this.attrs[k]=v}});return fields.get(id)}};
  vm.createContext(ctx);vm.runInContext(html.slice(html.indexOf('function hideRobot('),html.indexOf('class TimedJog')),ctx);
  return {ctx,fields,preview};
}
test('newer PNG renders its own pose despite an older JSON response',()=>{
  const p=page();p.ctx.renderMapView({state:'MAPPING',potentially_open:true,mapping_session_id:'s1',preview:{...p.preview,revision:100}});
  assert.equal(p.fields.get('mapOverlay').hidden,false);
  assert.match(p.fields.get('robotMarker').attrs.transform,/translate\(40 60\)/);
});
test('brief cloud latency keeps a grey labelled last position, never a fresh pose',()=>{
  const p=page(5);p.ctx.renderMapView({state:'MAPPING',potentially_open:true,mapping_session_id:'s1'});
  assert.equal(p.fields.get('mapOverlay').hidden,false);
  assert.equal(p.fields.get('robotDot').attrs.fill,'#777');
  assert.match(p.fields.get('robotPose').textContent,/Vị trí cuối/);
  assert.equal(p.ctx.freshMapPose(p.preview.pose,p.preview.captured_at),false);
});
test('expired frames and other sessions cannot show a robot marker',()=>{
  const p=page(11);p.ctx.renderMapView({state:'MAPPING',potentially_open:true,mapping_session_id:'s1'});
  assert.equal(p.fields.get('mapOverlay').hidden,true);
  p.preview.captured_at=new Date().toISOString();
  p.ctx.renderMapView({state:'MAPPING',potentially_open:true,mapping_session_id:'s2'});
  assert.equal(p.fields.get('mapOverlay').hidden,true);
});
test('image metadata must agree with the PNG revision',()=>{
  const p=page();const raw=Buffer.from(JSON.stringify(p.preview)).toString('base64');
  const response=revision=>({headers:{get:k=>k==='X-Map-Metadata'?raw:String(revision)}});
  assert.equal(p.ctx.imageMapMetadata(response(101)).revision,101);
  assert.equal(p.ctx.imageMapMetadata(response(102)),null);
});

test('image load before the first session response is harmless',()=>{
  const p=page();assert.doesNotThrow(()=>p.ctx.renderMapView(null));
  assert.equal(p.fields.get('mapOverlay').hidden,true);
});
