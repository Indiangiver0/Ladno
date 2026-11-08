package com.ladno.finance;

import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.OfdReceiptParserTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfdReceiptClientTest {
    private static FiscalQrParser.Result qr() {
        return FiscalQrParser.parse("http://consumer.oofd.kz/?i=123456&f=000000987654&s=1234.5&t=20261103T201122");
    }
    @Test public void validatedLegacyQrUsesFixedHttpsEndpointAndNoRedirects() throws Exception {
        Fake connection = new Fake();
        OfdReceiptClient client = new OfdReceiptClient(url -> {
            assertEquals("https", url.getProtocol()); assertEquals("consumer.oofd.kz", url.getHost());
            assertEquals(-1, url.getPort()); assertEquals("/api/consumer-proxy/api/tickets/get-by-url", url.getPath());
            assertEquals("f=000000987654&i=123456&s=1234.50&t=20261103T201122", url.getQuery());
            return connection;
        }, 1000);
        ImportCandidate value = client.lookup(qr());
        assertEquals("high", value.confidence); assertEquals("1234.50", value.amount);
        assertFalse(connection.getInstanceFollowRedirects()); assertTrue(connection.disconnected);
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"));
        assertNull(connection.getRequestProperty("Authorization")); assertNull(connection.getRequestProperty("Cookie"));
    }
    @Test public void redirectAuthRateLimitAndServerErrorsNeverCreateExpense() throws Exception {
        for (int status : new int[]{301, 302, 307, 401, 403, 404, 429, 500}) {
            Fake connection = new Fake(); connection.status = status;
            ImportCandidate value = new OfdReceiptClient(url -> connection, 1000).lookup(qr());
            assertNull(value.kind); assertTrue(value.reasons.contains("OFD_UNAVAILABLE"));
            assertFalse(connection.bodyRead); assertTrue(connection.disconnected);
        }
    }
    @Test public void htmlAndUnexpectedCompressionAreNotParsed() throws Exception {
        for (String contentType : new String[]{null, "text/html", "text/plain", "application/jsonp"}) {
            Fake connection = new Fake(); connection.contentType = contentType;
            assertInvalid(connection);
        }
        Fake encoded = new Fake(); encoded.encoding = "gzip"; assertInvalid(encoded);
    }
    @Test public void actualStreamBoundDoesNotTrustMissingContentLength() throws Exception {
        Fake connection = new Fake();
        connection.bytes = new byte[OfdReceiptClient.MAX_BODY_BYTES + 1];
        connection.reportedLength = -1;
        assertInvalid(connection);
        assertTrue(connection.bodyClosed);
    }
    @Test public void malformedJsonUtf8AndIdentityMismatchNeverBecomeHigh() throws Exception {
        Fake malformed = new Fake(); malformed.bytes = "{not-json}".getBytes(StandardCharsets.UTF_8); assertInvalid(malformed);
        Fake utf8 = new Fake(); utf8.bytes = new byte[]{(byte) 0xc3, (byte) 0x28};
        ImportCandidate utf = new OfdReceiptClient(url -> utf8, 1000).lookup(qr());
        assertNull(utf.kind); assertNotEquals("high", utf.confidence);
        Fake mismatch = new Fake(); mismatch.bytes = OfdReceiptParserTest.receipt().replace("123456", "123457").getBytes(StandardCharsets.UTF_8);
        ImportCandidate value = new OfdReceiptClient(url -> mismatch, 1000).lookup(qr());
        assertNull(value.kind); assertTrue(value.reasons.contains("OFD_MISMATCH"));
    }
    @Test public void totalDeadlineAndFailureMessagesRemainBoundedAndSanitized() throws Exception {
        Fake blocked = new Fake(); blocked.delayMillis = 5000;
        long before = System.nanoTime();
        ImportCandidate value = new OfdReceiptClient(url -> blocked, 50).lookup(qr());
        long elapsed = (System.nanoTime() - before) / 1000000;
        assertTrue("Bounded caller deadline", elapsed < 1500);
        assertNull(value.kind); assertTrue(value.reasons.contains("OFD_UNAVAILABLE"));
        assertEquals("", value.note); assertTrue(blocked.disconnected);
    }
    @Test public void anotherProviderIsNotSentToKazakhtelecom() throws Exception {
        FiscalQrParser.Result other = FiscalQrParser.parse("https://ofd.rekassa.kz/t/?i=123456&f=000000987654&s=1234.50&t=20261103T201122");
        ImportCandidate result = new OfdReceiptClient(url -> { fail("Unexpected network"); return null; }, 1000).lookup(other);
        assertNull(result.kind); assertEquals("1234.50", result.amount);
    }
    private static void assertInvalid(Fake connection) {
        ImportCandidate value = new OfdReceiptClient(url -> connection, 1000).lookup(qr());
        assertNull(value.kind); assertTrue(value.reasons.contains("OFD_INVALID_RESPONSE"));
        assertTrue(connection.disconnected);
    }
    private static final class Fake extends HttpsURLConnection {
        int status = 200;
        String contentType = "application/json; charset=utf-8", encoding;
        byte[] bytes = OfdReceiptParserTest.receipt().getBytes(StandardCharsets.UTF_8);
        long reportedLength = -1, delayMillis;
        volatile boolean disconnected, bodyRead, bodyClosed;
        Fake() throws Exception { super(new URL("https://consumer.oofd.kz/")); }
        public int getResponseCode() throws IOException {
            if (delayMillis > 0) try { Thread.sleep(delayMillis); }
            catch (InterruptedException error) { throw new IOException("private URL/response must never escape"); }
            return status;
        }
        public String getContentType() { return contentType; }
        public String getContentEncoding() { return encoding; }
        public long getContentLengthLong() { return reportedLength; }
        public InputStream getInputStream() {
            bodyRead = true;
            return new ByteArrayInputStream(bytes) { public void close() throws IOException { bodyClosed = true; super.close(); } };
        }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() { }
        public String getCipherSuite() { return "synthetic"; }
        public Certificate[] getLocalCertificates() { return null; }
        public Certificate[] getServerCertificates() { return new Certificate[0]; }
    }
}
