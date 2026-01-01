package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRequest;

/**
 * Interface for authorization rules.
 *
 * Each rule evaluates an authorization request and returns a result
 * indicating whether the transaction should be approved or declined.
 */
public interface Rule {

    /**
     * Evaluate the rule against an authorization request.
     *
     * @param request the authorization request to evaluate
     * @return the result of the rule evaluation
     */
    RuleResult evaluate(AuthorizationRequest request);

    /**
     * Get the name of this rule.
     */
    String getRuleName();
}
