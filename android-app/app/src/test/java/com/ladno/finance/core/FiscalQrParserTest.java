package com.ladno.finance.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Synthetic locators only: these tests make no HTTP requests. */
public class FiscalQrParserTest {
    private static String url(String amount, String time) {
        return "https://consumer.oofd.kz/?i=001234567&f=000123456789&s=" + amount + "&t=" + time;
    }
    private static void rejected(String code, String value) {
        try { FiscalQrParser.parse(value); fail("accepted invalid QR"); }
        catch (ContractException e) { assertEquals(code, e.code); assertFalse(e.getMessage().contains(value)); }
    }

    @Test public void exactAmountDateAndPostableExpense() {
        FiscalQrParser.Result qr = FiscalQrParser.parse(url("1234.5", "20261104T235959"));
        ImportCandidate candidate = ImportCandidate.fromJson(qr.candidate().toJson());
        assertEquals("1234.50", candidate.amount); assertEquals("KZT", candidate.currency);
        assertEquals("2026-11-04", candidate.date); assertEquals("source", candidate.dateOrigin);
        // A fiscal QR is a cash-register-signed paid total: it is posted as an expense.
        assertEquals("expense", candidate.kind); assertEquals("high", candidate.confidence);
        assertNull(candidate.paymentReference); assertEquals("", candidate.note);
        assertTrue(candidate.reasons.isEmpty());
    }

    @Test public void acceptedAmountsDoNotRoundAndSupportMaximum() {
        String[] input = {"1", "1.2", "1.20", "0.01", "999999999999999999999999.99"};
        String[] expected = {"1.00", "1.20", "1.20", "0.01", "999999999999999999999999.99"};
        for (int i = 0; i < input.length; i++)
            assertEquals(expected[i], FiscalQrParser.parse(url(input[i], "20240229T000000")).amount);
    }

    @Test public void invalidAmountsNeverBecomeMoney() {
        for (String amount : new String[]{"0", "0.0", "0.00", "-1", "+1", "1e2", "1.205", "01", ".1", "1.", "1000000000000000000000000"})
            rejected("QR_AMOUNT_INVALID", url(amount, "20261104T120000"));
        // Comma decimals and trailing zeros are formatting, not rounding.
        assertEquals("1.20", FiscalQrParser.parse(url("1,20", "20261104T120000")).amount);
        assertEquals("1.20", FiscalQrParser.parse(url("1.200", "20261104T120000")).amount);
    }

    @Test public void calendarAndClockAreStrict() {
        for (String time : new String[]{"20260229T120000", "20261301T120000", "20261131T120000", "20261104T240000", "20261104T126000", "20261104T120060", "00001104T120000", "2026114T120000"})
            rejected("QR_DATE_INVALID", url("1", time));
        assertEquals("0001-01-01", FiscalQrParser.parse(url("1", "00010101T000000")).date);
        assertEquals("9999-12-31", FiscalQrParser.parse(url("1", "99991231T235959")).date);
    }

    @Test public void anyOperatorHostIsReadLocallyButNeverTrustedForLookup() {
        String query = "?i=1&f=2&s=3&t=20261104T120000";
        for (String prefix : new String[]{"https://ofd1.kz/t/", "https://cabinet.kofd.kz/consumer", "http://consumer.oofd.kz", "https://ofd.rekassa.kz/t/", "https://consumer.oofd.kz:443/"})
            assertEquals(prefix, "3.00", FiscalQrParser.parse(prefix + query).amount);
        for (String prefix : new String[]{"https://user@consumer.oofd.kz/", "ftp://consumer.oofd.kz/", "file:///consumer/", "https://localhost/"})
            rejected("QR_UNSUPPORTED", prefix + query);
        // Only the exact Kazakhtelecom host may be looked up; look-alikes stay local.
        assertFalse(OfdReceiptParser.Request.from(FiscalQrParser.parse("https://consumer.oofd.kz.evil.test/" + query)).supported());
        assertTrue(OfdReceiptParser.Request.from(FiscalQrParser.parse("https://consumer.oofd.kz/" + query)).supported());
    }

