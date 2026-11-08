package com.ladno.finance.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Two-model financial agreement guard. Agreement is necessary, never proof of OCR accuracy. */
public final class ReceiptOcrConsensus {
    private ReceiptOcrConsensus() { }

    public static ImportCandidate evaluate(ImportCandidate primary, int primaryConfidence,
            ImportCandidate verification, int verificationConfidence, boolean verificationFailed) {
        if (primary == null) throw new IllegalArgumentException("Primary recognition is required");
        boolean agree = !verificationFailed && primaryConfidence >= 80 && verificationConfidence >= 80
                && complete(primary) && complete(verification)
                && Objects.equals(primary.kind, verification.kind)
                && Objects.equals(primary.amount, verification.amount)
                && Objects.equals(primary.currency, verification.currency)
                && Objects.equals(primary.date, verification.date);
        if (agree) return primary;
        List<String> reasons = new ArrayList<>(primary.reasons);
        if (!reasons.contains("LOW_CONFIDENCE")) reasons.add("LOW_CONFIDENCE");
        return new ImportCandidate(primary.kind, primary.amount, primary.currency, primary.date,
                primary.dateOrigin, primary.note, reasons,
                primary.confidence.equals("unavailable") ? "unavailable" : "uncertain", primary.paymentReference);
    }

    private static boolean complete(ImportCandidate candidate) {
        return candidate != null && candidate.confidence.equals("high") && candidate.reasons.isEmpty()
                && candidate.kind != null && candidate.amount != null && candidate.currency != null && candidate.date != null;
    }
}
