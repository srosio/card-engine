package com.cardengine.settlement;

import com.cardengine.accounts.Account;
import com.cardengine.accounts.AccountRepository;
import com.cardengine.accounts.BaseAccount;
import com.cardengine.authorization.Authorization;
import com.cardengine.authorization.AuthorizationRepository;
import com.cardengine.authorization.AuthorizationStatus;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.common.exception.AccountNotFoundException;
import com.cardengine.ledger.LedgerEntry;
import com.cardengine.ledger.LedgerRepository;
import com.cardengine.ledger.LedgerService;
import com.cardengine.ledger.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for processing settlement operations (clearing and reversals).
 *
 * Settlement flow:
 * 1. Validate authorization exists and is in correct state
 * 2. Commit funds from account (for clearing) or credit funds back (for reversal)
 * 3. Record in ledger
 * 4. Update authorization status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final AuthorizationRepository authorizationRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    @Transactional
    public void clearTransaction(ClearingRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate
        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate clearing request for authorization {}", request.getAuthorizationId());
            return;
        }

        log.info("Processing clearing for authorization {} amount {} {}",
            request.getAuthorizationId(),
            request.getClearingAmount().getAmount(),
            request.getClearingAmount().getCurrency());

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(request.getAuthorizationId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + request.getAuthorizationId()));

        // Validate authorization state
        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot clear authorization in state: " + authorization.getStatus());
        }

        // Validate clearing amount
        if (request.getClearingAmount().isGreaterThan(authorization.getAmount())) {
            throw new IllegalArgumentException(
                "Clearing amount cannot exceed authorization amount");
        }

        // Get account
        BaseAccount account = accountRepository.findByAccountId(authorization.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(authorization.getAccountId()));

        // Commit funds (moves money out of account)
        account.commit(request.getClearingAmount(), request.getAuthorizationId());
        accountRepository.save(account);

        // Record in ledger
        ledgerService.recordClearing(
            authorization.getAccountId(),
            authorization.getCardId(),
            request.getClearingAmount(),
            request.getAuthorizationId(),
            request.getIdempotencyKey()
        );

        // Update authorization
        authorization.clear(request.getClearingAmount());
        authorizationRepository.save(authorization);

        log.info("Cleared authorization {} for {} {}",
            request.getAuthorizationId(),
            request.getClearingAmount().getAmount(),
            request.getClearingAmount().getCurrency());
    }

    @Transactional
    public void releaseAuthorization(String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        // Check for duplicate
        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate release request for authorization {}", authorizationId);
            return;
        }

        log.info("Releasing authorization {}", authorizationId);

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + authorizationId));

        // Validate authorization state
        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot release authorization in state: " + authorization.getStatus());
        }

        // Get account and release funds
        BaseAccount account = accountRepository.findByAccountId(authorization.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(authorization.getAccountId()));

        account.release(authorization.getAmount(), authorizationId);
        accountRepository.save(account);

        // Record in ledger
        ledgerService.recordAuthRelease(
            authorization.getAccountId(),
            authorization.getCardId(),
            authorization.getAmount(),
            authorizationId,
            idempotencyKey
        );

        // Update authorization
        authorization.release();
        authorizationRepository.save(authorization);

        log.info("Released authorization {}", authorizationId);
    }

    @Transactional
    public void reverseTransaction(ReversalRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate
        Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate reversal request for authorization {}", request.getAuthorizationId());
            return;
        }

        log.info("Processing reversal for authorization {} amount {} {}",
            request.getAuthorizationId(),
            request.getReversalAmount().getAmount(),
            request.getReversalAmount().getCurrency());

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(request.getAuthorizationId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + request.getAuthorizationId()));

        // Validate authorization state
        if (authorization.getStatus() != AuthorizationStatus.CLEARED) {
            throw new IllegalStateException(
                "Cannot reverse authorization in state: " + authorization.getStatus());
        }

        // Validate reversal amount
        if (request.getReversalAmount().isGreaterThan(authorization.getClearedAmount())) {
            throw new IllegalArgumentException(
                "Reversal amount cannot exceed cleared amount");
        }

        // Get account and credit funds back
        BaseAccount account = accountRepository.findByAccountId(authorization.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(authorization.getAccountId()));

        account.deposit(request.getReversalAmount());
        accountRepository.save(account);

        // Record in ledger
        ledgerService.recordReversal(
            authorization.getAccountId(),
            authorization.getCardId(),
            request.getReversalAmount(),
            request.getAuthorizationId(),
            request.getIdempotencyKey()
        );

        // Update authorization
        authorization.reverse();
        authorizationRepository.save(authorization);

        log.info("Reversed authorization {} for {} {}",
            request.getAuthorizationId(),
            request.getReversalAmount().getAmount(),
            request.getReversalAmount().getCurrency());
    }
}
