package com.cardengine.bank;

import com.cardengine.authorization.*;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardRepository;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.exception.TransactionDeclinedException;
import com.cardengine.ledger.LedgerService;
import com.cardengine.rules.RuleResult;
import com.cardengine.rules.RulesEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Authorization service for bank-backed cards.
 *
 * BANK INTEGRATION:
 * This service handles authorization for cards backed by bank core accounts.
 * Unlike the original AuthorizationService which worked with internal accounts,
 * this service:
 * - Checks balance in the external bank core (source of truth)
 * - Places holds via BankAccountAdapter
 * - Never maintains local balances
 * - Records authorization in local DB for tracking
 *
 * AUTHORIZATION FLOW:
 * 1. Validate card state (active, not expired)
 * 2. Run rules engine (limits, MCC blocking, velocity)
 * 3. Get bank account reference from mapping
 * 4. Check balance in bank core via adapter
 * 5. Place hold in bank core
 * 6. Record authorization locally
 * 7. Return APPROVED or DECLINED
 *
 * IMPORTANT:
 * The bank core is the authoritative system.
 * Local database only tracks authorization status for correlation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankAuthorizationService {

    private final CardRepository cardRepository;
    private final BankAccountMappingRepository mappingRepository;
    private final BankAccountAdapter bankAccountAdapter;
    private final RulesEngine rulesEngine;
    private final AuthorizationRepository authorizationRepository;
    private final LedgerService ledgerService;

    @Transactional
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate request
        Optional<Authorization> existing = authorizationRepository
            .findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate authorization request: {}", request.getAuthorizationId());
            return buildResponse(existing.get());
        }

        log.info("Processing bank authorization: authId={}, cardId={}, amount={} {}",
            request.getAuthorizationId(), request.getCardId(),
            request.getAmount().getAmount(), request.getAmount().getCurrency());

        try {
            // Step 1: Validate card
            Card card = cardRepository.findByCardId(request.getCardId())
                .orElseThrow(() -> new TransactionDeclinedException("Card not found"));

            validateCardState(card);

            // Step 2: Get bank account mapping
            BankAccountMapping mapping = mappingRepository.findByCardId(card.getCardId())
                .orElseThrow(() -> new TransactionDeclinedException(
                    "No bank account linked to card"));

            // Step 3: Run rules engine
            RuleResult ruleResult = rulesEngine.evaluateRules(request);
            if (!ruleResult.isApproved()) {
                return declineAuthorization(request, mapping, ruleResult.getReason());
            }

            // Step 4: Place hold in bank core
            try {
                bankAccountAdapter.placeHold(
                    mapping.getBankAccountRef(),
                    request.getAmount(),
                    request.getAuthorizationId()
                );
            } catch (Exception e) {
                log.error("Bank core rejected hold: authId={}", request.getAuthorizationId(), e);
                return declineAuthorization(request, mapping,
                    "Bank declined: " + e.getMessage());
            }

            // Step 5: Record authorization locally (for tracking only)
            Authorization authorization = new Authorization(
                request.getAuthorizationId(),
                request.getCardId(),
                mapping.getBankAccountRef(),  // Bank account ref, not internal ID
                request.getAmount(),
                AuthorizationStatus.APPROVED,
                request.getMerchantName(),
                request.getMerchantCategoryCode(),
                request.getMerchantCity(),
                request.getMerchantCountry(),
                request.getIdempotencyKey()
            );
            authorizationRepository.save(authorization);

            // Step 6: Record in local ledger (audit trail, not balance tracking)
            ledgerService.recordAuthHold(
                mapping.getBankAccountRef(),
                request.getCardId(),
                request.getAmount(),
                request.getAuthorizationId(),
                request.getIdempotencyKey()
            );

            log.info("Bank authorization APPROVED: authId={}", request.getAuthorizationId());
            return AuthorizationResponse.approved(request.getAuthorizationId());

        } catch (TransactionDeclinedException e) {
            log.info("Authorization DECLINED: authId={}, reason={}",
                request.getAuthorizationId(), e.getDeclineReason());
            // Try to get mapping for decline recording
            Optional<BankAccountMapping> mapping = mappingRepository.findByCardId(request.getCardId());
            return declineAuthorization(request,
                mapping.orElse(null),
                e.getDeclineReason());
        }
    }

    private void validateCardState(Card card) {
        if (!card.isActive()) {
            throw new TransactionDeclinedException("Card is not active: " + card.getState());
        }
        if (card.isExpired()) {
            throw new TransactionDeclinedException("Card is expired");
        }
    }

    private AuthorizationResponse declineAuthorization(
            AuthorizationRequest request,
            BankAccountMapping mapping,
            String reason) {

        String accountRef = mapping != null ? mapping.getBankAccountRef() : "UNKNOWN";

        Authorization authorization = new Authorization(
            request.getAuthorizationId(),
            request.getCardId(),
            accountRef,
            request.getAmount(),
            AuthorizationStatus.DECLINED,
            request.getMerchantName(),
            request.getMerchantCategoryCode(),
            request.getMerchantCity(),
            request.getMerchantCountry(),
            request.getIdempotencyKey()
        );
        authorization.decline(reason);
        authorizationRepository.save(authorization);

        return AuthorizationResponse.declined(request.getAuthorizationId(), reason);
    }

    private AuthorizationResponse buildResponse(Authorization auth) {
        if (auth.getStatus() == AuthorizationStatus.APPROVED) {
            return AuthorizationResponse.approved(auth.getAuthorizationId());
        } else {
            return AuthorizationResponse.declined(
                auth.getAuthorizationId(),
                auth.getDeclineReason()
            );
        }
    }

    @Transactional(readOnly = true)
    public Authorization getAuthorization(String authorizationId) {
        return authorizationRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + authorizationId));
    }
}
