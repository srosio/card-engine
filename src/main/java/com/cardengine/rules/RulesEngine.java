package com.cardengine.rules;

import com.cardengine.authorization.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rules engine that evaluates all configured rules against authorization requests.
 *
 * Rules are evaluated in order, and the first rule that declines the transaction
 * will cause the entire authorization to be declined.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngine {

    private final List<Rule> rules;

    /**
     * Evaluate all rules against an authorization request.
     *
     * @param request the authorization request to evaluate
     * @return the result of the rules evaluation
     */
    public RuleResult evaluateRules(AuthorizationRequest request) {
        log.debug("Evaluating {} rules for authorization on card {}", rules.size(), request.getCardId());

        for (Rule rule : rules) {
            RuleResult result = rule.evaluate(request);

            if (!result.isApproved()) {
                log.info("Rule {} declined authorization: {}", rule.getRuleName(), result.getReason());
                return result;
            }

            log.debug("Rule {} approved", rule.getRuleName());
        }

        log.debug("All rules approved for card {}", request.getCardId());
        return RuleResult.approve();
    }
}
