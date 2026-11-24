package com.ladno.finance.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local decoding of a documented KZ fiscal QR subset. No URL is loaded. */
public final class FiscalQrParser {
    public static final int MAX_PAYLOAD_BYTES = 4096;
    public static final int MAX_CODES = 16;
    private static final DateTimeFormatter TIME = DateTimeFormatter
        .ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private FiscalQrParser() { }

    /** Returns null only when no QR was decoded. Mixed/distinct QR require another photo. */
    public static Result select(List<String> payloads) {
        if (payloads == null || payloads.isEmpty()) return null;
        if (payloads.size() > MAX_CODES) throw limit();
        Map<String, Result> unique = new LinkedHashMap<>();
        ContractException invalid = null;
        for (String payload : payloads) {
            try {
                Result parsed = parse(payload);
                unique.put(parsed.locator() + "\n" + parsed.revision(), parsed);
            } catch (ContractException e) {
                if ("LIMIT_EXCEEDED".equals(e.code)) throw e;
                invalid = e;
            }
        }
        if (payloads.size() > 1 && (invalid != null || unique.size() > 1))
            throw new ContractException("QR_MULTIPLE", null,
                "На снимке несколько QR. Оставьте один фискальный QR чека.");
        if (invalid != null) throw invalid;
        return unique.values().iterator().next();
    }

    public static Result parse(String payload) {
        if (payload != null) payload = payload.trim();
        if (payload == null || payload.isEmpty()) throw unsupported();
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) throw limit();
        for (int i = 0; i < payload.length(); i++) {
            char ch = payload.charAt(i);
            if (ch <= 0x20 || ch == 0x7f || ch == '\\') throw unsupported();
        }
        try {
            URI uri = new URI(payload);
            String scheme = uri.getScheme(), host = uri.getHost();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || host == null || uri.isOpaque() || uri.getRawUserInfo() != null) throw unsupported();
            host = host.toLowerCase(Locale.ROOT);
            // Every KZ OFD encodes the same KGD fields (i, f, s, t) in its QR link; the host only
            // names the operator. The URL itself is never loaded, so any operator is accepted.
            if (!host.matches("[a-z0-9.-]{1,253}") || !host.contains(".")) throw unsupported();
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isEmpty()) throw unsupported();
            String[] parts = rawQuery.split("&", -1);
            if (parts.length > 16) throw unsupported();
            Map<String, String> query = new LinkedHashMap<>();
            for (String part : parts) {
                int equals = part.indexOf('=');
                if (equals <= 0) continue;
                String key = URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8.name()).toLowerCase(Locale.ROOT);
                if (!(key.equals("i") || key.equals("f") || key.equals("s") || key.equals("t"))) continue;
                String value = URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8.name());
                if (query.put(key, value) != null) throw unsupported();
            }
            String fiscal = query.get("i"), register = query.get("f"), amount = query.get("s"), timestamp = query.get("t");
            if (fiscal == null || register == null || !fiscal.matches("[0-9]{1,32}")
                || !register.matches("[0-9]{1,32}")) throw unsupported();
            if (amount != null) amount = amount.replace(',', '.');
            if (amount != null && amount.matches("(?:0|[1-9][0-9]{0,23})\\.[0-9]{3,}")) {
                // Some cash registers print "1500.000": only trailing zeros may be dropped.
                String trimmed = amount.replaceFirst("0+$", "");
                amount = trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            }
            if (amount == null || !amount.matches("(?:0|[1-9][0-9]{0,23})(?:\\.[0-9]{1,2})?")) throw amountInvalid();
            Money money;
            try { money = Money.parseManual(amount, "KZT"); }
            catch (ContractException e) { throw amountInvalid(); }
            if (timestamp != null && timestamp.matches("[0-9]{8}T[0-9]{4}")) timestamp += "00";
            if (timestamp == null || !timestamp.matches("[0-9]{8}T[0-9]{6}")) throw dateInvalid();
            LocalDateTime dateTime;
            try {
                dateTime = LocalDateTime.parse(timestamp, TIME);
                if (dateTime.getYear() < 1 || dateTime.getYear() > 9999) throw dateInvalid();
            } catch (java.time.DateTimeException e) { throw dateInvalid(); }
            return new Result(host, fiscal, register, money.amount, timestamp, Money.date(dateTime.toLocalDate().toString()));
        } catch (URISyntaxException | IllegalArgumentException | java.io.UnsupportedEncodingException e) {
            throw unsupported();
        }
    }

    public static final class Result {
        // These are transient, never source-authored metadata for JavaScript or storage.
        private final String host, fiscalSign, registerNumber, timestamp;
        public final String amount, date;
        private Result(String host, String fiscalSign, String registerNumber, String amount, String timestamp, String date) {
            this.host = host; this.fiscalSign = fiscalSign; this.registerNumber = registerNumber;
            this.amount = amount; this.timestamp = timestamp; this.date = date;
        }
        /** Only feed this receipt locator to per-install, purpose-separated HMAC. */
        public String locator() { return host + "\n" + fiscalSign + "\n" + registerNumber; }
        public String revision() { return amount + "\n" + timestamp; }
        /** A fiscal QR carries the paid total and time signed by the cash register: post it as an expense. */
        public ImportCandidate candidate() {
            return new ImportCandidate("expense", amount, "KZT", date, "source", "",
                java.util.Collections.emptyList(), "high", null);
        }
    }

    private static ContractException unsupported() {
        return new ContractException("QR_UNSUPPORTED", null, "Этот QR пока не поддерживается. Нужен фискальный QR чека.");
    }
    private static ContractException amountInvalid() {
        return new ContractException("QR_AMOUNT_INVALID", null, "Не удалось надёжно разобрать сумму из QR чека.");
    }
    private static ContractException dateInvalid() {
        return new ContractException("QR_DATE_INVALID", null, "Не удалось надёжно разобрать дату из QR чека.");
    }
    private static ContractException limit() {
        return new ContractException("LIMIT_EXCEEDED", null, "QR превышает допустимый размер или на снимке слишком много QR.");
    }
}
