package com.cardengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Card Engine.
 *
 * Card Engine is an issuer-agnostic, account-agnostic card orchestration platform
 * that handles card authorization, settlement, and ledger logic while delegating
 * compliance and issuing to external providers.
 */
@SpringBootApplication
public class CardEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardEngineApplication.class, args);
    }
}
