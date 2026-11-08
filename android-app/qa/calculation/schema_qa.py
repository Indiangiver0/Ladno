"""Independent checks against actual DDL on host SQLite, not Android LedgerStore."""
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import sqlite3

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SCHEMA = ROOT / 'app/src/main/assets/db/schema.sql'
FIXTURES = json.loads((ROOT / 'fixtures/money-cases.json').read_text(encoding='utf-8'))
RESULTS = []


def check(test_id, expected, actual):
    RESULTS.append(dict(id=test_id, expected=expected, actual=actual, passed=expected == actual))


def rejected(test_id, connection, statement, parameters=()):
    try:
        connection.execute(statement, parameters)
    except sqlite3.IntegrityError:
        check(test_id, 'IntegrityError', 'IntegrityError')
    else:
        check(test_id, 'IntegrityError', 'accepted')


db_path = HERE / 'qa.sqlite'
if db_path.exists():
    # Keep earlier evidence; each run uses a fresh numbered database.
    index = 1
    while (HERE / f'qa-{index}.sqlite').exists():
        index += 1
    db_path = HERE / f'qa-{index}.sqlite'
db = sqlite3.connect(db_path)
db.execute('PRAGMA foreign_keys=ON')
db.executescript(SCHEMA.read_text(encoding='utf-8'))
check('freshSetup', (0, None, None, None), db.execute('SELECT initialized,primary_currency,zone_id,date_policy FROM settings').fetchone())
check('freshTransactionCount', 0, db.execute('SELECT count(*) FROM transactions').fetchone()[0])
check('freshSourceCount', 0, db.execute('SELECT count(*) FROM selected_sources').fetchone()[0])

INSERT = '''INSERT INTO transactions(id,revision,kind,operation_date,amount,currency,scale,category_id,note,source,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)'''
def params(identifier, amount='1.20', date='2026-10-31', category=None, kind='expense', currency='KZT', scale=2):
    return (identifier, 1, kind, date, amount, currency, scale, category, '', 'manual', '2026-11-02T00:00:00Z', '2026-11-02T00:00:00Z')

for index, amount in enumerate(['0.00', '-1.00', '+1.00', '01.00', '1e2', '1.200', '1.', '1,20', '1..00', '1000000000000000000000000.00']):
    rejected(f'invalidAmount/{index}', db, INSERT, params('invalid'+str(index), amount))
for index, date in enumerate(['2026-02-29', '1900-02-29', '2026-04-31', '0000-01-01', '2026-13-01']):
    rejected(f'invalidDate/{index}', db, INSERT, params('badDate'+str(index), date=date))
for index, date in enumerate(['2024-02-29', '2000-02-29', '0001-01-01', '9999-12-31']):
    db.execute(INSERT, params('validDate'+str(index), date=date))
    check(f'validDate/{index}', date, db.execute('SELECT operation_date FROM transactions WHERE id=?', ('validDate'+str(index),)).fetchone()[0])

db.execute("INSERT INTO categories VALUES('expenses',1,'Test','expense',0)")
db.execute("INSERT INTO categories VALUES('income',1,'Test','income',0)")
db.execute(INSERT, params('withCategory', category='expenses'))
rejected('categoryKindMismatch', db, INSERT, params('wrongCategory', category='income'))
rejected('categoryMissing', db, INSERT, params('missingCategory', category='missing'))
rejected('categoryInUseKindImmutable', db, "UPDATE categories SET kind='income' WHERE id='expenses'")
rejected('categoryInUseCannotTombstone', db, "UPDATE categories SET deleted=1 WHERE id='expenses'")
db.execute("UPDATE transactions SET category_id=NULL WHERE category_id='expenses'")
db.execute("UPDATE categories SET deleted=1 WHERE id='expenses'")
check('categoryDeletePreservesMoney', ('1.20', None, 0), db.execute("SELECT amount,category_id,deleted FROM transactions WHERE id='withCategory'").fetchone())

