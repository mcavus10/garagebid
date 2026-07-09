package com.garagebid.auction.application.port.in;

import java.util.UUID;

public interface CloseAuctionUseCase {
    void closeAuction(UUID auctionId);
}