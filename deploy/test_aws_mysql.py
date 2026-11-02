"""Cutover admission guards only; no network, DB mutations or robot commands."""
import unittest
from unittest.mock import patch
from pathlib import Path
import aws_mysql as a

class AwsGuards(unittest.TestCase):
    def state(self):return dict(flyway='39',robot_ids='robot\tcode',map_ids='map\thash')
    def receipt(self):return {**self.state(), 'device_auth_verified':True,'map_storage_verified':True}
    def test_new_scanned_maps_do_not_break_restart_but_changed_verified_identity_does(self):
        s=self.state();s['map_ids']+='\nnew-map\tnew-hash';a.require_verified(s,self.receipt())
        for key in ('map_ids','robot_ids'):
            s=self.state();s[key]='foreign'
            with self.assertRaisesRegex(RuntimeError,'identity/schema'):a.require_verified(s,self.receipt())
    def test_missing_auth_map_bytes_or_schema_upgrade_never_enable_dispatch(self):
        for field in ('device_auth_verified','map_storage_verified','robot_ids','map_ids'):
            r=self.receipt();r.pop(field)
            with self.assertRaises(RuntimeError):a.require_verified(self.state(),r)
        s=self.state();s['flyway']='38'
        with self.assertRaises(RuntimeError):a.require_verified(s,self.receipt())
    def test_held_source_job_stops_before_tunnel_or_backend_is_changed(self):
        with patch.object(a,'require_setup',return_value={}),patch.object(a,'source_idle',side_effect=RuntimeError('held source Jobs')),patch.object(a.common,'command') as command:
            with self.assertRaisesRegex(RuntimeError,'held source'):a.use(Path('.'),True)
            command.assert_not_called()

if __name__=='__main__':unittest.main()
