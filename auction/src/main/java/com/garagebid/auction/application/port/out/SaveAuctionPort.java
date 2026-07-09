package com.garagebid.auction.application.port.out;

import com.garagebid.auction.domain.model.Auction;

public interface SaveAuctionPort {
    Auction save(Auction auction);
}