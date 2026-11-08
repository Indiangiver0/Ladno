package com.ladno.finance.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserTest {
    private static ImportCandidate bank(String message) { return BankMessageParser.parse(message, 1793478600000L, "UTC", true); }
    private static void review(ImportCandidate candidate) { assertNotEquals("high", candidate.confidence); assertFalse(candidate.reasons.isEmpty()); }
    @Test public void explicitBankPaymentAndBalanceAreDifferentAmounts() {
        ImportCandidate value = bank("Покупка 1 234,50 KZT 31.10.2026\nБаланс 9 000,00 KZT");
        review(value);
        assertEquals("expense", value.kind); assertEquals("1234.50", value.amount);
        assertEquals("2026-10-31", value.date); assertEquals("source", value.dateOrigin);
        assertNull(value.paymentReference); assertEquals("", value.note);
        assertEquals(9, value.toJson().length());
        assertEquals(value.toJson().toString(), ImportCandidate.fromJson(value.toJson()).toJson().toString());
        assertEquals("high", bank("Покупка 1 234,50 KZT 31.10.2026").confidence);
    }
    @Test public void incomeAndExplicitPostTimeConsent() {
        ImportCandidate income = bank("Зачислено 0,10 KZT");
        assertEquals("income", income.kind); assertEquals("0.10", income.amount);
        assertEquals("post_time", income.dateOrigin); assertEquals("2026-10-31", income.date);
        assertEquals("2026-11-01", BankMessageParser.parse("Paid 1.20 KZT", 1793478600000L, "Asia/Tokyo", true).date);
        review(BankMessageParser.parse("Paid 1.20 KZT", 1793478600000L, "UTC", false));
        review(BankMessageParser.parse("Paid 1.20 KZT", 1793478600000L, "Unknown/Zone", true));
        review(BankMessageParser.parse("Paid 1.20 KZT", 0, "UTC", true));
        ImportCandidate invalid = bank("Оплата 1.20 KZT 2026-02-29");
        review(invalid); assertNull(invalid.date);
    }
    @Test public void ambiguityAndUnsupportedEventsCannotAutopost() {
        for (String message : new String[]{"OTP 1234. Оплата 10.00 KZT", "Код: 1234. Оплата 10.00 KZT", "Оплата 1.00 KZT не прошла", "Not paid 1.00 KZT", "Покупка 1.00 KZT pending", "Refund paid 1.00 KZT", "Перевод 1.00 KZT", "Акция! Оплата 1.00 KZT", "Paid 1.00 $", "Paid 1.00 ZZZ", "Paid 1.234 KWD", "Paid 1.00 KZT and 2.00 KZT", "Баланс 1.00 KZT", "Purchase at shop", "Оплата 01 234,50 KZT", "Оплата +1.00 KZT", "Покупка -1.00 KZT +2.00 KZT"}) review(bank(message));
    }
    @Test public void plannedAndNegatedBankOperationsNeedReview() {
        for (String message : new String[]{
                "Запланирована покупка 10.00 RUB 2026-11-02",
                "Оплата 10.00 RUB 2026-11-02 не произведена",
                "Планируется списание 10.00 RUB 2026-11-02",
                "Оплата 10.00 RUB 2026-11-02 НЕ\u00a0ПРОИЗВЕДЕНА",
                "Оплата 10.00 RUB 2026-11-02 не осуществлена",
                "Оплата 10.00 RUB 2026-11-02 непроизведена",
                "Списание 10.00 RUB 2026-11-02 отложено",
                "Оплата 10.00 RUB 2026-11-02 отклонена",
                "Paid 10.00 RUB 2026-11-02 was not made",
                "Scheduled purchase completed 10.00 RUB 2026-11-02",
                "Planned payment completed 10.00 RUB 2026-11-02",
                "Payment completed 10.00 RUB 2026-11-02 wasn't successful"}) {
            ImportCandidate candidate = bank(message);
            review(candidate);
            assertTrue(candidate.reasons.contains("UNSUPPORTED_EVENT"));
            assertEquals("10.00", candidate.amount);
            assertEquals("RUB", candidate.currency);
            assertEquals("2026-11-02", candidate.date);
        }
    }
    @Test public void clearCompletedPurchasesAndDebitsRemainHighConfidence() {
        for (String message : new String[]{
                "Покупка 10.00 RUB 2026-11-02",
                "Покупка 10.00 RUB 2026-11-02 успешно завершена",
                "Оплата 10.00 RUB 2026-11-02 произведена",
                "Оплата 10.00 RUB 2026-11-02 успешно выполнена",
                "Списано 10.00 RUB 2026-11-02",
                "Списание 10.00 RUB 2026-11-02 успешно выполнено",
                "Paid 10.00 RUB 2026-11-02",
                "Debited 10.00 RUB 2026-11-02",
                "Payment completed 10.00 RUB 2026-11-02",
                "Purchase completed 10.00 RUB 2026-11-02"}) {
            ImportCandidate candidate = bank(message);
            assertEquals("high", candidate.confidence);
            assertTrue(candidate.reasons.isEmpty());
            assertEquals("expense", candidate.kind);
            assertEquals("10.00", candidate.amount);
            assertEquals("RUB", candidate.currency);
            assertEquals("2026-11-02", candidate.date);
        }
    }
    @Test public void receiptUsesFinalTotalAndRequiresSourceDate() {
        ImportCandidate receipt = ReceiptParser.parse("Магазин\nТовар 50.00 KZT\nИТОГО 100.20 KZT\nОплачено\nНаличными 200.00 KZT\nСдача 99.80 KZT\n31.10.2026\nhttps://example.invalid");
        assertEquals("high", receipt.confidence); assertEquals("expense", receipt.kind);
        assertEquals("100.20", receipt.amount); assertEquals("KZT", receipt.currency);
        assertEquals("2026-10-31", receipt.date); assertEquals("", receipt.note);
        assertNull(receipt.paymentReference);
        review(ReceiptParser.parse("ИТОГО 100.20 KZT"));
        review(ReceiptParser.parse("ИТОГО 100.20 KZT\n01/02/26"));
        review(ReceiptParser.parse("TOTAL 1,234 KWD\n2026-10-31"));
        review(ReceiptParser.parse("TOTAL 1.00 KZT\nTOTAL 2.00 KZT\n2026-10-31"));
        review(ReceiptParser.parse("TOTAL 1.00 $\n2026-10-31"));
        review(ReceiptParser.parse("ВОЗВРАТ\nИТОГО 1.00 KZT\n2026-10-31"));
        review(ReceiptParser.parse("TOTAL 10.00 KZT\nCash 5.00 KZT\nCard 5.00 KZT\n2026-10-31"));
    }
    @Test public void receiptRequiresCompletedPaymentEvidence() {
        for (String text : new String[]{
                "2026-10-31\nК оплате 123.45 KZT",
                "2026-10-31\nВсего к оплате 123.45 KZT",
                "2026-10-31\nИтого 123.45 KZT",
                "2026-10-31\nTOTAL 123.45 KZT",
                "2026-10-31\nGrand total 123.45 KZT",
                "2026-10-31\nКассовый чек\nИтого 123.45 KZT",
                "2026-10-31\nSubtotal 100.00 KZT\nTOTAL 123.45 KZT",
                "2026-10-31\nТовар 100.00 KZT\nТовар 23.45 KZT\nИтого 123.45 KZT\nВнесено наличными 200.00 KZT\nСдача 76.55 KZT"}) {
            ImportCandidate candidate = ReceiptParser.parse(text);
            review(candidate);
            assertTrue(candidate.reasons.contains("UNSUPPORTED_EVENT"));
            assertEquals("123.45", candidate.amount);
            assertEquals("KZT", candidate.currency);
            assertEquals("2026-10-31", candidate.date);
        }
        review(ReceiptParser.parse("2026-10-31\nSubtotal 123.45 KZT\nОплачено"));
    }
    @Test public void completedPaidReceiptTotalsRemainHighConfidence() {
        for (String text : new String[]{
                "2026-10-31\nОплачено 123.45 KZT",
                "2026-10-31\nTotal paid 123.45 KZT",
                "2026-10-31\nAmount paid 123.45 KZT",
                "2026-10-31\nPaid 123.45 KZT",
                "2026-10-31\nТөленген 123.45 KZT",
                "2026-10-31\nТөленді 123.45 KZT",
                "2026-10-31\nИтого 123.45 KZT\nОплачено",
                "2026-10-31\nК оплате 123.45 KZT\nОплата произведена",
                "2026-10-31\nИтого 123.45 KZT\nОплата успешно выполнена",
                "2026-10-31\nИтого 123.45 KZT\nОплата завершена",
                "2026-10-31\nИтого 123.45 KZT\nPayment completed",
                "2026-10-31\nИтого 123.45 KZT\nСтатус: Оплачено",
                "2026-10-31\nТовар 100.00 KZT\nТовар 23.45 KZT\nИтого 123.45 KZT\nВнесено наличными 200.00 KZT\nСдача 76.55 KZT\nОплачено"}) {
            ImportCandidate candidate = ReceiptParser.parse(text);
            assertEquals("high", candidate.confidence);
            assertTrue(candidate.reasons.isEmpty());
            assertEquals("expense", candidate.kind);
            assertEquals("123.45", candidate.amount);
            assertEquals("KZT", candidate.currency);
            assertEquals("2026-10-31", candidate.date);
        }
    }
    @Test public void receiptNegativeOrPartialPaymentOverridesPaidWords() {
        for (String status : new String[]{"Not paid", "Не оплачено", "Оплачено не полностью", "Төленген жоқ", "Partially paid", "Оплачено?", "Выберите статус Оплачено", "Оплачено\nPayment pending", "Оплата не произведена", "Оплата произведена\nОплата отложена"}) {
            ImportCandidate candidate = ReceiptParser.parse("2026-10-31\nК оплате 123.45 KZT\n" + status);
            review(candidate);
            assertTrue(candidate.reasons.contains("UNSUPPORTED_EVENT"));
            assertEquals("123.45", candidate.amount);
            assertEquals("2026-10-31", candidate.date);
        }
    }
    @Test public void receiptReadsAdjacentTotalsAndLocalTengeAliases() {
        for (String layout : new String[]{
                "Итого\n123,45 тг\nОплачено",
                "ИТОГ:\n\n123.45 теңге\nОплата произведена",
                "Оплачено\n123.45 тенге",
                "Total paid:\nKZT 123.45",
                "Итого 123.45 ТГ\nОплачено",
                "Валюта KZT\nИтого\n123.45\nОплачено"}) {
            ImportCandidate candidate = ReceiptParser.parse("31.10.2026\n" + layout);
            assertEquals(layout, "high", candidate.confidence);
            assertEquals("123.45", candidate.amount);
            assertEquals("KZT", candidate.currency);
        }
    }
    @Test public void identicalFinalAndPaidAmountsAreOneTotal() {
        ImportCandidate candidate = ReceiptParser.parse("2026-10-31\nИтого 123,45 тг\nОплачено 123.45 KZT");
        assertEquals("high", candidate.confidence);
        assertEquals("123.45", candidate.amount);
        assertFalse(candidate.reasons.contains("AMBIGUOUS_TOTAL"));
        ImportCandidate conflict = ReceiptParser.parse("2026-10-31\nИтого 123.45 KZT\nОплачено 124.45 KZT");
        review(conflict);
        assertNull(conflict.amount);
        assertTrue(conflict.reasons.contains("AMBIGUOUS_TOTAL"));
    }
    @Test public void adjacentAmountDoesNotInventCompletedPayment() {
        ImportCandidate candidate = ReceiptParser.parse("31.10.2026\nК оплате\n123.45 тг");
        review(candidate);
        assertEquals("123.45", candidate.amount);
        assertTrue(candidate.reasons.contains("UNSUPPORTED_EVENT"));
        for (String next : new String[]{"Товар 123.45 KZT", "Дата 2026-10-31", "Телефон 87001234567", "Сдача 123.45 KZT", "123.45 KZT товар", "2026-10-31"}) {
            ImportCandidate unavailable = ReceiptParser.parse("2026-10-31\nИтого\n" + next + "\nОплачено");
            review(unavailable);
            assertNull(next, unavailable.amount);
        }
    }
    @Test public void fiscalSaleWithMatchingSingleNoncashPaymentCanAutopost() {
        for (String payment : new String[]{"Банковская карта 123.45 KZT", "Банковской картой\n123.45 тг", "Безналичными 123.45 KZT", "Оплата картой: 123.45 KZT"}) {
            ImportCandidate candidate = ReceiptParser.parse("ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\n31.10.2026\nИТОГО\n123.45 KZT\n" + payment);
            assertEquals(payment, "high", candidate.confidence);
            assertEquals("123.45", candidate.amount);
        }
    }
    @Test public void fiscalWordAloneOrMismatchingPaymentCannotAutopost() {
        for (String text : new String[]{
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT",
                "ФИСКАЛЬНЫЙ ЧЕК\nИтого 123.45 KZT\nБезналичными 123.45 KZT",
                "ПРИХОД\nИтого 123.45 KZT\nБезналичными 123.45 KZT",
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT\nБезналичными 120.00 KZT",
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT\nНаличными 200.00 KZT\nСдача 76.55 KZT",
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT\nБезналичными 123.45 KZT\nОплата отложена",
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT\nБезналичными 123.45 KZT\nВозврат",
                "ФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 123.45 KZT\nБезналичными 100.00 KZT\nНаличными 23.45 KZT"}) {
            ImportCandidate candidate = ReceiptParser.parse("31.10.2026\n" + text);
            review(candidate);
            assertEquals(text, "123.45", candidate.amount);
        }
    }
    @Test public void unknownOrConflictingReceiptCurrencyStillNeedsReview() {
        review(ReceiptParser.parse("31.10.2026\nИтого\n123.45\nОплачено"));
        review(ReceiptParser.parse("31.10.2026\nИтого\n123.45 тг\nОплачено 123.45 USD"));
        assertEquals("high", bank("Покупка 123.45 тг 31.10.2026").confidence); // тг is plain KZT in bank pushes
    }
    @Test public void taxAndFeeAmountsAreNotPurchaseTotals() {
        for (String component : new String[]{"Сумма НДС", "Сумма налога", "Сумма комиссии", "Сумма скидки", "Итого НДС", "Итог налога", "TOTAL TAX"}) {
            ImportCandidate componentOnly = ReceiptParser.parse("03.11.2026\n" + component + " 50.00 тг\nОплачено");
            review(componentOnly);
            assertNull(component, componentOnly.amount);
            assertTrue(componentOnly.reasons.contains("AMOUNT_MISSING"));
        }
        for (String component : new String[]{"Сумма НДС", "Итого НДС", "Сумма налога", "TOTAL TAX"}) {
            ImportCandidate total = ReceiptParser.parse("03.11.2026\n" + component + " 50.00 тг\nИтого 500.00 тг\nОплачено");
            assertEquals(component, "high", total.confidence);
            assertEquals("500.00", total.amount);
        }
    }
    @Test public void finalLabelsRequireAmountOnlyTailsAndKeepValidSumLayouts() {
        for (String layout : new String[]{"Сумма: 500.00 тг", "Сумма\n500.00 тг", "Итого:\n500.00 тг", "Сумма KZT 500.00"}) {
            ImportCandidate total = ReceiptParser.parse("03.11.2026\n" + layout + "\nОплачено");
            assertEquals(layout, "high", total.confidence);
            assertEquals("500.00", total.amount);
        }
        ImportCandidate partial = ReceiptParser.parse("03.11.2026\nИтого 500.00 тг\nОплачено НДС 50.00 тг");
        review(partial);
        assertEquals("500.00", partial.amount);
        assertTrue(partial.reasons.contains("UNSUPPORTED_EVENT"));
    }
    @Test public void unknownCurrencySuffixCanOnlyPrefillAnExplicitReceiptCurrencyForReview() {
        for (String layout : new String[]{"ИТОГО\n\n8765.43 КАТ", "Итого 8765.43 КИТ", "Итого 8765.43 XYZ"}) {
            ImportCandidate candidate = ReceiptParser.parse("03.11.2026\n" + layout + "\nКартой 8765.43 KZT\nОплачено");
            review(candidate);
            assertEquals(layout, "8765.43", candidate.amount);
            assertEquals("KZT", candidate.currency);
            assertTrue(candidate.reasons.contains("CURRENCY_UNKNOWN"));
        }
        ImportCandidate noExplicitCurrency = ReceiptParser.parse("03.11.2026\nИтого\n8765.43 КАТ\nОплачено");
        review(noExplicitCurrency);
        assertNull(noExplicitCurrency.amount);
        assertNull(noExplicitCurrency.currency);
        for (String component : new String[]{"Сумма НДС 50.00 КАТ", "Итого НДС 50.00 КИТ", "Итого 50.00 НДС", "Сумма 50.00 TAX", "TOTAL 50.00 VAT", "TOTAL 50.00 FEE"}) {
            ImportCandidate tax = ReceiptParser.parse("03.11.2026\n" + component + "\nВалюта KZT\nОплачено");
            review(tax);
            assertNull(component, tax.amount);
        }
    }
    @Test public void schemaIsStrictAndRawReferenceIsTransientOnly() throws Exception {
        JSONObject value = bank("Оплата 1.00 KZT").toJson();
        value.put("amount", 1);
        try { ImportCandidate.fromJson(value); fail(); } catch (ContractException expected) { assertEquals("amount", expected.field); }
        value = bank("Оплата 1.00 KZT").toJson(); value.remove("paymentReference");
        try { ImportCandidate.fromJson(value); fail(); } catch (ContractException expected) { assertEquals("paymentReference", expected.field); }
        value = bank("Оплата 1.00 KZT").toJson(); value.put("rawText", "synthetic");
        try { ImportCandidate.fromJson(value); fail(); } catch (ContractException expected) { assertEquals("VALIDATION", expected.code); }
    }
}
