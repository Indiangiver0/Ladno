package com.ladno.finance;

import com.ladno.finance.core.BankMessageParser;
import com.ladno.finance.core.ImportCandidate;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationTextTest {
    private static ImportCandidate parse(String value) {
        return BankMessageParser.parse(value, 1793478600000L, "UTC", true);
    }

    @Test public void linesStillSupplyFieldsWhenShortBodyExists() {
        String value = NotificationText.assemble("Банк", "Успешно", null,
            new CharSequence[]{"Пополнение 123.45 KZT", "31.10.2026"});
        ImportCandidate result = parse(value);
        assertEquals("income", result.kind);
        assertEquals("123.45", result.amount);
        assertEquals("2026-10-31", result.date);
    }

    @Test public void equivalentViewsDoNotDuplicateAmounts() {
        String full = "Покупка 123.45 KZT\n31.10.2026";
        assertEquals(full, NotificationText.assemble("Покупка 123.45 KZT",
            "Покупка 123.45 KZT", full,
            new CharSequence[]{"Покупка 123.45 KZT", "31.10.2026"}));
        ImportCandidate result = parse(NotificationText.assemble(null, full, full, null));
        assertEquals("123.45", result.amount);
        assertEquals("high", result.confidence);
    }

    @Test public void distinctEqualAmountsAreNotCollapsed() {
        ImportCandidate result = parse(NotificationText.assemble(null,
            "Оплачено магазин А 100.00 KZT", "Оплачено магазин Б 100.00 KZT", null));
        assertNull(result.amount);
        assertTrue(result.reasons.contains("AMBIGUOUS_TOTAL"));
    }

    @Test public void repeatedLinesWithinOneViewKeepMultiplicity() {
        String line = "Покупка 100.00 KZT";
        String value = NotificationText.assemble(null, line, null, new CharSequence[]{line, line});
        assertEquals(line + "\n" + line, value);
        assertNull(parse(value).amount);
        assertTrue(parse(value).reasons.contains("AMBIGUOUS_TOTAL"));
    }

    @Test public void conflictingCompactAndExpandedViewsStayAmbiguous() {
        ImportCandidate result = parse(NotificationText.assemble("Банк", "Покупка 10.00 KZT", "Покупка 20.00 KZT", null));
        assertNull(result.amount);
        assertNotEquals("high", result.confidence);
        ImportCandidate direction = parse(NotificationText.assemble(null,
            "Списано 10.00 KZT", "Зачислено 10.00 KZT", null));
        assertNull(direction.kind);
        assertNotEquals("high", direction.confidence);
    }

    @Test public void partialStringMatchesAreNotEquivalentRepresentations() {
        String value = NotificationText.assemble(null, "Покупка 10.00 KZT", "Покупка 10.00 KZT отменена", null);
        assertTrue(value.contains("\nПокупка 10.00 KZT"));
        assertNotEquals("high", parse(value).confidence);
    }

    @Test public void emptyAndLineEndingsAreHandledWithoutInventingText() {
        assertEquals("", NotificationText.assemble(null, "", null, new CharSequence[]{null, ""}));
        assertEquals("Оплата\n10.00 KZT", NotificationText.assemble(null, "Оплата\r\n10.00 KZT", "Оплата\n10.00 KZT", null));
    }

    @Test public void sourceAndCombinedLimitsAreEnforced() {
        assertEquals(65536, NotificationText.assemble(null, "x".repeat(65536), null, null).length());
        try { NotificationText.assemble(null, "x".repeat(65537), null, null); fail(); }
        catch (NotificationText.LimitExceeded expected) { }
        try { NotificationText.assemble("a".repeat(32768), "b".repeat(32768), null, null); fail(); }
        catch (NotificationText.LimitExceeded expected) { }
        try { NotificationText.assemble(null, null, null, new CharSequence[65537]); fail(); }
        catch (NotificationText.LimitExceeded expected) { }
    }
}
