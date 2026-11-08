package com.ladno.finance;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ladno.finance.storage.LedgerStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Test-only synthetic inputs and evidence. Never replaces a production permission gate. */
final class NativeRuntimeFixtures implements AutoCloseable {
    static final String MONTH = "2026-11", DATE = "2026-11-02", CURRENCY = "KZT";
    final Context context;
    final JSONObject evidence;
    private final JSONArray checks = new JSONArray(), observations = new JSONArray();
    private final File output;
    private final String suite;

    NativeRuntimeFixtures(String name) throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        suite = name;
        File directory = new File(context.getFilesDir(), "qa-android-scenario");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("QA evidence directory unavailable");
        output = new File(directory, name + ".json");
        evidence = json("suite", name, "startedAt", Instant.now().toString(), "pid", Process.myPid(),
            "apiLevel", Build.VERSION.SDK_INT, "abis", new JSONArray(java.util.Arrays.asList(Build.SUPPORTED_ABIS)),
            "package", context.getPackageName(), "data", "synthetic-only", "checks", checks, "observations", observations);
        save();
    }

    static JSONObject json(Object... pairs) throws Exception {
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
        return result;
    }

    void equal(String id, Object expected, Object actual) throws Exception {
        boolean pass = Objects.equals(expected, actual);
        checks.put(json("id", id, "expected", expected, "actual", actual, "pass", pass));
        save();
        Bundle status = new Bundle();
        status.putString("stream", "LadnoQA " + suite + " / " + id + ": " + (pass ? "PASS" : "FAIL") + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
        Assert.assertEquals(id, expected, actual);
    }

    void truth(String id, boolean actual) throws Exception { equal(id, true, actual); }
    void observe(String id, Object actual) throws Exception { observations.put(json("id", id, "actual", actual)); save(); }
    void failure(Throwable error) throws Exception { evidence.put("failure", error.getClass().getName() + ": " + error.getMessage()); save(); }

    interface Probe { boolean ready() throws Exception; }
    void await(String id, long timeoutMillis, Probe probe) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (probe.ready()) { truth(id, true); return; }
            SystemClock.sleep(100);
        }
        truth(id + "/deadline", false);
    }

    static void setup(LedgerStore store) throws Exception {
        store.execute("settings", json("primaryCurrency", CURRENCY, "zoneId", "Asia/Qyzylorda", "datePolicy", "POST_TIME_IN_ZONE"));
    }

    static void select(LedgerStore store, JSONArray packages) throws Exception { store.execute("saveSources", json("packages", packages)); }
    static JSONArray selected(LedgerStore store) throws Exception { return new JSONArray(store.state(MONTH, CURRENCY).getJSONArray("sources").toString()); }
    static JSONArray withPackage(JSONArray original, String packageName) {
        JSONArray result = new JSONArray(); boolean found = false;
        for (int i = 0; i < original.length(); i++) { String value = original.optString(i); result.put(value); found |= packageName.equals(value); }
        if (!found) result.put(packageName);
        return result;
    }
    static int records(LedgerStore store) throws Exception { return store.state(MONTH, CURRENCY).getJSONArray("records").length(); }
    static int reviews(LedgerStore store) throws Exception { return store.state(MONTH, CURRENCY).getJSONArray("imports").length(); }
    static String expense(LedgerStore store) throws Exception { return store.state(MONTH, CURRENCY).getJSONObject("summary").getString("expense"); }
    static String uniqueAmount(String prefix, String cents) { return prefix + (System.currentTimeMillis() % 1000000L) + "." + cents; }

    static JSONObject record(LedgerStore store, String amount, String kind) throws Exception {
        JSONArray rows = store.state(MONTH, CURRENCY).getJSONArray("records");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (amount.equals(row.optString("amount")) && kind.equals(row.optString("kind")) && "notification".equals(row.optString("source"))) return row;
        }
        return null;
    }

    static JSONObject review(LedgerStore store, String amount, String reason) throws Exception {
        JSONArray rows = store.state(MONTH, CURRENCY).getJSONArray("imports");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if ((amount == null || amount.equals(row.optString("amount"))) && has(row.optJSONArray("reasons"), reason)) return row;
        }
        return null;
    }

    static boolean has(JSONArray values, String expected) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) if (expected.equals(values.optString(i))) return true;
        return false;
    }

    static JSONObject source(String provider, SourceTokens.NotificationTokens tokens, long postTime) throws Exception {
        return json("kind", "notification", "eventId", UUID.randomUUID().toString(), "sourceKeyToken", tokens.sourceKeyToken,
            "revisionToken", tokens.revisionToken, "accountSurrogate", null, "paymentRefToken", null, "packageName", provider,
            "label", "Уведомление", "capturedAt", Instant.now().toString(), "postTime", postTime);
    }

    static JSONObject receiptObservation(Context context, String sourceToken) throws Exception {
        File path = context.getDatabasePath("ledger.db");
        if (!path.isFile()) return null;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT c.id,c.status,c.kind,c.amount,c.currency,c.operation_date,c.confidence,c.transaction_id FROM source_observations s JOIN import_candidates c ON c.id=s.candidate_id WHERE s.source_kind='receipt' AND s.source_key_token=? ORDER BY s.captured_at DESC LIMIT 1", new String[]{sourceToken})) {
            if (!cursor.moveToFirst()) return null;
            JSONObject result = new JSONObject();
            for (int i = 0; i < cursor.getColumnCount(); i++) result.put(cursor.getColumnName(i), cursor.isNull(i) ? JSONObject.NULL : cursor.getString(i));
            JSONArray reasons = new JSONArray();
            try (Cursor rows = db.rawQuery("SELECT reason FROM candidate_reasons WHERE candidate_id=? ORDER BY position", new String[]{result.getString("id")})) {
                while (rows.moveToNext()) reasons.put(rows.getString(0));
            }
            result.put("reasons", reasons);
            return result;
        }
    }

    static boolean databaseContains(Context context, String marker) {
        File path = context.getDatabasePath("ledger.db");
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)) {
            while (names.moveToNext()) {
                String name = names.getString(0);
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalStateException("Unexpected test schema identifier");
                try (Cursor cells = db.rawQuery("SELECT * FROM \"" + name + "\"", null)) {
                    while (cells.moveToNext()) for (int i = 0; i < cells.getColumnCount(); i++)
                        if (!cells.isNull(i) && cells.getType(i) != Cursor.FIELD_TYPE_BLOB && cells.getString(i).contains(marker)) return true;
                }
            }
            return false;
        }
    }

    private void save() throws Exception {
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(evidence.toString(2).getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
        }
    }

    @Override public void close() throws Exception { evidence.put("finishedAt", Instant.now().toString()); save(); }
}
