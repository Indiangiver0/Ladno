"""Repeatable real-SQLite schema checks. This is not an Android LedgerStore/device test.

Run: python android-app/app/src/test/java/com/ladno/finance/storage/schema_contract_test.py
Synthetic database files are created only beside this owned test, then removed.
"""
import json
from pathlib import Path
import sqlite3
import uuid


def run():
    test_dir = Path(__file__).resolve().parent
    schema = next(parent / "main/assets/db/schema.sql" for parent in test_dir.parents
                  if (parent / "main/assets/db/schema.sql").is_file()).read_text(encoding="utf-8")
    database_path = (test_dir / (".synthetic-" + str(uuid.uuid4()) + ".db")).resolve()
    assert database_path.parent == test_dir
    db = None
    checks = 0

    def check(value):
        nonlocal checks
        assert value
        checks += 1

    def reject(sql, args):
        try:
            db.execute(sql, args)
        except sqlite3.IntegrityError:
            check(True)
        else:
            raise AssertionError("Schema accepted invalid synthetic row")

    def transaction(ident, amount="1.20", scale=2, date="2026-10-31", category=None):
        db.execute("INSERT INTO transactions(id,revision,kind,operation_date,amount,currency,scale,category_id,note,source,created_at,updated_at) VALUES(?,1,?,?,?,?,?,?,'','manual','2026-11-01T00:00:00Z','2026-11-01T00:00:00Z')",
                   (ident, "expense", date, amount, "KZT", scale, category))

    def candidate(ident):
        db.execute("INSERT INTO import_candidates(id,revision,status,source,source_label,received_at,note,confidence) VALUES(?,1,'needs_review','receipt','Чек','2026-11-01T00:00:00Z','','uncertain')", (ident,))

    def observation(ident, candidate_id, revision):
        db.execute("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,captured_at) VALUES(?,?,'receipt','event','',?,?,'2026-11-01T00:00:00Z')",
                   (ident, candidate_id, "a" * 64, revision))

    try:
        db = sqlite3.connect(database_path)
        db.execute("PRAGMA foreign_keys=ON")
        # Same statement boundaries used by Android Database.onCreate, including triggers.
        for chunk in schema.split("-- @statement"):
            statement = "\n".join(line for line in chunk.splitlines() if not line.strip().startswith("--")).strip()
            if statement:
                db.execute(statement)
        check(db.execute("PRAGMA foreign_keys").fetchone()[0] == 1)
        check(db.execute("SELECT initialized,primary_currency,zone_id,date_policy FROM settings").fetchone() == (0, None, None, None))
        check(db.execute("SELECT count(*) FROM transactions").fetchone()[0] == 0)
        check(db.execute("SELECT count(*) FROM selected_sources").fetchone()[0] == 0)
        reject("UPDATE settings SET initialized=1 WHERE singleton=?", (1,))
        for amount in ["0.00", "-1.00", "+1.00", "01.00", "1.200", "1.0", "1", "1e2", "NaN", "1 000.00", "1000000000000000000000000.00", "1.2.0"]:
            try:
                transaction("invalid-amount", amount)
            except sqlite3.IntegrityError:
                check(True)
            else:
                raise AssertionError("Invalid canonical amount accepted")
        for date in ["2026-02-29", "1900-02-29", "2026-04-31", "0000-01-01", "2026-13-01", "2026-2-01"]:
            try:
                transaction("invalid-date", date=date)
            except sqlite3.IntegrityError:
                check(True)
            else:
                raise AssertionError("Invalid calendar date accepted")
        for index, date in enumerate(["2024-02-29", "2000-02-29", "0001-01-01", "9999-12-31"]):
            transaction("date-" + str(index), date=date)
            check(True)
        db.execute("INSERT INTO categories VALUES('category',1,'Food','expense',0)")
        transaction("stable", category="category")
        reject("UPDATE categories SET kind='income' WHERE id=?", ("category",))
        reject("UPDATE categories SET deleted=1 WHERE id=?", ("category",))
        reject("UPDATE transactions SET category_id=? WHERE id=?", ("missing", "stable"))
        check(db.execute("UPDATE transactions SET note='stale',revision=revision+1 WHERE id='stable' AND revision=99").rowcount == 0)
        check(db.execute("UPDATE transactions SET note='saved',revision=revision+1 WHERE id='stable' AND revision=1").rowcount == 1)
        before = db.execute("SELECT id,amount,operation_date FROM transactions WHERE id='stable'").fetchone()
        db.execute("UPDATE transactions SET category_id=NULL,revision=revision+1 WHERE category_id='category'")
        db.execute("UPDATE categories SET deleted=1,revision=revision+1 WHERE id='category'")
        check(db.execute("SELECT id,amount,operation_date FROM transactions WHERE id='stable'").fetchone() == before)
        check(db.execute("SELECT category_id,revision FROM transactions WHERE id='stable'").fetchone() == (None, 3))
        transaction("max", "999999999999999999999999.99")
        candidate("c1")
        observation("s1", "c1", "b" * 64)
        candidate("c2")
        reject("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,captured_at) VALUES(?,?,'receipt','event','',?,?,'2026-11-01T00:00:00Z')",
               ("s2", "c2", "a" * 64, "b" * 64))
        observation("s2", "c2", "c" * 64)
        check(db.execute("SELECT count(*) FROM source_observations").fetchone()[0] == 2)
        candidate("c3")
        db.execute("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,captured_at) VALUES('s3','c3','receipt','event','',NULL,?,'2026-11-01T00:00:00Z')", ("e" * 64,))
        candidate("c4")
        reject("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,captured_at) VALUES('s4','c4','receipt','event','',NULL,?,'2026-11-01T00:00:00Z')", ("e" * 64,))
        db.execute("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,captured_at) VALUES('s4','c4','receipt','event','',NULL,?,'2026-11-01T00:00:00Z')", ("f" * 64,))
        check(db.execute("SELECT count(*) FROM source_observations").fetchone()[0] == 4)
        db.execute("UPDATE transactions SET deleted=1 WHERE id='stable'")
        check(db.execute("SELECT id FROM transactions WHERE id='stable' AND deleted=1").fetchone() == ("stable",))
        check(db.execute("PRAGMA foreign_key_check").fetchall() == [])
        db.commit()
        try:
            db.execute("BEGIN")
            transaction("rollback")
            candidate("rollback-candidate")
            observation("rollback-source", "rollback-candidate", "d" * 64)
            raise RuntimeError("Synthetic interruption")
        except RuntimeError:
            db.rollback()
        check(db.execute("SELECT count(*) FROM transactions WHERE id='rollback'").fetchone()[0] == 0)
        check(db.execute("SELECT count(*) FROM import_candidates WHERE id='rollback-candidate'").fetchone()[0] == 0)
        check(db.execute("SELECT count(*) FROM source_observations WHERE id='rollback-source'").fetchone()[0] == 0)
        db.close()
        db = sqlite3.connect(database_path)
        check(db.execute("SELECT amount,typeof(amount),scale FROM transactions WHERE id='max'").fetchone() == ("999999999999999999999999.99", "text", 2))
        check(db.execute("SELECT count(*) FROM source_observations").fetchone()[0] == 4)
        check(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok")
        print(json.dumps({"sqlite_version": sqlite3.sqlite_version, "schema_checks_passed": checks,
                          "file_reopen_exact_text": True, "atomic_rollback": True,
                          "scope": "SQLite schema only; Android LedgerStore runtime not exercised"}))
    finally:
        if db is not None:
            db.close()
        if database_path.exists():
            database_path.unlink()


if __name__ == "__main__":
    run()
