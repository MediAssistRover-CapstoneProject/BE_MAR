"""Use an already provisioned EC2 DB over a pinned SSH tunnel. Never imports/drops data.

Private .env.aws, key, known_hosts, MySQL CA/truststore and a verified SQL backup
must already exist in secrets/aws. First start is inspection; enable dispatch
only after map storage and DEVICE auth have been checked.
"""
import argparse,json,pathlib,subprocess,tempfile,time,urllib.request
import cloud_mysql as common

ROOT=pathlib.Path(__file__).resolve().parents[1]
NETWORK='mar-emqx-demo_default'

def compose(project):
    return ['docker','compose','-p','mar-emqx-demo','--env-file',str(project/'.env'),
        '--env-file',str(project/'.env.aws'),'-f',str(project/'compose.yaml'),
        '-f',str(project/'deploy/compose.aws.yaml'),'--profile','production']

def require_setup(project):
    for name in ['.env.aws','secrets/aws/ec2-key.pem','secrets/aws/known_hosts',
                 'secrets/aws/ca.pem','secrets/aws/truststore.p12','secrets/aws/backup-receipt.json']:
        if not (project/name).is_file():raise RuntimeError('AWS setup missing: '+name)
    cfg=common.read_env(project/'.env.aws')
    if cfg.get('AWS_DB_URL')!='jdbc:mysql://aws-db-tunnel:3306/mediassistrover?sslMode=VERIFY_CA':
        raise RuntimeError('Unexpected AWS JDBC route; use the internal pinned SSH tunnel')
    return cfg

class Client:
    def __init__(self,project,cfg):
        self.tmp=tempfile.TemporaryDirectory(prefix='client-',dir=project/'secrets/aws')
        folder=pathlib.Path(self.tmp.name)
        import shutil
        shutil.copy2(project/'secrets/aws/ca.pem',folder/'ca.pem')
        text='[client]\nhost=aws-db-tunnel\nport=3306\nuser='+common.option(cfg['AWS_DB_USERNAME'])
        text+='\npassword='+common.option(cfg['AWS_DB_PASSWORD'])
        text+='\ndatabase=mediassistrover\nconnect-timeout=8\nssl-mode=VERIFY_CA\nssl-ca=/migration/ca.pem\ndefault-character-set=utf8mb4\n'
        (folder/'client.cnf').write_text(text,encoding='utf-8')
        self.base=['docker','run','--rm','-i','--network',NETWORK,'--mount',
            'type=bind,src='+str(folder)+',dst=/migration,readonly','--entrypoint','/bin/sh',
            'mysql:8.4','-c',common.CLIENT_BOOTSTRAP,'mar-aws-mysql']
    def query(self,sql):
        return common.command(self.base+['-e',sql],stdout=subprocess.PIPE).stdout.decode().strip()
    def close(self):self.tmp.cleanup()

def source_idle(project):
    running=common.command(['docker','inspect','--format','{{.State.Running}}',common.BACKEND],stdout=subprocess.PIPE).stdout.strip()
    if running!=b'true':return
    cfg=common.read_env(project/'.env');base='http://127.0.0.1:8080'
    req=urllib.request.Request(base+'/api/v1/auth/login',data=json.dumps({
        'username':cfg['ADMIN_USERNAME'],'password':cfg['ADMIN_PASSWORD']}).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=15) as response:a=json.load(response)
    token=a.get('data',a)['accessToken']
    for status in ('RUNNING','QUEUED','ACCEPTED','RECOVERY_REQUIRED'):
        req=urllib.request.Request(base+'/api/v1/jobs?status='+status+'&size=100',headers={'Authorization':'Bearer '+token})
        with urllib.request.urlopen(req,timeout=15) as response:d=json.load(response)
        d=d.get('data',d) if isinstance(d,dict) else d
        if isinstance(d,dict) and not any(k in d for k in ('content','items')):
            raise RuntimeError('Unknown Job response; refusing restart')
        values=d.get('content',d.get('items',[])) if isinstance(d,dict) else d
        if not isinstance(values,list):raise RuntimeError('Unknown Job response; refusing restart')
        if any(x.get('status')==status for x in values):raise RuntimeError('Close held source Jobs before switching/restarting DB')

def inspect(client):
    return {'flyway':client.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1'),
        'robot_ids':client.query('SELECT BIN_TO_UUID(robot_id),robot_code FROM Robot ORDER BY robot_code'),
        'map_ids':client.query('SELECT BIN_TO_UUID(map_id),asset_hash FROM Map ORDER BY map_id')}

def require_verified(state,receipt):
    if int(state['flyway'])<39 or any(not receipt.get(k) or
            not set(receipt[k].splitlines())<=set(state[k].splitlines()) for k in ('robot_ids','map_ids')):
        raise RuntimeError('AWS identity/schema changed since inspection; dispatch remains off')
    if not receipt.get('device_auth_verified') or not receipt.get('map_storage_verified'):
        raise RuntimeError('Verify DEVICE auth and map bytes before enabling dispatch')

def use(project,enable=False):
    cfg=require_setup(project);source_idle(project)
    args=compose(project);common.command(args+['config','-q'],stdout=subprocess.PIPE)
    common.command(args+['up','-d','--build','aws-db-tunnel'],stdout=None)
    client=Client(project,cfg)
    try:
        # Includes fresh TLS handshake through the SSH tunnel and target Job checks.
        state=inspect(client);common.idle(client.query)
        if enable:
            receipt=json.loads((project/'secrets/aws/inspection-receipt.json').read_text())
            require_verified(state,receipt)
    finally:client.close()
    flag='true' if enable else 'false'
    cfg.update(AWS_OPERATIONS_SCHEDULER_ENABLED=flag,AWS_MQTT_ENABLED=flag,AWS_MQTT_DISPATCH_ENABLED=flag)
    common.private_env(project/'.env.aws',cfg)
    common.command(args+['build','backend'],stdout=None)
    common.command(args+['up','-d','--no-deps','backend'],stdout=None)
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/actuator/health',timeout=3) as r:
                if json.load(r).get('status')=='UP':break
        except Exception:pass
        time.sleep(2)
    else:raise RuntimeError('AWS backend not healthy; inspect logs before robot startup')
    # Inspection mode intentionally becomes the selected provider too: later
    # network/certificate renewal must never silently recreate an Aiven backend.
    (project/'secrets/database-provider.txt').write_text('aws\n',encoding='ascii')
    common.command(['docker','exec','mar-emqx-demo-reverse-proxy-1','nginx','-s','reload'],stdout=None)
    print('BE_ON_AWS; DISPATCH_ENABLED' if enable else 'BE_ON_AWS_INSPECTION; MQTT and dispatch OFF')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',type=pathlib.Path,default=ROOT)
    p.add_argument('--enable-dispatch',action='store_true');a=p.parse_args()
    use(a.project.resolve(strict=True),a.enable_dispatch)

if __name__=='__main__':
    try:main()
    except Exception as e:raise SystemExit('AWS_START_STOPPED: '+str(e))
