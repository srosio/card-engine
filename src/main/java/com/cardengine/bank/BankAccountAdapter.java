package com.cardengine.bank;

import com.cardengine.common.Money;

/**
 * Generic interface for integrating with bank core systems.
 *
 * This interface abstracts operations on bank accounts that exist in external
 * core banking systems (CBS). The bank core is the SINGLE SOURCE OF TRUTH for:
 * - Client/customer data
 * - Account balances
 * - Transaction history
 * - Ledger entries
 *
 * IMPORTANT PRINCIPLES:
 * - Card Engine NEVER owns or mirrors balances
 * - Bank accounts exist BEFORE card issuance
 * - Cards are payment instruments mapped to existing accounts
 * - This project does NOT handle KYC, onboarding, or account creation
 *
 * VENDOR NEUTRALITY:
 * This interface is vendor-agnostic. Implementations can integrate with:
 * - Apache Fineract (reference implementation)
 * - Temenos T24/Transact
 * - Mambu
 * - Oracle FLEXCUBE
 * - Proprietary core banking systems
 *
 * AUTHORIZATION HOLD PATTERN:
 * Card authorizations require a two-phase commit:
 * 1. placeHold() - Reserve funds during authorization (immediately)
 * 2. commitDebit() - Actually move funds during clearing (1-3 days later)
 * 3. releaseHold() - Cancel authorization without moving funds
 *
 * If the bank core doesn't natively support holds, implementers must
 * provide a workaround (e.g., shadow transactions, hold accounts).
 * See documentation for hold implementation patterns.
 */
public interface BankAccountAdapter {

    /**
     * Get the available balance for a bank account.
     *
     * This must return the real-time balance from the bank core,
     * accounting for any active holds or pending transactions.
     *
     * @param accountRef bank's account reference/ID
     * @return available balance
     * @throws BankCoreException if bank core is unavailable or account not found
     */
    Money getAvailableBalance(String accountRef);

    /**
     * Place an authorization hold on funds.
     *
     * This reserves funds for a pending card authorization.
     * The funds become unavailable but are not yet debited.
     *
     * Requirements:
     * - MUST be idempotent (same referenceId returns success if already held)
     * - MUST check sufficient funds before placing hold
     * - MUST be reversible via releaseHold()
     * - MUST NOT debit the account (that happens in commitDebit)
     *
     * Implementation Notes:
     * - If bank core supports native holds, use those
     * - Otherwise, implement workaround (shadow transactions, hold account, etc.)
     * - See documentation for hold implementation patterns
     *
     * @param accountRef bank's account reference/ID
     * @param amount amount to hold
     * @param referenceId unique reference for this authorization (idempotency key)
     * @throws InsufficientFundsException if insufficient funds
     * @throws BankCoreException if bank core operation fails
     */
    void placeHold(String accountRef, Money amount, String referenceId);

    /**
     * Commit a debit to the bank account (clearing/settlement).
     *
     * This actually moves funds out of the account after a previous authorization.
     * Called when a card transaction clears (typically 1-3 days after authorization).
     *
     * Requirements:
     * - MUST be idempotent (same referenceId returns success if already committed)
     * - MUST release any associated hold
     * - amount MAY be less than original hold (partial clearing)
     * - MUST record transaction in bank's ledger
     *
     * Flow:
     * 1. Release the authorization hold
     * 2. Debit the account for the final amount
     * 3. Ensure no double-debit if called multiple times
     *
     * @param accountRef bank's account reference/ID
     * @param amount amount to debit
     * @param referenceId reference to original authorization
     * @throws BankCoreException if bank core operation fails
     */
    void commitDebit(String accountRef, Money amount, String referenceId);

    /**
     * Release an authorization hold without debiting.
     *
     * Called when:
     * - Authorization expires without clearing
     * - Authorization is explicitly canceled
     * - Transaction is declined after initial approval
     *
     * Requirements:
     * - MUST be idempotent
     * - MUST restore funds to available balance
     * - Safe to call even if hold doesn't exist
     *
     * @param accountRef bank's account reference/ID
     * @param amount amount to release
     * @param referenceId reference to original authorization
     */
    void releaseHold(String accountRef, Money amount, String referenceId);

    /**
     * Get the name of this bank core adapter.
     * Used for logging and debugging.
     *
     * @return adapter name (e.g., "Fineract", "T24", "CustomCore")
     */
    String getAdapterName();

    /**
     * Health check for bank core connectivity.
     *
     * @return true if bank core is reachable and operational
     */
    boolean isHealthy();
}
