package com.garagebid.auction.application.port.in;

import com.garagebid.auction.domain.model.Money;
import java.util.UUID;

public interface PlaceBidUseCase {
    void placeBid(PlaceBidCommand command);

    record PlaceBidCommand(UUID auctionId, UUID bidderId, Money amount) {
        public PlaceBidCommand {
            if (auctionId == null) throw new IllegalArgumentException("auctionId required");
            if (bidderId == null) throw new IllegalArgumentException("bidderId required");
            if (amount == null) throw new IllegalArgumentException("amount required");
        }
    }
}