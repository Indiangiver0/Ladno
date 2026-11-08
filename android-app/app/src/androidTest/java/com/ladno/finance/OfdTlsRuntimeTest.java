package com.ladno.finance;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Collections;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicitly opted-in, public TLS-only probe: no HTTP, QR, receipt IDs or ledger calls. */
@RunWith(AndroidJUnit4.class)
public final class OfdTlsRuntimeTest {
    @Test public void systemPolicyWithAuxiliaryCompletesPublicTlsAndRejectsWrongPeer() throws Exception {
        Assume.assumeTrue("Explicit public TLS test opt-in", "true".equals(
            InstrumentationRegistry.getArguments().getString("ladnoPublicOfdTls")));
        Assert.assertEquals("Ladno_QA_API30", InstrumentationRegistry.getArguments().getString("ladnoSyntheticAvd"));
        Assert.assertTrue("Synthetic emulator only", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        X509ExtendedTrustManager delegate = OfdTls.defaultTrustManager();
        X509Certificate[] originalRoots = delegate.getAcceptedIssuers();
        Assert.assertArrayEquals(originalRoots,
            new OfdTls.ChainCompletion(delegate, OfdTls.intermediateBytes()).getAcceptedIssuers());
        Object originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        handshake(OfdTls.HOST);
        Assert.assertSame(originalVerifier, HttpsURLConnection.getDefaultHostnameVerifier());
        try { handshake("wrong.invalid"); Assert.fail("Wrong TLS peer must remain rejected"); }
        catch (SSLHandshakeException expected) { /* No message/URL/body logs. */ }
    }

    private static void handshake(String peer) throws Exception {
        try (Socket tcp = new Socket()) {
            tcp.connect(new InetSocketAddress(OfdTls.HOST, 443), 5000);
            try (SSLSocket tls = (SSLSocket) OfdTls.socketFactory().createSocket(tcp, peer, 443, true)) {
                tls.setSoTimeout(5000);
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(Collections.singletonList(new SNIHostName(peer)));
                tls.setSSLParameters(parameters);
                tls.startHandshake();
                Assert.assertTrue(tls.getSession().isValid());
            }
        }
    }
}
