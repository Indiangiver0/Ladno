package com.ladno.finance.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded JSON with number lexemes: Android's org.json would otherwise use double. */
final class OfdJson {
    static final class NumberToken {
        final String text;
        NumberToken(String text) { this.text = text; }
    }
    private final String input;
    private int position;
    private OfdJson(String input) { this.input = input; }
    static Object parse(String text) {
        if (text == null || text.length() == 0 || text.length() > 262144) throw invalid();
        OfdJson reader = new OfdJson(text);
        Object value = reader.value(0);
        reader.space();
        if (reader.position != text.length()) throw invalid();
        return value;
    }
    private Object value(int depth) {
        if (depth > 16) throw invalid();
        space();
        if (position == input.length()) throw invalid();
        char first = input.charAt(position);
        if (first == '{') {
            position++;
            Map<String, Object> object = new LinkedHashMap<>();
            space();
            if (take('}')) return object;
            do {
                space();
                if (position == input.length() || input.charAt(position) != '"') throw invalid();
                String key = string();
                if (object.size() >= 256 || key.length() > 256 || object.containsKey(key)) throw invalid();
                space();
                if (!take(':')) throw invalid();
                object.put(key, value(depth + 1));
                space();
                if (take('}')) return object;
            } while (take(','));
            throw invalid();
        }
        if (first == '[') {
            position++;
            List<Object> array = new ArrayList<>();
            space();
            if (take(']')) return array;
            do {
                if (array.size() >= 500) throw invalid();
                array.add(value(depth + 1));
                space();
                if (take(']')) return array;
            } while (take(','));
            throw invalid();
        }
        if (first == '"') return string();
        if (first == 't') { literal("true"); return Boolean.TRUE; }
        if (first == 'f') { literal("false"); return Boolean.FALSE; }
        if (first == 'n') { literal("null"); return null; }
        int start = position;
        take('-');
        if (!take('0')) {
            if (position == input.length() || input.charAt(position) < '1' || input.charAt(position) > '9') throw invalid();
            digits();
        }
        if (take('.')) {
            int fraction = position; digits();
            if (position == fraction) throw invalid();
        }
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            int exponent = position; digits();
            if (position == exponent) throw invalid();
        }
        if (position - start > 64) throw invalid();
        return new NumberToken(input.substring(start, position));
    }
    private void digits() {
        while (position < input.length() && input.charAt(position) >= '0' && input.charAt(position) <= '9') position++;
    }
    private String string() {
        if (!take('"')) throw invalid();
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char next = input.charAt(position++);
            if (next == '"') {
                for (int i = 0; i < result.length(); i++) {
                    if (Character.isHighSurrogate(result.charAt(i))) {
                        if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i))) throw invalid();
                    } else if (Character.isLowSurrogate(result.charAt(i))) throw invalid();
                }
                return result.toString();
            }
            if (next < 0x20) throw invalid();
            if (next == '\\') {
                if (position == input.length()) throw invalid();
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"': case '\\': case '/': next = escape; break;
                    case 'b': next = '\b'; break;
                    case 'f': next = '\f'; break;
                    case 'n': next = '\n'; break;
                    case 'r': next = '\r'; break;
                    case 't': next = '\t'; break;
                    case 'u':
                        if (position + 4 > input.length()) throw invalid();
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            char digit = input.charAt(position++);
                            int value = Character.digit(digit, 16);
                            if (value < 0 || digit > 127) throw invalid();
                            code = (code << 4) + value;
                        }
                        next = (char) code; break;
                    default: throw invalid();
                }
            }
            if (result.length() >= 65536) throw invalid();
            result.append(next);
        }
        throw invalid();
    }
    private void literal(String value) {
        if (!input.startsWith(value, position)) throw invalid();
        position += value.length();
    }
    private boolean take(char value) {
        if (position < input.length() && input.charAt(position) == value) { position++; return true; }
        return false;
    }
    private void space() {
        while (position < input.length()) {
            char value = input.charAt(position);
            if (value != ' ' && value != '\r' && value != '\n' && value != '\t') return;
            position++;
        }
    }
    static ContractException invalid() {
        return new ContractException("OFD_INVALID_RESPONSE", null, "Не удалось проверить данные чека в ОФД.");
    }
}
