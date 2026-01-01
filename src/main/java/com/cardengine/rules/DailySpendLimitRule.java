package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRepository;
import com.cardengine.authorization.AuthorizationRequest;
import com.cardengine.authorization.AuthorizationStatus;
import com.cardengine.common.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rule that enforces daily spending limits per card.
 */
@Component
@RequiredArgsConstructor
public class DailySpendLimitRule implements Rule {

    private final AuthorizationRepository authorizationRepository;

    @Value("${card-engine.rules.daily-limit-default:5000.00}")
    private BigDecimal dailyLimit;

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        // Calculate start of current day
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // Get all approved authorizations for this card today
        var todaysAuthorizations = authorizationRepository
            .findByCardIdAndCreatedAtAfter(request.getCardId(), startOfDay);

        // Sum up approved amounts
        BigDecimal spentToday = todaysAuthorizations.stream()
            .filter(auth -> auth.getStatus() == AuthorizationStatus.APPROVED)
            .map(auth -> auth.getAmount().getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Add current transaction amount
        BigDecimal totalWithCurrent = spentToday.add(request.getAmount().getAmount());

        if (totalWithCurrent.compareTo(dailyLimit) > 0) {
            return RuleResult.decline(
                String.format("Daily spend limit exceeded. Spent today: %s, Limit: %s",
                    spentToday, dailyLimit)
            );
        }

        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "DailySpendLimit";
    }
}
