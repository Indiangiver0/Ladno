"""Bind latest independent calculation evidence to final build; reuse unchanged narrow audits."""
import hashlib,json,zipfile
from pathlib import Path
from datetime import datetime,timezone
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];APP=ROOT/'android-app'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
checks=[]
def eq(id,expected,actual):checks.append({'id':id,'expected':expected,'actual':actual,'pass':type(expected)is type(actual) and expected==actual})
summary=read(HERE/'summary.json');build=read(APP/'dist/build-result.json');manifest=read(APP/'dist/build-source-manifest.json')
eq('build/source fingerprint',build['sourceFingerprint'],manifest['sourceFingerprint'])
eq('actual main APK',build['sha256'],sha(APP/'dist'/build['apk']))
hashes={f['path']:f['sha256'] for f in manifest['files']}
for name,value in summary['sourceHashes'].items():
    eq('tested source unchanged/'+name,value,sha(ROOT/name))
    if name.startswith('android-app/'):
        relative=name[len('android-app/'):]
        eq('tested source in built manifest/'+relative,value,hashes[relative])
        eq('tested source staged/'+relative,value,sha(Path(build['stage'])/relative))
with zipfile.ZipFile(APP/'dist'/build['apk']) as archive:
    view=hashlib.sha256(archive.read('assets/www/view.js')).hexdigest()
eq('actual APK chart model',summary['sourceHashes']['android-app/app/src/main/assets/www/view.js'],view)
consensus=read(HERE/'consensus-summary.json')
helper='android-app/app/src/main/java/com/ladno/finance/core/ReceiptOcrConsensus.java'
eq('reused 384-assertion helper same bytes',consensus['sourceHashes'][helper],sha(ROOT/helper))
eq('reused helper actual build',consensus['sourceHashes'][helper],hashes[helper[len('android-app/'):]])
eq('reused guard assertions',384,consensus['assertions']);eq('reused guard failures',0,consensus['failed'])
migration=read(HERE/'migration-review.json')
eq('root migration executed on final APK',build['sha256'],migration['apkSha256'])
storage_names=['app/src/main/java/com/ladno/finance/storage/LedgerStore.java','app/src/main/java/com/ladno/finance/storage/StarterCategories.java','app/src/main/assets/db/schema.sql','app/src/androidTest/java/com/ladno/finance/storage/StarterCategoryMigrationTest.java']
for name in storage_names:
    expected=migration['sourceAndEvidenceHashes']['android-app/'+name]
    eq('reused Android migration source unchanged/'+name,expected,sha(APP/name))
    eq('reused Android migration source built/'+name,expected,hashes[name])
eq('reused migration actual assertion pairs',39,migration['privateAssertionPairs'])
eq('reused migration audit failures',0,migration['failed'])
result={'executedAtUtc':datetime.now(timezone.utc).isoformat(),'command':'python android-app/qa/app008-calculation/final_continuity.py','scope':'Current JVM/chart tested sources and root-executed migration evidence bound to same final APK; pure helper evidence reused where tested bytes unchanged. This script does not execute native OCR or Android migration.','apkSha256':build['sha256'],'sourceFingerprint':build['sourceFingerprint'],'actualTests':{'regression':read(HERE/'regression-results.json')['assertions'],'receiptBank':read(HERE/'receipt-results.json')['assertions'],'chartGroups':summary['chartGroups'],'chartComparisons':summary['chartComparisons']},'checks':checks,'auditChecks':len(checks),'failed':sum(not c['pass'] for c in checks),'evidenceHashes':{n:sha(HERE/n) for n in ['summary.json','receipt-results.json','regression-results.json','chart-results.json','consensus-summary.json','migration-review.json']}}
(HERE/'final-continuity.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Final calculation continuity:',len(checks),'checks; failed',result['failed'])
if result['failed']:print(json.dumps([c for c in checks if not c['pass']],ensure_ascii=False,indent=2))
raise SystemExit(1 if result['failed'] else 0)
