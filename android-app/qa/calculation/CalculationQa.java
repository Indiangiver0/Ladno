import com.ladno.finance.core.*;
import org.json.*;
import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;

/** Independent QA adapter: expectations originate in MONEY v2/finance fixtures. */
public final class CalculationQa {
    static int assertions, failed;
    static final JSONArray results = new JSONArray();
    static void eq(String id, Object expected, Object actual) {
        assertions++;
        boolean pass = Objects.equals(expected, actual);
        if (!pass) failed++;
        results.put(new JSONObject().put("id",id).put("expected",expected == null ? JSONObject.NULL : expected)
            .put("actual",actual == null ? JSONObject.NULL : actual).put("pass",pass));
    }
    interface Call { void run(); }
    static void rejects(String id, Call call) {
        String actual = "accepted";
        try { call.run(); } catch (ContractException e) { actual = e.code; }
        eq(id,"VALIDATION",actual);
    }
    static JSONObject group(JSONArray groups,String id) {
        for (int i=0;i<groups.length();i++) if(groups.getJSONObject(i).getString("id").equals(id)) return groups.getJSONObject(i);
        throw new IllegalArgumentException(id);
    }
    static String nullable(JSONObject value,String key) { return value.isNull(key) ? null : value.getString(key); }
    static void manual(JSONObject group) {
        JSONArray variants = group.getJSONObject("input").getJSONArray("variants");
        for(int i=0;i<variants.length();i++) {
            JSONObject v=variants.getJSONObject(i), in=v.getJSONObject("input"), expected=v.getJSONObject("expected");
            String id=group.getString("id")+"/"+i, raw=in.getString("raw"), currency=in.getString("currency");
            if(expected.getString("decision").equals("reject")) rejects(id,()->Money.parseManual(raw,currency));
            else {
                Money money=Money.parseManual(raw,currency);
                eq(id+"/amount",expected.getString("amount"),money.amount);
                eq(id+"/minor",expected.getString("minor"),money.minor.toString());
                eq(id+"/scale",expected.getInt("scale"),money.scale);
                eq(id+"/currency",currency,money.currency);
            }
        }
    }
    static void bank(String id,String text,String expectedConfidence,String expectedAmount,String expectedDate) {
        ImportCandidate c=BankMessageParser.parse(text,1793478600000L,"UTC",true);
        eq(id+"/confidence",expectedConfidence,c.confidence);
        if(expectedAmount!=null) eq(id+"/amount",expectedAmount,c.amount);
        if(expectedDate!=null) eq(id+"/date",expectedDate,c.date);
    }
    static void receipt(String id,String text,String confidence,String amount) {
        ImportCandidate c=ReceiptParser.parse(text);
        eq(id+"/confidence",confidence,c.confidence);
        if(amount!=null) eq(id+"/amount",amount,c.amount);
    }
    public static void main(String[] args) throws Exception {
        JSONObject fixtures=new JSONObject(Files.readString(Path.of(args[0])));
        JSONArray groups=fixtures.getJSONArray("caseGroups");
        eq("fixture/groupCount",40,groups.length());
        for(String id: new String[]{"MONEY-002","MONEY-003","MONEY-004","MONEY-006","MONEY-007","MONEY-008","MONEY-009","MONEY-013"}) manual(group(groups,id));
        JSONObject canonical=group(groups,"MONEY-005").getJSONObject("input");
        for(String key: new String[]{"valid","nonCanonical"}) {
            JSONArray array=canonical.getJSONArray(key);
            for(int i=0;i<array.length();i++) {
                JSONObject v=array.getJSONObject(i); String id="MONEY-005/"+key+"/"+i;
                if(key.equals("valid")) eq(id,v.getString("amount"),Money.parseCanonical(v.getString("amount"),v.getString("currency"),v.getInt("scale")).amount);
                else rejects(id,()->Money.parseCanonical(v.getString("amount"),v.getString("currency"),v.getInt("scale")));
            }
        }
        JSONArray sources=group(groups,"MONEY-010").getJSONObject("input").getJSONArray("variants");
        for(int i=0;i<sources.length();i++) {
            JSONObject v=sources.getJSONObject(i), expected=v.getJSONObject("expected"); String id="MONEY-010/"+i;
            if(expected.getString("decision").equals("normalize")) {
                Money money=Money.parseSource(v.getString("raw"),"KZT");
                eq(id+"/amount",expected.getString("amount"),money.amount);
                eq(id+"/minor",expected.getString("minor"),money.minor.toString());
            } else rejects(id,()->Money.parseSource(v.getString("raw"),"KZT"));
        }
        JSONArray punctuation=group(groups,"MONEY-011").getJSONObject("input").getJSONArray("variants");
        for(int i=0;i<punctuation.length();i++) {
            JSONObject v=punctuation.getJSONObject(i); rejects("MONEY-011/"+i,()->Money.parseSource(v.getString("raw"),v.getString("currency")));
        }
        for(String code:new String[]{"ZZZ","XXX","kzt","$"}) rejects("MONEY-012/currency/"+code,()->Money.scale(code));
        rejects("MONEY-030/scaleMismatch",()->Money.parseCanonical("1.000","KZT",3));
        JSONArray additions=group(groups,"MONEY-014").getJSONObject("input").getJSONArray("variants");
        for(int i=0;i<additions.length();i++) {
            JSONObject v=additions.getJSONObject(i); JSONArray operands=v.getJSONArray("operands"); BigInteger sum=BigInteger.ZERO;
            for(int n=0;n<operands.length();n++) {
                JSONObject o=operands.getJSONObject(n); Money m=Money.parseCanonical(o.getString("amount"),o.getString("currency"),o.getInt("scale"));
                eq("MONEY-014/"+i+"/operand/"+n,v.getJSONObject("expected").getJSONArray("minorOperands").getString(n),m.minor.toString()); sum=sum.add(m.minor);
            }
            eq("MONEY-014/"+i+"/sum",v.getJSONObject("expected").getString("sum"),Money.formatMinor(sum,2));
        }
        // Test core conversion/format precision, not LedgerStore's aggregation implementation.
        for(String id:new String[]{"MONEY-015","MONEY-016","MONEY-018"}) {
            JSONObject g=group(groups,id); JSONArray ops=g.getJSONObject("input").getJSONArray("operations"); BigInteger income=BigInteger.ZERO,expense=BigInteger.ZERO;
            for(int i=0;i<ops.length();i++) { JSONObject o=ops.getJSONObject(i); Money m=Money.parseCanonical(o.getString("amount"),o.getString("currency"),o.getInt("scale")); if(o.getString("kind").equals("income")) income=income.add(m.minor); else expense=expense.add(m.minor); }
            JSONObject e=g.getJSONObject("expected").getJSONObject("summary");
            eq(id+"/coreIncomePrecision",e.getString("income"),Money.formatMinor(income,2));
            eq(id+"/coreExpensePrecision",e.getString("expense"),Money.formatMinor(expense,2));
            eq(id+"/coreNetPrecision",e.getString("net"),Money.formatMinor(income.subtract(expense),2));
        }
        for(int scale:new int[]{0,2,3}) eq("MONEY-017/zero/"+scale,scale==0?"0":scale==2?"0.00":"0.000",Money.formatMinor(BigInteger.ZERO,scale));
        JSONObject dates=group(groups,"MONEY-020"); JSONArray dateValues=dates.getJSONObject("input").getJSONArray("dates"), decisions=dates.getJSONObject("expected").getJSONArray("dateDecisions");
        for(int i=0;i<dateValues.length();i++) { String date=dateValues.getString(i); if(decisions.getString(i).equals("accept")) eq("MONEY-020/"+i,date,Money.date(date)); else rejects("MONEY-020/"+i,()->Money.date(date)); }
        eq("MONEY-019/monthStart","2026-10-01",Money.month("2026-10").atDay(1).toString());
        eq("MONEY-019/monthEnd","2026-11-01",Money.month("2026-10").plusMonths(1).atDay(1).toString());
        eq("MONEY-020/terminalBoundary","+10000-01-01",Money.month("9999-12").plusMonths(1).atDay(1).toString());
        for(String month:new String[]{"0000-01","2026-13","2026-1","2026-10-01"}) rejects("strictMonth/"+month,()->Money.month(month));
        ImportCandidate utc=BankMessageParser.parse("Paid 1.20 KZT",1793478600000L,"UTC",true), tokyo=BankMessageParser.parse("Paid 1.20 KZT",1793478600000L,"Asia/Tokyo",true);
        eq("MONEY-023/UTC","2026-10-31",utc.date); eq("MONEY-023/Tokyo","2026-11-01",tokyo.date); eq("MONEY-023/origin","post_time",tokyo.dateOrigin);
        for(String zone:new String[]{null,"Unknown/Zone"}) eq("MONEY-024/zone/"+zone,null,BankMessageParser.parse("Paid 1.20 KZT",1793478600000L,zone,true).date);
        eq("MONEY-024/noConsent",null,BankMessageParser.parse("Paid 1.20 KZT",1793478600000L,"UTC",false).date);
        eq("MONEY-024/noPostTime",null,BankMessageParser.parse("Paid 1.20 KZT",0,"UTC",true).date);
        bank("MONEY-022/sourceDate","Paid 1.20 KZT 2026-10-30","high","1.20","2026-10-30");
        bank("MONEY-025/invalidExplicitDate","Paid 1.20 KZT 2026-02-29","uncertain","1.20",null);
        eq("MONEY-025/noManufacturedDate",null,BankMessageParser.parse("Paid 1.20 KZT 2026-02-29",1793478600000L,"UTC",true).date);
        for(String text:new String[]{"Scheduled purchase 1.20 KZT 2026-10-31","Payment pending 1.20 KZT 2026-10-31","Payment completed 1.20 KZT 2026-10-31 cancelled","Refund 1.20 KZT 2026-10-31","Transfer received 1.20 KZT 2026-10-31","Credited and debited 1.20 KZT 2026-10-31","-1.20 KZT 2026-10-31","Запланирована покупка 10.00 RUB 2026-11-02","Оплата 10.00 RUB 2026-11-02 не произведена","Покупка не состоялась 1.20 KZT 2026-10-31"}) bank("MONEY-032/"+text,text,"uncertain",null,null);
        bank("completedRussian","Оплата произведена 10.00 RUB 2026-11-02","high","10.00","2026-11-02");
        bank("completedIncome","Зачислено 10.00 RUB 2026-11-02","high","10.00","2026-11-02");
        bank("MONEY-033/balanceOnly","Balance 9999.99 KZT 2026-10-31","uncertain",null,null);
        eq("MONEY-033/balanceNotAmount",null,BankMessageParser.parse("Balance 9999.99 KZT 2026-10-31",1793478600000L,"UTC",true).amount);
        bank("MONEY-033/multiple","Paid 123.45 KZT 2026-10-31\nBalance 9999.99 KZT","uncertain","123.45","2026-10-31");
        receipt("MONEY-034/paid","2026-10-31\nТовар 100.00 KZT\nТовар 23.45 KZT\nИтого 123.45 KZT\nОплачено\nВнесено наличными 200.00 KZT\nСдача 76.55 KZT","high","123.45");
        receipt("MONEY-034/noFinal","2026-10-31\nВнесено наличными 200.00 KZT\nСдача 76.55 KZT","uncertain",null);
        receipt("MONEY-034/multipleFinal","2026-10-31\nИтого 123.45 KZT\nИтого 124.45 KZT","uncertain",null);
        receipt("MONEY-025/noReceiptDate","Итого 123.45 KZT","uncertain","123.45");
        receipt("QA-RECEIPT-FINALITY","2026-10-31\nК оплате 123.45 KZT","uncertain","123.45");
        ImportCandidate needsPaidReview=ReceiptParser.parse("2026-10-31\nК оплате 123.45 KZT");
        eq("QA-RECEIPT-FINALITY/retainedDate","2026-10-31",needsPaidReview.date);
        eq("QA-RECEIPT-FINALITY/reason",true,needsPaidReview.reasons.contains("UNSUPPORTED_EVENT"));
        receipt("QA-BARE-TOTAL-CASH","2026-10-31\nИтого 123.45 KZT\nВнесено наличными 200.00 KZT\nСдача 76.55 KZT","uncertain","123.45");
        receipt("QA-PAID-ENGLISH","2026-10-31\nTotal paid 123.45 KZT","high","123.45");
        receipt("QA-PAID-RUSSIAN","2026-10-31\nИтого 123.45 KZT\nОплата успешно произведена","high","123.45");
        receipt("QA-PAID-KAZAKH","2026-10-31\nТөленген 123.45 KZT","high","123.45");
        receipt("QA-NEGATED-PAID","2026-10-31\nИтого 123.45 KZT\nНе оплачено","uncertain","123.45");
        receipt("QA-ENGLISH-NOT-PAID","2026-10-31\nTotal paid 123.45 KZT\nNot paid","uncertain","123.45");
        ImportCandidate large=new ImportCandidate("expense","9007199254740993.01","KZT","2026-10-31","source","",List.of(),"high",null);
        JSONObject wire=large.toJson(); eq("MONEY-040/JSONString",true,wire.get("amount") instanceof String);
        eq("MONEY-040/roundTrip","9007199254740993.01",ImportCandidate.fromJson(new JSONObject(wire.toString())).amount);
        eq("MONEY-040/minor","900719925474099301",Money.parseCanonical(large.amount,"KZT",2).minor.toString());
        JSONObject output=new JSONObject().put("assertions",assertions).put("failed",failed).put("results",results).put("javaVersion",System.getProperty("java.version"));
        Files.writeString(Path.of(args[1]),output.toString(2)+"\n"); System.out.println("Independent JVM QA: "+assertions+" assertions; failed="+failed);
        if(failed>0) System.exit(1);
    }
}
