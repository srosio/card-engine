package com.cardengine.providers.fineract;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import com.cardengine.common.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FineractAccountAdapter.
 *
 * Tests the authorization hold workaround implementation:
 * - Reserve creates shadow journal entry
 * - Commit reverses hold and debits account
 * - Release reverses hold without debit
 *
 * Uses mocks for Fineract HTTP calls to avoid dependency on running Fineract instance.
 */
@ExtendWith(MockitoExtension.class)
class FineractAccountAdapterTest {

    @Mock
    private FineractClient fineractClient;

    @Mock
    private FineractAuthHoldRepository holdRepository;

    private FineractAccountAdapter adapter;

    private static final String ACCOUNT_ID = "test-account-123";
    private static final Long FINERACT_ACCOUNT_ID = 100L;
    private static final Long CARD_AUTH_HOLDS_GL_ACCOUNT_ID = 1000L;

    @BeforeEach
    void setUp() {
        adapter = new FineractAccountAdapter(
            fineractClient,
            holdRepository,
            ACCOUNT_ID,
            FINERACT_ACCOUNT_ID,
            Currency.USD,
            CARD_AUTH_HOLDS_GL_ACCOUNT_ID
        );
    }

    @Test
    void testGetBalance_Success() {
        // Mock Fineract response
        FineractDTOs.AccountBalance balance = new FineractDTOs.AccountBalance();
        balance.setSavingsId(FINERACT_ACCOUNT_ID);
        balance.setAccountBalance(new BigDecimal("1000.00"));
        balance.setAvailableBalance(new BigDecimal("900.00"));  // $100 held
        balance.setCurrency("USD");

        when(fineractClient.getAccountBalance(FINERACT_ACCOUNT_ID)).thenReturn(balance);

        // Get balance
        Money result = adapter.getBalance();

        // Verify
        assertEquals(0, new BigDecimal("900.00").compareTo(result.getAmount()));
        assertEquals(Currency.USD, result.getCurrency());
        verify(fineractClient).getAccountBalance(FINERACT_ACCOUNT_ID);
    }

    @Test
    void testReserve_Success() {
        String authId = "auth-123";
        Money amount = Money.of("100.00", Currency.USD);

        // Mock balance check
        FineractDTOs.AccountBalance balance = new FineractDTOs.AccountBalance();
        balance.setAvailableBalance(new BigDecimal("1000.00"));
        when(fineractClient.getAccountBalance(FINERACT_ACCOUNT_ID)).thenReturn(balance);

        // Mock no existing hold
        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.empty());

        // Mock journal entry creation
        FineractDTOs.JournalEntryResponse journalResponse = new FineractDTOs.JournalEntryResponse();
        journalResponse.setTransactionId(500L);
        when(fineractClient.createJournalEntry(any())).thenReturn(journalResponse);

        // Mock save hold
        when(holdRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Reserve funds
        adapter.reserve(amount, authId);

        // Verify journal entry was created
        verify(fineractClient).createJournalEntry(argThat(request ->
            request.getReferenceNumber().equals(authId) &&
            request.getComments().contains("authorization hold")
        ));

        // Verify hold was saved
        verify(holdRepository).save(argThat(hold ->
            hold.getAuthorizationId().equals(authId) &&
            hold.getHoldAmount().compareTo(new BigDecimal("100.00")) == 0 &&
            hold.getStatus() == FineractAuthHold.HoldStatus.ACTIVE
        ));
    }

    @Test
    void testReserve_InsufficientFunds() {
        String authId = "auth-124";
        Money amount = Money.of("2000.00", Currency.USD);

        // Mock balance check - insufficient funds
        FineractDTOs.AccountBalance balance = new FineractDTOs.AccountBalance();
        balance.setAvailableBalance(new BigDecimal("1000.00"));
        when(fineractClient.getAccountBalance(FINERACT_ACCOUNT_ID)).thenReturn(balance);

        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.empty());

        // Should throw insufficient funds exception
        assertThrows(InsufficientFundsException.class, () ->
            adapter.reserve(amount, authId)
        );

