"""Export/import a stopped MAR DB; no cloud writes without an explicit import command.

Passwords are prompted, never passed as process arguments. No pip packages required.
SQL/credentials belong outside Git. The laptop keeps its map volume and HTTPS URL.
"""
import argparse,datetime,getpass,hashlib,json,os,pathlib,re,secrets,shutil,subprocess,sys,tempfile,time,urllib.request

HOST='mysql-2c54a41d-fpt-9265.i.aivencloud.com'
MYSQL='mar-emqx-demo-mysql-1'
BACKEND='mar-emqx-demo-backend-1'
STABLE=('Account','Robot','Map','Endpoint','Zone','Workflow','Job','flyway_schema_history')
TERMINAL="'COMPLETED','REJECTED','ABANDONED','RESOLVED_BY_STAFF','STOP_CONFIRMED','EXPIRED','CANCELLED'"
SANITIZE="""
-- Run only against the freshly imported destination, with BE stopped.
START TRANSACTION;
UPDATE Robot SET status='OFFLINE',last_heartbeat_at=NULL;
UPDATE RobotMqttSession SET closed=1,handshake_accepted=0,handshake_acked=0,
 dispatch_ready=0,failure_reason='DB_MIGRATION_NEW_HANDSHAKE_REQUIRED';
UPDATE RobotNavigationSession SET closed_at=COALESCE(closed_at,UTC_TIMESTAMP(6)),planned_path_json=NULL;
DELETE FROM RobotNavigationProfile;
DELETE FROM RobotOperationAuthorization;
UPDATE ShedLock SET lock_until=UTC_TIMESTAMP(6);
COMMIT;
"""

def command(args,**kw):
    r=subprocess.run(args,stderr=subprocess.PIPE,**kw)
    if r.returncode:raise RuntimeError('Command failed: '+args[0]+'; '+r.stderr.decode(errors='replace')[:600])
    return r

def private_path(path,directory=False):
    if os.name=='nt':
        identity=command(['whoami'],stdout=subprocess.PIPE).stdout.decode().strip()
        rights='(OI)(CI)F' if directory else 'F'
        command(['icacls',str(path),'/inheritance:r','/grant:r',identity+':'+rights,'SYSTEM:'+rights],stdout=subprocess.PIPE)
    else:path.chmod(0o700 if directory else 0o600)

def source(query):
    r=command(['docker','exec',MYSQL,'sh','-c',
        'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -B "$@"','mysql','-e',query],stdout=subprocess.PIPE)
    return r.stdout.decode('utf-8').strip()

def inventory(query):
    names=[n for n in query('SHOW TABLES').splitlines() if re.fullmatch('[A-Za-z0-9_]+',n)]
    present={n.lower():n for n in names}
    counts={n:int(query('SELECT COUNT(*) FROM `'+present[n.lower()]+'`')) for n in STABLE if n.lower() in present}
    if not {'Robot','Map','Endpoint','Zone','Workflow','Job','flyway_schema_history'}<=counts.keys():
        raise RuntimeError('Not a complete MAR database')
    return dict(counts=counts,flyway=query('SELECT version,COALESCE(checksum,0),success FROM flyway_schema_history ORDER BY installed_rank'),
        robot_ids=query('SELECT BIN_TO_UUID(robot_id),robot_code FROM Robot ORDER BY robot_code'),
        map_ids=query('SELECT BIN_TO_UUID(map_id),asset_hash FROM Map ORDER BY map_id'))

def idle(query):
    for label,sql in (
      ('active/recovery jobs',"SELECT COUNT(*) FROM Job WHERE status IN ('QUEUED','ACCEPTED','RUNNING','RECOVERY_REQUIRED')"),
      ('pending physical commands','SELECT COUNT(*) FROM RobotCommandOutbox WHERE state NOT IN ('+TERMINAL+')'),
      ('open navigation sessions','SELECT COUNT(*) FROM RobotNavigationSession WHERE closed_at IS NULL AND expires_at>UTC_TIMESTAMP(6)'),
      ('operation authorizations','SELECT COUNT(*) FROM RobotOperationAuthorization WHERE valid_until>UTC_TIMESTAMP(6)')):
        if int(query(sql)):raise RuntimeError('Close '+label+' before migrating')

