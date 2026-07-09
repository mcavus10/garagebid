package com.garagebid.auction.application.port.out;

import com.garagebid.auction.domain.model.Auction;
import java.util.Optional;
import java.util.UUID;

public interface LoadAuctionPort {
    Optional<Auction> loadById(UUID auctionId);
}