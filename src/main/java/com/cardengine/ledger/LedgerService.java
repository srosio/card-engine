package com.cardengine.ledger;

import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing the double-entry ledger.
 *
 * All financial operations in the card engine are recorded as immutable
 * ledger entries following double-entry accounting principles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    /**
     * Record an authorization hold.
     * This creates ledger entries but does not move funds yet.
     */
    @Transactional
    public String recordAuthHold(String accountId, String cardId, Money amount,
                                 String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        // Check for duplicate
        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate auth hold request with idempotency key {}", idempotencyKey);
            return existing.get().getTransactionId();
        }

        String transactionId = UUID.randomUUID().toString();

        // For auth holds, we record the reservation but don't move funds
        // In a full double-entry system, this might debit a "reserved funds" account
        LedgerEntry entry = new LedgerEntry(
            transactionId,
            accountId,
            LedgerEntry.EntryType.DEBIT,
            amount,
            TransactionType.AUTH_HOLD,
            authorizationId,
            cardId,
            "Authorization hold",
            idempotencyKey
        );

        ledgerRepository.save(entry);

        log.info("Recorded AUTH_HOLD: txn={}, auth={}, amount={} {}",
            transactionId, authorizationId, amount.getAmount(), amount.getCurrency());

        return transactionId;
    }

    /**
     * Record an authorization release.
     * This happens when an authorization expires or is canceled.
     */
    @Transactional
    public String recordAuthRelease(String accountId, String cardId, Money amount,
                                    String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate auth release request with idempotency key {}", idempotencyKey);
            return existing.get().getTransactionId();
        }

        String transactionId = UUID.randomUUID().toString();

        LedgerEntry entry = new LedgerEntry(
            transactionId,
            accountId,
            LedgerEntry.EntryType.CREDIT,
            amount,
            TransactionType.AUTH_RELEASE,
            authorizationId,
            cardId,
            "Authorization release",
            idempotencyKey
        );

        ledgerRepository.save(entry);

        log.info("Recorded AUTH_RELEASE: txn={}, auth={}, amount={} {}",
            transactionId, authorizationId, amount.getAmount(), amount.getCurrency());

        return transactionId;
    }

    /**
     * Record clearing/settlement of a previously authorized transaction.
     * This actually moves the funds.
     */
    @Transactional
    public String recordClearing(String accountId, String cardId, Money amount,
                                 String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate clearing request with idempotency key {}", idempotencyKey);
            return existing.get().getTransactionId();
        }

        String transactionId = UUID.randomUUID().toString();

        // Debit the account (money out)
        LedgerEntry debit = new LedgerEntry(
            transactionId,
            accountId,
            LedgerEntry.EntryType.DEBIT,
            amount,
            TransactionType.CLEARING_COMMIT,
            authorizationId,
            cardId,
            "Clearing settlement",
            idempotencyKey
        );

        // Credit would go to merchant account (not implemented in MVP)
        // In production, there would be a corresponding credit entry

        ledgerRepository.save(debit);

        log.info("Recorded CLEARING_COMMIT: txn={}, auth={}, amount={} {}",
            transactionId, authorizationId, amount.getAmount(), amount.getCurrency());

        return transactionId;
    }

    /**
     * Record a reversal (refund) of a previously cleared transaction.
     */
    @Transactional
    public String recordReversal(String accountId, String cardId, Money amount,
                                 String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate reversal request with idempotency key {}", idempotencyKey);
            return existing.get().getTransactionId();
        }

        String transactionId = UUID.randomUUID().toString();

        // Credit the account (money back)
        LedgerEntry credit = new LedgerEntry(
            transactionId,
            accountId,
            LedgerEntry.EntryType.CREDIT,
            amount,
            TransactionType.REVERSAL,
            authorizationId,
            cardId,
            "Transaction reversal",
            idempotencyKey
        );

        ledgerRepository.save(credit);

        log.info("Recorded REVERSAL: txn={}, auth={}, amount={} {}",
            transactionId, authorizationId, amount.getAmount(), amount.getCurrency());

        return transactionId;
    }

    /**
     * Record a deposit to an account.
     */
    @Transactional
    public String recordDeposit(String accountId, Money amount, String description,
                                String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate deposit request with idempotency key {}", idempotencyKey);
            return existing.get().getTransactionId();
        }

        String transactionId = UUID.randomUUID().toString();

        LedgerEntry credit = new LedgerEntry(
            transactionId,
            accountId,
            LedgerEntry.EntryType.CREDIT,
            amount,
            TransactionType.DEPOSIT,
            null,
            null,
            description,
            idempotencyKey
        );

        ledgerRepository.save(credit);

        log.info("Recorded DEPOSIT: txn={}, account={}, amount={} {}",
            transactionId, accountId, amount.getAmount(), amount.getCurrency());

        return transactionId;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountLedger(String accountId) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAuthorizationLedger(String authorizationId) {
        return ledgerRepository.findByAuthorizationId(authorizationId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getCardLedger(String cardId) {
        return ledgerRepository.findByCardId(cardId);
    }
}
