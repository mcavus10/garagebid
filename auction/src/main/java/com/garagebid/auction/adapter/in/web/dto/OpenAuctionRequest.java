package com.garagebid.auction.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OpenAuctionRequest(
        @NotNull UUID carId,
        @NotNull UUID sellerId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal startingAmount,
        @NotBlank String currency,
        @NotNull Instant endsAt
) {
}