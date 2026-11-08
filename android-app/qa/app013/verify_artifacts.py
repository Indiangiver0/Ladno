"""Bounded identity checks for the final APP-013/014 artifacts, not a broad audit."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
CACHE = Path('C:/Users/04068/.cache/ladno-android')
EXPECTED_MAIN = 'a8ec70cd97674fb6f9c8d224e10139611c52a4ac25f69f6cd26143f7d88e5b76'
EXPECTED_SOURCE = '85db77f1cbb5412032dfdc869c4c14faadc07def9992ab620d9a6e05ead03284'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    state = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    dist = ROOT / 'android-app/dist'
    build = json.loads((dist / 'build-result.json').read_text(encoding='utf-8'))
    sources = json.loads((dist / 'build-source-manifest.json').read_text(encoding='utf-8'))
    current = dist / build['apk']
    previous = ROOT / 'team/snapshots/android-alpha-v3-files/android-app/dist/ladno-0.2.0-debug.apk'
    evidence = {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'checks': [], 'commands': []}

    def check(label, expected, actual):
        passed = expected == actual
        evidence['checks'].append({'id': label, 'expected': expected, 'actual': actual, 'pass': passed})
        if not passed:
            raise AssertionError(label)

    env = dict(os.environ)
    env['JAVA_HOME'] = state['javaHome']
    env['PATH'] = str(Path(state['javaHome']) / 'bin') + os.pathsep + env.get('PATH', '')
    tools = Path(state['sdkRoot']) / 'build-tools/35.0.0'

    def run(args):
        result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', env=env, timeout=60)
        row = {'args': [str(arg) for arg in args], 'exitCode': result.returncode,
               'stdout': result.stdout, 'stderr': result.stderr}
        if result.returncode:
            raise RuntimeError('Artifact tool failed: ' + str(args[0]))
        return row

    try:
        check('main APK bytes', 68393641, current.stat().st_size)
        check('main APK actual SHA', EXPECTED_MAIN, sha(current))
        check('metadata SHA', EXPECTED_MAIN, build['sha256'])
        check('metadata versionName', '0.3.0', build['versionName'])
        check('metadata versionCode', 3, build['versionCode'])
        check('source fingerprint', EXPECTED_SOURCE, build['sourceFingerprint'])
        check('source manifest fingerprint', EXPECTED_SOURCE, sources['sourceFingerprint'])
        for item in sources['files']:
            path = (ROOT / 'android-app' / item['path']).resolve()
            if not path.is_relative_to((ROOT / 'android-app').resolve()):
                raise RuntimeError('Unsafe source manifest path')
            check('source bytes: ' + item['path'], item['sha256'].lower(), sha(path))
        evidence['sourceFilesVerified'] = len(sources['files'])
        with ThreadPoolExecutor(max_workers=3) as pool:
            futures = [pool.submit(run, [tools / 'apksigner.bat', 'verify', '--print-certs', current]),
                       pool.submit(run, [tools / 'apksigner.bat', 'verify', '--print-certs', previous]),
                       pool.submit(run, [tools / 'aapt2.exe', 'dump', 'badging', current])]
            rows = [future.result() for future in futures]
        evidence['commands'] = rows
        certs = []
        for row in rows[:2]:
            match = re.search(r'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)', row['stdout'])
            if not match:
                raise AssertionError('Certificate SHA missing')
            certs.append(match.group(1).lower())
        check('same signing certificate as archived 0.2.0', certs[1], certs[0])
        badging = rows[2]['stdout']
        package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        if not package:
            raise AssertionError('APK package metadata missing')
        check('APK package', 'com.ladno.finance', package.group(1))
        check('APK versionCode', '3', package.group(2))
        check('APK versionName', '0.3.0', package.group(3))
        minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'$", badging, re.MULTILINE)
        check('APK minimum SDK', '26', minimum.group(1) if minimum else None)
        check('APK target SDK', True, "targetSdkVersion:'36'" in badging)
        check('APK has no INTERNET permission', False, "name='android.permission.INTERNET'" in badging)
        evidence['passed'] = True
        print(json.dumps({'passed': True, 'checks': len(evidence['checks']),
                          'sourceFilesVerified': len(sources['files']), 'mainSha256': EXPECTED_MAIN,
                          'certificateSha256': certs[0]}))
    except Exception as error:
        evidence['passed'] = False
        evidence['error'] = str(error)
        raise
    finally:
        (HERE / 'artifact-identity.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
