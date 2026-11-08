"""Explicit two-own-receipt native lookup; no ingest/UI, no raw values in commands or output."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import subprocess
from urllib.parse import urlsplit, parse_qs

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
CACHE = Path('C:/Users/04068/.cache/ladno-android')
AVD, SERIAL = 'Ladno_QA_API30', 'emulator-5580'
SCOPE = 'APP-017-SAMPLES-4-5'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-own-receipts-4-5', action='store_true', required=True)
    parser.add_argument('--transport-diagnostic', action='store_true')
    args = parser.parse_args()
    toolchain = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    adb = str(Path(toolchain['sdkRoot']) / 'platform-tools/adb.exe')
    evidence = {'startedAtUtc': datetime.now(timezone.utc).isoformat(), 'scope': SCOPE,
                'serial': SERIAL, 'avd': AVD, 'commands': [], 'ledgerIngestCalled': False,
                'transportDiagnostic': args.transport_diagnostic}
    guarded = False

    def run(args, timeout=30, input_bytes=None, binary=False, redact_output=False):
        result = subprocess.run([adb, '-s', SERIAL, *args], input=input_bytes, capture_output=True, timeout=timeout)
        # Input bytes are never logged. These commands contain fixed paths/opt-in only.
        stdout = result.stdout.decode('utf-8', errors='replace')
        stderr = result.stderr.decode('utf-8', errors='replace')
        evidence['commands'].append({'args': args, 'exitCode': result.returncode,
                                     'stdout': '[private upload output suppressed]' if redact_output else stdout,
                                     'stderr': '[private upload output suppressed]' if redact_output else stderr})
        if result.returncode:
            raise RuntimeError('Authorized native command failed')
        return result.stdout if binary else stdout

    try:
        identity = run(['emu', 'avd', 'name'])  # FIRST adb command, before any private-data handling.
        if not identity.splitlines() or identity.splitlines()[0].strip() != AVD:
            raise RuntimeError('Refusing non-dedicated AVD')
        guarded = True
        build = json.loads((ROOT / 'android-app/dist/build-result.json').read_text(encoding='utf-8'))
        if build['versionName'] != '0.4.0' or build['versionCode'] != 4:
            raise RuntimeError('Expected version 0.4.0/code4')
        main_apk = ROOT / 'android-app/dist' / build['apk']
        stage = Path(build['stage']).resolve()
        if not stage.is_relative_to((CACHE / 'work').resolve()) or sha(main_apk) != build['sha256']:
            raise RuntimeError('Build artifact mismatch')
        test_apk = stage / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        source = Path('app/src/androidTest/java/com/ladno/finance/OfdAuthorizedLiveRuntimeTest.java')
        if sha(stage / source) != sha(ROOT / 'android-app' / source):
            raise RuntimeError('Authorized fixture differs from staged source')
        evidence['mainApkSha256'] = build['sha256']
        evidence['testApkSha256'] = sha(test_apk)
        for package, expected in [('com.ladno.finance', build['sha256']), ('com.ladno.finance.test', sha(test_apk))]:
            location = run(['shell', 'pm', 'path', package]).strip().splitlines()
            if len(location) != 1 or not location[0].startswith('package:/data/app/'):
                raise RuntimeError('Unexpected installed package location')
            actual = run(['shell', 'sha256sum', location[0][8:]]).split()[0].lower()
            if actual != expected.lower():
                raise RuntimeError('Installed artifact mismatch')
        decoded = json.loads((ROOT / 'android-app/.local/app017/decoded-receipts.json').read_text(encoding='utf-8'))
        samples = []
        for number, amount in [(4, '4395.00'), (5, '3547.00')]:
            records = [row for row in decoded if row.get('sample') == number]
            if len(records) != 1 or len(records[0].get('payloads', [])) != 1:
                raise RuntimeError('Authorized sample is not uniquely decoded')
            payload = records[0]['payloads'][0]
            uri = urlsplit(payload)
            query = parse_qs(uri.query, keep_blank_values=True)
            if uri.hostname != 'consumer.oofd.kz' or uri.path not in ['', '/'] or set(query) != {'i', 'f', 's', 't'}:
                raise RuntimeError('Authorized fiscal provider mismatch')
            if (any(len(value) != 1 for value in query.values()) or Decimal(query['s'][0]) != Decimal(amount)
                    or not query['t'][0].startswith('20261103T')):
                raise RuntimeError('Authorized fiscal scope mismatch')
            samples.append({'sample': number, 'qr': payload})
        private_bytes = json.dumps({'scope': SCOPE, 'samples': samples}, ensure_ascii=True).encode('utf-8')
        decoded = None; samples = None
        expected_size = len(private_bytes)
        expected_hash = hashlib.sha256(private_bytes).hexdigest()
        # Direct argv avoids remote-shell redirection/quoting. tee's echoed input
        # is captured and suppressed, never printed or written to QA evidence.
        run(['exec-in', 'run-as', 'com.ladno.finance', 'tee', 'files/qa-ofd-authorized.json'],
            input_bytes=private_bytes, redact_output=True)
        private_bytes = None
        actual_size = int(run(['shell', 'run-as', 'com.ladno.finance', 'stat', '-c', '%s',
                               'files/qa-ofd-authorized.json']).strip())
        actual_hash = run(['shell', 'run-as', 'com.ladno.finance', 'sha256sum',
                           'files/qa-ofd-authorized.json']).split()[0].lower()
        evidence['privateInputVerified'] = actual_size == expected_size and actual_hash == expected_hash
        evidence['privateInputBytes'] = actual_size
        evidence['privateInputSha256'] = actual_hash
        if not evidence['privateInputVerified']:
            raise RuntimeError('Authorized private input metadata mismatch')
        instrumentation = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                           'com.ladno.finance.OfdAuthorizedLiveRuntimeTest', '-e', 'ladnoSyntheticAvd', AVD,
                           '-e', 'ladnoLiveOfdScope', SCOPE]
        if args.transport_diagnostic:
            instrumentation += ['-e', 'ladnoOfdDiagnostic', 'transport-v1']
        instrumentation += ['com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner']
        stdout = run(instrumentation, timeout=90)
        raw = run(['exec-out', 'run-as', 'com.ladno.finance', 'cat', 'files/qa-app017-authorized-live.json'], binary=True)
        safe = json.loads(raw)
        (HERE / 'authorized-live-evidence.json').write_bytes(raw)
        passed = ('OK (1 test)' in stdout and 'FAILURES!!!' not in stdout and safe.get('passed')
                  and safe.get('privateInputRemoved') and safe.get('noLedgerWrite')
                  and len(safe.get('results', [])) == 2)
        evidence['passed'] = bool(passed)
        if not passed:
            raise RuntimeError('Authorized default-client check failed; safe evidence retained')
        print(json.dumps({'passed': True, 'sampleResults': safe['results'], 'noLedgerWrite': True,
                          'privateInputRemoved': True, 'mainApkSha256': build['sha256']}))
    except Exception as error:
        evidence['passed'] = False
        evidence['safeFailureType'] = type(error).__name__
        # Never stringify arbitrary parsing/network exceptions carrying private input.
        raise RuntimeError('Authorized live runner failed; inspect safe evidence') from None
    finally:
        if guarded:
            try:
                run(['shell', 'run-as', 'com.ladno.finance', 'rm', '-f', 'files/qa-ofd-authorized.json'])
                evidence['cleanupAttempted'] = True
            except Exception:
                evidence['cleanupAttempted'] = False
        evidence['finishedAtUtc'] = datetime.now(timezone.utc).isoformat()
        HERE.mkdir(parents=True, exist_ok=True)
        (HERE / 'authorized-live-run.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
