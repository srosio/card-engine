package com.cardengine.cards;

/**
 * Lifecycle states for a card.
 */
public enum CardState {
    /**
     * Card is active and can be used for transactions.
     */
    ACTIVE,

    /**
     * Card is temporarily frozen. Can be unfrozen.
     */
    FROZEN,

    /**
     * Card is permanently closed. Cannot be reactivated.
     */
    CLOSED
}
