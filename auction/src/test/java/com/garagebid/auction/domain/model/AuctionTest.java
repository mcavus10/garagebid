package com.garagebid.auction.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuctionTest {

    private final UUID car = UUID.randomUUID();
    private final UUID seller = UUID.randomUUID();
    private final UUID bidder = UUID.randomUUID();
    private final Instant now = Instant.now();
    private final Instant endsAt = now.plus(1, ChronoUnit.HOURS);

    private Auction openAuction() {
        return Auction.open(car, seller, Money.of("100000", "USD"), endsAt);
    }

    @Test
    void firstBidMustMeetStartingPrice() {
        assertThatThrownBy(() -> openAuction().placeBid(bidder, Money.of("90000", "USD"), now))
                .isInstanceOf(BidTooLowException.class);
    }

    @Test
    void acceptsFirstBidAtStartingPrice() {
        Auction a = openAuction();
        a.placeBid(bidder, Money.of("100000", "USD"), now);
        assertThat(a.highestBid().amount()).isEqualTo(Money.of("100000", "USD"));
    }

    @Test
    void nextBidMustBeStrictlyHigher() {
        Auction a = openAuction();
        a.placeBid(bidder, Money.of("100000", "USD"), now);
        assertThatThrownBy(() -> a.placeBid(bidder, Money.of("100000", "USD"), now))
                .isInstanceOf(BidTooLowException.class);
    }

    @Test
    void cannotBidOnClosedAuction() {
        Auction a = openAuction();
        a.close();
        assertThatThrownBy(() -> a.placeBid(bidder, Money.of("200000", "USD"), now))
                .isInstanceOf(AuctionNotOpenException.class);
    }

    @Test
    void rejectsBidInDifferentCurrency() {
        assertThatThrownBy(() -> openAuction().placeBid(bidder, Money.of("100000", "EUR"), now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}