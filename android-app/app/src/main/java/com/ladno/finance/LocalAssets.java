package com.ladno.finance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exact string matching intentionally rejects URL equivalents, queries, encodings, and ports. */
final class LocalAssets {
    static final String ORIGIN = "https://appassets.androidplatform.net";
    static final String PAGE = ORIGIN + "/www/index.html";
    private static final Set<String> PATHS = new HashSet<>(Arrays.asList("/www/index.html", "/www/styles.css",
        "/www/view.js", "/www/bridge.js", "/www/app.js", "/www/preview-adapter.js"));

    static String path(String url, String method) {
        if (!"GET".equals(method) || url == null) return null;
        for (String path : PATHS) if ((ORIGIN + path).equals(url)) return path;
        return null;
    }

    private LocalAssets() { }
}
