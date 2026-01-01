package com.cardengine.accounts;

/**
 * Types of accounts supported by the card engine.
 */
public enum AccountType {
    /**
     * Internal ledger account managed entirely within the card engine.
     * Supports full reserve-commit-release lifecycle.
     */
    INTERNAL_LEDGER,

    /**
     * Fiat wallet account (mocked in MVP).
     * In production, this would integrate with a banking partner API.
     */
    FIAT_WALLET,

    /**
     * Stablecoin account (mocked in MVP).
     * In production, this would integrate with a blockchain or custodian API.
     */
    STABLECOIN,

    /**
     * External custodial account (read-only in MVP).
     * In production, this would integrate with a third-party custodian API.
     */
    EXTERNAL_CUSTODIAL
}
