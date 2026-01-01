package com.cardengine.bank;

import com.cardengine.authorization.AuthorizationRequest;
import com.cardengine.authorization.AuthorizationResponse;
import com.cardengine.authorization.AuthorizationStatus;
import com.cardengine.bank.mock.MockBankAccountAdapter;
import com.cardengine.cards.Card;
import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.settlement.ClearingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for bank-centric card operations.
 *
 * These tests demonstrate the complete flow when cards are backed
 * by external bank core accounts:
 *
 * 1. Bank account exists BEFORE card issuance
 * 2. Card is issued and linked to bank account
 * 3. Authorizations check balance in bank core
 * 4. Clearing commits debit in bank core
 * 5. Local database only tracks status, not balances
 *
 * Uses MockBankAccountAdapter to simulate bank core without dependencies.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BankIntegrationTest {

    @Autowired
    private BankCardIssuanceService cardIssuanceService;

    @Autowired
    private BankAuthorizationService authorizationService;

    @Autowired
    private BankSettlementService settlementService;

    @Autowired
    private BankAccountMappingRepository mappingRepository;

    private MockBankAccountAdapter mockBank;
    private String bankClientRef;
    private String bankAccountRef;

    @BeforeEach
    void setUp() {
        // Setup: Bank core already has client and account
        // (This would be done in the bank's core system, not here)
        mockBank = new MockBankAccountAdapter(Currency.USD);
        bankClientRef = "BANK_CLIENT_12345";
        bankAccountRef = "BANK_ACCOUNT_67890";

        // Bank core creates account with initial balance
        mockBank.createAccount(bankAccountRef, new BigDecimal("1000.00"));
    }

    @Test
    void testCompleteFlow_IssueCardForBankAccount() {
        // Scenario: Bank issues card to existing customer

        // Step 1: Bank issues card for existing account
        Card card = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "Jane Doe",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );

        assertNotNull(card);
        assertEquals("Jane Doe", card.getCardholderName());
        assertNotNull(card.getLast4());

        // Verify mapping was created
        BankAccountMapping mapping = mappingRepository.findByCardId(card.getCardId())
            .orElseThrow();

        assertEquals(bankClientRef, mapping.getBankClientRef());
        assertEquals(bankAccountRef, mapping.getBankAccountRef());
        assertEquals("MockBank", mapping.getBankCoreType());

        // Step 2: Activate card
        cardIssuanceService.activateCard(card.getCardId());

        // Step 3: Authorize a transaction
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-001")
            .cardId(card.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Coffee Shop")
            .merchantCategoryCode("5814")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse authResponse = authorizationService.authorize(authRequest);

        assertEquals(AuthorizationStatus.APPROVED, authResponse.getStatus());

        // Verify hold was placed in bank core
        assertTrue(mockBank.getActiveHoldReferences().contains("auth-001"));

        // Verify balance in bank core (should show reduced available balance)
        Money availableBalance = mockBank.getAvailableBalance(bankAccountRef);
        assertEquals(0, Money.of("950.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));

        // Step 4: Clear the transaction
        ClearingRequest clearRequest = ClearingRequest.builder()
            .authorizationId("auth-001")
            .clearingAmount(Money.of("50.00", Currency.USD))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.clearTransaction(clearRequest);

        // Verify hold was released in bank core
        assertFalse(mockBank.getActiveHoldReferences().contains("auth-001"));

        // Verify final balance in bank core
        Money finalBalance = mockBank.getAvailableBalance(bankAccountRef);
        assertEquals(0, Money.of("950.00", Currency.USD).getAmount()
            .compareTo(finalBalance.getAmount()));
    }

    @Test
    void testAuthorization_InsufficientFunds_DeclinedByBankCore() {
        // Setup: Create card
        Card card = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "John Smith",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );
        cardIssuanceService.activateCard(card.getCardId());

        // Attempt to authorize more than available balance
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-002")
            .cardId(card.getCardId())
            .amount(Money.of("2000.00", Currency.USD))  // More than $1000 available
            .merchantName("Electronics Store")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse authResponse = authorizationService.authorize(authRequest);

        // Bank core declines due to insufficient funds
        assertEquals(AuthorizationStatus.DECLINED, authResponse.getStatus());
        assertTrue(authResponse.getDeclineReason().contains("Bank declined") ||
                  authResponse.getDeclineReason().contains("Insufficient funds"));

        // Verify no hold was placed
        assertFalse(mockBank.getActiveHoldReferences().contains("auth-002"));
    }

    @Test
    void testAuthorization_ReleaseWithoutClearing() {
        // Setup: Create and activate card
        Card card = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "Alice Johnson",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );
        cardIssuanceService.activateCard(card.getCardId());

        // Authorize transaction
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-003")
            .cardId(card.getCardId())
            .amount(Money.of("75.00", Currency.USD))
            .merchantName("Restaurant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        authorizationService.authorize(authRequest);

        // Verify hold placed
        assertTrue(mockBank.getActiveHoldReferences().contains("auth-003"));

        // Release without clearing (e.g., authorization expired)
        settlementService.releaseAuthorization("auth-003", IdempotencyKey.generate());

        // Verify hold released
        assertFalse(mockBank.getActiveHoldReferences().contains("auth-003"));

        // Verify full balance available again
        Money balance = mockBank.getAvailableBalance(bankAccountRef);
        assertEquals(0, Money.of("1000.00", Currency.USD).getAmount()
            .compareTo(balance.getAmount()));
    }

    @Test
    void testMultipleCardsForSameAccount() {
        // Scenario: Bank issues multiple cards to same account
        // (e.g., primary and secondary cardholder)

        Card card1 = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "Primary Cardholder",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );

        Card card2 = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "Secondary Cardholder",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );

        cardIssuanceService.activateCard(card1.getCardId());
        cardIssuanceService.activateCard(card2.getCardId());

        // Both cards should map to same bank account
        BankAccountMapping mapping1 = mappingRepository.findByCardId(card1.getCardId()).orElseThrow();
        BankAccountMapping mapping2 = mappingRepository.findByCardId(card2.getCardId()).orElseThrow();

        assertEquals(bankAccountRef, mapping1.getBankAccountRef());
        assertEquals(bankAccountRef, mapping2.getBankAccountRef());

        // Both cards can authorize against same account
        AuthorizationResponse auth1 = authorizationService.authorize(
            AuthorizationRequest.builder()
                .authorizationId("auth-card1")
                .cardId(card1.getCardId())
                .amount(Money.of("100.00", Currency.USD))
                .merchantName("Store A")
                .idempotencyKey(IdempotencyKey.generate())
                .build()
        );

        AuthorizationResponse auth2 = authorizationService.authorize(
            AuthorizationRequest.builder()
                .authorizationId("auth-card2")
                .cardId(card2.getCardId())
                .amount(Money.of("100.00", Currency.USD))
                .merchantName("Store B")
                .idempotencyKey(IdempotencyKey.generate())
                .build()
        );

        assertEquals(AuthorizationStatus.APPROVED, auth1.getStatus());
        assertEquals(AuthorizationStatus.APPROVED, auth2.getStatus());

        // Available balance reduced by both holds
        Money availableBalance = mockBank.getAvailableBalance(bankAccountRef);
        assertEquals(0, Money.of("800.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }

    @Test
    void testIdempotency_DuplicateAuthorization() {
        Card card = cardIssuanceService.issueCardForBankAccount(
            bankClientRef,
            bankAccountRef,
            "Test User",
            LocalDate.now().plusYears(2),
            "bank-admin"
        );
        cardIssuanceService.activateCard(card.getCardId());

        String idempotencyKey = IdempotencyKey.generate();

        AuthorizationRequest request1 = AuthorizationRequest.builder()
            .authorizationId("auth-dup-1")
            .cardId(card.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Store")
            .idempotencyKey(idempotencyKey)  // Same key
            .build();

        // First authorization
        AuthorizationResponse response1 = authorizationService.authorize(request1);
        assertEquals(AuthorizationStatus.APPROVED, response1.getStatus());

        // Duplicate authorization with same idempotency key
        AuthorizationRequest request2 = AuthorizationRequest.builder()
            .authorizationId("auth-dup-2")  // Different auth ID
            .cardId(card.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Store")
            .idempotencyKey(idempotencyKey)  // Same key
            .build();

        AuthorizationResponse response2 = authorizationService.authorize(request2);
        assertEquals(AuthorizationStatus.APPROVED, response2.getStatus());

        // Balance should only be held once
        Money availableBalance = mockBank.getAvailableBalance(bankAccountRef);
        assertEquals(0, Money.of("950.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }
}
