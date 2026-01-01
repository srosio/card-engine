package com.cardengine.common;

import java.util.UUID;

/**
 * Utility class for generating and validating idempotency keys.
 * Idempotency is critical for financial operations to prevent duplicate transactions.
 */
public class IdempotencyKey {

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void validate(String key) {
        if (!isValid(key)) {
            throw new IllegalArgumentException("Invalid idempotency key: " + key);
        }
    }
}
