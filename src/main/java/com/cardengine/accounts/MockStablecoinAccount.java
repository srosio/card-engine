package com.cardengine.accounts;

import com.cardengine.common.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Mock stablecoin account.
 *
 * In production, this would integrate with a blockchain API or
 * custodian service to check on-chain balances and execute transfers.
 *
 * For the MVP, this simulates the behavior with local state.
 */
@Entity
@DiscriminatorValue("STABLECOIN")
@NoArgsConstructor
public class MockStablecoinAccount extends BaseAccount {

    public MockStablecoinAccount(String ownerId, Money initialBalance) {
        super(ownerId, initialBalance, AccountType.STABLECOIN);
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.STABLECOIN;
    }
}
