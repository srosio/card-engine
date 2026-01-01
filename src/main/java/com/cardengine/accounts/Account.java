package com.cardengine.accounts;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;

/**
 * Core abstraction for all account types.
 *
 * Accounts store money. Cards never store money - they only authorize access to accounts.
 *
 * This interface enables the card engine to work with any type of backing account:
 * - Internal prepaid ledger
 * - Fiat wallet (mocked)
 * - Stablecoin account (mocked)
 * - External custodial account (read-only)
 *
 * All implementations must support the reserve-commit-release pattern for authorization holds.
 */
public interface Account {

    /**
     * Get the unique identifier for this account.
     */
    String getAccountId();

    /**
     * Get the account type.
     */
    AccountType getAccountType();

    /**
     * Get the current available balance (total balance minus reserved funds).
     */
    Money getBalance();

    /**
     * Get the currency of this account.
     */
    Currency getCurrency();

    /**
     * Reserve funds for a pending authorization.
     * This reduces the available balance but does not move funds yet.
     *
     * @param amount the amount to reserve
     * @param authorizationId unique identifier for this authorization
     * @throws com.cardengine.common.exception.InsufficientFundsException if insufficient funds
     */
    void reserve(Money amount, String authorizationId);

    /**
     * Commit previously reserved funds (complete the transaction).
     * This permanently moves the funds out of the account.
     *
     * @param amount the amount to commit (may be less than reserved for partial clears)
     * @param authorizationId the authorization that reserved the funds
     */
    void commit(Money amount, String authorizationId);

    /**
     * Release previously reserved funds (cancel the authorization).
     * This restores the funds to the available balance.
     *
     * @param amount the amount to release
     * @param authorizationId the authorization to release
     */
    void release(Money amount, String authorizationId);

    /**
     * Get the total amount currently reserved across all pending authorizations.
     */
    Money getReservedBalance();

    /**
     * Get the total balance (available + reserved).
     */
    Money getTotalBalance();
}
