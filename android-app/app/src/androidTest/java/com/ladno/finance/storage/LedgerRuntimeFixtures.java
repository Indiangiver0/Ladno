package com.ladno.finance.storage;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.ImportCandidate;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Test-only synthetic fixtures and expected/actual evidence; no DB reset or reflection. */
final class LedgerRuntimeFixtures {
    static final String BANK = "com.ladno.qa.bank";
    static final String CONTROL = "qa-ledger-control.json";
    static final String WRITE_EVIDENCE = "qa-ledger-write.json";
    static final String READ_EVIDENCE = "qa-ledger-read.json";
    final Context context;
    final LedgerStore store;
    final JSONArray assertions = new JSONArray();
    final String phase;
    LedgerRuntimeFixtures(String phase) {
        this.phase = phase;
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = LedgerStore.get(context);
    }
    static JSONObject object(Object... pairs) throws Exception {
        JSONObject value = new JSONObject();
        for (int i=0;i<pairs.length;i+=2) value.put((String)pairs[i], pairs[i+1] == null ? JSONObject.NULL : pairs[i+1]);
        return value;
    }
    void check(String id, Object expected, Object actual) throws Exception {
        Object want = expected == null ? JSONObject.NULL : expected;
        Object got = actual == null ? JSONObject.NULL : actual;
        boolean pass = want instanceof Number && got instanceof Number
            ? want.toString().equals(got.toString()) : Objects.equals(want, got);
        assertions.put(object("id",id,"expected",want,"actual",got,"pass",pass));
        Bundle event = new Bundle();
        event.putString("stream", "N1 " + phase + " " + id + " expected=" + want + " actual=" + got + " pass=" + pass + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, event);
        assertTrue(id + " expected=" + want + " actual=" + got, pass);
    }
    interface Call { void run() throws Exception; }
    void rejects(String id, String expectedCode, Call action) throws Exception {
        String code = "ACCEPTED";
        try { action.run(); } catch (ContractException error) { code = error.code; }
        check(id, expectedCode, code);
    }
    JSONObject save(String kind,String date,String amount,String currency,String category,String note) throws Exception {
        return store.execute("saveTransaction", transaction(null,null,kind,date,amount,currency,category,note)).getJSONObject("record");
    }
    static JSONObject transaction(String id,Long revision,String kind,String date,String amount,String currency,String category,String note) throws Exception {
        return object("id",id,"expectedRevision",revision,"kind",kind,"date",date,"amount",amount,"currency",currency,"categoryId",category,"note",note);
    }
    static JSONObject reviewTransaction(String kind,String date,String amount,String currency) throws Exception {
        return object("kind",kind,"date",date,"amount",amount,"currency",currency,"categoryId",null,"note","");
    }
    JSONObject record(String month,String currency,String id) throws Exception {
        JSONArray records = store.state(month,currency).getJSONArray("records");
        for(int i=0;i<records.length();i++) if(records.getJSONObject(i).getString("id").equals(id)) return records.getJSONObject(i);
        return null;
    }
    JSONObject pending(String id) throws Exception {
        JSONArray imports = store.state("2026-06","KZT").getJSONArray("imports");
        for(int i=0;i<imports.length();i++) if(imports.getJSONObject(i).getString("id").equals(id)) return imports.getJSONObject(i);
        return null;
    }
    void summary(String prefix,String month,String currency,String income,String expense,String net,int recordCount) throws Exception {
        JSONObject state=store.state(month,currency), sum=state.getJSONObject("summary");
        check(prefix+"/month",month,sum.getString("month"));
        check(prefix+"/currency",currency,sum.getString("currency"));
        check(prefix+"/income",income,sum.getString("income"));
        check(prefix+"/expense",expense,sum.getString("expense"));
        check(prefix+"/net",net,sum.getString("net"));
        check(prefix+"/records",recordCount,state.getJSONArray("records").length());
        check(prefix+"/amountType",true,sum.get("net") instanceof String);
    }
    static ImportCandidate candidate(String amount,String date) {
        return new ImportCandidate("expense",amount,"KZT",date,"source","",Collections.emptyList(),"high",null);
    }
    static ImportCandidate uncertain(String amount,String date) {
        return new ImportCandidate("expense",amount,"KZT",date,"source","",Collections.singletonList("LOW_CONFIDENCE"),"uncertain",null);
    }
    static String token(String label) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(("APP-006-N1-synthetic/"+label).getBytes(StandardCharsets.UTF_8));
        char[] chars = new char[digest.length*2]; String hex="0123456789abcdef";
        for(int i=0;i<digest.length;i++) { int value=digest[i]&255; chars[i*2]=hex.charAt(value>>>4); chars[i*2+1]=hex.charAt(value&15); }
        return new String(chars);
    }
    static JSONObject source(String kind,String key,String revision,String account,String reference) throws Exception {
        return object("kind",kind,"eventId",UUID.randomUUID().toString(),"sourceKeyToken",key==null?null:token(key),
            "revisionToken",token(revision),"accountSurrogate",account==null?null:token(account),
            "paymentRefToken",reference==null?null:token(reference),"packageName",BANK,
            "label",kind.equals("receipt")?"Чек":"Уведомление","capturedAt","2026-11-02T00:00:00Z","postTime",1793577600000L);
    }
    static boolean contains(JSONArray array,String value) throws Exception {
        for(int i=0;i<array.length();i++) if(value.equals(array.getString(i))) return true;
        return false;
    }
    void writeJson(String filename,JSONObject value) throws Exception {
        try(FileOutputStream output=context.openFileOutput(filename,Context.MODE_PRIVATE)) {
            output.write((value.toString(2)+"\n").getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
    }
    JSONObject readJson(String filename) throws Exception {
        try(FileInputStream input=context.openFileInput(filename)) {
            byte[] bytes=new byte[(int)input.getChannel().size()]; int offset=0, read;
            while(offset<bytes.length && (read=input.read(bytes,offset,bytes.length-offset))!=-1) offset+=read;
            return new JSONObject(new String(bytes,0,offset,StandardCharsets.UTF_8));
        }
    }
    void finish(boolean completed) throws Exception {
        JSONObject evidence=object("task","APP-006","criterion","N1","phase",phase,"pid",Process.myPid(),
            "api",Build.VERSION.SDK_INT,"abis",new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)),
            "completed",completed,"assertionCount",assertions.length(),"assertions",assertions);
        writeJson(phase.equals("write")?WRITE_EVIDENCE:READ_EVIDENCE,evidence);
    }
}
