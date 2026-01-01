package com.cardengine.providers.processor;

import com.cardengine.accounts.AccountService;
import com.cardengine.accounts.InternalLedgerAccount;
import com.cardengine.authorization.AuthorizationService;
import com.cardengine.authorization.AuthorizationStatus;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardService;
import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.settlement.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SampleProcessorAdapter.
 *
 * Tests the complete webhook flow:
 * - Authorization webhook -> internal authorization
 * - Clearing webhook -> settlement
 * - Reversal webhook -> refund
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SampleProcessorAdapterTest {

    @Autowired
    private SampleProcessorAdapter processorAdapter;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CardService cardService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ProcessorTransactionMappingRepository mappingRepository;

    private Card testCard;
    private InternalLedgerAccount testAccount;

    @BeforeEach
    void setUp() {
        // Create test account with $1000
        testAccount = accountService.createInternalLedgerAccount(
            "processor-test-owner",
            Money.of("1000.00", Currency.USD)
        );

        // Create test card
        testCard = cardService.issueCard(
            "Processor Test User",
            "1234",  // This acts as card token for testing
            LocalDate.now().plusYears(2),
            testAccount.getAccountId(),
            "processor-test-owner"
        );
    }

    @Test
    void testAuthorizationWebhook_Approved() {
        // Simulate authorization webhook from SampleProcessor
        SampleProcessorWebhooks.AuthorizationWebhook webhook = createAuthWebhook(
            "proc-txn-001",
            "1234",  // Card token (last4)
            "100.00",
            "Coffee Shop",
            "5814"  // Coffee shop MCC
        );

        // Process webhook
        SampleProcessorWebhooks.ProcessorResponse response =
            processorAdapter.handleAuthorizationWebhook(webhook);

        // Verify approval
        assertEquals("APPROVED", response.getStatus());
        assertNotNull(response.getAuthorizationCode());
        assertNull(response.getDeclineReason());

        // Verify mapping was created
        ProcessorTransactionMapping mapping = mappingRepository
            .findByProcessorTransactionId("proc-txn-001")
            .orElseThrow();

        assertNotNull(mapping.getInternalAuthorizationId());
        assertEquals("1234", mapping.getCardToken());
        assertEquals("SampleProcessor", mapping.getProcessorName());

        // Verify funds were reserved
        Money availableBalance = testAccount.getBalance();
        assertEquals(0, Money.of("900.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }

    @Test
    void testAuthorizationWebhook_Declined_InsufficientFunds() {
        // Try to authorize more than available balance
        SampleProcessorWebhooks.AuthorizationWebhook webhook = createAuthWebhook(
            "proc-txn-002",
            "1234",
            "2000.00",  // More than $1000 available
            "Electronics Store",
            "5732"
        );

        SampleProcessorWebhooks.ProcessorResponse response =
            processorAdapter.handleAuthorizationWebhook(webhook);

        assertEquals("DECLINED", response.getStatus());
        assertNull(response.getAuthorizationCode());
        assertTrue(response.getDeclineReason().contains("Insufficient funds"));

        // Verify no mapping was created
        assertTrue(mappingRepository.findByProcessorTransactionId("proc-txn-002").isEmpty());
    }

    @Test
    void testAuthorizationWebhook_Declined_BlockedMCC() {
        // Try to authorize at blocked merchant category (gambling)
        SampleProcessorWebhooks.AuthorizationWebhook webhook = createAuthWebhook(
            "proc-txn-003",
            "1234",
            "50.00",
            "Online Casino",
            "7995"  // Gambling MCC - blocked by rules engine
        );

        SampleProcessorWebhooks.ProcessorResponse response =
            processorAdapter.handleAuthorizationWebhook(webhook);

        assertEquals("DECLINED", response.getStatus());
        assertTrue(response.getDeclineReason().contains("blocked"));
    }

    @Test
    void testClearingWebhook_Success() {
        // First, authorize a transaction
        SampleProcessorWebhooks.AuthorizationWebhook authWebhook = createAuthWebhook(
            "proc-txn-004",
            "1234",
            "75.00",
            "Restaurant",
            "5812"
        );

        processorAdapter.handleAuthorizationWebhook(authWebhook);

        // Now clear it
        SampleProcessorWebhooks.ClearingWebhook clearingWebhook = new SampleProcessorWebhooks.ClearingWebhook();
        clearingWebhook.setProcessorTransactionId("proc-txn-004");
        clearingWebhook.setAuthorizationId("proc-txn-004");
        clearingWebhook.setSettledAmount(new BigDecimal("75.00"));
        clearingWebhook.setCurrency("USD");
        clearingWebhook.setSettlementDate(Instant.now());
        clearingWebhook.setIdempotencyKey(IdempotencyKey.generate());

        // Process clearing webhook
        processorAdapter.handleClearingWebhook(clearingWebhook);

        // Verify funds were committed (deducted from account)
        Money totalBalance = testAccount.getTotalBalance();
        assertEquals(0, Money.of("925.00", Currency.USD).getAmount()
            .compareTo(totalBalance.getAmount()));
    }

    @Test
    void testReversalWebhook_Success() {
        // First, authorize and clear a transaction
        SampleProcessorWebhooks.AuthorizationWebhook authWebhook = createAuthWebhook(
            "proc-txn-005",
            "1234",
            "50.00",
            "Bookstore",
            "5942"
        );

        processorAdapter.handleAuthorizationWebhook(authWebhook);

        SampleProcessorWebhooks.ClearingWebhook clearingWebhook = new SampleProcessorWebhooks.ClearingWebhook();
        clearingWebhook.setProcessorTransactionId("proc-txn-005");
        clearingWebhook.setAuthorizationId("proc-txn-005");
        clearingWebhook.setSettledAmount(new BigDecimal("50.00"));
        clearingWebhook.setCurrency("USD");
        clearingWebhook.setSettlementDate(Instant.now());
        clearingWebhook.setIdempotencyKey(IdempotencyKey.generate());

        processorAdapter.handleClearingWebhook(clearingWebhook);

        // Now reverse it (refund)
        SampleProcessorWebhooks.ReversalWebhook reversalWebhook = new SampleProcessorWebhooks.ReversalWebhook();
        reversalWebhook.setProcessorTransactionId("proc-txn-005");
        reversalWebhook.setOriginalAuthorizationId("proc-txn-005");
        reversalWebhook.setReversalAmount(new BigDecimal("50.00"));
        reversalWebhook.setCurrency("USD");
        reversalWebhook.setReason("Customer returned item");
        reversalWebhook.setTimestamp(Instant.now());
        reversalWebhook.setIdempotencyKey(IdempotencyKey.generate());

        processorAdapter.handleReversalWebhook(reversalWebhook);

        // Verify funds were returned
        Money totalBalance = testAccount.getTotalBalance();
        assertEquals(0, Money.of("1000.00", Currency.USD).getAmount()
            .compareTo(totalBalance.getAmount()));
    }

    @Test
    void testIdempotency_DuplicateAuthorizationWebhook() {
        String idempotencyKey = IdempotencyKey.generate();

        SampleProcessorWebhooks.AuthorizationWebhook webhook1 = createAuthWebhook(
            "proc-txn-006",
            "1234",
            "100.00",
            "Store",
            "5411"
        );
        webhook1.setIdempotencyKey(idempotencyKey);

        // First call
        SampleProcessorWebhooks.ProcessorResponse response1 =
            processorAdapter.handleAuthorizationWebhook(webhook1);
        assertEquals("APPROVED", response1.getStatus());

        // Second call with same idempotency key (duplicate webhook)
        SampleProcessorWebhooks.AuthorizationWebhook webhook2 = createAuthWebhook(
            "proc-txn-007",  // Different processor txn ID
            "1234",
            "100.00",
            "Store",
            "5411"
        );
        webhook2.setIdempotencyKey(idempotencyKey);  // Same idempotency key

        SampleProcessorWebhooks.ProcessorResponse response2 =
            processorAdapter.handleAuthorizationWebhook(webhook2);
        assertEquals("APPROVED", response2.getStatus());

        // Balance should only be reserved once
        Money availableBalance = testAccount.getBalance();
        assertEquals(0, Money.of("900.00", Currency.USD).getAmount()
            .compareTo(availableBalance.getAmount()));
    }

    private SampleProcessorWebhooks.AuthorizationWebhook createAuthWebhook(
            String processorTxnId, String cardToken, String amount,
            String merchantName, String mcc) {

        SampleProcessorWebhooks.AuthorizationWebhook webhook =
            new SampleProcessorWebhooks.AuthorizationWebhook();

        webhook.setProcessorTransactionId(processorTxnId);
        webhook.setCardToken(cardToken);
        webhook.setAmount(new BigDecimal(amount));
        webhook.setCurrency("USD");
        webhook.setTimestamp(Instant.now());
        webhook.setIdempotencyKey(IdempotencyKey.generate());

        SampleProcessorWebhooks.AuthorizationWebhook.MerchantInfo merchant =
            new SampleProcessorWebhooks.AuthorizationWebhook.MerchantInfo();
        merchant.setName(merchantName);
        merchant.setCategoryCode(mcc);
        merchant.setCity("San Francisco");
        merchant.setCountry("US");
        webhook.setMerchant(merchant);

        return webhook;
    }
}
