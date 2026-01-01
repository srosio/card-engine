package com.cardengine.api.controller;

import com.cardengine.api.dto.AuthorizeTransactionRequest;
import com.cardengine.authorization.*;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for card authorizations.
 */
@RestController
@RequestMapping("/api/v1/authorizations")
@RequiredArgsConstructor
@Tag(name = "Authorizations", description = "Card authorization API")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Authorize a card transaction")
    public ResponseEntity<AuthorizationResponse> authorize(
            @Valid @RequestBody AuthorizeTransactionRequest request) {

        String authorizationId = UUID.randomUUID().toString();

        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .authorizationId(authorizationId)
            .cardId(request.getCardId())
            .amount(Money.of(request.getAmount(), request.getCurrency()))
            .merchantName(request.getMerchantName())
            .merchantCategoryCode(request.getMerchantCategoryCode())
            .merchantCity(request.getMerchantCity())
            .merchantCountry(request.getMerchantCountry())
            .idempotencyKey(IdempotencyKey.generate())
            .build();

        AuthorizationResponse response = authorizationService.authorize(authRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{authorizationId}")
    @Operation(summary = "Get authorization details")
    public ResponseEntity<Authorization> getAuthorization(@PathVariable String authorizationId) {
        Authorization authorization = authorizationService.getAuthorization(authorizationId);
        return ResponseEntity.ok(authorization);
    }
}
