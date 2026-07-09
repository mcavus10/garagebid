package com.garagebid.catalog.web.dto;

import com.garagebid.catalog.domain.CarCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CarResponse(
        UUID id,
        String make,
        String model,
        int modelYear,
        int mileageKm,
        BigDecimal priceUsd,
        String color,
        CarCondition condition,
        String description,
        String imageUrl,
        Instant createdAt
) {}