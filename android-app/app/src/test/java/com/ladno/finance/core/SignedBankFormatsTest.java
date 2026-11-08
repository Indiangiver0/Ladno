package com.ladno.finance.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** Common KZ bank push shapes (Freedom/Halyk/Kaspi style): signed amounts, glued "тг", Kazakh words. */
public class SignedBankFormatsTest {
    private static ImportCandidate bank(String text) {
        return BankMessageParser.parse(text, 1793478600000L, "Asia/Almaty", true);
    }

    @Test public void signedAmountIsParsedAndGivesDirection() {
        ImportCandidate minus = bank("Halyk\n-2 500,00 ₸ MAGNUM CASH&CARRY");
        assertEquals("2500.00", minus.amount);
        assertEquals("KZT", minus.currency);
        assertEquals("expense", minus.kind);
        assertEquals("high", minus.confidence);

        ImportCandidate plus = bank("Freedom Bank\n+10 000 ₸");
        assertEquals("10000.00", plus.amount);
        assertEquals("income", plus.kind);

        assertEquals("expense", bank("−1 200 ₸ Glovo").kind);
        assertEquals("1200.00", bank("−1 200 ₸ Glovo").amount);
        assertEquals("expense", bank("Покупка: -3 990 KZT").kind);
    }

    @Test public void transferWithSignKeepsAmountAndDirectionForReview() {
        ImportCandidate out = bank("Перевод\n-5 000 ₸ Айгерим С.");
        assertEquals("5000.00", out.amount);
        assertEquals("expense", out.kind);
        assertNotEquals("high", out.confidence); // transfers stay reviewable

        ImportCandidate in = bank("Перевод\n+7 500 ₸ от Ерлан Б.");
        assertEquals("7500.00", in.amount);
        assertEquals("income", in.kind);
    }

    @Test public void conflictingSignAndWordOrOwnAccountsNeverGuessDirection() {
        ImportCandidate conflict = bank("Покупка +100 ₸");
        assertNull(conflict.kind);
        assertNotEquals("high", conflict.confidence);
        assertNull(bank("Перевод между своими счетами -100 ₸").kind);
    }

    @Test public void gluedTengeAndKazakhWords() {
        ImportCandidate glued = bank("Оплата 5000тг");
        assertEquals("5000.00", glued.amount);
        assertEquals("expense", glued.kind);
        assertEquals("expense", bank("Төлем 1 500 ₸").kind);
        assertEquals("income", bank("Шотыңыз толықтырылды 20 000 ₸").kind);
        assertEquals("income", bank("Зачисление 3 000 ₸").kind);
        assertEquals("income", bank("Поступление 3 000 ₸").kind);
        assertEquals("income", bank("Входящий перевод 3 000 ₸").kind);
    }

    @Test public void balanceBeforePurchaseOnSameLineDoesNotEraseIt() {
        ImportCandidate value = bank("Доступно: 120 000 ₸. Покупка 2 300 ₸ SMALL");
        assertEquals("2300.00", value.amount);
        assertEquals("expense", value.kind);
    }

    @Test public void phoneNumbersAndDatesAreNotSigns() {
        ImportCandidate value = bank("Покупка 1 000 ₸ 2026-10-31 +7 701 000 00 00");
        assertEquals("1000.00", value.amount);
        assertEquals("expense", value.kind);
        assertEquals("2026-10-31", value.date);
    }
}
