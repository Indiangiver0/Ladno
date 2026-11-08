package com.ladno.finance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/** Supplies one missing, UNTRUSTED intermediate to the unchanged system validator. */
final class OfdTls {
    static final String HOST = "consumer.oofd.kz";
    static final String INTERMEDIATE_SHA256 = "43CAC31EF8E8BA1B4B16B8206E4C0A26C5BADB2FC3AA09E90170E41B66C2FD64";
    // Public GoGetSSL RSA DV CA, official HTTPS catalogue. Not a root or a trust-store entry.
    // https://www.gogetssl.com/wiki/intermediate-certificates/gogetssl-intermediate-root-certificates/
    // https://gogetssl-cdn.s3.eu-central-1.amazonaws.com/wiki/gogetssl-rsa-dv-ca.crt
    private static final String INTERMEDIATE_BASE64 = "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";
    private static volatile SSLSocketFactory factory;

    private OfdTls() { }

    static HttpsURLConnection open(URL url) throws IOException {
        if (!"https".equals(url.getProtocol()) || !HOST.equals(url.getHost())
                || (url.getPort() != -1 && url.getPort() != 443) || url.getUserInfo() != null)
            throw new IOException("Unsupported TLS endpoint");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try { connection.setSSLSocketFactory(socketFactory()); }
        catch (GeneralSecurityException error) {
            connection.disconnect();
            throw new IOException("TLS configuration unavailable", error);
        }
        // HttpsURLConnection's default HostnameVerifier remains unchanged.
        return connection;
    }

    static SSLSocketFactory socketFactory() throws GeneralSecurityException {
        SSLSocketFactory current = factory;
        if (current != null) return current;
        synchronized (OfdTls.class) {
            if (factory == null) {
                X509ExtendedTrustManager delegate = defaultTrustManager();
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new ChainCompletion(delegate, intermediateBytes())}, null);
                factory = context.getSocketFactory();
            }
            return factory;
        }
    }

    static X509ExtendedTrustManager defaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init((KeyStore) null);
        for (TrustManager manager : managers.getTrustManagers())
            if (manager instanceof X509ExtendedTrustManager) return (X509ExtendedTrustManager) manager;
        // Do not repair through a context-free two-argument validator on an unknown platform.
        throw new GeneralSecurityException("Context-aware system validator unavailable");
    }

    static byte[] intermediateBytes() { return Base64.getDecoder().decode(INTERMEDIATE_BASE64); }

    static final class ChainCompletion extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private final X509Certificate intermediate;

        ChainCompletion(X509ExtendedTrustManager delegate, byte[] publicDer) {
            if (delegate == null) throw new IllegalArgumentException("System validator required");
            this.delegate = delegate;
            this.intermediate = checkedIntermediate(publicDer);
        }

        private static X509Certificate checkedIntermediate(byte[] der) {
            if (der == null) return null;
            try {
                byte[] expected = new byte[INTERMEDIATE_SHA256.length() / 2];
                for (int i = 0; i < expected.length; i++)
                    expected[i] = (byte) Integer.parseInt(INTERMEDIATE_SHA256.substring(i * 2, i * 2 + 2), 16);
                if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(der))) return null;
                ByteArrayInputStream input = new ByteArrayInputStream(der);
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
                boolean[] usage = certificate.getKeyUsage();
                if (input.available() != 0 || certificate.getBasicConstraints() < 0
                        || usage == null || usage.length <= 5 || !usage[5]) return null;
                // Validity is checked only when used: an expired unused auxiliary must not
                // break an already complete chain accepted by the original validator.
                return certificate;
            } catch (GeneralSecurityException | RuntimeException error) { return null; }
        }

        private X509Certificate[] completed(X509Certificate[] chain) throws CertificateException {
            if (intermediate == null || chain == null || chain.length != 1 || chain[0] == null
                    || !chain[0].getIssuerX500Principal().equals(intermediate.getSubjectX500Principal()))
                throw new CertificateException("Unsupported incomplete certificate chain");
            try {
                intermediate.checkValidity();
                chain[0].checkValidity();
                chain[0].verify(intermediate.getPublicKey());
            } catch (GeneralSecurityException | RuntimeException error) {
                throw new CertificateException("Incomplete certificate chain rejected", error);
            }
            return new X509Certificate[]{chain[0], intermediate};
        }

        private static boolean provider(SSLSession session) {
            return session != null && HOST.equals(session.getPeerHost()) && session.getPeerPort() == 443;
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            try { delegate.checkServerTrusted(chain, authType, socket); }
            catch (CertificateException first) {
                if (!(socket instanceof SSLSocket) || !provider(((SSLSocket) socket).getHandshakeSession())) throw first;
                X509Certificate[] augmented;
                try { augmented = completed(chain); }
                catch (CertificateException rejected) { throw first; }
                delegate.checkServerTrusted(augmented, authType, socket);
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            try { delegate.checkServerTrusted(chain, authType, engine); }
            catch (CertificateException first) {
                if (engine == null || !HOST.equals(engine.getPeerHost()) || engine.getPeerPort() != 443
                        || !provider(engine.getHandshakeSession())) throw first;
                X509Certificate[] augmented;
                try { augmented = completed(chain); }
                catch (CertificateException rejected) { throw first; }
                delegate.checkServerTrusted(augmented, authType, engine);
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException { delegate.checkClientTrusted(chain, authType, socket); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException { delegate.checkClientTrusted(chain, authType, engine); }
        @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    }
}
