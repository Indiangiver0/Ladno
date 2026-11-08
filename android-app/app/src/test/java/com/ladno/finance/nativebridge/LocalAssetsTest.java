package com.ladno.finance;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalAssetsTest {
    @Test public void servesOnlyTheSixImmutableGetUrls() {
        for (String name : new String[]{"index.html", "styles.css", "view.js", "bridge.js", "app.js", "preview-adapter.js"})
            assertEquals("/www/" + name, LocalAssets.path(LocalAssets.ORIGIN + "/www/" + name, "GET"));
        String[] denied = {"http://appassets.androidplatform.net/www/index.html",
            "https://appassets.androidplatform.net:443/www/index.html",
            "https://user@appassets.androidplatform.net/www/index.html",
            "https://appassets.androidplatform.net.evil/www/index.html",
            "https://appassets.androidplatform.net/www/index.html?x=1",
            "https://appassets.androidplatform.net/www/index.html#frame",
            "https://appassets.androidplatform.net/www/%69ndex.html",
            "https://appassets.androidplatform.net/www/../www/index.html",
            "https://appassets.androidplatform.net/www/%2e%2e/db/schema.sql",
            "https://appassets.androidplatform.net/www/unknown.js", "file:///android_asset/www/index.html",
            "content://com.ladno.finance.capture/receipt_capture/capture.jpg", "data:text/html,test", "javascript:alert(1)", "blob:https://appassets.androidplatform.net/x", "intent://x"};
        for (String url : denied) assertNull(LocalAssets.path(url, "GET"));
        assertNull(LocalAssets.path(LocalAssets.PAGE, "POST"));
        assertNull(LocalAssets.path(LocalAssets.PAGE, "get"));
        assertNull(LocalAssets.path(null, "GET"));
    }
}
