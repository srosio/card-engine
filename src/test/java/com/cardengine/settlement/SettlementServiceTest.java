package com.cardengine.settlement;

import com.cardengine.accounts.AccountService;
import com.cardengine.accounts.InternalLedgerAccount;
import com.cardengine.authorization.*;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardService;
import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for settlement flow.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementServiceTest {

    @Autowired
    private SettlementService settlementService;

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
        testAccount = accountService.createInternalLedgerAccount(
            "test-owner",
            Money.of("1000.00", Currency.USD)
        );

        testCard = cardService.issueCard(
            "Test User",
            "1234",
            LocalDate.now().plusYears(2),
            testAccount.getAccountId(),
            "test-owner"
        );
    }

    @Test
    void testFullClearing() {
        // Authorize
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-1")
            .cardId(testCard.getCardId())
            .amount(Money.of("100.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse authResponse = authorizationService.authorize(authRequest);
        assertEquals(AuthorizationStatus.APPROVED, authResponse.getStatus());

        // Clear the full amount
        ClearingRequest clearRequest = ClearingRequest.builder()
            .authorizationId(authRequest.getAuthorizationId())
            .clearingAmount(Money.of("100.00", Currency.USD))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.clearTransaction(clearRequest);

        // Verify authorization is cleared
        Authorization auth = authorizationService.getAuthorization(authRequest.getAuthorizationId());
        assertEquals(AuthorizationStatus.CLEARED, auth.getStatus());

        // Verify balance is reduced
        Money balance = testAccount.getTotalBalance();
        assertEquals(0, Money.of("900.00", Currency.USD).getAmount()
            .compareTo(balance.getAmount()));
    }

    @Test
    void testPartialClearing() {
        // Authorize $100
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-2")
            .cardId(testCard.getCardId())
            .amount(Money.of("100.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        authorizationService.authorize(authRequest);

        // Clear only $75
        ClearingRequest clearRequest = ClearingRequest.builder()
            .authorizationId(authRequest.getAuthorizationId())
            .clearingAmount(Money.of("75.00", Currency.USD))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.clearTransaction(clearRequest);

        // Verify cleared amount
        Authorization auth = authorizationService.getAuthorization(authRequest.getAuthorizationId());
        assertEquals(0, Money.of("75.00", Currency.USD).getAmount()
            .compareTo(auth.getClearedAmount().getAmount()));
    }

    @Test
    void testAuthorizationRelease() {
        // Authorize
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-3")
            .cardId(testCard.getCardId())
            .amount(Money.of("100.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        authorizationService.authorize(authRequest);

        // Release without clearing
        settlementService.releaseAuthorization(
            authRequest.getAuthorizationId(),
            IdempotencyKey.generate()
        );

        // Verify authorization is released
        Authorization auth = authorizationService.getAuthorization(authRequest.getAuthorizationId());
        assertEquals(AuthorizationStatus.RELEASED, auth.getStatus());

        // Verify full balance is available again
        Money availableBalance = testAccount.getBalance();
        assertEquals(0, Money.of("1000.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }

    @Test
    void testReversal() {
        // Authorize and clear
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId("auth-4")
            .cardId(testCard.getCardId())
            .amount(Money.of("100.00", Currency.USD))
            .merchantName("Test Merchant")
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        authorizationService.authorize(authRequest);

        ClearingRequest clearRequest = ClearingRequest.builder()
            .authorizationId(authRequest.getAuthorizationId())
            .clearingAmount(Money.of("100.00", Currency.USD))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.clearTransaction(clearRequest);

        // Now reverse (refund)
        ReversalRequest reversalRequest = ReversalRequest.builder()
            .authorizationId(authRequest.getAuthorizationId())
            .reversalAmount(Money.of("100.00", Currency.USD))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.reverseTransaction(reversalRequest);

        // Verify authorization is reversed
        Authorization auth = authorizationService.getAuthorization(authRequest.getAuthorizationId());
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus());

        // Verify funds are back
        Money balance = testAccount.getTotalBalance();
        assertEquals(0, Money.of("1000.00", Currency.USD).getAmount()
            .compareTo(balance.getAmount()));
    }
}
