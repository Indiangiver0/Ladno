package com.ladno.finance.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import static org.junit.Assert.*;

public class MoneyTest {
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) {
        try { action.run(); fail("Expected validation rejection"); }
        catch (ContractException expected) { assertEquals("VALIDATION", expected.code); }
        catch (Exception unexpected) { throw new AssertionError(unexpected); }
    }
    private static JSONObject fixture() throws Exception {
        for (String relative : new String[]{"fixtures/money-cases.json", "../fixtures/money-cases.json", "android-app/fixtures/money-cases.json"}) {
            Path path = Paths.get(relative);
            if (Files.isRegularFile(path)) return new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        }
        throw new AssertionError("Independent money fixture not found");
    }
    /** Independent finance fixtures are read-only domain inputs, not copied expectations. */
    @Test public void independentManualVariants() throws Exception {
        JSONArray groups = fixture().getJSONArray("caseGroups");
        int checked = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject input = groups.getJSONObject(i).getJSONObject("input");
            JSONArray variants = input.optJSONArray("variants");
            if (variants == null) continue;
            for (int v = 0; v < variants.length(); v++) {
                JSONObject variant = variants.getJSONObject(v);
                JSONObject value = variant.optJSONObject("input"), expected = variant.optJSONObject("expected");
                if (value == null || expected == null || !value.has("raw") || !value.has("currency")) continue;
                String raw = value.getString("raw"), currency = value.getString("currency");
                if (expected.getString("decision").equals("accept")) {
                    Money actual = Money.parseManual(raw, currency);
                    assertEquals(expected.getString("amount"), actual.amount);
                    assertEquals(expected.getString("minor"), actual.minor.toString());
                    assertEquals(expected.getInt("scale"), actual.scale);
                } else rejects(() -> Money.parseManual(raw, currency));
                checked++;
            }
        }
        assertTrue("Expected at least 40 independent variants", checked >= 40);
    }
    @Test public void independentSourceGroupingAndAmbiguity() throws Exception {
        JSONArray groups = fixture().getJSONArray("caseGroups");
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            String id = group.getString("id");
            if (!id.equals("MONEY-010") && !id.equals("MONEY-011")) continue;
            JSONObject input = group.getJSONObject("input");
            JSONArray variants = input.getJSONArray("variants");
            for (int v = 0; v < variants.length(); v++) {
                JSONObject value = variants.getJSONObject(v);
                String raw = value.getString("raw"), currency = value.optString("currency", input.optString("currency"));
                JSONObject expected = value.optJSONObject("expected");
                if (expected != null && expected.optString("decision").equals("normalize")) {
                    assertEquals(expected.getString("amount"), Money.parseSource(raw, currency).amount);
                    assertEquals(expected.getString("minor"), Money.parseSource(raw, currency).minor.toString());
                } else rejects(() -> Money.parseSource(raw, currency));
            }
        }
    }
    @Test public void independentCanonicalAndStrictCalendar() throws Exception {
        JSONArray groups = fixture().getJSONArray("caseGroups");
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i), input = group.getJSONObject("input");
            if (group.getString("id").equals("MONEY-005")) {
                JSONArray valid = input.getJSONArray("valid"), invalid = input.getJSONArray("nonCanonical");
                for (int v = 0; v < valid.length(); v++) {
                    JSONObject value = valid.getJSONObject(v);
                    assertEquals(value.getString("amount"), Money.parseCanonical(value.getString("amount"), value.getString("currency"), value.getInt("scale")).amount);
                }
                for (int v = 0; v < invalid.length(); v++) {
                    JSONObject value = invalid.getJSONObject(v);
                    rejects(() -> Money.parseCanonical(value.getString("amount"), value.getString("currency"), value.getInt("scale")));
                }
            }
            if (group.getString("id").equals("MONEY-020")) {
                JSONArray dates = input.getJSONArray("dates"), decisions = group.getJSONObject("expected").getJSONArray("dateDecisions");
                for (int d = 0; d < dates.length(); d++) {
                    String date = dates.getString(d);
                    if (decisions.getString(d).equals("accept")) assertEquals(date, Money.date(date));
                    else rejects(() -> Money.date(date));
                }
            }
        }
    }
    @Test public void independentAggregateOraclesHaveNoInputLimit() throws Exception {
        JSONArray groups = fixture().getJSONArray("caseGroups");
        int assertions = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i), input = group.getJSONObject("input"), expected = group.getJSONObject("expected");
            JSONArray operations = input.optJSONArray("operations");
            if (operations == null) continue;
            JSONObject summary = expected.optJSONObject("summary");
            if (summary != null) { checkAggregate(operations, summary); assertions++; }
            JSONArray summaries = expected.optJSONArray("summaries");
            if (summaries != null) for (int s = 0; s < summaries.length(); s++) { checkAggregate(operations, summaries.getJSONObject(s)); assertions++; }
        }
        assertTrue(assertions >= 9);
        Money max = Money.parseManual("999999999999999999999999.99", "KZT");
        assertEquals("1999999999999999999999999.98", Money.formatMinor(max.minor.add(max.minor), 2));
        assertEquals("-0.20", Money.formatMinor(new BigInteger("-20"), 2));
    }
    private static void checkAggregate(JSONArray operations, JSONObject summary) throws Exception {
        BigInteger income = BigInteger.ZERO, expense = BigInteger.ZERO;
        YearMonth month = Money.month(summary.getString("month"));
        String currency = summary.getString("currency");
        int scale = summary.getInt("scale");
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.getJSONObject(i);
            LocalDate date = LocalDate.parse(Money.date(operation.getString("date")));
            if (!operation.getString("currency").equals(currency) || !YearMonth.from(date).equals(month) || !operation.getString("status").equals("posted")) continue;
            Money money = Money.parseCanonical(operation.getString("amount"), currency, operation.getInt("scale"));
            if (operation.getString("kind").equals("income")) income = income.add(money.minor); else expense = expense.add(money.minor);
        }
        assertEquals(summary.getString("income"), Money.formatMinor(income, scale));
        assertEquals(summary.getString("expense"), Money.formatMinor(expense, scale));
        assertEquals(summary.getString("net"), Money.formatMinor(income.subtract(expense), scale));
    }
    @Test public void strictTypesAndUnsupportedCurrency() {
        rejects(() -> Money.parseManual("1", "XXX"));
        rejects(() -> Money.parseManual("1", "ZZZ"));
        rejects(() -> Money.parseCanonical("1.000", "KZT", 3));
        rejects(() -> JsonChecks.string(new JSONObject().put("amount", 1.20), "amount", 64));
        rejects(() -> JsonChecks.integer(new JSONObject().put("revision", 1.0), "revision"));
        rejects(() -> Money.month("0000-01"));
        rejects(() -> Money.month("2026-13"));
    }
}
