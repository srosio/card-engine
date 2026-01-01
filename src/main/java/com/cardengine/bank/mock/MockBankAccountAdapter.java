package com.cardengine.bank.mock;

import com.cardengine.bank.BankAccountAdapter;
import com.cardengine.bank.BankCoreException;
import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import com.cardengine.common.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock bank account adapter for testing and development.
 *
 * This adapter simulates a bank core system for testing purposes.
 * It maintains in-memory state and can be used without external dependencies.
 *
 * USE CASES:
 * - Unit and integration testing
 * - Development without real bank core
 * - Demonstrations and POCs
 *
 * NOT FOR PRODUCTION:
 * This is a test double, not a real integration.
 * Production systems must use real bank core adapters.
 */
@Slf4j
public class MockBankAccountAdapter implements BankAccountAdapter {

    // In-memory account balances (accountRef → balance)
    private final Map<String, BigDecimal> accountBalances = new ConcurrentHashMap<>();

    // Active holds (referenceId → HoldInfo)
    private final Map<String, HoldInfo> activeHolds = new ConcurrentHashMap<>();

    // Currency for all accounts (simplified for mock)
    private final Currency defaultCurrency;

    // Simulate health status
    private boolean healthy = true;

    public MockBankAccountAdapter() {
        this(Currency.USD);
    }

    public MockBankAccountAdapter(Currency defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    /**
     * Create a mock account with initial balance.
     * Helper method for testing.
     */
    public void createAccount(String accountRef, BigDecimal initialBalance) {
        accountBalances.put(accountRef, initialBalance);
        log.debug("Mock account created: {} with balance {}", accountRef, initialBalance);
    }

    @Override
    public Money getAvailableBalance(String accountRef) {
        log.debug("Mock: Getting balance for account {}", accountRef);

        if (!accountBalances.containsKey(accountRef)) {
            throw new BankCoreException(
                "Account not found in mock bank",
                accountRef,
                "getAvailableBalance"
            );
        }

        // Calculate available = balance - active holds for this account
        BigDecimal balance = accountBalances.get(accountRef);
        BigDecimal heldAmount = calculateHeldAmount(accountRef);
        BigDecimal available = balance.subtract(heldAmount);

        return Money.of(available, defaultCurrency);
    }

    @Override
    public void placeHold(String accountRef, Money amount, String referenceId) {
        log.info("Mock: Placing hold on account {}, amount {} {}, ref={}",
            accountRef, amount.getAmount(), amount.getCurrency(), referenceId);

        // Idempotency check
        if (activeHolds.containsKey(referenceId)) {
            log.debug("Hold already exists: {}", referenceId);
            return;
        }

        // Check sufficient funds
        Money available = getAvailableBalance(accountRef);
        if (available.isLessThan(amount)) {
            throw new InsufficientFundsException(accountRef, amount, available);
        }

        // Place hold
        activeHolds.put(referenceId, new HoldInfo(
            accountRef,
            amount.getAmount(),
            amount.getCurrency()
        ));

        log.debug("Hold placed: {}", referenceId);
    }

    @Override
    public void commitDebit(String accountRef, Money amount, String referenceId) {
        log.info("Mock: Committing debit on account {}, amount {} {}, ref={}",
            accountRef, amount.getAmount(), amount.getCurrency(), referenceId);

        HoldInfo hold = activeHolds.get(referenceId);
        if (hold == null) {
            throw new IllegalStateException("No hold found for reference: " + referenceId);
        }

        // Validate amount
        if (amount.getAmount().compareTo(hold.amount) > 0) {
            throw new IllegalArgumentException("Cannot commit more than held amount");
        }

        // Release hold
        activeHolds.remove(referenceId);

        // Debit account
        BigDecimal currentBalance = accountBalances.get(accountRef);
        BigDecimal newBalance = currentBalance.subtract(amount.getAmount());
        accountBalances.put(accountRef, newBalance);

        log.debug("Debit committed: {}, new balance: {}", referenceId, newBalance);
    }

    @Override
    public void releaseHold(String accountRef, Money amount, String referenceId) {
        log.info("Mock: Releasing hold on account {}, ref={}", accountRef, referenceId);

        // Idempotent - safe to call even if hold doesn't exist
        activeHolds.remove(referenceId);

        log.debug("Hold released: {}", referenceId);
    }

    @Override
    public String getAdapterName() {
        return "MockBank";
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * Calculate total amount held for an account.
     */
    private BigDecimal calculateHeldAmount(String accountRef) {
        return activeHolds.values().stream()
            .filter(hold -> hold.accountRef.equals(accountRef))
            .map(hold -> hold.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Set health status (for testing failure scenarios).
     */
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    /**
     * Get all active hold references (for testing).
     */
    public Set<String> getActiveHoldReferences() {
        return activeHolds.keySet();
    }

    /**
     * Clear all state (for test cleanup).
     */
    public void reset() {
        accountBalances.clear();
        activeHolds.clear();
        healthy = true;
    }

    /**
     * Internal class to track hold information.
     */
    private static class HoldInfo {
        final String accountRef;
        final BigDecimal amount;
        final Currency currency;

        HoldInfo(String accountRef, BigDecimal amount, Currency currency) {
            this.accountRef = accountRef;
            this.amount = amount;
            this.currency = currency;
        }
    }
}
