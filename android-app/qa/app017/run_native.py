"""APP-017 synthetic native suites only. Never runs live-receipt or UI tests."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = Path(__file__).resolve().parent
CACHE = Path('C:/Users/04068/.cache/ladno-android')
SERIAL, AVD = 'emulator-5580', 'Ladno_QA_API30'
SUITES = {
    'com.ladno.finance.OfdIndependentRuntimeTest': 'app017-ofd-independent',
    'com.ladno.finance.QrImportRuntimeTest': 'app013-qr-runtime',
    'com.ladno.finance.OfdReplayRuntimeTest': 'app017-ofd-replay',
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public-tls', action='store_true', help='Explicit provider TLS-only positive/wrong-peer test; no HTTP')
    args = parser.parse_args()
    toolchain = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    adb = str(Path(toolchain['sdkRoot']) / 'platform-tools/adb.exe')
    evidence = {'startedAtUtc': datetime.now(timezone.utc).isoformat(), 'serial': SERIAL, 'avd': AVD,
                'scope': 'explicit synthetic native API suites; injected transport; no live receipt IDs or UI',
                'commands': [], 'uiExercised': False, 'liveHttpExercised': False,
                'publicTlsOnlyRequested': args.public_tls}

    def run(args, timeout=30, binary=False):
        result = subprocess.run([adb, '-s', SERIAL, *args], capture_output=True, timeout=timeout)
        stdout = result.stdout.decode('utf-8', errors='replace')
        stderr = result.stderr.decode('utf-8', errors='replace')
        evidence['commands'].append({'args': args, 'exitCode': result.returncode, 'stdout': stdout, 'stderr': stderr})
        if result.returncode:
            raise RuntimeError('Native command failed: ' + ' '.join(args))
        return result.stdout if binary else stdout

    try:
        # FIRST adb command is the exact synthetic-AVD guard, before package reads or instrumentation.
        identity = run(['emu', 'avd', 'name'])
        if not identity.splitlines() or identity.splitlines()[0].strip() != AVD:
            raise RuntimeError('Refusing non-dedicated AVD')
        build = json.loads((ROOT / 'android-app/dist/build-result.json').read_text(encoding='utf-8'))
        manifest = json.loads((ROOT / 'android-app/dist/build-source-manifest.json').read_text(encoding='utf-8'))
        if build['versionName'] != '0.4.0' or build['versionCode'] != 4:
            raise RuntimeError('Expected version 0.4.0/code4')
        if build['sourceFingerprint'] != manifest['sourceFingerprint']:
            raise RuntimeError('Build/source fingerprint mismatch')
        main_apk = ROOT / 'android-app/dist' / build['apk']
        stage = Path(build['stage']).resolve()
        if not stage.is_relative_to((CACHE / 'work').resolve()):
            raise RuntimeError('Unexpected staging directory')
        if sha(main_apk) != build['sha256']:
            raise RuntimeError('Main APK differs from build metadata')
        for item in manifest['files']:
            source = (ROOT / 'android-app' / item['path']).resolve()
            if not source.is_relative_to((ROOT / 'android-app').resolve()) or sha(source) != item['sha256']:
                raise RuntimeError('Source changed after build')
        evidence['sourceFilesVerified'] = len(manifest['files'])
        test_apk = stage / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        evidence['build'] = build
        evidence['testApkSha256'] = sha(test_apk)
        source_names = ['OfdIndependentRuntimeTest.java', 'QrImportRuntimeTest.java', 'OfdReplayRuntimeTest.java']
        if args.public_tls:
            source_names.append('OfdTlsRuntimeTest.java')
        for name in source_names:
            relative = Path('app/src/androidTest/java/com/ladno/finance') / name
            if sha(stage / relative) != sha(ROOT / 'android-app' / relative):
                raise RuntimeError('Native test source differs from staged bytes')
        webview_source = (stage / 'app/src/main/java/com/ladno/finance/MainActivity.java').read_text(encoding='utf-8')
        if 'setBlockNetworkLoads(true)' not in webview_source:
            raise RuntimeError('Expected unchanged WebView network block source')
        evidence['webViewNetworkBlockSourcePresent'] = True
        for package, expected in [('com.ladno.finance', build['sha256']), ('com.ladno.finance.test', evidence['testApkSha256'])]:
            location = run(['shell', 'pm', 'path', package]).strip().splitlines()
            if len(location) != 1 or not location[0].startswith('package:/data/app/'):
                raise RuntimeError('Unexpected installed package location')
            actual = run(['shell', 'sha256sum', location[0][8:]]).split()[0].lower()
            if actual != expected.lower():
                raise RuntimeError('Installed APK differs from built bytes')
        stdout = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', ','.join(SUITES),
                      '-e', 'ladnoSyntheticAvd', AVD,
                      'com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner'], timeout=420)
        native_ok = 'OK (3 tests)' in stdout and 'FAILURES!!!' not in stdout and 'INSTRUMENTATION_FAILED' not in stdout
        outcomes = []
        for suite, filename in SUITES.items():
            raw = run(['exec-out', 'run-as', 'com.ladno.finance', 'cat',
                       'files/qa-android-scenario/' + filename + '.json'], binary=True)
            (OUTPUT / (filename + '.json')).write_bytes(raw)
            item = json.loads(raw)
            checks = item.get('checks', [])
            outcomes.append({'suite': suite, 'checks': len(checks), 'failure': item.get('failure'),
                             'failedChecks': [check for check in checks if not check.get('pass')]})
        evidence['suites'] = outcomes
        evidence['passed'] = bool(native_ok and all(item['checks'] and not item['failedChecks'] and not item['failure'] for item in outcomes))
        if not evidence['passed']:
            raise RuntimeError('Synthetic native suites or recorded assertions failed')
        if args.public_tls:
            # Separate, explicit public handshake-only target: no fiscal request,
            # no QR identifiers, no HTTP and never an unrestricted all-suite run.
            tls_stdout = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                              'com.ladno.finance.OfdTlsRuntimeTest', '-e', 'ladnoSyntheticAvd', AVD,
                              '-e', 'ladnoPublicOfdTls', 'true',
                              'com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner'], timeout=90)
            evidence['publicTlsOnlyPassed'] = ('OK (1 test)' in tls_stdout and 'FAILURES!!!' not in tls_stdout
                                               and 'INSTRUMENTATION_FAILED' not in tls_stdout)
            if not evidence['publicTlsOnlyPassed']:
                raise RuntimeError('Public TLS-only policy check failed')
        print(json.dumps({'passed': True, 'checks': sum(item['checks'] for item in outcomes),
                          'suites': len(outcomes), 'publicTlsOnlyPassed': evidence.get('publicTlsOnlyPassed'),
                          'mainApkSha256': build['sha256'], 'testApkSha256': evidence['testApkSha256']}))
    except Exception as error:
        evidence['passed'] = False
        evidence['error'] = str(error)
        raise
    finally:
        evidence['finishedAtUtc'] = datetime.now(timezone.utc).isoformat()
        OUTPUT.mkdir(parents=True, exist_ok=True)
        (OUTPUT / 'native-run.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
