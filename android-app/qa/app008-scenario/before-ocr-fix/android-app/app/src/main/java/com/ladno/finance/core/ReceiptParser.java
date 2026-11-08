package com.ladno.finance.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads OCR text only; fiscal QR/URL text is never executed. */
public final class ReceiptParser {
    private static final String PAID_LABEL = "(?:total\\s+paid|amount\\s+paid|payment\\s+completed|paid|оплачено|оплата\\s+(?:успешно\\s+)?(?:произведена|выполнена|завершена)|төленген|төленді)";
    private static final Pattern COMPLETED_PAID = Pattern.compile("(?ium)^\\h*(?:статус\\h*:\\h*)?" + PAID_LABEL + "(?=$|[\\s:=])");
    private static final Pattern PARTIAL_OR_QUESTION = Pattern.compile("(?iu)(?:\\?|\\bpartial(?:ly)?\\b|частич|жоқ)");
    private static final Pattern FINAL = Pattern.compile("(?iu)^\\h*(?:" + PAID_LABEL + "|grand\\h+total|total|всего\\h+к\\h+оплате|к\\h+оплате|итого|итог|барлығы|сумма)(?=$|[\\s:=])");
    private static final Pattern EXCLUDE = Pattern.compile("(?iu)(?:subtotal|sub\\s+total|change|tender|cash\\s+received|сдача|внесено|наличными|получено|промежуточ)");
    private static final Pattern CODE = Pattern.compile("(?<![A-Za-z])(" + ParseSupport.CURRENCY + ")(?![A-Za-z])");
    private static final Pattern AMOUNT_ONLY = Pattern.compile("^\\s*[:=]?\\s*(" + ParseSupport.NUMBER + ")\\s*$");
    private static final Pattern AMOUNT_LINE = Pattern.compile("^\\h*[:=]?\\h*(?:(" + ParseSupport.CURRENCY
            + ")\\h*)?(" + ParseSupport.NUMBER + ")\\h*(" + ParseSupport.CURRENCY + ")?\\h*$");
    private static final Pattern PAYMENT = Pattern.compile("(?iu)(?:\\b(?:cash|card)\\b|наличн|безналичн|банковск.{0,8}карт|картой)");
    private static final Pattern LOCAL_TENGE = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(?:тг|теңге|тенге)(?![\\p{L}\\p{N}_])");
    // A fiscal SALE with one matching noncash settlement is evidence of a completed
    // purchase. A generic total, cash tender, or card number is not such evidence.
    private static final Pattern FISCAL = Pattern.compile("(?ium)^\\h*фискальный\\h+чек\\h*$");
    private static final Pattern SALE = Pattern.compile("(?ium)^\\h*(?:тип\\h+(?:чека|операции)\\h*[:=]\\h*)?(?:приход|продажа|сату)\\h*$");
    private static final Pattern NONCASH = Pattern.compile("(?iu)^\\h*(?:оплата\\h+)?(?:банковск(?:ая|ой)\\h+карт(?:а|ой)|безналичными|безналичный\\h+расч[её]т|картой|card)(?=$|[\\s:=])");
    private ReceiptParser() {}
    public static ImportCandidate parse(String text) {
        if (text == null || text.trim().isEmpty()) return ParseSupport.unavailable("UNAVAILABLE");
        if (text.length() > 200000) return ParseSupport.unavailable("LIMIT_EXCEEDED");
        text = text.replaceAll("(?iu)(?:https?://|www\\.)\\S+", "");
        // Receipt-only aliases: do not change generic bank-message interpretation.
        text = LOCAL_TENGE.matcher(text).replaceAll("KZT");
        List<String> reasons = new ArrayList<>();
        boolean blocked = ParseSupport.requiresReview(text) || PARTIAL_OR_QUESTION.matcher(text).find();
        Set<String> currencies = new HashSet<>();
        Matcher codes = CODE.matcher(text);
        while (codes.find()) { String code = ParseSupport.currency(codes.group(1)); if (code != null) currencies.add(code); }
        if (currencies.size() > 1) ParseSupport.reason(reasons, "CURRENCY_UNKNOWN");
        Map<String, Money> totals = new LinkedHashMap<>();
        List<Money> noncash = new ArrayList<>();
        int paymentLines = 0;
        boolean unresolvedTotal = false;
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher card = NONCASH.matcher(line);
            if (card.find()) {
                List<Money> values = totalValues(line.substring(card.end()), lines, i, currencies, reasons);
                if (values.size() == 1) { noncash.add(values.get(0)); paymentLines++; }
                else if (values.size() > 1) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
            } else if (PAYMENT.matcher(line).find() && !line.toLowerCase(java.util.Locale.ROOT).contains("сдач")
                    && !ParseSupport.amounts(line, new ArrayList<>()).isEmpty()) paymentLines++;
            Matcher marker = FINAL.matcher(line);
            if (!marker.find() || EXCLUDE.matcher(line).find()) continue;
            String tail = line.substring(marker.end());
            // A label followed by qualifiers such as НДС/налог/комиссия names
            // a component, not the purchase total. Ignore that line entirely,
            // so a separate valid final total remains usable.
            if (!labelOnly(tail) && !amountLine(tail)) continue;
            List<Money> values = totalValues(tail, lines, i, currencies, reasons);
            // Standalone payment-status wording may confirm another total, but
            // never consumes an item/payment/date line as an amount.
            if (values.isEmpty() && marker.group().trim().matches("(?iu)" + PAID_LABEL)
                    && !tail.matches("(?s).*[0-9].*")) continue;
            if (values.size() == 1) {
                Money value = values.get(0);
                totals.put(value.currency + ":" + value.amount, value);
            }
            else if (values.size() > 1) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
            else unresolvedTotal = true;
        }
        if (totals.size() > 1 || (unresolvedTotal && !totals.isEmpty())) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
        if (paymentLines > 1) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
        Money money = totals.size() == 1 && currencies.size() <= 1 ? totals.values().iterator().next() : null;
        boolean fiscalSettlement = money != null && FISCAL.matcher(text).find() && SALE.matcher(text).find()
                && paymentLines == 1 && noncash.size() == 1
                && money.currency.equals(noncash.get(0).currency) && money.amount.equals(noncash.get(0).amount);
        if (blocked || (!completedPaid(lines) && !fiscalSettlement))
            ParseSupport.reason(reasons, "UNSUPPORTED_EVENT");
        String date = ParseSupport.date(text, reasons);
        return ParseSupport.candidate("expense", money, date, "source", reasons);
    }

    private static List<Money> totalValues(String tail, String[] lines, int index,
            Set<String> currencies, List<String> reasons) {
        if (!labelOnly(tail) && !amountLine(tail)) return new ArrayList<>();
        List<Money> values = sameLine(tail, currencies, reasons);
        // Only a label-only line may borrow a strictly amount-only adjacent
        // line; never search arbitrary later lines or numeric product text.
        if (!values.isEmpty() || !labelOnly(tail)) return values;
        int next = index + 1;
        if (next < lines.length && lines[next].trim().isEmpty()) next++;
        if (next >= lines.length) return values;
        String candidate = lines[next].trim();
        if (!amountLine(candidate)) return values;
        return sameLine(candidate, currencies, reasons);
    }

    private static boolean labelOnly(String tail) { return tail.trim().matches("[:=]?"); }

    private static boolean amountLine(String text) {
        Matcher value = AMOUNT_LINE.matcher(text);
        if (!value.matches()) return false;
        // Three Latin letters such as TAX are not automatically a currency.
        return (value.group(1) == null || ParseSupport.currency(value.group(1)) != null)
                && (value.group(3) == null || ParseSupport.currency(value.group(3)) != null);
    }

    private static boolean completedPaid(String[] lines) {
        for (String line : lines) {
            Matcher paid = COMPLETED_PAID.matcher(line);
            if (paid.find()) {
                String tail = line.substring(paid.end());
                if (labelOnly(tail) || amountLine(tail)) return true;
            }
        }
        return false;
    }

    private static List<Money> sameLine(String text, Set<String> currencies, List<String> reasons) {
        List<Money> values = ParseSupport.amounts(text, reasons);
        if (values.isEmpty() && currencies.size() == 1) {
            Matcher only = AMOUNT_ONLY.matcher(text);
            if (only.matches()) {
                Money money = ParseSupport.money(only.group(1), currencies.iterator().next(), reasons);
                if (money != null) values.add(money);
            }
        }
        return values;
    }
}
