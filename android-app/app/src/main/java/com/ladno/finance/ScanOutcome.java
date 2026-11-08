package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.Money;
import org.json.JSONObject;

/** Whitelisted projection of a committed storage result; never includes OCR text. */
final class ScanOutcome {
    private ScanOutcome() { }

    static JSONObject fromIngest(JSONObject result) {
        if (result == null) throw invalid();
        String status = NativeJson.string(result, "outcome", 16, false);
        if (status.equals("review")) status = "needs_review";
        if (!java.util.Arrays.asList("posted", "needs_review", "linked", "duplicate", "ignored").contains(status))
            throw invalid();
        JSONObject candidate = result.optJSONObject("candidate");
        if (candidate == null) throw invalid();
        NativeJson.uuid(candidate, "id", false);
        String candidateId = NativeJson.string(candidate, "id", 36, false);
        JSONObject record = result.optJSONObject("record");
        String transactionId = null;
        JSONObject destination = candidate;
        if (record != null) {
            NativeJson.uuid(record, "id", false);
            transactionId = NativeJson.string(record, "id", 36, false);
            destination = record;
        }
        String date = NativeJson.string(destination, "date", 10, true);
        String currency = NativeJson.string(destination, "currency", 3, true);
        if (date != null) Money.date(date);
        if (currency != null) Money.scale(currency);
        return NativeJson.object("status", status, "candidateId", candidateId,
                "transactionId", transactionId, "month", date == null ? null : date.substring(0, 7),
                "currency", currency);
    }

    private static ContractException invalid() {
        return new ContractException("STORAGE_FAILED", null, "Не удалось прочитать результат обработки чека.");
    }
}
