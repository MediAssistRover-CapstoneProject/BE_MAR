const test=require('node:test');
const assert=require('node:assert/strict');
const {plannedPath}=require('../../main/resources/static/map-navigation.js');

test('initial path remains visible without pose refresh and disappears when the step or session ends',()=>{
  const meta={map_id:'map',asset_hash:'a'.repeat(64),frame_id:'map',width_px:100,height_px:100,
    resolution_m_per_pixel:.025,origin_x_m:0,origin_y_m:0,origin_yaw_rad:0};
  const plan={sessionId:'session',mapId:'map',mapAssetHash:meta.asset_hash,frameId:'map',initializationRevision:1,
    jobId:'job',jobStepId:'step-a',points:[{xM:.3,yM:.3},{xM:1,yM:1}]};
  const snapshot={activeJobId:'job',selectedJobStepId:'step-a',selectedStepType:'MOVE',navigationPlan:plan,
    navigationSession:{sessionId:'session',initialization:{revision:1}}};
  assert.equal(plannedPath(snapshot,meta),plan);
  assert.equal(plannedPath({...snapshot,latestPose:null,mqttConnected:false,heartbeatFresh:false},meta),plan,
    'observation freshness does not change the static plan');
  for(const change of [{activeJobId:null},{selectedJobStepId:'step-b'},{navigationPlan:null},
    {navigationSession:{sessionId:'other',initialization:{revision:1}}}])
    assert.equal(plannedPath({...snapshot,...change},meta),null);
});
