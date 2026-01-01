package com.cardengine.authorization;

import com.cardengine.accounts.AccountService;
import com.cardengine.accounts.InternalLedgerAccount;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardService;
import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.ledger.LedgerService;
import com.cardengine.rules.RulesEngine;
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
 * Integration tests for authorization flow.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationServiceTest {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CardService cardService;

    private Card testCard;
    private InternalLedgerAccount testAccount;

    @BeforeEach
    void setUp() {
        // Create test account with $1000
        testAccount = accountService.createInternalLedgerAccount(
            "test-owner",
            Money.of("1000.00", Currency.USD)
        );

        // Create test card
        testCard = cardService.issueCard(
            "Test User",
            "1234",
            LocalDate.now().plusYears(2),
            testAccount.getAccountId(),
            "test-owner"
        );
    }

    @Test
    void testSuccessfulAuthorization() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .authorizationId("auth-1")
            .cardId(testCard.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Test Merchant")
            .merchantCategoryCode("5411")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse response = authorizationService.authorize(request);

        assertEquals(AuthorizationStatus.APPROVED, response.getStatus());
        assertNull(response.getDeclineReason());

        // Verify funds are reserved
        Money availableBalance = testAccount.getBalance();
        assertEquals(0, Money.of("950.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }

    @Test
    void testDeclineInsufficientFunds() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .authorizationId("auth-2")
            .cardId(testCard.getCardId())
            .amount(Money.of("2000.00", Currency.USD))  // More than available
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse response = authorizationService.authorize(request);

        assertEquals(AuthorizationStatus.DECLINED, response.getStatus());
        assertNotNull(response.getDeclineReason());
        assertTrue(response.getDeclineReason().contains("Insufficient funds"));
    }

    @Test
    void testDeclineTransactionLimit() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .authorizationId("auth-3")
            .cardId(testCard.getCardId())
            .amount(Money.of("1500.00", Currency.USD))  // Exceeds transaction limit
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse response = authorizationService.authorize(request);

        assertEquals(AuthorizationStatus.DECLINED, response.getStatus());
        assertNotNull(response.getDeclineReason());
        assertTrue(response.getDeclineReason().contains("limit"));
    }

    @Test
    void testDeclineBlockedMCC() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .authorizationId("auth-4")
            .cardId(testCard.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Casino")
            .merchantCategoryCode("7995")  // Gambling - blocked
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse response = authorizationService.authorize(request);

        assertEquals(AuthorizationStatus.DECLINED, response.getStatus());
        assertNotNull(response.getDeclineReason());
        assertTrue(response.getDeclineReason().contains("blocked"));
    }

    @Test
    void testIdempotency() {
        String idempotencyKey = IdempotencyKey.generate();

        AuthorizationRequest request1 = AuthorizationRequest.builder()
            .authorizationId("auth-5")
            .cardId(testCard.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(idempotencyKey)
            .build();

        AuthorizationResponse response1 = authorizationService.authorize(request1);
        assertEquals(AuthorizationStatus.APPROVED, response1.getStatus());

        // Same idempotency key - should return same result without double charging
        AuthorizationRequest request2 = AuthorizationRequest.builder()
            .authorizationId("auth-6")  // Different auth ID
            .cardId(testCard.getCardId())
            .amount(Money.of("50.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(idempotencyKey)  // Same idempotency key
            .build();

        AuthorizationResponse response2 = authorizationService.authorize(request2);
        assertEquals(AuthorizationStatus.APPROVED, response2.getStatus());

        // Balance should only be reduced once
        Money availableBalance = testAccount.getBalance();
        assertEquals(0, Money.of("950.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }
}
