package com.cardengine.providers.fineract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTOs for Apache Fineract API integration.
 *
 * These match Fineract's REST API structure.
 * See: https://fineract.apache.org/api-docs/
 */
public class FineractDTOs {

    /**
     * Account balance response from Fineract.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalance {
        private Long savingsId;
        private String accountNo;
        private BigDecimal accountBalance;
        private BigDecimal availableBalance;
        private String currency;
        private AccountStatus status;

        @Data
        public static class AccountStatus {
            private Long id;
            private String code;
            private String value;
        }
    }

    /**
     * Journal entry request for Fineract.
     * Used for making ledger entries (debits, credits, holds).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalEntryRequest {
        private Long officeId;
        private String transactionDate;  // Format: "dd MMMM yyyy"
        private String referenceNumber;   // Our authorization ID or transaction reference
        private String comments;
        private DebitsAndCredits[] debits;
        private DebitsAndCredits[] credits;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DebitsAndCredits {
            private Long glAccountId;
            private BigDecimal amount;
        }
    }

    /**
     * Journal entry response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalEntryResponse {
        private Long transactionId;
        private String transactionDate;
    }

    /**
     * Savings transaction (debit/credit) request.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavingsTransactionRequest {
        private String transactionDate;  // Format: "dd MMMM yyyy"
        private BigDecimal transactionAmount;
        private String note;
        private String referenceNumber;
        private String dateFormat = "dd MMMM yyyy";
        private String locale = "en";
    }

    /**
     * Savings transaction response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavingsTransactionResponse {
        private Long savingsId;
        private Long resourceId;
        private Map<String, Object> changes;
    }

    /**
     * Hold entry tracking structure.
     * Stored as metadata to track auth holds.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldEntry {
        private String authorizationId;
        private Long journalEntryId;
        private BigDecimal amount;
        private String currency;
        private String createdAt;
        private String status;  // "ACTIVE", "RELEASED", "COMMITTED"
    }
}
