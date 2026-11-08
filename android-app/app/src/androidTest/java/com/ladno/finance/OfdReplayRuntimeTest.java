package com.ladno.finance;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ladno.finance.core.FiscalQrParser;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.Money;
import com.ladno.finance.core.OfdReceiptParser;
import com.ladno.finance.storage.LedgerStore;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Author regression of the critical 0.3 observation lifecycle; synthetic native API only. */
@RunWith(AndroidJUnit4.class)
public final class OfdReplayRuntimeTest {
    @Test public void legacyQrDecisionsWinOverVerifiedApiAndMetadataChanges() throws Throwable {
        Assert.assertEquals("Ladno_QA_API30", InstrumentationRegistry.getArguments().getString("ladnoSyntheticAvd"));
        Assert.assertTrue("Synthetic emulator only", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        try (NativeRuntimeFixtures f = new NativeRuntimeFixtures("app017-ofd-replay")) {
            try {
                LedgerStore store = LedgerStore.get(f.context);
                NativeRuntimeFixtures.setup(store);
                SourceTokens tokens = new SourceTokens(f.context);
                String nonce = Long.toString(System.currentTimeMillis());
                String amount = "8" + nonce + ".01";
                int baseline = NativeRuntimeFixtures.records(store);

                FiscalQrParser.Result ignoredQr = qr(nonce + "1", amount);
                JSONObject ignoredFirst = store.ingestCandidate(source(tokens, ignoredQr), ignoredQr.candidate());
                JSONObject ignoredCandidate = ignoredFirst.getJSONObject("candidate");
                JSONObject ignored = store.execute("reviewCandidate", NativeRuntimeFixtures.json("id", ignoredCandidate.getString("id"),
                    "expectedRevision", ignoredCandidate.getLong("revision"), "decision", "ignore")).getJSONObject("candidate");
                JSONObject ignoredReplay = store.ingestCandidate(source(tokens, ignoredQr), verified(ignoredQr));
                f.equal("0.3 ignored replay outcome", "duplicate", ignoredReplay.getString("outcome"));
                f.equal("0.3 ignored same candidate", ignored.getString("id"), ignoredReplay.getJSONObject("candidate").getString("id"));
                f.equal("0.3 ignored status preserved", "ignored", ignoredReplay.getJSONObject("candidate").getString("status"));
                f.equal("0.3 ignored revision preserved", ignored.getLong("revision"), ignoredReplay.getJSONObject("candidate").getLong("revision"));
                f.equal("0.3 ignored no expense", baseline, NativeRuntimeFixtures.records(store));

                FiscalQrParser.Result postedQr = qr(nonce + "2", amount);
                JSONObject local = store.ingestCandidate(source(tokens, postedQr), postedQr.candidate()).getJSONObject("candidate");
                JSONObject posted = store.execute("reviewCandidate", NativeRuntimeFixtures.json("id", local.getString("id"),
                    "expectedRevision", local.getLong("revision"), "decision", "post", "confirmDistinct", true,
                    "transaction", transaction(postedQr.amount, "2026-11-03", "initial synthetic note")));
                JSONObject original = posted.getJSONObject("record");
                String editedAmount = Money.formatMinor(Money.parseCanonical(amount, "KZT", 2).minor.add(BigInteger.valueOf(17)), 2);
                JSONObject editInput = transaction(editedAmount, "2026-11-01", "retained synthetic manual edit");
                editInput.put("id", original.getString("id")).put("expectedRevision", original.getLong("revision"));
                JSONObject edited = store.execute("saveTransaction", editInput).getJSONObject("record");
                JSONObject postedReplay = store.ingestCandidate(source(tokens, postedQr), verified(postedQr));
                f.equal("posted after manual edit is duplicate", "duplicate", postedReplay.getString("outcome"));
                JSONObject kept = postedReplay.getJSONObject("record");
                f.equal("posted edited record ID retained", edited.getString("id"), kept.getString("id"));
                f.equal("posted edited amount retained", editedAmount, kept.getString("amount"));
                f.equal("posted edited date retained", "2026-11-01", kept.getString("date"));
                f.equal("posted edited note retained", "retained synthetic manual edit", kept.getString("note"));
                f.equal("posted edited revision retained", edited.getLong("revision"), kept.getLong("revision"));
                f.equal("posted replay no second expense", baseline + 1, NativeRuntimeFixtures.records(store));

                FiscalQrParser.Result pendingQr = qr(nonce + "3", amount);
                JSONObject pending = store.ingestCandidate(source(tokens, pendingQr),
                    OfdReceiptParser.fallback(pendingQr, "OFD_UNAVAILABLE")).getJSONObject("candidate");
                int pendingCount = NativeRuntimeFixtures.reviews(store);
                JSONObject pendingReplay = store.ingestCandidate(source(tokens, pendingQr), verified(pendingQr));
                f.equal("network fallback API replay same candidate", pending.getString("id"), pendingReplay.getJSONObject("candidate").getString("id"));
                f.equal("network fallback safe review retained", "needs_review", pendingReplay.getJSONObject("candidate").getString("status"));
                f.equal("network fallback no new review", pendingCount, NativeRuntimeFixtures.reviews(store));
                f.equal("network fallback no new expense", baseline + 1, NativeRuntimeFixtures.records(store));

                String newAmount = Money.formatMinor(Money.parseCanonical(amount, "KZT", 2).minor.add(BigInteger.valueOf(100)), 2);
                FiscalQrParser.Result firstQr = qr(nonce + "4", newAmount), otherQr = qr(nonce + "5", newAmount);
                JSONObject first = store.ingestCandidate(source(tokens, firstQr), verified(firstQr));
                f.equal("first new verified sale auto-posts", "posted", first.getString("outcome"));
                JSONObject repeat = store.ingestCandidate(source(tokens, firstQr), verified(firstQr));
                f.equal("same QR metadata-independent repeat", "duplicate", repeat.getString("outcome"));
                f.equal("same QR keeps record ID", first.getJSONObject("record").getString("id"), repeat.getJSONObject("record").getString("id"));
                JSONObject separate = store.ingestCandidate(source(tokens, otherQr), verified(otherQr));
                f.equal("different equal amount receipt not automatically merged", "review", separate.getString("outcome"));
                f.truth("different equal amount has own candidate", !separate.getJSONObject("candidate").getString("id").equals(first.getJSONObject("candidate").getString("id")));
                f.truth("different equal amount possible match requires decision", NativeRuntimeFixtures.has(separate.getJSONObject("candidate").getJSONArray("reasons"), "POSSIBLE_DUPLICATE"));
                f.equal("separate receipt no silent second expense", baseline + 2, NativeRuntimeFixtures.records(store));

                JSONObject manualInput = transaction(editedAmount, "2026-11-02", "retained synthetic link");
                manualInput.put("id", JSONObject.NULL).put("expectedRevision", JSONObject.NULL);
                JSONObject manual = store.execute("saveTransaction", manualInput).getJSONObject("record");
                FiscalQrParser.Result linkedQr = qr(nonce + "6", amount);
                JSONObject linkCandidate = store.ingestCandidate(source(tokens, linkedQr), linkedQr.candidate()).getJSONObject("candidate");
                store.execute("reviewCandidate", NativeRuntimeFixtures.json("id", linkCandidate.getString("id"),
                    "expectedRevision", linkCandidate.getLong("revision"), "decision", "link", "transactionId", manual.getString("id")));
                JSONObject linkedReplay = store.ingestCandidate(source(tokens, linkedQr), verified(linkedQr));
                f.equal("legacy explicit link decision retained", "linked", linkedReplay.getJSONObject("candidate").getString("status"));
                f.equal("legacy link target retained", manual.getString("id"), linkedReplay.getJSONObject("record").getString("id"));
                f.equal("legacy link no new expense", baseline + 3, NativeRuntimeFixtures.records(store));

                JSONObject firstRecord = first.getJSONObject("record");
                store.execute("deleteTransaction", NativeRuntimeFixtures.json("id", firstRecord.getString("id"),
                    "expectedRevision", firstRecord.getLong("revision")));
                JSONObject deletedReplay = store.ingestCandidate(source(tokens, firstQr), verified(firstQr));
                f.equal("deleted posted QR remains duplicate", "duplicate", deletedReplay.getString("outcome"));
                f.truth("deleted record is not resurrected", deletedReplay.isNull("record"));
                f.equal("deleted replay does not restore money", baseline + 2, NativeRuntimeFixtures.records(store));
                f.observe("scope", "Synthetic direct parser/storage regression only; no HTTP, original photos, Activity or bank data. Exact original QR source+revision tokens are identical before/after API.");
            } catch (Throwable error) { f.failure(error); throw error; }
        }
    }
    private static FiscalQrParser.Result qr(String sign, String amount) {
        return FiscalQrParser.parse("https://consumer.oofd.kz/?i=" + sign + "&f=000000123456&s=" + amount + "&t=20261103T201122");
    }
    private static JSONObject source(SourceTokens tokens, FiscalQrParser.Result qr) throws Exception {
        return NativeRuntimeFixtures.json("kind", "receipt", "eventId", UUID.randomUUID().toString(),
            "sourceKeyToken", tokens.hmac("receipt-source", "receipt-fiscal-qr-v1", qr.locator()),
            "revisionToken", tokens.hmac("receipt-revision", "receipt-fiscal-qr-v1", qr.locator(), qr.revision()),
            "accountSurrogate", null, "paymentRefToken", null, "packageName", null, "label", "Чек",
            "capturedAt", Instant.now().toString(), "postTime", null);
    }
    private static JSONObject transaction(String amount, String date, String note) throws Exception {
        return NativeRuntimeFixtures.json("kind", "expense", "date", date, "amount", amount, "currency", "KZT", "categoryId", null, "note", note);
    }
    private static ImportCandidate verified(FiscalQrParser.Result qr) {
        OfdReceiptParser.Request request = OfdReceiptParser.Request.from(qr);
        String body = "{\"kkmFnsId\":\"" + request.registerNumber + "\",\"ticket\":{\"type\":\"TICKET\",\"fiscalId\":\"" + request.fiscalSign
            + "\",\"offlineFiscalId\":null,\"cancelDate\":null,\"transactionDate\":\"2026-11-03T20:11:22.000\",\"totalSum\":" + qr.amount
            + ",\"operationType\":2,\"payments\":[{\"paymentType\":\"CARD\",\"sum\":" + qr.amount
            + "}],\"items\":[{\"itemType\":1,\"commodity\":{\"name\":\"Synthetic\"}}],\"acceptedAt\":null}}";
        return OfdReceiptParser.parse(body, qr);
    }
}
