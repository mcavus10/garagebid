package com.garagebid.auction.application.port.out;

import com.garagebid.auction.domain.model.Auction;

public interface CreateAuctionPort {

    void create(Auction auction);
}