package com.ladno.finance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.ReceiptParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Author diagnostic on the unchanged independent synthetic PNG; not a product acceptance test. */
@RunWith(AndroidJUnit4.class)
public final class AuthorOcrDiagnosticTest {
    @Test public void compareSegmentationOnUnchangedFixture() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File image = new File(context.getCacheDir(), "qa-layout-fixtures/en-multiline-paid.png");
        JSONObject evidence = NativeJson.object("scope", "author diagnostic; synthetic fixture only; no UI or ledger mutation",
            "startedAt", java.time.Instant.now().toString(), "fixture", image.getName(),
            "expectedAmount", "754.32", "variants", new JSONArray());
        Bitmap bitmap = null;
        TessBaseAPI api = null;
        try {
            assertTrue("Original independent PNG must already exist", image.isFile());
            String before = digest(image);
            NativeJson.put(evidence, "originalSha256", before);
            bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
            assertNotNull(bitmap);
            assertEquals(2100, bitmap.getWidth()); assertEquals(1300, bitmap.getHeight());
            NativeJson.put(evidence, "width", bitmap.getWidth()); NativeJson.put(evidence, "height", bitmap.getHeight());
            OcrEngine preparation = new OcrEngine(context);
            try { preparation.initialize(); } finally { preparation.release(); }
            api = new TessBaseAPI();
            assertTrue(api.init(new File(context.getNoBackupFilesDir(), "tesseract").getAbsolutePath(),
                "eng+rus+kaz", TessBaseAPI.OEM_LSTM_ONLY));
            int[] modes = {TessBaseAPI.PageSegMode.PSM_AUTO,
                TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT};
            for (int mode : modes) {
                try {
                    api.setPageSegMode(mode);
                    api.setImage(bitmap);
                    String text = api.getUTF8Text();
                    int confidence = api.meanConfidence();
                    ImportCandidate parsed = ReceiptParser.parse(text);
                    evidence.getJSONArray("variants").put(NativeJson.object("languages", "eng+rus+kaz", "psm", mode,
                        "text", text, "meanConfidence", confidence, "parsed", parsed.toJson(),
                        "matchesExpectedAmount", "754.32".equals(parsed.amount)));
                } finally { api.clear(); }
            }
            assertEquals("Diagnostic must not modify independent input bytes", before, digest(image));
            NativeJson.put(evidence, "finalSha256", digest(image));
        } catch (Throwable error) {
            NativeJson.put(evidence, "failure", error.getClass().getName() + ": " + error.getMessage());
            throw error;
        } finally {
            if (api != null) {
                try { api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK); } finally { api.recycle(); }
            }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            NativeJson.put(evidence, "finishedAt", java.time.Instant.now().toString());
            try (FileOutputStream out = context.openFileOutput("qa-app008-ocr-diagnostic.json", Context.MODE_PRIVATE)) {
                out.write((evidence.toString(2) + "\n").getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
            }
        }
    }

    private static String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
