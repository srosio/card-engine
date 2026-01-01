package com.cardengine.accounts;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import com.cardengine.common.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public InternalLedgerAccount createInternalLedgerAccount(String ownerId, Money initialBalance) {
        InternalLedgerAccount account = new InternalLedgerAccount(ownerId, initialBalance);
        accountRepository.save(account);
        log.info("Created internal ledger account {} for owner {} with balance {} {}",
            account.getAccountId(), ownerId, initialBalance.getAmount(), initialBalance.getCurrency());
        return account;
    }

    @Transactional
    public MockFiatAccount createFiatAccount(String ownerId, Money initialBalance) {
        MockFiatAccount account = new MockFiatAccount(ownerId, initialBalance);
        accountRepository.save(account);
        log.info("Created fiat account {} for owner {} with balance {} {}",
            account.getAccountId(), ownerId, initialBalance.getAmount(), initialBalance.getCurrency());
        return account;
    }

    @Transactional
    public MockStablecoinAccount createStablecoinAccount(String ownerId, Money initialBalance) {
        MockStablecoinAccount account = new MockStablecoinAccount(ownerId, initialBalance);
        accountRepository.save(account);
        log.info("Created stablecoin account {} for owner {} with balance {} {}",
            account.getAccountId(), ownerId, initialBalance.getAmount(), initialBalance.getCurrency());
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public List<BaseAccount> getAccountsByOwner(String ownerId) {
        return accountRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public void deposit(String accountId, Money amount) {
        BaseAccount account = accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.deposit(amount);
        accountRepository.save(account);
        log.info("Deposited {} {} to account {}", amount.getAmount(), amount.getCurrency(), accountId);
    }
}
