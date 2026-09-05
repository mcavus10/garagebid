package com.garagebid.auction.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionClosedIntegrationEvent(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        UUID winnerId,
        BigDecimal winningAmount,
        String currency
) implements IntegrationEvent {

    public static final String TYPE = "auction.closed";
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