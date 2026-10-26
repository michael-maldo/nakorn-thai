import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('prototype_release', Path(__file__).with_name('release.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)
SHA = 'a' * 40
ID = SHA + '-123-1'


def fixture(root, text='new'):
    directory = root / 'staff-pwa-proto'
    (directory / 'assets').mkdir(parents=True)
    (directory / 'assets/app.js').write_text('const version=' + repr(text) + ';')
    (directory / 'assets/app.css').write_text('body{color:black}')
    (directory / 'icon.png').write_bytes(b'test-image')
    (directory / 'index.html').write_text('<script src="/staff-pwa-proto/assets/app.js"></script><link href="/staff-pwa-proto/assets/app.css"><link href="/staff-pwa-proto/manifest.webmanifest">')
    (directory / 'manifest.webmanifest').write_text(json.dumps({'id': deploy.PREFIX, 'scope': deploy.PREFIX, 'start_url': deploy.PREFIX, 'icons': [{'src': deploy.PREFIX + 'icon.png'}]}))
    (directory / 'sw.js').write_text("const CACHE='nakorn-proto-staff-0000000000000000',ASSETS=" + json.dumps([deploy.PREFIX + 'assets/app.js', deploy.PREFIX + 'assets/app.css']) + ';')


def manifest(root):
    files = deploy.validate(root)
    (root / 'release-manifest.json').write_text(json.dumps({'commit': SHA, 'files': files}))


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.previous = self.base / 'releases/old-manual'
        self.new = self.base / 'releases' / ID
        fixture(self.previous, 'old')
        fixture(self.new)
        manifest(self.new)
        (self.base / 'current').symlink_to(self.previous)

    def test_valid_artifact_and_manifest(self):
        self.assertEqual(len(deploy.validate(self.new, SHA, True)), 6)

    def test_legacy_manual_nginx_template_allowed_only_for_read_only_smoke(self):
        (self.previous / 'nginx.conf').write_text('original manual template')
        with self.assertRaisesRegex(ValueError, 'outside prototype'):
            deploy.validate(self.previous)
        self.assertEqual(len(deploy.validate(self.previous, allow_legacy_config=True)), 6)

    def test_smoke_cli_accepts_original_manual_release(self):
        (self.previous / 'nginx.conf').write_text('original manual template')
        with patch('sys.argv', ['release.py', 'smoke', str(self.previous)]), patch.object(deploy, 'smoke') as check:
            deploy.main()
            check.assert_called_once_with(self.previous)

    def test_uploaded_corruption_rejected_before_activation(self):
        (self.new / 'staff-pwa-proto/assets/app.js').write_text('corrupt')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            deploy.activate(self.base, ID, SHA, Mock(), Mock())
        self.assertEqual((self.base / 'current').resolve(), self.previous)

    def test_wrong_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'revision'):
            deploy.validate(self.new, 'b' * 40, True)

    def test_root_assets_and_symlinks_are_rejected(self):
        (self.new / 'outside.js').write_text('outside')
        with self.assertRaisesRegex(ValueError, 'outside prototype'):
            deploy.validate(self.new)
        (self.new / 'outside.js').unlink()
        (self.new / 'staff-pwa-proto/link.js').symlink_to('/etc/passwd')
        with self.assertRaisesRegex(ValueError, 'Symlinks'):
            deploy.validate(self.new)

    def test_missing_asset_and_scope_escape_rejected(self):
        (self.new / 'staff-pwa-proto/assets/app.js').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing'):
            deploy.validate(self.new)
        with self.assertRaisesRegex(ValueError, 'escapes'):
            deploy.file_for(self.new, '/staff-pwa-proto/../../etc/passwd')

    def test_manifest_scope_and_cache_namespace_rejected(self):
        path = self.new / 'staff-pwa-proto/manifest.webmanifest'
        value = json.loads(path.read_text())
        value['scope'] = '/'
        path.write_text(json.dumps(value))
        with self.assertRaisesRegex(ValueError, 'manifest scope'):
            deploy.validate(self.new)
        value['scope'] = deploy.PREFIX
        path.write_text(json.dumps(value))
        worker = self.new / 'staff-pwa-proto/sw.js'
        worker.write_text(worker.read_text().replace('nakorn-proto-staff-', 'nakorn-staff-'))
        with self.assertRaisesRegex(ValueError, 'namespace'):
            deploy.validate(self.new)

    def test_successful_activation_is_atomic_and_records_previous(self):
        def smoke(root):
            self.assertEqual(root, self.new)
            self.assertEqual((self.base / 'current').resolve(), self.new)
        deploy.activate(self.base, ID, SHA, smoke, lambda: {'protected': 'unchanged'})
        report = json.loads((self.base / 'backups' / (ID + '.json')).read_text())
        self.assertEqual(report['status'], 'active')
        self.assertEqual(report['previous'], str(self.previous))
        self.assertTrue(self.previous.exists())

    def test_failed_smoke_restores_manual_release_without_manifest(self):
        smoke = Mock(side_effect=[ValueError('HTTP failure'), None])
        with self.assertRaisesRegex(ValueError, 'HTTP failure'):
            deploy.activate(self.base, ID, SHA, smoke, lambda: {})
        self.assertEqual((self.base / 'current').resolve(), self.previous)
        self.assertEqual(smoke.call_args_list[1].args[0], self.previous)
        report = json.loads((self.base / 'backups' / (ID + '.json')).read_text())
        self.assertEqual(report['status'], 'rolled_back')
        self.assertNotIn('rollbackVerificationError', report)

    def test_protected_state_change_causes_rollback(self):
        capture = Mock(side_effect=[{'pid': 1}, {'pid': 2}, {'pid': 1}])
        with self.assertRaisesRegex(ValueError, 'Protected'):
            deploy.activate(self.base, ID, SHA, lambda root: None, capture)
        self.assertEqual((self.base / 'current').resolve(), self.previous)

    def test_rollback_verification_failure_is_reported_without_losing_old_release(self):
        with self.assertRaisesRegex(ValueError, 'offline'):
            deploy.activate(self.base, ID, SHA, Mock(side_effect=ValueError('offline')), lambda: {})
        self.assertEqual((self.base / 'current').resolve(), self.previous)
        report = json.loads((self.base / 'backups' / (ID + '.json')).read_text())
        self.assertIn('rollbackVerificationError', report)

    def test_invalid_release_or_wrong_revision_cannot_touch_current(self):
        for release, commit in [('../outside', SHA), (ID, 'b' * 40)]:
            with self.assertRaises(ValueError):
                deploy.activate(self.base, release, commit)
        self.assertEqual((self.base / 'current').resolve(), self.previous)

    def test_repeat_activation_attempt_is_rejected(self):
        deploy.activate(self.base, ID, SHA, lambda root: None, lambda: {})
        with self.assertRaisesRegex(ValueError, 'already attempted'):
            deploy.activate(self.base, ID, SHA, lambda root: None, lambda: {})

    def test_prepare_cannot_overwrite_existing_release(self):
        with patch.object(deploy, 'BASE', self.base), patch('sys.argv', ['release.py', 'prepare', ID, '--commit', SHA]):
            with self.assertRaises(FileExistsError):
                deploy.main()


if __name__ == '__main__':
    unittest.main()
