package com.cardengine.authorization;

import com.cardengine.accounts.Account;
import com.cardengine.accounts.AccountService;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardService;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.exception.InsufficientFundsException;
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
 * Service for processing card authorizations.
 *
 * Authorization flow:
 * 1. Validate card state
 * 2. Run rules engine
 * 3. Reserve funds from backing account
 * 4. Record authorization in ledger
 * 5. Return approved/declined response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final CardService cardService;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final RulesEngine rulesEngine;
    private final AuthorizationRepository authorizationRepository;

    @Transactional
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        IdempotencyKey.validate(request.getIdempotencyKey());

        // Check for duplicate request
        Optional<Authorization> existing = authorizationRepository
            .findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate authorization request: {}", request.getAuthorizationId());
            return AuthorizationResponse.builder()
                .authorizationId(existing.get().getAuthorizationId())
                .status(existing.get().getStatus())
                .declineReason(existing.get().getDeclineReason())
                .build();
        }

        log.info("Processing authorization {} for card {} amount {} {}",
            request.getAuthorizationId(), request.getCardId(),
            request.getAmount().getAmount(), request.getAmount().getCurrency());

        try {
            // Step 1: Validate card state
            Card card = cardService.getCard(request.getCardId());
            validateCardState(card);

            // Step 2: Run rules engine
            RuleResult ruleResult = rulesEngine.evaluateRules(request);
            if (!ruleResult.isApproved()) {
                return declineAuthorization(request, card, ruleResult.getReason());
            }

            // Step 3: Reserve funds from backing account
            Account account = accountService.getAccount(card.getFundingAccountId());
            account.reserve(request.getAmount(), request.getAuthorizationId());

            // Step 4: Record in ledger
            ledgerService.recordAuthHold(
                account.getAccountId(),
                request.getCardId(),
                request.getAmount(),
                request.getAuthorizationId(),
                request.getIdempotencyKey()
            );

            // Step 5: Save authorization
            Authorization authorization = new Authorization(
                request.getAuthorizationId(),
                request.getCardId(),
                account.getAccountId(),
                request.getAmount(),
                AuthorizationStatus.APPROVED,
                request.getMerchantName(),
                request.getMerchantCategoryCode(),
                request.getMerchantCity(),
                request.getMerchantCountry(),
                request.getIdempotencyKey()
            );
            authorizationRepository.save(authorization);

            log.info("Authorization {} APPROVED", request.getAuthorizationId());
            return AuthorizationResponse.approved(request.getAuthorizationId());

        } catch (InsufficientFundsException e) {
            log.info("Authorization {} DECLINED: {}", request.getAuthorizationId(), e.getMessage());
            Card card = cardService.getCard(request.getCardId());
            return declineAuthorization(request, card, "Insufficient funds");

        } catch (TransactionDeclinedException e) {
            log.info("Authorization {} DECLINED: {}", request.getAuthorizationId(), e.getMessage());
            Card card = cardService.getCard(request.getCardId());
            return declineAuthorization(request, card, e.getDeclineReason());
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

    private AuthorizationResponse declineAuthorization(AuthorizationRequest request,
                                                       Card card, String reason) {
        Authorization authorization = new Authorization(
            request.getAuthorizationId(),
            request.getCardId(),
            card.getFundingAccountId(),
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

    @Transactional(readOnly = true)
    public Authorization getAuthorization(String authorizationId) {
        return authorizationRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException("Authorization not found: " + authorizationId));
    }
}
