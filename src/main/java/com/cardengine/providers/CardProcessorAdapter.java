package com.cardengine.providers;

import com.cardengine.authorization.AuthorizationResponse;

/**
 * Adapter interface for card processor/network integration.
 *
 * In production, this would integrate with:
 * - Visa/Mastercard networks
 * - Card-as-a-Service providers (Marqeta, Lithic, etc.)
 * - Payment processors
 *
 * The adapter translates between the card engine's internal format
 * and the external processor's API format.
 */
public interface CardProcessorAdapter {

    /**
     * Send an authorization response to the card processor.
     *
     * @param response the authorization response from the card engine
     */
    void sendAuthorizationResponse(AuthorizationResponse response);

    /**
     * Get the processor name.
     */
    String getProcessorName();
}
