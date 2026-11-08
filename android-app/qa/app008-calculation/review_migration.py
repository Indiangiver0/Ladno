"""Read-only independent audit of root-executed real Android migration evidence."""
import hashlib
import json
from pathlib import Path
from datetime import datetime,timezone

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
HOST=ROOT/'android-app/qa/runtime-host'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
checks=[]; paths=[]
def eq(id,expected,actual):
    checks.append({'id':id,'expected':expected,'actual':actual,'pass':type(expected) is type(actual) and expected==actual})
build=read(ROOT/'android-app/dist/build-result.json');install=read(HOST/'install.json');test=read(HOST/'test-starter.json')
paths += [ROOT/'android-app/dist/build-result.json',HOST/'install.json',HOST/'test-starter.json']
device=read(HOST/'emulator-state.json');paths.append(HOST/'emulator-state.json')
eq('runtime API','30',device['api']);eq('runtime ABI','x86_64',device['abi'])
eq('test AVD','Ladno_QA_API30',test['avd']);eq('install AVD','Ladno_QA_API30',install['avd'])
eq('test serial','emulator-5580',test['serial']);eq('install serial','emulator-5580',install['serial'])
eq('source fingerprint',build['sourceFingerprint'],install['sourceFingerprint']);eq('main APK declared',build['sha256'],install['apkSha256'])
eq('main APK bytes',install['apkSha256'],sha(ROOT/'android-app/dist'/build['apk']))
eq('test APK bytes',install['testApkSha256'],sha(ROOT/'android-app/dist/ladno-0.2.0-androidTest.apk'))
eq('test invocation exit',0,test['results'][0]['exitCode']);eq('test invocation pass',True,test['passed'])
eq('three actual test methods',True,'OK (3 tests)' in test['results'][0]['stdout'])
eq('expected actual test class',True,'com.ladno.finance.storage.StarterCategoryMigrationTest' in test['results'][0]['command'])
manifest=read(ROOT/'android-app/dist/build-source-manifest.json');paths.append(ROOT/'android-app/dist/build-source-manifest.json')
eq('manifest fingerprint',build['sourceFingerprint'],manifest['sourceFingerprint'])
for name in ['app/src/androidTest/java/com/ladno/finance/storage/StarterCategoryMigrationTest.java','app/src/main/java/com/ladno/finance/storage/LedgerStore.java','app/src/main/java/com/ladno/finance/storage/StarterCategories.java','app/src/main/assets/db/schema.sql']:
    expected=next(item['sha256'] for item in manifest['files'] if item['path']==name)
    p=ROOT/'android-app'/name; paths.append(p)
    eq('built source '+name,expected,sha(p))
    eq('staged source '+name,expected,sha(Path(build['stage'])/name))

phases={}; pairs=0
for phase in ['fresh','upgrade','empty-upgrade']:
    raw=HOST/('pulled-qa-starter-'+phase+'.json');command=HOST/('pull-qa-starter-'+phase+'.json.json')
    paths += [raw,command]
    report=read(raw); provenance=read(command)
    eq(phase+'/phase',phase,report['phase']);eq(phase+'/error',None,report['error'])
    eq(phase+'/pull exit',0,provenance['results'][0]['exitCode']);eq(phase+'/pull AVD','Ladno_QA_API30',provenance['avd'])
    eq(phase+'/pull command path',True,'files/qa-starter-'+phase+'.json' in provenance['results'][0]['command'])
    eq(phase+'/private JSON readback',report,json.loads(provenance['results'][0]['stdout']))
    phases[phase]={a['id']:a for a in report['assertions']}
    for assertion in report['assertions']:
        eq(phase+'/'+assertion['id'],assertion['expected'],assertion['actual'])
        eq(phase+'/'+assertion['id']+'/reported pass',True,assertion['pass'])
        pairs+=1

# Separate expected values are taken from APP-008 seed request and synthetic v1 rows,
# not by trusting author-written expected values inside each private result.
for phase,id,value in [('fresh','starter total',15),('fresh','expense starters',11),('fresh','income starters',4),('fresh','ledger remains empty',0),('fresh','reopen total unchanged',15),('fresh','deleted starter stays deleted',1),('fresh','renamed starter preserved','Моя квартира'),('upgrade','only missing starter names added',17),('upgrade','product revision preserved',7),('upgrade','custom category revision kept',5),('upgrade','transport not resurrected',1),('upgrade','money remains TEXT','text'),('upgrade','foreign key integrity','[]'),('empty-upgrade','empty upgrade starters',15),('empty-upgrade','no financial records invented',0)]:
    eq('independent '+phase+'/'+id,value,phases[phase][id]['actual'])
for table in ['settings','transactions','import_candidates','source_observations','candidate_reasons','candidate_matches','selected_sources']:
    assertion=phases['upgrade']['preserved '+table]
    eq('nested SQLite cells/'+table,json.loads(assertion['expected']),json.loads(assertion['actual']))
transactions=json.loads(phases['upgrade']['preserved transactions']['actual'])
eq('stored huge amount string','999999999999999999999999.99',transactions[0][4]['value'])
eq('stored huge amount SQLiteTEXT',3,transactions[0][4]['type'])
eq('transaction revision9','9',transactions[0][1]['value']);eq('tombstone revision12','12',transactions[1][1]['value'])
eq('deleted financial tombstone retained','1',transactions[1][-1]['value'])
observations=json.loads(phases['upgrade']['preserved source_observations']['actual'])
eq('HMAC source key retained','a'*64,observations[0][5]['value']);eq('HMAC revision token retained','b'*64,observations[0][6]['value'])
previous=read(HERE/'summary.json')
for name,value in previous['sourceHashes'].items():eq('prior JVM/chart source continuity/'+name,value,sha(ROOT/name))
result={'reviewedAtUtc':datetime.now(timezone.utc).isoformat(),'author':'/root/improve_qa_calculation','executionAttribution':'Tests authored by data-engineer; installed and executed by root on synthetic API30x86_64. This audit independently read and checked evidence; did not run Android or phone UI.','command':'python android-app/qa/app008-calculation/review_migration.py','apkSha256':install['apkSha256'],'testApkSha256':install['testApkSha256'],'sourceFingerprint':build['sourceFingerprint'],'actualTests':3,'privateAssertionPairs':pairs,'auditChecks':len(checks),'failed':sum(not c['pass'] for c in checks),'sourceAndEvidenceHashes':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'checks':checks,'limits':'Synthetic SQLiteOpenHelper API create/upgrade/reopen only; no actual user DB, no MainActivity/WebView/camera/gallery UI or real receipt photo verification.'}
(HERE/'migration-review.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
previous['migrationAudit']={'file':'migration-review.json','sha256':sha(HERE/'migration-review.json'),'privateAssertionPairs':pairs,'auditChecks':len(checks),'failed':result['failed'],'apkSha256':install['apkSha256']}
(HERE/'summary.json').write_text(json.dumps(previous,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Reviewed root Android migration:',pairs,'private pairs;',len(checks),'audit checks; failed',result['failed'])
if result['failed']:print(json.dumps([c for c in checks if not c['pass']],ensure_ascii=False,indent=2))
raise SystemExit(1 if result['failed'] else 0)
