package com.ladno.finance;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.ladno.finance.core.ContractException;
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

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public final class OcrRuntimeTest {
    /** Root: fresh instrumentation process, real NLS grant revoked, no Activity started. */
    @Test public void realTesseractAndNativeMediaPipeline() throws Throwable {
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("ocr-runtime")) {
            NotificationManager manager = f.context.getSystemService(NotificationManager.class);
            f.equal("OCR class real NLS grant revoked", false,
                manager.isNotificationListenerAccessGranted(new ComponentName(f.context, BankNotificationService.class)));
            File directory = new File(f.context.getCacheDir(), "qa-receipt-fixtures");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Fixture directory unavailable");
            Fixture[] fixtures = {
                new Fixture("en-paid", true, "123.45", "PAID", "TOTAL 123.45 KZT"),
                new Fixture("en-unpaid", false, "123.45", "UNPAID", "TOTAL 123.45 KZT"),
                new Fixture("ru-paid", true, "234.56", "Оплачено", "ИТОГО 234.56 KZT"),
                new Fixture("ru-unpaid", false, "234.56", "Не оплачено", "ИТОГО 234.56 KZT"),
                new Fixture("kz-paid", true, "345.67", "Төленген", "БАРЛЫҒЫ 345.67 KZT"),
                new Fixture("kz-unpaid", false, "345.67", "Төленген жоқ", "БАРЛЫҒЫ 345.67 KZT")
            };
            OcrEngine engine = new OcrEngine(f.context);
            File invalid = new File(directory, "invalid-synthetic.txt");
            try {
                engine.initialize();
                f.truth("actual Tesseract JNI ready", engine.state().getBoolean("ready"));
                f.equal("bundled language state", "[\"eng\",\"rus\",\"kaz\"]", engine.state().getJSONArray("languages").toString());
                for (Fixture input : fixtures) {
                    input.file = image(directory, input);
                    OcrEngine.Result recognized = engine.recognize(input.file);
                    ImportCandidate parsed = ReceiptParser.parse(recognized.text);
                    f.observe(input.id + "/direct actual OCR", NativeRuntimeFixtures.json("syntheticInput", input.description(), "text", recognized.text,
                        "meanConfidence", recognized.confidence, "candidate", parsed.toJson()));
                    f.truth(input.id + "/nonempty actual OCR", !recognized.text.trim().isEmpty());
                    f.truth(input.id + "/confidence bounds", recognized.confidence >= 0 && recognized.confidence <= 100);
                    if (!input.paid) f.truth(input.id + "/unpaid must not be high", !"high".equals(parsed.confidence));
                    if ("high".equals(parsed.confidence)) {
                        f.equal(input.id + "/high amount matches independent input", input.amount, parsed.amount);
                        f.equal(input.id + "/high currency", "KZT", parsed.currency);
                        f.equal(input.id + "/high date", NativeRuntimeFixtures.DATE, parsed.date);
                    }
                }
                try (FileOutputStream out = new FileOutputStream(invalid)) { out.write(new byte[]{1, 2, 3, 4}); }
                try { engine.recognize(invalid); f.truth("non-image rejected", false); }
                catch (ContractException expected) { f.equal("non-image safe code", "IMAGE_INVALID", expected.code); }
            } catch (Throwable error) { f.failure(error); throw error; }
            finally { engine.release(); }

            // Sequential initialization prevents concurrent writes of shared model .copy files.
            NativeRuntime runtime = NativeRuntime.get(f.context);
            NativeRuntimeFixtures.setup(runtime.store);
            try {
                f.await("singleton actual OCR ready", 90000, () -> runtime.state(NativeRuntimeFixtures.MONTH, "KZT").getJSONObject("ocr").optBoolean("ready"));
                for (Fixture input : fixtures) {
                    String token = runtime.tokens.hmac("receipt-source", "receipt-image-v1", digest(input.file));
                    JSONObject previous = NativeRuntimeFixtures.receiptObservation(f.context, token);
                    int recordsBefore = NativeRuntimeFixtures.records(runtime.store);
                    File capture = runtime.newCameraFile(); copy(input.file, capture);
                    runtime.processMedia("qa-unregistered-" + UUID.randomUUID(), null, capture);
                    f.await(input.id + "/native private image cleanup", 90000, () -> !capture.exists());
                    JSONObject observed = NativeRuntimeFixtures.receiptObservation(f.context, token);
                    f.truth(input.id + "/native observation committed", observed != null);
                    f.observe(input.id + "/actual native outcome", NativeRuntimeFixtures.json("expectedImage", input.description(), "previousObservation", previous, "actualObservation", observed));
                    String status = observed.getString("status");
                    if (!input.paid) f.equal(input.id + "/unpaid native review", "needs_review", status);
                    if ("posted".equals(status)) {
                        f.equal(input.id + "/posted amount", input.amount, observed.getString("amount"));
                        f.equal(input.id + "/posted currency", "KZT", observed.getString("currency"));
                        f.equal(input.id + "/posted date", NativeRuntimeFixtures.DATE, observed.getString("operation_date"));
                        f.equal(input.id + "/posted kind", "expense", observed.getString("kind"));
                        f.equal(input.id + "/ledger delta", recordsBefore + (previous == null ? 1 : 0), NativeRuntimeFixtures.records(runtime.store));
                    } else {
                        f.equal(input.id + "/safe non-posted status", "needs_review", status);
                        f.truth(input.id + "/review explains uncertainty", observed.getJSONArray("reasons").length() > 0);
                        f.equal(input.id + "/review excludes ledger totals", recordsBefore, NativeRuntimeFixtures.records(runtime.store));
                    }
                }
                Fixture repeated = fixtures[0];
                String source = runtime.tokens.hmac("receipt-source", "receipt-image-v1", digest(repeated.file));
                JSONObject original = NativeRuntimeFixtures.receiptObservation(f.context, source);
                int before = NativeRuntimeFixtures.records(runtime.store), reviewsBefore = NativeRuntimeFixtures.reviews(runtime.store);
                File replay = runtime.newCameraFile(); copy(repeated.file, replay);
                runtime.processMedia("qa-unregistered-replay-" + UUID.randomUUID(), null, replay);
                f.await("identical image processed and cleaned", 90000, () -> !replay.exists());
                JSONObject replayed = NativeRuntimeFixtures.receiptObservation(f.context, source);
                f.equal("identical image same candidate", original.getString("id"), replayed.getString("id"));
                f.equal("identical image no second ledger record", before, NativeRuntimeFixtures.records(runtime.store));
                f.equal("identical image no second pending review", reviewsBefore, NativeRuntimeFixtures.reviews(runtime.store));
                File badCapture = runtime.newCameraFile(); copy(invalid, badCapture);
                String badToken = runtime.tokens.hmac("receipt-source", "receipt-image-v1", digest(badCapture));
                runtime.processMedia("qa-unregistered-invalid-" + UUID.randomUUID(), null, badCapture);
                f.await("invalid image cleanup", 90000, () -> !badCapture.exists());
                f.equal("invalid image no candidate", null, NativeRuntimeFixtures.receiptObservation(f.context, badToken));
                f.equal("invalid image no ledger change", before, NativeRuntimeFixtures.records(runtime.store));
                f.equal("invalid image no pending review", reviewsBefore, NativeRuntimeFixtures.reviews(runtime.store));
                f.observe("explicit execution limits", "Native API/image path only; arbitrary test job IDs do not register Activity jobs. Camera/gallery chooser/grants, UI job lifecycle and OCR accuracy on real photographs were not exercised.");
            } catch (Throwable error) { f.failure(error); throw error; }
        }
    }

    private static File image(File directory, Fixture input) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1900, 850, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(76);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String[] lines = {input.payment, input.total, NativeRuntimeFixtures.DATE};
        for (int i = 0; i < lines.length; i++) canvas.drawText(lines[i], 100, 180 + i * 180, paint);
        File file = new File(directory, input.id + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("Synthetic PNG unavailable");
        } finally { bitmap.recycle(); }
        return file;
    }

    private static void copy(File source, File target) throws Exception {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return SourceTokens.hex(digest.digest());
    }

    private static final class Fixture {
        final String id, amount, payment, total;
        final boolean paid;
        File file;
        Fixture(String id, boolean paid, String amount, String payment, String total) {
            this.id = id; this.paid = paid; this.amount = amount; this.payment = payment; this.total = total;
        }
        JSONObject description() throws Exception {
            return NativeRuntimeFixtures.json("id", id, "paid", paid, "amount", amount, "currency", "KZT", "date", NativeRuntimeFixtures.DATE,
                "paymentLine", payment, "totalLine", total, "image", file == null ? null : file.getName());
        }
    }
}
