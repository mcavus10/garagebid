package com.garagebid.auction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface AuctionJpaRepository extends JpaRepository<AuctionJpaEntity, UUID> {
}