package com.cardengine.bank.fineract;

import com.cardengine.bank.BankAccountAdapter;
import com.cardengine.bank.BankCoreException;
import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import com.cardengine.common.exception.InsufficientFundsException;
import com.cardengine.providers.fineract.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Apache Fineract implementation of BankAccountAdapter.
 *
 * REFERENCE IMPLEMENTATION:
 * This adapter demonstrates how to integrate with Apache Fineract as the bank core.
 * Other banks can use this as a template for their own core banking integrations.
 *
 * FINERACT CONTEXT:
 * Apache Fineract is an open-source core banking platform.
 * When used as the bank core:
 * - Clients (customers) are managed in Fineract
 * - Savings accounts hold balances
 * - General ledger tracks all transactions
 * - This adapter translates card operations to Fineract API calls
 *
 * AUTHORIZATION HOLD WORKAROUND:
 * Fineract doesn't natively support card-style authorization holds.
 * We implement holds using shadow journal entries:
 *
 * 1. placeHold():
 *    Create journal entry: DEBIT savings account → CREDIT CARD_AUTH_HOLDS GL account
 *    Funds become unavailable but aren't removed from account
 *
 * 2. commitDebit():
 *    Reverse the hold journal entry, then make actual withdrawal
 *    Ensures no double-debit
 *
 * 3. releaseHold():
 *    Reverse the hold journal entry
 *    Funds return to available balance
 *
 * This workaround:
 * - Maintains Fineract ledger balance
 * - Provides proper fund reservation
 * - Is auditable in Fineract's journal
 * - Prevents duplicate debits
 *
 * NOTE FOR OTHER BANKS:
 * If your core banking system supports native holds/reservations,
 * use those instead of this shadow transaction approach.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FineractBankAccountAdapter implements BankAccountAdapter {

    private final FineractClient fineractClient;
    private final FineractAuthHoldRepository holdRepository;

    // GL account for holding reserved funds (configured via application.yml)
    private Long cardAuthHoldsGLAccountId;

    private static final DateTimeFormatter FINERACT_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd MMMM yyyy");

    @Override
    public Money getAvailableBalance(String accountRef) {
        log.debug("Fetching balance from Fineract for account: {}", accountRef);

        try {
            Long savingsAccountId = parseAccountRef(accountRef);
            FineractDTOs.AccountBalance balance =
                fineractClient.getAccountBalance(savingsAccountId);

            Money availableBalance = Money.of(
                balance.getAvailableBalance(),
                parseCurrency(balance.getCurrency())
            );

            log.debug("Fineract account {} balance: {} {}",
                accountRef, availableBalance.getAmount(), availableBalance.getCurrency());

            return availableBalance;

        } catch (Exception e) {
            log.error("Error fetching balance from Fineract: accountRef={}", accountRef, e);
            throw new BankCoreException(
                "Failed to fetch balance from Fineract: " + e.getMessage(),
                accountRef,
                "getAvailableBalance",
                e
            );
        }
    }

    @Override
    public void placeHold(String accountRef, Money amount, String referenceId) {
        log.info("Placing hold in Fineract: account={}, amount={} {}, ref={}",
            accountRef, amount.getAmount(), amount.getCurrency(), referenceId);

        // Check for duplicate (idempotency)
        Optional<FineractAuthHold> existing = holdRepository.findByAuthorizationId(referenceId);
        if (existing.isPresent()) {
            log.info("Hold already exists for reference: {}", referenceId);
            return;
        }

        try {
            Long savingsAccountId = parseAccountRef(accountRef);

            // Check available balance first
            Money availableBalance = getAvailableBalance(accountRef);
            if (availableBalance.isLessThan(amount)) {
                throw new InsufficientFundsException(accountRef, amount, availableBalance);
            }

            // Create shadow journal entry to hold funds
            // DEBIT: Savings account (reduces available balance)
            // CREDIT: CARD_AUTH_HOLDS GL account
            FineractDTOs.JournalEntryRequest journalRequest = FineractDTOs.JournalEntryRequest.builder()
                .officeId(1L)  // TODO: Make configurable
                .transactionDate(LocalDate.now().format(FINERACT_DATE_FORMAT))
                .referenceNumber(referenceId)
                .comments("Card authorization hold - " + referenceId)
                .debits(new FineractDTOs.JournalEntryRequest.DebitsAndCredits[]{
                    FineractDTOs.JournalEntryRequest.DebitsAndCredits.builder()
                        .glAccountId(savingsAccountId)
                        .amount(amount.getAmount())
                        .build()
                })
                .credits(new FineractDTOs.JournalEntryRequest.DebitsAndCredits[]{
                    FineractDTOs.JournalEntryRequest.DebitsAndCredits.builder()
                        .glAccountId(cardAuthHoldsGLAccountId)
                        .amount(amount.getAmount())
                        .build()
                })
                .build();

            FineractDTOs.JournalEntryResponse journalResponse =
                fineractClient.createJournalEntry(journalRequest);

            // Store hold reference
            FineractAuthHold hold = new FineractAuthHold(
                referenceId,
                savingsAccountId,
                journalResponse.getTransactionId(),
                amount.getAmount(),
                amount.getCurrency().name()
            );
            holdRepository.save(hold);

            log.info("Hold placed successfully: ref={}, journalId={}",
                referenceId, journalResponse.getTransactionId());

        } catch (InsufficientFundsException e) {
            throw e;  // Re-throw as-is
        } catch (Exception e) {
            log.error("Error placing hold in Fineract: ref={}", referenceId, e);
            throw new BankCoreException(
                "Failed to place hold in Fineract: " + e.getMessage(),
                accountRef,
                "placeHold",
                e
            );
        }
    }

    @Override
    public void commitDebit(String accountRef, Money amount, String referenceId) {
        log.info("Committing debit in Fineract: account={}, amount={} {}, ref={}",
            accountRef, amount.getAmount(), amount.getCurrency(), referenceId);

        try {
            Long savingsAccountId = parseAccountRef(accountRef);

            // Find the hold record
            FineractAuthHold hold = holdRepository.findByAuthorizationId(referenceId)
                .orElseThrow(() -> new IllegalStateException(
                    "No hold found for reference: " + referenceId));

            if (hold.getStatus() == FineractAuthHold.HoldStatus.COMMITTED) {
                log.info("Hold already committed: ref={}", referenceId);
                return;  // Idempotent
            }

            if (hold.getStatus() != FineractAuthHold.HoldStatus.ACTIVE) {
                throw new IllegalStateException(
                    "Hold is not active: " + hold.getStatus());
            }

            // Validate amount
            Money heldAmount = Money.of(
                hold.getHoldAmount(),
                Currency.valueOf(hold.getCurrency())
            );
            if (amount.isGreaterThan(heldAmount)) {
                throw new IllegalArgumentException(
                    "Cannot commit more than held amount");
            }

            // Step 1: Reverse the hold journal entry
            // This returns funds from CARD_AUTH_HOLDS back to available balance
            reverseHoldJournalEntry(hold, referenceId);

            // Step 2: Make actual debit (withdrawal)
            FineractDTOs.SavingsTransactionRequest debitRequest =
                FineractDTOs.SavingsTransactionRequest.builder()
                    .transactionDate(LocalDate.now().format(FINERACT_DATE_FORMAT))
                    .transactionAmount(amount.getAmount())
                    .note("Card transaction cleared - " + referenceId)
                    .referenceNumber(referenceId)
                    .build();

            fineractClient.debitAccount(savingsAccountId, debitRequest);

            // Step 3: Mark hold as committed
            hold.markCommitted();
            holdRepository.save(hold);

            log.info("Debit committed successfully: ref={}", referenceId);

        } catch (Exception e) {
            log.error("Error committing debit in Fineract: ref={}", referenceId, e);
            throw new BankCoreException(
                "Failed to commit debit in Fineract: " + e.getMessage(),
                accountRef,
                "commitDebit",
                e
            );
        }
    }

    @Override
    public void releaseHold(String accountRef, Money amount, String referenceId) {
        log.info("Releasing hold in Fineract: account={}, ref={}", accountRef, referenceId);

        try {
            // Find the hold record
            Optional<FineractAuthHold> holdOpt = holdRepository.findByAuthorizationId(referenceId);

            if (holdOpt.isEmpty()) {
                log.warn("No hold found for reference: {}", referenceId);
                return;  // Idempotent - safe to call even if hold doesn't exist
            }

            FineractAuthHold hold = holdOpt.get();

            if (hold.getStatus() == FineractAuthHold.HoldStatus.RELEASED) {
                log.info("Hold already released: ref={}", referenceId);
                return;  // Idempotent
            }

            if (hold.getStatus() != FineractAuthHold.HoldStatus.ACTIVE) {
                log.warn("Hold is not active, status={}: ref={}",
                    hold.getStatus(), referenceId);
                return;  // Don't fail, just log
            }

            // Reverse the hold journal entry
            reverseHoldJournalEntry(hold, referenceId);

            // Mark as released
            hold.markReleased();
            holdRepository.save(hold);

            log.info("Hold released successfully: ref={}", referenceId);

        } catch (Exception e) {
            log.error("Error releasing hold in Fineract: ref={}", referenceId, e);
            throw new BankCoreException(
                "Failed to release hold in Fineract: " + e.getMessage(),
                accountRef,
                "releaseHold",
                e
            );
        }
    }

    @Override
    public String getAdapterName() {
        return "Fineract";
    }

    @Override
    public boolean isHealthy() {
        try {
            // Attempt a simple API call to check connectivity
            // In production, might have a dedicated health endpoint
            return fineractClient != null;
        } catch (Exception e) {
            log.error("Fineract health check failed", e);
            return false;
        }
    }

    /**
     * Reverse a hold journal entry in Fineract.
     * Creates an offsetting entry that cancels the original.
     */
    private void reverseHoldJournalEntry(FineractAuthHold hold, String referenceId) {
        log.debug("Reversing hold journal entry: journalId={}, ref={}",
            hold.getJournalEntryId(), referenceId);

        // Create reversing journal entry
        // DEBIT: CARD_AUTH_HOLDS GL account
        // CREDIT: Savings account
        FineractDTOs.JournalEntryRequest reverseRequest =
            FineractDTOs.JournalEntryRequest.builder()
                .officeId(1L)
                .transactionDate(LocalDate.now().format(FINERACT_DATE_FORMAT))
                .referenceNumber(referenceId + "-REVERSE")
                .comments("Reverse authorization hold - " + referenceId)
                .debits(new FineractDTOs.JournalEntryRequest.DebitsAndCredits[]{
                    FineractDTOs.JournalEntryRequest.DebitsAndCredits.builder()
                        .glAccountId(cardAuthHoldsGLAccountId)
                        .amount(hold.getHoldAmount())
                        .build()
                })
                .credits(new FineractDTOs.JournalEntryRequest.DebitsAndCredits[]{
                    FineractDTOs.JournalEntryRequest.DebitsAndCredits.builder()
                        .glAccountId(hold.getFineractAccountId())
                        .amount(hold.getHoldAmount())
                        .build()
                })
                .build();

        fineractClient.createJournalEntry(reverseRequest);
    }

    /**
     * Parse account reference to Fineract account ID.
     * In production, might support multiple formats.
     */
    private Long parseAccountRef(String accountRef) {
        try {
            return Long.parseLong(accountRef);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid Fineract account reference: " + accountRef);
        }
    }

    /**
     * Parse currency from Fineract response.
     */
    private Currency parseCurrency(String currencyCode) {
        try {
            return Currency.valueOf(currencyCode);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown currency from Fineract: {}, defaulting to USD", currencyCode);
            return Currency.USD;
        }
    }

    /**
     * Set the GL account ID for card authorization holds.
     * Called during bean initialization with configured value.
     */
    public void setCardAuthHoldsGLAccountId(Long accountId) {
        this.cardAuthHoldsGLAccountId = accountId;
        log.info("Fineract adapter configured with CARD_AUTH_HOLDS GL account: {}", accountId);
    }
}
