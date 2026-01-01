package com.cardengine.authorization;

import com.cardengine.common.Money;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persisted authorization record.
 *
 * Tracks the lifecycle of a card authorization from initial approval
 * through clearing or release.
 */
@Entity
@Table(name = "authorizations", indexes = {
    @Index(name = "idx_auth_card_id", columnList = "card_id"),
    @Index(name = "idx_auth_account_id", columnList = "account_id"),
    @Index(name = "idx_auth_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
public class Authorization {

    @Id
    private String authorizationId;

    private String cardId;

    private String accountId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money amount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "cleared_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "cleared_currency"))
    })
    private Money clearedAmount;

    @Enumerated(EnumType.STRING)
    private AuthorizationStatus status;

    private String merchantName;
    private String merchantCategoryCode;
    private String merchantCity;
    private String merchantCountry;

    private String declineReason;

    private String idempotencyKey;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Authorization(String authorizationId, String cardId, String accountId,
                        Money amount, AuthorizationStatus status,
                        String merchantName, String merchantCategoryCode,
                        String merchantCity, String merchantCountry,
                        String idempotencyKey) {
        this.authorizationId = authorizationId;
        this.cardId = cardId;
        this.accountId = accountId;
        this.amount = amount;
        this.status = status;
        this.merchantName = merchantName;
        this.merchantCategoryCode = merchantCategoryCode;
        this.merchantCity = merchantCity;
        this.merchantCountry = merchantCountry;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void decline(String reason) {
        this.status = AuthorizationStatus.DECLINED;
        this.declineReason = reason;
        this.updatedAt = Instant.now();
    }

    public void clear(Money amount) {
        this.status = AuthorizationStatus.CLEARED;
        this.clearedAmount = amount;
        this.updatedAt = Instant.now();
    }

    public void release() {
        this.status = AuthorizationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public void reverse() {
        this.status = AuthorizationStatus.REVERSED;
        this.updatedAt = Instant.now();
    }
}
