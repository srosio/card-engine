package com.cardengine.providers.fineract;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracks card authorization holds in Fineract.
 *
 * Since Fineract doesn't natively support card-style authorization holds,
 * we implement a workaround using shadow transactions:
 *
 * WORKAROUND STRATEGY:
 * 1. On reserve(): Create a journal entry moving funds from user's account
 *    to a special CARD_AUTH_HOLDS GL account. Store the journal entry ID here.
 *
 * 2. On commit(): Reverse the hold journal entry, then make the actual debit.
 *
 * 3. On release(): Reverse the hold journal entry to return funds to user.
 *
 * This table tracks the mapping between authorization IDs and Fineract journal entries.
 */
@Entity
@Table(name = "fineract_auth_holds", indexes = {
    @Index(name = "idx_auth_hold_auth_id", columnList = "authorization_id"),
    @Index(name = "idx_auth_hold_account_id", columnList = "fineract_account_id")
})
@Data
@NoArgsConstructor
public class FineractAuthHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Our internal authorization ID.
     */
    @Column(name = "authorization_id", unique = true, nullable = false)
    private String authorizationId;

    /**
     * Fineract savings account ID.
     */
    @Column(name = "fineract_account_id", nullable = false)
    private Long fineractAccountId;

    /**
     * Fineract journal entry ID for the hold.
     * This is the transaction we need to reverse on commit/release.
     */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /**
     * Amount held.
     */
    @Column(name = "hold_amount", nullable = false)
    private BigDecimal holdAmount;

    /**
     * Currency code.
     */
    @Column(name = "currency", nullable = false)
    private String currency;

    /**
     * Hold status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HoldStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum HoldStatus {
        ACTIVE,      // Hold is in place
        COMMITTED,   // Hold was committed (cleared)
        RELEASED     // Hold was released without clearing
    }

    public FineractAuthHold(String authorizationId, Long fineractAccountId,
                           Long journalEntryId, BigDecimal holdAmount, String currency) {
        this.authorizationId = authorizationId;
        this.fineractAccountId = fineractAccountId;
        this.journalEntryId = journalEntryId;
        this.holdAmount = holdAmount;
        this.currency = currency;
        this.status = HoldStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markCommitted() {
        this.status = HoldStatus.COMMITTED;
        this.updatedAt = Instant.now();
    }

    public void markReleased() {
        this.status = HoldStatus.RELEASED;
        this.updatedAt = Instant.now();
    }
}
