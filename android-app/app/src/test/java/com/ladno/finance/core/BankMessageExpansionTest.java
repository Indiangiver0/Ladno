package com.ladno.finance.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BankMessageExpansionTest {
    private static ImportCandidate bank(String text) {
        return BankMessageParser.parse(text, 1793478600000L, "UTC", true);
    }
    private static void review(ImportCandidate value) {
        assertNotEquals("high", value.confidence);
        assertFalse(value.reasons.isEmpty());
    }

    @Test public void tengeSpellingsAreOrdinaryKzt() {
        for (String currency : new String[]{"тг", "ТГ", "тенге", "теңге"}) {
            ImportCandidate value = bank("Пополнение 1 234,50 " + currency + " 31.10.2026");
            assertEquals(currency, "1234.50", value.amount);
            assertEquals("KZT", value.currency);
            assertEquals("income", value.kind);
            assertEquals("2026-10-31", value.date);
            assertEquals(currency, "high", value.confidence);
        }
    }

    @Test public void unknownOrEmbeddedAliasesDoNotSupplyCurrency() {
        for (String suffix : new String[]{"тгк", "автотг", "теңгелік", "$", "ZZZ"}) {
            ImportCandidate value = bank("Пополнение 123.45 " + suffix + " 31.10.2026");
            assertNull(suffix, value.amount);
            review(value);
        }
        review(bank("Пополнение 1.234 тг 31.10.2026"));
        assertNull(bank("Пополнение 1.234 тг 31.10.2026").amount);
    }

    @Test public void explicitOutgoingAndIncomingTransfersPrefillDirection() {
        for (String prefix : new String[]{"Вы перевели", "Вы отправили", "You sent", "You transferred"}) {
            ImportCandidate value = bank(prefix + " 500.00 KZT 31.10.2026");
            assertEquals(prefix, "expense", value.kind);
            assertEquals("500.00", value.amount);
            review(value);
        }
        for (String prefix : new String[]{"Вам перевели", "Вам отправили", "Получен перевод", "Перевод получен", "Перевод зачислен", "Transfer received"}) {
            ImportCandidate value = bank(prefix + " 500.00 KZT 31.10.2026");
            assertEquals(prefix, "income", value.kind);
            assertEquals("500.00", value.amount);
            review(value);
        }
    }

    @Test public void anonymousAndOwnAccountTransfersDoNotInventDirection() {
        for (String prefix : new String[]{"Переведено", "Перевод выполнен", "Вы перевели между своими счетами",
            "Вам перевели между вашими счетами", "Пополнение на свой счёт", "You sent to your account"}) {
            ImportCandidate value = bank(prefix + " 500.00 KZT 31.10.2026");
            assertNull(prefix, value.kind);
            assertEquals("500.00", value.amount);
            review(value);
        }
    }

    @Test public void negatedCancelledAndRequestedTransfersNeverAutopost() {
        for (String text : new String[]{"Вы не перевели", "Вам перевели, но перевод отменён", "Вы перевели? Подтвердите перевод",
            "Вы перевели, статус: ожидает", "Получен перевод: возврат", "OTP Вы отправили"}) {
            ImportCandidate value = bank(text + " 500.00 KZT 31.10.2026");
            review(value);
            assertEquals("500.00", value.amount);
        }
    }

    @Test public void feesBalanceAndDifferentOperationsAreNotSingleTransferAmount() {
        ImportCandidate fee = bank("Вы перевели 500.00 KZT\nКомиссия 10.00 KZT\n31.10.2026");
        review(fee);
        assertNull(fee.amount);
        assertTrue(fee.reasons.contains("AMBIGUOUS_TOTAL"));
        ImportCandidate balance = bank("Вы перевели 500.00 тг\nБаланс 10 000.00 тенге\n31.10.2026");
        review(balance);
        assertEquals("500.00", balance.amount);
        assertTrue(balance.reasons.contains("AMBIGUOUS_TOTAL"));
        ImportCandidate both = bank("Вы перевели 500.00 KZT\nВам перевели 300.00 KZT\n31.10.2026");
        review(both);
        assertNull(both.kind);
        assertNull(both.amount);
    }

    @Test public void postTimeRequiresExistingConsentAndCurrencyCannotComeFromSettings() {
        ImportCandidate noDate = BankMessageParser.parse("Вам перевели 12.00 тг", 1793478600000L, "UTC", false);
        assertNull(noDate.date);
        assertEquals("12.00", noDate.amount);
        review(noDate);
        assertNull(bank("Вам перевели 12.00").amount);
        assertEquals("2026-10-31", bank("Вам перевели 12.00 KZT").date);
    }
}
