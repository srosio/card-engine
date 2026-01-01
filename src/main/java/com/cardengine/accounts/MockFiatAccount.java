package com.cardengine.accounts;

import com.cardengine.common.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Mock fiat wallet account.
 *
 * In production, this would integrate with a banking partner API
 * to check real-time balances and execute fund movements.
 *
 * For the MVP, this simulates the behavior with local state.
 */
@Entity
@DiscriminatorValue("FIAT_WALLET")
@NoArgsConstructor
public class MockFiatAccount extends BaseAccount {

    public MockFiatAccount(String ownerId, Money initialBalance) {
        super(ownerId, initialBalance, AccountType.FIAT_WALLET);
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.FIAT_WALLET;
    }
}
