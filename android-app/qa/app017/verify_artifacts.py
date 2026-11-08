"""One bounded final artifact/source/signature/manifest check for APP-017."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
CACHE = Path('C:/Users/04068/.cache/ladno-android')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-main-sha', required=True)
    parser.add_argument('--expected-source-fingerprint', required=True)
    args = parser.parse_args()
    if not all(re.fullmatch('[0-9a-fA-F]{64}', value) for value in [args.expected_main_sha, args.expected_source_fingerprint]):
        raise RuntimeError('Expected exact SHA256 inputs')
    state = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    dist = ROOT / 'android-app/dist'
    build = json.loads((dist / 'build-result.json').read_text(encoding='utf-8'))
    manifest = json.loads((dist / 'build-source-manifest.json').read_text(encoding='utf-8'))
    current = dist / build['apk']
    archived = ROOT / 'team/snapshots/android-alpha-v4-files/android-app/dist/ladno-0.3.0-debug.apk'
    evidence = {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'checks': [], 'commands': []}

    def check(name, expected, actual):
        passed = expected == actual
        evidence['checks'].append({'id': name, 'expected': expected, 'actual': actual, 'pass': passed})
        if not passed:
            raise AssertionError(name)

    env = dict(os.environ)
    env['JAVA_HOME'] = state['javaHome']
    env['PATH'] = str(Path(state['javaHome']) / 'bin') + os.pathsep + env.get('PATH', '')
    tools = Path(state['sdkRoot']) / 'build-tools/35.0.0'

    def run(command):
        result = subprocess.run([str(item) for item in command], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', env=env, timeout=60)
        row = {'args': [str(item) for item in command], 'exitCode': result.returncode,
               'stdout': result.stdout, 'stderr': result.stderr}
        if result.returncode:
            raise RuntimeError('Artifact verification tool failed')
        return row

    try:
        check('main APK actual SHA', args.expected_main_sha.lower(), sha(current))
        check('metadata SHA', args.expected_main_sha.lower(), build['sha256'])
        check('metadata byte size', build['bytes'], current.stat().st_size)
        check('version name', '0.4.0', build['versionName'])
        check('version code', 4, build['versionCode'])
        check('source fingerprint', args.expected_source_fingerprint.lower(), build['sourceFingerprint'])
        check('source manifest fingerprint', build['sourceFingerprint'], manifest['sourceFingerprint'])
        for item in manifest['files']:
            path = (ROOT / 'android-app' / item['path']).resolve()
            if not path.is_relative_to((ROOT / 'android-app').resolve()) or '.local' in path.parts:
                raise RuntimeError('Unsafe or private source included in manifest')
            check('source SHA: ' + item['path'], item['sha256'].lower(), sha(path))
        evidence['sourceFilesVerified'] = len(manifest['files'])
        with ThreadPoolExecutor(max_workers=3) as pool:
            pending = [pool.submit(run, [tools / 'apksigner.bat', 'verify', '--print-certs', current]),
                       pool.submit(run, [tools / 'apksigner.bat', 'verify', '--print-certs', archived]),
                       pool.submit(run, [tools / 'aapt2.exe', 'dump', 'badging', current])]
            results = [task.result() for task in pending]
        evidence['commands'] = results
        certs = []
        for result in results[:2]:
            match = re.search(r'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)', result['stdout'])
            if not match:
                raise AssertionError('Certificate SHA absent')
            certs.append(match.group(1).lower())
        check('same certificate as archived 0.3.0', certs[1], certs[0])
        badging = results[2]['stdout']
        package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        if not package:
            raise AssertionError('APK package metadata absent')
        check('APK package', 'com.ladno.finance', package.group(1))
        check('APK version code', '4', package.group(2))
        check('APK version name', '0.4.0', package.group(3))
        minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'$", badging, re.MULTILINE)
        check('APK minimum SDK', '26', minimum.group(1) if minimum else None)
        check('APK target SDK', True, "targetSdkVersion:'36'" in badging)
        check('native OFD INTERNET permission', True, "name='android.permission.INTERNET'" in badging)
        with zipfile.ZipFile(current) as apk:
            dex = b''.join(apk.read(name) for name in apk.namelist() if re.fullmatch(r'classes[0-9]*\.dex', name))
            for needle in [b'OfdReceiptClient', b'OfdReceiptParser', b'OfdJson', b'OfdTls', b'https://consumer.oofd.kz/api/consumer-proxy/api/tickets/get-by-url']:
                check('OFD class or fixed endpoint packaged: ' + needle.decode('ascii'), True, needle in dex)
            check('live instrumentation fixture absent from main APK', False, b'OfdAuthorizedLiveRuntimeTest' in dex)
            check('private local folder absent from APK paths', False, any('.local' in name.split('/') for name in apk.namelist()))
            # Bounded exact-marker check, not a broad private-data dump/audit.
            # Approved own QR bytes remain in memory and never enter output/evidence.
            decoded = json.loads((ROOT / 'android-app/.local/app017/decoded-receipts.json').read_text(encoding='utf-8'))
            raw_qr = [item['payloads'][0].encode('utf-8') for item in decoded
                      if item.get('sample') in [4, 5] and len(item.get('payloads', [])) == 1]
            check('two approved private QR markers available', 2, len(raw_qr))
            app_text = dex + b''.join(apk.read(name) for name in apk.namelist()
                                     if name.startswith('assets/www/') and name.endswith(('.js', '.html', '.css', '.json')))
            check('approved private raw QR absent from packaged code/UI', True, all(value not in app_text for value in raw_qr))
        evidence['passed'] = True
        print(json.dumps({'passed': True, 'checks': len(evidence['checks']),
                          'sourceFilesVerified': len(manifest['files']), 'mainApkSha256': sha(current),
                          'certificateSha256': certs[0]}))
    except Exception as error:
        evidence['passed'] = False
        evidence['error'] = str(error)
        raise
    finally:
        (HERE / 'artifact-identity.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
