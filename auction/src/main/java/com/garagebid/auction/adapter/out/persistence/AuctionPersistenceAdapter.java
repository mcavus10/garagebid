package com.garagebid.auction.adapter.out.persistence;

import com.garagebid.auction.application.port.out.CreateAuctionPort;
import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.domain.model.Auction;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuctionPersistenceAdapter
        implements LoadAuctionPort, CreateAuctionPort, SaveAuctionPort {

    private final AuctionJpaRepository repository;
    private final EntityManager entityManager;

    public AuctionPersistenceAdapter(
            AuctionJpaRepository repository,
            EntityManager entityManager
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Auction> loadById(UUID auctionId) {
        return repository.findById(auctionId)
                .map(AuctionPersistenceMapper::toDomain);
    }

    @Override
    public void create(Auction auction) {
        AuctionJpaEntity entity = AuctionPersistenceMapper.toNewJpa(auction);
        entityManager.persist(entity);
    }

    @Override
    public Auction save(Auction auction) {
        AuctionJpaEntity saved = repository.save(AuctionPersistenceMapper.toJpa(auction));
        return AuctionPersistenceMapper.toDomain(saved);
    }
}