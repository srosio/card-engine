package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRequest;
import com.cardengine.common.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Rule that enforces per-transaction spending limits.
 */
@Component
@RequiredArgsConstructor
public class TransactionLimitRule implements Rule {

    @Value("${card-engine.rules.transaction-limit-default:1000.00}")
    private BigDecimal transactionLimit;

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        Money limitMoney = Money.of(transactionLimit, request.getAmount().getCurrency());

        if (request.getAmount().isGreaterThan(limitMoney)) {
            return RuleResult.decline(
                String.format("Transaction amount %s exceeds limit %s",
                    request.getAmount().getAmount(), transactionLimit)
            );
        }

        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "TransactionLimit";
    }
}
