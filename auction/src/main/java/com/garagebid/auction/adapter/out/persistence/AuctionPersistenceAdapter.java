package com.garagebid.auction.adapter.out.persistence;

import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuctionPersistenceAdapter implements LoadAuctionPort, SaveAuctionPort {

    private final AuctionJpaRepository repository;

    public AuctionPersistenceAdapter(AuctionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Auction> loadById(UUID auctionId) {
        return repository.findById(auctionId).map(AuctionPersistenceMapper::toDomain);
    }

    @Override
    public Auction save(Auction auction) {
        AuctionJpaEntity saved = repository.save(AuctionPersistenceMapper.toJpa(auction));
        return AuctionPersistenceMapper.toDomain(saved);
    }
}