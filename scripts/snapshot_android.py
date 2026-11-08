"""Freeze APP-005 source, APK and actual review evidence without copying private keys."""
from datetime import datetime, timezone
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive-v1', action='store_true')
    parser.add_argument('--version', choices=['android-alpha-v1', 'android-alpha-v2', 'android-alpha-v3'], default='android-alpha-v1')
    args = parser.parse_args()
    if args.archive_v1:
        source = ROOT / 'team/snapshots/android-alpha-v1.json'
        original = json.loads(source.read_text(encoding='utf-8'))
        archive = ROOT / 'team/snapshots/android-alpha-v1-files'
        archive.mkdir(exist_ok=True)
        for item in original['files']:
            current = ROOT / item['path']
            target = (archive / item['path']).resolve()
            if not target.is_relative_to(archive.resolve()) or digest(current) != item['sha256'].lower():
                raise RuntimeError('Archive source/path mismatch: ' + item['path'])
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists() and digest(target) != item['sha256'].lower():
                raise RuntimeError('Existing archive differs: ' + item['path'])
            if not target.exists():
                shutil.copy2(current, target)
            if digest(target) != item['sha256'].lower():
                raise RuntimeError('Archive copy mismatch: ' + item['path'])
        shutil.copy2(source, archive / 'snapshot.json')
        print(json.dumps({'archived': len(original['files']), 'manifestSha256': original['manifestSha256']}))
        return
    old = json.loads((ROOT / 'team/snapshots/local-prototype-v1.json').read_text(encoding='utf-8'))
    for item in old['files']:
        if digest(ROOT / item['path']) != item['sha256'].lower():
            raise RuntimeError('Protected previous deliverable changed: ' + item['path'])
    build = json.loads((ROOT / 'android-app/dist/build-result.json').read_text(encoding='utf-8'))
    if args.version in {'android-alpha-v2', 'android-alpha-v3'}:
        parent_version = 'android-alpha-v2' if args.version == 'android-alpha-v3' else 'android-alpha-v1'
        archive = ROOT / ('team/snapshots/' + parent_version + '-files')
        previous = json.loads((archive / 'snapshot.json').read_text(encoding='utf-8'))
        for item in previous['files']:
            if digest(archive / item['path']) != item['sha256'].lower():
                raise RuntimeError('Historical archive mismatch: ' + item['path'])
    sources = json.loads((ROOT / 'android-app/dist/build-source-manifest.json').read_text(encoding='utf-8'))
    if build['sourceFingerprint'] != sources['sourceFingerprint']:
        raise RuntimeError('Build/source manifest mismatch')
    paths = set()
    for item in sources['files']:
        path = ROOT / 'android-app' / item['path']
        if digest(path) != item['sha256'].lower():
            raise RuntimeError('Source differs from tested build: ' + item['path'])
        paths.add(path)
    apk = ROOT / 'android-app/dist' / build['apk']
    if digest(apk) != build['sha256']:
        raise RuntimeError('APK hash mismatch')
    for pattern in ['docs/android/*', 'docs/product/PERSONAL-MVP.md',
                    'docs/product/AUTO-ENTRY-ACCEPTANCE.md', 'team/reports/APP-005-*.md', 'team/reports/APP-006-*.md', 'team/reports/APP-008-*.md',
                    'android-app/qa/**/*', 'android-app/dist/*',
                    'android-app/dist/test-results/**/*.xml', 'scripts/android_runtime_tools.py',
                    'scripts/run_android_runtime.py', 'scripts/snapshot_android.py']:
        for path in ROOT.glob(pattern):
            if path.is_file() and not {'classes', '__pycache__'}.intersection(path.parts) and path.suffix not in {'.sqlite', '.db'}:
                paths.add(path)
    files = [{'path': path.relative_to(ROOT).as_posix(), 'sha256': digest(path), 'bytes': path.stat().st_size}
             for path in sorted(paths, key=lambda p: p.relative_to(ROOT).as_posix())]
    canonical = ''.join(item['sha256'] + '  ' + item['path'] + '\n' for item in files)
    snapshot = {'version': args.version, 'createdAtUtc': datetime.now(timezone.utc).isoformat(),
                'scope': ('APP-008 receipt extraction/outcomes, starter categories migration, inline category and expense charts; per-version QA reports define executed checks; original user receipt and all-bank corpus unverified' if args.version == 'android-alpha-v3' else 'APP-005/006 installable alpha; Android11 native SQLite/KeyStore/NLS/Tesseract API evidence; MainActivity/camera/gallery UI and real-bank/photo corpus explicitly unverified' if args.version == 'android-alpha-v2' else 'APP-005 installable alpha; device/OCR/NLS end-to-end execution remains explicitly unverified'),
                'previousProtectedSnapshot': old['manifestSha256'], 'previousProtectedFilesUnchanged': len(old['files']),
                'apkSha256': build['sha256'], 'sourceFingerprint': build['sourceFingerprint'],
                'canonicalization': 'SHA-256 two-spaces POSIX-path LF, sorted paths, exact bytes',
                'manifestSha256': hashlib.sha256(canonical.encode()).hexdigest(), 'files': files}
    if args.version in {'android-alpha-v2', 'android-alpha-v3'}:
        snapshot['parent'] = parent_version
        snapshot['parentManifestSha256'] = previous['manifestSha256']
        snapshot['parentArchive'] = 'team/snapshots/' + parent_version + '-files'
        snapshot['parentArchiveFilesVerified'] = len(previous['files'])
    target = ROOT / ('team/snapshots/' + args.version + '.json')
    if target.exists():
        raise RuntimeError('Refusing to overwrite an existing frozen snapshot: ' + str(target))
    target.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'files': len(files), 'manifestSha256': snapshot['manifestSha256'],
                      'apkSha256': build['sha256'], 'oldProtectedFilesUnchanged': len(old['files'])}))


if __name__ == '__main__':
    main()
