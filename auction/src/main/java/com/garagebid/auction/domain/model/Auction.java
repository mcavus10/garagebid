package com.garagebid.auction.domain.model;

import com.garagebid.auction.domain.event.AuctionOpenedEvent;
import com.garagebid.auction.domain.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Auction {

    private final UUID id;
    private final UUID carId;
    private final UUID sellerId;
    private final Money startingPrice;
    private final Instant endsAt;

    private AuctionStatus status;
    private Bid highestBid;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Auction(UUID id, UUID carId, UUID sellerId, Money startingPrice,
                    Instant endsAt, AuctionStatus status, Bid highestBid) {
        this.id = id;
        this.carId = carId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.endsAt = endsAt;
        this.status = status;
        this.highestBid = highestBid;
    }

    public static Auction open(
            UUID carId,
            UUID sellerId,
            Money startingPrice,
            Instant endsAt,
            Instant now
    ) {
        UUID auctionId = UUID.randomUUID();

        Auction auction = new Auction(
                auctionId,
                carId,
                sellerId,
                startingPrice,
                endsAt,
                AuctionStatus.OPEN,
                null
        );

        auction.registerEvent(
                new AuctionOpenedEvent(
                        auctionId,
                        carId,
                        sellerId,
                        startingPrice,
                        endsAt,
                        now

                )
        );

        return auction;
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public static Auction rehydrate(UUID id, UUID carId, UUID sellerId, Money startingPrice,
                                    Instant endsAt, AuctionStatus status, Bid highestBid) {
        return new Auction(id, carId, sellerId, startingPrice, endsAt, status, highestBid);
    }


    public void placeBid(UUID bidderId, Money amount, Instant now) {
        if (status != AuctionStatus.OPEN || now.isAfter(endsAt))
            throw new AuctionNotOpenException(id);

        boolean acceptable = (highestBid == null)
                ? amount.isGreaterThanOrEqual(startingPrice)
                : amount.isGreaterThan(highestBid.amount());
        if (!acceptable)
            throw new BidTooLowException(amount,
                    highestBid == null ? startingPrice : highestBid.amount());

        this.highestBid = new Bid(bidderId, amount, now);
    }

    public void close() {
        if (status != AuctionStatus.OPEN) throw new AuctionNotOpenException(id);
        this.status = AuctionStatus.CLOSED;
    }

    public boolean hasWinner() {
        return status == AuctionStatus.CLOSED && highestBid != null;
    }

    public UUID id() { return id; }
    public UUID carId() { return carId; }
    public UUID sellerId() { return sellerId; }
    public Money startingPrice() { return startingPrice; }
    public Instant endsAt() { return endsAt; }
    public AuctionStatus status() { return status; }
    public Bid highestBid() { return highestBid; }

    @Override public boolean equals(Object o) {
        return (o instanceof Auction other) && id.equals(other.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}