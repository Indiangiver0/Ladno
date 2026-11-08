package com.ladno.finance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.ladno.finance.core.BankMessageParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.storage.LedgerStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.security.KeyStore;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public final class NotificationRuntimeTest {
    private static final String CHANNEL = "ladno-qa-synthetic";

    /** Run separately from grant-dependent methods; no reflection, mock key or callback. */
    @Test public void keystoreParserStorage() throws Throwable {
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("notification-native")) {
            LedgerStore store = LedgerStore.get(f.context);
            NativeRuntimeFixtures.setup(store);
            JSONArray original = NativeRuntimeFixtures.selected(store);
            String provider = f.context.getPackageName();
            NativeRuntimeFixtures.select(store, NativeRuntimeFixtures.withPackage(original, provider));
            try {
                SourceTokens tokens = new SourceTokens(f.context);
                f.truth("AndroidKeyStore available", tokens.isAvailable());
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore"); keyStore.load(null);
                f.truth("production key alias exists", keyStore.containsAlias("ladno.capture.hmac.v1"));
                f.equal("key material not exportable", null, keyStore.getKey("ladno.capture.hmac.v1", null).getEncoded());
                String hmac = tokens.hmac("qa-purpose", "qa-namespace", "a", "bc");
                f.truth("HMAC hex64", hmac.matches("[0-9a-f]{64}"));
                f.equal("same input stable across SourceTokens objects", hmac, new SourceTokens(f.context).hmac("qa-purpose", "qa-namespace", "a", "bc"));
                f.truth("purpose scoped", !hmac.equals(tokens.hmac("different-purpose", "qa-namespace", "a", "bc")));
                f.truth("namespace scoped", !hmac.equals(tokens.hmac("qa-purpose", "different-namespace", "a", "bc")));
                f.truth("field framing", !hmac.equals(tokens.hmac("qa-purpose", "qa-namespace", "ab", "c")));
                String marker = "qa-secret-" + UUID.randomUUID(), rawKey = marker + "-key", rawTag = marker + "-tag";
                String amount = NativeRuntimeFixtures.uniqueAmount("31", "11");
                String message = "Paid " + amount + " KZT 2026-11-02";
                long postTime = System.currentTimeMillis();
                tokens.listenerConnected();
                SourceTokens.NotificationTokens identity = tokens.notification(provider + "/qa-profile", rawKey, rawTag, postTime, message);
                f.truth("new lifecycle identity confident", !identity.uncertain);
                ImportCandidate parsed = BankMessageParser.parse(message, postTime, "Asia/Qyzylorda", true);
                f.equal("completed parser confidence", "high", parsed.confidence);
                f.equal("completed amount independent input", amount, parsed.amount);
                ImportCandidate safe = NativeRuntime.safeCandidate(parsed, identity.uncertain);
                f.equal("safe note empty", "", safe.note); f.equal("safe reference null", null, safe.paymentReference);
                int baseline = NativeRuntimeFixtures.records(store);
                JSONObject posted = store.ingestCandidate(NativeRuntimeFixtures.source(provider, identity, postTime), safe);
                f.equal("native complete auto-post", "posted", posted.getString("outcome"));
                JSONObject row = posted.getJSONObject("record");
                f.equal("native posted amount", amount, row.getString("amount"));
                f.truth("nullable category", row.isNull("categoryId"));
                f.equal("record delta", baseline + 1, NativeRuntimeFixtures.records(store));
                SourceTokens.NotificationTokens replay = tokens.notification(provider + "/qa-profile", rawKey, rawTag, postTime, message);
                f.equal("exact source key stable", identity.sourceKeyToken, replay.sourceKeyToken);
                f.equal("exact revision stable", identity.revisionToken, replay.revisionToken);
                JSONObject duplicate = store.ingestCandidate(NativeRuntimeFixtures.source(provider, replay, postTime), NativeRuntime.safeCandidate(parsed, replay.uncertain));
                f.equal("exact replay outcome", "duplicate", duplicate.getString("outcome"));
                f.equal("exact replay same candidate", posted.getJSONObject("candidate").getString("id"), duplicate.getJSONObject("candidate").getString("id"));
                f.equal("exact replay no second record", baseline + 1, NativeRuntimeFixtures.records(store));
                JSONObject changed = store.execute("saveTransaction", NativeRuntimeFixtures.json("id", row.getString("id"), "expectedRevision", row.getLong("revision"),
                    "kind", "expense", "date", NativeRuntimeFixtures.DATE, "amount", amount, "currency", "KZT", "categoryId", null, "note", "QA manual edit"));
                store.ingestCandidate(NativeRuntimeFixtures.source(provider, replay, postTime), safe);
                f.equal("replay preserves manual note", "QA manual edit", NativeRuntimeFixtures.record(store, amount, "expense").getString("note"));
                JSONObject changedRow = changed.getJSONObject("record");
                store.execute("deleteTransaction", NativeRuntimeFixtures.json("id", changedRow.getString("id"), "expectedRevision", changedRow.getLong("revision")));
                store.ingestCandidate(NativeRuntimeFixtures.source(provider, replay, postTime), safe);
                f.equal("tombstone replay not resurrected", baseline, NativeRuntimeFixtures.records(store));
                String[] incomplete = {"Оплата " + amount + " KZT 2026-11-02 не произведена", "Запланирована покупка " + amount + " KZT 2026-11-02", "Покупка: сумма скрыта 2026-11-02"};
                for (int i = 0; i < incomplete.length; i++) {
                    ImportCandidate input = BankMessageParser.parse(incomplete[i], postTime, "Asia/Qyzylorda", true);
                    f.truth("non-final or hidden parser review " + i, !"high".equals(input.confidence));
                    SourceTokens.NotificationTokens identity2 = tokens.notification(provider + "/qa-profile", rawKey + i, rawTag, postTime + i + 1, incomplete[i]);
                    JSONObject result = store.ingestCandidate(NativeRuntimeFixtures.source(provider, identity2, postTime + i + 1), NativeRuntime.safeCandidate(input, identity2.uncertain));
                    f.equal("non-final or hidden not auto-post " + i, "review", result.getString("outcome"));
                }
                tokens.captureGap();
                SourceTokens.NotificationTokens afterGap = tokens.notification(provider + "/qa-profile", rawKey, rawTag, postTime + 100, message);
                f.truth("gap on existing lifecycle uncertain", afterGap.uncertain);
                JSONObject gap = store.ingestCandidate(NativeRuntimeFixtures.source(provider, afterGap, postTime + 100), NativeRuntime.safeCandidate(parsed, afterGap.uncertain));
                f.equal("new revision after gap review", "review", gap.getString("outcome"));
                f.truth("gap reason persisted", NativeRuntimeFixtures.has(gap.getJSONObject("candidate").getJSONArray("reasons"), "IDENTITY_UNCERTAIN"));
                f.equal("raw markers absent in database cells", false, NativeRuntimeFixtures.databaseContains(f.context, marker));
                f.equal("raw markers absent in lifecycle preferences", false, f.context.getSharedPreferences("capture_lifecycles", Context.MODE_PRIVATE).getAll().toString().contains(marker));
                f.observe("final structured state", store.state(NativeRuntimeFixtures.MONTH, "KZT"));
            } catch (Throwable error) { f.failure(error); throw error; }
            finally { NativeRuntimeFixtures.select(store, original); }
        }
    }

    /** Root must first grant real NLS access on only Ladno_QA_API30. */
    @Test public void osGrantedDelivery() throws Throwable {
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("notification-os-granted")) {
            NativeRuntime runtime = NativeRuntime.get(f.context);
            LedgerStore store = runtime.store;
            NativeRuntimeFixtures.setup(store);
            f.truth("actual OS NLS grant", runtime.notificationAccess());
            NotificationManager manager = f.context.getSystemService(NotificationManager.class);
            createChannel(manager);
            JSONArray original = NativeRuntimeFixtures.selected(store);
            String tag = "qa-nls-" + UUID.randomUUID();
            try {
                NativeRuntimeFixtures.select(store, new JSONArray());
                int unselectedRecords = NativeRuntimeFixtures.records(store), unselectedReviews = NativeRuntimeFixtures.reviews(store);
                publish(manager, tag, 1, "Paid " + NativeRuntimeFixtures.uniqueAmount("41", "13") + " KZT 2026-11-02");
                assertNoChanges(f, store, "unselected source", unselectedRecords, unselectedReviews);
                manager.cancel(tag, 1);
                NativeRuntimeFixtures.select(store, NativeRuntimeFixtures.withPackage(original, f.context.getPackageName()));
                int baseline = NativeRuntimeFixtures.records(store);
                String expense = NativeRuntimeFixtures.uniqueAmount("52", "19");
                String message = "Paid " + expense + " KZT 2026-11-02";
                publish(manager, tag, 2, message);
                f.await("actual OS expense delivery", 30000, () -> NativeRuntimeFixtures.record(store, expense, "expense") != null);
                JSONObject record = NativeRuntimeFixtures.record(store, expense, "expense");
                f.equal("OS expense source", "notification", record.getString("source"));
                f.equal("OS expense date", NativeRuntimeFixtures.DATE, record.getString("date"));
                f.truth("OS expense no category", record.isNull("categoryId"));
                f.equal("OS expense record delta", baseline + 1, NativeRuntimeFixtures.records(store));
                SystemClock.sleep(150);
                publish(manager, tag, 2, message);
                f.await("OS updated notification needs review", 30000, () -> {
                    JSONObject review = NativeRuntimeFixtures.review(store, expense, "IDENTITY_UNCERTAIN");
                    return review != null && NativeRuntimeFixtures.has(review.optJSONArray("possibleMatchIds"), record.getString("id"));
                });
                f.equal("OS repeat no second expense", baseline + 1, NativeRuntimeFixtures.records(store));
                f.observe("repeat semantics", "NotificationManager.notify updates system postTime; this is new-revision review, not a claimed exact callback replay.");
                String income = NativeRuntimeFixtures.uniqueAmount("62", "23");
                publish(manager, tag, 3, "Зачислено " + income + " KZT 2026-11-02");
                f.await("actual OS income delivery", 30000, () -> NativeRuntimeFixtures.record(store, income, "income") != null);
                f.equal("OS income and expense count", baseline + 2, NativeRuntimeFixtures.records(store));
                String negated = NativeRuntimeFixtures.uniqueAmount("72", "29");
                publish(manager, tag, 4, "Оплата " + negated + " KZT 2026-11-02 не произведена");
                f.await("actual OS negated operation review", 30000, () -> NativeRuntimeFixtures.review(store, negated, "UNSUPPORTED_EVENT") != null);
                f.equal("OS negated no expense", baseline + 2, NativeRuntimeFixtures.records(store));
                f.observe("actual structured state", runtime.state(NativeRuntimeFixtures.MONTH, "KZT"));
            } catch (Throwable error) { f.failure(error); throw error; }
            finally {
                for (int i = 1; i <= 4; i++) manager.cancel(tag, i);
                SystemClock.sleep(300);
                NativeRuntimeFixtures.select(store, original);
            }
        }
    }

    /** Separate invocation after root revokes the real listener access. */
    @Test public void osRevokedNoCapture() throws Throwable {
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("notification-os-revoked")) {
            NativeRuntime runtime = NativeRuntime.get(f.context);
            NativeRuntimeFixtures.setup(runtime.store);
            f.equal("actual OS access revoked", false, runtime.notificationAccess());
            JSONArray original = NativeRuntimeFixtures.selected(runtime.store);
            NotificationManager manager = f.context.getSystemService(NotificationManager.class); createChannel(manager);
            String tag = "qa-revoked-" + UUID.randomUUID();
            try {
                NativeRuntimeFixtures.select(runtime.store, NativeRuntimeFixtures.withPackage(original, f.context.getPackageName()));
                int records = NativeRuntimeFixtures.records(runtime.store), reviews = NativeRuntimeFixtures.reviews(runtime.store);
                publish(manager, tag, 1, "Paid " + NativeRuntimeFixtures.uniqueAmount("82", "31") + " KZT 2026-11-02");
                assertNoChanges(f, runtime.store, "revoked access", records, reviews);
            } catch (Throwable error) { f.failure(error); throw error; }
            finally { manager.cancel(tag, 1); NativeRuntimeFixtures.select(runtime.store, original); }
        }
    }

    private static void assertNoChanges(NativeRuntimeFixtures f, LedgerStore store, String id, int records, int reviews) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 3000;
        boolean unchanged = true;
        while (SystemClock.elapsedRealtime() < deadline) {
            unchanged &= records == NativeRuntimeFixtures.records(store) && reviews == NativeRuntimeFixtures.reviews(store);
            SystemClock.sleep(100);
        }
        f.truth(id + "/no records or pending imports during 3s", unchanged);
    }

    private static void createChannel(NotificationManager manager) {
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Ladno synthetic QA", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private static void publish(NotificationManager manager, String tag, int id, String text) {
        Context context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        Notification notification = new Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ladno synthetic QA").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true).setShowWhen(false).build();
        manager.notify(tag, id, notification);
    }
}
