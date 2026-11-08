package com.ladno.finance.storage;

import android.os.Process;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static com.ladno.finance.storage.LedgerRuntimeFixtures.*;

/** Execute separately, after force-stop and without app-data clearing or intervening writes. */
@RunWith(AndroidJUnit4.class)
public final class LedgerRuntimeReadTest {
    @Test public void readNativeLedgerInAnotherProcess() throws Exception {
        LedgerRuntimeFixtures f=new LedgerRuntimeFixtures("read"); boolean completed=false;
        try {
            JSONObject control=f.readJson(CONTROL);
            f.check("restart/newPID",false,control.getInt("writePid")==Process.myPid());
            JSONObject settings=f.store.state("2026-10","KZT").getJSONObject("settings");
            f.check("restart/initialized",true,settings.getBoolean("initialized"));
            f.check("restart/currency","KZT",settings.getString("primaryCurrency"));
            f.check("restart/zone","Asia/Qyzylorda",settings.getString("zoneId"));
            JSONArray expected=control.getJSONArray("summaries");
            for(int i=0;i<expected.length();i++) {
                JSONObject sum=expected.getJSONObject(i);
                f.summary("restart/"+sum.getString("month")+"/"+sum.getString("currency"),sum.getString("month"),sum.getString("currency"),
                    sum.getString("income"),sum.getString("expense"),sum.getString("net"),sum.getInt("count"));
            }
            JSONObject moved=f.record("2026-11","KZT",control.getString("movedID"));
            f.check("restart/movedRevision",4L,moved.getLong("revision"));
            f.check("restart/movedAmount","1.20",moved.getString("amount"));
            f.check("restart/movedDate","2026-11-01",moved.getString("date"));
            f.check("restart/movedNote","moved QA",moved.getString("note"));
            f.check("restart/movedCategory",true,moved.isNull("categoryId"));
            f.check("restart/categoryDeletedRecordKept","2.30",f.record("2026-10","KZT",control.getString("categorizedID")).getString("amount"));
            JSONArray categories=f.store.state("2026-10","KZT").getJSONArray("categories");
            f.check("restart/categoryCount",1,categories.length());
            f.check("restart/categoryIdentity",control.getString("incomeCategoryID"),categories.getJSONObject(0).getString("id"));
            f.check("restart/categoryName","Зарплата QA",categories.getJSONObject(0).getString("name"));
            f.check("restart/selectedSources",1,f.store.state("2026-10","KZT").getJSONArray("sources").length());
            f.check("restart/selectedBank",BANK,f.store.state("2026-10","KZT").getJSONArray("sources").getString(0));
            f.check("restart/pendingRevision","needs_review",f.pending(control.getString("pendingRevisionID")).getString("status"));
            f.check("restart/pendingIdentity","needs_review",f.pending(control.getString("pendingNoIdentityID")).getString("status"));
            f.check("restart/pendingCount",2,f.store.state("2026-06","KZT").getJSONArray("imports").length());
            f.check("restart/distinctRecord","13.34",f.record("2026-06","KZT",control.getString("distinctID")).getString("amount"));
            f.check("restart/explicitPostRecord","7.89",f.record("2026-06","KZT",control.getString("explicitPostID")).getString("amount"));
            f.check("restart/tombstoneHidden",null,f.record("2026-06","KZT",control.getString("deletedRecordID")));
            JSONObject replay=f.store.ingestCandidate(control.getJSONObject("originalSource"),candidate("12.34","2026-06-02"));
            f.check("restart/exactReplayOutcome","duplicate",replay.getString("outcome"));
            f.check("restart/tombstoneNotResurrected",true,replay.isNull("record"));
            JSONObject ignoredReplay=f.store.ingestCandidate(control.getJSONObject("ignoredSource"),uncertain("9.99","2026-06-15"));
            f.check("restart/ignoredReplayOutcome","duplicate",ignoredReplay.getString("outcome"));
            f.check("restart/ignoredStatus","ignored",ignoredReplay.getJSONObject("candidate").getString("status"));
            f.check("restart/ignoredNoRecord",true,ignoredReplay.isNull("record"));
            f.summary("restart/afterReplays","2026-06","KZT","0.00","21.23","-21.23",2);
            completed=true;
        } finally { f.finish(completed); }
    }
}
