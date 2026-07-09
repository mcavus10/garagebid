package com.garagebid.auction.domain.model;

import java.util.UUID;

public class AuctionNotOpenException extends AuctionException {
    public AuctionNotOpenException(UUID auctionId) {
        super("Auction is not open for bidding: " + auctionId);
    }
}