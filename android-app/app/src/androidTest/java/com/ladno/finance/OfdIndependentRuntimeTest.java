package com.ladno.finance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.Money;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Independent synthetic file->native client->registered internal job->SQLite checks. */
@RunWith(AndroidJUnit4.class)
public final class OfdIndependentRuntimeTest {
    @Test public void verifiedSalesAndOriginal03DecisionsShareTheSameObservation() throws Throwable {
        Assert.assertEquals("Ladno_QA_API30", InstrumentationRegistry.getArguments().getString("ladnoSyntheticAvd"));
        Assert.assertTrue("Only the dedicated synthetic emulator", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("app017-ofd-independent")) {
            NativeRuntime runtime = NativeRuntime.get(f.context);
            NativeRuntimeFixtures.setup(runtime.store);
            try (SyntheticProvider remote = new SyntheticProvider(runtime)) {
                try {
                    String nonce = Long.toString(System.currentTimeMillis());
                    Fixture fresh = new Fixture(nonce + "1", "9007" + nonce + ".01");
                    remote.allow(fresh);
                    int baseline = count(f, "transactions WHERE deleted=0");
                    JSONObject first = scan(f, runtime, fresh, "new verified sale");
                    f.equal("verified sale job completes", "completed", first.getString("status"));
                    f.equal("verified sale posts", "posted", first.getJSONObject("outcome").getString("status"));
                    String newCandidate = first.getJSONObject("outcome").getString("candidateId");
                    JSONObject row = candidate(f, newCandidate);
                    f.equal("Android exact decimal survives above double range", fresh.amount, row.getString("amount"));
                    f.equal("sale KZT", "KZT", row.getString("currency"));
                    f.equal("sale source day", "2026-11-03", row.getString("operation_date"));
                    f.equal("one sale expense", baseline + 1, count(f, "transactions WHERE deleted=0"));
                    JSONObject repeated = scan(f, runtime, fresh, "new photo same verified sale");
                    f.equal("new photo is exact duplicate", "duplicate", repeated.getJSONObject("outcome").getString("status"));
                    f.equal("new photo same candidate", newCandidate, repeated.getJSONObject("outcome").getString("candidateId"));
                    f.equal("repeat no second expense", baseline + 1, count(f, "transactions WHERE deleted=0"));
                    assertOriginalTokens(f, runtime, fresh, newCandidate);

                    Fixture ignored = new Fixture(nonce + "2", "82" + nonce + ".02"); remote.allow(ignored);
                    JSONObject ignoredSeed = seed(runtime, ignored, local(ignored));
                    runtime.store.execute("reviewCandidate", json("id", ignoredSeed.getString("id"), "expectedRevision", ignoredSeed.getLong("revision"), "decision", "ignore"));
                    int ignoredCount = count(f, "import_candidates");
                    JSONObject ignoredReplay = scan(f, runtime, ignored, "legacy ignored then API");
                    f.equal("ignored legacy outcome duplicate", "duplicate", ignoredReplay.getJSONObject("outcome").getString("status"));
                    f.equal("ignored legacy same candidate", ignoredSeed.getString("id"), ignoredReplay.getJSONObject("outcome").getString("candidateId"));
                    f.equal("ignored decision persists", "ignored", candidate(f, ignoredSeed.getString("id")).getString("status"));
                    f.equal("ignored no new candidate", ignoredCount, count(f, "import_candidates"));
                    f.equal("ignored no new expense", baseline + 1, count(f, "transactions WHERE deleted=0"));

                    Fixture edited = new Fixture(nonce + "3", "83" + nonce + ".03"); remote.allow(edited);
                    JSONObject editSeed = seed(runtime, edited, local(edited));
                    JSONObject posted = runtime.store.execute("reviewCandidate", json("id", editSeed.getString("id"), "expectedRevision", editSeed.getLong("revision"), "decision", "post", "confirmDistinct", true,
                        "transaction", transaction(edited.amount, "2026-11-03", "original synthetic"))).getJSONObject("record");
                    String changedAmount = Money.formatMinor(Money.parseCanonical(edited.amount, "KZT", 2).minor.add(BigInteger.valueOf(71)), 2);
                    JSONObject edit = transaction(changedAmount, "2026-10-01", "retain this manual correction");
                    edit.put("id", posted.getString("id")).put("expectedRevision", posted.getLong("revision"));
                    JSONObject changed = runtime.store.execute("saveTransaction", edit).getJSONObject("record");
                    int editedCount = count(f, "transactions WHERE deleted=0");
                    JSONObject editReplay = scan(f, runtime, edited, "legacy user-edited post then API");
                    f.equal("edited legacy replay is duplicate", "duplicate", editReplay.getJSONObject("outcome").getString("status"));
                    JSONObject retained = record(f, changed.getString("id"));
                    f.equal("manual amount retained", changedAmount, retained.getString("amount"));
                    f.equal("manual date retained outside weak-match month", "2026-10-01", retained.getString("operation_date"));
                    f.equal("manual note retained", "retain this manual correction", retained.getString("note"));
                    f.equal("manual record revision retained", changed.getLong("revision"), retained.getLong("revision"));
                    f.equal("manual replay no second expense", editedCount, count(f, "transactions WHERE deleted=0"));

                    Fixture pending = new Fixture(nonce + "4", "84" + nonce + ".04"); remote.allow(pending);
                    JSONObject pendingSeed = seed(runtime, pending, local(pending));
                    int pendingRows = count(f, "import_candidates");
                    JSONObject pendingReplay = scan(f, runtime, pending, "legacy fallback pending then success");
                    f.equal("pending same candidate", pendingSeed.getString("id"), pendingReplay.getJSONObject("outcome").getString("candidateId"));
                    f.equal("pending not silently promoted", "needs_review", candidate(f, pendingSeed.getString("id")).getString("status"));
                    f.equal("pending no new row", pendingRows, count(f, "import_candidates"));
                    f.equal("pending no extra expense", editedCount, count(f, "transactions WHERE deleted=0"));

                    Fixture linked = new Fixture(nonce + "5", "85" + nonce + ".05"); remote.allow(linked);
                    JSONObject linkedSeed = seed(runtime, linked, local(linked));
                    JSONObject manualInput = transaction(linked.amount, "2026-10-02", "existing synthetic link");
                    manualInput.put("id", JSONObject.NULL).put("expectedRevision", JSONObject.NULL);
                    JSONObject manual = runtime.store.execute("saveTransaction", manualInput).getJSONObject("record");
                    runtime.store.execute("reviewCandidate", json("id", linkedSeed.getString("id"), "expectedRevision", linkedSeed.getLong("revision"), "decision", "link", "transactionId", manual.getString("id")));
                    int linkCount = count(f, "transactions WHERE deleted=0");
                    scan(f, runtime, linked, "legacy linked then API");
                    JSONObject keptLink = candidate(f, linkedSeed.getString("id"));
                    f.equal("linked decision retained", "linked", keptLink.getString("status"));
                    f.equal("linked target retained", manual.getString("id"), keptLink.getString("transaction_id"));
                    f.equal("link no extra expense", linkCount, count(f, "transactions WHERE deleted=0"));

                    Fixture offline = new Fixture(nonce + "6", "86" + nonce + ".06");
                    int offlineCount = count(f, "transactions WHERE deleted=0"), offlineRows = count(f, "import_candidates");
                    JSONObject fallback = scan(f, runtime, offline, "synthetic unavailable network");
                    JSONObject offlineCandidate = candidate(f, fallback.getJSONObject("outcome").getString("candidateId"));
                    f.equal("failure keeps local sum", offline.amount, offlineCandidate.getString("amount"));
                    f.truth("failure has unknown direction", offlineCandidate.isNull("kind"));
                    f.equal("failure is review", "needs_review", offlineCandidate.getString("status"));
                    f.equal("failure one useful candidate", offlineRows + 1, count(f, "import_candidates"));
                    f.equal("failure no automatic expense", offlineCount, count(f, "transactions WHERE deleted=0"));

                    crossSource(f, runtime, remote, new Fixture(nonce + "7", "87" + nonce + ".07"), "notification");
                    crossSource(f, runtime, remote, new Fixture(nonce + "8", "88" + nonce + ".08"), "receipt");

                    String transactionId = first.getJSONObject("outcome").getString("transactionId");
                    JSONObject originalRecord = record(f, transactionId);
                    runtime.store.execute("deleteTransaction", json("id", transactionId, "expectedRevision", originalRecord.getLong("revision")));
                    int afterDelete = count(f, "transactions WHERE deleted=0");
                    scan(f, runtime, fresh, "deleted verified transaction replay");
                    f.equal("deleted transaction not resurrected", 1, record(f, transactionId).getInt("deleted"));
                    f.equal("deleted replay no expense restored", afterDelete, count(f, "transactions WHERE deleted=0"));
                    f.truth("only synthetic transport factories used", remote.attempts.get() >= 10);
                    f.truth("synthetic raw request not persisted", !NativeRuntimeFixtures.databaseContains(f.context, fresh.url()));
                    f.observe("scope", "Synthetic PNG -> native QR scanner -> fixed client with injected fake HTTPS connection -> strict parser -> registered internal job -> SQLite. Original 0.3 HMAC pair seeded literally. No real HTTP or user data, no Activity/queueAction/camera UI, no actual bank notification delivery.");
                } catch (Throwable error) { f.failure(error); throw error; }
            }
        }
    }

