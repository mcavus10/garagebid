package com.garagebid.auction.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionBidPlacedIntegrationEvent(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        UUID bidderId,
        BigDecimal amount,
        String currency
) implements IntegrationEvent {

    public static final String TYPE = "auction.bid-placed";
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