package com.cardengine.api.controller;

import com.cardengine.api.dto.CreateCardRequest;
import com.cardengine.cards.Card;
import com.cardengine.cards.CardService;
import com.cardengine.ledger.LedgerEntry;
import com.cardengine.ledger.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for card management.
 */
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card management API")
public class CardController {

    private final CardService cardService;
    private final LedgerService ledgerService;

    @PostMapping
    @Operation(summary = "Issue a new card")
    public ResponseEntity<Card> issueCard(@Valid @RequestBody CreateCardRequest request) {
        Card card = cardService.issueCard(
            request.getCardholderName(),
            request.getLast4(),
            request.getExpirationDate(),
            request.getFundingAccountId(),
            request.getOwnerId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(card);
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get card details")
    public ResponseEntity<Card> getCard(@PathVariable String cardId) {
        Card card = cardService.getCard(cardId);
        return ResponseEntity.ok(card);
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "Get all cards for an owner")
    public ResponseEntity<List<Card>> getCardsByOwner(@PathVariable String ownerId) {
        List<Card> cards = cardService.getCardsByOwner(ownerId);
        return ResponseEntity.ok(cards);
    }

    @PostMapping("/{cardId}/freeze")
    @Operation(summary = "Freeze a card")
    public ResponseEntity<Void> freezeCard(@PathVariable String cardId) {
        cardService.freezeCard(cardId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{cardId}/unfreeze")
    @Operation(summary = "Unfreeze a card")
    public ResponseEntity<Void> unfreezeCard(@PathVariable String cardId) {
        cardService.unfreezeCard(cardId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{cardId}/close")
    @Operation(summary = "Close a card permanently")
    public ResponseEntity<Void> closeCard(@PathVariable String cardId) {
        cardService.closeCard(cardId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{cardId}/transactions")
    @Operation(summary = "Get transaction history for a card")
    public ResponseEntity<List<LedgerEntry>> getCardTransactions(@PathVariable String cardId) {
        List<LedgerEntry> entries = ledgerService.getCardLedger(cardId);
        return ResponseEntity.ok(entries);
    }
}
