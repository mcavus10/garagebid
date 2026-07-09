package com.garagebid.auction.adapter.out.persistence;

import com.garagebid.auction.domain.model.*;

import java.util.Currency;

final class AuctionPersistenceMapper {

    private AuctionPersistenceMapper() {}

    static AuctionJpaEntity toJpa(Auction a) {
        AuctionJpaEntity e = new AuctionJpaEntity();
        e.setId(a.id());
        e.setCarId(a.carId());
        e.setSellerId(a.sellerId());
        e.setStartingAmount(a.startingPrice().amount());
        e.setCurrency(a.startingPrice().currency().getCurrencyCode());
        e.setEndsAt(a.endsAt());
        e.setStatus(a.status().name());
        Bid bid = a.highestBid();
        if (bid != null) {
            e.setHighestBidderId(bid.bidderId());
            e.setHighestAmount(bid.amount().amount());
            e.setHighestPlacedAt(bid.placedAt());
        }
        return e;
    }

    static Auction toDomain(AuctionJpaEntity e) {
        Currency currency = Currency.getInstance(e.getCurrency());
        Money startingPrice = new Money(e.getStartingAmount(), currency);

        Bid highestBid = null;
        if (e.getHighestBidderId() != null) {
            highestBid = new Bid(
                    e.getHighestBidderId(),
                    new Money(e.getHighestAmount(), currency),
                    e.getHighestPlacedAt());
        }

        return Auction.rehydrate(
                e.getId(), e.getCarId(), e.getSellerId(), startingPrice,
                e.getEndsAt(), AuctionStatus.valueOf(e.getStatus()), highestBid);
    }
}