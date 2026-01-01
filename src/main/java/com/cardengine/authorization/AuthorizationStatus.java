package com.cardengine.authorization;

/**
 * Status of a card authorization.
 */
public enum AuthorizationStatus {
    /**
     * Authorization was approved and funds are reserved.
     */
    APPROVED,

    /**
     * Authorization was declined.
     */
    DECLINED,

    /**
     * Authorization has been cleared/settled.
     */
    CLEARED,

    /**
     * Authorization was released without clearing.
     */
    RELEASED,

    /**
     * Authorization was reversed (refunded).
     */
    REVERSED
}
