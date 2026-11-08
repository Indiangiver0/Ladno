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

/** All methods touching TessBaseAPI are called only by NativeRuntime's single OCR worker. */
final class OcrEngine {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PIXELS = 32_000_000L;
    private static final String[] LANGUAGES = {"eng", "rus", "kaz"};
    private final Context context;
    private TessBaseAPI tess;
    private volatile boolean ready;
    private volatile JSONObject error;

    OcrEngine(Context context) { this.context = context.getApplicationContext(); }

    JSONObject state() {
        return NativeJson.object("ready", ready, "languages", new JSONArray(java.util.Arrays.asList(LANGUAGES)), "error", error);
    }

    void initialize() {
        if (ready && tess != null) return;
        TessBaseAPI created = null;
        try {
            File parent = new File(context.getNoBackupFilesDir(), "tesseract");
            File data = new File(parent, "tessdata");
            if (!data.isDirectory() && !data.mkdirs()) throw new IOException();
            for (String language : LANGUAGES) copyModel(data, language);
            created = new TessBaseAPI();
            if (!created.init(parent.getAbsolutePath(), "eng+rus+kaz", TessBaseAPI.OEM_LSTM_ONLY)) throw new IOException();
            tess = created;
            ready = true;
            error = null;
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            if (created != null) created.recycle();
            tess = null;
            ready = false;
            error = NativeJson.error("OCR_UNAVAILABLE", null, "Модуль распознавания недоступен.");
            throw new ContractException("OCR_UNAVAILABLE", null, "Модуль распознавания недоступен.");
        }
    }

    private void copyModel(File directory, String language) throws IOException {
        File destination = new File(directory, language + ".traineddata");
        File temporary = new File(directory, language + ".copy");
        try {
            long count = 0;
            try (InputStream input = context.getAssets().open("tessdata/" + language + ".traineddata");
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
            if (!temporary.renameTo(destination)) throw new IOException();
        } finally {
            if (temporary.exists()) temporary.delete();
        }
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
            tess.setImage(bitmap);
            String text = tess.getUTF8Text();
            if (text == null || text.trim().isEmpty()) throw new ContractException("IMAGE_INVALID", null, "Текст на изображении не распознан.");
            if (text.length() > 200000) throw limit();
            return new Result(text, tess.meanConfidence());
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
        Result(String text, int confidence) { this.text = text; this.confidence = confidence; }
    }
}
