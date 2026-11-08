package com.ladno.finance.core;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReceiptOcrConsensusTest {
    private ImportCandidate paid(String amount) {
        return ReceiptParser.parse("PAID\nTOTAL " + amount + " KZT\n2026-11-03");
    }

    @Test public void bothHighCompleteEqualResultsRetainCompletedEvidence() {
        ImportCandidate primary = paid("123.45");
        assertSame(primary, ReceiptOcrConsensus.evaluate(primary, 94, paid("123.45"), 92, false));
    }

    @Test public void knownDigitDisagreementPreservesPrimaryForReview() {
        ImportCandidate value = ReceiptOcrConsensus.evaluate(paid("754.32"), 94, paid("794.32"), 94, false);
        assertEquals("754.32", value.amount);
        assertEquals("KZT", value.currency);
        assertEquals("2026-11-03", value.date);
        assertEquals("uncertain", value.confidence);
        assertEquals(Collections.singletonList("LOW_CONFIDENCE"), value.reasons);
    }

    @Test public void lowConfidenceOrFailedMissingVerificationNeverAutoposts() {
        assertReview(ReceiptOcrConsensus.evaluate(paid("123.45"), 79, paid("123.45"), 99, false));
        assertReview(ReceiptOcrConsensus.evaluate(paid("123.45"), 99, paid("123.45"), 79, false));
        assertReview(ReceiptOcrConsensus.evaluate(paid("123.45"), 99, null, -1, true));
        assertReview(ReceiptOcrConsensus.evaluate(paid("123.45"), 99, null, 99, false));
    }

    @Test public void completedEvidenceAndEveryFinancialFieldMustAgree() {
        ImportCandidate primary = paid("123.45");
        for (String text : new String[]{"TOTAL 123.45 KZT\n2026-11-03", "UNPAID\nTOTAL 123.45 KZT\n2026-11-03",
                "PAID\nTOTAL 123.45 USD\n2026-11-03", "PAID\nTOTAL 123.45 KZT\n2026-11-04", "PAID\nTOTAL 123.45 KZT"}) {
            assertReview(ReceiptOcrConsensus.evaluate(primary, 99, ReceiptParser.parse(text), 99, false));
        }
        ImportCandidate income = new ImportCandidate("income", primary.amount, primary.currency, primary.date,
            primary.dateOrigin, primary.note, primary.reasons, primary.confidence, primary.paymentReference);
        assertReview(ReceiptOcrConsensus.evaluate(primary, 99, income, 99, false));
    }

    @Test public void agreementCannotPromoteUnpaidOrIncompletePrimary() {
        ImportCandidate unpaid = ReceiptParser.parse("TOTAL 123.45 KZT\n2026-11-03");
        ImportCandidate result = ReceiptOcrConsensus.evaluate(unpaid, 99, unpaid, 99, false);
        assertReview(result);
        assertTrue(result.reasons.contains("UNSUPPORTED_EVENT"));
        assertEquals("123.45", result.amount);
        assertEquals(unpaid.toJson().toString(), ReceiptParser.parse("TOTAL 123.45 KZT\n2026-11-03").toJson().toString());
    }

    private void assertReview(ImportCandidate candidate) {
        assertNotEquals("high", candidate.confidence);
        assertTrue(candidate.reasons.contains("LOW_CONFIDENCE"));
    }
}
