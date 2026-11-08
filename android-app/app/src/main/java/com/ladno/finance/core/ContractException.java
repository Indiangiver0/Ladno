package com.ladno.finance.core;

/** Only fixed, non-sensitive messages may cross the native boundary. */
public final class ContractException extends RuntimeException {
    public final String code;
    public final String field;

    public ContractException(String code, String field, String safeMessage) {
        super(safeMessage);
        this.code = code;
        this.field = field;
    }
}
