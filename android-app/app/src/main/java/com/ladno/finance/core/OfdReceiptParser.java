package com.ladno.finance.core;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One source-backed consumer API subset. Raw data never enters the candidate DTO. */
public final class OfdReceiptParser {
    private static final DateTimeFormatter QR_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> TICKET_FIELDS = new HashSet<>(Arrays.asList(
        "acceptedAt", "cancelDate", "changeSum", "customerEmail", "customerIinOrBin", "customerPhone",
        "discount", "domain", "extensionAuxiliaries", "fiscalId", "geoPosition", "insertedAt", "items",
        "kkmId", "kkmRequestId", "linkedKkmRequestId", "linkedKkmRequestTime", "linkedTransactionId",
        "markup", "modifiers", "offlineFiscalId", "operationType", "operator", "options", "parentTicket",
        "payload", "payments", "protocolVersion", "requestPayload", "responsePayload", "shiftDocumentNumber",
        "shiftNumber", "takenSum", "taxes", "ticketAdInfo", "totalSum", "transactionDate", "transactionId", "type"));
    private OfdReceiptParser() { }

    public static final class Request {
        public final String host, fiscalSign, registerNumber, amount, timestamp, date;
        private Request(FiscalQrParser.Result qr) {
            String[] locator = qr.locator().split("\n", -1), revision = qr.revision().split("\n", -1);
            if (locator.length != 3 || revision.length != 2) throw OfdJson.invalid();
            host = locator[0]; fiscalSign = locator[1]; registerNumber = locator[2];
            amount = qr.amount; timestamp = revision[1]; date = qr.date;
        }
        public static Request from(FiscalQrParser.Result qr) {
            if (qr == null) throw OfdJson.invalid();
            return new Request(qr);
        }
        public boolean supported() { return "consumer.oofd.kz".equals(host); }
    }

    public static ImportCandidate parse(String body, FiscalQrParser.Result qr) {
        Request request = Request.from(qr);
        if (!request.supported()) return fallback(qr, "OFD_UNAVAILABLE");
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > 262144) throw OfdJson.invalid();
        Map<String, Object> root = object(OfdJson.parse(body));
        Map<String, Object> ticket = object(required(root, "ticket"));
        String fiscal = string(required(ticket, "fiscalId"), 32);
        String register = string(required(root, "kkmFnsId"), 32);
        Money total = money(required(ticket, "totalSum"), false);
        String dateTime = string(required(ticket, "transactionDate"), 32);
        LocalDateTime sourceTime;
        if (!dateTime.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.000)?"))
            throw OfdJson.invalid();
        try { sourceTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.STRICT)); }
        catch (java.time.DateTimeException error) { throw OfdJson.invalid(); }
        if (!fiscal.equals(request.fiscalSign) || !register.equals(request.registerNumber)
            || !total.amount.equals(request.amount) || !sourceTime.format(QR_TIME).equals(request.timestamp))
            return fallback(qr, "OFD_MISMATCH");
        boolean finalSale = "TICKET".equals(string(required(ticket, "type"), 32))
            && integer(required(ticket, "operationType")) == 2
            && required(ticket, "cancelDate") == null && required(ticket, "offlineFiscalId") == null;
        if (!TICKET_FIELDS.containsAll(ticket.keySet())) finalSale = false;
        for (String linked : Arrays.asList("parentTicket", "linkedKkmRequestId", "linkedKkmRequestTime", "linkedTransactionId"))
            if (ticket.get(linked) != null) finalSale = false;
        List<Object> payments = array(required(ticket, "payments"));
        if (payments.isEmpty() || payments.size() > 32) throw OfdJson.invalid();
        BigInteger paid = BigInteger.ZERO;
        for (Object value : payments) {
            Map<String, Object> payment = object(value);
            String type = string(required(payment, "paymentType"), 32);
            if (!Arrays.asList("CARD", "CASH", "MOBILE").contains(type)) finalSale = false;
            paid = paid.add(moneyMinor(required(payment, "sum"), true));
        }
        if (!paid.equals(total.minor)) return fallback(qr, "OFD_MISMATCH");
        List<Object> items = array(required(ticket, "items"));
        if (items.isEmpty()) finalSale = false;
        for (Object value : items) {
            Map<String, Object> item = object(value);
            int type = integer(required(item, "itemType"));
            if (type == 1) {
                Map<String, Object> commodity = object(required(item, "commodity"));
                string(required(commodity, "name"), 512);
                // Positions are bounded evidence, never a recomputed total (discounts/taxes differ).
            } else if (type == 5) {
                object(required(item, "discount"));
            } else finalSale = false;
        }
        if (!finalSale) return fallback(qr, "OFD_REVIEW_REQUIRED");
        return new ImportCandidate("expense", total.amount, "KZT", request.date, "source", "",
            Collections.emptyList(), "high", null);
    }

    public static ImportCandidate fallback(FiscalQrParser.Result qr, String reason) {
        List<String> reasons = new ArrayList<>(qr.candidate().reasons);
        if (!reasons.contains(reason)) reasons.add(reason);
        return new ImportCandidate(null, qr.amount, "KZT", qr.date, "source", "", reasons, "uncertain", null);
    }
    private static Object required(Map<String, Object> object, String key) {
        if (!object.containsKey(key)) throw OfdJson.invalid();
        return object.get(key);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw OfdJson.invalid();
        return (Map<String, Object>) value;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) {
        if (!(value instanceof List)) throw OfdJson.invalid();
        return (List<Object>) value;
    }
    private static String string(Object value, int maxBytes) {
        if (!(value instanceof String) || ((String) value).getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw OfdJson.invalid();
        return (String) value;
    }
    private static int integer(Object value) {
        if (!(value instanceof OfdJson.NumberToken)) throw OfdJson.invalid();
        String text = ((OfdJson.NumberToken) value).text;
        if (!text.matches("[0-9]{1,3}")) throw OfdJson.invalid();
        return Integer.parseInt(text);
    }
    private static Money money(Object value, boolean allowZero) {
        String text = moneyToken(value);
        try { return Money.parseManual(text, "KZT"); }
        catch (ContractException error) { throw OfdJson.invalid(); }
    }
    private static BigInteger moneyMinor(Object value, boolean allowZero) {
        String text = moneyToken(value);
        if (allowZero && text.matches("0(?:\\.0{1,2})?")) return BigInteger.ZERO;
        return money(value, false).minor;
    }
    private static String moneyToken(Object value) {
        if (!(value instanceof OfdJson.NumberToken)) throw OfdJson.invalid();
        String text = ((OfdJson.NumberToken) value).text;
        if (!text.matches("(?:0|[1-9][0-9]{0,23})(?:\\.[0-9]{1,2})?")) throw OfdJson.invalid();
        return text;
    }
}
