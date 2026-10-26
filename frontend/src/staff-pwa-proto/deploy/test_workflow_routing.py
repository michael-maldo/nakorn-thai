"""Keep prototype pushes independent without adding a YAML dependency to CI."""
import fnmatch
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[4]


def patterns(name):
    text = (ROOT / '.github/workflows' / name).read_text()
    block = text.split('  push:', 1)[1].split('  workflow_dispatch:', 1)[0]
    return re.findall(r"^\s+- '([^']+)'$", block, re.MULTILINE)


def matches(rules, name):
    included = False
    for rule in rules:
        if fnmatch.fnmatchcase(name, rule.lstrip('!')):
            included = not rule.startswith('!')
    return included


class WorkflowRoutingTests(unittest.TestCase):
    def test_prototype_only_paths_never_trigger_existing_deployment(self):
        old = patterns('deploy.yml')
        new = patterns('deploy-staff-pwa-proto.yml')
        for path in ('frontend/src/staff-pwa-proto/StaffProtoApp.jsx', 'frontend/src/staff-pwa-proto/deploy/release.py', 'frontend/staff-pwa-proto/index.html', 'frontend/vite.proto.config.js', '.github/workflows/deploy-staff-pwa-proto.yml'):
            self.assertTrue(matches(new, path), path)
            self.assertFalse(matches(old, path), path)
        self.assertFalse(matches(old, '.github/workflows/deploy.yml'))

    def test_original_app_deployment_paths_remain_effective(self):
        old = patterns('deploy.yml')
        new = patterns('deploy-staff-pwa-proto.yml')
        for path in ('backend/pom.xml', 'frontend/src/domains/menu/pages/MenuPage.jsx', 'infrastructure/systemd/nakorn-thai-backend.service'):
            self.assertTrue(matches(old, path), path)
            self.assertFalse(matches(new, path), path)
        self.assertFalse(matches(new, 'android-staff/app/build.gradle'))

    def test_consumed_shared_dependencies_rebuild_prototype(self):
        new = patterns('deploy-staff-pwa-proto.yml')
        for path in ('frontend/package.json', 'frontend/package-lock.json', 'frontend/src/assets/images/signature-green-curry.jpg', 'frontend/tests/e2e/staffIdentityFixtures.mjs', 'frontend/tests/e2e/staffLayoutFixtures.mjs'):
            self.assertTrue(matches(new, path), path)

    def test_shared_orders_changes_trigger_both_frontend_workflows(self):
        old = patterns('deploy.yml')
        new = patterns('deploy-staff-pwa-proto.yml')
        for path in ('frontend/src/shared/staff-orders/audio/alerts.js', 'frontend/src/shared/staff-orders/components/OrderDetails.jsx', 'frontend/src/domains/ordering/components/OrderItemOptions.jsx', 'frontend/src/domains/staff/components/StaffPresentation.jsx'):
            self.assertTrue(matches(old, path), path)
            self.assertTrue(matches(new, path), path)

    def test_no_shared_deployment_or_service_actions_in_dedicated_job(self):
        workflow = (ROOT / '.github/workflows/deploy-staff-pwa-proto.yml').read_text()
        self.assertIn('group: staff-pwa-proto-deploy', workflow)
        self.assertIn('cancel-in-progress: false', workflow)
        self.assertIn('StrictHostKeyChecking yes', workflow)
        self.assertIn('VPS_KNOWN_HOSTS', workflow)
        for forbidden in ('npm run build\n', 'activate-backend.sh', 'systemctl restart', 'sudo ', '/opt/nakorn-thai/', 'postgres:', 'workflow_call', 'workflow_run'):
            self.assertNotIn(forbidden, workflow)


if __name__ == '__main__':
    unittest.main()
