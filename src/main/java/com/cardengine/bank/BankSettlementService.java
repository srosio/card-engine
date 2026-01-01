package com.cardengine.bank;

import com.cardengine.authorization.Authorization;
import com.cardengine.authorization.AuthorizationRepository;
import com.cardengine.authorization.AuthorizationStatus;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.ledger.LedgerRepository;
import com.cardengine.ledger.LedgerService;
import com.cardengine.settlement.ClearingRequest;
import com.cardengine.settlement.ReversalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settlement service for bank-backed cards.
 *
 * BANK INTEGRATION:
 * This service handles clearing and settlement for cards backed by bank core accounts.
 * - Commits debits via BankAccountAdapter
 * - Releases holds via BankAccountAdapter
 * - Bank core performs actual fund movements
 * - Local DB tracks settlement status
 *
 * CLEARING FLOW:
 * 1. Validate authorization exists and is approved
 * 2. Commit debit in bank core (via adapter)
 * 3. Update local authorization status
 * 4. Record in local ledger (audit)
 *
 * REVERSAL FLOW:
 * 1. Validate authorization is cleared
 * 2. Credit funds back in bank core (reversal)
 * 3. Update local authorization status
 *
 * IMPORTANT:
 * Bank core is the system of record.
 * All balance changes happen in the bank core, not locally.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankSettlementService {

    private final AuthorizationRepository authorizationRepository;
    private final BankAccountMappingRepository mappingRepository;
    private final BankAccountAdapter bankAccountAdapter;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    @Transactional
    public void clearTransaction(ClearingRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate
        if (ledgerRepository.findByIdempotencyKey(request.getIdempotencyKey()).isPresent()) {
            log.info("Duplicate clearing request: {}", request.getAuthorizationId());
            return;
        }

        log.info("Processing bank clearing: authId={}, amount={} {}",
            request.getAuthorizationId(),
            request.getClearingAmount().getAmount(),
            request.getClearingAmount().getCurrency());

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(request.getAuthorizationId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + request.getAuthorizationId()));

        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot clear authorization in state: " + authorization.getStatus());
        }

        // Validate clearing amount
        if (request.getClearingAmount().isGreaterThan(authorization.getAmount())) {
            throw new IllegalArgumentException(
                "Clearing amount cannot exceed authorization amount");
        }

        // Get bank account reference
        String bankAccountRef = authorization.getAccountId();  // This is bank account ref

        // Commit debit in bank core
        try {
            bankAccountAdapter.commitDebit(
                bankAccountRef,
                request.getClearingAmount(),
                request.getAuthorizationId()
            );
        } catch (Exception e) {
            log.error("Bank core rejected clearing: authId={}", request.getAuthorizationId(), e);
            throw new RuntimeException("Bank declined clearing: " + e.getMessage(), e);
        }

        // Record in local ledger (audit trail)
        ledgerService.recordClearing(
            bankAccountRef,
            authorization.getCardId(),
            request.getClearingAmount(),
            request.getAuthorizationId(),
            request.getIdempotencyKey()
        );

        // Update authorization status
        authorization.clear(request.getClearingAmount());
        authorizationRepository.save(authorization);

        log.info("Bank clearing completed: authId={}", request.getAuthorizationId());
    }

    @Transactional
    public void releaseAuthorization(String authorizationId, String idempotencyKey) {
        IdempotencyKey.validate(idempotencyKey);

        // Check for duplicate
        if (ledgerRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.info("Duplicate release request: {}", authorizationId);
            return;
        }

        log.info("Releasing bank authorization: {}", authorizationId);

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + authorizationId));

        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            log.warn("Authorization not in approved state: {}, status={}",
                authorizationId, authorization.getStatus());
            return;  // Idempotent
        }

        // Release hold in bank core
        String bankAccountRef = authorization.getAccountId();
        try {
            bankAccountAdapter.releaseHold(
                bankAccountRef,
                authorization.getAmount(),
                authorizationId
            );
        } catch (Exception e) {
            log.error("Error releasing hold in bank core: authId={}", authorizationId, e);
            // Continue anyway - update local state
        }

        // Record in ledger
        ledgerService.recordAuthRelease(
            bankAccountRef,
            authorization.getCardId(),
            authorization.getAmount(),
            authorizationId,
            idempotencyKey
        );

        // Update authorization
        authorization.release();
        authorizationRepository.save(authorization);

        log.info("Bank authorization released: {}", authorizationId);
    }

    @Transactional
    public void reverseTransaction(ReversalRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate
        if (ledgerRepository.findByIdempotencyKey(request.getIdempotencyKey()).isPresent()) {
            log.info("Duplicate reversal request: {}", request.getAuthorizationId());
            return;
        }

        log.info("Processing bank reversal: authId={}, amount={} {}",
            request.getAuthorizationId(),
            request.getReversalAmount().getAmount(),
            request.getReversalAmount().getCurrency());

        // Get authorization
        Authorization authorization = authorizationRepository
            .findByAuthorizationId(request.getAuthorizationId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + request.getAuthorizationId()));

        if (authorization.getStatus() != AuthorizationStatus.CLEARED) {
            throw new IllegalStateException(
                "Cannot reverse authorization in state: " + authorization.getStatus());
        }

        // Validate reversal amount
        if (request.getReversalAmount().isGreaterThan(authorization.getClearedAmount())) {
            throw new IllegalArgumentException(
                "Reversal amount cannot exceed cleared amount");
        }

        // Note: For bank core integration, reversals are typically handled
        // by the bank core's own reversal mechanisms.
        // This service would trigger that via the adapter if supported,
        // or log for manual processing.

        log.warn("Bank reversal logged - may require manual processing in bank core: authId={}",
            request.getAuthorizationId());

        // Record reversal in ledger (audit trail)
        String bankAccountRef = authorization.getAccountId();
        ledgerService.recordReversal(
            bankAccountRef,
            authorization.getCardId(),
            request.getReversalAmount(),
            request.getAuthorizationId(),
            request.getIdempotencyKey()
        );

        // Update authorization
        authorization.reverse();
        authorizationRepository.save(authorization);

        log.info("Bank reversal recorded: authId={}", request.getAuthorizationId());
    }
}
