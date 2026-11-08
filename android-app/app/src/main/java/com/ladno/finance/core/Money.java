package com.ladno.finance.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.Currency;
import java.util.Map;
import java.util.TreeMap;

/** Exact minor units. No numeric SQL, floating point or monetary rounding. */
public final class Money {
    private static final Map<String, Integer> CURRENCIES;
    static {
        Map<String, Integer> currencies = new TreeMap<>();
        for (Currency currency : Currency.getAvailableCurrencies()) {
            int scale = currency.getDefaultFractionDigits();
            if (scale >= 0 && scale <= 6) currencies.put(currency.getCurrencyCode(), scale);
        }
        CURRENCIES = Collections.unmodifiableMap(currencies);
    }
    public final String amount, currency;
    public final int scale;
    public final BigInteger minor;
    private Money(String amount, String currency, int scale, BigInteger minor) {
        this.amount = amount; this.currency = currency; this.scale = scale; this.minor = minor;
    }
    public static int scale(String currency) {
        Integer scale = CURRENCIES.get(currency);
        if (scale == null) throw JsonChecks.invalid("currency");
        return scale;
    }
    public static JSONArray currencies() {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, Integer> entry : CURRENCIES.entrySet()) {
            JSONObject value = new JSONObject();
            JsonChecks.put(value, "code", entry.getKey()); JsonChecks.put(value, "scale", entry.getValue());
            result.put(value);
        }
        return result;
    }
    public static Money parseManual(String raw, String currency) {
        int scale = scale(currency);
        if (raw == null || raw.length() > 64 || !raw.matches("(?:0|[1-9][0-9]{0,23})(?:[.,][0-9]+)?")) throw JsonChecks.invalid("amount");
        String normalized = raw.replace(',', '.');
        int point = normalized.indexOf('.');
        int fraction = point < 0 ? 0 : normalized.length() - point - 1;
        // Check lexical precision before any numeric conversion: even trailing zeros matter.
        if (fraction > scale) throw JsonChecks.invalid("amount");
        if (point < 0 && scale > 0) normalized += ".";
        if (scale > 0) normalized += zeros(scale - fraction);
        return parseCanonical(normalized, currency, scale);
    }
    public static Money parseCanonical(String amount, String currency, int givenScale) {
        int scale = scale(currency);
        if (scale != givenScale) throw JsonChecks.invalid("scale");
        String pattern = "(?:0|[1-9][0-9]{0,23})" + (scale == 0 ? "" : "\\.[0-9]{" + scale + "}");
        if (amount == null || !amount.matches(pattern)) throw JsonChecks.invalid("amount");
        BigInteger minor = new BigInteger(amount.replace(".", ""));
        if (minor.signum() <= 0) throw JsonChecks.invalid("amount");
        return new Money(amount, currency, scale, minor);
    }
    /** Source grouping is accepted only as complete space-separated triples. */
    public static Money parseSource(String raw, String currency) {
        if (raw == null || raw.length() > 64) throw JsonChecks.invalid("amount");
        if (raw.matches("[1-9][0-9]*[.,][0-9]{3}")) throw JsonChecks.invalid("amount");
        String normalized = raw;
        if (raw.indexOf(' ') >= 0 || raw.indexOf('\u00a0') >= 0 || raw.indexOf('\u202f') >= 0) {
            if (!raw.matches("[1-9][0-9]{0,2}([ \\u00a0\\u202f])[0-9]{3}(?:\\1[0-9]{3})*(?:[.,][0-9]+)?")) throw JsonChecks.invalid("amount");
            normalized = raw.replace(" ", "").replace("\u00a0", "").replace("\u202f", "");
        }
        return parseManual(normalized, currency);
    }
    public static String formatMinor(BigInteger minor, int scale) {
        if (scale < 0 || scale > 6) throw JsonChecks.invalid("scale");
        boolean negative = minor.signum() < 0;
        String digits = minor.abs().toString();
        if (scale > 0) {
            if (digits.length() <= scale) digits = zeros(scale + 1 - digits.length()) + digits;
            digits = digits.substring(0, digits.length() - scale) + "." + digits.substring(digits.length() - scale);
        }
        return (negative ? "-" : "") + digits;
    }
    private static String zeros(int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append('0');
        return value.toString();
    }
    public static String date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw JsonChecks.invalid("date");
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT));
            if (date.getYear() < 1 || date.getYear() > 9999) throw JsonChecks.invalid("date");
            return date.toString();
        } catch (java.time.DateTimeException ex) { throw JsonChecks.invalid("date"); }
    }
    public static YearMonth month(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) throw JsonChecks.invalid("month");
        try {
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 1 || month.getYear() > 9999) throw JsonChecks.invalid("month");
            return month;
        } catch (java.time.DateTimeException ex) { throw JsonChecks.invalid("month"); }
    }
    public static ZoneId zone(String value) {
        try { if (value == null || value.length() > 100) throw JsonChecks.invalid("zoneId"); return ZoneId.of(value); }
        catch (java.time.DateTimeException ex) { throw JsonChecks.invalid("zoneId"); }
    }
}
