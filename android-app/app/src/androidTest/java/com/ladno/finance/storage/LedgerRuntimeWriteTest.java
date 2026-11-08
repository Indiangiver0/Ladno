package com.ladno.finance.storage;

import android.os.Process;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ladno.finance.core.ImportCandidate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static com.ladno.finance.storage.LedgerRuntimeFixtures.*;

/** Run on a fresh dedicated emulator app; root then force-stops before ReadTest. */
@RunWith(AndroidJUnit4.class)
public final class LedgerRuntimeWriteTest {
    @Test public void writeAndExerciseNativeLedger() throws Exception {
        LedgerRuntimeFixtures f=new LedgerRuntimeFixtures("write"); boolean completed=false;
        try {
            JSONObject fresh=f.store.state("2026-10","KZT");
            f.check("fresh/setup",false,fresh.getJSONObject("settings").getBoolean("initialized"));
            f.check("fresh/records",0,fresh.getJSONArray("records").length());
            f.check("fresh/categories",0,fresh.getJSONArray("categories").length());
            f.check("fresh/imports",0,fresh.getJSONArray("imports").length());
            f.check("fresh/currency",true,fresh.getJSONObject("selection").isNull("currency"));
            f.rejects("setup/required","SETUP_REQUIRED",()->f.save("expense","2026-10-01","1.00","KZT",null,""));
            f.store.execute("settings",object("primaryCurrency","KZT","zoneId","Asia/Qyzylorda","datePolicy","POST_TIME_IN_ZONE"));
            f.summary("setup/zero","2026-10","KZT","0.00","0.00","0.00",0);
            JSONObject category=f.store.execute("saveCategory",object("id",null,"expectedRevision",null,"name","Продукты QA","kind","expense")).getJSONObject("category");
            JSONObject incomeCategory=f.store.execute("saveCategory",object("id",null,"expectedRevision",null,"name","Зарплата QA","kind","income")).getJSONObject("category");
            String cat=category.getString("id"), incomeCat=incomeCategory.getString("id");
            JSONObject incomeA=f.save("income","2026-10-31","0.10","KZT",null,""), incomeB=f.save("income","2026-10-31","0,2","KZT",incomeCat,"");
            JSONObject expenseA=f.save("expense","2026-10-01","1.20","KZT",null,""), expenseB=f.save("expense","2026-10-31","2.30","KZT",cat,"");
            String a=expenseA.getString("id"), b=expenseB.getString("id");
            f.check("manual/canonical","0.20",incomeB.getString("amount"));
            f.check("manual/nullCategory",true,expenseA.isNull("categoryId"));
            f.summary("manual/exact","2026-10","KZT","0.30","3.50","-3.20",4);
            f.save("expense","2026-10-31","7.89","USD",null,"");
            f.summary("currency/USD","2026-10","USD","0.00","7.89","-7.89",1);
            f.summary("currency/KZTSeparate","2026-10","KZT","0.30","3.50","-3.20",4);
            for(String invalid:new String[]{"0","-1","+1","01.00","1e2","1.200","1000000000000000000000000.00"}) {
                f.rejects("invalid/amount/"+invalid,"VALIDATION",()->f.save("expense","2026-10-01",invalid,"KZT",null,""));
            }
            f.rejects("invalid/date","VALIDATION",()->f.save("expense","2026-02-29","1.00","KZT",null,""));
            f.rejects("invalid/categoryKind","REFERENCE_CONFLICT",()->f.save("expense","2026-10-01","1.00","KZT",incomeCat,""));
            f.summary("invalid/noPartialWrites","2026-10","KZT","0.30","3.50","-3.20",4);
            JSONObject assigned=f.store.execute("saveTransaction",transaction(a,1L,"expense","2026-10-01","1.20","KZT",cat,"")).getJSONObject("record");
            f.check("category/assignSameID",a,assigned.getString("id")); f.check("category/assignRevision",2L,assigned.getLong("revision"));
            f.rejects("cas/staleTransaction","CONFLICT",()->f.store.execute("saveTransaction",transaction(a,1L,"expense","2026-10-01","999.99","KZT",cat,"")));
            f.check("cas/unchangedAmount","1.20",f.record("2026-10","KZT",a).getString("amount"));
            f.store.execute("saveCategory",object("id",cat,"expectedRevision",1L,"name","Еда QA","kind","expense"));
            f.rejects("category/kindInUse","REFERENCE_CONFLICT",()->f.store.execute("saveCategory",object("id",cat,"expectedRevision",2L,"name","Еда QA","kind","income")));
            f.store.execute("deleteCategory",object("id",cat,"expectedRevision",2L));
            f.check("category/deleteKeepsA",true,f.record("2026-10","KZT",a).isNull("categoryId"));
            f.check("category/deleteKeepsB",true,f.record("2026-10","KZT",b).isNull("categoryId"));
            f.check("category/deleteBRevision",2L,f.record("2026-10","KZT",b).getLong("revision"));
            f.summary("category/noDoubleCount","2026-10","KZT","0.30","3.50","-3.20",4);
            f.store.execute("saveTransaction",transaction(a,3L,"expense","2026-11-01","1.20","KZT",null,"moved QA"));
            f.summary("dateEdit/October","2026-10","KZT","0.30","2.30","-2.00",3);
            f.summary("dateEdit/November","2026-11","KZT","0.00","1.20","-1.20",1);
            f.rejects("dateEdit/invalidAtomic","VALIDATION",()->f.store.execute("saveTransaction",transaction(a,4L,"expense","2026-11-31","8.00","KZT",null,"")));
            f.check("dateEdit/retainedDate","2026-11-01",f.record("2026-11","KZT",a).getString("date"));
            f.check("dateEdit/retainedRevision",4L,f.record("2026-11","KZT",a).getLong("revision"));
            f.save("income","2026-09-30","9007199254740993.01","KZT",null,""); f.save("income","2026-09-30","0.02","KZT",null,"");
            f.save("expense","2026-09-30","9007199254740992.99","KZT",null,"");
            f.summary("precision/above2pow53","2026-09","KZT","9007199254740993.03","9007199254740992.99","0.04",3);
            f.save("income","2026-08-31","999999999999999999999999.99","KZT",null,"");
            f.save("income","2026-08-31","999999999999999999999999.99","KZT",null,"");
            f.summary("precision/aggregate25Digits","2026-08","KZT","1999999999999999999999999.98","0.00","1999999999999999999999999.98",2);
            f.save("expense","9999-12-31","0.01","KZT",null,""); f.summary("date/terminalYear","9999-12","KZT","0.00","0.01","-0.01",1);
            f.save("expense","2026-07-31","7","JPY",null,""); f.save("income","2026-07-31","1,2","KWD",null,"");
            f.rejects("scale/JPYFraction","VALIDATION",()->f.save("expense","2026-07-31","1.0","JPY",null,""));
            f.summary("scale/JPY","2026-07","JPY","0","7","-7",1); f.summary("scale/KWD","2026-07","KWD","1.200","0.000","1.200",1);
            f.store.execute("saveSources",object("packages",new JSONArray().put(BANK)));
            JSONObject original=source("notification","first-bank","first-revision","accountA","referenceA");
            ImportCandidate originalCandidate=candidate("12.34","2026-06-02");
            JSONObject first=f.store.ingestCandidate(original,originalCandidate);
            f.check("ingest/autoOutcome","posted",first.getString("outcome"));
            String postedId=first.getJSONObject("record").getString("id");
            f.check("ingest/autoSource","notification",first.getJSONObject("record").getString("source"));
            JSONObject strong=source("receipt","linked-receipt","linked-revision","accountA","referenceA");
            JSONObject linked=f.store.ingestCandidate(strong,originalCandidate);
            f.check("identity/strongOutcome","linked",linked.getString("outcome"));
            f.check("identity/strongSameID",postedId,linked.getJSONObject("record").getString("id"));
            f.summary("identity/noDuplicate","2026-06","KZT","0.00","12.34","-12.34",1);
            f.store.execute("saveTransaction",transaction(postedId,1L,"expense","2026-06-02","13.34","KZT",null,"manual correction QA"));
            JSONObject replay=f.store.ingestCandidate(original,originalCandidate);
            f.check("replay/outcome","duplicate",replay.getString("outcome"));
            f.check("replay/preservesEdit","13.34",replay.getJSONObject("record").getString("amount"));
            f.check("replay/preservesID",postedId,replay.getJSONObject("record").getString("id"));
            JSONObject revision=f.store.ingestCandidate(source("notification","first-bank","updated-revision",null,null),candidate("13.34","2026-06-02"));
            f.check("revision/review","review",revision.getString("outcome"));
            JSONObject revisionCandidate=revision.getJSONObject("candidate");
            f.check("revision/match",true,contains(revisionCandidate.getJSONArray("possibleMatchIds"),postedId));
            JSONObject weak=f.store.ingestCandidate(source("receipt","weak-link","weak-link-revision",null,null),candidate("13.34","2026-06-02"));
            f.check("weak/review","review",weak.getString("outcome")); JSONObject weakCandidate=weak.getJSONObject("candidate");
            f.rejects("weak/distinctNeedsConfirmation","REFERENCE_CONFLICT",()->f.store.execute("reviewCandidate",object("id",weakCandidate.getString("id"),"expectedRevision",1L,"decision","post","transaction",reviewTransaction("expense","2026-06-02","13.34","KZT"),"confirmDistinct",false)));
            JSONObject weakLinked=f.store.execute("reviewCandidate",object("id",weakCandidate.getString("id"),"expectedRevision",1L,"decision","link","transactionId",postedId));
            f.check("review/linkStatus","linked",weakLinked.getJSONObject("candidate").getString("status"));
            f.rejects("review/staleResolved","CONFLICT",()->f.store.execute("reviewCandidate",object("id",weakCandidate.getString("id"),"expectedRevision",1L,"decision","ignore")));
            JSONObject distinct=f.store.ingestCandidate(source("receipt","weak-distinct","weak-distinct-revision",null,null),candidate("13.34","2026-06-02"));
            JSONObject distinctResult=f.store.execute("reviewCandidate",object("id",distinct.getJSONObject("candidate").getString("id"),"expectedRevision",1L,"decision","post","transaction",reviewTransaction("expense","2026-06-02","13.34","KZT"),"confirmDistinct",true));
            f.check("review/distinctStatus","posted",distinctResult.getJSONObject("candidate").getString("status"));
            f.check("review/distinctNewID",false,postedId.equals(distinctResult.getJSONObject("record").getString("id")));
            JSONObject ignoredSource=source("receipt","ignore","ignore-revision",null,null);
            JSONObject toIgnore=f.store.ingestCandidate(ignoredSource,uncertain("9.99","2026-06-15"));
            JSONObject ignored=f.store.execute("reviewCandidate",object("id",toIgnore.getJSONObject("candidate").getString("id"),"expectedRevision",1L,"decision","ignore"));
            f.check("review/ignoreStatus","ignored",ignored.getJSONObject("candidate").getString("status"));
            f.check("review/ignoreNoRecord",true,ignored.isNull("record"));
            JSONObject explicit=f.store.ingestCandidate(source("receipt","explicit-post","explicit-post-revision",null,null),uncertain("7.89","2026-06-10"));
            JSONObject explicitPost=f.store.execute("reviewCandidate",object("id",explicit.getJSONObject("candidate").getString("id"),"expectedRevision",1L,"decision","post","transaction",reviewTransaction("expense","2026-06-10","7.89","KZT"),"confirmDistinct",false));
            f.check("review/postStatus","posted",explicitPost.getJSONObject("candidate").getString("status"));
            JSONObject noIdentity=f.store.ingestCandidate(source("receipt",null,"no-identity-revision",null,null),candidate("6.66","2026-06-20"));
            f.check("identity/uncertainReview","review",noIdentity.getString("outcome"));
            f.check("identity/uncertainReason",true,contains(noIdentity.getJSONObject("candidate").getJSONArray("reasons"),"IDENTITY_UNCERTAIN"));
            f.summary("review/unpostedExcluded","2026-06","KZT","0.00","34.57","-34.57",3);
            int pendingBefore=f.store.state("2026-06","KZT").getJSONArray("imports").length();
            JSONObject unselected=source("notification","unselected","unselected-revision",null,null); unselected.put("packageName","com.ladno.qa.unselected");
            f.rejects("source/notSelected","VALIDATION",()->f.store.ingestCandidate(unselected,candidate("1.00","2026-06-25")));
            f.check("source/noPartialImport",pendingBefore,f.store.state("2026-06","KZT").getJSONArray("imports").length());
            f.store.execute("deleteTransaction",object("id",postedId,"expectedRevision",2L));
            JSONObject deletedReplay=f.store.ingestCandidate(original,originalCandidate);
            f.check("tombstone/replayDuplicate","duplicate",deletedReplay.getString("outcome"));
            f.check("tombstone/noResurrection",true,deletedReplay.isNull("record"));
            f.summary("tombstone/excluded","2026-06","KZT","0.00","21.23","-21.23",2);
            f.check("tombstone/noVisibleID",null,f.record("2026-06","KZT",postedId));
            JSONArray expectations=new JSONArray();
            expectations.put(object("month","2026-10","currency","KZT","income","0.30","expense","2.30","net","-2.00","count",3));
            expectations.put(object("month","2026-10","currency","USD","income","0.00","expense","7.89","net","-7.89","count",1));
            expectations.put(object("month","2026-11","currency","KZT","income","0.00","expense","1.20","net","-1.20","count",1));
            expectations.put(object("month","2026-09","currency","KZT","income","9007199254740993.03","expense","9007199254740992.99","net","0.04","count",3));
            expectations.put(object("month","2026-08","currency","KZT","income","1999999999999999999999999.98","expense","0.00","net","1999999999999999999999999.98","count",2));
            expectations.put(object("month","2026-07","currency","JPY","income","0","expense","7","net","-7","count",1));
            expectations.put(object("month","2026-07","currency","KWD","income","1.200","expense","0.000","net","1.200","count",1));
            expectations.put(object("month","9999-12","currency","KZT","income","0.00","expense","0.01","net","-0.01","count",1));
            expectations.put(object("month","2026-06","currency","KZT","income","0.00","expense","21.23","net","-21.23","count",2));
            f.writeJson(CONTROL,object("writePid",Process.myPid(),"summaries",expectations,"movedID",a,"categorizedID",b,"incomeCategoryID",incomeCat,
                "deletedCategoryID",cat,"deletedRecordID",postedId,"originalSource",original,"ignoredSource",ignoredSource,
                "pendingRevisionID",revisionCandidate.getString("id"),"pendingNoIdentityID",noIdentity.getJSONObject("candidate").getString("id"),
                "distinctID",distinctResult.getJSONObject("record").getString("id"),"explicitPostID",explicitPost.getJSONObject("record").getString("id")));
            completed=true;
        } finally { f.finish(completed); }
    }
}
