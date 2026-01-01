package com.cardengine.common.exception;

/**
 * Thrown when an account is not found.
 */
public class AccountNotFoundException extends CardEngineException {

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}
