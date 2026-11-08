package com.ladno.finance;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.ReceiptParser;
import com.ladno.finance.core.ReceiptOcrConsensus;
import com.ladno.finance.storage.LedgerStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Process-scoped jobs survive Activity recreation, while unfinished jobs never fake persistence. */
final class NativeRuntime {
    private static NativeRuntime instance;
    final Context context;
    final LedgerStore store;
    final SourceTokens tokens;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OcrEngine ocr;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(4), runnable -> new Thread(runnable, "ladno-ocr"));
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private WeakReference<MainActivity> host = new WeakReference<>(null);
    private String activeMedia;
    private String activeSettings;
    private File cameraOutput;
    private volatile JSONObject notificationError;

    static synchronized NativeRuntime get(Context context) {
        if (instance == null) instance = new NativeRuntime(context.getApplicationContext());
        return instance;
    }

    private NativeRuntime(Context context) {
        this.context = context;
        store = LedgerStore.get(context);
        tokens = new SourceTokens(context);
        ocr = new OcrEngine(context);
        cleanExpiredMedia();
        worker.execute(() -> {
            try { ocr.initialize(); } catch (ContractException | OutOfMemoryError ignored) { }
            refresh();
        });
        context.registerComponentCallbacks(new ComponentCallbacks2() {
            public void onConfigurationChanged(Configuration configuration) { }
            public void onLowMemory() { releaseOcrWhenIdle(); }
            public void onTrimMemory(int level) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) releaseOcrWhenIdle();
            }
        });
    }

    private void releaseOcrWhenIdle() {
        synchronized (this) { if (activeMedia != null) return; }
        if (worker.getQueue().isEmpty()) worker.execute(() -> { ocr.release(); refresh(); });
    }

    synchronized void attach(MainActivity activity) { host = new WeakReference<>(activity); }
    synchronized void detach(MainActivity activity) { if (host.get() == activity) host.clear(); }

    void refresh() {
        main.post(() -> {
            MainActivity activity;
            synchronized (NativeRuntime.this) { activity = host.get(); }
            if (activity != null) activity.refreshFromNative();
        });
    }

    JSONObject state(String month, String currency) {
        JSONObject state = store.state(month, currency);
        JSONArray sources = state.optJSONArray("sources");
        NativeJson.put(state, "permissions", NativeJson.object("notificationAccess", notificationAccess(),
            "cameraGranted", context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            "notificationError", tokens.isAvailable() ? notificationError
                : NativeJson.error("UNAVAILABLE", null, "Защищённая идентификация источника недоступна. Попробуйте позже.")));
        NativeJson.put(state, "packages", packageState(sources));
        NativeJson.put(state, "ocr", ocr.state());
        NativeJson.put(state, "jobs", jobState());
        NativeJson.put(state, "suggestedZoneId", ZoneId.systemDefault().getId());
        return state;
    }

    boolean notificationAccess() {
        ComponentName component = new ComponentName(context, BankNotificationService.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(component);
        }
        String value = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (value == null) return false;
        for (String item : value.split(":")) if (component.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }

    void requireSetup() {
        JSONObject settings = store.state(null, null).optJSONObject("settings");
        if (settings == null || !settings.optBoolean("initialized"))
            throw new ContractException("SETUP_REQUIRED", null, "Сначала выберите валюту и часовой пояс.");
    }

    void notificationError(JSONObject error) {
        notificationError = error;
        refresh();
    }

    private List<ResolveInfo> launcherApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        return context.getPackageManager().queryIntentActivities(intent, 0);
    }

    private JSONArray packageState(JSONArray sources) {
        Set<String> selected = new HashSet<>();
        if (sources != null) for (int i = 0; i < sources.length(); i++) selected.add(sources.optString(i));
        java.util.Map<String, String> labels = new java.util.TreeMap<>();
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo info : launcherApps()) {
            if (info.activityInfo == null || info.activityInfo.packageName.equals(context.getPackageName())) continue;
            String packageName = info.activityInfo.packageName;
            String label = info.loadLabel(pm).toString();
            // Package labels are display-only and never trusted as bank identity.
            labels.put(packageName, label.length() > 160 ? label.substring(0, 160) : label);
        }
        JSONArray result = new JSONArray();
        for (java.util.Map.Entry<String, String> entry : labels.entrySet())
            result.put(NativeJson.object("packageName", entry.getKey(), "label", entry.getValue(), "selected", selected.contains(entry.getKey())));
        return result;
    }

    void validatePackages(JSONArray selected) {
        Set<String> available = new HashSet<>();
        for (ResolveInfo info : launcherApps()) if (info.activityInfo != null && !context.getPackageName().equals(info.activityInfo.packageName))
            available.add(info.activityInfo.packageName);
        for (int i = 0; i < selected.length(); i++) if (!available.contains(selected.optString(i)))
            throw new ContractException("VALIDATION", "packages", "Выберите доступные приложения из списка.");
    }

    synchronized JSONObject queueAction(String type) {
        MainActivity activity = host.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            throw new ContractException("UNAVAILABLE", null, "Откройте приложение для этого действия.");
        boolean media = !type.equals("openNotificationAccess");
        if ((media && activeMedia != null) || (!media && activeSettings != null))
            throw new ContractException("BUSY", null, "Дождитесь завершения текущего действия.");
        trimJobs();
        Job job = new Job(type);
        jobs.put(job.id, job);
        if (media) activeMedia = job.id; else activeSettings = job.id;
        main.post(() -> {
            MainActivity current;
            synchronized (NativeRuntime.this) { current = host.get(); }
            if (current == null || current.isFinishing() || current.isDestroyed()) {
                finish(job.id, "failed", NativeJson.error("UNAVAILABLE", null, "Откройте приложение для этого действия."));
            } else {
                running(job.id);
                current.launchNativeAction(type, job.id);
            }
        });
        return NativeJson.object("jobId", job.id, "status", "queued");
    }

    private synchronized void trimJobs() {
        if (jobs.size() < 32) return;
        java.util.Iterator<Job> iterator = jobs.values().iterator();
        while (iterator.hasNext() && jobs.size() >= 32) {
            Job job = iterator.next();
            if (!job.status.equals("queued") && !job.status.equals("running")) iterator.remove();
        }
    }

    private synchronized JSONArray jobState() {
        JSONArray result = new JSONArray();
        for (Job job : jobs.values()) result.put(job.json());
        return result;
    }

    synchronized String mediaJob() { return activeMedia; }
    synchronized String settingsJob() { return activeSettings; }
    synchronized File cameraOutput() { return cameraOutput; }
    synchronized void cameraOutput(File file) { cameraOutput = file; }

    private synchronized void running(String id) {
        Job job = jobs.get(id);
        if (job != null) job.status = "running";
        refresh();
    }

    synchronized void finish(String id, String status, JSONObject error) {
        finish(id, status, error, null);
    }

    private synchronized void finish(String id, String status, JSONObject error, JSONObject outcome) {
        Job job = jobs.get(id);
        if (job == null || (!job.status.equals("queued") && !job.status.equals("running"))) return;
        job.status = status;
        job.error = error;
        job.outcome = status.equals("completed") ? outcome : null;
        if (id.equals(activeMedia)) {
            activeMedia = null;
            if (cameraOutput != null) deleteOwnedMedia(cameraOutput);
            cameraOutput = null;
        }
        if (id.equals(activeSettings)) activeSettings = null;
        refresh();
    }

    void failed(String id, String code, String message) { finish(id, "failed", NativeJson.error(code, null, message)); }

    void processMedia(String jobId, Uri selected, File camera) {
        worker.execute(() -> {
            File copied = null;
            try {
                requireSetup();
                File input;
                if (camera != null) input = camera;
                else { copied = copySelection(selected); input = copied; }
                String digest = digest(input);
                OcrEngine.Result recognized = ocr.recognize(input);
                ImportCandidate parsed = ReceiptParser.parse(recognized.text);
                ImportCandidate verification = recognized.verificationFailed ? null : ReceiptParser.parse(recognized.verificationText);
                parsed = ReceiptOcrConsensus.evaluate(parsed, recognized.confidence, verification,
                    recognized.verificationConfidence, recognized.verificationFailed);
                // Raw text is not retained by jobs, storage, logs, or JavaScript.
                recognized = null;
                String sourceKey = tokens.hmac("receipt-source", "receipt-image-v1", digest);
                String revision = tokens.hmac("receipt-revision", "receipt-image-v1", digest);
                boolean uncertain = tokens.isUncertain();
                ImportCandidate sanitized = safeCandidate(parsed, uncertain);
                JSONObject source = NativeJson.object("kind", "receipt", "eventId", UUID.randomUUID().toString(),
                    "sourceKeyToken", sourceKey, "revisionToken", revision, "accountSurrogate", null,
                    "paymentRefToken", null, "packageName", null, "label", "Чек", "capturedAt", Instant.now().toString(), "postTime", null);
                JSONObject committed = store.ingestCandidate(source, sanitized);
                finish(jobId, "completed", null, ScanOutcome.fromIngest(committed));
            } catch (ContractException e) { failed(jobId, e.code, e.getMessage()); }
            catch (SecurityException e) { failed(jobId, "PERMISSION_DENIED", "Нет доступа к выбранному изображению."); }
            catch (OutOfMemoryError e) { failed(jobId, "LIMIT_EXCEEDED", "Недостаточно памяти для изображения."); }
            catch (Exception | LinkageError e) { failed(jobId, "IMAGE_INVALID", "Не удалось обработать изображение."); }
            finally {
                if (copied != null) deleteOwnedMedia(copied);
                if (camera != null) deleteOwnedMedia(camera);
            }
        });
    }

    static ImportCandidate safeCandidate(ImportCandidate parsed, boolean uncertain) {
        JSONObject value = parsed.toJson();
        NativeJson.put(value, "paymentReference", JSONObject.NULL);
        // Generic capture never persists raw text as a note or guesses trusted payment/account identity.
        NativeJson.put(value, "note", "");
        if (uncertain) {
            value = reviewReason(ImportCandidate.fromJson(value), "IDENTITY_UNCERTAIN").toJson();
        }
        return ImportCandidate.fromJson(value);
    }

    private static ImportCandidate reviewReason(ImportCandidate candidate, String reason) {
        JSONObject value = candidate.toJson();
        JSONArray reasons = value.optJSONArray("reasons");
        if (reasons == null) reasons = new JSONArray();
        boolean present = false;
        for (int i = 0; i < reasons.length(); i++) present |= reason.equals(reasons.optString(i));
        if (!present) reasons.put(reason);
        NativeJson.put(value, "reasons", reasons);
        if ("high".equals(value.optString("confidence"))) NativeJson.put(value, "confidence", "uncertain");
        return ImportCandidate.fromJson(value);
    }

    static ImportCandidate unavailableCandidate(String reason) {
        return ImportCandidate.fromJson(NativeJson.object("kind", null, "amount", null, "currency", null,
            "date", null, "dateOrigin", null, "note", "", "reasons", new JSONArray().put(reason),
            "confidence", "unavailable", "paymentReference", null));
    }

    File newCameraFile() throws java.io.IOException {
        File directory = mediaDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException();
        File file = File.createTempFile("capture-", ".jpg", directory);
        // Retention is bounded even if an external camera never returns to the Activity.
        main.postDelayed(() -> {
            deleteOwnedMedia(file);
            String id;
            synchronized (NativeRuntime.this) { id = file.equals(cameraOutput) ? activeMedia : null; }
            if (id != null) failed(id, "UNAVAILABLE", "Время ожидания изображения истекло.");
        }, TimeUnit.HOURS.toMillis(24));
        return file;
    }

    private File copySelection(Uri uri) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new ContractException("IMAGE_INVALID", null, "Выберите изображение через системный выбор файла.");
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || !mime.startsWith("image/"))
            throw new ContractException("IMAGE_INVALID", null, "Выбранный файл не является изображением.");
        File file = newCameraFile();
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(file)) {
                if (input == null) throw new java.io.IOException();
                copyBounded(input, output);
            }
            return file;
        } catch (Exception e) { deleteOwnedMedia(file); throw e; }
    }

    private void copyBounded(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[32768];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > OcrEngine.MAX_BYTES) throw new ContractException("LIMIT_EXCEEDED", null, "Файл превышает 20 МиБ.");
            output.write(buffer, 0, read);
        }
        if (count == 0) throw new ContractException("IMAGE_INVALID", null, "Выбран пустой файл.");
    }

    private String digest(File file) throws Exception {
        if (file.length() > OcrEngine.MAX_BYTES) throw new ContractException("LIMIT_EXCEEDED", null, "Файл превышает 20 МиБ.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[32768];
            int read;
            long count = 0;
            while ((read = input.read(bytes)) != -1) {
                count += read;
                if (count > OcrEngine.MAX_BYTES) throw new ContractException("LIMIT_EXCEEDED", null, "Файл превышает 20 МиБ.");
                digest.update(bytes, 0, read);
            }
        }
        return SourceTokens.hex(digest.digest());
    }

    private File mediaDirectory() { return new File(context.getCacheDir(), "capture"); }

    private void cleanExpiredMedia() {
        File[] files = mediaDirectory().listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        for (File file : files) if (file.isFile() && file.lastModified() <= cutoff) deleteOwnedMedia(file);
    }

    void deleteOwnedMedia(File file) {
        try {
            File directory = mediaDirectory().getCanonicalFile();
            File absolute = file.getCanonicalFile();
            if (directory.equals(absolute.getParentFile()) && absolute.getName().startsWith("capture-")) absolute.delete();
        } catch (java.io.IOException ignored) { }
    }

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final String type;
        String status = "queued";
        JSONObject error;
        JSONObject outcome;
        Job(String type) { this.type = type; }
        JSONObject json() { return NativeJson.object("jobId", id, "type", type, "status", status, "error", error, "outcome", outcome); }
    }
}
