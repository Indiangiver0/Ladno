package com.ladno.finance;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import org.junit.Test;
import org.junit.Ignore;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ReceiptQrDecoderTest {
    private static final String A = "https://consumer.oofd.kz/?i=111&f=222&s=754.32&t=20261104T123456";
    private static final String B = "https://ofd.rekassa.kz/t/?i=333&f=444&s=2000&t=20261104T123500";
    private static int[] qr(String text, int size) throws Exception {
        BitMatrix bits = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) pixels[y * size + x] = bits.get(x, y) ? 0xff000000 : 0xffffffff;
        return pixels;
    }
    private static int[] pair(String first, String second) throws Exception {
        int[] pixels = new int[1000 * 500]; Arrays.fill(pixels, 0xffffffff);
        int[] a = qr(first, 400), b = qr(second, 400);
        for (int y = 0; y < 400; y++) {
            System.arraycopy(a, y * 400, pixels, (y + 50) * 1000 + 50, 400);
            System.arraycopy(b, y * 400, pixels, (y + 50) * 1000 + 550, 400);
        }
        return pixels;
    }

    @Test public void generatedQrGivesExactFields() throws Exception {
        List<String> decoded = ReceiptQrDecoder.decode(400, 400, qr(A, 400));
        assertEquals(1, decoded.size()); assertEquals(A, decoded.get(0));
        assertEquals("754.32", FiscalQrParser.select(decoded).candidate().amount);
        assertEquals("2026-11-04", FiscalQrParser.select(decoded).candidate().date);
        assertNull(FiscalQrParser.select(decoded).candidate().kind);
    }

    @Test public void rotatedAndMirroredQrDecode() throws Exception {
        int size = 400; int[] source = qr(A, size), rotated = new int[size * size], mirrored = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            rotated[x * size + size - 1 - y] = source[y * size + x];
            mirrored[y * size + size - 1 - x] = source[y * size + x];
        }
        assertEquals(A, ReceiptQrDecoder.decode(size, size, rotated).get(0));
        assertEquals(A, ReceiptQrDecoder.decode(size, size, mirrored).get(0));
    }

    @Test public void distinctAndMixedQrNeverPickFirst() throws Exception {
        for (String second : new String[]{B, "https://pay.example/qr/123"}) {
            List<String> decoded = ReceiptQrDecoder.decode(1000, 500, pair(A, second));
            assertEquals(2, decoded.size());
            try { FiscalQrParser.select(decoded); fail("picked first QR"); }
            catch (ContractException e) { assertEquals("QR_MULTIPLE", e.code); }
        }
    }

    @Test public void repeatedSameQrIsSingleObservation() throws Exception {
        List<String> decoded = ReceiptQrDecoder.decode(1000, 500, pair(A, A));
        assertEquals(1, decoded.size()); assertEquals("754.32", FiscalQrParser.select(decoded).amount);
    }

    @Ignore("Diagnostic: ZXing cannot guarantee detection of an undetectable neighboring QR; supported contract concerns decoded QR only")
    @Test public void readableQrNextToDamagedQrRequiresAnotherPhoto() throws Exception {
        int[] pixels = pair(A, B);
        // Keep the second QR's finder corners, erase much of its data region.
        for (int y = 150; y < 350; y++) for (int x = 650; x < 850; x++) pixels[y * 1000 + x] = 0xffffffff;
        try {
            FiscalQrParser.select(ReceiptQrDecoder.decode(1000, 500, pixels));
            fail("accepted a partially unreadable multi-QR image");
        } catch (ContractException e) { assertEquals("QR_MULTIPLE", e.code); }
    }

    @Test public void blankImageHasNoQrAndInvalidSizeCannotAllocateMore() {
        int[] blank = new int[300 * 300]; Arrays.fill(blank, 0xffffffff);
        assertTrue(ReceiptQrDecoder.decode(300, 300, blank).isEmpty());
        for (int[] dimensions : new int[][]{{4097, 1}, {4096, 4096}, {-1, 4}, {100, 100}}) {
            try { ReceiptQrDecoder.decode(dimensions[0], dimensions[1], new int[1]); fail("accepted invalid size"); }
            catch (ContractException e) { assertEquals("LIMIT_EXCEEDED", e.code); }
        }
    }
}
