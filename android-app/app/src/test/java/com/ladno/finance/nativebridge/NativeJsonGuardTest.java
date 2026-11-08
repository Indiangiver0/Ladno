package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Author tests for the native boundary; they do not exercise Android UI/OCR/SQLite. */
public final class NativeJsonGuardTest {
    @Test public void preservesTextAndExactIntegerTypes() throws Exception {
        JSONObject value = NativeJson.parseObject("{\"text\":\"\\u0410\\n😀\",\"revision\":9223372036854775807,\"empty\":null}");
        assertEquals("А\n😀", value.getString("text"));
        assertEquals(Long.MAX_VALUE, value.getLong("revision"));
        assertTrue(value.isNull("empty"));
    }

    @Test public void rejectsNonJsonAndAmbiguousOrUnboundedStructures() {
        String[] bad = {"null", "[]", "{'x':1}", "{x:1}", "{\"x\":1,}", "{\"x\":1} trailing",
            "{\"x\":1,\"x\":2}", "{\"a\":{\"x\":1,\"x\":2}}", "{\"x\":01}", "{\"x\":1.0}",
            "{\"x\":NaN}", "{\"x\":true false}", "{\"x\":\"\\u+123\"}", "{\"x\":\"\\uD800\"}",
            "{\"x\":9223372036854775808}", "{\"x\":\"line\nfeed\"}"};
        for (String input : bad) {
            try { NativeJson.parseObject(input); fail("Accepted malformed JSON"); }
            catch (ContractException e) { assertEquals("BAD_JSON", e.code); }
        }
        StringBuilder nested = new StringBuilder("{\"x\":");
        for (int i = 0; i < 20; i++) nested.append('[');
        nested.append('0');
        for (int i = 0; i < 20; i++) nested.append(']');
        nested.append('}');
        try { NativeJson.parseObject(nested.toString()); fail("Accepted excessive depth"); }
        catch (ContractException e) { assertEquals("BAD_JSON", e.code); }
    }

    @Test public void rejectsActionsFieldsAndPayloadBytesBeforeNativeDispatch() throws Exception {
        AppBridge bridge = new AppBridge(null);
        assertError(bridge.request("executeSql", "{}"), "UNKNOWN_ACTION");
        assertError(bridge.request("state", "[]"), "BAD_JSON");
        assertError(bridge.request("state", "{\"path\":\"secret\"}"), "VALIDATION");
        assertError(bridge.request("scanGallery", "{\"uri\":\"content://example\"}"), "VALIDATION");
        assertError(bridge.request("settings", "{\"primaryCurrency\":\"USD\",\"zoneId\":\"UTC\",\"datePolicy\":\"GUESS\"}"), "VALIDATION");
        StringBuilder oversized = new StringBuilder("{\"text\":\"");
        for (int i = 0; i < 24000; i++) oversized.append('文');
        oversized.append("\"}");
        assertTrue(oversized.length() < 65536);
        assertError(bridge.request("state", oversized.toString()), "LIMIT_EXCEEDED");
    }

    private void assertError(String response, String code) throws Exception {
        JSONObject envelope = new JSONObject(response);
        assertFalse(envelope.getBoolean("ok"));
        assertTrue(envelope.isNull("data"));
        assertEquals(code, envelope.getJSONObject("error").getString("code"));
        assertFalse(response.contains("secret"));
    }
}
