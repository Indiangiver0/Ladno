package com.ladno.finance.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ParseSupport {
    private ParseSupport() {}
    static final String NUMBER = "[-+]?[0-9](?:[0-9 .,\\u00a0\\u202f]*[0-9])?";
    static final String CURRENCY = "(?:[A-Z]{3}|₸|₽|€)";
    static final Pattern SUFFIX = Pattern.compile("(?<![0-9A-Za-z.,])(" + NUMBER + ")\\s*(" + CURRENCY + ")(?![A-Za-z])");
    static final Pattern PREFIX = Pattern.compile("(?<![A-Za-z])(" + CURRENCY + ")\\s*(" + NUMBER + ")(?![0-9.,])");
    static final Pattern DATE = Pattern.compile("(?<![0-9])(?:[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}|[0-9]{1,2}[./][0-9]{1,2}[./][0-9]{2,4})(?![0-9])");
    static final Pattern BLOCKED = Pattern.compile("(?iu)(?:\\b(?:otp|pin|verification|password|refund|reversal|transfer|pending|cancelled|canceled|declined|failed|unsuccessful|insufficient|authorization|reserved|hold|cashback|offer|discount|promotion|invoice|proforma|confirm|scheduled|request)\\b|\\bnot\\s+(?:paid|completed|successful)\\b|\\bwill\\s+be\\b|(?:^|\\W)код(?:ы)?(?:\\W|$)|одноразов|парол|возврат|отмен|отказ|отклон|неуспеш|недостаточ|ошибк|не\\s+(?:оплачен|выполнен|прошл)|перевод|ожида|в\\s+обработк|авторизац|резерв|блокировк|будет|предстоит|подтверд|подтвержден|кэшбэк|кешбэк|скидк|акци[яию]|предложен|выигр|реклам|предварительн|нефискальн|сч[её]т\\s+на\\s+оплату)");
    // A direction noun is not evidence of finality. Negation is intentionally
    // conservative for generic messages: no provider template can prove its scope.
    // Unicode letter boundaries also cover НЕ + NBSP, newlines and punctuation.
    private static final Pattern NON_FINAL = Pattern.compile("(?iu)(?:"
            + "(?<![\\p{L}\\p{N}_])(?:не|нет|no|not|never|unpaid|incomplete|denied|rejected|planned|planning|scheduled|upcoming|awaiting|attempt|attempted|will|would|could|cannot)(?![\\p{L}\\p{N}_])|"
            + "\\b(?:isn|wasn|weren|didn|doesn|don|hasn|haven|hadn|couldn|wouldn|shouldn|mustn|won|can)['’]t\\b|"
            + "не(?:произвед|осуществ|выполн|исполн|провед|оплачен|заверш|состоя|зачисл|списан|успеш)|"
            + "заплан|планир|планов|намечен|отложен|предстоящ|предстоит|запрошен|попытк|будет|будут|завтра|послезавтра|"
            + "в\\s+очереди|к\\s+исполнению|выполнится|произвед[её]тся|спишется|спишутся|зачислится)");
    static boolean requiresReview(String text) {
        return BLOCKED.matcher(text).find() || NON_FINAL.matcher(text).find();
    }
    static void reason(List<String> reasons, String reason) { if (!reasons.contains(reason)) reasons.add(reason); }
    static String currency(String text) {
        if (text.equals("₸")) return "KZT";
        if (text.equals("₽")) return "RUB";
        if (text.equals("€")) return "EUR";
        try { Money.scale(text); return text; } catch (ContractException ex) { return null; }
    }
    static Money money(String raw, String code, List<String> reasons) {
        String currency = currency(code);
        if (currency == null) { reason(reasons, "CURRENCY_UNKNOWN"); return null; }
        try { return Money.parseSource(raw.trim(), currency); }
        catch (ContractException ex) { reason(reasons, "AMBIGUOUS_SEPARATOR"); return null; }
    }
    static List<Money> amounts(String text, List<String> reasons) {
        List<Money> result = new ArrayList<>();
        List<int[]> consumed = new ArrayList<>();
        Matcher suffix = SUFFIX.matcher(text);
        while (suffix.find()) {
            Money money = money(suffix.group(1), suffix.group(2), reasons);
            if (money != null) result.add(money);
            consumed.add(new int[]{suffix.start(), suffix.end()});
        }
        Matcher prefix = PREFIX.matcher(text);
        while (prefix.find()) {
            boolean overlaps = false;
            for (int[] range : consumed) if (prefix.start() < range[1] && prefix.end() > range[0]) overlaps = true;
            if (!overlaps) {
                Money money = money(prefix.group(2), prefix.group(1), reasons);
                if (money != null) result.add(money);
            }
        }
        return result;
    }
    static String date(String text, List<String> reasons) {
        Set<String> dates = new LinkedHashSet<>();
        Matcher matcher = DATE.matcher(text);
        boolean invalid = false;
        while (matcher.find()) {
            String raw = matcher.group();
            try {
                String value;
                if (raw.indexOf('-') >= 0) value = Money.date(raw);
                else {
                    String[] parts = raw.split("[./]");
                    if (parts[2].length() != 4) throw JsonChecks.invalid("date");
                    int day = Integer.parseInt(parts[0]), month = Integer.parseInt(parts[1]), year = Integer.parseInt(parts[2]);
                    if (raw.indexOf('/') >= 0 && day <= 12 && month <= 12 && day != month) throw JsonChecks.invalid("date");
                    value = Money.date(LocalDate.of(year, month, day).toString());
                }
                dates.add(value);
            } catch (java.time.DateTimeException | ContractException ex) { invalid = true; }
        }
        if (invalid || dates.size() > 1) reason(reasons, "DATE_INVALID");
        return !invalid && dates.size() == 1 ? dates.iterator().next() : null;
    }
    static ImportCandidate candidate(String kind, Money money, String date, String origin, List<String> reasons) {
        if (kind == null) reason(reasons, "UNKNOWN_KIND");
        if (money == null) { reason(reasons, "AMOUNT_MISSING"); reason(reasons, "CURRENCY_MISSING"); }
        if (date == null) reason(reasons, "DATE_MISSING");
        return new ImportCandidate(kind, money == null ? null : money.amount, money == null ? null : money.currency,
                date, date == null ? null : origin, "", reasons, reasons.isEmpty() ? "high" : "uncertain", null);
    }
    static ImportCandidate unavailable(String reason) {
        return new ImportCandidate(null, null, null, null, null, "", Collections.singletonList(reason), "unavailable", null);
    }
}
