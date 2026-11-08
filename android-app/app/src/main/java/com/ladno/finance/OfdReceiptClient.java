package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.OfdReceiptParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;

/** Fixed native-only OFD lookup. Neither the photo nor raw QR URL is transmitted. */
final class OfdReceiptClient {
    static final int MAX_BODY_BYTES = 262144;
    private static final int TOTAL_MILLIS = 10000;
    private static final String ENDPOINT = "https://consumer.oofd.kz/api/consumer-proxy/api/tickets/get-by-url";
    // One bounded daemon also bounds a platform DNS call which ignores interruption.
    // A stuck transport cannot create an unbounded queue/thread per receipt or block UI/OCR forever.
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), runnable -> { Thread thread = new Thread(runnable, "ladno-ofd"); thread.setDaemon(true); return thread; });

    interface Connections { HttpsURLConnection open(URL url) throws java.io.IOException; }
    private final Connections connections;
    private final int totalMillis;
    OfdReceiptClient() { this(OfdTls::open, TOTAL_MILLIS); }
    // Package-only seam for synthetic transport tests, never a bridge-controlled endpoint.
    OfdReceiptClient(Connections connections, int totalMillis) {
        if (connections == null || totalMillis <= 0 || totalMillis > TOTAL_MILLIS) throw new IllegalArgumentException();
        this.connections = connections; this.totalMillis = totalMillis;
    }

    ImportCandidate lookup(FiscalQrParser.Result qr) {
        OfdReceiptParser.Request request = OfdReceiptParser.Request.from(qr);
        if (!request.supported()) return qr.candidate();
        AtomicReference<HttpsURLConnection> active = new AtomicReference<>();
        Future<ImportCandidate> result = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalMillis);
        try {
            result = NETWORK.submit(() -> fetch(request, qr, active, deadline));
            return result.get(totalMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return OfdReceiptParser.fallback(qr, "OFD_UNAVAILABLE");
        } catch (Exception error) {
            // Exception messages may contain URL/response data: never log or forward them.
            return OfdReceiptParser.fallback(qr, "OFD_UNAVAILABLE");
        } finally {
            if (result != null) result.cancel(true);
            HttpsURLConnection connection = active.getAndSet(null);
            if (connection != null) connection.disconnect();
        }
    }

    private ImportCandidate fetch(OfdReceiptParser.Request request, FiscalQrParser.Result qr,
            AtomicReference<HttpsURLConnection> active, long deadline) {
        HttpsURLConnection connection = null;
        try {
            connection = connections.open(requestUrl(request));
            active.set(connection);
            deadline(deadline);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(Math.min(5000, totalMillis));
            connection.setReadTimeout(Math.min(5000, totalMillis));
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            // Android WebView cookies and credentials are never copied into this client.
            if (connection.getResponseCode() != 200) return OfdReceiptParser.fallback(qr, "OFD_UNAVAILABLE");
            deadline(deadline);
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;.*)?"))
                return OfdReceiptParser.fallback(qr, "OFD_INVALID_RESPONSE");
            String encoding = connection.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity"))
                return OfdReceiptParser.fallback(qr, "OFD_INVALID_RESPONSE");
            long length = connection.getContentLengthLong();
            if (length > MAX_BODY_BYTES) return OfdReceiptParser.fallback(qr, "OFD_INVALID_RESPONSE");
            byte[] body;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    deadline(deadline);
                    if ((long) output.size() + read > MAX_BODY_BYTES) throw OfdResponseLimit.INSTANCE;
                    output.write(buffer, 0, read);
                }
                body = output.toByteArray();
            }
            deadline(deadline);
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            ImportCandidate parsed = OfdReceiptParser.parse(text, qr);
            deadline(deadline);
            return parsed;
        } catch (ContractException | OfdResponseLimit error) {
            return OfdReceiptParser.fallback(qr, "OFD_INVALID_RESPONSE");
        } catch (Exception error) {
            return OfdReceiptParser.fallback(qr, "OFD_UNAVAILABLE");
        } finally {
            if (connection != null) {
                active.compareAndSet(connection, null);
                connection.disconnect();
            }
        }
    }

    static URL requestUrl(OfdReceiptParser.Request request) throws java.net.MalformedURLException {
        if (!request.supported()) throw new java.net.MalformedURLException("Unsupported receipt provider");
        // All values originate in FiscalQrParser's digit/decimal/calendar grammar.
        return new URL(ENDPOINT + "?f=" + request.registerNumber + "&i=" + request.fiscalSign
            + "&s=" + request.amount + "&t=" + request.timestamp);
    }
    private static void deadline(long deadline) throws java.io.IOException {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
            throw new java.io.IOException("Receipt lookup deadline");
    }
    private static final class OfdResponseLimit extends RuntimeException {
        static final OfdResponseLimit INSTANCE = new OfdResponseLimit();
    }
}
