"""Migration guards only; no Docker, cloud, secret files or robot activity."""
import hashlib,json,pathlib,subprocess,tempfile,types,unittest
from unittest.mock import Mock,patch
import cloud_mysql as m

class Guards(unittest.TestCase):
    def test_mysql_uses_a_private_linux_copy_and_explicit_tcp(self):
        self.assertIn('mktemp /tmp/mar-mysql.',m.CLIENT_BOOTSTRAP)
        self.assertIn('chmod 600 "$mar_client_config"',m.CLIENT_BOOTSTRAP)
        self.assertIn('--defaults-extra-file="$mar_client_config"',m.CLIENT_BOOTSTRAP)
        self.assertIn('--protocol=TCP',m.CLIENT_BOOTSTRAP)
        self.assertNotIn('--defaults-extra-file=/migration/client.cnf',m.CLIENT_BOOTSTRAP)
    def fixture(self,root):
        sql=root/'01_mar_to_aiven.sql';sql.write_text('SELECT 1;\n')
        ca=root/'ca.pem';ca.write_text('dummy')
        meta=dict(sql_sha256=hashlib.sha256(sql.read_bytes()).hexdigest(),cutover_ready=True)
        (root/'manifest.json').write_text(json.dumps(meta))
        return types.SimpleNamespace(project=root,sql=sql,ca=ca,host='example.invalid',port=12636,user='test',verify_existing=False)
    def test_nonempty_destination_never_runs_load(self):
        with tempfile.TemporaryDirectory() as d:
            a=self.fixture(pathlib.Path(d));client=Mock();client.query.return_value='1'
            with patch.object(m,'command',return_value=types.SimpleNamespace(stdout=b'false')),patch.object(m,'keytool',return_value='keytool'),patch.object(m,'private_path'),patch.object(m.getpass,'getpass',return_value='dummy'),patch.object(m,'Cloud',return_value=client):
                with self.assertRaisesRegex(RuntimeError,'not empty'):m.import_db(a)
            client.load.assert_not_called();client.close.assert_called_once()
            self.assertFalse((a.project/'.env.aiven').exists())
    def test_modified_sql_fails_before_cloud_connection(self):
        with tempfile.TemporaryDirectory() as d:
            a=self.fixture(pathlib.Path(d));a.sql.write_text('SELECT 2;')
            with patch.object(m,'Cloud') as client:
                with self.assertRaisesRegex(RuntimeError,'SQL_HASH'):m.import_db(a)
                client.assert_not_called()
    def test_preview_requires_new_frozen_export(self):
        with tempfile.TemporaryDirectory() as d:
            a=self.fixture(pathlib.Path(d));p=a.sql.parent/'manifest.json';meta=json.loads(p.read_text());meta['cutover_ready']=False;p.write_text(json.dumps(meta))
            with patch.object(m,'Cloud') as client:
                with self.assertRaisesRegex(RuntimeError,'freeze-source'):m.import_db(a)
                client.assert_not_called()
    def test_active_job_refuses_cutover(self):
        with self.assertRaisesRegex(RuntimeError,'active/recovery jobs'):m.idle(lambda _: '1')

if __name__=='__main__':unittest.main()
