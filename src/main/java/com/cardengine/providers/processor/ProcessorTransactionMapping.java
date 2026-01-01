package com.cardengine.providers.processor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Maps processor transaction IDs to internal authorization IDs.
 *
 * Required because external processors use their own ID scheme,
 * and we need to correlate their webhooks with our internal authorizations.
 */
@Entity
@Table(name = "processor_transaction_mapping", indexes = {
    @Index(name = "idx_processor_txn_id", columnList = "processor_transaction_id"),
    @Index(name = "idx_internal_auth_id", columnList = "internal_authorization_id")
})
@Data
@NoArgsConstructor
public class ProcessorTransactionMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The processor's transaction ID (from their webhook).
     */
    @Column(name = "processor_transaction_id", unique = true, nullable = false)
    private String processorTransactionId;

    /**
     * Our internal authorization ID.
     */
    @Column(name = "internal_authorization_id", nullable = false)
    private String internalAuthorizationId;

    /**
     * Card token from processor (links to our card).
     */
    @Column(name = "card_token")
    private String cardToken;

    /**
     * Which processor this mapping belongs to.
     */
    @Column(name = "processor_name", nullable = false)
    private String processorName;

    @Column(name = "created_at")
    private Instant createdAt;

    public ProcessorTransactionMapping(String processorTransactionId,
                                      String internalAuthorizationId,
                                      String cardToken,
                                      String processorName) {
        this.processorTransactionId = processorTransactionId;
        this.internalAuthorizationId = internalAuthorizationId;
        this.cardToken = cardToken;
        this.processorName = processorName;
        this.createdAt = Instant.now();
    }
}
