package com.cardengine.settlement;

import com.cardengine.common.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to reverse (refund) a previously cleared transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequest {

    /**
     * The authorization to reverse.
     */
    private String authorizationId;

    /**
     * Amount to reverse.
     * May be less than the cleared amount (partial reversal).
     */
    private Money reversalAmount;

    /**
     * Idempotency key to prevent duplicate processing.
     */
    private String idempotencyKey;
}
