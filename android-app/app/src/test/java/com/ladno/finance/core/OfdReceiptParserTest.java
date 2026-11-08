package com.ladno.finance.core;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic identities and independent decimal expectations, derived only from the observed schema. */
public class OfdReceiptParserTest {
    private static final String QR = "https://consumer.oofd.kz/?i=123456&f=000000987654&s=1234.50&t=20261103T201122";
    private static FiscalQrParser.Result qr() { return FiscalQrParser.parse(QR); }
    public static String receipt() {
        return "{\"kkmFnsId\":\"000000987654\",\"ticket\":{\"type\":\"TICKET\",\"fiscalId\":\"123456\","
            + "\"offlineFiscalId\":null,\"cancelDate\":null,\"transactionDate\":\"2026-11-03T20:11:22.000\","
            + "\"totalSum\":1234.50,\"operationType\":2,\"payments\":[{\"paymentType\":\"CARD\",\"sum\":1234.50}],"
            + "\"items\":[{\"itemType\":1,\"commodity\":{\"name\":\"Synthetic item\"}}]}}";
    }
    @Test public void verifiedSaleUsesNetTotalAndSourceDateWithoutRawData() {
        ImportCandidate value = OfdReceiptParser.parse(receipt(), qr());
        assertEquals("expense", value.kind); assertEquals("1234.50", value.amount);
        assertEquals("KZT", value.currency); assertEquals("2026-11-03", value.date);
        assertEquals("source", value.dateOrigin); assertEquals("high", value.confidence);
        assertTrue(value.reasons.isEmpty()); assertNull(value.paymentReference); assertEquals("", value.note);
    }
    @Test public void exactLargeNumberAboveDoublePrecisionIsNotRounded() {
        String amount = "9007199254740993.01";
        FiscalQrParser.Result large = FiscalQrParser.parse(QR.replace("1234.50", amount));
        ImportCandidate value = OfdReceiptParser.parse(receipt().replace("1234.50", amount), large);
        assertEquals(amount, value.amount); assertEquals("high", value.confidence);
    }
    @Test public void saleWithDiscountRowDoesNotRecomputeCommodityTotals() {
        String body = receipt().replace("\"name\":\"Synthetic item\"", "\"name\":\"Synthetic item\",\"sum\":99999.00")
            .replace("}}]", "}},{\"itemType\":5,\"commodity\":null,\"discount\":{\"sum\":36.00}}]");
        ImportCandidate value = OfdReceiptParser.parse(body, qr());
        assertEquals("1234.50", value.amount); assertEquals("high", value.confidence);
    }
    @Test public void metadataVariabilityDoesNotChangeFinancialResult() {
        for (String metadata : Arrays.asList("\"acceptedAt\":null,\"kkmId\":null,", "\"acceptedAt\":\"later\",\"kkmId\":\"not-financial\",")) {
            ImportCandidate value = OfdReceiptParser.parse(receipt().replace("\"type\":", metadata + "\"type\":"), qr());
            assertEquals("high", value.confidence); assertEquals("1234.50", value.amount);
        }
    }
    @Test public void cashAndCardAreSummedExactly() {
        String body = receipt().replace("{\"paymentType\":\"CARD\",\"sum\":1234.50}",
            "{\"paymentType\":\"CARD\",\"sum\":1200.01},{\"paymentType\":\"CASH\",\"sum\":34.49}");
        assertEquals("high", OfdReceiptParser.parse(body, qr()).confidence);
    }
    @Test public void nonSalesCancelledOfflineAndFutureStatusesNeverAutoPost() {
        for (String body : Arrays.asList(receipt().replace("\"operationType\":2", "\"operationType\":0"),
            receipt().replace("\"operationType\":2", "\"operationType\":1"), receipt().replace("\"operationType\":2", "\"operationType\":3"),
            receipt().replace("\"operationType\":2", "\"operationType\":999"), receipt().replace("\"cancelDate\":null", "\"cancelDate\":\"2026-11-03\""),
            receipt().replace("\"offlineFiscalId\":null", "\"offlineFiscalId\":\"offline\""),
            receipt().replace("\"type\":", "\"status\":\"unknown\",\"type\":"), receipt().replace("\"type\":", "\"parentTicket\":{},\"type\":"))) {
            ImportCandidate value = OfdReceiptParser.parse(body, qr());
            assertNull(value.kind); assertEquals("uncertain", value.confidence); assertTrue(value.reasons.contains("OFD_REVIEW_REQUIRED"));
        }
    }
    @Test public void identityDateTotalAndPaymentMismatchesAreReview() {
        for (String body : Arrays.asList(receipt().replace("\"123456\"", "\"123457\""),
            receipt().replace("\"000000987654\"", "\"987654\""),
            receipt().replace("2026-11-03T20:11:22", "2026-11-04T20:11:22"),
            receipt().replace("\"totalSum\":1234.50", "\"totalSum\":1234.49"),
            receipt().replace("\"sum\":1234.50", "\"sum\":1234.49"))) {
            ImportCandidate value = OfdReceiptParser.parse(body, qr());
            assertNull(value.kind); assertTrue(value.reasons.contains("OFD_MISMATCH"));
        }
    }
    @Test public void badLexicalMoneyCannotBecomeHighOrRounded() {
        for (String number : Arrays.asList("1234.500", "1.2345e3", "-1234.50", "0", "9999999999999999999999999.00", "\"1234.50\"")) {
            rejected(receipt().replace("\"totalSum\":1234.50", "\"totalSum\":" + number));
        }
    }
    @Test public void missingRequiredNullsAndTimestampOffsetsAreNotSuccess() {
        for (String body : Arrays.asList(receipt().replace("\"cancelDate\":null,", ""),
            receipt().replace("\"offlineFiscalId\":null,", ""), receipt().replace(".000\"", ".001\""),
            receipt().replace(".000\"", "Z\""), receipt().replace("2026-11-03", "2026-02-29"))) rejected(body);
    }
    @Test public void duplicateKeysDepthAndBadStringsAreRejected() {
        rejected(receipt().replace("\"totalSum\":1234.50", "\"totalSum\":1234.50,\"totalSum\":1"));
        rejected(receipt().replace("\"name\":\"Synthetic item\"", "\"name\":\"\\ud800\""));
        String nested = "0";
        for (int i = 0; i < 18; i++) nested = "[" + nested + "]";
        rejected(receipt().replace("\"type\":", "\"payload\":" + nested + ",\"type\":"));
    }
    @Test public void fallbackPreservesUsefulFieldsAndUnknownKind() {
        ImportCandidate value = OfdReceiptParser.fallback(qr(), "OFD_UNAVAILABLE");
        assertEquals("1234.50", value.amount); assertEquals("2026-11-03", value.date);
        assertNull(value.kind); assertEquals("uncertain", value.confidence); assertTrue(value.reasons.contains("OFD_UNAVAILABLE"));
    }
    private static void rejected(String body) {
        try { OfdReceiptParser.parse(body, qr()); fail("Invalid response accepted"); }
        catch (ContractException expected) { assertEquals("OFD_INVALID_RESPONSE", expected.code); }
    }
}
