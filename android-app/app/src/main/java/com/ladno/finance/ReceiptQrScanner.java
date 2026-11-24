package com.ladno.finance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

/** Bounded local media decoding. Raw QR content never leaves this native call chain. */
final class ReceiptQrScanner {
    private static final long MAX_SOURCE_PIXELS = 32_000_000L;
    private ReceiptQrScanner() { }

    static FiscalQrParser.Result scan(File input) throws IOException {
        if (input == null || input.length() == 0) throw invalid();
        if (input.length() > OcrEngine.MAX_BYTES) throw limit();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null
            || !bounds.outMimeType.startsWith("image/")) throw invalid();
        if ((long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS) throw limit();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while ((Math.max(bounds.outWidth, bounds.outHeight) + options.inSampleSize - 1L) / options.inSampleSize
                > ReceiptQrDecoder.MAX_DIMENSION
            || ((bounds.outWidth + options.inSampleSize - 1L) / options.inSampleSize)
                * ((bounds.outHeight + options.inSampleSize - 1L) / options.inSampleSize)
                > ReceiptQrDecoder.MAX_DECODE_PIXELS) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeFile(input.getAbsolutePath(), options);
        if (bitmap == null) throw invalid();
        int width, height;
        int[] pixels;
        try {
            bitmap = orient(input, bitmap);
            // The bundled ML Kit model finds small, faint thermal-paper QR codes far more
            // reliably than the pure-Java detector, which remains the offline fallback.
            FiscalQrParser.Result fast = fiscal(mlKit(bitmap));
            if (fast != null) return fast;
            width = bitmap.getWidth(); height = bitmap.getHeight();
            long count = (long) width * height;
            if (width > ReceiptQrDecoder.MAX_DIMENSION || height > ReceiptQrDecoder.MAX_DIMENSION
                || count > ReceiptQrDecoder.MAX_DECODE_PIXELS) throw limit();
            pixels = new int[(int) count];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
        // Release the bitmap before allocating the luminance/detection buffers.
        List<String> decoded = ReceiptQrDecoder.decode(width, height, pixels);
        FiscalQrParser.Result found = fiscal(decoded);
        return found != null || decoded.isEmpty() ? found : FiscalQrParser.select(decoded);
    }

    private static List<String> mlKit(Bitmap bitmap) {
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
        try {
            List<Barcode> codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 15, TimeUnit.SECONDS);
            List<String> payloads = new ArrayList<>();
            for (Barcode code : codes) if (code.getRawValue() != null) payloads.add(code.getRawValue());
            return payloads;
        } catch (Exception | LinkageError e) {
            return Collections.emptyList();
        } finally { scanner.close(); }
    }

    /** A receipt may also carry a promo/loyalty QR: keep only fiscal ones and require one receipt. */
    static FiscalQrParser.Result fiscal(List<String> payloads) {
        FiscalQrParser.Result chosen = null;
        for (String payload : payloads) {
            FiscalQrParser.Result parsed;
            try { parsed = FiscalQrParser.select(Collections.singletonList(payload)); }
            catch (ContractException e) { continue; }
            if (chosen != null && !(chosen.locator() + chosen.revision()).equals(parsed.locator() + parsed.revision()))
                throw new ContractException("QR_MULTIPLE", null, "На снимке несколько чеков. Оставьте в кадре один чек.");
            chosen = parsed;
        }
        return chosen;
    }

    private static Bitmap orient(File file, Bitmap bitmap) throws IOException {
        int orientation;
        try (FileInputStream stream = new FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(stream);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) { return bitmap; }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: return bitmap;
        }
        Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (oriented != bitmap) bitmap.recycle();
        return oriented;
    }

    private static ContractException invalid() {
        return new ContractException("IMAGE_INVALID", null, "Не удалось прочитать изображение чека.");
    }
    private static ContractException limit() {
        return new ContractException("LIMIT_EXCEEDED", null, "Изображение превышает допустимый размер.");
    }
}