    @Test public void queriesAreDecodedOnceAndDuplicatesRejected() {
        String valid = url("3", "20261104T120000");
        for (String invalid : new String[]{valid + "&i=2", valid.replace("&f=000123456789", "&%69=2"), valid.replace("s=3", "s=%GG"), valid.replace("i=001234567", "i="), valid.replace("&f=000123456789", "&other=2"), valid.replace("https:", "https:\\")})
            rejected("QR_UNSUPPORTED", invalid);
        rejected("QR_AMOUNT_INVALID", valid.replace("s=3", "s=3%26f%3D8"));
        rejected("QR_AMOUNT_INVALID", valid.replace("s=3", "s=%2533"));
        assertEquals("3.00", FiscalQrParser.parse(valid + "&extra=1").amount);
        assertEquals("3.00", FiscalQrParser.parse(valid.replace("s=3", "s=%33")).amount);
    }

    @Test public void amountAndDateErrorsAreNotHiddenByMalformedEncoding() {
        rejected("QR_AMOUNT_INVALID", url("3%2C5%2C0", "20261104T120000"));
        rejected("QR_DATE_INVALID", url("3", "20261104T1200"  + "0%0A1"));
        // Scanner payloads may end with a newline.
        assertEquals("3.00", FiscalQrParser.parse(url("3", "20261104T120000") + "\n").amount);
    }

    @Test public void canonicalLocatorIsIndependentOfPhotoAndQueryOrder() {
        FiscalQrParser.Result a = FiscalQrParser.parse(url("1.2", "20261104T120000"));
        FiscalQrParser.Result b = FiscalQrParser.parse("http://CONSUMER.OOFD.KZ?t=20261104T120000&s=1.20&f=000123456789&i=001234567");
        assertEquals(a.locator(), b.locator()); assertEquals(a.revision(), b.revision());
        assertNotEquals(a.revision(), FiscalQrParser.parse(url("2", "20261104T120000")).revision());
        assertNotEquals(a.locator(), FiscalQrParser.parse(url("1.2", "20261104T120000").replace("i=001234567", "i=1234567")).locator());
        assertEquals("1.20", FiscalQrParser.select(Arrays.asList(url("1.2", "20261104T120000"),
            "http://consumer.oofd.kz?f=000123456789&i=001234567&t=20261104T120000&s=1.20")).amount);
    }

    @Test public void noQrVersusMultipleMixedAndUnsupported() {
        assertNull(FiscalQrParser.select(Collections.emptyList()));
        String valid = url("1", "20261104T120000");
        assertNotNull(FiscalQrParser.select(Collections.singletonList(valid)));
        for (java.util.List<String> payloads : Arrays.asList(Arrays.asList(valid, url("2", "20261104T120000")), Arrays.asList(valid, "https://pay.example/qr"))) {
            try { FiscalQrParser.select(payloads); fail("accepted multiple QR"); }
            catch (ContractException e) { assertEquals("QR_MULTIPLE", e.code); }
        }
        rejected("QR_UNSUPPORTED", "https://pay.example/qr");
        rejected("QR_AMOUNT_INVALID", "http://consumer.test-oofd.kz?i=1196572871&f=678980123456&s=0.0&t=20200518T181817");
    }

    @Test public void oversizedPayloadAndCountAreExplicitLimits() {
        rejected("LIMIT_EXCEEDED", "x".repeat(4097));
        rejected("LIMIT_EXCEEDED", "ж".repeat(2049));
        try { FiscalQrParser.select(Collections.nCopies(17, url("1", "20261104T120000"))); fail("accepted too many QR"); }
        catch (ContractException e) { assertEquals("LIMIT_EXCEEDED", e.code); }
    }
}
