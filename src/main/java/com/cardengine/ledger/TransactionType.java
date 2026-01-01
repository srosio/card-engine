package com.cardengine.ledger;

/**
 * Types of ledger transactions.
 *
 * The card engine uses a double-entry ledger with specific transaction types
 * that reflect the authorization and settlement lifecycle.
 */
public enum TransactionType {
    /**
     * Initial authorization hold on funds.
     * Reserves funds but does not move them yet.
     */
    AUTH_HOLD,

    /**
     * Release of a previously held authorization.
     * Occurs when authorization expires or is explicitly canceled.
     */
    AUTH_RELEASE,

    /**
     * Commitment of previously authorized funds (clearing/settlement).
     * Actually moves the funds from the account.
     */
    CLEARING_COMMIT,

    /**
     * Reversal of a previously cleared transaction.
     * Returns funds to the account.
     */
    REVERSAL,

    /**
     * Direct deposit to an account.
     */
    DEPOSIT,

    /**
     * Direct withdrawal from an account.
     */
    WITHDRAWAL
}
