import com.ladno.finance.core.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Independent guard vectors derived from the two-pass contract, not author tests. */
public final class ReceiptConsensusIndependentQa {
    static final JSONArray checks=new JSONArray(),vectors=new JSONArray();
    static int failures;
    static ImportCandidate candidate(String kind,String amount,String currency,String date,String confidence,List<String> reasons) {
        return new ImportCandidate(kind,amount,currency,date,date==null?null:"source","primary-safe-note",reasons,confidence,"primary-reference");
    }
    static ImportCandidate good() {return candidate("expense","754.32","KZT","2026-11-03","high",List.of());}
    static void equal(String id,Object expected,Object actual) {
        boolean pass=Objects.equals(expected,actual);
        checks.put(new JSONObject().put("id",id).put("expected",expected==null?JSONObject.NULL:expected)
            .put("actual",actual==null?JSONObject.NULL:actual).put("pass",pass));
        if(!pass)failures++;
    }
    static void check(String id,ImportCandidate primary,int pconf,ImportCandidate verification,int vconf,boolean failed,String expectedConfidence) {
        String pbefore=primary.toJson().toString(),vbefore=verification==null?null:verification.toJson().toString();
        ImportCandidate result=ReceiptOcrConsensus.evaluate(primary,pconf,verification,vconf,failed);
        vectors.put(new JSONObject().put("id",id).put("primary",new JSONObject(pbefore)).put("primaryOcrConfidence",pconf)
            .put("verification",vbefore==null?JSONObject.NULL:new JSONObject(vbefore)).put("verificationOcrConfidence",vconf)
            .put("verificationFailed",failed).put("expectedConfidence",expectedConfidence).put("actual",result.toJson()));
        equal(id+"/confidence",expectedConfidence,result.confidence);
        equal(id+"/primaryUnmodified",pbefore,primary.toJson().toString());
        equal(id+"/verificationUnmodified",vbefore,verification==null?null:verification.toJson().toString());
        equal(id+"/kind",primary.kind,result.kind); equal(id+"/amount",primary.amount,result.amount);
        equal(id+"/currency",primary.currency,result.currency);equal(id+"/date",primary.date,result.date);
        equal(id+"/dateOrigin",primary.dateOrigin,result.dateOrigin);equal(id+"/note",primary.note,result.note);
        equal(id+"/paymentReference",primary.paymentReference,result.paymentReference);
        List<String> expectedReasons=new ArrayList<>(primary.reasons);
        if(!"high".equals(expectedConfidence)&&!expectedReasons.contains("LOW_CONFIDENCE"))expectedReasons.add("LOW_CONFIDENCE");
        equal(id+"/reasonsExact",expectedReasons,result.reasons);
        equal(id+"/lowConfidenceNoDuplicate",expectedReasons.contains("LOW_CONFIDENCE")?1:0,Collections.frequency(result.reasons,"LOW_CONFIDENCE"));
    }
    public static void main(String[] args)throws Exception {
        check("C01-boundary80",good(),80,good(),80,false,"high");
        check("C02-primary79",good(),79,good(),99,false,"uncertain");
        check("C03-secondary79",good(),99,good(),79,false,"uncertain");
        check("C04-secondaryFailed",good(),99,good(),99,true,"uncertain");
        check("C05-nullSecondaryEvenWithoutFailureFlag",good(),99,null,99,false,"uncertain");
        check("C06-nullSecondaryFailure",good(),99,null,0,true,"uncertain");
        check("C07-missingPrimaryAmount",candidate("expense",null,"KZT","2026-11-03","high",List.of()),99,good(),99,false,"uncertain");
        check("C08-missingSecondaryAmount",good(),99,candidate("expense",null,"KZT","2026-11-03","high",List.of()),99,false,"uncertain");
        check("C09-missingPrimaryDate",candidate("expense","754.32","KZT",null,"high",List.of()),99,good(),99,false,"uncertain");
        check("C10-missingSecondaryDate",good(),99,candidate("expense","754.32","KZT",null,"high",List.of()),99,false,"uncertain");
        check("C11-missingPrimaryCurrency",candidate("expense","754.32",null,"2026-11-03","high",List.of()),99,good(),99,false,"uncertain");
        check("C12-missingSecondaryCurrency",good(),99,candidate("expense","754.32",null,"2026-11-03","high",List.of()),99,false,"uncertain");
        check("C13-missingPrimaryKind",candidate(null,"754.32","KZT","2026-11-03","high",List.of()),99,good(),99,false,"uncertain");
        check("C14-missingSecondaryKind",good(),99,candidate(null,"754.32","KZT","2026-11-03","high",List.of()),99,false,"uncertain");
        check("C15-known754vs794",good(),94,candidate("expense","794.32","KZT","2026-11-03","high",List.of()),95,false,"uncertain");
        check("C16-kindMismatch",good(),99,candidate("income","754.32","KZT","2026-11-03","high",List.of()),99,false,"uncertain");
        check("C17-currencyMismatch",good(),99,candidate("expense","754.32","USD","2026-11-03","high",List.of()),99,false,"uncertain");
        check("C18-dateMismatch",good(),99,candidate("expense","754.32","KZT","2026-11-02","high",List.of()),99,false,"uncertain");
        check("C19-primaryUncertain",candidate("expense","754.32","KZT","2026-11-03","uncertain",List.of()),99,good(),99,false,"uncertain");
        check("C20-secondaryUncertain",good(),99,candidate("expense","754.32","KZT","2026-11-03","uncertain",List.of()),99,false,"uncertain");
        check("C21-primaryReasonsEvenIfHigh",candidate("expense","754.32","KZT","2026-11-03","high",List.of("UNSUPPORTED_EVENT")),99,good(),99,false,"uncertain");
        check("C22-secondaryReasonsEvenIfHigh",good(),99,candidate("expense","754.32","KZT","2026-11-03","high",List.of("UNSUPPORTED_EVENT")),99,false,"uncertain");
        check("C23-primaryUnavailableStaysUnavailable",candidate(null,null,null,null,"unavailable",List.of("UNAVAILABLE")),99,good(),99,false,"unavailable");
        check("C24-existingLowConfidenceNoDuplicate",candidate("expense","754.32","KZT","2026-11-03","uncertain",List.of("LOW_CONFIDENCE")),99,good(),99,false,"uncertain");
        check("C25-unavailableAndVerificationFailure",candidate(null,null,null,null,"unavailable",List.of("UNAVAILABLE")),0,null,0,true,"unavailable");
        check("C26-both100",good(),100,good(),100,false,"high");
        check("C27-primary0",good(),0,good(),100,false,"uncertain");
        check("C28-secondary0",good(),100,good(),0,false,"uncertain");
        ImportCandidate differentNonfinancial=new ImportCandidate("expense","754.32","KZT","2026-11-03","user","different-safe-note",List.of(),"high","different-reference");
        check("C29-nonfinancialDifferencePreservePrimary",good(),99,differentNonfinancial,99,false,"high");
        check("C30-bothUncertainNotPromoted",candidate("expense","754.32","KZT","2026-11-03","uncertain",List.of("LOW_CONFIDENCE")),99,candidate("expense","754.32","KZT","2026-11-03","uncertain",List.of("LOW_CONFIDENCE")),99,false,"uncertain");
        ImportCandidate huge=candidate("expense","9007199254740993.01","KZT","2026-11-03","high",List.of());
        check("C31-hugeOneMinorMismatch",huge,99,candidate("expense","9007199254740993.02","KZT","2026-11-03","high",List.of()),99,false,"uncertain");
        check("C32-hugeExactAgreement",huge,99,candidate("expense","9007199254740993.01","KZT","2026-11-03","high",List.of()),99,false,"high");
        JSONObject report=new JSONObject().put("scope","Independent pure-Java consensus guard only; not OCR JNI/photo/Android UI integration")
            .put("vectorGroups",vectors.length()).put("assertions",checks.length()).put("failed",failures).put("vectors",vectors).put("checks",checks);
        Files.writeString(Path.of(args[0]),report.toString(2)+"\n");
        System.out.println("Independent consensus QA: "+vectors.length()+" groups, "+checks.length()+" assertions, failed="+failures);
        if(failures>0)System.exit(1);
    }
}
