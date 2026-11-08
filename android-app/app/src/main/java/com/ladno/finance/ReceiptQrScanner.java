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
            width = bitmap.getWidth(); height = bitmap.getHeight();
            long count = (long) width * height;
            if (width > ReceiptQrDecoder.MAX_DIMENSION || height > ReceiptQrDecoder.MAX_DIMENSION
                || count > ReceiptQrDecoder.MAX_DECODE_PIXELS) throw limit();
            pixels = new int[(int) count];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
        // Release the bitmap before allocating the luminance/detection buffers.
        return FiscalQrParser.select(ReceiptQrDecoder.decode(width, height, pixels));
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
