package com.ladno.finance;

import android.webkit.JavascriptInterface;
import com.ladno.finance.core.ContractException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** The only method exposed to the six immutable local UI assets. */
public final class AppBridge {
    private static final Set<String> ACTIONS = new HashSet<>(java.util.Arrays.asList("state", "settings", "saveTransaction",
        "deleteTransaction", "saveCategory", "deleteCategory", "saveSources", "reviewCandidate",
        "scanCamera", "scanGallery", "openNotificationAccess"));
    private final NativeRuntime runtime;

    AppBridge(NativeRuntime runtime) { this.runtime = runtime; }

    @JavascriptInterface
    public String request(String action, String payloadJson) {
        try {
            if (action == null || !ACTIONS.contains(action))
                throw new ContractException("UNKNOWN_ACTION", null, "Неизвестное действие.");
            if (payloadJson == null) throw new ContractException("BAD_JSON", null, "Ожидался JSON-объект.");
            if (payloadJson.length() > 65536 || payloadJson.getBytes(StandardCharsets.UTF_8).length > 65536)
                throw new ContractException("LIMIT_EXCEEDED", null, "Запрос слишком большой.");
            JSONObject payload = NativeJson.parseObject(payloadJson);
            validate(action, payload);
            JSONObject data;
            if (action.equals("state")) {
                data = runtime.state(nullable(payload, "month"), nullable(payload, "currency"));
            } else if (action.equals("settings")) {
                data = runtime.store.execute(action, payload);
                runtime.refresh();
            } else {
                runtime.requireSetup();
                if (action.equals("scanCamera") || action.equals("scanGallery") || action.equals("openNotificationAccess")) {
                    data = runtime.queueAction(action);
                } else {
                    if (action.equals("saveSources")) runtime.validatePackages(payload.optJSONArray("packages"));
                    data = runtime.store.execute(action, payload);
                    if (action.equals("saveSources")) runtime.tokens.captureGap();
                    runtime.refresh();
                }
            }
            return NativeJson.object("ok", true, "data", data, "error", null).toString();
        } catch (ContractException e) {
            return failure(e.code, e.field, e.getMessage());
        } catch (RuntimeException e) {
            return failure("INTERNAL", null, "Не удалось выполнить действие.");
        }
    }

    private String failure(String code, String field, String message) {
        return NativeJson.object("ok", false, "data", null,
            "error", NativeJson.error(code, field, message)).toString();
    }

    private String nullable(JSONObject value, String name) {
        return !value.has(name) || value.isNull(name) ? null : value.optString(name);
    }

    private void validate(String action, JSONObject value) {
        switch (action) {
            case "state":
                if (value.length() > 2) NativeJson.invalid(null);
                java.util.Iterator<String> fields = value.keys();
                while (fields.hasNext()) {
                    String name = fields.next();
                    if (!name.equals("month") && !name.equals("currency")) NativeJson.invalid(name);
                    NativeJson.string(value, name, name.equals("month") ? 7 : 3, true);
                }
                break;
            case "settings":
                NativeJson.keys(value, "primaryCurrency", "zoneId", "datePolicy");
                NativeJson.string(value, "primaryCurrency", 3, false);
                NativeJson.string(value, "zoneId", 128, false);
                if (!"POST_TIME_IN_ZONE".equals(NativeJson.string(value, "datePolicy", 32, false))) NativeJson.invalid("datePolicy");
                break;
            case "saveTransaction":
                NativeJson.keys(value, "id", "expectedRevision", "kind", "date", "amount", "currency", "categoryId", "note");
                NativeJson.uuid(value, "id", true);
                NativeJson.revision(value, "expectedRevision", true);
                transaction(value);
                break;
            case "saveCategory":
                NativeJson.keys(value, "id", "expectedRevision", "name", "kind");
                NativeJson.uuid(value, "id", true);
                NativeJson.revision(value, "expectedRevision", true);
                NativeJson.string(value, "name", 160, false);
                kind(value);
                break;
            case "deleteTransaction": case "deleteCategory":
                NativeJson.keys(value, "id", "expectedRevision");
                NativeJson.uuid(value, "id", false);
                NativeJson.revision(value, "expectedRevision", false);
                break;
            case "saveSources":
                NativeJson.keys(value, "packages");
                Object packages = value.opt("packages");
                if (!(packages instanceof JSONArray)) NativeJson.invalid("packages");
                JSONArray list = (JSONArray) packages;
                if (list.length() > 1000) NativeJson.invalid("packages");
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < list.length(); i++) {
                    Object item = list.opt(i);
                    if (!(item instanceof String) || ((String) item).length() > 255 || !seen.add((String) item)) NativeJson.invalid("packages");
                }
                break;
            case "reviewCandidate":
                String decision = NativeJson.string(value, "decision", 16, false);
                if (decision.equals("post")) {
                    NativeJson.keys(value, "id", "expectedRevision", "decision", "transaction", "confirmDistinct");
                    Object txn = value.opt("transaction");
                    if (!(txn instanceof JSONObject)) NativeJson.invalid("transaction");
                    NativeJson.keys((JSONObject) txn, "kind", "date", "amount", "currency", "categoryId", "note");
                    transaction((JSONObject) txn);
                    NativeJson.bool(value, "confirmDistinct");
                } else if (decision.equals("link")) {
                    NativeJson.keys(value, "id", "expectedRevision", "decision", "transactionId");
                    NativeJson.uuid(value, "transactionId", false);
                } else if (decision.equals("ignore")) {
                    NativeJson.keys(value, "id", "expectedRevision", "decision");
                } else NativeJson.invalid("decision");
                NativeJson.uuid(value, "id", false);
                NativeJson.revision(value, "expectedRevision", false);
                break;
            default:
                NativeJson.keys(value);
        }
    }

    private void kind(JSONObject value) {
        String kind = NativeJson.string(value, "kind", 7, false);
        if (!kind.equals("income") && !kind.equals("expense")) NativeJson.invalid("kind");
    }

    private void transaction(JSONObject value) {
        kind(value);
        NativeJson.string(value, "date", 10, false);
        NativeJson.string(value, "amount", 64, false);
        NativeJson.string(value, "currency", 3, false);
        NativeJson.uuid(value, "categoryId", true);
        NativeJson.string(value, "note", 1000, false);
    }
}
