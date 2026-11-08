"""Collect already-executed synthetic runtime evidence. No ADB, device or product writes."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
HOST = ROOT / 'android-app/qa/runtime-host'
OUT = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect():
    destination = OUT / 'executed'
    destination.mkdir(exist_ok=True)
    cases = [
        ('N2a', 'test-notifications-keystoreParserStorage.json', 'pulled-notification-native.json'),
        ('N2b', 'test-notifications-osGrantedDelivery.json', 'pulled-notification-os-granted.json'),
        ('N2c', 'test-notifications-osRevokedNoCapture.json', 'pulled-notification-os-revoked.json'),
        ('N3', 'test-ocr.json', 'pulled-ocr-runtime.json'),
    ]
    summaries = []
    for criterion, command_name, private_name in cases:
        for name in [command_name, private_name]:
            data = (HOST / name).read_bytes()
            json.loads(data)
            (destination / name).write_bytes(data)
        command = json.loads((destination / command_name).read_text(encoding='utf-8'))
        private = json.loads((destination / private_name).read_text(encoding='utf-8'))
        summaries.append({'criterion': criterion, 'pid': private['pid'], 'api': private['apiLevel'], 'abis': private['abis'],
            'startedAt': private['startedAt'], 'finishedAt': private['finishedAt'],
            'commandPassed': command['passed'], 'assertions': len(private['checks']),
            'failedAssertions': [item['id'] for item in private['checks'] if not item['pass']],
            'commandEvidence': command_name, 'commandSha256': sha(destination / command_name),
            'privateEvidence': private_name, 'privateSha256': sha(destination / private_name)})
    attempts = []
    attempts_dir = OUT / 'pre-readiness-attempts'
    attempts_dir.mkdir(exist_ok=True)
    for path in sorted(HOST.glob('test-notifications-osGrantedDelivery-previous-*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        if data['passed']:
            continue
        (attempts_dir / path.name).write_bytes(path.read_bytes())
        attempts.append({'file': path.name, 'createdAtUtc': data['createdAtUtc'], 'passed': data['passed'],
                         'sha256': sha(attempts_dir / path.name),
                         'classification': 'redundant old diagnostic APK orchestration attempt' if '1793644575' in path.name else 'pre-readiness setup failure'})
    models = json.loads((destination / 'pulled-ocr-runtime.json').read_text(encoding='utf-8'))
    formats = []
    for observation in models['observations']:
        actual = observation['actual']
        if observation['id'].endswith('/direct actual OCR'):
            formats.append({'format': actual['syntheticInput'], 'actualOcrText': actual['text'], 'meanConfidence': actual['meanConfidence'],
                            'actualParsedCandidate': actual['candidate']})
        if observation['id'].endswith('/actual native outcome'):
            key = actual['expectedImage']['id']
            next(row for row in formats if row['format']['id'] == key)['nativeOutcome'] = actual['actualObservation']
    apk_paths = {
        'productionApk': ROOT / 'android-app/dist/ladno-0.1.0-debug.apk',
        'currentReadinessTestApk': ROOT / 'android-app/dist/ladno-0.1.0-androidTest.apk',
        'preservedInitialTestApk': HOST / 'nls-initial-test.apk',
        'preservedDiagnosticTestApk': HOST / 'nls-diagnostic-test.apk',
    }
    for name in ['install.json', 'grant-nls.json', 'revoke-nls.json', 'emulator-state.json']:
        data = (HOST / name).read_bytes(); json.loads(data); (destination / name).write_bytes(data)
    summary = {'environment': {'avd': 'Ladno_QA_API30', 'serial': 'emulator-5580', 'api': 30, 'abi': 'x86_64'},
        'executionOwner': 'root: guarded native instrumentation, no UI automation',
        'reviewer': '/root/android_qa_scenario: independent readback of actual commands/private evidence',
        'cases': summaries, 'successfulAssertions': sum(row['assertions'] for row in summaries),
        'allRecordedSuccessfulRunsPassed': all(row['commandPassed'] and not row['failedAssertions'] for row in summaries),
        'preservedPreReadinessFailedAttempts': attempts,
        'installation': json.loads((destination / 'install.json').read_text(encoding='utf-8')),
        'apkHashes': {name: {'sha256': sha(path), 'bytes': path.stat().st_size} for name, path in apk_paths.items()},
        'formats': formats,
        'limitations': ['Synthetic own-package notifications after actual public OS readiness; no bank compatibility claim.',
            'Generated clean PNGs, not real receipt photos; KZ paid sample remained review after OCR misspelling.',
            'Native API/media path only; camera/gallery UI and registered job lifecycle not executed.',
            'Only Android11/API30 x86_64 emulator execution; physical ARM/OEM/other API runtime not executed.']}
    (OUT / 'results-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'successfulRuns': len(summaries), 'successfulAssertions': summary['successfulAssertions'],
        'passed': summary['allRecordedSuccessfulRunsPassed'], 'preservedFailedAttempts': len(attempts)}))


if __name__ == '__main__':
    collect()
