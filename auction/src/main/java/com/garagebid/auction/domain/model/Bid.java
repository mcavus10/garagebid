package com.garagebid.auction.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Bid(UUID bidderId, Money amount, Instant placedAt) {
    public Bid {
        if (bidderId == null) throw new IllegalArgumentException("bidderId required");
        if (amount == null) throw new IllegalArgumentException("amount required");
        if (placedAt == null) throw new IllegalArgumentException("placedAt required");
    }
}