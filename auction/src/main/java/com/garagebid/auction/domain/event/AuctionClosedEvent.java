package com.garagebid.auction.domain.event;

import com.garagebid.auction.domain.model.Bid;

import java.time.Instant;
import java.util.UUID;

public record AuctionClosedEvent(
        UUID auctionId,
        Bid winningBid,
        Instant occurredAt
) implements DomainEvent {
}