    private static void crossSource(NativeRuntimeFixtures f, NativeRuntime runtime, SyntheticProvider remote, Fixture fixture, String sourceKind) throws Exception {
        JSONArray old = NativeRuntimeFixtures.selected(runtime.store);
        String provider = sourceKind.equals("notification") ? "com.ladno.synthetic.bank" : null;
        if (provider != null) NativeRuntimeFixtures.select(runtime.store, NativeRuntimeFixtures.withPackage(old, provider));
        try {
            JSONObject source = json("kind", sourceKind, "eventId", UUID.randomUUID().toString(),
                "sourceKeyToken", runtime.tokens.hmac("receipt-source", "independent-cross-source", fixture.sign),
                "revisionToken", runtime.tokens.hmac("receipt-revision", "independent-cross-source", fixture.sign),
                "accountSurrogate", null, "paymentRefToken", null, "packageName", provider,
                "label", sourceKind.equals("notification") ? "Уведомление" : "Чек", "capturedAt", Instant.now().toString(), "postTime", null);
            ImportCandidate prior = new ImportCandidate("expense", fixture.amount, "KZT", "2026-11-03", "source", "", Collections.emptyList(), "high", null);
            JSONObject first = runtime.store.ingestCandidate(source, prior);
            f.equal(sourceKind + "/synthetic existing event posted", "posted", first.getString("outcome"));
            int records = count(f, "transactions WHERE deleted=0"); remote.allow(fixture);
            JSONObject qr = scan(f, runtime, fixture, sourceKind + " followed by same purchase QR");
            JSONObject candidate = candidate(f, qr.getJSONObject("outcome").getString("candidateId"));
            f.equal(sourceKind + "/weak match review", "needs_review", candidate.getString("status"));
            f.equal(sourceKind + "/no automatic second expense", records, count(f, "transactions WHERE deleted=0"));
        } finally { if (provider != null) NativeRuntimeFixtures.select(runtime.store, old); }
    }

