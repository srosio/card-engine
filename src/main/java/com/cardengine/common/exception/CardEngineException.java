package com.cardengine.common.exception;

/**
 * Base exception for all card engine exceptions.
 */
public class CardEngineException extends RuntimeException {

    public CardEngineException(String message) {
        super(message);
    }

    public CardEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
