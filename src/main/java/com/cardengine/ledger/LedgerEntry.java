package com.cardengine.ledger;

import com.cardengine.common.Money;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry representing one side of a double-entry transaction.
 *
 * Every transaction results in at least two ledger entries:
 * - One debit
 * - One credit
 *
 * Ledger entries are never updated or deleted - they are append-only.
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_ledger_account_id", columnList = "account_id"),
    @Index(name = "idx_ledger_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
public class LedgerEntry {

    @Id
    private String entryId;

    /**
     * The transaction this entry belongs to.
     * Multiple entries share the same transaction_id (double-entry).
     */
    private String transactionId;

    /**
     * The account affected by this entry.
     */
    private String accountId;

    /**
     * Type of entry: DEBIT or CREDIT.
     */
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    /**
     * The monetary amount for this entry.
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money amount;

    /**
     * Type of transaction this entry is part of.
     */
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    /**
     * Optional authorization ID if this entry is related to a card authorization.
     */
    private String authorizationId;

    /**
     * Optional card ID if this entry is related to a card transaction.
     */
    private String cardId;

    /**
     * Description or memo for this entry.
     */
    private String description;

    /**
     * Idempotency key to prevent duplicate transactions.
     */
    private String idempotencyKey;

    /**
     * Timestamp when this entry was created.
     * Ledger entries are immutable and never updated.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry(String transactionId, String accountId, EntryType entryType,
                       Money amount, TransactionType transactionType,
                       String authorizationId, String cardId, String description,
                       String idempotencyKey) {
        this.entryId = UUID.randomUUID().toString();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.transactionType = transactionType;
        this.authorizationId = authorizationId;
        this.cardId = cardId;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public enum EntryType {
        DEBIT,
        CREDIT
    }
}
