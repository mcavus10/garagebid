package com.garagebid.auction.application.port.in;

import com.garagebid.auction.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public interface OpenAuctionUseCase {

    UUID openAuction(OpenAuctionCommand command);

    record OpenAuctionCommand(
            UUID carId,
            UUID sellerId,
            Money startingPrice,
            Instant endsAt
    ) {
        public OpenAuctionCommand {
            if (carId == null) throw new IllegalArgumentException("carId required");
            if (sellerId == null) throw new IllegalArgumentException("sellerId required");
            if (startingPrice == null) throw new IllegalArgumentException("startingPrice required");
            if (endsAt == null) throw new IllegalArgumentException("endsAt required");
        }
    }
}