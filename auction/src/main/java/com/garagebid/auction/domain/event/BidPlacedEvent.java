package com.garagebid.auction.domain.event;

import com.garagebid.auction.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID auctionId,
        UUID bidderId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {
}