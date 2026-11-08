"""Run native API tests only on the dedicated synthetic Ladno AVD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ROOT = Path(__file__).resolve().parents[1]
CACHE = Path('C:/Users/04068/.cache/ladno-android')
SERIAL = 'emulator-5580'
COMPONENT = 'com.ladno.finance/.BankNotificationService'
CLASSES = {'write': 'com.ladno.finance.storage.LedgerRuntimeWriteTest',
           'read': 'com.ladno.finance.storage.LedgerRuntimeReadTest',
           'notifications': 'com.ladno.finance.NotificationRuntimeTest',
           'ocr': 'com.ladno.finance.OcrRuntimeTest',
           'starter': 'com.ladno.finance.storage.StarterCategoryMigrationTest',
           'receipt-layout': 'com.ladno.finance.ReceiptLayoutRuntimeTest',
           'ocr-diagnostic': 'com.ladno.finance.AuthorOcrDiagnosticTest'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['install', 'clear', 'stop', 'grant-nls', 'revoke-nls', 'test', 'pull-control', 'pull', 'pull-scenario', 'diagnose', 'pull-db', 'logcat', 'shutdown'])
    parser.add_argument('--suite', choices=CLASSES)
    parser.add_argument('--method')
    parser.add_argument('--file')
    parser.add_argument('--pid', type=int)
    args = parser.parse_args()
    state = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    adb = Path(state['sdkRoot']) / 'platform-tools/adb.exe'
    env = dict(os.environ)
    env.update(ANDROID_USER_HOME=str(CACHE / 'android-user'), ANDROID_AVD_HOME=str(CACHE / 'avd'),
               ANDROID_EMULATOR_HOME=str(CACHE / 'emulator-user'))
    prefix = [str(adb), '-s', SERIAL]
    identity = subprocess.run(prefix + ['emu', 'avd', 'name'], env=env, capture_output=True, text=True, timeout=20)
    if identity.returncode or identity.stdout.splitlines()[0].strip() != 'Ladno_QA_API30':
        raise RuntimeError('Refusing action: not the dedicated synthetic AVD')
    evidence = ROOT / 'android-app/qa/runtime-host'
    evidence.mkdir(exist_ok=True)
    if args.action == 'pull-db':
        import sqlite3
        target_dir = evidence / ('db-snapshot-' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S'))
        target_dir.mkdir()
        raw_dir, work_dir = target_dir / 'raw', target_dir / 'working'
        raw_dir.mkdir()
        work_dir.mkdir()
        copied = []
        for name in ['ledger.db', 'ledger.db-wal', 'ledger.db-shm']:
            command = prefix + ['exec-out', 'run-as', 'com.ladno.finance', 'cat', 'databases/' + name]
            result = subprocess.run(command, env=env, capture_output=True, timeout=30)
            if result.returncode:
                if name == 'ledger.db':
                    raise RuntimeError('Cannot read synthetic app database')
                continue
            (raw_dir / name).write_bytes(result.stdout)
            shutil.copy2(raw_dir / name, work_dir / name)
            copied.append({'name': 'raw/' + name, 'bytes': len(result.stdout), 'sha256': hashlib.sha256(result.stdout).hexdigest()})
        preview = {}
        with sqlite3.connect(work_dir / 'ledger.db') as connection:
            connection.row_factory = sqlite3.Row
            tables = connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
            for table in tables:
                name = table['name']
                if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', name):
                    continue
                preview[name] = [dict(row) for row in connection.execute('SELECT * FROM "' + name + '" ORDER BY rowid DESC LIMIT 12')]
        (target_dir / 'preview.json').write_text(json.dumps(preview, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        (target_dir / 'files.json').write_text(json.dumps(copied, indent=2) + '\n', encoding='utf-8')
        print('Synthetic Android DB snapshot: ' + str(target_dir))
        return 0
    commands = []
    if args.action == 'install':
        build = json.loads((ROOT / 'android-app/dist/build-result.json').read_text(encoding='utf-8'))
        manifest = json.loads((ROOT / 'android-app/dist/build-source-manifest.json').read_text(encoding='utf-8'))
        if manifest['sourceFingerprint'] != build['sourceFingerprint']:
            raise RuntimeError('Refusing stale install: the new build is still running or did not pass')
        for item in manifest['files']:
            current_source = (ROOT / 'android-app' / item['path']).resolve()
            if not current_source.is_relative_to((ROOT / 'android-app').resolve()) or hashlib.sha256(current_source.read_bytes()).hexdigest() != item['sha256']:
                raise RuntimeError('Refusing stale install: source changed after the tested build: ' + item['path'])
        apk = ROOT / 'android-app/dist' / build['apk']
        if hashlib.sha256(apk.read_bytes()).hexdigest() != build['sha256']:
            raise RuntimeError('APK hash mismatch')
        stage = Path(build['stage'])
        if not stage.resolve().is_relative_to((CACHE / 'work').resolve()):
            raise RuntimeError('Unexpected test build stage')
        test_apk = stage / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        target = ROOT / 'android-app/dist' / build['apk'].replace('-debug.apk', '-androidTest.apk')
        shutil.copy2(test_apk, target)
        install_metadata = {'sourceFingerprint': build['sourceFingerprint'], 'stage': str(stage),
                            'apkSha256': build['sha256'], 'testApkSha256': hashlib.sha256(target.read_bytes()).hexdigest()}
        commands = [['install', '-r', '-t', str(apk)], ['install', '-r', '-t', str(target)]]
    elif args.action == 'clear':
        commands = [['shell', 'pm', 'clear', 'com.ladno.finance']]
    elif args.action == 'stop':
        commands = [['shell', 'am', 'force-stop', 'com.ladno.finance']]
    elif args.action == 'grant-nls':
        commands = [['shell', 'cmd', 'notification', 'allow_listener', COMPONENT]]
    elif args.action == 'revoke-nls':
        commands = [['shell', 'cmd', 'notification', 'disallow_listener', COMPONENT]]
    elif args.action == 'pull-control':
        commands = [['shell', 'run-as', 'com.ladno.finance', 'cat', 'files/qa-ledger-control.json']]
    elif args.action == 'pull':
        if not args.file or not re.fullmatch(r'qa-[A-Za-z0-9_-]+\.json', args.file):
            parser.error('A named qa-*.json private fixture is required')
        commands = [['shell', 'run-as', 'com.ladno.finance', 'cat', 'files/' + args.file]]
    elif args.action == 'pull-scenario':
        if args.file not in {'notification-native.json', 'notification-os-granted.json', 'notification-os-revoked.json', 'ocr-runtime.json'}:
            parser.error('Unknown scenario fixture file')
        commands = [['shell', 'run-as', 'com.ladno.finance', 'cat', 'files/qa-android-scenario/' + args.file]]
    elif args.action == 'diagnose':
        commands = [['shell', 'settings', 'get', 'secure', 'enabled_notification_listeners'],
                    ['shell', 'dumpsys', 'activity', 'services', 'com.ladno.finance'],
                    ['shell', 'dumpsys', 'notification']]
    elif args.action == 'logcat':
        if args.pid is None or not 1 <= args.pid <= 1000000:
            parser.error('A recorded synthetic target PID is required')
        commands = [['logcat', '-d', '--pid=' + str(args.pid), '-v', 'threadtime']]
    elif args.action == 'shutdown':
        commands = [['emu', 'kill']]
    else:
        if not args.suite:
            parser.error('--suite is required for test')
        selected = CLASSES[args.suite]
        if args.method:
            if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]{0,100}', args.method):
                parser.error('Invalid test method')
            selected += '#' + args.method
        commands = [['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selected,
                     'com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner']]
    results = []
    for arguments in commands:
        command = prefix + arguments
        print(subprocess.list2cmdline(command), flush=True)
        completed = subprocess.run(command, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=420)
        if args.action in {'pull', 'pull-control', 'pull-scenario'}:
            print('Read synthetic private fixture: ' + str(len(completed.stdout.encode('utf-8'))) + ' bytes', flush=True)
        else:
            print(completed.stdout[-1600:] if args.action in {'test', 'diagnose', 'logcat'} else completed.stdout, flush=True)
        if completed.stderr:
            print(completed.stderr, flush=True)
        passed = completed.returncode == 0
        if args.action == 'test':
            passed = passed and bool(re.search(r'OK \([0-9]+ tests?\)', completed.stdout)) and 'FAILURES!!!' not in completed.stdout
        results.append({'command': command, 'exitCode': completed.returncode, 'passed': passed,
                        'stdout': completed.stdout, 'stderr': completed.stderr})
        if not passed:
            break
    report = {'action': args.action, 'suite': args.suite, 'avd': 'Ladno_QA_API30', 'serial': SERIAL,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'results': results,
              'passed': len(results) == len(commands) and all(item['passed'] for item in results)}
    if args.action == 'install':
        report.update(install_metadata)
    name = args.action + ('-' + args.suite if args.suite else '') + ('-' + args.method if args.method else '')
    if args.file:
        name += '-' + args.file
    if args.pid:
        name += '-' + str(args.pid)
    target = evidence / (name + '.json')
    if target.exists():
        shutil.copy2(target, evidence / (name + '-previous-' + str(int(target.stat().st_mtime)) + '.json'))
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if args.action == 'pull-control' and report['passed']:
        (evidence / 'ledger-control.json').write_text(results[0]['stdout'], encoding='utf-8')
    if args.action in {'pull', 'pull-scenario'} and report['passed']:
        (evidence / ('pulled-' + args.file)).write_text(results[0]['stdout'], encoding='utf-8')
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
