"""Run APP-013 native API checks on the named synthetic AVD only; no UI actions."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = Path(__file__).resolve().parent
CACHE = Path('C:/Users/04068/.cache/ladno-android')
SERIAL = 'emulator-5580'
AVD = 'Ladno_QA_API30'
CLASS = 'com.ladno.finance.QrImportRuntimeTest'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    toolchain = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    adb = str(Path(toolchain['sdkRoot']) / 'platform-tools/adb.exe')
    evidence = {'startedAtUtc': datetime.now(timezone.utc).isoformat(), 'scope': 'synthetic native API only',
                'serial': SERIAL, 'avd': AVD, 'commands': [], 'uiExercised': False}

    def run(args, timeout=30, binary=False):
        command = [adb, '-s', SERIAL, *args]
        result = subprocess.run(command, capture_output=True, timeout=timeout)
        stdout = result.stdout.decode('utf-8', errors='replace')
        stderr = result.stderr.decode('utf-8', errors='replace')
        evidence['commands'].append({'args': args, 'exitCode': result.returncode, 'stdout': stdout, 'stderr': stderr})
        if result.returncode:
            raise RuntimeError('Native command failed: ' + ' '.join(args))
        return result.stdout if binary else stdout

    try:
        # This MUST be the first command: do not query packages or execute tests before identity is established.
        identity = run(['emu', 'avd', 'name'])
        if not identity.splitlines() or identity.splitlines()[0].strip() != AVD:
            raise RuntimeError('Refusing non-dedicated AVD')
        build = json.loads((ROOT / 'android-app/dist/build-result.json').read_text(encoding='utf-8'))
        if build['versionName'] != '0.3.0' or build['versionCode'] != 3:
            raise RuntimeError('Unexpected build version')
        main_apk = ROOT / 'android-app/dist' / build['apk']
        stage = Path(build['stage']).resolve()
        if not stage.is_relative_to((CACHE / 'work').resolve()):
            raise RuntimeError('Unexpected staging path')
        test_apk = stage / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        if sha(main_apk) != build['sha256']:
            raise RuntimeError('Main APK differs from build metadata')
        test_source = Path('app/src/androidTest/java/com/ladno/finance/QrImportRuntimeTest.java')
        if sha(stage / test_source) != sha(ROOT / 'android-app' / test_source):
            raise RuntimeError('Native test source changed after staging')
        evidence['build'] = build
        evidence['testApkSha256'] = sha(test_apk)
        evidence['testSourceSha256'] = sha(stage / test_source)
        for package, expected in [('com.ladno.finance', build['sha256']),
                                  ('com.ladno.finance.test', evidence['testApkSha256'])]:
            location = run(['shell', 'pm', 'path', package]).strip().splitlines()
            if len(location) != 1 or not location[0].startswith('package:/data/app/'):
                raise RuntimeError('Unexpected installed package location')
            actual = run(['shell', 'sha256sum', location[0][8:]]).split()[0].lower()
            if actual != expected.lower():
                raise RuntimeError('Installed APK differs from reviewed local bytes: ' + package)
        stdout = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', CLASS,
                      '-e', 'ladnoSyntheticAvd', AVD,
                      'com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner'], timeout=240)
        passed = 'OK (1 test)' in stdout and 'FAILURES!!!' not in stdout and 'INSTRUMENTATION_FAILED' not in stdout
        raw = run(['exec-out', 'run-as', 'com.ladno.finance', 'cat',
                   'files/qa-android-scenario/app013-qr-runtime.json'], binary=True)
        (OUTPUT / 'native-runtime-evidence.json').write_bytes(raw)
        runtime = json.loads(raw)
        checks = runtime.get('checks', [])
        evidence['runtimeChecks'] = len(checks)
        evidence['runtimeFailures'] = [item for item in checks if not item.get('pass')]
        evidence['passed'] = bool(passed and checks and not evidence['runtimeFailures'] and 'failure' not in runtime)
        if not evidence['passed']:
            raise RuntimeError('Native suite or recorded assertions failed')
        print(json.dumps({'passed': True, 'checks': len(checks), 'apkSha256': build['sha256'],
                          'testApkSha256': evidence['testApkSha256']}))
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
