package com.cardengine.rules;

import lombok.Value;

/**
 * Result of a rule evaluation.
 */
@Value
public class RuleResult {
    boolean approved;
    String reason;

    public static RuleResult approve() {
        return new RuleResult(true, null);
    }

    public static RuleResult decline(String reason) {
        return new RuleResult(false, reason);
    }
}
