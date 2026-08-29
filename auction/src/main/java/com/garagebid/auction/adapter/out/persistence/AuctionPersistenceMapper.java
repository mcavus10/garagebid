package com.garagebid.auction.adapter.out.persistence;

import com.garagebid.auction.domain.model.*;

import java.util.Currency;

final class AuctionPersistenceMapper {

    private AuctionPersistenceMapper() {}

    static AuctionJpaEntity toNewJpa(Auction auction) {
        AuctionJpaEntity entity = mapCommonFields(auction);
        entity.setVersion(null);

        return entity;
    }

    static AuctionJpaEntity toJpa(Auction auction) {
        AuctionJpaEntity entity = mapCommonFields(auction);
        entity.setVersion(auction.version());
        return entity;
    }

    static Auction toDomain(AuctionJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        Money startingPrice = new Money(entity.getStartingAmount(), currency);

        Bid highestBid = null;

        if (entity.getHighestBidderId() != null) {
            highestBid = new Bid(
                    entity.getHighestBidderId(),
                    new Money(entity.getHighestAmount(), currency),
                    entity.getHighestPlacedAt()
            );
        }

        return Auction.rehydrate(
                entity.getId(),
                entity.getCarId(),
                entity.getSellerId(),
                startingPrice,
                entity.getEndsAt(),
                AuctionStatus.valueOf(entity.getStatus()),
                highestBid,
                entity.getVersion()
        );
    }

    private static AuctionJpaEntity mapCommonFields(Auction auction) {
        AuctionJpaEntity entity = new AuctionJpaEntity();

        entity.setId(auction.id());
        entity.setCarId(auction.carId());
        entity.setSellerId(auction.sellerId());
        entity.setStartingAmount(auction.startingPrice().amount());
        entity.setCurrency(auction.startingPrice().currency().getCurrencyCode());
        entity.setEndsAt(auction.endsAt());
        entity.setStatus(auction.status().name());

        Bid bid = auction.highestBid();

        if (bid != null) {
            entity.setHighestBidderId(bid.bidderId());
            entity.setHighestAmount(bid.amount().amount());
            entity.setHighestPlacedAt(bid.placedAt());
        }

        return entity;
    }
}