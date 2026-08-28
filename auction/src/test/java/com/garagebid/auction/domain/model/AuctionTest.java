package com.garagebid.auction.domain.model;

import com.garagebid.auction.domain.event.AuctionOpenedEvent;
import com.garagebid.auction.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AuctionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-29T00:00:00Z");

    private final UUID car = UUID.randomUUID();
    private final UUID seller = UUID.randomUUID();
    private final UUID bidder = UUID.randomUUID();

    private final Instant endsAt =
            NOW.plus(1, ChronoUnit.HOURS);

    private Auction openAuction() {
        return Auction.open(
                car,
                seller,
                Money.of("100000", "USD"),
                endsAt,
                NOW // event occurredAt
        );
    }

    @Test
    void firstBidMustMeetStartingPrice() {
        assertThatThrownBy(
                () -> openAuction().placeBid(
                        bidder,
                        Money.of("90000", "USD"),
                        NOW
                )
        ).isInstanceOf(BidTooLowException.class);
    }

    @Test
    void acceptsFirstBidAtStartingPrice() {
        Auction auction = openAuction();

        auction.placeBid(
                bidder,
                Money.of("100000", "USD"),
                NOW
        );

        assertThat(auction.highestBid().amount())
                .isEqualTo(Money.of("100000", "USD"));
    }

    @Test
    void nextBidMustBeStrictlyHigher() {
        Auction auction = openAuction();

        auction.placeBid(
                bidder,
                Money.of("100000", "USD"),
                NOW
        );

        assertThatThrownBy(
                () -> auction.placeBid(
                        bidder,
                        Money.of("100000", "USD"),
                        NOW
                )
        ).isInstanceOf(BidTooLowException.class);
    }

    @Test
    void cannotBidOnClosedAuction() {
        Auction auction = openAuction();

        auction.close();

        assertThatThrownBy(
                () -> auction.placeBid(
                        bidder,
                        Money.of("200000", "USD"),
                        NOW
                )
        ).isInstanceOf(AuctionNotOpenException.class);
    }

    @Test
    void rejectsBidInDifferentCurrency() {
        assertThatThrownBy(
                () -> openAuction().placeBid(
                        bidder,
                        Money.of("100000", "EUR"),
                        NOW
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRaiseAuctionOpenedEvent() {
        UUID carId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        Money startingPrice =
                Money.of("395000.00", "USD");

        Instant auctionEndsAt =
                Instant.parse("2027-08-20T18:00:00Z");

        Instant occurredAt =
                Instant.parse("2026-08-29T00:00:00Z");

        Auction auction = Auction.open(
                carId,
                sellerId,
                startingPrice,
                auctionEndsAt,
                occurredAt
        );

        List<DomainEvent> events =
                auction.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst())
                .isInstanceOf(AuctionOpenedEvent.class);

        AuctionOpenedEvent event =
                (AuctionOpenedEvent) events.getFirst();

        assertThat(event.auctionId())
                .isEqualTo(auction.id());

        assertThat(event.carId())
                .isEqualTo(carId);

        assertThat(event.sellerId())
                .isEqualTo(sellerId);

        assertThat(event.startingPrice())
                .isEqualTo(startingPrice);

        assertThat(event.endsAt())
                .isEqualTo(auctionEndsAt);

        assertThat(event.occurredAt())
                .isEqualTo(occurredAt);

        // pull semantics:
        // the same event should not be returned twice.
        assertThat(auction.pullDomainEvents())
                .isEmpty();
    }
}