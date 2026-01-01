package com.cardengine.api.dto;

import com.cardengine.common.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for authorizing a card transaction.
 */
@Data
public class AuthorizeTransactionRequest {

    @NotBlank(message = "Card ID is required")
    private String cardId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Currency currency;

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    private String merchantCategoryCode;

    private String merchantCity;

    private String merchantCountry;
}
