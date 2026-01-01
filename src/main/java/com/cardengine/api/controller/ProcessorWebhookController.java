package com.cardengine.api.controller;

import com.cardengine.providers.processor.SampleProcessorAdapter;
import com.cardengine.providers.processor.SampleProcessorWebhooks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoints for card processor callbacks.
 *
 * These endpoints receive real-time notifications from card processors
 * when transactions occur (authorizations, clearings, reversals).
 *
 * In production, these endpoints should be:
 * - Behind HTTPS
 * - Authenticated with processor-specific signatures
 * - Rate limited
 * - Monitored for failures
 */
@RestController
@RequestMapping("/api/v1/webhooks/processor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Processor Webhooks", description = "Card processor webhook callbacks")
public class ProcessorWebhookController {

    private final SampleProcessorAdapter processorAdapter;

    /**
     * Receive authorization webhook from SampleProcessor.
     *
     * Called in real-time when a cardholder swipes/taps their card.
     * Must respond quickly (typically <500ms for card-present, <3s for card-not-present).
     */
    @PostMapping("/sample/authorize")
    @Operation(summary = "Authorization webhook from SampleProcessor")
    public ResponseEntity<SampleProcessorWebhooks.ProcessorResponse> handleAuthorization(
            @RequestBody SampleProcessorWebhooks.AuthorizationWebhook webhook) {

        log.info("Webhook received: Authorization - processorTxnId={}", webhook.getProcessorTransactionId());

        try {
            SampleProcessorWebhooks.ProcessorResponse response =
                processorAdapter.handleAuthorizationWebhook(webhook);

            // Return 200 OK with approval/decline
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing authorization webhook", e);

            // Return decline on error (safe default)
            return ResponseEntity.ok(
                SampleProcessorWebhooks.ProcessorResponse.declined("System error")
            );
        }
    }

    /**
     * Receive clearing webhook from SampleProcessor.
     *
     * Called when a transaction settles (typically 1-3 days after authorization).
     * Not time-critical - can be processed asynchronously if needed.
     */
    @PostMapping("/sample/clear")
    @Operation(summary = "Clearing webhook from SampleProcessor")
    public ResponseEntity<Void> handleClearing(
            @RequestBody SampleProcessorWebhooks.ClearingWebhook webhook) {

        log.info("Webhook received: Clearing - processorTxnId={}", webhook.getProcessorTransactionId());

        try {
            processorAdapter.handleClearingWebhook(webhook);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing clearing webhook", e);
            // Return 500 so processor retries
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive reversal webhook from SampleProcessor.
     *
     * Called when a transaction is refunded or cancelled.
     */
    @PostMapping("/sample/reverse")
    @Operation(summary = "Reversal webhook from SampleProcessor")
    public ResponseEntity<Void> handleReversal(
            @RequestBody SampleProcessorWebhooks.ReversalWebhook webhook) {

        log.info("Webhook received: Reversal - processorTxnId={}", webhook.getProcessorTransactionId());

        try {
            processorAdapter.handleReversalWebhook(webhook);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing reversal webhook", e);
            // Return 500 so processor retries
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
