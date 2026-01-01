package com.cardengine.accounts;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import com.cardengine.common.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base entity for all account implementations.
 * Provides common fields and reserve tracking logic.
 */
@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type", discriminatorType = DiscriminatorType.STRING)
@Data
@NoArgsConstructor
public abstract class BaseAccount implements Account {

    @Id
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", insertable = false, updatable = false)
    private AccountType accountType;

    private String ownerId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "balance_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "balance_currency"))
    })
    private Money balance;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_reserves", joinColumns = @JoinColumn(name = "account_id"))
    @MapKeyColumn(name = "authorization_id")
    @Column(name = "reserved_amount")
    private Map<String, BigDecimal> reserves = new HashMap<>();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected BaseAccount(String ownerId, Money initialBalance, AccountType accountType) {
        this.accountId = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.balance = initialBalance;
        this.accountType = accountType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @Override
    public Currency getCurrency() {
        return balance.getCurrency();
    }

    @Override
    public Money getBalance() {
        Money reserved = getReservedBalance();
        return balance.subtract(reserved);
    }

    @Override
    public Money getTotalBalance() {
        return balance;
    }

    @Override
    public Money getReservedBalance() {
        BigDecimal totalReserved = reserves.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(totalReserved, balance.getCurrency());
    }

    @Override
    public void reserve(Money amount, String authorizationId) {
        if (reserves.containsKey(authorizationId)) {
            throw new IllegalStateException("Authorization already has reserved funds: " + authorizationId);
        }

        Money available = getBalance();
        if (available.isLessThan(amount)) {
            throw new InsufficientFundsException(accountId, amount, available);
        }

        reserves.put(authorizationId, amount.getAmount());
        this.updatedAt = Instant.now();
    }

    @Override
    public void commit(Money amount, String authorizationId) {
        BigDecimal reserved = reserves.get(authorizationId);
        if (reserved == null) {
            throw new IllegalStateException("No reserved funds for authorization: " + authorizationId);
        }

        Money reservedMoney = Money.of(reserved, balance.getCurrency());
        if (amount.isGreaterThan(reservedMoney)) {
            throw new IllegalArgumentException("Cannot commit more than reserved amount");
        }

        // Deduct from balance and remove from reserves
        balance = balance.subtract(amount);
        reserves.remove(authorizationId);
        this.updatedAt = Instant.now();
    }

    @Override
    public void release(Money amount, String authorizationId) {
        BigDecimal reserved = reserves.get(authorizationId);
        if (reserved == null) {
            throw new IllegalStateException("No reserved funds for authorization: " + authorizationId);
        }

        Money reservedMoney = Money.of(reserved, balance.getCurrency());
        if (!amount.equals(reservedMoney)) {
            throw new IllegalArgumentException("Release amount must match reserved amount");
        }

        reserves.remove(authorizationId);
        this.updatedAt = Instant.now();
    }

    public void deposit(Money amount) {
        if (!amount.getCurrency().equals(this.balance.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }
}
