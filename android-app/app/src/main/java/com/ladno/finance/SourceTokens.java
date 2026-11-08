package com.ladno.finance;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.ladno.finance.core.ContractException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** No raw key/tag/account/ref is written to disk, including lifecycle bookkeeping. */
final class SourceTokens {
    private static final String ALIAS = "ladno.capture.hmac.v1";
    private final SharedPreferences identity;
    private final SharedPreferences lifecycles;
    private SecretKey key;
    private boolean uncertain;
    private boolean resumedAfterGap;

    SourceTokens(Context context) {
        identity = context.getSharedPreferences("capture_identity", Context.MODE_PRIVATE);
        lifecycles = context.getSharedPreferences("capture_lifecycles", Context.MODE_PRIVATE);
        resumedAfterGap = identity.getBoolean("listener_open", false);
        loadKey();
    }

    private void loadKey() {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            boolean existed = identity.getBoolean("created", false);
            if (!store.containsAlias(ALIAS)) {
                uncertain = existed;
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256).build());
                key = generator.generateKey();
            } else key = (SecretKey) store.getKey(ALIAS, null);
            if (key == null) uncertain = true;
            // Identity loss is persistent; never silently regain confident identity by a restart.
            uncertain |= identity.getBoolean("identity_lost", false);
            if (!identity.edit().putBoolean("created", true).putBoolean("identity_lost", uncertain).commit()) uncertain = true;
        } catch (Exception e) {
            key = null;
            uncertain = true;
        }
    }

    synchronized String hmac(String purpose, String namespace, String... fields) {
        if (key == null) loadKey();
        if (key == null) throw unavailable();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            field(mac, "ladno-source-v1");
            field(mac, purpose);
            field(mac, namespace);
            for (String value : fields) field(mac, value);
            return hex(mac.doFinal());
        } catch (Exception e) {
            key = null;
            uncertain = true;
            throw unavailable();
        }
    }

    private void field(Mac mac, String value) {
        if (value == null) {
            mac.update(ByteBuffer.allocate(4).putInt(-1).array());
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            mac.update(bytes);
        }
    }

    synchronized void listenerConnected() {
        if (!identity.edit().putBoolean("listener_open", true).commit()) resumedAfterGap = true;
    }

    synchronized void listenerDisconnected() {
        captureGap();
    }

    synchronized void captureGap() {
        resumedAfterGap = true;
        identity.edit().putBoolean("listener_open", true).apply();
    }

    synchronized NotificationTokens notification(String namespace, String rawKey, String rawTag,
                                                  long postTime, String text) {
        String base = hmac("notification-lifecycle-index", namespace, rawKey, rawTag);
        String lifecycle = lifecycles.getString(base, null);
        boolean existing = lifecycle != null;
        boolean lost = uncertain;
        if (lifecycle == null) {
            lifecycle = UUID.randomUUID().toString();
            // Only opaque HMAC -> random lifecycle is durable, never raw -> token mapping.
            if (!lifecycles.edit().putString(base, lifecycle).commit()) lost = true;
        }
        String source = hmac("notification-source", namespace, rawKey, rawTag, lifecycle);
        String revision = hmac("notification-revision", namespace, rawKey, rawTag, lifecycle,
            Long.toString(postTime), text);
        lost |= existing && resumedAfterGap;
        return new NotificationTokens(source, revision, lost);
    }

    synchronized void removed(String namespace, String rawKey, String rawTag) {
        String base = hmac("notification-lifecycle-index", namespace, rawKey, rawTag);
        lifecycles.edit().remove(base).commit();
    }

    synchronized boolean isUncertain() { return uncertain || key == null; }
    synchronized boolean isAvailable() { return key != null; }

    private ContractException unavailable() {
        return new ContractException("UNAVAILABLE", null, "Защищённая идентификация источника недоступна. Попробуйте позже.");
    }

    static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            result[2 * i] = digits[(bytes[i] & 255) >>> 4];
            result[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    static final class NotificationTokens {
        final String sourceKeyToken;
        final String revisionToken;
        final boolean uncertain;
        NotificationTokens(String sourceKeyToken, String revisionToken, boolean uncertain) {
            this.sourceKeyToken = sourceKeyToken;
            this.revisionToken = revisionToken;
            this.uncertain = uncertain;
        }
    }
}
