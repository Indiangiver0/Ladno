package com.ladno.finance.storage;

import com.ladno.finance.core.ContractException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class IngestSourceTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static JSONObject source() throws Exception {
        return new JSONObject().put("kind", "notification").put("eventId", "20000000-0000-4000-8000-000000000001")
                .put("sourceKeyToken", TOKEN).put("revisionToken", TOKEN).put("accountSurrogate", JSONObject.NULL)
                .put("paymentRefToken", JSONObject.NULL).put("packageName", "com.example.bank")
                .put("label", "Уведомление").put("capturedAt", "2026-11-01T00:30:00Z").put("postTime", 1793493000000L);
    }
    @Test public void identityNeedsSafeScopedTokensAndReferenceNeedsAccountProvider() throws Exception {
        IngestSource value = IngestSource.fromJson(source());
        assertTrue(value.hasIdentity()); assertFalse(value.hasStrongReference());
        assertTrue(IngestSource.fromJson(source().put("accountSurrogate", TOKEN).put("paymentRefToken", TOKEN)).hasStrongReference());
        assertFalse(IngestSource.fromJson(source().put("sourceKeyToken", JSONObject.NULL)).hasIdentity());
    }
    @Test public void labelsAreFixedAndCannotPersistSourceAuthoredMetadata() throws Exception {
        IngestSource value = IngestSource.fromJson(source().put("label", "synthetic sensitive label"));
        assertEquals("Уведомление", value.label);
    }
    @Test public void rawUnknownFieldsAndInvalidTokensAreRejected() throws Exception {
        for (JSONObject value : new JSONObject[]{source().put("rawKey", "synthetic"), source().put("revisionToken", JSONObject.NULL),
                source().put("sourceKeyToken", "raw source id"), source().put("postTime", "1793493000000"),
                source().put("postTime", 1.5), source().put("packageName", "../private"), source().put("capturedAt", "2026-11-01")}) {
            try { IngestSource.fromJson(value); fail("Expected validation rejection"); }
            catch (ContractException expected) { assertEquals("VALIDATION", expected.code); }
        }
    }
}
