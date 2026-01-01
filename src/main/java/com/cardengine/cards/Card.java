package com.cardengine.cards;

import com.cardengine.common.exception.InvalidCardStateException;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Card entity representing a virtual card.
 *
 * Cards never store money - they only authorize access to accounts.
 * Each card is backed by exactly one funding account (MVP constraint).
 */
@Entity
@Table(name = "cards")
@Data
@NoArgsConstructor
public class Card {

    @Id
    private String cardId;

    private String cardholderName;

    /**
     * Last 4 digits of the card number (PAN).
     * Full PAN is not stored in this MVP for security reasons.
     * In production, PAN would be tokenized and stored in a PCI-compliant vault.
     */
    private String last4;

    /**
     * Expiration date of the card.
     */
    private LocalDate expirationDate;

    /**
     * The account that backs this card.
     * All transactions on this card will reserve/commit funds from this account.
     */
    private String fundingAccountId;

    /**
     * Current state of the card.
     */
    @Enumerated(EnumType.STRING)
    private CardState state;

    /**
     * Owner/user ID who owns this card.
     */
    private String ownerId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Card(String cardholderName, String last4, LocalDate expirationDate,
                String fundingAccountId, String ownerId) {
        this.cardId = UUID.randomUUID().toString();
        this.cardholderName = cardholderName;
        this.last4 = last4;
        this.expirationDate = expirationDate;
        this.fundingAccountId = fundingAccountId;
        this.ownerId = ownerId;
        this.state = CardState.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void freeze() {
        if (state == CardState.CLOSED) {
            throw new InvalidCardStateException(cardId, state.name(), "freeze");
        }
        this.state = CardState.FROZEN;
        this.updatedAt = Instant.now();
    }

    public void unfreeze() {
        if (state != CardState.FROZEN) {
            throw new InvalidCardStateException(cardId, state.name(), "unfreeze");
        }
        this.state = CardState.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (state == CardState.CLOSED) {
            throw new InvalidCardStateException(cardId, state.name(), "close");
        }
        this.state = CardState.CLOSED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return state == CardState.ACTIVE;
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(expirationDate);
    }
}
