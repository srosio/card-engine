package com.cardengine.bank;

import com.cardengine.cards.Card;
import com.cardengine.cards.CardRepository;
import com.cardengine.cards.CardState;
import com.cardengine.common.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for issuing cards linked to bank core accounts.
 *
 * BANK INTEGRATION PRINCIPLES:
 * - Bank accounts MUST exist before card issuance
 * - Cards are payment instruments, NOT account holders
 * - Bank core is the source of truth for client and account data
 * - This service only creates the link between card and bank account
 *
 * ISSUANCE FLOW:
 * 1. Verify bank account exists and has sufficient balance (via BankAccountAdapter)
 * 2. Create card in INACTIVE state
 * 3. Create immutable mapping: card → bank account
 * 4. Card can be activated separately
 *
 * NOT HANDLED HERE:
 * - Client/customer creation (done in bank core)
 * - Account opening (done in bank core)
 * - KYC/compliance (bank responsibility)
 * - Initial funding (account already has balance)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankCardIssuanceService {

    private final CardRepository cardRepository;
    private final BankAccountMappingRepository mappingRepository;
    private final BankAccountAdapter bankAccountAdapter;

    /**
     * Issue a card linked to an existing bank account.
     *
     * @param bankClientRef bank's client/customer ID
     * @param bankAccountRef bank's account ID (must exist in bank core)
     * @param cardholderName name for the card
     * @param expirationDate card expiration date
     * @param issuedBy who is issuing the card (for audit)
     * @return issued card (in INACTIVE state)
     */
    @Transactional
    public Card issueCardForBankAccount(
            String bankClientRef,
            String bankAccountRef,
            String cardholderName,
            LocalDate expirationDate,
            String issuedBy) {

        log.info("Issuing card for bank account: client={}, account={}",
            bankClientRef, bankAccountRef);

        // Validate bank account exists and is accessible
        try {
            Money balance = bankAccountAdapter.getAvailableBalance(bankAccountRef);
            log.debug("Bank account {} verified, balance: {} {}",
                bankAccountRef, balance.getAmount(), balance.getCurrency());
        } catch (Exception e) {
            log.error("Failed to verify bank account: {}", bankAccountRef, e);
            throw new IllegalArgumentException(
                "Bank account not found or inaccessible: " + bankAccountRef, e);
        }

        // Generate card identifiers
        String cardId = UUID.randomUUID().toString();
        String last4 = generateLast4();  // In production, from card processor

        // Create card in INACTIVE state
        // Card is not usable until explicitly activated
        Card card = new Card();
        card.setCardId(cardId);
        card.setCardholderName(cardholderName);
        card.setLast4(last4);
        card.setExpirationDate(expirationDate);
        card.setState(CardState.FROZEN);  // Start inactive for security
        card.setOwnerId(bankClientRef);  // Link to bank client
        card.setFundingAccountId(bankAccountRef);  // Temporary - will use mapping

        cardRepository.save(card);

        // Create immutable mapping to bank account
        BankAccountMapping mapping = new BankAccountMapping(
            cardId,
            bankClientRef,
            bankAccountRef,
            bankAccountAdapter.getAdapterName(),
            issuedBy
        );
        mappingRepository.save(mapping);

        log.info("Card issued successfully: cardId={}, last4={}, bankAccount={}",
            cardId, last4, bankAccountRef);

        return card;
    }

    /**
     * Activate a card.
     * Makes the card usable for transactions.
     */
    @Transactional
    public void activateCard(String cardId) {
        Card card = cardRepository.findByCardId(cardId)
            .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        if (card.getState() == CardState.CLOSED) {
            throw new IllegalStateException("Cannot activate a closed card");
        }

        card.setState(CardState.ACTIVE);
        cardRepository.save(card);

        log.info("Card activated: {}", cardId);
    }

    /**
     * Get bank account mapping for a card.
     */
    @Transactional(readOnly = true)
    public BankAccountMapping getBankAccountMapping(String cardId) {
        return mappingRepository.findByCardId(cardId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No bank account mapping found for card: " + cardId));
    }

    /**
     * Generate random last 4 digits.
     * In production, this would come from the card processor.
     */
    private String generateLast4() {
        return String.format("%04d", (int)(Math.random() * 10000));
    }
}
