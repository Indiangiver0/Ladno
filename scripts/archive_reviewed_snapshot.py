"""Preserve the exact files of an existing review before starting a new version."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot')
    args = parser.parse_args()
    source = (ROOT / args.snapshot).resolve()
    if not source.is_relative_to((ROOT / 'team/snapshots').resolve()):
        raise RuntimeError('Snapshot must be under team/snapshots')
    manifest = json.loads(source.read_text(encoding='utf-8'))
    archive = source.with_name(source.stem + '-files')
    entries = []
    for item in manifest['files']:
        current = (ROOT / item['path']).resolve()
        target = (archive / item['path']).resolve()
        if not current.is_relative_to(ROOT) or not target.is_relative_to(archive):
            raise RuntimeError('Unsafe path: ' + item['path'])
        if sha(current) != item['sha256'].lower():
            raise RuntimeError('Current bytes differ from reviewed version: ' + item['path'])
        if target.exists() and sha(target) != item['sha256'].lower():
            raise RuntimeError('Existing archive differs: ' + item['path'])
        entries.append((current, target, item))
    archive.mkdir(exist_ok=True)
    for current, target, item in entries:
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copy2(current, target)
        if sha(target) != item['sha256'].lower():
            raise RuntimeError('Archive copy differs: ' + item['path'])
    manifest_copy = archive / 'snapshot.json'
    if manifest_copy.exists() and sha(manifest_copy) != sha(source):
        raise RuntimeError('Existing archived snapshot differs')
    if not manifest_copy.exists():
        shutil.copy2(source, manifest_copy)
    print(json.dumps({'archivedFiles': len(entries), 'manifestSha256': manifest['manifestSha256'],
                      'archive': archive.relative_to(ROOT).as_posix(), 'allCopiesVerified': True}))


if __name__ == '__main__':
    main()
