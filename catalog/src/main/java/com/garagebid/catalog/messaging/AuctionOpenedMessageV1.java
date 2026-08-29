package com.garagebid.catalog.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionOpenedMessageV1(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        UUID carId,
        UUID sellerId,
        BigDecimal startingAmount,
        String currency,
        Instant endsAt
) {
}