package com.cardengine.providers.processor;

import com.cardengine.authorization.AuthorizationResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Webhook payload DTOs for SampleProcessor.
 * These represent real-world webhook formats from card processors.
 */
public class SampleProcessorWebhooks {

    /**
     * Authorization webhook payload.
     * Sent when a cardholder attempts a transaction.
     */
    @Data
    public static class AuthorizationWebhook {
        private String processorTransactionId;  // Processor's unique ID
        private String cardToken;               // Tokenized card identifier
        private BigDecimal amount;
        private String currency;
        private MerchantInfo merchant;
        private Instant timestamp;
        private String idempotencyKey;

        @Data
        public static class MerchantInfo {
            private String name;
            private String categoryCode;  // MCC
            private String city;
            private String country;
        }
    }

    /**
     * Clearing webhook payload.
     * Sent when a transaction settles (usually 1-3 days after authorization).
     */
    @Data
    public static class ClearingWebhook {
        private String processorTransactionId;  // Links to original authorization
        private String authorizationId;          // Processor's auth reference
        private BigDecimal settledAmount;        // May differ from auth amount
        private String currency;
        private Instant settlementDate;
        private String idempotencyKey;
    }

    /**
     * Reversal webhook payload.
     * Sent when a transaction is refunded or cancelled.
     */
    @Data
    public static class ReversalWebhook {
        private String processorTransactionId;
        private String originalAuthorizationId;
        private BigDecimal reversalAmount;
        private String currency;
        private String reason;
        private Instant timestamp;
        private String idempotencyKey;
    }

    /**
     * Response format expected by SampleProcessor.
     */
    @Data
    public static class ProcessorResponse {
        private String status;  // "APPROVED" or "DECLINED"
        private String authorizationCode;
        private String declineReason;
        private Instant responseTime;

        public static ProcessorResponse approved(String authCode) {
            ProcessorResponse response = new ProcessorResponse();
            response.setStatus("APPROVED");
            response.setAuthorizationCode(authCode);
            response.setResponseTime(Instant.now());
            return response;
        }

        public static ProcessorResponse declined(String reason) {
            ProcessorResponse response = new ProcessorResponse();
            response.setStatus("DECLINED");
            response.setDeclineReason(reason);
            response.setResponseTime(Instant.now());
            return response;
        }
    }
}
