const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {consumeRobotEvents}=require('../../main/resources/static/map-navigation.js');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/mapping.html'),'utf8');
const source=html.slice(html.indexOf('function validMappingFrame('),html.indexOf('function ensureMappingStream('));
function page(){
  const images=[],revoked=[],fields={mapImage:{hidden:false,src:'old'},mapTime:{}},draw=[];
  const ctx={watchEpoch:1,job:'j1',session:'s1',mapFrameMetadata:null,mapFrameRevision:null,mapImageSerial:0,mapLoaded:true,
    mapFrameUrls:new Set(),images:{mapImage:'old'},lastMappingStatus:{},$:id=>fields[id],renderMapView:()=>draw.push(ctx.mapFrameRevision),
    URL:{createObjectURL:()=> 'blob-'+images.length,revokeObjectURL:x=>revoked.push(x)},Image:class{constructor(){images.push(this)}}};
  vm.createContext(ctx);vm.runInContext(source,ctx);return {ctx,images,fields,draw,revoked};
}
const frame=revision=>({mapping_session_id:'s1',revision,width:100,height:80,resolution:.025,origin:[0,0,0]});
test('old image and marker remain together until the next image is loaded',()=>{
  const p=page();p.ctx.stageMappingImage(frame(2),{},1,'j1');
  assert.equal(p.fields.mapImage.src,'old');assert.equal(p.ctx.mapFrameRevision,null);
  Object.assign(p.images[0],{naturalWidth:100,naturalHeight:80});p.images[0].onload();
  assert.equal(p.ctx.mapFrameRevision,null); // actual displayed image has not loaded yet
  p.fields.mapImage.onload();
  assert.equal(p.ctx.mapFrameRevision,2);assert.equal(p.ctx.mapFrameMetadata.revision,2);
  assert.equal(p.draw[0],2);assert(p.revoked.includes('old'));
});
test('late decodes and old-session frames cannot overwrite a newer scoped frame',()=>{
  const p=page();p.ctx.stageMappingImage(frame(1),{},1,'j1');p.ctx.stageMappingImage(frame(2),{},1,'j1');
  Object.assign(p.images[0],{naturalWidth:100,naturalHeight:80});p.images[0].onload();
  assert.equal(p.fields.mapImage.src,'old');
  Object.assign(p.images[1],{naturalWidth:100,naturalHeight:80});p.images[1].onload();p.fields.mapImage.onload();
  assert.equal(p.ctx.mapFrameRevision,2);
  assert.equal(p.ctx.validMappingFrame(frame(1),p.ctx.mapFrameMetadata,'s1'),false);
  assert.equal(p.ctx.validMappingFrame({...frame(3),mapping_session_id:'other'},null,'s1'),false);
});
test('mapping stream handles fragmented payloads separately from navigation state events',async()=>{
  const expected={preview:frame(1),png_base64:'png'};const wire='event: mapping-frame\ndata: '+JSON.stringify(expected)+'\n\n';
  const bytes=new TextEncoder().encode(wire),received=[];
  const response=new Response(new ReadableStream({start(c){for(let i=0;i<bytes.length;i+=7)c.enqueue(bytes.slice(i,i+7));c.close()}}));
  await consumeRobotEvents(response,x=>received.push(x),'mapping-frame',6500000);
  assert.deepEqual(received,[expected]);
});
