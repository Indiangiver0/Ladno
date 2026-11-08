"""Compile and exercise actual consensus helper independently; no product edits."""
import hashlib,json,subprocess,sys
from pathlib import Path
from datetime import datetime,timezone
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];APP=ROOT/'android-app'
TOOLS=Path('C:/Users/04068/.cache/ladno-android');JAVA=TOOLS/'jdk21/jdk-21.0.13+9/bin'
JAR=next((TOOLS/'gradle-cache/caches/modules-2/files-2.1/org.json/json/20240303').rglob('*.jar'))
classes=HERE/'consensus-classes';classes.mkdir(exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources=sorted((APP/'app/src/main/java/com/ladno/finance/core').glob('*.java'))
before={p.relative_to(ROOT).as_posix():sha(p) for p in sources}
previous=json.loads((HERE/'summary.json').read_text(encoding='utf-8'))
unaffected={name:sha(ROOT/name)==value for name,value in previous['sourceHashes'].items() if (ROOT/name).suffix=='.java'}
commands=[{'name':'consensus-javac','command':list(map(str,[JAVA/'javac.exe','--release','17','-encoding','UTF-8','-cp',JAR,'-d',classes,*sources,HERE/'ReceiptConsensusIndependentQa.java']))},
          {'name':'consensus-java','command':list(map(str,[JAVA/'java.exe','-cp',str(classes)+';'+str(JAR),'ReceiptConsensusIndependentQa',HERE/'consensus-results.json']))}]
for entry in commands:
    result=subprocess.run(entry['command'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    entry.update(exitCode=result.returncode)
    (HERE/(entry['name']+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
    print(entry['name'],'exit',result.returncode,result.stdout.strip())
    if result.returncode:print(result.stderr[-1500:]);break
changed=[name for name,value in before.items() if sha(ROOT/name)!=value]
result={'scope':'Independent actual pure-Java ReceiptOcrConsensus; no native OCR/JNI/UI execution','executedAtUtc':datetime.now(timezone.utc).isoformat(),'sourceHashes':before,'previousJvmCoreUnchanged':unaffected,'sourceChangedDuringRun':changed,'commands':commands}
if (HERE/'consensus-results.json').is_file():
    actual=json.loads((HERE/'consensus-results.json').read_text(encoding='utf-8'))
    result.update(vectorGroups=actual['vectorGroups'],assertions=actual['assertions'],failed=actual['failed'],resultSha256=sha(HERE/'consensus-results.json'))
(HERE/'consensus-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
raise SystemExit(1 if changed or not all(unaffected.values()) or any(e.get('exitCode',1) for e in commands) or result.get('failed',1) else 0)
