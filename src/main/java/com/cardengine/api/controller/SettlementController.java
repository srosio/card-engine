package com.cardengine.api.controller;

import com.cardengine.common.Currency;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.settlement.ClearingRequest;
import com.cardengine.settlement.ReversalRequest;
import com.cardengine.settlement.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST API for settlement operations.
 */
@RestController
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
@Tag(name = "Settlement", description = "Settlement and clearing API")
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/clear/{authorizationId}")
    @Operation(summary = "Clear (settle) an authorized transaction")
    public ResponseEntity<Void> clearTransaction(
            @PathVariable String authorizationId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency) {

        ClearingRequest request = ClearingRequest.builder()
            .authorizationId(authorizationId)
            .clearingAmount(Money.of(amount, Currency.valueOf(currency)))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.clearTransaction(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/release/{authorizationId}")
    @Operation(summary = "Release an authorization without clearing")
    public ResponseEntity<Void> releaseAuthorization(@PathVariable String authorizationId) {
        settlementService.releaseAuthorization(authorizationId, IdempotencyKey.generate());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reverse/{authorizationId}")
    @Operation(summary = "Reverse (refund) a cleared transaction")
    public ResponseEntity<Void> reverseTransaction(
            @PathVariable String authorizationId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency) {

        ReversalRequest request = ReversalRequest.builder()
            .authorizationId(authorizationId)
            .reversalAmount(Money.of(amount, Currency.valueOf(currency)))
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        settlementService.reverseTransaction(request);
        return ResponseEntity.ok().build();
    }
}
