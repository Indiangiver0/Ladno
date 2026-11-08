package com.ladno.finance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.ResultIterator;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.ReceiptParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
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
            File models = new File(context.getNoBackupFilesDir(), "tesseract");
            assertTrue(api.init(models.getAbsolutePath(), "eng+rus+kaz", TessBaseAPI.OEM_LSTM_ONLY));
            int[] modes = {TessBaseAPI.PageSegMode.PSM_AUTO,
                TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT};
            for (int mode : modes) {
                record(api, evidence, bitmap, "original", "eng+rus+kaz", mode);
            }
            // Diagnosis only: every output is retained, none is selected as a winner.
            for (String languages : new String[]{"eng", "eng+rus", "rus+eng+kaz", "kaz+rus+eng"}) {
                assertTrue(api.init(models.getAbsolutePath(), languages, TessBaseAPI.OEM_LSTM_ONLY));
                record(api, evidence, bitmap, "original", languages, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            }
            assertTrue(api.init(models.getAbsolutePath(), "eng+rus+kaz", TessBaseAPI.OEM_LSTM_ONLY));
            for (int percent : new int[]{75, 150}) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * percent / 100,
                    bitmap.getHeight() * percent / 100, true);
                try {
                    record(api, evidence, scaled, "scaled-filtered-" + percent,
                        "eng+rus+kaz", TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
                } finally { if (scaled != bitmap) scaled.recycle(); }
            }
            Bitmap binary = threshold(bitmap, 180);
            try {
                record(api, evidence, binary, "binary-threshold-180", "eng+rus+kaz",
                    TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            } finally { binary.recycle(); }
            List<int[]> boxes = numericWordBoxes(api, bitmap);
            NativeJson.put(evidence, "numericWordBoxes", new JSONArray(boxes));
            for (int index = 0; index < boxes.size(); index++) {
                Bitmap word = paddedCrop(bitmap, boxes.get(index));
                try {
                    for (int mode : new int[]{TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, TessBaseAPI.PageSegMode.PSM_RAW_LINE}) {
                        record(api, evidence, word, "fast-numeric-word-" + index, "eng+rus+kaz", mode);
                        assertTrue(api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789.,"));
                        try { record(api, evidence, word, "fast-numeric-whitelist-" + index, "eng+rus+kaz", mode); }
                        finally { api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, ""); }
                    }
                } finally { word.recycle(); }
            }
            File best = copyBestModel(context);
            assertTrue(api.init(best.getAbsolutePath(), "eng", TessBaseAPI.OEM_LSTM_ONLY));
            for (int mode : new int[]{TessBaseAPI.PageSegMode.PSM_AUTO, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK})
                record(api, evidence, bitmap, "best-original", "eng-best", mode);
            for (int index = 0; index < boxes.size(); index++) {
                Bitmap word = paddedCrop(bitmap, boxes.get(index));
                try { record(api, evidence, word, "best-numeric-word-" + index, "eng-best", TessBaseAPI.PageSegMode.PSM_SINGLE_LINE); }
                finally { word.recycle(); }
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

    private static void record(TessBaseAPI api, JSONObject evidence, Bitmap bitmap,
            String imageVariant, String languages, int mode) throws Exception {
        try {
            api.setPageSegMode(mode);
            api.setImage(bitmap);
            String text = api.getUTF8Text();
            int confidence = api.meanConfidence();
            ImportCandidate parsed = ReceiptParser.parse(text);
            evidence.getJSONArray("variants").put(NativeJson.object("languages", languages,
                "imageVariant", imageVariant, "width", bitmap.getWidth(), "height", bitmap.getHeight(),
                "psm", mode, "text", text, "meanConfidence", confidence, "parsed", parsed.toJson(),
                "matchesExpectedAmount", "754.32".equals(parsed.amount)));
        } finally { api.clear(); }
    }

    private static Bitmap threshold(Bitmap source, int level) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        int[] row = new int[source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            source.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int x = 0; x < row.length; x++) {
                int pixel = row[x];
                int gray = (299 * Color.red(pixel) + 587 * Color.green(pixel) + 114 * Color.blue(pixel) + 500) / 1000;
                row[x] = gray < level ? Color.BLACK : Color.WHITE;
            }
            output.setPixels(row, 0, row.length, 0, y, row.length, 1);
        }
        return output;
    }

    private static List<int[]> numericWordBoxes(TessBaseAPI api, Bitmap bitmap) {
        List<int[]> boxes = new ArrayList<>();
        try {
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            api.setImage(bitmap); api.getUTF8Text();
            ResultIterator iterator = api.getResultIterator();
            if (iterator == null) return boxes;
            try {
                iterator.begin();
                do {
                    String word = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD);
                    if (word != null && word.trim().matches("[0-9]{1,24}[.,][0-9]{1,6}")) {
                        int[] box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD);
                        if (box != null && box.length == 4 && box[0] >= 0 && box[1] >= 0
                                && box[2] > box[0] && box[3] > box[1]
                                && box[2] <= bitmap.getWidth() && box[3] <= bitmap.getHeight()) boxes.add(box.clone());
                    }
                } while (boxes.size() < 32 && iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));
            } finally { iterator.delete(); }
            return boxes;
        } finally { api.clear(); }
    }

    private static Bitmap paddedCrop(Bitmap source, int[] box) {
        int border = 20;
        Bitmap output = Bitmap.createBitmap(box[2] - box[0] + border * 2,
            box[3] - box[1] + border * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output); canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(source, new android.graphics.Rect(box[0], box[1], box[2], box[3]),
            new android.graphics.Rect(border, border, output.getWidth() - border, output.getHeight() - border), null);
        return output;
    }

    private static File copyBestModel(Context context) throws Exception {
        File parent = new File(context.getNoBackupFilesDir(), "tesseract-best");
        File directory = new File(parent, "tessdata");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Model directory unavailable");
        File model = new File(directory, "eng.traineddata");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("tessdata-best/eng.traineddata");
             FileOutputStream output = new FileOutputStream(model)) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            output.getFD().sync();
        }
        return parent;
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
