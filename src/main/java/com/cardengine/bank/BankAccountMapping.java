package com.cardengine.bank;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Maps cards to bank accounts in the external core banking system.
 *
 * CRITICAL PRINCIPLES:
 * - Bank accounts exist BEFORE cards are issued
 * - This is a reference mapping, NOT account creation
 * - Bank core is the source of truth for account data
 * - This mapping is immutable once created (audit trail)
 *
 * BANK CONTEXT:
 * When a bank issues a card to an existing client:
 * 1. Client already exists in bank core (KYC done, account opened)
 * 2. Bank identifies which account should back the card
 * 3. Card is issued and linked to that account via this mapping
 * 4. All transactions check balance in bank core, not locally
 *
 * Example:
 * - Bank client "John Doe" has checking account #1234567 in core
 * - Bank issues card with last4 "4242"
 * - This mapping links card "4242" → account "1234567"
 * - Card authorizations check balance of account 1234567 in core
 */
@Entity
@Table(name = "bank_account_mappings", indexes = {
    @Index(name = "idx_card_bank_mapping", columnList = "card_id", unique = true)
})
@Data
@NoArgsConstructor
public class BankAccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Internal card ID from card_engine.cards table.
     */
    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId;

    /**
     * Bank's client/customer reference.
     * This is the client ID in the external core banking system.
     * NOT created by card engine - must already exist.
     */
    @Column(name = "bank_client_ref", nullable = false)
    private String bankClientRef;

    /**
     * Bank's account reference.
     * This is the account ID in the external core banking system.
     * This account must exist before card issuance.
     */
    @Column(name = "bank_account_ref", nullable = false)
    private String bankAccountRef;

    /**
     * Name of the bank core adapter being used.
     * Examples: "Fineract", "T24", "Mambu", "CustomCore"
     */
    @Column(name = "bank_core_type", nullable = false)
    private String bankCoreType;

    /**
     * When this mapping was created (card issued).
     * Immutable - never updated.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Who created this mapping (for audit).
     */
    @Column(name = "created_by")
    private String createdBy;

    public BankAccountMapping(String cardId, String bankClientRef,
                             String bankAccountRef, String bankCoreType,
                             String createdBy) {
        this.cardId = cardId;
        this.bankClientRef = bankClientRef;
        this.bankAccountRef = bankAccountRef;
        this.bankCoreType = bankCoreType;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
