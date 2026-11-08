package com.ladno.finance;

import java.util.ArrayList;
import java.util.List;

/** Bounded, transient notification views. Never deduplicates monetary values or
 * repeated lines within a view: those may describe separate operations. */
final class NotificationText {
    static final int MAX_LENGTH = 65536;
    private NotificationText() { }

    static String assemble(CharSequence title, CharSequence text, CharSequence bigText, CharSequence[] lines) {
        return assemble(title, text, bigText, lines, null);
    }

    /** Fallback views (sub/summary/info text, messaging-style messages, ticker) are used
     * only when the primary views carry no digits, so they cannot duplicate an amount. */
    static String assemble(CharSequence title, CharSequence text, CharSequence bigText, CharSequence[] lines,
                           CharSequence[] fallback) {
        String primary = assemblePrimary(title, text, bigText, lines);
        if (fallback == null || primary.matches("(?s).*[0-9].*")) return primary;
        if (fallback.length > MAX_LENGTH) throw new LimitExceeded();
        StringBuilder result = new StringBuilder(primary);
        for (CharSequence view : fallback) {
            String value = normalized(view);
            if (!value.isEmpty() && !containsWholeLines(result.toString(), value)) append(result, value);
        }
        return result.toString();
    }

    private static String assemblePrimary(CharSequence title, CharSequence text, CharSequence bigText, CharSequence[] lines) {
        List<String> views = new ArrayList<>();
        addView(views, title);
        addView(views, bigText);
        addView(views, text);
        if (lines != null) {
            // Bounding even empty entries prevents an unbounded source array walk.
            if (lines.length > MAX_LENGTH) throw new LimitExceeded();
            StringBuilder block = new StringBuilder();
            for (CharSequence line : lines) append(block, normalized(line));
            addView(views, block);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < views.size(); i++) {
            String value = views.get(i);
            boolean redundant = false;
            for (int j = 0; j < views.size(); j++) {
                if (i == j) continue;
                String other = views.get(j);
                if (containsWholeLines(other, value) && (!other.equals(value) || j < i)) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) append(result, value);
        }
        return result.toString();
    }

    private static void addView(List<String> views, CharSequence source) {
        String value = normalized(source);
        if (!value.isEmpty()) views.add(value);
    }

    private static String normalized(CharSequence source) {
        if (source == null) return "";
        if (source.length() > MAX_LENGTH) throw new LimitExceeded();
        return source.toString().replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static boolean containsWholeLines(String outer, String inner) {
        return ("\n" + outer + "\n").contains("\n" + inner + "\n");
    }

    private static void append(StringBuilder output, String value) {
        if (value.isEmpty()) return;
        int separator = output.length() == 0 ? 0 : 1;
        if ((long) output.length() + value.length() + separator > MAX_LENGTH) throw new LimitExceeded();
        if (separator != 0) output.append('\n');
        output.append(value);
    }

    static final class LimitExceeded extends RuntimeException { }
}
