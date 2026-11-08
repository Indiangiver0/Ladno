package com.ladno.finance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.ReceiptParser;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.UUID;

/** Independent synthetic images. Does not launch an Activity or replace camera/permission gates. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public final class ReceiptLayoutRuntimeTest {
    @Test public void syntheticMultilineAndFiscalImages() throws Throwable {
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("receipt-layout-runtime")) {
            try {
            File directory = new File(f.context.getCacheDir(), "qa-layout-fixtures");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Fixture directory unavailable");
            Fixture[] inputs = {
                new Fixture("en-multiline-paid", "754.32", true, false,
                    new String[]{"PAID", "TOTAL", "754.32 KZT", "2026-11-03"}),
                new Fixture("ru-fiscal-card", "8765.43", true, false,
                    new String[]{"ФИСКАЛЬНЫЙ ЧЕК", "ПРИХОД", "ИТОГО", "8765.43 KZT", "Картой 8765.43 KZT", "2026-11-03"}),
                new Fixture("ru-unpaid", "99.85", false, false,
                    new String[]{"Не оплачено", "ИТОГО", "99.85 KZT", "2026-11-03"}),
                new Fixture("en-agreement-paid", "123.45", true, true,
                    new String[]{"PAID", "TOTAL 123.45 KZT", "2026-11-03"})
            };
            OcrEngine engine = new OcrEngine(f.context);
            try {
                engine.initialize();
                f.truth("real Tesseract initialized", engine.state().getBoolean("ready"));
                for (Fixture input : inputs) {
                    input.file = image(directory, input);
                    if (input.id.equals("en-multiline-paid")) f.equal("original counterexample PNG unchanged",
                        "03d1e86729936c8be09a39af09eedba6f6d2aece07ff97196edce4a09ae7af2d", digest(input.file));
                    OcrEngine.Result ocr = engine.recognize(input.file);
                    ImportCandidate parsed = ReceiptParser.parse(ocr.text);
                    ImportCandidate verification = ReceiptParser.parse(ocr.verificationText);
                    f.observe(input.id + "/synthetic OCR", NativeRuntimeFixtures.json("lines", new org.json.JSONArray(java.util.Arrays.asList(input.lines)),
                        "actualText", ocr.text, "meanConfidence", ocr.confidence, "parsed", parsed.toJson(),
                        "verificationText", ocr.verificationText, "verificationConfidence", ocr.verificationConfidence,
                        "verificationFailed", ocr.verificationFailed, "verificationParsed", verification.toJson(),
                        "primaryMillis", ocr.primaryMillis, "verificationMillis", ocr.verificationMillis));
                    f.equal(input.id + "/amount", input.amount, parsed.amount);
                    f.equal(input.id + "/currency", "KZT", parsed.currency);
                    f.equal(input.id + "/date", "2026-11-03", parsed.date);
                    f.equal(input.id + "/completed-paid evidence", input.paid, "high".equals(parsed.confidence));
                    // Expected status follows the approved two-pass contract, independently
                    // of the production consensus helper. Wrong values can never be accepted.
                    input.eligible = input.paid && ocr.confidence >= 80 && !ocr.verificationFailed
                        && ocr.verificationConfidence >= 80 && "high".equals(parsed.confidence)
                        && parsed.reasons.isEmpty() && "high".equals(verification.confidence) && verification.reasons.isEmpty()
                        && "expense".equals(verification.kind) && input.amount.equals(verification.amount)
                        && "KZT".equals(verification.currency) && "2026-11-03".equals(verification.date);
                    if (input.mustAuto) f.truth(input.id + "/known agreement proves automatic path", input.eligible);
                    if (input.id.equals("en-multiline-paid")) {
                        f.equal("counterexample verifier retains alternative794.32", "794.32", verification.amount);
                        f.equal("counterexample disagreement disallows auto", false, input.eligible);
                    }
                }
            } catch (Throwable error) { f.failure(error); throw error; }
            finally { engine.release(); }

            NativeRuntime runtime = NativeRuntime.get(f.context);
            NativeRuntimeFixtures.setup(runtime.store);
            try {
                f.await("runtime OCR initialized", 90000, () -> runtime.state("2026-11", "KZT").getJSONObject("ocr").optBoolean("ready"));
                for (Fixture input : inputs) {
                    String token = runtime.tokens.hmac("receipt-source", "receipt-image-v1", digest(input.file));
                    JSONObject previous = NativeRuntimeFixtures.receiptObservation(f.context, token);
                    int before = NativeRuntimeFixtures.records(runtime.store);
                    int wrongBefore = wrongAmountRecords(runtime);
                    File capture = runtime.newCameraFile(); copy(input.file, capture);
                    runtime.processMedia("qa-layout-unregistered-" + UUID.randomUUID(), null, capture);
                    f.await(input.id + "/private image cleaned", 90000, () -> !capture.exists());
                    JSONObject observed = NativeRuntimeFixtures.receiptObservation(f.context, token);
                    f.truth(input.id + "/native candidate exists", observed != null);
                    f.observe(input.id + "/native committed", observed);
                    f.equal(input.id + "/native amount", input.amount, observed.getString("amount"));
                    f.equal(input.id + "/native currency", "KZT", observed.getString("currency"));
                    f.equal(input.id + "/native date", "2026-11-03", observed.getString("operation_date"));
                    f.equal(input.id + "/native status", input.eligible ? "posted" : "needs_review", observed.getString("status"));
                    f.equal(input.id + "/ledger delta", before + (input.eligible && previous == null ? 1 : 0), NativeRuntimeFixtures.records(runtime.store));
                    f.equal(input.id + "/no794.32 receipt autoexpense", wrongBefore, wrongAmountRecords(runtime));
                    if (input.paid && !input.eligible) f.truth(input.id + "/disagreement explains review",
                        NativeRuntimeFixtures.has(observed.getJSONArray("reasons"), "LOW_CONFIDENCE"));
                }
                f.observe("scope", "Synthetic clear PNGs only; actual photographed paper, MainActivity, camera chooser/grants, registered job lifecycle and device UI were not exercised.");
            } catch (Throwable error) { f.failure(error); throw error; }
            finally { for (Fixture input : inputs) if (input.file != null) input.file.delete(); }
            } finally {
                f.evidence.put("finishedAt", java.time.Instant.now().toString());
                try (FileOutputStream out = f.context.openFileOutput("qa-app008-receipt-layout.json", android.content.Context.MODE_PRIVATE)) {
                    out.write((f.evidence.toString(2) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.getFD().sync();
                }
            }
        }
    }

    private static File image(File directory, Fixture input) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(2100, 1300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(72);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        for (int i = 0; i < input.lines.length; i++) canvas.drawText(input.lines[i], 100, 150 + i * 170, paint);
        File file = new File(directory, input.id + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG unavailable");
        } finally { bitmap.recycle(); }
        return file;
    }
    private static void copy(File source, File target) throws Exception {
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }
    private static String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder text = new StringBuilder(); for (byte b : digest.digest()) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return text.toString();
    }
    private static int wrongAmountRecords(NativeRuntime runtime) throws Exception {
        org.json.JSONArray records = runtime.store.state("2026-11", "KZT").getJSONArray("records");
        int count = 0;
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.getJSONObject(i);
            if ("receipt".equals(row.getString("source")) && "794.32".equals(row.getString("amount"))) count++;
        }
        return count;
    }
    private static final class Fixture {
        final String id, amount; final boolean paid, mustAuto; final String[] lines; File file; boolean eligible;
        Fixture(String id, String amount, boolean paid, boolean mustAuto, String[] lines) { this.id=id; this.amount=amount; this.paid=paid; this.mustAuto=mustAuto; this.lines=lines; }
    }
}
