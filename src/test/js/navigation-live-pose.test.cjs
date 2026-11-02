const {test}=require('node:test'),assert=require('node:assert/strict');
const {liveNavigationPose,consumeRobotEvents}=require('../../main/resources/static/map-navigation.js');
test('observations display only in the current session/map/revision and expire without changing authorization',()=>{
  const meta={map_id:'map',asset_hash:'a'.repeat(64),frame_id:'map',width_px:100,height_px:100,resolution_m_per_pixel:.025,origin_x_m:0,origin_y_m:0,origin_yaw_rad:0};
  const snapshot={robot:{robotId:'robot'},dispatchReady:false,navigationSession:{state:'READY',sessionId:'session',mapId:'map',mapAssetHash:meta.asset_hash,initialization:{revision:1}}};
  const sample={messageType:'NAVIGATION_POSE',robotId:'robot',navigationSessionId:'session',mapId:'map',mapAssetHash:meta.asset_hash,initializationRevision:1,capturedAt:'2026-11-02T15:00:00Z',receivedAt:'2026-11-02T15:00:00.100Z',pose:{xM:1,yM:1,yawRad:.3}};
  assert.equal(liveNavigationPose(sample,meta,snapshot,100).ageMs,200);
  assert.equal(snapshot.dispatchReady,false);
  for(const change of [{initializationRevision:2},{navigationSessionId:'old'},{mapAssetHash:'b'.repeat(64)}])assert.equal(liveNavigationPose({...sample,...change},meta,snapshot,100),null);
  assert.equal(liveNavigationPose(sample,meta,snapshot,1600),null);
});
test('one authenticated SSE stream carries separate pose and persisted state events',async()=>{
  const text='event: robot-pose\ndata: {"sequenceNo":1}\n\nevent: robot-state\ndata: {"dispatchReady":false}\n\n';
  const response=new Response(new ReadableStream({start(c){c.enqueue(new TextEncoder().encode(text));c.close();}}));const seen=[];
  await consumeRobotEvents(response,(value,name)=>seen.push([name,value]),['robot-state','robot-pose']);
  assert.deepEqual(seen,[['robot-pose',{sequenceNo:1}],['robot-state',{dispatchReady:false}]]);
});
