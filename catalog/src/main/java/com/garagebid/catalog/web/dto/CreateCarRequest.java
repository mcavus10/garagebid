package com.garagebid.catalog.web.dto;

import com.garagebid.catalog.domain.CarCondition;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateCarRequest(
        @NotBlank String make,
        @NotBlank String model,
        @Min(1900) @Max(2100) int modelYear,
        @Min(0) int mileageKm,
        @NotNull @DecimalMin("0.0") BigDecimal priceUsd,
        String color,
        @NotNull CarCondition condition,
        String description,
        String imageUrl
) {}