    private static ImportCandidate local(Fixture f) {
        return new ImportCandidate(null, f.amount, "KZT", "2026-11-03", "source", "",
            Arrays.asList("UNKNOWN_KIND", "LOW_CONFIDENCE", "IDENTITY_UNCERTAIN", "OFD_UNAVAILABLE"), "uncertain", null);
    }
    private static JSONObject seed(NativeRuntime runtime, Fixture f, ImportCandidate value) throws Exception {
        return runtime.store.ingestCandidate(json("kind", "receipt", "eventId", UUID.randomUUID().toString(),
            "sourceKeyToken", runtime.tokens.hmac("receipt-source", "receipt-fiscal-qr-v1", f.locator()),
            "revisionToken", runtime.tokens.hmac("receipt-revision", "receipt-fiscal-qr-v1", f.locator(), f.revision()),
            "accountSurrogate", null, "paymentRefToken", null, "packageName", null, "label", "Чек",
            "capturedAt", Instant.now().toString(), "postTime", null), value).getJSONObject("candidate");
    }
    private static void assertOriginalTokens(NativeRuntimeFixtures f, NativeRuntime runtime, Fixture input, String id) throws Exception {
        try (SQLiteDatabase db = database(f); Cursor row = db.rawQuery("SELECT source_key_token,revision_token FROM source_observations WHERE candidate_id=?", new String[]{id})) {
            f.truth("observation tokens exist", row.moveToFirst());
            f.equal("literal 0.3 source token unchanged", runtime.tokens.hmac("receipt-source", "receipt-fiscal-qr-v1", input.locator()), row.getString(0));
            f.equal("literal 0.3 revision token unchanged", runtime.tokens.hmac("receipt-revision", "receipt-fiscal-qr-v1", input.locator(), input.revision()), row.getString(1));
        }
    }
    private static JSONObject transaction(String amount, String date, String note) throws Exception {
        return json("kind", "expense", "amount", amount, "currency", "KZT", "date", date, "categoryId", null, "note", note);
    }
    private static SQLiteDatabase database(NativeRuntimeFixtures f) {
        return SQLiteDatabase.openDatabase(f.context.getDatabasePath("ledger.db").getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }
    private static int count(NativeRuntimeFixtures f, String clause) {
        // Clauses are fixed test-owned literals, never source-authored input.
        if (!Arrays.asList("transactions WHERE deleted=0", "import_candidates").contains(clause)) throw new AssertionError();
        try (SQLiteDatabase db = database(f); Cursor row = db.rawQuery("SELECT COUNT(*) FROM " + clause, null)) { row.moveToFirst(); return row.getInt(0); }
    }
    private static JSONObject candidate(NativeRuntimeFixtures f, String id) throws Exception { return row(f, "import_candidates", id); }
    private static JSONObject record(NativeRuntimeFixtures f, String id) throws Exception { return row(f, "transactions", id); }
    private static JSONObject row(NativeRuntimeFixtures f, String table, String id) throws Exception {
        try (SQLiteDatabase db = database(f); Cursor c = db.rawQuery("SELECT * FROM " + table + " WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) throw new AssertionError("Expected synthetic row missing");
            JSONObject row = new JSONObject(); for (int i = 0; i < c.getColumnCount(); i++) {
                Object value = c.isNull(i) ? JSONObject.NULL : c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i);
                row.put(c.getColumnName(i), value);
            } return row;
        }
    }
    private static JSONObject json(Object... fields) throws Exception { return NativeRuntimeFixtures.json(fields); }

    private static JSONObject scan(NativeRuntimeFixtures f, NativeRuntime runtime, Fixture input, String label) throws Exception {
        File file = image(runtime, input.url());
        String id;
        synchronized (runtime) {
            Assert.assertNull(runtime.mediaJob());
            Class<?> type = Class.forName("com.ladno.finance.NativeRuntime$Job");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class); constructor.setAccessible(true);
            Object job = constructor.newInstance("scanCamera"); Field key = type.getDeclaredField("id"); key.setAccessible(true); id = (String) key.get(job);
            Field jobs = NativeRuntime.class.getDeclaredField("jobs"); jobs.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) jobs.get(runtime); map.put(id, job);
            Field active = NativeRuntime.class.getDeclaredField("activeMedia"); active.setAccessible(true); active.set(runtime, id); runtime.cameraOutput(file);
        }
        Method running = NativeRuntime.class.getDeclaredMethod("running", String.class); running.setAccessible(true); running.invoke(runtime, id);
        runtime.processMedia(id, null, file);
        final String jobId = id;
        f.await(label + "/registered terminal job", 90000, () -> {
            String status = find(runtime, jobId).getString("status"); return status.equals("completed") || status.equals("failed");
        });
        f.await(label + "/private input cleanup", 10000, () -> !file.exists());
        JSONObject result = find(runtime, id); f.observe(label + "/job", result);
        f.equal(label + "/completed", "completed", result.getString("status"));
        return result;
    }
    private static JSONObject find(NativeRuntime runtime, String id) throws Exception {
        JSONArray jobs = runtime.state("2026-11", "KZT").getJSONArray("jobs");
        for (int i = 0; i < jobs.length(); i++) if (id.equals(jobs.getJSONObject(i).getString("jobId"))) return jobs.getJSONObject(i);
        throw new AssertionError("Registered internal job missing");
    }
    private static File image(NativeRuntime runtime, String payload) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.MARGIN, 4);
        BitMatrix code = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720, hints);
        Bitmap bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888);
        try {
            for (int y = 0; y < 720; y++) for (int x = 0; x < 720; x++) bitmap.setPixel(x, y, code.get(x, y) ? Color.BLACK : Color.WHITE);
            File file = runtime.newCameraFile(); try (FileOutputStream stream = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new AssertionError("PNG encoding unavailable");
            } return file;
        } finally { bitmap.recycle(); }
    }

    private static final class Fixture {
        final String sign, amount;
        Fixture(String sign, String amount) { this.sign = sign; this.amount = amount; }
        String url() { return "https://consumer.oofd.kz/?i=" + sign + "&f=000000000111&s=" + amount + "&t=20261103T102030"; }
        String locator() { return "consumer.oofd.kz\n" + sign + "\n000000000111"; }
        String revision() { return amount + "\n20261103T102030"; }
        String body() {
            return "{\"kkmFnsId\":\"000000000111\",\"ticket\":{\"type\":\"TICKET\",\"fiscalId\":\"" + sign
                + "\",\"offlineFiscalId\":null,\"cancelDate\":null,\"transactionDate\":\"2026-11-03T10:20:30.000\",\"totalSum\":" + amount
                + ",\"operationType\":2,\"payments\":[{\"paymentType\":\"CARD\",\"sum\":" + amount
                + "}],\"items\":[{\"itemType\":1,\"commodity\":{\"name\":\"Synthetic commodity\"}}]}}";
        }
    }
    private static final class SyntheticProvider implements AutoCloseable {
        final AtomicInteger attempts = new AtomicInteger();
        final Map<String, Fixture> allowed = new ConcurrentHashMap<>();
        final NativeRuntime runtime; final Field field; final Object previous;
        SyntheticProvider(NativeRuntime runtime) throws Exception {
            this.runtime = runtime; field = NativeRuntime.class.getDeclaredField("ofd"); field.setAccessible(true); previous = field.get(runtime);
            field.set(runtime, new OfdReceiptClient(url -> {
                attempts.incrementAndGet();
                if (!url.getProtocol().equals("https") || !url.getHost().equals("consumer.oofd.kz")
                    || !url.getPath().equals("/api/consumer-proxy/api/tickets/get-by-url")) throw new IOException("Synthetic endpoint mismatch");
                Map<String, String> query = new HashMap<>(); for (String part : url.getQuery().split("&")) { String[] kv = part.split("=", 2); query.put(kv[0], kv[1]); }
                Fixture input = allowed.get(query.get("i"));
                if (input == null) throw new IOException("Synthetic unavailable; no connection opened");
                return new FakeConnection(url, input.body());
            }, 1500));
        }
        void allow(Fixture fixture) { allowed.put(fixture.sign, fixture); }
        @Override public void close() throws Exception { field.set(runtime, previous); }
    }
    private static final class FakeConnection extends HttpsURLConnection {
        final byte[] bytes;
        FakeConnection(URL url, String body) { super(url); bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public int getResponseCode() { return 200; }
        @Override public String getContentType() { return "application/json"; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public long getContentLengthLong() { return bytes.length; }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public String getCipherSuite() { return "synthetic"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
    }
}
