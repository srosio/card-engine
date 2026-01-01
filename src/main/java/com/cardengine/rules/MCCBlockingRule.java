package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Rule that blocks transactions based on Merchant Category Code (MCC).
 *
 * MCCs are 4-digit codes that classify the type of goods or services
 * a merchant provides. Common examples:
 * - 6211: Securities brokers/dealers
 * - 7995: Betting/casino gambling
 * - 5967: Direct marketing - inbound teleservices
 */
@Component
public class MCCBlockingRule implements Rule {

    // Example blocked MCCs (can be configured per card in production)
    private static final Set<String> BLOCKED_MCCS = Set.of(
        "6211",  // Securities brokers
        "7995",  // Gambling
        "5993",  // Cigar stores
        "5912",  // Drug stores (if prescription-only cards)
        "9754"   // Quasi-cash (money orders, etc.)
    );

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        String mcc = request.getMerchantCategoryCode();

        if (mcc != null && BLOCKED_MCCS.contains(mcc)) {
            return RuleResult.decline(
                String.format("Merchant category %s is blocked", mcc)
            );
        }

        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "MCCBlocking";
    }
}
