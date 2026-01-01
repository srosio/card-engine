package com.cardengine.bank;

import com.cardengine.common.exception.CardEngineException;

/**
 * Exception thrown when bank core operations fail.
 *
 * This wraps all errors from external bank core systems,
 * allowing the card engine to handle them consistently.
 */
public class BankCoreException extends CardEngineException {

    private final String bankAccountRef;
    private final String operation;

    public BankCoreException(String message, String bankAccountRef, String operation) {
        super(message);
        this.bankAccountRef = bankAccountRef;
        this.operation = operation;
    }

    public BankCoreException(String message, String bankAccountRef, String operation, Throwable cause) {
        super(message, cause);
        this.bankAccountRef = bankAccountRef;
        this.operation = operation;
    }

    public String getBankAccountRef() {
        return bankAccountRef;
    }

    public String getOperation() {
        return operation;
    }
}
