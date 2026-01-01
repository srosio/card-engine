package com.cardengine.common.exception;

/**
 * Thrown when a card is not found.
 */
public class CardNotFoundException extends CardEngineException {

    public CardNotFoundException(String cardId) {
        super("Card not found: " + cardId);
    }
}
