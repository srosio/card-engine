package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRepository;
import com.cardengine.authorization.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rule that prevents too many transactions in a short time period.
 * This helps detect potential card fraud or compromise.
 */
@Component
@RequiredArgsConstructor
public class VelocityRule implements Rule {

    private final AuthorizationRepository authorizationRepository;

    @Value("${card-engine.rules.velocity-max-per-minute:5}")
    private int maxTransactionsPerMinute;

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        Instant oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES);

        long recentTransactionCount = authorizationRepository
            .findByCardIdAndCreatedAtAfter(request.getCardId(), oneMinuteAgo)
            .size();

        if (recentTransactionCount >= maxTransactionsPerMinute) {
            return RuleResult.decline(
                String.format("Velocity limit exceeded: %d transactions in last minute (max: %d)",
                    recentTransactionCount, maxTransactionsPerMinute)
            );
        }

        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "Velocity";
    }
}
