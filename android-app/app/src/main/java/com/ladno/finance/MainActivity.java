package com.ladno.finance;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Android shell for private storage, capture flows, and a strictly local trusted UI. */
public final class MainActivity extends Activity {
    static final String PAGE = LocalAssets.PAGE;
    private static final String REFRESH = "window.NativeEvents&&window.NativeEvents.onRefresh()";
    private static final int CAMERA = 41;
    private static final int GALLERY = 42;
    private static final int NOTIFICATION_ACCESS = 43;
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
        + "font-src 'self'; connect-src 'none'; frame-src 'none'; child-src 'none'; object-src 'none'; "
        + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private NativeRuntime runtime;
    private WebView web;
    private boolean pageReady;
    private Uri cameraGrant;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.rgb(246, 245, 250));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(246, 245, 250));
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        container.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(container);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), container);
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(container);
        runtime = NativeRuntime.get(this);
        runtime.attach(this);
        if (savedInstanceState != null) {
            File pending = runtime.cameraOutput();
            if (pending != null) cameraGrant = FileProvider.getUriForFile(this, getPackageName() + ".capture", pending);
        }
        configureWebView();
        web.addJavascriptInterface(new AppBridge(runtime), "AndroidApp");
        // The only native loadUrl call is this fixed URL. No Intents/deep links supply a URL.
        web.loadUrl(PAGE);
    }

    @SuppressWarnings("deprecation")
    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setBlockNetworkImage(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setSaveFormData(false);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSafeBrowsingEnabled(true);
        android.webkit.CookieManager.getInstance().setAcceptCookie(false);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setDownloadListener((url, agent, disposition, mime, length) -> { });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message message) { return false; }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams parameters) {
                callback.onReceiveValue(null);
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localResource(request.getUrl(), request.getMethod());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || !"GET".equals(request.getMethod()) || !PAGE.equals(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !PAGE.equals(url); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageReady = false;
                if (!PAGE.equals(url)) view.stopLoading();
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = PAGE.equals(url);
                if (pageReady) refreshFromNative();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.cancel(); }
        });
    }

    private boolean allowed(Uri uri, String method) {
        if (uri == null || !"GET".equals(method) || !"https".equals(uri.getScheme())
            || !"appassets.androidplatform.net".equals(uri.getHost()) || uri.getPort() != -1
            || uri.getUserInfo() != null || uri.getEncodedQuery() != null || uri.getEncodedFragment() != null) return false;
        return LocalAssets.path(uri.toString(), method) != null;
    }

    private WebResourceResponse localResource(Uri uri, String method) {
        if (!allowed(uri, method)) return denied();
        String path = uri.getEncodedPath();
        String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".css") ? "text/css" : "application/javascript";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Security-Policy", CSP);
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("X-Frame-Options", "DENY");
        headers.put("Cache-Control", "no-store");
        headers.put("Referrer-Policy", "no-referrer");
        try {
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open(path.substring(1)));
        } catch (IOException e) { return denied(); }
    }

    private WebResourceResponse denied() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
            java.util.Collections.singletonMap("Cache-Control", "no-store"),
            new ByteArrayInputStream(new byte[]{'D', 'e', 'n', 'i', 'e', 'd'}));
    }

    void refreshFromNative() {
        if (pageReady && web != null && !isDestroyed()) web.evaluateJavascript(REFRESH, null);
    }

    void launchNativeAction(String type, String jobId) {
        try {
            if (type.equals("scanGallery")) {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(picker, GALLERY);
            } else if (type.equals("scanCamera")) {
                File output = runtime.newCameraFile();
                runtime.cameraOutput(output);
                cameraGrant = FileProvider.getUriForFile(this, getPackageName() + ".capture", output);
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, cameraGrant)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                camera.setClipData(ClipData.newRawUri("Receipt", cameraGrant));
                startActivityForResult(camera, CAMERA);
            } else {
                startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), NOTIFICATION_ACCESS);
            }
        } catch (ActivityNotFoundException e) {
            revokeCameraGrant();
            runtime.failed(jobId, "ACTIVITY_UNAVAILABLE", "Системное приложение для этого действия недоступно.");
        } catch (SecurityException e) {
            revokeCameraGrant();
            runtime.failed(jobId, "PERMISSION_DENIED", "Система не разрешила это действие.");
        } catch (Exception e) {
            revokeCameraGrant();
            runtime.failed(jobId, "UNAVAILABLE", "Не удалось открыть системное приложение.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == NOTIFICATION_ACCESS) {
            String id = runtime.settingsJob();
            if (id != null) runtime.finish(id, "completed", null);
            runtime.refresh(); // Read actual OS access; returning from settings never grants access by assumption.
            return;
        }
        if (request != CAMERA && request != GALLERY) return;
        String jobId = runtime.mediaJob();
        if (jobId == null) { revokeCameraGrant(); return; }
        if (result != RESULT_OK) {
            revokeCameraGrant();
            runtime.finish(jobId, "cancelled", null);
            return;
        }
        if (request == CAMERA) {
            revokeCameraGrant();
            File file = runtime.cameraOutput();
            if (file == null) runtime.failed(jobId, "IMAGE_INVALID", "Изображение камеры недоступно.");
            else runtime.processMedia(jobId, null, file);
        } else {
            Uri selected = data == null ? null : data.getData();
            if (selected == null) runtime.failed(jobId, "IMAGE_INVALID", "Изображение не выбрано.");
            else runtime.processMedia(jobId, selected, null);
        }
    }

    private void revokeCameraGrant() {
        if (cameraGrant != null) {
            revokeUriPermission(cameraGrant, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraGrant = null;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (runtime != null) {
            runtime.attach(this);
            runtime.refresh();
        }
    }

    @Override protected void onDestroy() {
        if (runtime != null) runtime.detach(this);
        if (web != null) {
            web.removeJavascriptInterface("AndroidApp");
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
