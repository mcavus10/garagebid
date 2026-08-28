package com.garagebid.auction.domain.event;

import com.garagebid.auction.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain fact:
 * an auction has successfully been opened.
 * This is NOT yet our Kafka contract.
 * Integration-event serialization will be handled at a later boundary.
 */
public record AuctionOpenedEvent(
        UUID auctionId,
        UUID carId,
        UUID sellerId,
        Money startingPrice,
        Instant endsAt,
        Instant occurredAt
) implements DomainEvent {
}