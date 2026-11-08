package com.ladno.finance;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.detector.MultiDetector;
import com.google.zxing.qrcode.decoder.Decoder;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure Java QR-only decoder, also exercised with synthetic RGB images on the JVM. */
public final class ReceiptQrDecoder {
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_DECODE_PIXELS = 8_000_000L;
    private ReceiptQrDecoder() { }

    public static List<String> decode(int width, int height, int[] pixels) {
        long count = (long) width * height;
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
            || count > MAX_DECODE_PIXELS || pixels == null || count != pixels.length)
            throw new ContractException("LIMIT_EXCEEDED", null, "Изображение превышает допустимый размер для QR.");
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        DetectorResult[] detected;
        try { detected = new MultiDetector(image.getBlackMatrix()).detectMulti(hints); }
        catch (NotFoundException e) { return Collections.emptyList(); }
        if (detected.length > FiscalQrParser.MAX_CODES)
            throw new ContractException("LIMIT_EXCEEDED", null, "На снимке слишком много QR. Оставьте один чек.");
        Set<String> payloads = new LinkedHashSet<>();
        Decoder decoder = new Decoder();
        boolean unreadable = false;
        for (DetectorResult found : detected) {
            DecoderResult decoded;
            try { decoded = decoder.decode(found.getBits(), hints); }
            catch (FormatException | ChecksumException e) { unreadable = true; continue; }
            // Do not silently join multiple structured QR into one financial observation.
            if (decoded.hasStructuredAppend())
                throw new ContractException("QR_MULTIPLE", null, "На снимке составной QR. Оставьте один фискальный QR чека.");
            String text = decoded.getText();
            if (text == null || text.getBytes(StandardCharsets.UTF_8).length > FiscalQrParser.MAX_PAYLOAD_BYTES)
                throw new ContractException("LIMIT_EXCEEDED", null, "QR превышает допустимый размер.");
            payloads.add(text);
        }
        if (unreadable && !payloads.isEmpty() && detected.length > 1)
            throw new ContractException("QR_MULTIPLE", null,
                "На снимке несколько QR, часть не читается. Оставьте один фискальный QR чека.");
        return new ArrayList<>(payloads);
    }
}
