import com.ladno.finance.core.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Independent synthetic expectations from APP-008 requirements, not production tests. */
public final class ReceiptIndependentQa {
    static final JSONArray checks = new JSONArray(), cases = new JSONArray();
    static int failures;
    static void equal(String id, Object expected, Object actual) {
        boolean pass = Objects.equals(expected, actual);
        checks.put(new JSONObject().put("id", id).put("expected", expected == null ? JSONObject.NULL : expected)
                .put("actual", actual == null ? JSONObject.NULL : actual).put("pass", pass));
        if (!pass) failures++;
    }
    static void receipt(String id, String text, String confidence, String amount, String date, String reason) {
        ImportCandidate result = ReceiptParser.parse(text);
        cases.put(new JSONObject().put("id",id).put("input",text == null ? JSONObject.NULL : text).put("actual",result.toJson()));
        equal(id+"/confidence",confidence,result.confidence);
        equal(id+"/amount",amount,result.amount);
        equal(id+"/date",date,result.date);
        if (amount != null) equal(id+"/currency","KZT",result.currency);
        if (reason != null) equal(id+"/reason/"+reason,true,result.reasons.contains(reason));
        equal(id+"/noRawNote","",result.note);
        if ("high".equals(result.confidence)) equal(id+"/highHasNoReasons",0,result.reasons.size());
    }
    static void bank(String id, String text, String confidence, String amount, String kind) {
        ImportCandidate result = BankMessageParser.parse(text,1793664000000L,"Asia/Qyzylorda",true);
        cases.put(new JSONObject().put("id",id).put("input",text).put("actual",result.toJson()));
        equal(id+"/confidence",confidence,result.confidence); equal(id+"/amount",amount,result.amount);
        equal(id+"/kind",kind,result.kind);
    }
    public static void main(String[] args) throws Exception {
        String day="03.11.2026", date="2026-11-03";
        receipt("R01-multiline-local",day+"\nИтого\n12 345.67 тг\nОплачено","high","12345.67",date,null);
        receipt("R02-cyrillic-tenge",day+"\nИтог: 78,90 теңге\nОплата выполнена","high","78.90",date,null);
        receipt("R03-alias-tenge",day+"\nОплачено 45.10 тенге","high","45.10",date,null);
        receipt("R04-equal-total-status",day+"\nИТОГО 9.99 KZT\nСумма 9.99 KZT\nОплачено 9.99 KZT","high","9.99",date,null);
        receipt("R05-fiscal-multiline",day+"\nФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого\n1250.00 тг\nКартой\n1250.00 тг","high","1250.00",date,null);
        receipt("R06-fiscal-one-line",day+"\nФискальный чек\nТип операции: Продажа\nИтого 22.22 KZT\nБезналичный расчет 22.22 KZT","high","22.22",date,null);
        receipt("R07-bare-total",day+"\nИтого\n1250.00 тг","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R08-negated-paid",day+"\nИтого 1250.00 тг\nНе оплачено","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R09-mismatch-card",day+"\nФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 1250.00 тг\nКартой 1500.00 тг","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R10-two-settlements",day+"\nФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nИтого 1250.00 тг\nКартой 1250.00 тг\nКартой 1250.00 тг","uncertain","1250.00",date,"AMBIGUOUS_TOTAL");
        receipt("R11-no-sale",day+"\nФИСКАЛЬНЫЙ ЧЕК\nИтого 1250.00 тг\nКартой 1250.00 тг","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R12-no-fiscal",day+"\nПРИХОД\nИтого 1250.00 тг\nКартой 1250.00 тг","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R13-return",day+"\nФИСКАЛЬНЫЙ ЧЕК\nПРИХОД\nВозврат\nИтого 1250.00 тг\nКартой 1250.00 тг","uncertain","1250.00",date,"UNSUPPORTED_EVENT");
        receipt("R14-different-totals",day+"\nИтого 12.00 KZT\nСумма 13.00 KZT\nОплачено","uncertain",null,date,"AMBIGUOUS_TOTAL");
        receipt("R15-item-not-total",day+"\nИтого\nТовар 1250.00 KZT\nОплачено","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R16-phone-not-total",day+"\nИтого\nТелефон +77771234567\nВалюта KZT\nОплачено","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R17-no-date","Итого 2.50 KZT\nОплачено","uncertain","2.50",null,"DATE_MISSING");
        receipt("R18-invalid-date","29.02.2026\nИтого 2.50 KZT\nОплачено","uncertain","2.50",null,"DATE_INVALID");
        receipt("R19-cash-change",day+"\nИтого 5.00 KZT\nВнесено наличными 10.00 KZT\nСдача 5.00 KZT","uncertain","5.00",date,"UNSUPPORTED_EVENT");
        receipt("R20-ambiguous-separator",day+"\nИтого 1,234 KZT\nОплачено","uncertain",null,date,"AMBIGUOUS_SEPARATOR");
        receipt("R21-cross-currency",day+"\nИтого 12.00 KZT\nОплачено\nБаланс 10.00 USD","uncertain",null,date,"CURRENCY_UNKNOWN");
        receipt("R22-zero",day+"\nИтого 0.00 KZT\nОплачено","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R23-blank", "", "unavailable",null,null,"UNAVAILABLE");
        receipt("R24-next-blank-line",day+"\nИтого:\n\n6.40 тг\nОплачено","high","6.40",date,null);
        receipt("R25-paid-question",day+"\nИтого 6.40 KZT\nОплачено?","uncertain","6.40",date,"UNSUPPORTED_EVENT");
        receipt("R26-larger-than-double",day+"\nИтого 9007199254740993.01 тг\nОплачено","high","9007199254740993.01",date,null);
        receipt("R27-tax-not-final-total",day+"\nСумма НДС 50.00 тг\nОплачено","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R28-tax-before-actual-total",day+"\nСумма НДС 50.00 тг\nИтого 500.00 тг\nОплачено","high","500.00",date,null);
        receipt("R29-itogo-tax-qualifier",day+"\nИтого НДС 50.00 тг\nОплачено","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R30-total-tax-qualifier",day+"\nTOTAL TAX 50.00 KZT\nPaid","uncertain",null,date,"AMOUNT_MISSING");
        receipt("R31-paid-tax-not-settlement",day+"\nИтого 500.00 тг\nОплачено НДС 50.00 тг","uncertain","500.00",date,"UNSUPPORTED_EVENT");
        receipt("R32-total-paid-tax-not-settlement",day+"\nTOTAL 500.00 KZT\nTOTAL PAID TAX 50.00 KZT","uncertain","500.00",date,"UNSUPPORTED_EVENT");
        bank("B01-paid","Оплачено 12.00 KZT 2026-11-03","high","12.00","expense");
        bank("B02-income","Зачислено 0.01 KZT 2026-11-03","high","0.01","income");
        bank("B03-scheduled","Запланирована покупка 12.00 KZT 2026-11-03","uncertain","12.00","expense");
        bank("B04-negated","Оплата 12.00 KZT не произведена 2026-11-03","uncertain","12.00","expense");
        bank("B05-balance","Покупка 12.00 KZT 2026-11-03\nБаланс 45.00 KZT","uncertain","12.00","expense");
        bank("B06-unsupported-local-alias","Оплачено 12.00 тг 2026-11-03","uncertain",null,"expense");
        bank("B07-transfer","Перевод получен 12.00 KZT 2026-11-03","uncertain","12.00",null);
        bank("B08-refund","Refund received 12.00 KZT 2026-11-03","uncertain","12.00","income");
        bank("B09-expense-income","Credited and debited 12.00 KZT 2026-11-03","uncertain","12.00",null);
        JSONObject report=new JSONObject().put("scope","Independent synthetic JVM receipt/bank checks; not real OCR/photo or Android UI")
            .put("assertions",checks.length()).put("failed",failures).put("checks",checks).put("cases",cases);
        Files.writeString(Path.of(args[0]),report.toString(2)+"\n");
        System.out.println("Receipt/bank independent QA: "+checks.length()+" assertions, failed="+failures);
        if(failures>0) System.exit(1);
    }
}
