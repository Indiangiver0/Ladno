"""Independent APP-008 JVM and exact chart-data checks. No UI automation."""
import copy
import hashlib
import json
from pathlib import Path
import random
import shutil
import subprocess
import sys
from datetime import datetime, timezone

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
APP=ROOT/'android-app'
TOOLS=Path('C:/Users/04068/.cache/ladno-android')
JAVA=TOOLS/'jdk21/jdk-21.0.13+9/bin'
JAR=next((TOOLS/'gradle-cache/caches/modules-2/files-2.1/org.json/json/20240303').rglob('*.jar'))
CLASSES=HERE/'classes'
CLASSES.mkdir(exist_ok=True)
commands=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
source_paths=list((APP/'app/src/main/java/com/ladno/finance/core').glob('*.java'))
source_paths += [APP/'app/src/main/assets/www/view.js',ROOT/'docs/android/APP-008-CHART-FIXTURES.json',ROOT/'docs/android/APP-008-CHARTS.md',APP/'fixtures/money-cases.json']
before={p.relative_to(ROOT).as_posix():sha(p) for p in source_paths}
def run(name,args):
    commands.append({'name':name,'args':list(map(str,args))})
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True,encoding='utf-8',errors='replace')
    (HERE/(name+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
    print(name,'exit',result.returncode,result.stdout.strip())
    if result.returncode: print(result.stderr[-1000:])
    return result.returncode

# Re-execute historical independent 211 financial regressions against current actual core,
# preserving original evidence and source byte-for-byte in a new output directory.
historical=APP/'qa/calculation/CalculationQa.java'
shutil.copy2(historical,HERE/'CalculationQa.java')
core=sorted((APP/'app/src/main/java/com/ladno/finance/core').glob('*.java'))
compile_exit=run('javac',[JAVA/'javac.exe','--release','17','-encoding','UTF-8','-cp',JAR,'-d',CLASSES,*core,HERE/'CalculationQa.java',HERE/'ReceiptIndependentQa.java'])
exits={'javac':compile_exit}
if not compile_exit:
    exits['regression']=run('regression',[JAVA/'java.exe','-cp',str(CLASSES)+';'+str(JAR),'CalculationQa',APP/'fixtures/money-cases.json',HERE/'regression-results.json'])
    exits['receipt']=run('receipt',[JAVA/'java.exe','-cp',str(CLASSES)+';'+str(JAR),'ReceiptIndependentQa',HERE/'receipt-results.json'])

finance=json.loads((ROOT/'docs/android/APP-008-CHART-FIXTURES.json').read_text(encoding='utf-8'))
tests=[{'id':t['id'],'state':t['state'],'expected':t['expected']} for t in finance['cases']+finance['invalidCases']]
rng=random.Random(8008)
def decimal(minor,scale):
    text=str(minor).zfill(scale+1)
    return text[:-scale]+'.'+text[-scale:] if scale else text
def independent_expected(state):
    # Reference derives from Python int, then divmod and exact rational floors.
    # It does not load/read the product chartModel implementation.
    scale=state['summary']['scale']; values={}; included=[]
    for row in state['records']:
        if row['kind']!='expense':continue
        key=row['categoryId'] or '~uncategorized'
        values[key]=values.get(key,0)+int(row['amount'].replace('.',''))
        included.append(row['id'])
    total=sum(values.values()); order=sorted(values,key=lambda k:(-values[k],k))
    divs={k:divmod(10000*values[k],total) for k in order}
    points={k:v[0] for k,v in divs.items()}
    ranked=sorted(order,key=lambda k:(-divs[k][1],k))
    for k in ranked[:10000-sum(points.values())]:points[k]+=1
    groups=[{'key':k,'minor':str(values[k]),'amount':decimal(values[k],scale),'basisPoints':points[k],'percent':str(points[k]//100)+'.'+str(points[k]%100).zfill(2)} for k in order]
    visual=[(k,values[k],points[k]) for k in order[:5]]+([('~other',sum(values[k] for k in order[5:]),sum(points[k] for k in order[5:]))] if len(order)>6 else [(k,values[k],points[k]) for k in order])
    sectors=[]; cumul=0; start=0
    for key,value,bps in visual:
        cumul+=value; end=1000000000*cumul//total
        sectors.append({'key':key,'minor':str(value),'amount':decimal(value,scale),'basisPoints':bps,'startUnit':start,'endUnit':end,'widthUnits':end-start,'zeroWidth':start==end})
        start=end
    return {'status':'ready','totalMinor':str(total),'totalAmount':decimal(total,scale),'includedExpenseIds':sorted(included),'groups':groups,'sectors':sectors}

for index in range(40):
    scale=[0,2,3,6][index%4]; code=['JPY','KZT','KWD','CLF'][index%4]
    categories=[{'id':f'00000000-0000-4000-8000-{i:012d}','name':f'Independent {i}','kind':'expense','revision':1} for i in range(1,2+index%9)]
    records=[]
    for i in range(2+index%17):
        magnitude=10**(2 if index%3==0 else 18 if index%3==1 else 24)
        minor=rng.randrange(1,magnitude)
        # Canonical input integer part stays within24 digits for all scales.
        records.append({'id':f'10000000-0000-4000-8000-{i+1:012d}','kind':'expense','date':'2026-11-03','amount':decimal(minor,scale),'currency':code,'scale':scale,'categoryId':None if i%7==0 else categories[i%len(categories)]['id']})
    total=sum(int(x['amount'].replace('.','')) for x in records)
    records.append({'id':'20000000-0000-4000-8000-000000000001','kind':'income','date':'2026-11-03','amount':decimal(67890,scale),'currency':code,'scale':scale,'categoryId':None})
    state={'selection':{'month':'2026-11','currency':code},'currencies':[{'code':code,'scale':scale}],'records':records,'categories':categories,'summary':{'month':'2026-11','currency':code,'scale':scale,'expense':decimal(total,scale)}}
    tests.append({'id':f'INDEPENDENT-{index:02d}','state':state,'expected':independent_expected(state)})
(HERE/'chart-inputs.json').write_text(json.dumps(tests,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
exits['chartNode']=run('chart-node',['node',HERE/'chart_actual.js',HERE/'chart-inputs.json',HERE/'chart-actual.json'])
comparisons=[]
def compare(actual,expected,at):
    if isinstance(expected,dict):
        for key,value in expected.items():compare(actual.get(key) if isinstance(actual,dict) else None,value,at+'.'+key)
    elif isinstance(expected,list):
        comparisons.append({'id':at+'/length','expected':len(expected),'actual':len(actual) if isinstance(actual,list) else None,'pass':isinstance(actual,list) and len(expected)==len(actual)})
        for i,value in enumerate(expected):compare(actual[i] if isinstance(actual,list) and i<len(actual) else None,value,at+f'[{i}]')
    else:comparisons.append({'id':at,'expected':expected,'actual':actual,'pass':type(expected) is type(actual) and expected==actual})
if not exits['chartNode']:
    actual=json.loads((HERE/'chart-actual.json').read_text(encoding='utf-8'))
    for test,result in zip(tests,actual):compare(result['actual'],test['expected'],test['id'])
    compare(actual[-1]['actual'],{'status':'unavailable','noPartialTotals':True},'NO-BIGINT')
report={'scope':'Independent pure chart data model; not browser/Android UI','caseGroups':len(tests)+1,'financeGroups':19,'independentRandomGroups':40,'comparisons':len(comparisons),'failed':sum(not c['pass'] for c in comparisons),'results':comparisons}
(HERE/'chart-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
changed=[key for key,value in before.items() if sha(ROOT/key)!=value]
summary={'executedAtUtc':datetime.now(timezone.utc).isoformat(),'commands':commands,'exits':exits,'sourceHashes':before,'sourcesChangedDuringRun':changed,'chartGroups':report['caseGroups'],'chartComparisons':report['comparisons'],'chartFailed':report['failed'],'historicalHarnessSha256':sha(historical),'orgJsonSha256':sha(JAR)}
(HERE/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Chart groups',report['caseGroups'],'scalar/array comparisons',report['comparisons'],'failed',report['failed'])
print('Sources changed:',changed)
sys.exit(1 if any(exits.values()) or report['failed'] or changed else 0)
