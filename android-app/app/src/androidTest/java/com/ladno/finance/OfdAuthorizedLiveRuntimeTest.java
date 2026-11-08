package com.ladno.finance;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.OfdReceiptParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.MessageDigest;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

/** Explicitly opted-in own receipts only. No Runtime, store, ingest, UI, or ledger writes. */
@RunWith(AndroidJUnit4.class)
public final class OfdAuthorizedLiveRuntimeTest {
    private static final String INPUT = "qa-ofd-authorized.json";
    private static final String SCOPE = "APP-017-SAMPLES-4-5";

    @Test public void authorizedOwnReceiptsLookupWithoutIngest() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Live own-receipt test requires explicit opt-in", SCOPE.equals(args.getString("ladnoLiveOfdScope")));
        Assert.assertEquals("Dedicated synthetic AVD required", "Ladno_QA_API30", args.getString("ladnoSyntheticAvd"));
        Assert.assertTrue("Only synthetic emulator allowed", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean diagnostic = "transport-v1".equals(args.getString("ladnoOfdDiagnostic"));
        java.util.List<SafeTrace> traces = new java.util.concurrent.CopyOnWriteArrayList<>();
        JSONObject evidence = new JSONObject();
        evidence.put("startedAt", Instant.now().toString()).put("scope", SCOPE)
            .put("nativeDefaultClient", !diagnostic).put("realDelegatingTransport", diagnostic)
            .put("ledgerIngestCalled", false).put("uiExercised", false);
        JSONArray results = new JSONArray(); evidence.put("results", results);
        JSONObject before = ledgerBytes(context);
        boolean passed = false;
        try {
            byte[] bytes;
            try (InputStream in = context.openFileInput(INPUT); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if ((long) out.size() + n > 16384) throw new AssertionError("Authorized input exceeds fixed bound");
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
            JSONObject input = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            bytes = null;
            if (!SCOPE.equals(input.optString("scope"))) throw new AssertionError("Authorized scope mismatch");
            JSONArray samples = input.getJSONArray("samples");
            if (samples.length() != 2) throw new AssertionError("Exactly two authorized samples required");
            // Validate BOTH inputs before any HTTP, never accepting arbitrary paths/hosts/fixtures.
            FiscalQrParser.Result[] qr = new FiscalQrParser.Result[2];
            String[] expected = {"4395.00", "3547.00"};
            for (int i = 0; i < 2; i++) {
                JSONObject sample = samples.getJSONObject(i);
                if (sample.getInt("sample") != i + 4) throw new AssertionError("Authorized sample order mismatch");
                qr[i] = FiscalQrParser.parse(sample.getString("qr"));
                if (!OfdReceiptParser.Request.from(qr[i]).supported() || !expected[i].equals(qr[i].amount)
                    || !"2026-11-03".equals(qr[i].date)) throw new AssertionError("Authorized fiscal scope mismatch");
            }
            input = null; samples = null;
            OfdReceiptClient client = diagnostic ? new OfdReceiptClient(url -> {
                SafeTrace trace = new SafeTrace(); traces.add(trace);
                try {
                    // Real platform HTTPS, normal TLS/hostname checks. No mock,
                    // custom trust manager, proxy, headers, or certificate changes.
                    HttpsURLConnection real = (HttpsURLConnection) url.openConnection();
                    trace.put("connectionOpened", true);
                    return new TracedConnection(real, trace);
                } catch (IOException error) { trace.error("openConnection", error); throw error; }
                catch (RuntimeException | Error error) { trace.error("openConnection", error); throw error; }
            }, 10000) : new OfdReceiptClient();
            boolean all = true;
            for (int i = 0; i < 2; i++) {
                ImportCandidate result = client.lookup(qr[i]);
                boolean verified = "expense".equals(result.kind) && "high".equals(result.confidence)
                    && expected[i].equals(result.amount) && "KZT".equals(result.currency)
                    && "2026-11-03".equals(result.date) && result.reasons.isEmpty();
                JSONObject safe = new JSONObject();
                safe.put("sample", i + 4).put("amount", result.amount == null ? JSONObject.NULL : result.amount)
                    .put("currency", result.currency == null ? JSONObject.NULL : result.currency)
                    .put("date", result.date == null ? JSONObject.NULL : result.date)
                    .put("kind", result.kind == null ? JSONObject.NULL : result.kind)
                    .put("confidence", result.confidence).put("reasons", new JSONArray(result.reasons))
                    .put("verifiedIdentityAndFinalSale", verified);
                results.put(safe); all &= verified; qr[i] = null;
            }
            if (!all) throw new AssertionError("Authorized OFD client did not verify both permitted receipts");
            if (!before.toString().equals(ledgerBytes(context).toString())) throw new AssertionError("Ledger bytes changed during read-only lookup");
            evidence.put("noLedgerWrite", true); passed = true;
        } catch (Throwable error) {
            // Do not attach original exception/cause: it may carry raw URL/query data.
            evidence.put("safeFailureType", error.getClass().getSimpleName());
            if (error instanceof ContractException) evidence.put("safeFailureCode", ((ContractException) error).code);
            throw new AssertionError("Authorized read-only OFD check failed; inspect safe evidence");
        } finally {
            evidence.put("passed", passed).put("finishedAt", Instant.now().toString());
            JSONArray safeTraces = new JSONArray();
            for (SafeTrace trace : traces) safeTraces.put(trace.snapshot());
            evidence.put("transportDiagnostics", safeTraces);
            context.deleteFile(INPUT);
            evidence.put("privateInputRemoved", !new File(context.getFilesDir(), INPUT).exists());
            try (FileOutputStream out = context.openFileOutput("qa-app017-authorized-live.json", Context.MODE_PRIVATE)) {
                out.write((evidence.toString(2) + "\n").getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
            }
        }
    }

    private static JSONObject ledgerBytes(Context context) throws Exception {
        JSONObject result = new JSONObject();
        for (String name : new String[]{"ledger.db", "ledger.db-wal"}) {
            File file = context.getDatabasePath(name);
            if (!file.isFile()) { result.put(name, JSONObject.NULL); continue; }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] bytes = new byte[32768]; int n; while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n);
            }
            StringBuilder text = new StringBuilder();
            for (byte value : digest.digest()) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            result.put(name, text.toString());
        }
        return result;
    }

    private static final class SafeTrace {
        private final JSONObject safe = new JSONObject();
        private long bytes;
        private final int[] prefix = new int[3];
        private int prefixSize;
        synchronized void put(String key, Object value) {
            try { safe.put(key, value); } catch (org.json.JSONException impossible) { throw new AssertionError("Safe trace encoding failed"); }
        }
        synchronized void error(String phase, Throwable error) {
            put("exceptionPhase", phase); put("exceptionClass", error.getClass().getName());
        }
        synchronized void read(int count) { if (count > 0) { bytes += count; put("bodyBytesRead", bytes); } }
        synchronized void prefix(int value) {
            if (value >= 0 && prefixSize < 3) {
                prefix[prefixSize++] = value & 255;
                if (prefixSize == 3) put("bodyStartsUtf8Bom", prefix[0] == 0xef && prefix[1] == 0xbb && prefix[2] == 0xbf);
            }
        }
        synchronized void prefix(byte[] buffer, int offset, int length) {
            for (int i = 0; i < length && prefixSize < 3; i++) prefix(buffer[offset + i] & 255);
        }
        synchronized JSONObject snapshot() throws org.json.JSONException { return new JSONObject(safe.toString()); }
    }

    /** Transparent real delegate. It observes only bounded headers/classes/counts. */
    private static final class TracedConnection extends HttpsURLConnection {
        private final HttpsURLConnection real;
        private final SafeTrace trace;
        TracedConnection(HttpsURLConnection real, SafeTrace trace) { super(real.getURL()); this.real = real; this.trace = trace; }
        @Override public void setInstanceFollowRedirects(boolean enabled) { real.setInstanceFollowRedirects(enabled); trace.put("redirectsEnabled", enabled); }
        @Override public void setConnectTimeout(int value) { real.setConnectTimeout(value); trace.put("connectTimeoutMillis", value); }
        @Override public void setReadTimeout(int value) { real.setReadTimeout(value); trace.put("readTimeoutMillis", value); }
        @Override public void setUseCaches(boolean value) { real.setUseCaches(value); }
        @Override public void setRequestMethod(String method) throws ProtocolException { real.setRequestMethod(method); }
        @Override public void setRequestProperty(String key, String value) { real.setRequestProperty(key, value); }
        @Override public int getResponseCode() throws IOException {
            try { int status = real.getResponseCode(); trace.put("httpStatus", status); return status; }
            catch (IOException error) { trace.error("getResponseCode", error); throw error; }
            catch (RuntimeException | Error error) { trace.error("getResponseCode", error); throw error; }
        }
        @Override public String getContentType() {
            try {
                String raw = real.getContentType();
                String lower = raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT);
                String media = lower.split(";", 2)[0].trim();
                trace.put("contentType", java.util.Arrays.asList("application/json", "text/html", "text/plain", "application/octet-stream", "application/problem+json").contains(media) ? media : raw == null ? "absent" : "other");
                trace.put("jsonContentTypeContract", raw != null && lower.matches("application/json(?:\\s*;.*)?"));
                return raw;
            } catch (RuntimeException | Error error) { trace.error("getContentType", error); throw error; }
        }
        @Override public String getContentEncoding() {
            try {
                String raw = real.getContentEncoding();
                String lower = raw == null ? "absent" : raw.toLowerCase(java.util.Locale.ROOT);
                trace.put("contentEncoding", java.util.Arrays.asList("absent", "identity", "gzip", "br", "deflate").contains(lower) ? lower : "other");
                return raw;
            } catch (RuntimeException | Error error) { trace.error("getContentEncoding", error); throw error; }
        }
        @Override public long getContentLengthLong() {
            try { long length = real.getContentLengthLong(); trace.put("contentLength", length); return length; }
            catch (RuntimeException | Error error) { trace.error("getContentLengthLong", error); throw error; }
        }
        @Override public InputStream getInputStream() throws IOException {
            try {
                InputStream input = real.getInputStream(); trace.put("bodyOpened", true);
                return new FilterInputStream(input) {
                    @Override public int read() throws IOException {
                        try { int value = in.read(); if (value >= 0) { trace.prefix(value); trace.read(1); } return value; }
                        catch (IOException error) { trace.error("bodyRead", error); throw error; }
                        catch (RuntimeException | Error error) { trace.error("bodyRead", error); throw error; }
                    }
                    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                        try { int count = in.read(buffer, offset, length); if (count > 0) trace.prefix(buffer, offset, count); trace.read(count); return count; }
                        catch (IOException error) { trace.error("bodyRead", error); throw error; }
                        catch (RuntimeException | Error error) { trace.error("bodyRead", error); throw error; }
                    }
                    @Override public void close() throws IOException { try { super.close(); } finally { trace.put("bodyClosed", true); } }
                };
            } catch (IOException error) { trace.error("getInputStream", error); throw error; }
            catch (RuntimeException | Error error) { trace.error("getInputStream", error); throw error; }
        }
        @Override public void disconnect() {
            try { real.disconnect(); trace.put("disconnected", true); }
            catch (RuntimeException | Error error) { trace.error("disconnect", error); throw error; }
        }
        @Override public boolean usingProxy() { return real.usingProxy(); }
        @Override public void connect() throws IOException {
            try { real.connect(); } catch (IOException error) { trace.error("connect", error); throw error; }
            catch (RuntimeException | Error error) { trace.error("connect", error); throw error; }
        }
        @Override public String getCipherSuite() { return real.getCipherSuite(); }
        @Override public Certificate[] getLocalCertificates() { return real.getLocalCertificates(); }
        @Override public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException { return real.getServerCertificates(); }
    }
}
