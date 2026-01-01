package com.cardengine.settlement;

import com.cardengine.common.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to clear (settle) a previously authorized transaction.
 *
 * Clearing happens when the merchant finalizes the transaction,
 * which may be for the full authorization amount or a partial amount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClearingRequest {

    /**
     * The authorization to clear.
     */
    private String authorizationId;

    /**
     * Amount to clear.
     * May be less than the original authorization amount (partial clear).
     * Cannot be more than the authorization amount.
     */
    private Money clearingAmount;

    /**
     * Idempotency key to prevent duplicate processing.
     */
    private String idempotencyKey;
}
