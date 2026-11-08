package com.ladno.finance;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Synthetic native API integration; no Activity, camera chooser, or permission UI. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public final class QrImportRuntimeTest {
    @Test public void registeredSyntheticQrJobsAndNotificationFields() throws Throwable {
        Assert.assertEquals("Ladno_QA_API30", InstrumentationRegistry.getArguments().getString("ladnoSyntheticAvd"));
        Assert.assertTrue("Only the dedicated synthetic emulator may run this suite",
            "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("app013-qr-runtime")) {
            try {
                NativeRuntime runtime = NativeRuntime.get(f.context);
                NativeRuntimeFixtures.setup(runtime.store);
                // Version 0.4 has native OFD networking. Synthetic QR IDs MUST
                // never reach that endpoint: install the controlled offline seam
                // before the first processMedia call and restore it afterwards.
                try (ControlledOfflineClient network = new ControlledOfflineClient(runtime)) {
                int recordsBefore = NativeRuntimeFixtures.records(runtime.store);
                String sign = Long.toString(System.currentTimeMillis());
                String qr = "https://consumer.oofd.kz/?i=" + sign + "&f=000000123456&s=1234.50&t=20261104T112233";
                JSONObject first = run(f, runtime, image(runtime, qr), "first");
                f.equal("first registered job status", "completed", first.getString("status"));
                JSONObject outcome = first.getJSONObject("outcome");
                f.equal("first outcome review", "needs_review", outcome.getString("status"));
                f.equal("first outcome month", "2026-11", outcome.getString("month"));
                f.equal("first outcome currency", "KZT", outcome.getString("currency"));
                f.truth("first no transaction ID", outcome.isNull("transactionId"));
                String id = outcome.getString("candidateId");
                JSONObject candidate = candidate(runtime, id);
                f.equal("native amount exact", "1234.50", candidate.getString("amount"));
                f.equal("native date from QR", "2026-11-04", candidate.getString("date"));
                f.truth("native kind unknown", candidate.isNull("kind"));
                f.equal("native confidence uncertain", "uncertain", candidate.getString("confidence"));
                f.truth("native reason unknown kind", NativeRuntimeFixtures.has(candidate.getJSONArray("reasons"), "UNKNOWN_KIND"));
                f.equal("no automatic ledger record", recordsBefore, NativeRuntimeFixtures.records(runtime.store));
                f.truth("raw synthetic QR not persisted", !NativeRuntimeFixtures.databaseContains(f.context, qr));

                JSONObject duplicate = run(f, runtime, image(runtime, qr), "same QR new file");
                f.equal("repeat status", "duplicate", duplicate.getJSONObject("outcome").getString("status"));
                f.equal("repeat candidate preserved", id, duplicate.getJSONObject("outcome").getString("candidateId"));
                candidate = candidate(runtime, id);
                runtime.store.execute("reviewCandidate", NativeRuntimeFixtures.json("id", id,
                    "expectedRevision", candidate.getLong("revision"), "decision", "ignore"));
                int importsAfterIgnore = persistedImports(f);
                long ignoredRevision = candidate.getLong("revision") + 1;
                JSONObject ignored = run(f, runtime, image(runtime, qr), "ignored repeated QR");
                // Contract: an exact source revision is a duplicate observation;
                // the persisted candidate decision remains ignored independently.
                f.equal("ignored exact source repeat outcome", "duplicate", ignored.getJSONObject("outcome").getString("status"));
                f.equal("ignored repeat same candidate", id, ignored.getJSONObject("outcome").getString("candidateId"));
                JSONObject persisted = persistedCandidate(f, id);
                f.equal("ignored persisted decision retained", "ignored", persisted.getString("status"));
                f.equal("ignored persisted revision retained", ignoredRevision, persisted.getLong("revision"));
                f.equal("ignored repeat no new candidate", importsAfterIgnore, persistedImports(f));
                f.equal("ignored repeat no record", recordsBefore, NativeRuntimeFixtures.records(runtime.store));

                rejected(f, runtime, image(runtime, qr.replace("s=1234.50", "s=1234.500")), "QR_AMOUNT_INVALID", "excess precision");
                rejected(f, runtime, image(runtime, qr.replace("20261104T112233", "20260229T112233")), "QR_DATE_INVALID", "invalid date");
                rejected(f, runtime, image(runtime, "https://pay.example/synthetic-only"), "QR_UNSUPPORTED", "payment QR");
                rejected(f, runtime, image(runtime, qr, qr.replace(sign, sign + "1")), "QR_MULTIPLE", "two fiscal QR");
                rejected(f, runtime, image(runtime, qr, "https://pay.example/synthetic-only"), "QR_MULTIPLE", "mixed QR");

                String message = NotificationText.assemble("Пополнение", "Пополнение", null,
                    new CharSequence[]{"Пополнение", "456.78 теңге", "04.11.2026"});
                com.ladno.finance.core.ImportCandidate bank = com.ladno.finance.core.BankMessageParser.parse(message, 0, "Asia/Qyzylorda", false);
                f.equal("actual Android test process notification lines amount", "456.78", bank.amount);
                f.equal("notification lines currency", "KZT", bank.currency);
                f.equal("notification lines direction", "income", bank.kind);
                f.truth("new notification format stays review", !"high".equals(bank.confidence));
                String[] permissions = f.context.getPackageManager().getPackageInfo(f.context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
                f.truth("native OFD INTERNET permission declared", permissions != null && java.util.Arrays.asList(permissions).contains("android.permission.INTERNET"));
                f.truth("controlled offline transport seam was used", network.attempts.get() > 0);
                f.observe("scope", "Synthetic PNG file->decoder->native processMedia->registered Job/state/outcome->SQLite only. The injected Connections seam throws IOException without opening a network connection for fictional IDs. Job inserted by test-only reflection; queueAction/Activity/system camera/gallery UI and real user QR not exercised.");
                }
            } catch (Throwable error) { f.failure(error); throw error; }
        }
    }

    private static void rejected(NativeRuntimeFixtures f, NativeRuntime runtime, File file, String code, String label) throws Exception {
        int before = NativeRuntimeFixtures.reviews(runtime.store), records = NativeRuntimeFixtures.records(runtime.store);
        JSONObject job = run(f, runtime, file, label);
        f.equal(label + "/job failed", "failed", job.getString("status"));
        f.equal(label + "/visible error", code, job.getJSONObject("error").getString("code"));
        f.truth(label + "/no successful outcome", job.isNull("outcome"));
        f.equal(label + "/no empty incoming", before, NativeRuntimeFixtures.reviews(runtime.store));
        f.equal(label + "/no record", records, NativeRuntimeFixtures.records(runtime.store));
    }

    private static JSONObject candidate(NativeRuntime runtime, String id) throws Exception {
        JSONArray imports = runtime.store.state("2026-11", "KZT").getJSONArray("imports");
        for (int i = 0; i < imports.length(); i++) if (id.equals(imports.getJSONObject(i).getString("id"))) return imports.getJSONObject(i);
        throw new AssertionError("Expected QR candidate missing");
    }

    private static JSONObject persistedCandidate(NativeRuntimeFixtures f, String id) throws Exception {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(f.context.getDatabasePath("ledger.db").getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor row = db.rawQuery("SELECT status,revision FROM import_candidates WHERE id=?", new String[]{id})) {
            if (!row.moveToFirst()) throw new AssertionError("Persisted ignored candidate missing");
            return NativeRuntimeFixtures.json("status", row.getString(0), "revision", row.getLong(1));
        }
    }

    private static final class ControlledOfflineClient implements AutoCloseable {
        final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        private final NativeRuntime runtime;
        private final Field field;
        private final Object previous;
        ControlledOfflineClient(NativeRuntime runtime) throws Exception {
            this.runtime = runtime;
            field = NativeRuntime.class.getDeclaredField("ofd"); field.setAccessible(true);
            previous = field.get(runtime);
            field.set(runtime, new OfdReceiptClient(url -> {
                attempts.incrementAndGet();
                throw new java.io.IOException("Synthetic offline test; no connection opened");
            }, 1000));
        }
        @Override public void close() throws Exception { field.set(runtime, previous); }
    }

    private static int persistedImports(NativeRuntimeFixtures f) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(f.context.getDatabasePath("ledger.db").getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor row = db.rawQuery("SELECT COUNT(*) FROM import_candidates", null)) {
            if (!row.moveToFirst()) throw new AssertionError("Candidate count unavailable");
            return row.getInt(0);
        }
    }

    private static JSONObject run(NativeRuntimeFixtures f, NativeRuntime runtime, File file, String label) throws Exception {
        String jobId = register(runtime, file);
        Method running = NativeRuntime.class.getDeclaredMethod("running", String.class);
        running.setAccessible(true);
        running.invoke(runtime, jobId);
        runtime.processMedia(jobId, null, file);
        f.await(label + "/registered completion", 90000, () -> {
            String status = find(runtime, jobId).getString("status");
            return "completed".equals(status) || "failed".equals(status);
        });
        f.await(label + "/owned input cleaned", 10000, () -> !file.exists());
        f.truth(label + "/media slot released", runtime.mediaJob() == null);
        f.truth(label + "/camera output released", runtime.cameraOutput() == null);
        JSONObject value = find(runtime, jobId); f.observe(label + "/job", value); return value;
    }

    private static JSONObject find(NativeRuntime runtime, String id) throws Exception {
        JSONArray jobs = runtime.state("2026-11", "KZT").getJSONArray("jobs");
        for (int i = 0; i < jobs.length(); i++) if (id.equals(jobs.getJSONObject(i).getString("jobId"))) return jobs.getJSONObject(i);
        throw new AssertionError("Registered native job missing");
    }

    @SuppressWarnings("unchecked")
    private static String register(NativeRuntime runtime, File file) throws Exception {
        synchronized (runtime) {
            Assert.assertNull("No concurrent media job", runtime.mediaJob());
            Class<?> type = Class.forName("com.ladno.finance.NativeRuntime$Job");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class); constructor.setAccessible(true);
            Object job = constructor.newInstance("scanCamera");
            Field idField = type.getDeclaredField("id"); idField.setAccessible(true); String id = (String) idField.get(job);
            Field jobsField = NativeRuntime.class.getDeclaredField("jobs"); jobsField.setAccessible(true);
            ((Map<String, Object>) jobsField.get(runtime)).put(id, job);
            Field active = NativeRuntime.class.getDeclaredField("activeMedia"); active.setAccessible(true); active.set(runtime, id);
            runtime.cameraOutput(file); return id;
        }
    }

    private static File image(NativeRuntime runtime, String... payloads) throws Exception {
        int tile = 560, gap = 80, width = tile * payloads.length + gap * (payloads.length + 1), height = tile + gap * 2;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE);
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.MARGIN, 4);
        try {
            for (int index = 0; index < payloads.length; index++) {
                BitMatrix code = new QRCodeWriter().encode(payloads[index], BarcodeFormat.QR_CODE, tile, tile, hints);
                for (int y = 0; y < tile; y++) for (int x = 0; x < tile; x++)
                    if (code.get(x, y)) bitmap.setPixel(gap + index * (tile + gap) + x, gap + y, Color.BLACK);
            }
            File file = runtime.newCameraFile();
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new AssertionError("Synthetic PNG encoding failed");
            }
            return file;
        } finally { bitmap.recycle(); }
    }
}