large=next(group for group in FIXTURES['caseGroups'] if group['id']=='MONEY-040')['input']['record']
db.execute(INSERT, params('largeExact', amount=large['amount']))
check('largeExactTEXT', ('9007199254740993.01','text'), db.execute("SELECT amount,typeof(amount) FROM transactions WHERE id='largeExact'").fetchone())
check('staleCASChangesNothing', 0, db.execute("UPDATE transactions SET amount='2.00',revision=2 WHERE id='largeExact' AND revision=999").rowcount)
check('currentCASUpdatesOne', 1, db.execute("UPDATE transactions SET note='changed',revision=2 WHERE id='largeExact' AND revision=1").rowcount)

# Actual schema range selection uses text dates that its calendar CHECK validates.
for identifier, date, amount in [('monthStart','2026-10-01','1.00'),('monthEnd','2026-10-31','2.00'),('nextMonth','2026-11-01','4.00')]:
    db.execute(INSERT, params(identifier, amount=amount, date=date))
selected=db.execute("SELECT id,amount FROM transactions WHERE id IN ('monthStart','monthEnd','nextMonth') AND operation_date>=? AND operation_date<? ORDER BY id", ('2026-10-01','2026-11-01')).fetchall()
check('halfOpenMonthIDs', ['monthEnd','monthStart'], [row[0] for row in selected])
check('independentSelectedAmountOracle', '3.00', str(sum((Decimal(row[1]) for row in selected), Decimal(0))))

candidate_insert="""INSERT INTO import_candidates(id,revision,status,source,source_label,received_at,kind,amount,currency,scale,operation_date,date_origin,note,confidence,transaction_id)
 VALUES(?,1,'posted','notification','Уведомление','2026-11-02T00:00:00Z','expense','1.20','KZT',2,'2026-10-31','source','','high','withCategory')"""
source_insert="""INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,account_surrogate,payment_ref_token,captured_at,post_time)
 VALUES(?,?,'notification',?,'com.example.bank',?,?,NULL,NULL,'2026-11-02T00:00:00Z',1793478600000)"""
token='a'*64
for identifier in ['candidate1','candidate2','candidate3']:
    db.execute(candidate_insert, (identifier,))
db.execute(source_insert, ('source1','candidate1','event1',token,'b'*64))
rejected('exactRevisionUnique', db, source_insert, ('source2','candidate2','event2',token,'b'*64))
db.execute(source_insert, ('source3','candidate3','event3',token,'c'*64))
check('newRevisionRemainsObservation', 2, db.execute('SELECT count(*) FROM source_observations').fetchone()[0])
db.execute("UPDATE transactions SET deleted=1 WHERE id='withCategory'")
check('tombstoneKeepsSourceObservation', 2, db.execute('SELECT count(*) FROM source_observations').fetchone()[0])
db.commit()
before=db.execute('SELECT count(*) FROM transactions').fetchone()[0]
try:
    with db:
        db.execute(INSERT, params('shouldRollback'))
        db.execute(candidate_insert, ('candidate1',))
except sqlite3.IntegrityError:
    pass
check('sqlTransactionRollback', before, db.execute('SELECT count(*) FROM transactions').fetchone()[0])
db.close()
db=sqlite3.connect(db_path)
check('fileCloseReopenExactAmount', ('9007199254740993.01',2,'changed'), db.execute("SELECT amount,revision,note FROM transactions WHERE id='largeExact'").fetchone())
check('integrityCheck', 'ok', db.execute('PRAGMA integrity_check').fetchone()[0])
check('foreignKeyCheck', [], db.execute('PRAGMA foreign_key_check').fetchall())
db.close()
report=dict(runtime='host sqlite3 '+sqlite3.sqlite_version, nativeAndroidRuntime=False,
            schemaSha256=hashlib.sha256(SCHEMA.read_bytes()).hexdigest(), assertions=len(RESULTS),
            failed=sum(not result['passed'] for result in RESULTS), results=RESULTS)
(HERE / 'schema-results.json').write_text(json.dumps(report, indent=2, ensure_ascii=False)+'\n',encoding='utf-8')
print(f"Independent actual-DDL SQLite QA: {report['assertions']} assertions; failed={report['failed']}")
raise SystemExit(1 if report['failed'] else 0)