        // Verify journal entry was NOT created
        verify(fineractClient, never()).createJournalEntry(any());
    }

    @Test
    void testReserve_Idempotency() {
        String authId = "auth-125";
        Money amount = Money.of("100.00", Currency.USD);

        // Mock existing hold (duplicate request)
        FineractAuthHold existingHold = new FineractAuthHold();
        existingHold.setAuthorizationId(authId);
        existingHold.setStatus(FineractAuthHold.HoldStatus.ACTIVE);
        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.of(existingHold));

        // Reserve funds (duplicate)
        adapter.reserve(amount, authId);

        // Verify journal entry was NOT created again
        verify(fineractClient, never()).createJournalEntry(any());
        verify(fineractClient, never()).getAccountBalance(any());
    }

    @Test
    void testCommit_Success() {
        String authId = "auth-126";
        Money amount = Money.of("100.00", Currency.USD);

        // Mock existing hold
        FineractAuthHold hold = new FineractAuthHold(
            authId,
            FINERACT_ACCOUNT_ID,
            500L,  // Journal entry ID
            new BigDecimal("100.00"),
            "USD"
        );
        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.of(hold));

        // Mock debit response
        FineractDTOs.SavingsTransactionResponse debitResponse = new FineractDTOs.SavingsTransactionResponse();
        debitResponse.setResourceId(600L);
        when(fineractClient.debitAccount(eq(FINERACT_ACCOUNT_ID), any())).thenReturn(debitResponse);

        when(holdRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Commit funds
        adapter.commit(amount, authId);

        // Verify debit was made
        verify(fineractClient).debitAccount(eq(FINERACT_ACCOUNT_ID), argThat(request ->
            request.getTransactionAmount().compareTo(new BigDecimal("100.00")) == 0 &&
            request.getReferenceNumber().equals(authId)
        ));

        // Verify hold was marked as committed
        verify(holdRepository).save(argThat(savedHold ->
            savedHold.getStatus() == FineractAuthHold.HoldStatus.COMMITTED
        ));
    }

    @Test
    void testCommit_HoldNotFound() {
        String authId = "auth-127";
        Money amount = Money.of("100.00", Currency.USD);

        // Mock no hold found
        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.empty());

        // Should throw exception
        assertThrows(IllegalStateException.class, () ->
            adapter.commit(amount, authId)
        );

        // Verify debit was NOT made
        verify(fineractClient, never()).debitAccount(any(), any());
    }

    @Test
    void testRelease_Success() {
        String authId = "auth-128";
        Money amount = Money.of("100.00", Currency.USD);

        // Mock existing hold
        FineractAuthHold hold = new FineractAuthHold(
            authId,
            FINERACT_ACCOUNT_ID,
            500L,
            new BigDecimal("100.00"),
            "USD"
        );
        when(holdRepository.findByAuthorizationId(authId)).thenReturn(Optional.of(hold));
        when(holdRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Release hold
        adapter.release(amount, authId);

        // Verify hold was marked as released
        verify(holdRepository).save(argThat(savedHold ->
            savedHold.getStatus() == FineractAuthHold.HoldStatus.RELEASED
        ));

        // Verify no debit was made (unlike commit)
        verify(fineractClient, never()).debitAccount(any(), any());
    }

    @Test
    void testGetReservedBalance() {
        // Mock multiple active holds
        FineractAuthHold hold1 = new FineractAuthHold("auth-1", FINERACT_ACCOUNT_ID, 501L,
            new BigDecimal("100.00"), "USD");
        FineractAuthHold hold2 = new FineractAuthHold("auth-2", FINERACT_ACCOUNT_ID, 502L,
            new BigDecimal("50.00"), "USD");

        when(holdRepository.findByFineractAccountIdAndStatus(
            FINERACT_ACCOUNT_ID,
            FineractAuthHold.HoldStatus.ACTIVE
        )).thenReturn(List.of(hold1, hold2));

        // Get reserved balance
        Money reserved = adapter.getReservedBalance();

        // Should sum all active holds
        assertEquals(0, new BigDecimal("150.00").compareTo(reserved.getAmount()));
        assertEquals(Currency.USD, reserved.getCurrency());
    }

    @Test
    void testAccountMetadata() {
        assertEquals(ACCOUNT_ID, adapter.getAccountId());
        assertEquals(com.cardengine.accounts.AccountType.EXTERNAL_CUSTODIAL, adapter.getAccountType());
        assertEquals(Currency.USD, adapter.getCurrency());
    }
}
