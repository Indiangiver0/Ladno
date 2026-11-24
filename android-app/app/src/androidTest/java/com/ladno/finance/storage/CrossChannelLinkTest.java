package com.ladno.finance.storage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ladno.finance.core.ContractException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static com.ladno.finance.storage.LedgerRuntimeFixtures.*;
import static org.junit.Assert.*;

/** A purchase seen as a bank push and then as a paper receipt is recorded once. Run on its own. */
@RunWith(AndroidJUnit4.class)
public final class CrossChannelLinkTest {
    @Test public void receiptLinksToEarlierPushInsteadOfDuplicating() throws Exception {
        LedgerRuntimeFixtures f = new LedgerRuntimeFixtures("cross-channel");
        try { f.store.execute("settings", object("primaryCurrency", "KZT", "zoneId", "Asia/Almaty", "datePolicy", "POST_TIME_IN_ZONE")); }
        catch (ContractException alreadyInitialized) { /* settings exist from an earlier run */ }
        f.store.execute("saveSources", object("packages", new JSONArray().put(BANK)));
        String run = Long.toString(System.nanoTime());
        String amount = "7" + (System.nanoTime() % 900000 + 100000) + ".55";

        JSONObject push = f.store.ingestCandidate(source("notification", "push-" + run, "push-rev-" + run, null, null),
            candidate(amount, "2031-03-05"));
        assertEquals("posted", push.getString("outcome"));
        String recordId = push.getJSONObject("record").getString("id");

        JSONObject receipt = f.store.ingestCandidate(source("receipt", "receipt-" + run, "receipt-rev-" + run, null, null),
            candidate(amount, "2031-03-06"));
        assertEquals("linked", receipt.getString("outcome"));
        assertEquals(recordId, receipt.getJSONObject("record").getString("id"));

        // A second receipt with the same total is a different purchase candidate: ask, never merge twice.
        JSONObject second = f.store.ingestCandidate(source("receipt", "receipt2-" + run, "receipt2-rev-" + run, null, null),
            candidate(amount, "2031-03-05"));
        assertEquals("review", second.getString("outcome"));

        int same = 0;
        JSONArray records = f.store.state("2031-03", "KZT").getJSONArray("records");
        for (int i = 0; i < records.length(); i++) if (amount.equals(records.getJSONObject(i).getString("amount"))) same++;
        assertEquals(1, same);
    }
}
