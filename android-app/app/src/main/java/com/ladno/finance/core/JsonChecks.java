package com.ladno.finance.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Strict JSON types: org.json's coercing getString/getInt methods are not validators. */
public final class JsonChecks {
    private JsonChecks() {}
    public static ContractException invalid(String field) {
        return new ContractException("VALIDATION", field, "Проверьте данные поля.");
    }
    public static void keys(JSONObject value, String... required) {
        keysOptional(value, required, new String[0]);
    }
    public static void keysOptional(JSONObject value, String[] required, String[] optional) {
        if (value == null) throw invalid(null);
        Set<String> allowed = new HashSet<>(Arrays.asList(required));
        allowed.addAll(Arrays.asList(optional));
        for (Iterator<String> it = value.keys(); it.hasNext();) {
            if (!allowed.contains(it.next())) throw invalid(null);
        }
        for (String key : required) if (!value.has(key)) throw invalid(key);
    }
    public static Object value(JSONObject object, String key) {
        if (!object.has(key)) throw invalid(key);
        try { return object.get(key); } catch (JSONException ex) { throw invalid(key); }
    }
    public static boolean isNull(JSONObject object, String key) { return value(object, key) == JSONObject.NULL; }
    public static String string(JSONObject object, String key, int max) {
        Object value = value(object, key);
        if (!(value instanceof String) || ((String) value).length() > max) throw invalid(key);
        return (String) value;
    }
    public static String nullableString(JSONObject object, String key, int max) {
        return isNull(object, key) ? null : string(object, key, max);
    }
    public static JSONObject object(JSONObject object, String key) {
        Object value = value(object, key);
        if (!(value instanceof JSONObject)) throw invalid(key);
        return (JSONObject) value;
    }
    public static JSONArray array(JSONObject object, String key, int max) {
        Object value = value(object, key);
        if (!(value instanceof JSONArray) || ((JSONArray) value).length() > max) throw invalid(key);
        return (JSONArray) value;
    }
    public static long integer(JSONObject object, String key) {
        Object value = value(object, key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger)) throw invalid(key);
        BigInteger integer = new BigInteger(value.toString());
        if (integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0 || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) throw invalid(key);
        return integer.longValue();
    }
    public static boolean bool(JSONObject object, String key) {
        Object value = value(object, key);
        if (!(value instanceof Boolean)) throw invalid(key);
        return (Boolean) value;
    }
    public static String uuid(JSONObject object, String key, boolean nullable) {
        if (nullable && isNull(object, key)) return null;
        String text = string(object, key, 36);
        try { if (!UUID.fromString(text).toString().equals(text)) throw invalid(key); }
        catch (IllegalArgumentException ex) { throw invalid(key); }
        return text;
    }
    public static String choice(JSONObject object, String key, String... choices) {
        String value = string(object, key, 64);
        for (String choice : choices) if (choice.equals(value)) return value;
        throw invalid(key);
    }
    public static String instant(String value, String field) {
        try {
            if (value == null || value.length() > 40 || !value.endsWith("Z")) throw invalid(field);
            return Instant.parse(value).toString();
        } catch (java.time.DateTimeException ex) { throw invalid(field); }
    }
    public static String text(String value, int max, boolean nonempty, String field) {
        String trimmed = value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > max || (nonempty && trimmed.isEmpty())) throw invalid(field);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') throw invalid(field);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= trimmed.length() || !Character.isLowSurrogate(trimmed.charAt(i + 1))) throw invalid(field);
                i++;
            } else if (Character.isLowSurrogate(c)) throw invalid(field);
        }
        return trimmed;
    }
    public static JSONObject put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); return object; }
        catch (JSONException ex) { throw new ContractException("INTERNAL", null, "Не удалось подготовить данные."); }
    }
}
