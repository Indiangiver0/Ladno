package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScanOutcomeTest {
    private static final String CANDIDATE = "00000000-0000-4000-8000-000000000001";
    private static final String RECORD = "00000000-0000-4000-8000-000000000002";

    private JSONObject result(String status, boolean record) {
        return NativeJson.object("outcome", status,
            "candidate", NativeJson.object("id", CANDIDATE, "date", "2026-10-31", "currency", "KZT",
                "status", "posted", "transactionId", RECORD, "rawText", "not projected"),
            "record", record ? NativeJson.object("id", RECORD, "date", "2026-11-02", "currency", "USD",
                "amount", "12.00", "note", "not projected") : null);
    }

    @Test public void committedRecordProvidesDestinationAndNoOtherData() {
        JSONObject outcome = ScanOutcome.fromIngest(result("posted", true));
        assertEquals(5, outcome.length());
        assertEquals("posted", outcome.optString("status"));
        assertEquals(CANDIDATE, outcome.optString("candidateId"));
        assertEquals(RECORD, outcome.optString("transactionId"));
        assertEquals("2026-11", outcome.optString("month"));
        assertEquals("USD", outcome.optString("currency"));
        assertFalse(outcome.toString().contains("not projected"));
    }

    @Test public void reviewAndExactReplayAreDistinctFromSuccessfulNewExpense() {
        assertEquals("needs_review", ScanOutcome.fromIngest(result("review", false)).optString("status"));
        JSONObject duplicate = ScanOutcome.fromIngest(result("duplicate", false));
        assertEquals("duplicate", duplicate.optString("status"));
        assertTrue(duplicate.isNull("transactionId")); // deleted transaction is not a visible record
        assertEquals("2026-10", duplicate.optString("month"));
        assertEquals("linked", ScanOutcome.fromIngest(result("linked", true)).optString("status"));
        assertEquals("ignored", ScanOutcome.fromIngest(result("ignored", false)).optString("status"));
    }

    @Test public void unavailableCandidateFieldsStayNull() {
        JSONObject result = result("review", false);
        NativeJson.put(result.optJSONObject("candidate"), "date", null);
        NativeJson.put(result.optJSONObject("candidate"), "currency", null);
        JSONObject outcome = ScanOutcome.fromIngest(result);
        assertTrue(outcome.isNull("month"));
        assertTrue(outcome.isNull("currency"));
    }

    @Test public void malformedStructuredFieldsAreNotForwarded() {
        JSONObject badId = result("posted", true);
        NativeJson.put(badId.optJSONObject("record"), "id", "raw source");
        rejected(badId);
        JSONObject badDate = result("review", false);
        NativeJson.put(badDate.optJSONObject("candidate"), "date", "2026-02-30");
        rejected(badDate);
        JSONObject badCurrency = result("review", false);
        NativeJson.put(badCurrency.optJSONObject("candidate"), "currency", "ZZZ");
        rejected(badCurrency);
        rejected(result("raw source", false));
    }

    private void rejected(JSONObject value) {
        try { ScanOutcome.fromIngest(value); fail("Expected invalid structured result"); }
        catch (ContractException expected) { assertNotNull(expected.code); }
    }
}
