package com.ladno.finance.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structured transient parser result; never carries the raw message/image. */
public final class ImportCandidate {
    public final String kind, amount, currency, date, dateOrigin, note, confidence, paymentReference;
    public final List<String> reasons;
    public ImportCandidate(String kind, String amount, String currency, String date, String dateOrigin,
                           String note, List<String> reasons, String confidence, String paymentReference) {
        this.kind = kind; this.amount = amount; this.currency = currency; this.date = date;
        this.dateOrigin = dateOrigin; this.note = note; this.confidence = confidence;
        this.paymentReference = paymentReference;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
    }
    public JSONObject toJson() {
        JSONObject value = new JSONObject();
        JsonChecks.put(value, "kind", kind); JsonChecks.put(value, "amount", amount);
        JsonChecks.put(value, "currency", currency); JsonChecks.put(value, "date", date);
        JsonChecks.put(value, "dateOrigin", dateOrigin); JsonChecks.put(value, "note", note);
        JsonChecks.put(value, "reasons", new JSONArray(reasons)); JsonChecks.put(value, "confidence", confidence);
        JsonChecks.put(value, "paymentReference", paymentReference); return value;
    }
    public static ImportCandidate fromJson(JSONObject value) {
        JsonChecks.keys(value, "kind", "amount", "currency", "date", "dateOrigin", "note", "reasons", "confidence", "paymentReference");
        String kind = JsonChecks.nullableString(value, "kind", 7);
        if (kind != null && !kind.equals("income") && !kind.equals("expense")) throw JsonChecks.invalid("kind");
        String amount = JsonChecks.nullableString(value, "amount", 64);
        String currency = JsonChecks.nullableString(value, "currency", 3);
        if (currency != null) Money.scale(currency);
        if (amount != null) {
            if (currency == null) throw JsonChecks.invalid("currency");
            Money.parseCanonical(amount, currency, Money.scale(currency));
        }
        String date = JsonChecks.nullableString(value, "date", 10);
        if (date != null) Money.date(date);
        String origin = JsonChecks.nullableString(value, "dateOrigin", 12);
        if (origin != null && !origin.equals("source") && !origin.equals("post_time") && !origin.equals("user")) throw JsonChecks.invalid("dateOrigin");
        if ((date == null) != (origin == null)) throw JsonChecks.invalid("dateOrigin");
        String note = JsonChecks.text(JsonChecks.string(value, "note", 2000), 500, false, "note");
        JSONArray array = JsonChecks.array(value, "reasons", 32);
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object reason = array.opt(i);
            if (!(reason instanceof String) || !((String) reason).matches("[A-Z][A-Z0-9_]{0,63}")) throw JsonChecks.invalid("reasons");
            if (!reasons.contains(reason)) reasons.add((String) reason);
        }
        String confidence = JsonChecks.choice(value, "confidence", "high", "uncertain", "unavailable");
        String ref = JsonChecks.nullableString(value, "paymentReference", 256);
        return new ImportCandidate(kind, amount, currency, date, origin, note, reasons, confidence, ref);
    }
}
