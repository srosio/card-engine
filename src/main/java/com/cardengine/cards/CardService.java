package com.cardengine.cards;

import com.cardengine.accounts.Account;
import com.cardengine.accounts.AccountService;
import com.cardengine.common.exception.CardNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing card lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final AccountService accountService;

    @Transactional
    public Card issueCard(String cardholderName, String last4, LocalDate expirationDate,
                          String fundingAccountId, String ownerId) {
        // Validate that the funding account exists
        Account account = accountService.getAccount(fundingAccountId);

        Card card = new Card(cardholderName, last4, expirationDate, fundingAccountId, ownerId);
        cardRepository.save(card);

        log.info("Issued card {} for {} backed by {} account {}",
            card.getCardId(), cardholderName, account.getAccountType(), fundingAccountId);

        return card;
    }

    @Transactional(readOnly = true)
    public Card getCard(String cardId) {
        return cardRepository.findByCardId(cardId)
            .orElseThrow(() -> new CardNotFoundException(cardId));
    }

    @Transactional(readOnly = true)
    public List<Card> getCardsByOwner(String ownerId) {
        return cardRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public void freezeCard(String cardId) {
        Card card = getCard(cardId);
        card.freeze();
        cardRepository.save(card);
        log.info("Froze card {}", cardId);
    }

    @Transactional
    public void unfreezeCard(String cardId) {
        Card card = getCard(cardId);
        card.unfreeze();
        cardRepository.save(card);
        log.info("Unfroze card {}", cardId);
    }

    @Transactional
    public void closeCard(String cardId) {
        Card card = getCard(cardId);
        card.close();
        cardRepository.save(card);
        log.info("Closed card {}", cardId);
    }
}
