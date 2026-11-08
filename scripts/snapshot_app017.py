"""Freeze the exact new feature version and evidence for independent review."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    target = ROOT / 'team/snapshots/android-alpha-v5.json'
    if target.exists():
        raise RuntimeError('Refusing to overwrite frozen snapshot')
    parent_dir = ROOT / 'team/snapshots/android-alpha-v4-files'
    parent = json.loads((parent_dir / 'snapshot.json').read_text(encoding='utf-8'))
    for item in parent['files']:
        if sha(parent_dir / item['path']) != item['sha256'].lower():
            raise RuntimeError('Historical archive mismatch: ' + item['path'])
    original = json.loads((ROOT / 'team/snapshots/local-prototype-v1.json').read_text(encoding='utf-8'))
    for item in original['files']:
        if sha(ROOT / item['path']) != item['sha256'].lower():
            raise RuntimeError('Protected prototype changed: ' + item['path'])
    dist = ROOT / 'android-app/dist'
    build = json.loads((dist / 'build-result.json').read_text(encoding='utf-8'))
    sources = json.loads((dist / 'build-source-manifest.json').read_text(encoding='utf-8'))
    if build['sourceFingerprint'] != sources['sourceFingerprint']:
        raise RuntimeError('Build/source mismatch')
    paths = set()
    for item in sources['files']:
        path = ROOT / 'android-app' / item['path']
        if sha(path) != item['sha256'].lower():
            raise RuntimeError('Source differs from built version: ' + item['path'])
        paths.add(path)
    apk = dist / build['apk']
    if sha(apk) != build['sha256']:
        raise RuntimeError('APK mismatch')
    paths.update([apk, dist / 'build-result.json', dist / 'build-source-manifest.json'])
    native = json.loads((ROOT / 'android-app/qa/app017/native-run.json').read_text(encoding='utf-8'))
    test_apk = dist / build['apk'].replace('-debug.apk', '-androidTest.apk')
    if not native['passed'] or sha(test_apk) != native['testApkSha256']:
        raise RuntimeError('Native proof/test APK mismatch')
    live = json.loads((ROOT / 'android-app/qa/app017/authorized-live-evidence.json').read_text(encoding='utf-8'))
    if not live.get('passed') or not live.get('nativeDefaultClient') or not live.get('noLedgerWrite') or not live.get('privateInputRemoved'):
        raise RuntimeError('Default live receipt proof incomplete')
    paths.add(test_apk)
    for pattern in ['docs/android/APP-017-*.md', 'team/reports/APP-017-*.md',
                    'team/reports/APP-017-*.log', 'team/reports/APP-017-*.xml', 'team/reports/APP-017-*.json',
                    'team/reports/APP-014-*.md', 'android-app/qa/app017/**/*',
                    'android-app/dist/test-results/testDebugUnitTest/TEST-*.xml',
                    'android-app/dist/reports/lint-results-debug.xml',
                    'scripts/archive_reviewed_snapshot.py', 'scripts/snapshot_app017.py']:
        for path in ROOT.glob(pattern):
            if path.is_file() and '__pycache__' not in path.parts:
                paths.add(path)
    files = [{'path': path.relative_to(ROOT).as_posix(), 'sha256': sha(path), 'bytes': path.stat().st_size}
             for path in sorted(paths, key=lambda p: p.relative_to(ROOT).as_posix())]
    canonical = ''.join(item['sha256'] + '  ' + item['path'] + '\n' for item in files)
    manifest = {'version': 'android-alpha-v5', 'createdAtUtc': datetime.now(timezone.utc).isoformat(),
                'scope': 'APP-017 native Kazakhtelecom receipt API with exact monetary parser, stable receipt replay and default-policy TLS chain completion; real samples 4/5 passed; samples 1/2 unreadable and camera UI unverified',
                'apkSha256': build['sha256'], 'sourceFingerprint': build['sourceFingerprint'],
                'parent': parent['version'], 'parentManifestSha256': parent['manifestSha256'],
                'parentArchive': parent_dir.relative_to(ROOT).as_posix(),
                'parentArchiveFilesVerified': len(parent['files']),
                'previousProtectedFilesUnchanged': len(original['files']),
                'canonicalization': 'SHA-256 two-spaces POSIX-path LF, sorted paths, exact bytes',
                'manifestSha256': hashlib.sha256(canonical.encode()).hexdigest(), 'files': files}
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: manifest[key] for key in ['version', 'manifestSha256', 'apkSha256',
                     'sourceFingerprint', 'parentArchiveFilesVerified', 'previousProtectedFilesUnchanged']}))


if __name__ == '__main__':
    main()
