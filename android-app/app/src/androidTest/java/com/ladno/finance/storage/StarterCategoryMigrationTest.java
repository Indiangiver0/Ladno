package com.ladno.finance.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Real Android SQLiteOpenHelper create/upgrade, isolated test-only named databases. */
@RunWith(AndroidJUnit4.class)
public final class StarterCategoryMigrationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void freshSeedIsOnceAndPreservesUserEdits() throws Throwable {
        Evidence evidence = new Evidence("fresh");
        String name = "qa-starter-fresh-" + UUID.randomUUID() + ".db";
        try {
            String deleted, renamed;
            try (LedgerStore.Database helper = new LedgerStore.Database(context, name)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                evidence.equal("schema version", 2L, (long) db.getVersion());
                evidence.equal("starter total", 15L, count(db, "SELECT count(*) FROM categories"));
                evidence.equal("expense starters", 11L, count(db, "SELECT count(*) FROM categories WHERE kind='expense'"));
                evidence.equal("income starters", 4L, count(db, "SELECT count(*) FROM categories WHERE kind='income'"));
                evidence.equal("ledger remains empty", 0L, count(db, "SELECT count(*) FROM transactions"));
                evidence.equal("setup remains explicit", 0L, count(db, "SELECT initialized FROM settings"));
                deleted = string(db, "SELECT id FROM categories WHERE name='Продукты'");
                renamed = string(db, "SELECT id FROM categories WHERE name='Дом'");
                evidence.truth("stable UUID format", deleted.matches("[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
                db.execSQL("UPDATE categories SET deleted=1,revision=2 WHERE id=?", new Object[]{deleted});
                db.execSQL("UPDATE categories SET name='Моя квартира',revision=3 WHERE id=?", new Object[]{renamed});
            }
            try (LedgerStore.Database reopened = new LedgerStore.Database(context, name)) {
                SQLiteDatabase db = reopened.getWritableDatabase();
                evidence.equal("reopen total unchanged", 15L, count(db, "SELECT count(*) FROM categories"));
                evidence.equal("deleted starter stays deleted", 1L, count(db, "SELECT deleted FROM categories WHERE id=?", deleted));
                evidence.equal("deleted revision preserved", 2L, count(db, "SELECT revision FROM categories WHERE id=?", deleted));
                evidence.equal("renamed starter preserved", "Моя квартира", string(db, "SELECT name FROM categories WHERE id=?", renamed));
                evidence.equal("rename revision preserved", 3L, count(db, "SELECT revision FROM categories WHERE id=?", renamed));
                evidence.equal("old name not recreated", 0L, count(db, "SELECT count(*) FROM categories WHERE name='Дом'"));
            }
            String another = "qa-starter-stable-" + UUID.randomUUID() + ".db";
            try (LedgerStore.Database helper = new LedgerStore.Database(context, another)) {
                evidence.equal("stable IDs across installs", deleted, string(helper.getWritableDatabase(), "SELECT id FROM categories WHERE name='Продукты'"));
            } finally { context.deleteDatabase(another); }
        } catch (Throwable failure) { evidence.error = failure.toString(); throw failure; }
        finally { evidence.write(); context.deleteDatabase(name); }
    }

    @Test public void populatedV1UpgradePreservesLedgerAndTombstones() throws Throwable {
        Evidence evidence = new Evidence("upgrade");
        String name = "qa-starter-upgrade-" + UUID.randomUUID() + ".db";
        String product = "11111111-1111-4111-8111-111111111111";
        String transport = "22222222-2222-4222-8222-222222222222";
        String salary = "33333333-3333-4333-8333-333333333333";
        String custom = "44444444-4444-4444-8444-444444444444";
        String transaction = "55555555-5555-4555-8555-555555555555";
        String candidate = "66666666-6666-4666-8666-666666666666";
        String observation = "77777777-7777-4777-8777-777777777777";
        JSONObject original = new JSONObject();
        try {
            try (SQLiteDatabase db = v1(name)) {
                db.execSQL("UPDATE settings SET initialized=1,primary_currency='KZT',zone_id='Asia/Qyzylorda',date_policy='POST_TIME_IN_ZONE'");
                category(db, product, 7, "   ПРОДУКТЫ\u00a0 ", "expense", 0);
                category(db, transport, 4, "Транспорт", "expense", 1);
                category(db, salary, 8, "Зарплата", "income", 0);
                category(db, custom, 5, "Моя категория", "expense", 0);
                category(db, "88888888-8888-4888-8888-888888888888", 3, "Зарплата", "expense", 0);
                db.execSQL("INSERT INTO transactions(id,revision,kind,operation_date,amount,currency,scale,category_id,note,source,created_at,updated_at,deleted) VALUES(?,9,'expense','2026-11-03','999999999999999999999999.99','KZT',2,?,'existing note','receipt','2026-11-03T00:00:00Z','2026-11-03T00:00:00Z',0)", new Object[]{transaction, custom});
                db.execSQL("INSERT INTO transactions(id,revision,kind,operation_date,amount,currency,scale,category_id,note,source,created_at,updated_at,deleted) VALUES('99999999-9999-4999-8999-999999999999',12,'income','2026-10-31','0.01','USD',2,NULL,'deleted record','notification','2026-10-31T00:00:00Z','2026-11-01T00:00:00Z',1)");
                db.execSQL("INSERT INTO import_candidates(id,revision,status,source,source_label,received_at,kind,amount,currency,scale,operation_date,date_origin,note,confidence,transaction_id) VALUES(?,6,'posted','receipt','Чек','2026-11-03T00:00:00Z','expense','999999999999999999999999.99','KZT',2,'2026-11-03','source','','high',?)", new Object[]{candidate, transaction});
                String token = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                db.execSQL("INSERT INTO source_observations(id,candidate_id,source_kind,event_id,provider_package,source_key_token,revision_token,account_surrogate,payment_ref_token,captured_at,post_time) VALUES(?,?,'receipt','old-event','com.ladno.qa.bank',?,?,NULL,NULL,'2026-11-03T00:00:00Z',1793664000000)", new Object[]{observation, candidate, token, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"});
                db.execSQL("INSERT INTO candidate_reasons(candidate_id,position,reason) VALUES(?,0,'LOW_CONFIDENCE')", new Object[]{candidate});
                db.execSQL("INSERT INTO candidate_matches(candidate_id,transaction_id) VALUES(?,?)", new Object[]{candidate, transaction});
                db.execSQL("INSERT INTO selected_sources(package_name) VALUES('com.ladno.qa.bank')");
                for (String table : new String[]{"settings", "transactions", "import_candidates", "source_observations", "candidate_reasons", "candidate_matches", "selected_sources"}) {
                    original.put(table, snapshot(db, table));
                }
                original.put("categories", snapshot(db, "categories"));
                evidence.equal("v1 version before", 1L, (long) db.getVersion());
            }
            String after;
            try (LedgerStore.Database helper = new LedgerStore.Database(context, name)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                evidence.equal("upgraded version", 2L, (long) db.getVersion());
                for (String table : new String[]{"settings", "transactions", "import_candidates", "source_observations", "candidate_reasons", "candidate_matches", "selected_sources"}) {
                    evidence.equal("preserved " + table, original.getString(table), snapshot(db, table));
                }
                evidence.equal("only missing starter names added", 17L, count(db, "SELECT count(*) FROM categories"));
                evidence.equal("existing product not duplicated", 0L, count(db, "SELECT count(*) FROM categories WHERE name='Продукты'"));
                evidence.equal("product custom name preserved", "   ПРОДУКТЫ\u00a0 ", string(db, "SELECT name FROM categories WHERE id=?", product));
                evidence.equal("product revision preserved", 7L, count(db, "SELECT revision FROM categories WHERE id=?", product));
                evidence.equal("transport not resurrected", 1L, count(db, "SELECT deleted FROM categories WHERE id=?", transport));
                evidence.equal("transport not duplicated", 1L, count(db, "SELECT count(*) FROM categories WHERE name='Транспорт'"));
                evidence.equal("salary existing income kept", 1L, count(db, "SELECT count(*) FROM categories WHERE name='Зарплата' AND kind='income'"));
                evidence.equal("salary different expense kept", 1L, count(db, "SELECT count(*) FROM categories WHERE name='Зарплата' AND kind='expense'"));
                evidence.equal("custom category revision kept", 5L, count(db, "SELECT revision FROM categories WHERE id=?", custom));
                evidence.equal("money remains TEXT", "text", string(db, "SELECT typeof(amount) FROM transactions WHERE id=?", transaction));
                evidence.equal("foreign key integrity", "[]", snapshotQuery(db, "PRAGMA foreign_key_check"));
                after = snapshot(db, "categories");
            }
            try (LedgerStore.Database reopened = new LedgerStore.Database(context, name)) {
                evidence.equal("repeat reopen exactly unchanged", after, snapshot(reopened.getWritableDatabase(), "categories"));
            }
        } catch (Throwable failure) { evidence.error = failure.toString(); throw failure; }
        finally { evidence.write(); context.deleteDatabase(name); }
    }

    @Test public void emptyV1GetsAllStartersWithoutSetup() throws Throwable {
        Evidence evidence = new Evidence("empty-upgrade");
        String name = "qa-starter-empty-" + UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase ignored = v1(name)) { }
            try (LedgerStore.Database helper = new LedgerStore.Database(context, name)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                evidence.equal("empty upgrade version", 2L, (long) db.getVersion());
                evidence.equal("empty upgrade starters", 15L, count(db, "SELECT count(*) FROM categories"));
                evidence.equal("no financial records invented", 0L, count(db, "SELECT count(*) FROM transactions"));
                evidence.equal("currency not guessed", 0L, count(db, "SELECT initialized FROM settings"));
            }
        } catch (Throwable failure) { evidence.error = failure.toString(); throw failure; }
        finally { evidence.write(); context.deleteDatabase(name); }
    }

    private SQLiteDatabase v1(String name) throws Exception {
        SQLiteDatabase db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        db.setForeignKeyConstraintsEnabled(true);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(context.getAssets().open("db/schema.sql"), StandardCharsets.UTF_8))) {
            StringBuilder sql = new StringBuilder(); String line;
            while ((line = input.readLine()) != null) {
                if (line.equals("-- @statement")) { if (!sql.toString().trim().isEmpty()) db.execSQL(sql.toString()); sql.setLength(0); }
                else if (!line.trim().startsWith("--")) sql.append(line).append('\n');
            }
            if (!sql.toString().trim().isEmpty()) db.execSQL(sql.toString());
        }
        db.setVersion(1);
        return db;
    }

    private static void category(SQLiteDatabase db, String id, int revision, String name, String kind, int deleted) {
        db.execSQL("INSERT INTO categories(id,revision,name,kind,deleted) VALUES(?,?,?,?,?)", new Object[]{id, revision, name, kind, deleted});
    }
    private static long count(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) { assertTrue(cursor.moveToFirst()); return cursor.getLong(0); }
    }
    private static String string(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }
    private static String snapshot(SQLiteDatabase db, String table) throws Exception { return snapshotQuery(db, "SELECT * FROM " + table + " ORDER BY rowid"); }
    private static String snapshotQuery(SQLiteDatabase db, String sql) throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                JSONArray row = new JSONArray();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    JSONObject cell = new JSONObject(); cell.put("type", cursor.getType(i));
                    cell.put("value", cursor.isNull(i) ? JSONObject.NULL : cursor.getString(i)); row.put(cell);
                }
                rows.put(row);
            }
        }
        return rows.toString();
    }
    private final class Evidence {
        final String phase; final JSONArray checks = new JSONArray(); String error;
        Evidence(String phase) { this.phase = phase; }
        void equal(String id, Object expected, Object actual) throws Exception {
            boolean pass = expected.equals(actual);
            JSONObject check = new JSONObject(); check.put("id", id); check.put("expected", expected); check.put("actual", actual); check.put("pass", pass); checks.put(check);
            assertEquals(id, expected, actual);
        }
        void truth(String id, boolean actual) throws Exception { equal(id, true, actual); }
        void write() throws Exception {
            JSONObject report = new JSONObject(); report.put("phase", phase); report.put("assertions", checks); report.put("error", error == null ? JSONObject.NULL : error);
            try (FileOutputStream stream = context.openFileOutput("qa-starter-" + phase + ".json", Context.MODE_PRIVATE)) {
                stream.write((report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
            }
        }
    }
}
