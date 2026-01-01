package com.cardengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO for issuing a new card.
 */
@Data
public class CreateCardRequest {

    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;

    @NotBlank(message = "Last 4 digits are required")
    @Pattern(regexp = "\\d{4}", message = "Last 4 must be 4 digits")
    private String last4;

    @NotNull(message = "Expiration date is required")
    private LocalDate expirationDate;

    @NotBlank(message = "Funding account ID is required")
    private String fundingAccountId;

    @NotBlank(message = "Owner ID is required")
    private String ownerId;
}
