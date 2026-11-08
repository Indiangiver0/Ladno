package com.ladno.finance;

import com.ladno.finance.core.ContractException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Safe construction and a bounded JSON parser accepting the contract's integer-number subset. */
final class NativeJson {
    private NativeJson() {}

    static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2)
                result.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
        } catch (JSONException e) { throw new IllegalStateException("JSON construction failed"); }
        return result;
    }

    static JSONObject error(String code, String field, String message) {
        return object("code", code, "message", message, "field", field);
    }

    static void put(JSONObject value, String name, Object item) {
        try { value.put(name, item == null ? JSONObject.NULL : item); }
        catch (JSONException e) { throw new IllegalStateException("JSON construction failed"); }
    }

    static void keys(JSONObject object, String... required) {
        Set<String> names = new HashSet<>(java.util.Arrays.asList(required));
        if (object.length() != names.size()) invalid(null);
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) if (!names.contains(iterator.next())) invalid(null);
    }

    static String string(JSONObject object, String key, int max, boolean nullable) {
        if (!object.has(key)) invalid(key);
        Object value = object.opt(key);
        if (nullable && value == JSONObject.NULL) return null;
        if (!(value instanceof String) || ((String) value).length() > max) invalid(key);
        return (String) value;
    }

    static void revision(JSONObject object, String key, boolean nullable) {
        Object value = object.opt(key);
        if (nullable && value == JSONObject.NULL) return;
        if (!(value instanceof Long || value instanceof Integer) || ((Number) value).longValue() < 1)
            invalid(key);
    }

    static void bool(JSONObject object, String key) {
        if (!(object.opt(key) instanceof Boolean)) invalid(key);
    }

    static void uuid(JSONObject object, String key, boolean nullable) {
        String value = string(object, key, 36, nullable);
        if (value != null && !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            invalid(key);
    }

    static void invalid(String field) {
        throw new ContractException("VALIDATION", field, "Проверьте поля запроса.");
    }

    static JSONObject parseObject(String input) {
        try {
            Parser parser = new Parser(input);
            Object value = parser.value(0);
            parser.space();
            if (!(value instanceof JSONObject) || parser.pos != input.length()) throw new IllegalArgumentException();
            return (JSONObject) value;
        } catch (JSONException | IllegalArgumentException e) {
            throw new ContractException("BAD_JSON", null, "Некорректный JSON-запрос.");
        }
    }

    private static final class Parser {
        final String input;
        int pos;
        Parser(String input) { this.input = input; }
        void space() {
            while (pos < input.length() && " \n\r\t".indexOf(input.charAt(pos)) >= 0) pos++;
        }
        char take() {
            if (pos == input.length()) throw new IllegalArgumentException();
            return input.charAt(pos++);
        }
        boolean next(char ch) {
            space();
            if (pos < input.length() && input.charAt(pos) == ch) { pos++; return true; }
            return false;
        }
        Object value(int depth) throws JSONException {
            if (depth > 12) throw new IllegalArgumentException();
            space();
            char ch = take();
            if (ch == '"') return quoted();
            if (ch == '{') {
                JSONObject object = new JSONObject();
                Set<String> keys = new HashSet<>();
                if (next('}')) return object;
                do {
                    space();
                    if (take() != '"') throw new IllegalArgumentException();
                    String name = quoted();
                    if (!keys.add(name) || !next(':')) throw new IllegalArgumentException();
                    object.put(name, value(depth + 1));
                    if (next('}')) return object;
                } while (next(','));
                throw new IllegalArgumentException();
            }
            if (ch == '[') {
                JSONArray array = new JSONArray();
                if (next(']')) return array;
                do {
                    if (array.length() >= 1000) throw new IllegalArgumentException();
                    array.put(value(depth + 1));
                    if (next(']')) return array;
                } while (next(','));
                throw new IllegalArgumentException();
            }
            pos--;
            for (String literal : new String[]{"true", "false", "null"}) {
                if (input.startsWith(literal, pos)) {
                    pos += literal.length();
                    return literal.equals("null") ? JSONObject.NULL : Boolean.valueOf(literal);
                }
            }
            int start = pos;
            if (input.charAt(pos) == '-') pos++;
            if (pos == input.length()) throw new IllegalArgumentException();
            if (input.charAt(pos) == '0') pos++;
            else {
                if (input.charAt(pos) < '1' || input.charAt(pos) > '9') throw new IllegalArgumentException();
                while (pos < input.length() && digit(input.charAt(pos))) pos++;
            }
            // The contract has only integer JSON numbers; money is always a string.
            if (pos < input.length() && ".eE".indexOf(input.charAt(pos)) >= 0) throw new IllegalArgumentException();
            return Long.valueOf(input.substring(start, pos));
        }
        String quoted() {
            StringBuilder result = new StringBuilder();
            while (true) {
                char ch = take();
                if (ch == '"') {
                    String value = result.toString();
                    for (int i = 0; i < value.length(); i++) {
                        char item = value.charAt(i);
                        if (Character.isHighSurrogate(item)) {
                            if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException();
                        } else if (Character.isLowSurrogate(item)) throw new IllegalArgumentException();
                    }
                    return value;
                }
                if (ch < 0x20) throw new IllegalArgumentException();
                if (ch == '\\') {
                    char escape = take();
                    switch (escape) {
                        case '"': case '\\': case '/': ch = escape; break;
                        case 'b': ch = '\b'; break;
                        case 'f': ch = '\f'; break;
                        case 'n': ch = '\n'; break;
                        case 'r': ch = '\r'; break;
                        case 't': ch = '\t'; break;
                        case 'u':
                            if (pos + 4 > input.length()) throw new IllegalArgumentException();
                            for (int i = pos; i < pos + 4; i++) if (Character.digit(input.charAt(i), 16) < 0 || input.charAt(i) > 127)
                                throw new IllegalArgumentException();
                            ch = (char) Integer.parseInt(input.substring(pos, pos + 4), 16);
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException();
                    }
                }
                result.append(ch);
            }
        }
        boolean digit(char ch) { return ch >= '0' && ch <= '9'; }
    }
}
