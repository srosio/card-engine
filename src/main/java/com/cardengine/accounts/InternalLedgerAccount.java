package com.cardengine.accounts;

import com.cardengine.common.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Internal ledger account managed entirely within the card engine.
 *
 * This is the primary account type for the MVP and supports full
 * reserve-commit-release lifecycle with real fund movements tracked
 * in the double-entry ledger.
 */
@Entity
@DiscriminatorValue("INTERNAL_LEDGER")
@NoArgsConstructor
public class InternalLedgerAccount extends BaseAccount {

    public InternalLedgerAccount(String ownerId, Money initialBalance) {
        super(ownerId, initialBalance, AccountType.INTERNAL_LEDGER);
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.INTERNAL_LEDGER;
    }
}
