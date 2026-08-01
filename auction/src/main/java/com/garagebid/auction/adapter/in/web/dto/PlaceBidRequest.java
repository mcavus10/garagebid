package com.garagebid.auction.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBidRequest(
        @NotNull UUID bidderId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal amount,
        @NotBlank String currency
) {
}