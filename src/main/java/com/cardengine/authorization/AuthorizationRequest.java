package com.cardengine.authorization;

import com.cardengine.common.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to authorize a card transaction.
 *
 * This represents an incoming authorization request from a card processor
 * or network (e.g., Visa, Mastercard).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRequest {

    /**
     * Unique identifier for this authorization request.
     */
    private String authorizationId;

    /**
     * Card being used for the transaction.
     */
    private String cardId;

    /**
     * Transaction amount.
     */
    private Money amount;

    /**
     * Merchant name.
     */
    private String merchantName;

    /**
     * Merchant Category Code (MCC).
     */
    private String merchantCategoryCode;

    /**
     * Merchant city.
     */
    private String merchantCity;

    /**
     * Merchant country code (ISO 3166-1 alpha-2).
     */
    private String merchantCountry;

    /**
     * Idempotency key to prevent duplicate processing.
     */
    private String idempotencyKey;
}
