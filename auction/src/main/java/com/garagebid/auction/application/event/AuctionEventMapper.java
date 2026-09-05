package com.garagebid.auction.application.event;

import com.garagebid.auction.domain.event.AuctionClosedEvent;
import com.garagebid.auction.domain.event.AuctionOpenedEvent;
import com.garagebid.auction.domain.event.BidPlacedEvent;
import com.garagebid.auction.domain.event.DomainEvent;
import com.garagebid.auction.domain.model.Bid;
import com.garagebid.auction.domain.model.Money;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuctionEventMapper {

    public IntegrationEvent toIntegrationEvent(DomainEvent domainEvent) {
        if (domainEvent instanceof AuctionOpenedEvent(
                UUID auctionId,
                UUID carId,
                UUID sellerId,
                Money startingPrice,
                Instant endsAt,
                Instant occurredAt
        )) {
            return new AuctionOpenedIntegrationEvent(
                    UUID.randomUUID(),
                    auctionId,
                    occurredAt,
                    carId,
                    sellerId,
                    startingPrice.amount(),
                    startingPrice.currency().getCurrencyCode(),
                    endsAt
            );
        }

        if (domainEvent instanceof BidPlacedEvent(
                UUID auctionId,
                UUID bidderId,
                Money amount,
                Instant occurredAt
        )) {
            return new AuctionBidPlacedIntegrationEvent(
                    UUID.randomUUID(),
                    auctionId,
                    occurredAt,
                    bidderId,
                    amount.amount(),
                    amount.currency().getCurrencyCode()
            );
        }

        if (domainEvent instanceof AuctionClosedEvent(
                UUID auctionId,
                Bid winningBid,
                Instant occurredAt
        )) {
            return new AuctionClosedIntegrationEvent(
                    UUID.randomUUID(),
                    auctionId,
                    occurredAt,
                    winningBid == null ? null : winningBid.bidderId(),
                    winningBid == null ? null : winningBid.amount().amount(),
                    winningBid == null
                            ? null
                            : winningBid.amount().currency().getCurrencyCode()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported domain event: " + domainEvent.getClass().getName()
        );
    }
}