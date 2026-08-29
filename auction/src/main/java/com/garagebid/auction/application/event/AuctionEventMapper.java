package com.garagebid.auction.application.event;

import com.garagebid.auction.domain.event.AuctionOpenedEvent;
import com.garagebid.auction.domain.event.DomainEvent;
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

        throw new IllegalArgumentException(
                "Unsupported domain event: "
                        + domainEvent.getClass().getName()
        );
    }
}