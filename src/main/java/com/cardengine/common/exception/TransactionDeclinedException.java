package com.cardengine.common.exception;

/**
 * Thrown when a transaction is declined.
 */
public class TransactionDeclinedException extends CardEngineException {

    private final String declineReason;

    public TransactionDeclinedException(String reason) {
        super("Transaction declined: " + reason);
        this.declineReason = reason;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}
