package com.ladno.finance;

import com.ladno.finance.core.BankMessageParser;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent contract examples. All receipt identifiers are synthetic; no HTTP. */
public final class QrIndependentTest {
    private static final String BASE = "https://consumer.oofd.kz/?i=000123&f=000456&s=";
    private static String qr(String amount, String time) { return BASE + amount + "&t=" + time; }
    private static void rejected(String input, String code) {
        try { FiscalQrParser.parse(input); fail("Unexpected accepted synthetic QR"); }
        catch (ContractException error) { assertEquals(code, error.code); }
    }

    @Test public void documentedTengeScaleNeverRounds() {
        String[][] samples = {{"1", "1.00"}, {"1.2", "1.20"}, {"1234.50", "1234.50"},
            {"999999999999999999999999.99", "999999999999999999999999.99"}};
        for (String[] sample : samples) {
            ImportCandidate result = FiscalQrParser.parse(qr(sample[0], "20260228T235959")).candidate();
            assertEquals(sample[1], result.amount); assertEquals("KZT", result.currency);
            assertEquals("2026-02-28", result.date); assertEquals("source", result.dateOrigin);
            assertNull(result.kind); assertEquals("uncertain", result.confidence);
            assertTrue(result.reasons.contains("UNKNOWN_KIND")); assertNull(result.paymentReference);
            assertEquals(sample[1], ImportCandidate.fromJson(result.toJson()).amount);
        }
    }

    @Test public void invalidAmountsDoNotBecomeRoundedExpenses() {
        for (String amount : new String[]{"0", "0.00", "-1", "+1", "01", "1.230", "1,20", "1e3", "1000000000000000000000000"})
            rejected(qr(amount, "20261104T101112"), "QR_AMOUNT_INVALID");
    }

    @Test public void invalidCalendarsAndTimesNeverUseCaptureDate() {
        for (String stamp : new String[]{"20260229T121212", "20261301T121212", "20261104T240000", "20261104T126000", "00000101T000000", "20261104"})
            rejected(qr("12.34", stamp), "QR_DATE_INVALID");
        assertEquals("2024-02-29", FiscalQrParser.parse(qr("12.34", "20240229T000000")).date);
    }

    @Test public void URITrustIsExactAndQueryIsDecodedOnce() {
        String valid = qr("12.34", "20261104T101112");
        for (String bad : new String[]{valid.replace("consumer.oofd.kz", "consumer.oofd.kz.evil.example"),
                valid.replace("consumer.oofd.kz", "consumer.oofd.kz:443"),
                valid.replace("consumer.oofd.kz", "user@consumer.oofd.kz"),
                valid.replace("/?", "/%2f?"), valid + "#fragment", valid + "&s=12.34",
                valid.replace("&f=000456", "&%69=000456"), valid.replace("s=12.34", "s=12%252E34"),
                valid.replace("i=000123", "i=000123%26f%3D77"), valid.replace("i=000123", "i=%GG"),
                valid.replace("https:", "file:"), valid.replace("consumer.oofd.kz", "consumer.test-oofd.kz")}) {
            try { FiscalQrParser.parse(bad); fail("Unsafe QR unexpectedly accepted"); }
            catch (ContractException expected) { assertNotEquals("high", expected.code); }
        }
    }

    @Test public void selectionNeverChoosesOneOfDistinctOrMixedCodes() {
        String first = qr("12.34", "20261104T101112");
        assertNull(FiscalQrParser.select(Collections.emptyList()));
        assertEquals("12.34", FiscalQrParser.select(Arrays.asList(first, first)).amount);
        for (java.util.List<String> mixed : Arrays.asList(Arrays.asList(first, first.replace("000123", "999123")),
                Arrays.asList(first, "https://pay.example/synthetic"))) {
            try { FiscalQrParser.select(mixed); fail("Multiple QR selected silently"); }
            catch (ContractException expected) { assertEquals("QR_MULTIPLE", expected.code); }
        }
    }

    @Test public void extraLinesAreRetainedAndEquivalentViewsDoNotDoubleAmounts() {
        String complete = "Пополнение\n12.34 тенге\n04.11.2026";
        String text = NotificationText.assemble("Пополнение", "Пополнение", complete,
            new CharSequence[]{"Пополнение", "12.34 тенге", "04.11.2026"});
        ImportCandidate candidate = BankMessageParser.parse(text, 0, "Asia/Qyzylorda", false);
        assertEquals("12.34", candidate.amount); assertEquals("KZT", candidate.currency);
        assertEquals("income", candidate.kind); assertEquals("2026-11-04", candidate.date);
        assertEquals("high", candidate.confidence);
    }

    @Test public void transfersAndConflictingViewsRemainReviewable() {
        ImportCandidate sent = BankMessageParser.parse("Вы перевели 345.67 теңге 04.11.2026", 0, "Asia/Qyzylorda", false);
        assertEquals("expense", sent.kind); assertEquals("345.67", sent.amount); assertNotEquals("high", sent.confidence);
        ImportCandidate received = BankMessageParser.parse("Вам перевели 345.67 тг 04.11.2026", 0, "Asia/Qyzylorda", false);
        assertEquals("income", received.kind); assertNotEquals("high", received.confidence);
        String conflict = NotificationText.assemble("Покупка", "Оплачено 12.34 KZT 04.11.2026", null,
            new CharSequence[]{"Оплачено 99.99 KZT 04.11.2026"});
        ImportCandidate ambiguous = BankMessageParser.parse(conflict, 0, "Asia/Qyzylorda", false);
        assertNull(ambiguous.amount); assertNotEquals("high", ambiguous.confidence);
        assertNotEquals("high", BankMessageParser.parse("Вы не перевели 12.34 KZT 04.11.2026", 0, "Asia/Qyzylorda", false).confidence);
    }
}
