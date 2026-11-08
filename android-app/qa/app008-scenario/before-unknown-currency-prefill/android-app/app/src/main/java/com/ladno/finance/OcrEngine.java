package com.ladno.finance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.ladno.finance.core.ContractException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/** All methods touching TessBaseAPI are called only by NativeRuntime's single OCR worker. */
final class OcrEngine {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PIXELS = 32_000_000L;
    private static final String[] LANGUAGES = {"eng", "rus", "kaz"};
    private static final String[] BEST_HASHES = {
        "8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba",
        "b617eb6830ffabaaa795dd87ea7fd251adfe9cf0efe05eb9a2e8128b7728d6b6",
        "34cbd9204b1ff3cc813d50b29e3c0ae3752bcc201f261a4a393ceca3895aea9d"};
    private static final String[] FAST_HASHES = {
        "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225",
        "fcc01eed3815a42b9c6321c4c9d3606f39b166cbf95ade98b7d8d12063eae53d"};
    private final Context context;
    private TessBaseAPI tess;
    private boolean activeBest;
    private boolean modelsPrepared;
    private volatile boolean ready;
    private volatile JSONObject error;

    OcrEngine(Context context) { this.context = context.getApplicationContext(); }

    JSONObject state() {
        return NativeJson.object("ready", ready, "languages", new JSONArray(java.util.Arrays.asList(LANGUAGES)), "error", error);
    }

    void initialize() {
        if (ready && tess != null && activeBest) return;
        try {
            if (!modelsPrepared) {
                prepareModels(true); prepareModels(false); modelsPrepared = true;
            }
            activate(true);
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            unavailable();
            throw new ContractException("OCR_UNAVAILABLE", null, "Модуль распознавания недоступен.");
        }
    }

    private void prepareModels(boolean best) throws IOException {
        File directory = new File(modelDirectory(best), "tessdata");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
        String[] hashes = best ? BEST_HASHES : FAST_HASHES;
        for (int i = 0; i < LANGUAGES.length; i++) copyModel(directory, best ? "tessdata-best" : "tessdata", LANGUAGES[i], hashes[i]);
    }

    private File modelDirectory(boolean best) {
        return new File(context.getNoBackupFilesDir(), best ? "tesseract-best" : "tesseract");
    }

    private void activate(boolean best) throws IOException {
        if (tess != null && activeBest == best) return;
        // At most one resident native network: release before creating the next.
        discardEngine();
        TessBaseAPI created = null;
        try {
            created = new TessBaseAPI();
            if (!created.init(modelDirectory(best).getAbsolutePath(), "eng+rus+kaz", TessBaseAPI.OEM_LSTM_ONLY)) throw new IOException();
            created.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            tess = created;
            activeBest = best;
            ready = true;
            error = null;
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            if (created != null) created.recycle();
            unavailable();
            if (e instanceof IOException) throw (IOException) e;
            throw e;
        }
    }

    private void copyModel(File directory, String assets, String language, String expectedHash) throws IOException {
        File destination = new File(directory, language + ".traineddata");
        if (destination.isFile() && expectedHash.equals(hash(destination))) return;
        File temporary = new File(directory, language + ".copy");
        try {
            long count = 0;
            try (InputStream input = context.getAssets().open(assets + "/" + language + ".traineddata");
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > 100L * 1024 * 1024) throw new IOException();
                    output.write(buffer, 0, read);
                }
                if (count < 100000) throw new IOException();
                output.getFD().sync();
            }
            if (!expectedHash.equals(hash(temporary))) throw new IOException("Model integrity check failed");
            if (!temporary.renameTo(destination)) throw new IOException();
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static String hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    Result recognize(File file) {
        initialize();
        Bitmap bitmap = null;
        try {
            if (file.length() == 0) throw invalid();
            if (file.length() > MAX_BYTES) throw limit();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null || !bounds.outMimeType.startsWith("image/")) throw invalid();
            if ((long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) throw limit();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = 1;
            while ((Math.max(bounds.outWidth, bounds.outHeight) + options.inSampleSize - 1L) / options.inSampleSize > 4096
                || ((bounds.outWidth + options.inSampleSize - 1L) / options.inSampleSize)
                    * ((bounds.outHeight + options.inSampleSize - 1L) / options.inSampleSize) > 8_000_000L)
                options.inSampleSize *= 2;
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) throw invalid();
            if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > 4096) throw limit();
            bitmap = orient(file, bitmap);
            long start = android.os.SystemClock.elapsedRealtime();
            tess.setImage(bitmap);
            String text = tess.getUTF8Text();
            if (text == null || text.trim().isEmpty()) throw new ContractException("IMAGE_INVALID", null, "Текст на изображении не распознан.");
            if (text.length() > 200000) throw limit();
            int confidence = tess.meanConfidence();
            long primaryMillis = android.os.SystemClock.elapsedRealtime() - start;
            tess.clear();
            String verificationText = null;
            int verificationConfidence = -1;
            boolean verificationFailed = false;
            start = android.os.SystemClock.elapsedRealtime();
            try {
                activate(false);
                tess.setImage(bitmap);
                verificationText = tess.getUTF8Text();
                if (verificationText == null || verificationText.trim().isEmpty() || verificationText.length() > 200000)
                    throw new IOException("Verification unavailable");
                verificationConfidence = tess.meanConfidence();
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                // The primary result remains editable, but cannot be auto-posted.
                verificationText = null;
                verificationConfidence = -1;
                verificationFailed = true;
                unavailable();
            }
            return new Result(text, confidence, verificationText, verificationConfidence, verificationFailed,
                primaryMillis, android.os.SystemClock.elapsedRealtime() - start);
        } catch (ContractException e) { throw e; }
        catch (OutOfMemoryError e) { throw new ContractException("LIMIT_EXCEEDED", null, "Недостаточно памяти для изображения."); }
        catch (Exception e) { throw new ContractException("OCR_UNAVAILABLE", null, "Не удалось распознать изображение."); }
        finally {
            if (tess != null) tess.clear();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private Bitmap orient(File file, Bitmap bitmap) throws IOException {
        int orientation;
        try (FileInputStream input = new FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(input);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            // A successfully decoded image can have no EXIF support on an older OS (for example PNG).
            return bitmap;
        }
        Matrix transform = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: transform.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: transform.setRotate(90); transform.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: transform.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: transform.setRotate(270); transform.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: transform.setRotate(270); break;
            default: return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    void release() {
        discardEngine();
    }

    private void unavailable() {
        discardEngine();
        error = NativeJson.error("OCR_UNAVAILABLE", null, "Модуль распознавания недоступен. Перезапустите приложение и попробуйте снова.");
    }

    private void discardEngine() {
        ready = false;
        if (tess != null) {
            tess.recycle();
            tess = null;
        }
    }

    private ContractException invalid() { return new ContractException("IMAGE_INVALID", null, "Не удалось прочитать изображение."); }
    private ContractException limit() { return new ContractException("LIMIT_EXCEEDED", null, "Изображение превышает допустимый размер."); }

    static final class Result {
        final String text;
        final int confidence;
        final String verificationText;
        final int verificationConfidence;
        final boolean verificationFailed;
        final long primaryMillis, verificationMillis;
        Result(String text, int confidence, String verificationText, int verificationConfidence,
                boolean verificationFailed, long primaryMillis, long verificationMillis) {
            this.text = text; this.confidence = confidence;
            this.verificationText = verificationText; this.verificationConfidence = verificationConfidence;
            this.verificationFailed = verificationFailed;
            this.primaryMillis = primaryMillis; this.verificationMillis = verificationMillis;
        }
    }
}
