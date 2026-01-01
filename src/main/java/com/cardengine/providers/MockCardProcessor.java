package com.cardengine.providers;

import com.cardengine.authorization.AuthorizationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock card processor adapter for testing and development.
 *
 * In production, this would be replaced with real integrations to
 * card networks or issuer processors.
 */
@Component
@Slf4j
public class MockCardProcessor implements CardProcessorAdapter {

    @Override
    public void sendAuthorizationResponse(AuthorizationResponse response) {
        log.info("MockCardProcessor: Sending authorization response to network: " +
            "authId={}, status={}, reason={}",
            response.getAuthorizationId(),
            response.getStatus(),
            response.getDeclineReason());

        // In a real implementation, this would make an API call to the processor
        // For now, just log the response
    }

    @Override
    public String getProcessorName() {
        return "MockProcessor";
    }
}
