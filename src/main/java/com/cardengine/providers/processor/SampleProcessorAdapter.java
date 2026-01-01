package com.cardengine.providers.processor;

import com.cardengine.authorization.*;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardRepository;
import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.providers.CardProcessorAdapter;
import com.cardengine.settlement.ClearingRequest;
import com.cardengine.settlement.ReversalRequest;
import com.cardengine.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Real card processor adapter for SampleProcessor.
 *
 * This adapter handles webhook callbacks from a real card processor
 * and translates them into internal orchestration calls.
 *
 * ADAPTER RESPONSIBILITIES:
 * - Translate processor payloads to internal format
 * - Map processor IDs to internal authorization IDs
 * - Forward calls to orchestration core
 * - Preserve idempotency keys
 *
 * NO BUSINESS LOGIC IN THIS ADAPTER.
 * All authorization rules, ledger logic, and account operations
 * are handled by the core orchestration layer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SampleProcessorAdapter implements CardProcessorAdapter {

    private final AuthorizationService authorizationService;
    private final SettlementService settlementService;
    private final CardRepository cardRepository;
    private final ProcessorTransactionMappingRepository mappingRepository;

    private static final String PROCESSOR_NAME = "SampleProcessor";

    /**
     * Handle authorization webhook from processor.
     *
     * Flow:
     * 1. Map card token to internal card ID
     * 2. Translate webhook to AuthorizationRequest
     * 3. Call core authorization service
     * 4. Store processor<->internal ID mapping
     * 5. Return processor-formatted response
     */
    public SampleProcessorWebhooks.ProcessorResponse handleAuthorizationWebhook(
            SampleProcessorWebhooks.AuthorizationWebhook webhook) {

        log.info("Received authorization webhook from SampleProcessor: processorTxnId={}, amount={} {}",
            webhook.getProcessorTransactionId(), webhook.getAmount(), webhook.getCurrency());

        try {
            // 1. Find card by token
            Card card = findCardByToken(webhook.getCardToken());

            // 2. Translate to internal authorization request
            String internalAuthId = java.util.UUID.randomUUID().toString();

            AuthorizationRequest request = AuthorizationRequest.builder()
                .authorizationId(internalAuthId)
                .cardId(card.getCardId())
                .amount(Money.of(webhook.getAmount(), Currency.valueOf(webhook.getCurrency())))
                .merchantName(webhook.getMerchant().getName())
                .merchantCategoryCode(webhook.getMerchant().getCategoryCode())
                .merchantCity(webhook.getMerchant().getCity())
                .merchantCountry(webhook.getMerchant().getCountry())
                .idempotencyKey(webhook.getIdempotencyKey())  // Preserve processor's idempotency key
                .build();

            // 3. Call core authorization (this does all the business logic)
            AuthorizationResponse response = authorizationService.authorize(request);

            // 4. Store mapping for future clearing/reversal webhooks
            if (response.getStatus() == AuthorizationStatus.APPROVED) {
                storeMapping(webhook.getProcessorTransactionId(), internalAuthId, webhook.getCardToken());
            }

            // 5. Translate response to processor format
            if (response.getStatus() == AuthorizationStatus.APPROVED) {
                log.info("Authorization APPROVED: processorTxnId={}, internalAuthId={}",
                    webhook.getProcessorTransactionId(), internalAuthId);
                return SampleProcessorWebhooks.ProcessorResponse.approved(internalAuthId);
            } else {
                log.info("Authorization DECLINED: processorTxnId={}, reason={}",
                    webhook.getProcessorTransactionId(), response.getDeclineReason());
                return SampleProcessorWebhooks.ProcessorResponse.declined(response.getDeclineReason());
            }

        } catch (Exception e) {
            log.error("Error processing authorization webhook: processorTxnId={}",
                webhook.getProcessorTransactionId(), e);
            return SampleProcessorWebhooks.ProcessorResponse.declined("System error: " + e.getMessage());
        }
    }

    /**
     * Handle clearing webhook from processor.
     *
     * Flow:
     * 1. Look up internal authorization ID from processor ID
     * 2. Translate to ClearingRequest
     * 3. Call settlement service (handles ledger and account commits)
     */
    public void handleClearingWebhook(SampleProcessorWebhooks.ClearingWebhook webhook) {

        log.info("Received clearing webhook from SampleProcessor: processorTxnId={}, settledAmount={} {}",
            webhook.getProcessorTransactionId(), webhook.getSettledAmount(), webhook.getCurrency());

        try {
            // 1. Look up internal authorization ID
            ProcessorTransactionMapping mapping = mappingRepository
                .findByProcessorTransactionId(webhook.getProcessorTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "No mapping found for processor transaction: " + webhook.getProcessorTransactionId()));

            // 2. Translate to clearing request
            ClearingRequest request = ClearingRequest.builder()
                .authorizationId(mapping.getInternalAuthorizationId())
                .clearingAmount(Money.of(webhook.getSettledAmount(), Currency.valueOf(webhook.getCurrency())))
                .idempotencyKey(webhook.getIdempotencyKey())  // Preserve idempotency
                .build();

            // 3. Call settlement service (does all the work)
            settlementService.clearTransaction(request);

            log.info("Clearing completed: processorTxnId={}, internalAuthId={}",
                webhook.getProcessorTransactionId(), mapping.getInternalAuthorizationId());

        } catch (Exception e) {
            log.error("Error processing clearing webhook: processorTxnId={}",
                webhook.getProcessorTransactionId(), e);
            throw e;
        }
    }

    /**
     * Handle reversal webhook from processor.
     *
     * Flow:
     * 1. Look up internal authorization ID
     * 2. Translate to ReversalRequest
     * 3. Call settlement service
     */
    public void handleReversalWebhook(SampleProcessorWebhooks.ReversalWebhook webhook) {

        log.info("Received reversal webhook from SampleProcessor: processorTxnId={}, amount={} {}, reason={}",
            webhook.getProcessorTransactionId(), webhook.getReversalAmount(),
            webhook.getCurrency(), webhook.getReason());

        try {
            // 1. Look up internal authorization ID
            ProcessorTransactionMapping mapping = mappingRepository
                .findByProcessorTransactionId(webhook.getProcessorTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "No mapping found for processor transaction: " + webhook.getProcessorTransactionId()));

            // 2. Translate to reversal request
            ReversalRequest request = ReversalRequest.builder()
                .authorizationId(mapping.getInternalAuthorizationId())
                .reversalAmount(Money.of(webhook.getReversalAmount(), Currency.valueOf(webhook.getCurrency())))
                .idempotencyKey(webhook.getIdempotencyKey())
                .build();

            // 3. Call settlement service
            settlementService.reverseTransaction(request);

            log.info("Reversal completed: processorTxnId={}, internalAuthId={}",
                webhook.getProcessorTransactionId(), mapping.getInternalAuthorizationId());

        } catch (Exception e) {
            log.error("Error processing reversal webhook: processorTxnId={}",
                webhook.getProcessorTransactionId(), e);
            throw e;
        }
    }

    /**
     * Send authorization response back to processor.
     * This is called by core orchestration when needed.
     */
    @Override
    public void sendAuthorizationResponse(AuthorizationResponse response) {
        // In a real implementation, this would make an HTTP callback to the processor
        log.info("SampleProcessor: Would send authorization response: authId={}, status={}",
            response.getAuthorizationId(), response.getStatus());
    }

    @Override
    public String getProcessorName() {
        return PROCESSOR_NAME;
    }

    /**
     * Map card token to internal card ID.
     * In production, card tokens would be stored in the cards table.
     */
    private Card findCardByToken(String cardToken) {
        // For this implementation, we'll use last4 as a simple token
        // In production, this would be a proper tokenization system
        return cardRepository.findAll().stream()
            .filter(card -> card.getLast4().equals(cardToken))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Card not found for token: " + cardToken));
    }

    /**
     * Store mapping between processor ID and internal authorization ID.
     */
    private void storeMapping(String processorTxnId, String internalAuthId, String cardToken) {
        // Check if mapping already exists (idempotency)
        Optional<ProcessorTransactionMapping> existing =
            mappingRepository.findByProcessorTransactionId(processorTxnId);

        if (existing.isEmpty()) {
            ProcessorTransactionMapping mapping = new ProcessorTransactionMapping(
                processorTxnId,
                internalAuthId,
                cardToken,
                PROCESSOR_NAME
            );
            mappingRepository.save(mapping);
            log.debug("Stored processor mapping: {} -> {}", processorTxnId, internalAuthId);
        }
    }
}
