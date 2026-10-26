#!/usr/bin/env python3
"""Prototype-only release validation, atomic activation and read-only smoke checks."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = Path('/var/www/nakorn-thai/prototype')
ORIGIN = 'https://nakorn-thai.tech-labs.dev'
PREFIX = '/staff-pwa-proto/'
RELEASE = re.compile(r'^[0-9a-f]{40}-[0-9]+-[0-9]+$')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_for(root, url):
    require(url.startswith(PREFIX) and not any(c in url for c in ('?', '#', '%', '\\')), 'Invalid prototype asset URL: ' + url)
    path = root / url.lstrip('/')
    require(path.resolve().is_relative_to(root.resolve()), 'Asset escapes release')
    require(path.is_file() and not path.is_symlink(), 'Missing/unsafe asset: ' + url)
    return path


def inventory(root, allow_legacy_config=False):
    require(root.is_dir() and not root.is_symlink(), 'Release must be a real directory')
    result = {}
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink(), 'Symlinks are forbidden in release assets')
        if path.is_file() and path != root / 'release-manifest.json':
            relative = path.relative_to(root).as_posix()
            if allow_legacy_config and relative == 'nginx.conf':
                continue  # The original manual release kept its unused Nginx template here.
            require(relative.startswith('staff-pwa-proto/'), 'File outside prototype namespace: ' + relative)
            data = path.read_bytes()
            require(bool(data), 'Empty release asset: ' + relative)
            result[relative] = sha(data)
    require(bool(result), 'Empty release')
    return result


def validate(root, commit=None, verify_manifest=False, allow_legacy_config=False):
    files = inventory(root, allow_legacy_config)
    html = file_for(root, PREFIX + 'index.html').read_text()
    references = re.findall(r'(?:src|href)="([^"]+)"', html)
    require(references and any(p.endswith('.js') for p in references) and any(p.endswith('.css') for p in references), 'HTML lacks JS/CSS')
    for url in references:
        file_for(root, url)
    manifest = json.loads(file_for(root, PREFIX + 'manifest.webmanifest').read_text())
    for field in ('id', 'scope', 'start_url'):
        require(manifest.get(field) == PREFIX, 'Incorrect manifest ' + field)
    require(bool(manifest.get('icons')), 'Manifest lacks icons')
    for icon in manifest['icons']:
        file_for(root, icon['src'])
    worker = file_for(root, PREFIX + 'sw.js').read_text()
    require(re.search(r"CACHE='nakorn-proto-staff-[0-9a-f]{16}'", worker), 'Wrong worker cache namespace')
    require('nakorn-staff-' not in worker, 'Protected cache namespace referenced')
    match = re.search(r'ASSETS=(\[[^;]+\])', worker)
    require(match is not None, 'Worker lacks immutable asset allowlist')
    assets = json.loads(match.group(1))
    require(assets and any(p.endswith('.js') for p in assets) and any(p.endswith('.css') for p in assets), 'Worker lacks JS/CSS')
    for url in assets:
        require(url.startswith(PREFIX + 'assets/') and url.endswith(('.js', '.css')), 'Unsafe worker cache URL')
        file_for(root, url)
    for path in root.rglob('*'):
        if path.suffix == '.js':
            for url in re.findall(r'/staff-pwa-proto/assets/[^"\'`\s)]+?\.(?:js|css|jpg|jpeg|png|svg|woff2?)', path.read_text()):
                file_for(root, url)
        if path.suffix == '.css':
            for url in re.findall(r'url\(["\']?([^"\')]+)', path.read_text()):
                if not url.startswith('data:'):
                    file_for(root, url)
    if verify_manifest:
        recorded = json.loads((root / 'release-manifest.json').read_text())
        require(recorded['commit'] == commit, 'Uploaded commit does not match requested revision')
        require(recorded['files'] == files, 'Uploaded files differ from checksum manifest')
    return files


def fetch(url, expected_status=200):
    request = urllib.request.Request(url, headers={'Cache-Control': 'no-cache', 'User-Agent': 'nakorn-staff-proto-deploy'})
    try:
        response = urllib.request.urlopen(request, timeout=12)
    except urllib.error.HTTPError as error:
        require(error.code == expected_status, 'HTTP ' + str(error.code) + ': ' + url)
        return b'', error.headers
    with response:
        require(response.status == expected_status, 'Unexpected HTTP response: ' + url)
        require(urllib.parse.urlsplit(response.url).netloc == urllib.parse.urlsplit(url).netloc, 'Unexpected host redirect')
        return response.read(), response.headers


def smoke(root, origin=ORIGIN):
    files = validate(root, allow_legacy_config=True)
    for relative, expected in files.items():
        data, headers = fetch(origin + '/' + relative)
        require(sha(data) == expected, 'Live response mismatch: ' + relative)
        mime = headers.get_content_type()
        if relative.endswith('.js'):
            require(mime in ('application/javascript', 'text/javascript'), 'Incorrect JavaScript MIME type')
        if relative.endswith('.css'):
            require(mime == 'text/css', 'Incorrect CSS MIME type')
        if relative.endswith('sw.js'):
            require(headers.get('Service-Worker-Allowed', PREFIX) == PREFIX, 'Worker scope widened')
    data, _ = fetch(origin + PREFIX)
    require(sha(data) == files['staff-pwa-proto/index.html'], 'Prototype entry did not activate')
    data, _ = fetch(origin + PREFIX + 'deployment-route-check')
    require(sha(data) == files['staff-pwa-proto/index.html'], 'Prototype SPA fallback broken')
    fetch(origin + PREFIX + 'assets/nonexistent-deployment-check.js', 404)
    for path in ('/', '/staff/', '/staff/sw.js', '/staff/manifest.webmanifest'):
        fetch(origin + path)
    availability, _ = fetch(origin + '/api/restaurant/availability')
    require(isinstance(json.loads(availability).get('timezone'), str), 'Backend availability response invalid')
    health, _ = fetch('http://127.0.0.1:8081/actuator/health')
    require(json.loads(health).get('status') == 'UP', 'Backend health is not UP')


def protected_state():
    website = Path('/var/www/nakorn-thai/current').resolve(strict=True)
    backend = Path('/opt/nakorn-thai/backend/current').resolve(strict=True)
    paths = ('index.html', 'staff/index.html', 'staff/sw.js', 'staff/manifest.webmanifest')
    return {
        'website': str(website), 'backend': str(backend),
        'files': {name: sha((website / name).read_bytes()) for name in paths},
        'nginx': sha(Path('/etc/nginx/sites-available/nakorn-thai').read_bytes()),
        'snippet': sha(Path('/etc/nginx/snippets/nakorn-staff-pwa-proto.conf').read_bytes()),
        'backendProcess': subprocess.check_output(['systemctl', 'show', 'nakorn-thai-backend', '--property=MainPID,ExecMainStartTimestamp'], text=True),
    }


def atomic_activate(base, destination, suffix):
    temporary = base / ('current-' + suffix)
    require(not temporary.exists() and not temporary.is_symlink(), 'Activation temporary path already exists')
    temporary.symlink_to(destination)
    try:
        os.replace(temporary, base / 'current')
    finally:
        temporary.unlink(missing_ok=True)


def activate(base, release, commit, check=smoke, capture=protected_state):
    require(RELEASE.fullmatch(release), 'Invalid release identifier')
    require(release.startswith(commit + '-'), 'Release/revision mismatch')
    with (base / '.deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        destination = base / 'releases' / release
        validate(destination, commit, verify_manifest=True)
        current = base / 'current'
        require(current.is_symlink(), 'Existing prototype current symlink is required')
        previous = current.resolve(strict=True)
        require(previous.is_relative_to((base / 'releases').resolve()), 'Previous release is outside prototype release tree')
        state = capture()
        record = {'commit': commit, 'release': release, 'previous': str(previous), 'protected': state, 'status': 'prepared'}
        report = base / 'backups' / (release + '.json')
        report.parent.mkdir(exist_ok=True)
        require(not report.exists(), 'Activation already attempted for this release; use a new run attempt')
        report.write_text(json.dumps(record, indent=2) + '\n')
        switched = False
        try:
            atomic_activate(base, destination, release)
            switched = True
            check(destination)
            require(capture() == state, 'Protected deployment state changed during prototype activation')
            record['status'] = 'active'
        except Exception as error:
            record['error'] = str(error)
            if switched:
                atomic_activate(base, previous, 'rollback-' + release)
                record['status'] = 'rolled_back'
                try:
                    check(previous)
                    require(capture() == state, 'Protected state differs after rollback')
                except Exception as rollback_error:
                    record['rollbackVerificationError'] = str(rollback_error)
            else:
                record['status'] = 'activation_failed'
            report.write_text(json.dumps(record, indent=2) + '\n')
            raise
        report.write_text(json.dumps(record, indent=2) + '\n')
        print(json.dumps({'commit': commit, 'release': str(destination), 'previous': str(previous), 'status': record['status'], 'url': ORIGIN + PREFIX}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('validate', 'manifest', 'prepare', 'activate', 'smoke'))
    parser.add_argument('target')
    parser.add_argument('--commit', default='')
    args = parser.parse_args()
    if args.operation in ('prepare', 'activate'):
        require(RELEASE.fullmatch(args.target), 'Invalid release identifier')
        require(re.fullmatch(r'[0-9a-f]{40}', args.commit), 'Invalid commit SHA')
        require(args.target.startswith(args.commit + '-'), 'Release/revision mismatch')
        if args.operation == 'prepare':
            require((BASE / 'current').is_symlink(), 'Prototype deployment must already be configured')
            (BASE / 'releases' / args.target).mkdir(exist_ok=False)
            print('Prepared prototype release ' + args.target)
        else:
            activate(BASE, args.target, args.commit)
    else:
        root = Path(args.target)
        files = validate(root, args.commit, verify_manifest=args.operation == 'validate' and bool(args.commit), allow_legacy_config=args.operation == 'smoke')
        if args.operation == 'manifest':
            require(re.fullmatch(r'[0-9a-f]{40}', args.commit), 'Invalid commit SHA')
            (root / 'release-manifest.json').write_text(json.dumps({'commit': args.commit, 'files': files}, indent=2) + '\n')
        elif args.operation == 'smoke':
            smoke(root)
        print('Validated ' + str(len(files)) + ' prototype files')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Prototype deployment failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
