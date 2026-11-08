package com.ladno.finance.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative generic fallback. No claim of provider-specific bank compatibility. */
public final class BankMessageParser {
    private static final Pattern EXPENSE = Pattern.compile("(?iu)(?:\\b(?:paid|debited|purchase)\\b|payment\\s+completed|purchase\\s+completed|покупка|оплачено|оплата|списано|списание|плат[её]ж|төлем|төленді|сатып\\s+ал|шығыс)");
    private static final Pattern INCOME = Pattern.compile("(?iu)(?:\\b(?:credited|received)\\b|deposit\\s+completed|зачислен|поступил|поступлен|пополн|толықтыр|түсті|түсім|кіріс)");
    // Stops at the first labelled amount so a later operation on the same line survives.
    private static final Pattern BALANCE = Pattern.compile("(?iu)(?:\\b(?:balance|available)\\b|баланс|остаток|доступно|қалдық|қолжетімді)[^\\n;0-9]{0,40}"
            + "(?:(?:[A-Z]{3}|₸|₽|€)\\s*)?(?:[0-9][0-9 .,\\u00a0\\u202f]*[0-9]|[0-9])(?:\\s*(?:[A-Z]{3}|₸|₽|€))?");
    // Letters may not touch the alias, digits may ("5000тг").
    private static final Pattern TENGE = Pattern.compile("(?iu)(?<![\\p{L}_])(?:тг|тенге|теңге)(?![\\p{L}\\p{N}_])");
    // Explicit sign in front of an amount with currency: "-2 500,00 ₸", "+10 000 KZT", "− 500 ₸".
    private static final Pattern SIGN = Pattern.compile("(?<![\\p{L}\\p{N}.,])([-+−–])\\s?(?=[0-9][0-9 .,\\u00a0\\u202f]*\\s*(?:[A-Z]{3}|₸|₽|€)(?![A-Za-z]))");
    private static final Pattern OUTGOING_TRANSFER = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(?:вы\\s+(?:перевели|отправили)|you\\s+(?:sent|transferred)|исходящий\\s+перевод|отправлен\\s+перевод|шығыс\\s+аударым)(?![\\p{L}\\p{N}_])");
    private static final Pattern INCOMING_TRANSFER = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(?:вам\\s+(?:перевели|отправили)|получен\\s+перевод|перевод\\s+(?:получен|зачислен)|входящий\\s+перевод|transfer\\s+received|кіріс\\s+аударым)(?![\\p{L}\\p{N}_])");
    private static final Pattern OWN_ACCOUNTS = Pattern.compile("(?iu)(?:между\\s+своими|на\\s+свой\\s+сч[её]т|на\\s+свою\\s+карту|между\\s+вашими|between\\s+(?:your|own)\\s+accounts|to\\s+(?:your|own)\\s+account|өз\\s+шоттар)");
    private BankMessageParser() {}
    public static ImportCandidate parse(String text, long postTimeMillis, String zoneId, boolean allowPostTimeDate) {
        if (text == null || text.trim().isEmpty()) return ParseSupport.unavailable("UNAVAILABLE");
        if (text.length() > 65536) return ParseSupport.unavailable("LIMIT_EXCEEDED");
        List<String> reasons = new ArrayList<>();
        if (ParseSupport.requiresReview(text)) ParseSupport.reason(reasons, "UNSUPPORTED_EVENT");
        if (TENGE.matcher(text).find()) text = TENGE.matcher(text).replaceAll("KZT");
        String transactionText = BALANCE.matcher(text).replaceAll("");
        // Signs are direction evidence, never part of the amount.
        Set<String> signs = new LinkedHashSet<>();
        Matcher signMatcher = SIGN.matcher(transactionText);
        StringBuffer unsigned = new StringBuffer();
        while (signMatcher.find()) {
            signs.add("+".equals(signMatcher.group(1)) ? "+" : "-");
            signMatcher.appendReplacement(unsigned, "");
        }
        signMatcher.appendTail(unsigned);
        transactionText = unsigned.toString();
        boolean outgoing = OUTGOING_TRANSFER.matcher(transactionText).find();
        boolean incoming = INCOMING_TRANSFER.matcher(transactionText).find();
        boolean ownAccounts = OWN_ACCOUNTS.matcher(transactionText).find();
        if (outgoing || incoming || ownAccounts) ParseSupport.reason(reasons, "UNSUPPORTED_EVENT");
        boolean expense = EXPENSE.matcher(transactionText).find() || outgoing;
        boolean income = INCOME.matcher(transactionText).find() || incoming;
        String kind = expense == income ? null : expense ? "expense" : "income";
        if (signs.size() > 1) {
            kind = null;
        } else if (signs.size() == 1) {
            String signKind = signs.contains("-") ? "expense" : "income";
            if (kind == null && !(expense && income)) kind = signKind;
            else if (!signKind.equals(kind)) { kind = null; ParseSupport.reason(reasons, "UNSUPPORTED_EVENT"); }
        }
        if (ownAccounts) kind = null;
        List<Money> amounts = ParseSupport.amounts(transactionText, reasons);
        Matcher balances = BALANCE.matcher(text);
        boolean balanceAmount = false;
        while (balances.find()) {
            if (!ParseSupport.amounts(balances.group(), new ArrayList<>()).isEmpty()) balanceAmount = true;
        }
        // Generic fallback is not a verified bank template: even labelled balance
        // alongside a payment is multiple monetary evidence, so ask for review.
        if (balanceAmount && !amounts.isEmpty()) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
        if (amounts.size() > 1) ParseSupport.reason(reasons, "AMBIGUOUS_TOTAL");
        Money money = amounts.size() == 1 ? amounts.get(0) : null;
        String date = ParseSupport.date(text, reasons), origin = "source";
        if (date == null && !reasons.contains("DATE_INVALID") && allowPostTimeDate) {
            try {
                if (postTimeMillis <= 0) throw JsonChecks.invalid("postTime");
                date = Money.date(Instant.ofEpochMilli(postTimeMillis).atZone(Money.zone(zoneId)).toLocalDate().toString());
                origin = "post_time";
            } catch (java.time.DateTimeException | ContractException ex) { ParseSupport.reason(reasons, "DATE_INVALID"); }
        }
        return ParseSupport.candidate(kind, money, date, origin, reasons);
    }
}
