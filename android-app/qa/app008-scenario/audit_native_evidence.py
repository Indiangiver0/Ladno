"""Independent read-only audit of root's synthetic Android execution and exact final provenance."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
RUNTIME = ROOT / 'android-app/qa/runtime-host'
OUT = HERE / 'final-native'
OUT.mkdir(exist_ok=True)

def read(path): return json.loads(path.read_text(encoding='utf-8'))
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

build = read(ROOT / 'android-app/dist/build-result.json')
install = read(RUNTIME / 'install.json')
clear = read(RUNTIME / 'clear.json')
command = read(RUNTIME / 'test-receipt-layout.json')
private = read(RUNTIME / 'pulled-qa-app008-receipt-layout.json')
source = read(ROOT / 'android-app/dist/build-source-manifest.json')
checks = []
def check(label, actual, expected):
    checks.append({'id':label,'expected':expected,'actual':actual,'pass':type(actual) is type(expected) and actual == expected})

check('final main APK pin', build['sha256'], 'f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac')
check('installed same main APK', install['apkSha256'], build['sha256'])
check('same source fingerprint', install['sourceFingerprint'], build['sourceFingerprint'])
check('same staged source fingerprint', source['sourceFingerprint'], build['sourceFingerprint'])
test_apk = ROOT / 'android-app/dist/ladno-0.2.0-androidTest.apk'
check('installed actual test APK', sha(test_apk), install['testApkSha256'])
for name, data in [('install',install),('clear',clear),('instrument',command)]:
    check(name + '/dedicated AVD', data['avd'], 'Ladno_QA_API30')
    check(name + '/dedicated serial', data['serial'], 'emulator-5580')
    check(name + '/command passed', data['passed'], True)
check('clear followed install', install['createdAtUtc'] < clear['createdAtUtc'] < private['startedAt'], True)
stdout = command['results'][0]['stdout']
check('actual instrumentation class', 'com.ladno.finance.ReceiptLayoutRuntimeTest' in stdout, True)
check('actual successful one test', bool(re.search(r'OK \(1 test\)', stdout)), True)
check('no instrumentation failures', 'FAILURES!!!' in stdout, False)
check('native API30', private['apiLevel'], 30)
check('native x86_64', private['abis'][0], 'x86_64')
check('private no failure', 'failure' in private, False)
check('private71assertions', len(private['checks']), 71)
for index, item in enumerate(private['checks']):
    check('assertion/' + str(index) + '/' + item['id'], item['actual'], item['expected'])
    check('assertion pass flag/' + str(index), item['pass'], True)
    check('command includes assertion/' + str(index), item['id'] + ': PASS' in stdout, True)

obs = {item['id']: item['actual'] for item in private['observations']}
expected = {
    'en-multiline-paid': ('754.32','needs_review',['LOW_CONFIDENCE']),
    'ru-fiscal-card': ('8765.43','needs_review',['CURRENCY_UNKNOWN','LOW_CONFIDENCE']),
    'ru-unpaid': ('99.85','needs_review',['UNSUPPORTED_EVENT','LOW_CONFIDENCE']),
    'en-agreement-paid': ('123.45','posted',[]),
    'ru-fiscal-tenge': ('6543.21','posted',[]),
}
timings = []
for name, (amount,status,reasons) in expected.items():
    actual = obs[name + '/native committed']
    for key,value in {'amount':amount,'currency':'KZT','kind':'expense','operation_date':'2026-11-03','status':status,'reasons':reasons}.items():
        check(name + '/independent ground truth/' + key, actual[key], value)
    check(name + '/transaction only if posted', actual['transaction_id'] is not None, status == 'posted')
    direct = obs[name + '/synthetic OCR']
    check(name + '/primary actual amount correct', direct['parsed']['amount'], amount)
    check(name + '/both passes executed', direct['verificationFailed'], False)
    timings.append({'fixture':name,'primaryMillis':direct['primaryMillis'],'verificationMillis':direct['verificationMillis'],
                    'primaryConfidence':direct['meanConfidence'],'verificationConfidence':direct['verificationConfidence']})
    if status == 'posted':
        check(name + '/verifier correct amount', direct['verificationParsed']['amount'], amount)
    for suffix in ['primaryMillis','verificationMillis']:
        check(name + '/' + suffix + ' nonnegative integer', type(direct[suffix]) is int and direct[suffix] >= 0, True)

check('known alternative stays794.32', obs['en-multiline-paid/synthetic OCR']['verificationParsed']['amount'], '794.32')
independent_counts = {'en-multiline-paid':0,'ru-fiscal-card':0,'ru-unpaid':0,'en-agreement-paid':1,'ru-fiscal-tenge':2}
private_checks = {item['id']:item for item in private['checks']}
for name, count in independent_counts.items():
    check(name + '/independent accumulated record count', private_checks[name + '/ledger delta']['actual'], count)
for item in private['checks']:
    if item['id'].endswith('/no794.32 receipt autoexpense'): check(item['id'] + '/independent zero', item['actual'], 0)
check('original PNG preserved independently', sha(ROOT / 'android-app/qa/app008-runtime/en-original-fixture.png'), '03d1e86729936c8be09a39af09eedba6f6d2aece07ff97196edce4a09ae7af2d')
for item in source['files']:
    check('final source/' + item['path'], sha(ROOT / 'android-app' / item['path']), item['sha256'])
stage = Path(build['stage'])
unit = [ET.parse(path).getroot() for path in (stage / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
unit_summary = {key:sum(int(x.attrib.get(key,0)) for x in unit) for key in ['tests','failures','errors']}
check('root unit tests42', unit_summary['tests'], 42)
check('root unit no failures', unit_summary['failures'], 0)
check('root unit no errors', unit_summary['errors'], 0)

copied = []
for path in [RUNTIME/'install.json', RUNTIME/'clear.json',RUNTIME/'test-receipt-layout.json',RUNTIME/'pulled-qa-app008-receipt-layout.json',RUNTIME/'pull-qa-app008-receipt-layout.json.json',ROOT/'android-app/dist/build-result.json',ROOT/'android-app/dist/build-source-manifest.json']:
    target = OUT / path.name
    if target.exists() and sha(target) != sha(path): raise RuntimeError('Refusing to overwrite frozen final evidence: ' + target.name)
    if not target.exists(): shutil.copy2(path,target)
    copied.append({'file':target.name,'sha256':sha(target)})
result = {'scope':'independent audit of root actual native API execution; not own Android execution or UI',
          'apkSha256':build['sha256'],'sourceFingerprint':build['sourceFingerprint'], 'nativeAssertions':len(private['checks']),
          'auditChecks':checks,'timings':timings,'rootUnitSummary':unit_summary,'copiedEvidence':copied,
          'failed':[x['id'] for x in checks if not x['pass']]}
(HERE/'native-evidence-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'nativeAssertions':len(private['checks']),'auditChecks':len(checks),'failed':result['failed'],'timings':timings}))
raise SystemExit(1 if result['failed'] else 0)
