package com.garagebid.auction.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionOpenedIntegrationEvent(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        UUID carId,
        UUID sellerId,
        BigDecimal startingAmount,
        String currency,
        Instant endsAt
) implements IntegrationEvent {

    public static final String TYPE = "auction.opened";
    public static final int VERSION = 1;

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public int eventVersion() {
        return VERSION;
    }
}