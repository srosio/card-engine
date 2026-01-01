package com.cardengine.accounts;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for account abstraction layer.
 * Verifies that different account types work interchangeably.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountAbstractionTest {

    @Autowired
    private AccountService accountService;

    @Test
    void testInternalLedgerAccount() {
        Account account = accountService.createInternalLedgerAccount(
            "owner-1",
            Money.of("500.00", Currency.USD)
        );

        assertEquals(AccountType.INTERNAL_LEDGER, account.getAccountType());
        assertEquals(0, Money.of("500.00", Currency.USD).getAmount()
            .compareTo(account.getBalance().getAmount()));

        // Test reserve-commit-release
        testAccountOperations(account);
    }

    @Test
    void testFiatAccount() {
        Account account = accountService.createFiatAccount(
            "owner-2",
            Money.of("500.00", Currency.USD)
        );

        assertEquals(AccountType.FIAT_WALLET, account.getAccountType());
        testAccountOperations(account);
    }

    @Test
    void testStablecoinAccount() {
        Account account = accountService.createStablecoinAccount(
            "owner-3",
            Money.of("500.00", Currency.USDC)
        );

        assertEquals(AccountType.STABLECOIN, account.getAccountType());
        assertEquals(Currency.USDC, account.getCurrency());
        testAccountOperations(account);
    }

    private void testAccountOperations(Account account) {
        String authId = "test-auth-123";
        Money amount = Money.of("100.00", account.getCurrency());

        // Reserve
        account.reserve(amount, authId);
        Money afterReserve = account.getBalance();
        Money expectedAfterReserve = account.getTotalBalance().subtract(amount);
        assertEquals(0, expectedAfterReserve.getAmount().compareTo(afterReserve.getAmount()));

        // Commit
        account.commit(amount, authId);
        Money afterCommit = account.getTotalBalance();
        Money expectedAfterCommit = Money.of("400.00", account.getCurrency());
        assertEquals(0, expectedAfterCommit.getAmount().compareTo(afterCommit.getAmount()));
    }
}
