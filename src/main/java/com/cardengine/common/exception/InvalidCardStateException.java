package com.cardengine.common.exception;

/**
 * Thrown when attempting an operation on a card in an invalid state.
 */
public class InvalidCardStateException extends CardEngineException {

    public InvalidCardStateException(String cardId, String currentState, String operation) {
        super(String.format("Cannot perform operation '%s' on card %s in state %s",
            operation, cardId, currentState));
    }
}
