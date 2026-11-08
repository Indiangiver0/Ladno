package com.ladno.finance;

import android.app.Notification;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.ladno.finance.core.BankMessageParser;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.ImportCandidate;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** No inbox, historical notifications, notification actions, or cross-profile capture. */
public final class BankNotificationService extends NotificationListenerService {
    private NativeRuntime runtime;
    // Initialize the shared monitor before constructing the executor/rejection callback.
    private final NotificationStatus notificationStatus = new NotificationStatus();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(32), runnable -> new Thread(runnable, "ladno-notifications"),
        (task, executor) -> {
            if (runtime != null) {
                notificationStatus.overflow(() -> {
                    runtime.tokens.captureGap();
                    runtime.notificationError(overflowError());
                });
            }
        });

    @Override public void onCreate() {
        super.onCreate();
        runtime = NativeRuntime.get(this);
    }

    @Override public void onListenerConnected() {
        runtime.tokens.listenerConnected();
        runtime.refresh();
    }

    @Override public void onListenerDisconnected() {
        runtime.tokens.listenerDisconnected();
        runtime.refresh();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !Process.myUserHandle().equals(sbn.getUser())) return;
        worker.execute(() -> capture(sbn));
    }

    private JSONObject selectedSettings(StatusBarNotification sbn) {
        // DB allowlist is read BEFORE obtaining Notification/extras/title/text.
        if (!Process.myUserHandle().equals(sbn.getUser())) return null;
        if (!runtime.notificationAccess()) return null;
        JSONObject state = runtime.store.state(null, null);
        JSONObject settings = state.optJSONObject("settings");
        if (settings == null || !settings.optBoolean("initialized")) return null;
        JSONArray sources = state.optJSONArray("sources");
        if (sources == null) return null;
        String packageName = sbn.getPackageName();
        for (int i = 0; i < sources.length(); i++) if (packageName.equals(sources.optString(i))) return settings;
        return null;
    }

    private void capture(StatusBarNotification sbn) {
        try {
            JSONObject settings = selectedSettings(sbn);
            if (settings == null) return;
            Notification notification = sbn.getNotification();
            if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            String text;
            ImportCandidate candidate;
            try {
                text = notificationText(notification);
                candidate = BankMessageParser.parse(text, sbn.getPostTime(), settings.optString("zoneId"),
                    "POST_TIME_IN_ZONE".equals(settings.optString("datePolicy")));
            } catch (NotificationText.LimitExceeded e) {
                text = "LIMIT_EXCEEDED";
                candidate = NativeRuntime.unavailableCandidate("LIMIT_EXCEEDED");
            }
            String namespace = namespace(sbn);
            SourceTokens.NotificationTokens tokens = runtime.tokens.notification(namespace, sbn.getKey(),
                sbn.getTag(), sbn.getPostTime(), text);
            text = null;
            candidate = NativeRuntime.safeCandidate(candidate, tokens.uncertain || profileSerial() < 0);
            JSONObject source = NativeJson.object("kind", "notification", "eventId", UUID.randomUUID().toString(),
                "sourceKeyToken", tokens.sourceKeyToken, "revisionToken", tokens.revisionToken,
                "accountSurrogate", null, "paymentRefToken", null, "packageName", sbn.getPackageName(),
                "label", "Уведомление", "capturedAt", Instant.now().toString(), "postTime", sbn.getPostTime());
            runtime.store.ingestCandidate(source, candidate);
            // A later successful event cannot erase evidence of an earlier dropped event.
            notificationStatus.success(overflowed -> runtime.notificationError(overflowed ? overflowError() : null));
        } catch (ContractException e) {
            runtime.tokens.captureGap();
            if ("VALIDATION".equals(e.code) && "source".equals(e.field)) return; // Deselected while this event was queued.
            runtime.notificationError(NativeJson.error(e.code, e.field, e.getMessage()));
        } catch (RuntimeException | OutOfMemoryError ignored) {
            // Never log source text, identifiers, parser input, or raw exception messages.
            // Storage commits are atomic; a failed capture cannot become a fake expense.
            runtime.tokens.captureGap();
            runtime.notificationError(NativeJson.error("UNAVAILABLE", null, "Не удалось обработать выбранное уведомление."));
        }
    }

    private String notificationText(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return NotificationText.assemble(null, notification.tickerText, null, null);
        java.util.List<CharSequence> fallback = new java.util.ArrayList<>();
        fallback.add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        fallback.add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        fallback.add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        fallback.add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        android.os.Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            // Only the newest message describes the event that just arrived.
            for (int i = messages.length - 1; i >= 0; i--) {
                if (messages[i] instanceof Bundle) {
                    CharSequence message = ((Bundle) messages[i]).getCharSequence("text");
                    if (message != null) { fallback.add(message); break; }
                }
            }
        }
        fallback.add(notification.tickerText);
        return NotificationText.assemble(extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES),
            fallback.toArray(new CharSequence[0]));
    }

    private JSONObject overflowError() {
        return NativeJson.error("LIMIT_EXCEEDED", null,
            "Очередь уведомлений переполнена. Часть событий не обработана; проверьте учёт вручную.");
    }

    private String namespace(StatusBarNotification sbn) {
        return sbn.getPackageName() + "/user-serial/" + profileSerial();
    }

    private long profileSerial() {
        try {
            UserManager users = getSystemService(UserManager.class);
            return users == null ? -1 : users.getSerialNumberForUser(Process.myUserHandle());
        } catch (RuntimeException e) { return -1; }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !Process.myUserHandle().equals(sbn.getUser())) return;
        worker.execute(() -> {
            try {
                if (selectedSettings(sbn) != null)
                    runtime.tokens.removed(namespace(sbn), sbn.getKey(), sbn.getTag());
            } catch (ContractException e) {
                runtime.tokens.captureGap();
                runtime.notificationError(NativeJson.error(e.code, e.field, e.getMessage()));
            } catch (RuntimeException ignored) { runtime.tokens.captureGap(); }
        });
        // Removal ends a capture lifecycle and never deletes a ledger transaction.
    }

    @Override public void onDestroy() {
        runtime.tokens.listenerDisconnected();
        worker.shutdown();
        super.onDestroy();
    }

}
