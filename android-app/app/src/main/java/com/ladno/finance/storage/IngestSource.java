package com.ladno.finance.storage;

import com.ladno.finance.core.JsonChecks;
import org.json.JSONObject;

/** Validated safe provenance only; raw key/account/reference fields have no representation. */
public final class IngestSource {
    public final String kind, eventId, sourceKeyToken, revisionToken, accountSurrogate, paymentRefToken,
            packageName, label, capturedAt;
    public final Long postTime;
    private IngestSource(JSONObject value) {
        JsonChecks.keys(value, "kind", "eventId", "sourceKeyToken", "revisionToken", "accountSurrogate",
                "paymentRefToken", "packageName", "label", "capturedAt", "postTime");
        kind = JsonChecks.choice(value, "kind", "notification", "receipt");
        eventId = JsonChecks.uuid(value, "eventId", false);
        sourceKeyToken = token(value, "sourceKeyToken", true);
        revisionToken = token(value, "revisionToken", false);
        accountSurrogate = token(value, "accountSurrogate", true);
        paymentRefToken = token(value, "paymentRefToken", true);
        packageName = JsonChecks.nullableString(value, "packageName", 255);
        if (packageName != null) packageName(packageName);
        if (kind.equals("notification") && packageName == null) throw JsonChecks.invalid("packageName");
        // Source labels are deliberately fixed: arbitrary source-authored metadata is transient.
        String requestedLabel = JsonChecks.string(value, "label", 80);
        if (requestedLabel.trim().isEmpty()) throw JsonChecks.invalid("label");
        label = kind.equals("receipt") ? "Чек" : "Уведомление";
        capturedAt = JsonChecks.instant(JsonChecks.string(value, "capturedAt", 40), "capturedAt");
        postTime = JsonChecks.isNull(value, "postTime") ? null : JsonChecks.integer(value, "postTime");
        if (postTime != null && postTime < 0) throw JsonChecks.invalid("postTime");
    }
    public static IngestSource fromJson(JSONObject value) { return new IngestSource(value); }
    public boolean hasIdentity() { return sourceKeyToken != null; }
    public boolean hasStrongReference() { return accountSurrogate != null && paymentRefToken != null && packageName != null; }
    public static String packageName(String value) {
        if (value == null || value.length() > 255 || !value.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")) throw JsonChecks.invalid("packages");
        return value;
    }
    private static String token(JSONObject value, String key, boolean nullable) {
        String token = nullable ? JsonChecks.nullableString(value, key, 64) : JsonChecks.string(value, key, 64);
        if (token != null && !token.matches("[0-9a-f]{64}")) throw JsonChecks.invalid(key);
        return token;
    }
}