def compose(project,cloud=False):
    args=['docker','compose','-p','mar-emqx-demo','--env-file',str(project/'.env')]
    if cloud:args+=['--env-file',str(project/'.env.aiven')]
    args+=['-f',str(project/'compose.yaml')]
    if cloud:args+=['-f',str(project/'deploy/compose.aiven.yaml')]
    return args+['--profile','production']

def export_db(a):
    project=a.project.resolve(strict=True);out=a.output.resolve()
    if out.exists() and any(out.iterdir()):raise RuntimeError('Output directory is not empty; use a new folder')
    idle(source)
    for table,column in (('triggers','trigger_schema'),('routines','routine_schema'),('views','table_schema')):
        if int(source('SELECT COUNT(*) FROM information_schema.'+table+' WHERE '+column+'=DATABASE()')):
            raise RuntimeError('Unexpected '+table+'; inspect before exporting')
    if a.freeze_source:command(compose(project)+['stop','backend'],stdout=subprocess.PIPE)
    running=command(['docker','inspect','--format','{{.State.Running}}',BACKEND],stdout=subprocess.PIPE).stdout.strip()==b'true'
    idle(source);meta=inventory(source);out.mkdir(parents=True,exist_ok=True);private_path(out,True)
    sql=out/'01_mar_to_aiven.sql';tmp=out/'01_mar_to_aiven.partial'
    try:
        with tmp.open('wb') as f:
            f.write(b'-- MAR snapshot: import into EMPTY defaultdb only. Contains private account/device data.\n')
            command(['docker','exec',MYSQL,'sh','-c','exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$@" "$MYSQL_DATABASE"','mysqldump',
                '--single-transaction','--quick','--hex-blob','--default-character-set=utf8mb4','--set-gtid-purged=OFF',
                '--column-statistics=0','--no-tablespaces','--skip-add-locks','--skip-lock-tables',
                '--skip-add-drop-table','--skip-triggers','--skip-comments'],stdout=f)
            f.write(SANITIZE.encode())
        text=tmp.read_text(encoding='utf-8')
        if re.search(r'^\s*(DROP\s|CREATE DATABASE\s|USE\s)',text,re.M|re.I):raise RuntimeError('Unexpected cross-database/destructive SQL')
        os.replace(tmp,sql)
        assets=out/'map-artifacts';assets.mkdir()
        command(['docker','cp',BACKEND+':/app/data/map-artifacts/.',str(assets)],stdout=subprocess.PIPE)
        meta.update(created_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),cutover_ready=not running,
            sql_sha256=hashlib.sha256(sql.read_bytes()).hexdigest(),source_mysql_version=source('SELECT VERSION()'),
            asset_hashes={str(p.relative_to(assets)):hashlib.sha256(p.read_bytes()).hexdigest() for p in assets.rglob('*') if p.is_file()})
        (out/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
        print('SQL_READY',sql);print('SOURCE_STOPPED',not running);print('MAP_ASSET_BACKUP',assets)
        if running:print('SNAPSHOT_ONLY: regenerate with --freeze-source at final cutover; source was not stopped.')
    finally:tmp.unlink(missing_ok=True)

def option(value):
    if any(x in value for x in '\r\n\x00'):raise ValueError('Invalid newline/control in connection setting')
    return '"'+value.replace('\\','\\\\').replace('"','\\"')+'"'

class Cloud:
    def __init__(self,project,host,port,user,password,ca):
        if not re.fullmatch('[A-Za-z0-9.-]+',host) or not 1<=port<=65535:raise ValueError('Invalid host/port')
        self.tmp=tempfile.TemporaryDirectory(prefix='client-',dir=project/'secrets/aiven')
        folder=pathlib.Path(self.tmp.name);shutil.copy2(ca,folder/'ca.pem')
        config='[client]\nhost='+option(host)+'\nport='+str(port)+'\nuser='+option(user)+'\npassword='+option(password)+'\ndatabase=defaultdb\nconnect-timeout=10\nssl-mode=VERIFY_IDENTITY\nssl-ca=/migration/ca.pem\ndefault-character-set=utf8mb4\n'
        p=folder/'client.cnf';p.write_text(config,encoding='utf-8');p.chmod(0o600)
        self.base=['docker','run','--rm','-i','--mount','type=bind,src='+str(folder)+',dst=/migration,readonly',
                   'mysql:8.4','mysql','--defaults-extra-file=/migration/client.cnf','--binary-mode=1','-N','-B']
    def query(self,sql):return command(self.base+['-e',sql],stdout=subprocess.PIPE).stdout.decode('utf-8').strip()
    def load(self,path):
        with path.open('rb') as f:command(self.base,stdin=f,stdout=subprocess.PIPE)
    def close(self):self.tmp.cleanup()

def read_env(path):
    result={}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            k,v=line.split('=',1);result[k.strip()]=v.strip().strip("'").replace("\\'","'")
    return result

def private_env(path,values):
    # Compose single quotes make $, # and backslashes literal. Reject line breaks.
    for v in values.values():option(v)
    path.write_text('\n'.join(k+"='"+v.replace("'","\\'")+"'" for k,v in values.items())+'\n',encoding='utf-8');private_path(path)

def keytool():
    found=shutil.which('keytool')
    candidates=[pathlib.Path(os.environ.get('JAVA_HOME',''))/'bin/keytool.exe',
        pathlib.Path.home()/'Downloads/MAR_Java_Tools/jdk-21.0.12.1+1/bin/keytool.exe']
    return found or next((str(p) for p in candidates if p.is_file()),None)

def import_db(a):
    project=a.project.resolve(strict=True);sql=a.sql.resolve(strict=True);ca=a.ca.resolve(strict=True)
    meta=json.loads((sql.parent/'manifest.json').read_text())
    if hashlib.sha256(sql.read_bytes()).hexdigest()!=meta['sql_sha256']:raise RuntimeError('SQL_HASH_MISMATCH')
    if re.search(r'^\s*(DROP\s|CREATE DATABASE\s|USE\s)',sql.read_text(encoding='utf-8'),re.M|re.I):
        raise RuntimeError('Unexpected cross-database/destructive SQL')
    if not meta['cutover_ready']:raise RuntimeError('Regenerate SQL with --freeze-source for cutover; this is only a live-source snapshot')
    if command(['docker','inspect','--format','{{.State.Running}}',BACKEND],stdout=subprocess.PIPE).stdout.strip()!=b'false':
        raise RuntimeError('Stop the local backend before importing/cutover')
    tool=keytool()
    if not tool:raise RuntimeError('JAVA_HOME/keytool is required to create the JDBC CA truststore')
    folder=project/'secrets/aiven';folder.mkdir(parents=True,exist_ok=True);private_path(folder,True)
    password=getpass.getpass('Aiven MySQL password (not saved in Git): ')
    cloud=Cloud(project,a.host,a.port,a.user,password,ca)
    try:
        tables=int(cloud.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='defaultdb'"))
        if a.verify_existing:
            if not tables:raise RuntimeError('defaultdb is empty; import the SQL first or omit --verify-existing')
        else:
            if tables:raise RuntimeError('defaultdb is not empty; no table or database was deleted')
            cloud.load(sql)
        actual=inventory(cloud.query);idle(cloud.query)
        if any(actual[k]!=meta[k] for k in ('counts','flyway','robot_ids','map_ids')):
            raise RuntimeError('Imported data differs; do not start BE or repeat the import over existing tables')
        store_password=secrets.token_urlsafe(24);tmp=folder/('truststore-'+secrets.token_hex(6)+'.p12')
        child_env={**os.environ,'MAR_AIVEN_STORE_PASSWORD':store_password}
        command([tool,'-importcert','-noprompt','-alias','mar-aiven','-file',str(ca),'-keystore',str(tmp),
                 '-storetype','PKCS12','-storepass:env','MAR_AIVEN_STORE_PASSWORD'],env=child_env,stdout=subprocess.PIPE)
        os.replace(tmp,folder/'truststore.p12')
        if ca!=folder/'ca.pem':shutil.copy2(ca,folder/'ca.pem')
        private_env(project/'.env.aiven',dict(AIVEN_DB_URL='jdbc:mysql://'+a.host+':'+str(a.port)+'/defaultdb?sslMode=VERIFY_IDENTITY',
            AIVEN_DB_USERNAME=a.user,AIVEN_DB_PASSWORD=password,AIVEN_DB_TRUSTSTORE_PASSWORD=store_password,
            CLOUD_OPERATIONS_SCHEDULER_ENABLED='false',CLOUD_MQTT_ENABLED='false',CLOUD_MQTT_DISPATCH_ENABLED='false'))
        receipt={**actual,'host':a.host,'port':a.port,'sql_sha256':meta['sql_sha256'],'snapshot_dir':str(sql.parent)}
        (folder/'import-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
        print('AIVEN_IMPORT_VERIFIED; IDs, Flyway history and map hashes match. Private .env.aiven created.')
    finally:cloud.close()

def use_db(a):
    project=a.project.resolve(strict=True);env=read_env(project/'.env.aiven')
    receipt=json.loads((project/'secrets/aiven/import-receipt.json').read_text())
    snapshot=pathlib.Path(receipt['snapshot_dir']);meta=json.loads((snapshot/'manifest.json').read_text())
    # Same laptop must still have every map artifact; DB rows alone are insufficient.
    with tempfile.TemporaryDirectory(prefix='asset-check-',dir=project/'secrets/aiven') as folder:
        command(['docker','cp',BACKEND+':/app/data/map-artifacts/.',folder],stdout=subprocess.PIPE)
        for name,digest in meta['asset_hashes'].items():
            p=pathlib.Path(folder)/name
            if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=digest:raise RuntimeError('Local map volume changed: '+name)
    cloud=Cloud(project,receipt['host'],receipt['port'],env['AIVEN_DB_USERNAME'],env['AIVEN_DB_PASSWORD'],project/'secrets/aiven/ca.pem')
    try:
        actual=inventory(cloud.query);idle(cloud.query)
        for k in ('robot_ids','map_ids'):
            if not set(receipt[k].splitlines())<=set(actual[k].splitlines()):raise RuntimeError('Cloud robot/map identity differs from verified import')
    finally:cloud.close()
    # This is the same Compose backend service, not a second BE instance.
    flag='true' if a.enable_dispatch else 'false'
    env.update(CLOUD_OPERATIONS_SCHEDULER_ENABLED=flag,CLOUD_MQTT_ENABLED=flag,CLOUD_MQTT_DISPATCH_ENABLED=flag)
    private_env(project/'.env.aiven',env)
    args=compose(project,True);command(args+['config','-q'],stdout=subprocess.PIPE)
    command(args+['build','backend'],stdout=None)
    command(args+['up','-d','--no-deps','backend'],stdout=None)
    for _ in range(30):
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/actuator/health',timeout=2) as r:
                if json.load(r).get('status')=='UP':break
        except Exception:pass
        time.sleep(1)
    else:raise RuntimeError('BE not ready; inspect logs. Do not switch robot or enable dispatch.')
    command(args+['exec','-T','reverse-proxy','nginx','-s','reload'],stdout=None)
    print('BE_ON_AIVEN; Pi HTTPS URL, robot ID/secret and local map volume unchanged.')
    print('DISPATCH_ENABLED' if a.enable_dispatch else 'INSPECTION_MODE: MQTT and schedulers remain off; inspect maps, endpoints and zones first.')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[1])
    s=p.add_subparsers(dest='action',required=True)
    e=s.add_parser('export');e.add_argument('--output',type=pathlib.Path,required=True);e.add_argument('--freeze-source',action='store_true')
    i=s.add_parser('import');i.add_argument('--sql',type=pathlib.Path,required=True);i.add_argument('--ca',type=pathlib.Path,required=True)
    i.add_argument('--host',default=HOST);i.add_argument('--port',type=int,default=12636);i.add_argument('--user',default='avnadmin')
    i.add_argument('--verify-existing',action='store_true',help='After manual SQL import: verify exact data and create config; does not import again')
    u=s.add_parser('use');u.add_argument('--enable-dispatch',action='store_true')
    a=p.parse_args();{'export':export_db,'import':import_db,'use':use_db}[a.action](a)
if __name__=='__main__':
    try:main()
    except Exception as e:print('MIGRATION_STOPPED:',str(e),file=sys.stderr);sys.exit(1)
