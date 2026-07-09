package com.garagebid.auction.domain.model;

public class BidTooLowException extends AuctionException {
    public BidTooLowException(Money attempted, Money minimum) {
        super("Bid %s is not high enough; minimum is %s".formatted(attempted, minimum));
    }
}