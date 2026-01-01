package com.cardengine.common.exception;

import com.cardengine.common.Money;

/**
 * Thrown when an account has insufficient funds for a transaction.
 */
public class InsufficientFundsException extends CardEngineException {

    public InsufficientFundsException(String accountId, Money required, Money available) {
        super(String.format("Insufficient funds in account %s. Required: %s %s, Available: %s %s",
            accountId,
            required.getAmount(), required.getCurrency(),
            available.getAmount(), available.getCurrency()));
    }
}
