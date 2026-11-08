package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.OfdReceiptParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently specified synthetic fiscal data. No user identifiers or HTTP. */
public final class OfdIndependentTest {
    private static FiscalQrParser.Result qr(String amount) {
        return FiscalQrParser.parse("https://consumer.oofd.kz/?i=700000123&f=000000000111&s=" + amount + "&t=20261103T102030");
    }
    private static String body(String amount) {
        return "{\"kkmFnsId\":\"000000000111\",\"ticket\":{\"fiscalId\":\"700000123\",\"type\":\"TICKET\","
            + "\"offlineFiscalId\":null,\"cancelDate\":null,\"transactionDate\":\"2026-11-03T10:20:30.000\","
            + "\"totalSum\":" + amount + ",\"operationType\":2,\"payments\":[{\"paymentType\":\"CARD\",\"sum\":" + amount
            + "}],\"items\":[{\"itemType\":1,\"commodity\":{\"name\":\"Synthetic commodity\",\"quantity\":1,\"price\":"
            + amount + ",\"sum\":" + amount + ",\"measureUnitCode\":\"796\"}}]}}";
    }
    private static ImportCandidate parse(String text) { return OfdReceiptParser.parse(text, qr("1234.50")); }
    private static void notHigh(String text) {
        try { assertNotEquals("high", parse(text).confidence); }
        catch (ContractException expected) { assertEquals("OFD_INVALID_RESPONSE", expected.code); }
    }

    @Test public void exactNumbersAboveBinaryFloatRangeStayExact() {
        String amount = "9007199254740993.01";
        ImportCandidate candidate = OfdReceiptParser.parse(body(amount), qr(amount));
        assertEquals(amount, candidate.amount); assertEquals("expense", candidate.kind);
        assertEquals("KZT", candidate.currency); assertEquals("2026-11-03", candidate.date);
        assertEquals("source", candidate.dateOrigin); assertEquals("high", candidate.confidence);
        assertTrue(candidate.reasons.isEmpty()); assertNull(candidate.paymentReference);
    }

    @Test public void numericLexemesCannotBecomeRoundedMoney() {
        for (String token : new String[]{"1234.500", "1.2345e3", "123450e-2", "-1234.50", "0", "1000000000000000000000000", "\"1234.50\""})
            notHigh(body("1234.50").replace("\"totalSum\":1234.50", "\"totalSum\":" + token));
    }

    @Test public void duplicateNullAndUnboundedJsonAreRejectedBeforeFinancialUse() {
        String valid = body("1234.50");
        notHigh(valid.replace("\"totalSum\":1234.50", "\"totalSum\":1234.50,\"totalSum\":1234.50"));
        notHigh(valid.replace("\"cancelDate\":null,", ""));
        notHigh(valid.replace("\"offlineFiscalId\":null", "\"offlineFiscalId\":false"));
        notHigh(valid.replace("\"payments\":[", "\"payments\":null,\"unused\":["));
        String deep = "0"; for (int i = 0; i < 20; i++) deep = "[" + deep + "]";
        notHigh(valid.substring(0, valid.length() - 1) + ",\"extra\":" + deep + "}");
        String sixtyFour = "1".repeat(64);
        assertEquals("high", parse(valid.substring(0, valid.length() - 1) + ",\"metadataNumber\":" + sixtyFour + "}").confidence);
        notHigh(valid.substring(0, valid.length() - 1) + ",\"metadataNumber\":" + sixtyFour + "1}");
    }

    @Test public void nonSalesCancellationAndExplicitCurrencyConflictNeverAutoPost() {
        String valid = body("1234.50");
        for (String operation : new String[]{"0", "1", "3", "99", "null", "2.0", "\"2\""})
            notHigh(valid.replace("\"operationType\":2", "\"operationType\":" + operation));
        notHigh(valid.replace("\"cancelDate\":null", "\"cancelDate\":\"2026-11-03T10:21:00\""));
        notHigh(valid.replace("\"type\":\"TICKET\"", "\"type\":\"OTHER\""));
        notHigh(valid.replace("\"type\":\"TICKET\"", "\"type\":\"TICKET\",\"currency\":\"USD\""));
    }

    @Test public void mismatchedIdentityTotalOrCalendarKeepsLocalReviewFields() {
        String valid = body("1234.50");
        for (String changed : new String[]{valid.replace("700000123", "700000124"),
            valid.replace("000000000111", "000000000112"), body("1234.51"),
            valid.replace("2026-11-03T10:20:30.000", "2026-11-04T10:20:30.000"),
            valid.replace("2026-11-03T10:20:30.000", "2026-11-03T10:20:31.000")}) {
            ImportCandidate result = parse(changed);
            assertNotEquals("high", result.confidence); assertEquals("1234.50", result.amount);
            assertEquals("2026-11-03", result.date); assertTrue(result.reasons.contains("OFD_MISMATCH"));
        }
        for (String timestamp : new String[]{"2026-02-29T10:20:30", "2026-11-03T10:20:30.001", "2026-11-03T10:20:30Z", "2026-11-03T24:00:00"})
            notHigh(valid.replace("2026-11-03T10:20:30.000", timestamp));
    }

