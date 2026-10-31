"""Generate a sequential existing-MOVE workflow and Swagger request; never dispatch a robot."""
import argparse,copy,json,math,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def definition(count,speed=.6,version=1):
    if type(count) is not int or not 2<=count<=20:raise ValueError('Route requires 2..20 stops')
    if not math.isfinite(speed) or not .05<=speed<=.6:raise ValueError('Speed must be .05..0.60 m/s')
    doc=json.loads((ROOT/'src/main/resources/static/navigation-move-workflow.json').read_text(encoding='utf-8-sig'))
    w=doc['workflow'];w.update(workflowCode='WF_ENDPOINT_ROUTE_'+str(count),versionNo=version,
        workflowName='Đi lần lượt '+str(count)+' endpoint trong một phiên',
        description='Sequential MOVE steps; advance only on confirmed SUCCESS, preserve one navigation session, stop on failure.',
        maxDurationSec=count*180+60,maxTotalVisits=count)
    w['inputSchemaJson']['properties']['route_waypoint_ids']={'type':'array','minItems':count-1,'maxItems':count-1,
        'items':{'type':'string','format':'uuid'}}
    w['inputSchemaJson']['required'].append('route_waypoint_ids')
    w['outputMappingJson']={'stops':[{'$ref':'/outputs/MOVE_'+str(i+1).zfill(2)} for i in range(count)]}
    step=doc['graph']['steps'][0];steps=[];transitions=[]
    for i in range(count):
        code='MOVE_'+str(i+1).zfill(2);s=copy.deepcopy(step)
        s.update(stepCode=code,stepName='Đi tới endpoint thứ '+str(i+1),sequenceNo=i+1)
        s['paramsJson']['max_speed_mps']=speed
        s['inputMappingJson']['endpoint_id']={'$ref':'/inputs/endpoint_id' if i==count-1 else '/inputs/route_waypoint_ids/'+str(i)}
        steps.append(s)
        success={'fromStepCode':code,'outcome':'SUCCESS','priority':1,'defaultTransition':True}
        success.update({'terminalStatus':'SUCCEEDED'} if i==count-1 else {'toStepCode':'MOVE_'+str(i+2).zfill(2)})
        transitions.append(success)
        for outcome,terminal in [('FAILURE','FAILED'),('TIMEOUT','RECOVERY_REQUIRED')]:
            transitions.append({'fromStepCode':code,'outcome':outcome,'priority':1,'defaultTransition':True,'terminalStatus':terminal})
    doc['graph'].update(startStepCode='MOVE_01',steps=steps,transitions=transitions)
    return doc

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stops',type=int,default=4);p.add_argument('--speed',type=float,default=.6)
    p.add_argument('--version',type=int,default=1);p.add_argument('--out',type=Path,default=Path('endpoint-route'))
    p.add_argument('--map-id');p.add_argument('--robot-id');p.add_argument('--session-id');p.add_argument('--workflow-id')
    p.add_argument('--endpoints',nargs='+',help='Ordered endpoint UUIDs, including the final destination')
    a=p.parse_args();count=len(a.endpoints) if a.endpoints else a.stops
    d=definition(count,a.speed,a.version);a.out.mkdir(parents=True,exist_ok=True)
    (a.out/'workflow.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
    if a.endpoints:
        for v in [*a.endpoints,a.map_id,a.robot_id,a.session_id,a.workflow_id]:uuid.UUID(v or '')
        request=dict(mapId=a.map_id,workflowId=a.workflow_id,robotId=a.robot_id,navigationSessionId=a.session_id,
            destinationEndpointId=a.endpoints[-1],targetMode='ENDPOINT',navigationMode='STANDARD',passengerCount=1,
            priority=50,note='Sequential endpoint route',data={'route_waypoint_ids':a.endpoints[:-1]})
        (a.out/'request.json').write_text(json.dumps(request,indent=2),encoding='utf-8')
    print('GENERATED',a.out.resolve(),'; no Job created or motor command sent')
if __name__=='__main__':main()
