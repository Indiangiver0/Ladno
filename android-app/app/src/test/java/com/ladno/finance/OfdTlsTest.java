package com.ladno.finance;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic forwarding/guard checks. Actual cryptographic handshakes belong to native QA. */
public final class OfdTlsTest {
    private static X509Certificate auxiliary() throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(OfdTls.intermediateBytes()));
    }
    private static OfdTls.ChainCompletion wrapper(Recording delegate) {
        return new OfdTls.ChainCompletion(delegate, OfdTls.intermediateBytes());
    }
    private static SSLSession session(String host, int port) {
        return (SSLSession) Proxy.newProxyInstance(OfdTlsTest.class.getClassLoader(), new Class<?>[]{SSLSession.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getPeerHost")) return host;
                if (method.getName().equals("getPeerPort")) return port;
                if (method.getReturnType().equals(boolean.class)) return false;
                if (method.getReturnType().equals(int.class)) return 0;
                if (method.getReturnType().equals(long.class)) return 0L;
                return null;
            });
    }

    @Test public void officialAuxiliaryIsBoundedAndNeverAddedToAcceptedRoots() throws Exception {
        X509Certificate certificate = auxiliary();
        assertEquals(1499, OfdTls.intermediateBytes().length);
        assertEquals(0, certificate.getBasicConstraints()); assertTrue(certificate.getKeyUsage()[5]);
        assertEquals("2028-09-05T23:59:59Z", certificate.getNotAfter().toInstant().toString());
        Recording delegate = new Recording();
        assertSame(delegate.roots, wrapper(delegate).getAcceptedIssuers());
        X509ExtendedTrustManager defaults = OfdTls.defaultTrustManager();
        X509Certificate[] before = defaults.getAcceptedIssuers();
        assertArrayEquals(before, new OfdTls.ChainCompletion(defaults, OfdTls.intermediateBytes()).getAcceptedIssuers());
        for (X509Certificate root : before) assertFalse("Auxiliary is not a trust anchor", root.equals(certificate));
    }

    @Test public void originalSuccessUsesOriginalArrayAndNeverNeedsAuxiliary() throws Exception {
        Recording delegate = new Recording(); delegate.allowOriginal = true;
        OfdTls.ChainCompletion value = new OfdTls.ChainCompletion(delegate, new byte[]{1, 2, 3});
        X509Certificate[] chain = new X509Certificate[]{new Leaf(auxiliary())};
        Socket socket = new PeerSocket(null);
        value.checkServerTrusted(chain, "RSA", socket);
        assertEquals(1, delegate.chains.size()); assertSame(chain, delegate.chains.get(0)); assertSame(socket, delegate.contexts.get(0));
    }

    @Test public void socketRepairForwardsExactContextAuthTypeAndKeepsInputArray() throws Exception {
        Recording delegate = new Recording(); Leaf leaf = new Leaf(auxiliary());
        X509Certificate[] chain = new X509Certificate[]{leaf};
        PeerSocket socket = new PeerSocket(session(OfdTls.HOST, 443));
        wrapper(delegate).checkServerTrusted(chain, "ECDHE_RSA", socket);
        assertEquals(2, delegate.chains.size()); assertSame(chain, delegate.chains.get(0));
        assertEquals(1, chain.length); assertSame(leaf, chain[0]);
        assertSame(leaf, delegate.chains.get(1)[0]); assertEquals(auxiliary(), delegate.chains.get(1)[1]);
        assertSame(socket, delegate.contexts.get(0)); assertSame(socket, delegate.contexts.get(1));
        assertEquals("ECDHE_RSA", delegate.authTypes.get(1));
    }

    @Test public void engineRepairForwardsSameEngineInsteadOfHostlessOverload() throws Exception {
        Recording delegate = new Recording();
        PeerEngine engine = new PeerEngine(OfdTls.HOST, 443, session(OfdTls.HOST, 443));
        wrapper(delegate).checkServerTrusted(new X509Certificate[]{new Leaf(auxiliary())}, "RSA", engine);
        assertEquals(2, delegate.contexts.size()); assertSame(engine, delegate.contexts.get(0)); assertSame(engine, delegate.contexts.get(1));
    }

    @Test public void absentWrongHostWrongPortAndNonTlsContextsCannotRepair() throws Exception {
        for (Socket socket : new Socket[]{null, new Socket(), new PeerSocket(null),
                new PeerSocket(session("wrong.invalid", 443)), new PeerSocket(session(OfdTls.HOST, 444))}) {
            Recording delegate = new Recording();
            expectRejected(() -> wrapper(delegate).checkServerTrusted(new X509Certificate[]{new Leaf(auxiliary())}, "RSA", socket));
            assertEquals(1, delegate.chains.size());
        }
        for (PeerEngine engine : new PeerEngine[]{new PeerEngine(OfdTls.HOST, 443, null),
                new PeerEngine("wrong.invalid", 443, session("wrong.invalid", 443)),
                new PeerEngine(OfdTls.HOST, 444, session(OfdTls.HOST, 444))}) {
            Recording delegate = new Recording();
            expectRejected(() -> wrapper(delegate).checkServerTrusted(new X509Certificate[]{new Leaf(auxiliary())}, "RSA", engine));
            assertEquals(1, delegate.chains.size());
        }
    }

    @Test public void substitutedAuxiliaryUnknownIssuerBadSignatureAndExpiredLeafCannotRepair() throws Exception {
        byte[] changed = OfdTls.intermediateBytes(); changed[30] ^= 1;
        Recording substituted = new Recording();
        expectRejected(() -> new OfdTls.ChainCompletion(substituted, changed).checkServerTrusted(
            new X509Certificate[]{new Leaf(auxiliary())}, "RSA", new PeerSocket(session(OfdTls.HOST, 443))));
        assertEquals(1, substituted.chains.size());
        for (int scenario = 0; scenario < 3; scenario++) {
            Recording delegate = new Recording(); Leaf leaf = new Leaf(auxiliary());
            if (scenario == 0) leaf.issuer = new X500Principal("CN=Unknown synthetic issuer");
            if (scenario == 1) leaf.signatureValid = false;
            if (scenario == 2) leaf.expired = true;
            expectRejected(() -> wrapper(delegate).checkServerTrusted(new X509Certificate[]{leaf}, "RSA", new PeerSocket(session(OfdTls.HOST, 443))));
            assertEquals(1, delegate.chains.size());
        }
    }

    @Test public void expiredAuxiliaryCannotRepairButDoesNotBreakOriginalSuccess() throws Exception {
        // Fault injection into the test object only; production never replaces its parsed asset.
        Leaf expiredAuxiliary = new Leaf(auxiliary()); expiredAuxiliary.expired = true;
        Leaf leaf = new Leaf(auxiliary()); leaf.issuer = expiredAuxiliary.getSubjectX500Principal();
        Recording delegate = new Recording(); OfdTls.ChainCompletion value = wrapper(delegate);
        java.lang.reflect.Field field = OfdTls.ChainCompletion.class.getDeclaredField("intermediate");
        field.setAccessible(true); field.set(value, expiredAuxiliary);
        expectRejected(() -> value.checkServerTrusted(new X509Certificate[]{leaf}, "RSA", new PeerSocket(session(OfdTls.HOST, 443))));
        assertEquals(1, delegate.chains.size());
        delegate.allowOriginal = true;
        value.checkServerTrusted(new X509Certificate[]{leaf}, "RSA", new PeerSocket(session(OfdTls.HOST, 443)));
        assertEquals(2, delegate.chains.size());
    }

    @Test public void nullEmptyMultipleAndNullLeafChainsCannotBeRepaired() throws Exception {
        X509Certificate leaf = new Leaf(auxiliary());
        for (X509Certificate[] chain : new X509Certificate[][]{null, {}, {null}, {leaf, leaf}}) {
            Recording delegate = new Recording();
            expectRejected(() -> wrapper(delegate).checkServerTrusted(chain, "RSA", new PeerSocket(session(OfdTls.HOST, 443))));
            assertEquals(1, delegate.chains.size());
        }
    }

    @Test public void repairedChainMustStillPassDelegateTrustAlgorithmAndHostnamePolicies() throws Exception {
        for (String rejection : new String[]{"untrusted root", "expired certificate", "disabled algorithm", "hostname mismatch"}) {
            Recording delegate = new Recording(); delegate.rejectCompleted = rejection;
            CertificateException error = expectRejected(() -> wrapper(delegate).checkServerTrusted(
                new X509Certificate[]{new Leaf(auxiliary())}, "RSA", new PeerSocket(session(OfdTls.HOST, 443))));
            assertEquals(rejection, error.getMessage()); assertEquals(2, delegate.chains.size());
        }
    }

    @Test public void allClientAndTwoArgumentServerCallsAreUnmodifiedAndDoNotRepair() throws Exception {
        Recording delegate = new Recording(); OfdTls.ChainCompletion value = wrapper(delegate);
        X509Certificate[] chain = new X509Certificate[]{new Leaf(auxiliary())};
        Socket socket = new PeerSocket(session(OfdTls.HOST, 443));
        SSLEngine engine = new PeerEngine(OfdTls.HOST, 443, session(OfdTls.HOST, 443));
        value.checkClientTrusted(chain, "RSA"); value.checkClientTrusted(chain, "RSA", socket); value.checkClientTrusted(chain, "RSA", engine);
        assertEquals(3, delegate.clientCalls); assertSame(chain, delegate.lastClientChain);
        assertEquals("RSA", delegate.lastClientAuth); assertSame(engine, delegate.lastClientContext);
        expectRejected(() -> value.checkServerTrusted(chain, "RSA")); assertEquals(1, delegate.chains.size());
        assertEquals("two-argument", delegate.contexts.get(0));
    }

    @Test public void factoryIsPerConnectionAndDoesNotChangeGlobalVerifierOrDefaults() throws Exception {
        Object verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        Object globalFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        SSLContext globalContext = SSLContext.getDefault();
        HttpsURLConnection connection = OfdTls.open(new URL("https://consumer.oofd.kz/api/consumer-proxy/api/tickets/get-by-url"));
        try {
            assertSame(verifier, connection.getHostnameVerifier()); assertSame(OfdTls.socketFactory(), connection.getSSLSocketFactory());
            assertSame(verifier, HttpsURLConnection.getDefaultHostnameVerifier());
            assertSame(globalFactory, HttpsURLConnection.getDefaultSSLSocketFactory()); assertSame(globalContext, SSLContext.getDefault());
        } finally { connection.disconnect(); }
        for (String url : new String[]{"http://consumer.oofd.kz/", "https://wrong.invalid/", "https://consumer.oofd.kz:444/", "https://user@consumer.oofd.kz/"}) {
            try { OfdTls.open(new URL(url)); fail("Non-provider rejected before network"); }
            catch (java.io.IOException expected) { }
        }
    }

    interface Check { void run() throws Exception; }
    static CertificateException expectRejected(Check value) throws Exception {
        try { value.run(); fail("Expected system rejection"); return null; }
        catch (CertificateException expected) { return expected; }
    }
    private static final class Recording extends X509ExtendedTrustManager {
        final X509Certificate[] roots = new X509Certificate[0];
        final List<X509Certificate[]> chains = new ArrayList<>();
        final List<Object> contexts = new ArrayList<>(); final List<String> authTypes = new ArrayList<>();
        boolean allowOriginal; String rejectCompleted; int clientCalls;
        X509Certificate[] lastClientChain; String lastClientAuth; Object lastClientContext;
        void server(X509Certificate[] chain, String auth, Object context) throws CertificateException {
            chains.add(chain); contexts.add(context); authTypes.add(auth);
            if (allowOriginal) return;
            if (chains.size() == 1 || chain == null || chain.length != 2) throw new CertificateException("Original system rejection");
            if (rejectCompleted != null) throw new CertificateException(rejectCompleted);
        }
        void client(X509Certificate[] chain, String auth, Object context) { clientCalls++; lastClientChain = chain; lastClientAuth = auth; lastClientContext = context; }
        public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { server(c, a, "two-argument"); }
        public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { server(c, a, s); }
        public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { server(c, a, e); }
        public void checkClientTrusted(X509Certificate[] c, String a) { client(c, a, "two-argument"); }
        public void checkClientTrusted(X509Certificate[] c, String a, Socket s) { client(c, a, s); }
        public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) { client(c, a, e); }
        public X509Certificate[] getAcceptedIssuers() { return roots; }
    }
    private static final class PeerSocket extends SSLSocket {
        final SSLSession session; PeerSocket(SSLSession session) { this.session = session; }
        public SSLSession getHandshakeSession() { return session; } public SSLSession getSession() { return session; }
        public String[] getSupportedCipherSuites() { return new String[0]; } public String[] getEnabledCipherSuites() { return new String[0]; }
        public void setEnabledCipherSuites(String[] v) { } public String[] getSupportedProtocols() { return new String[0]; }
        public String[] getEnabledProtocols() { return new String[0]; } public void setEnabledProtocols(String[] v) { }
        public void addHandshakeCompletedListener(HandshakeCompletedListener v) { } public void removeHandshakeCompletedListener(HandshakeCompletedListener v) { }
        public void startHandshake() { } public void setUseClientMode(boolean v) { } public boolean getUseClientMode() { return true; }
        public void setNeedClientAuth(boolean v) { } public boolean getNeedClientAuth() { return false; }
        public void setWantClientAuth(boolean v) { } public boolean getWantClientAuth() { return false; }
        public void setEnableSessionCreation(boolean v) { } public boolean getEnableSessionCreation() { return true; }
    }
    private static final class PeerEngine extends SSLEngine {
        final SSLSession session; PeerEngine(String host, int port, SSLSession session) { super(host, port); this.session = session; }
        public SSLSession getHandshakeSession() { return session; } public SSLSession getSession() { return session; }
        public SSLEngineResult wrap(ByteBuffer[] a, int b, int c, ByteBuffer d) { return null; }
        public SSLEngineResult unwrap(ByteBuffer a, ByteBuffer[] b, int c, int d) { return null; }
        public Runnable getDelegatedTask() { return null; } public void closeInbound() { } public boolean isInboundDone() { return false; }
        public void closeOutbound() { } public boolean isOutboundDone() { return false; }
        public String[] getSupportedCipherSuites() { return new String[0]; } public String[] getEnabledCipherSuites() { return new String[0]; }
        public void setEnabledCipherSuites(String[] v) { } public String[] getSupportedProtocols() { return new String[0]; }
        public String[] getEnabledProtocols() { return new String[0]; } public void setEnabledProtocols(String[] v) { }
        public void beginHandshake() { } public SSLEngineResult.HandshakeStatus getHandshakeStatus() { return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING; }
        public void setUseClientMode(boolean v) { } public boolean getUseClientMode() { return true; }
        public void setNeedClientAuth(boolean v) { } public boolean getNeedClientAuth() { return false; }
        public void setWantClientAuth(boolean v) { } public boolean getWantClientAuth() { return false; }
        public void setEnableSessionCreation(boolean v) { } public boolean getEnableSessionCreation() { return true; }
    }
    /** A deliberately non-cryptographic leaf for spy-delegate control-flow tests only. */
    private static final class Leaf extends X509Certificate {
        final X509Certificate source; X500Principal issuer; boolean signatureValid = true, expired;
        Leaf(X509Certificate source) { this.source = source; issuer = source.getSubjectX500Principal(); }
        public void checkValidity() throws CertificateExpiredException { if (expired) throw new CertificateExpiredException(); }
        public void checkValidity(Date date) throws CertificateExpiredException { checkValidity(); }
        public void verify(PublicKey key) throws SignatureException { if (!signatureValid) throw new SignatureException(); assertEquals(source.getPublicKey(), key); }
        public void verify(PublicKey key, String provider) throws SignatureException { verify(key); }
        public X500Principal getIssuerX500Principal() { return issuer; } public X500Principal getSubjectX500Principal() { return new X500Principal("CN=synthetic leaf"); }
        public int getVersion() { return 3; } public BigInteger getSerialNumber() { return BigInteger.ONE; }
        public Principal getIssuerDN() { return issuer; } public Principal getSubjectDN() { return getSubjectX500Principal(); }
        public Date getNotBefore() { return source.getNotBefore(); } public Date getNotAfter() { return source.getNotAfter(); }
        public byte[] getTBSCertificate() { return new byte[0]; } public byte[] getSignature() { return new byte[0]; }
        public String getSigAlgName() { return "SHA256withRSA"; } public String getSigAlgOID() { return "1.2.840.113549.1.1.11"; }
        public byte[] getSigAlgParams() { return null; } public boolean[] getIssuerUniqueID() { return null; }
        public boolean[] getSubjectUniqueID() { return null; } public boolean[] getKeyUsage() { return null; }
        public int getBasicConstraints() { return -1; } public byte[] getEncoded() { return new byte[0]; }
        public PublicKey getPublicKey() { return source.getPublicKey(); } public String toString() { return "synthetic leaf"; }
        public Set<String> getCriticalExtensionOIDs() { return null; } public Set<String> getNonCriticalExtensionOIDs() { return null; }
        public byte[] getExtensionValue(String oid) { return null; } public boolean hasUnsupportedCriticalExtension() { return false; }
    }
}