    @Test public void paymentSumAndNetDiscountTotalAreIndependentEvidence() {
        String valid = body("1234.50");
        notHigh(valid.replace("\"paymentType\":\"CARD\",\"sum\":1234.50", "\"paymentType\":\"CARD\",\"sum\":1234.49"));
        notHigh(valid.replace("\"paymentType\":\"CARD\"", "\"paymentType\":\"UNKNOWN\""));
        String discounted = valid.replace("\"items\":[", "\"items\":[{\"itemType\":5,\"commodity\":null,\"discount\":{\"sum\":25}},");
        ImportCandidate result = parse(discounted);
        assertEquals("1234.50", result.amount); assertEquals("high", result.confidence);
        String drift = valid.replace("\"ticket\":{", "\"ticket\":{\"acceptedAt\":null,\"kkmId\":\"synthetic-rotating-metadata\",");
        assertEquals(parse(valid).toJson().toString(), parse(drift).toJson().toString());
    }

    @Test public void fixedHttpsTransportRejectsRedirectsHtmlAuthAndOversize() throws Exception {
        FiscalQrParser.Result qr = qr("1234.50");
        AtomicReference<URL> seen = new AtomicReference<>();
        Fake positive = new Fake(new URL("https://consumer.oofd.kz/"), body("1234.50"));
        ImportCandidate result = new OfdReceiptClient(url -> { seen.set(url); return positive; }, 1000).lookup(qr);
        assertEquals("high", result.confidence); assertEquals("https", seen.get().getProtocol());
        assertEquals("consumer.oofd.kz", seen.get().getHost());
        assertEquals("/api/consumer-proxy/api/tickets/get-by-url", seen.get().getPath());
        assertFalse(positive.getInstanceFollowRedirects()); assertEquals("GET", positive.getRequestMethod());
        assertNull(positive.getRequestProperty("Cookie")); assertNull(positive.getRequestProperty("Authorization"));
        assertTrue(positive.disconnected); assertTrue(positive.bodyClosed);
        for (int status : new int[]{301, 302, 307, 401, 403, 429, 500}) {
            Fake fake = new Fake(seen.get(), body("1234.50")); fake.status = status;
            assertNotEquals("high", new OfdReceiptClient(url -> fake, 1000).lookup(qr).confidence);
            assertTrue(fake.disconnected);
        }
        Fake html = new Fake(seen.get(), "<html>not a receipt</html>"); html.contentType = "text/html";
        assertNotEquals("high", new OfdReceiptClient(url -> html, 1000).lookup(qr).confidence);
        Fake declared = new Fake(seen.get(), body("1234.50")); declared.length = 262145;
        assertNotEquals("high", new OfdReceiptClient(url -> declared, 1000).lookup(qr).confidence);
        Fake streamed = new Fake(seen.get(), "x".repeat(262145)); streamed.length = -1;
        assertNotEquals("high", new OfdReceiptClient(url -> streamed, 1000).lookup(qr).confidence);
    }

    @Test public void totalDeadlineReturnsLocalFieldsAndClosesTransport() throws Exception {
        Fake blocked = new Fake(new URL("https://consumer.oofd.kz/"), "");
        blocked.stream = new InputStream() {
            @Override public int read() throws IOException {
                try { Thread.sleep(2000); } catch (InterruptedException e) { throw new IOException("Synthetic interrupted read"); }
                return -1;
            }
        };
        long before = System.nanoTime();
        ImportCandidate result = new OfdReceiptClient(url -> blocked, 80).lookup(qr("1234.50"));
        long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
        assertTrue("Caller must have a bounded total deadline", millis < 1800);
        assertNotEquals("high", result.confidence); assertEquals("1234.50", result.amount);
        assertNull(result.kind); assertTrue(blocked.disconnected);
    }

    private static final class Fake extends HttpsURLConnection {
        int status = 200; long length = -1; String contentType = "application/json";
        volatile boolean disconnected, bodyClosed; InputStream stream;
        Fake(URL url, String json) {
            super(url);
            stream = new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                @Override public void close() throws IOException { bodyClosed = true; super.close(); }
            };
        }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return contentType; }
        @Override public long getContentLengthLong() { return length; }
        @Override public InputStream getInputStream() { return stream; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public String getCipherSuite() { return "synthetic"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
    }
}
