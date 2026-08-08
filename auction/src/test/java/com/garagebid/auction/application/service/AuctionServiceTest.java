package com.garagebid.auction.application.service;

import com.garagebid.auction.application.port.in.OpenAuctionUseCase.OpenAuctionCommand;
import com.garagebid.auction.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.garagebid.auction.application.port.out.CarLookupPort;
import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.domain.model.Auction;
import com.garagebid.auction.domain.model.AuctionStatus;
import com.garagebid.auction.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T12:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID CAR_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID SELLER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID BIDDER_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldOpenAuctionWhenCarExists() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                Instant.parse("2026-08-20T18:00:00Z")
        );

        UUID auctionId = service.openAuction(command);

        assertNotNull(auctionId);

        Auction savedAuction = savePort.lastSaved;

        assertNotNull(savedAuction);
        assertEquals(auctionId, savedAuction.id());
        assertEquals(CAR_ID, savedAuction.carId());
        assertEquals(SELLER_ID, savedAuction.sellerId());
        assertEquals(Money.of("395000.00", "USD"), savedAuction.startingPrice());
        assertEquals(AuctionStatus.OPEN, savedAuction.status());
        assertNull(savedAuction.highestBid());

        assertEquals(1, savePort.saveCount);
    }

    @Test
    void shouldRejectOpeningAuctionWhenCarDoesNotExist() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(false);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                Instant.parse("2026-08-20T18:00:00Z")
        );

        CarNotFoundException exception = assertThrows(
                CarNotFoundException.class,
                () -> service.openAuction(command)
        );

        assertEquals(
                "Car not found: " + CAR_ID,
                exception.getMessage()
        );

        assertEquals(0, savePort.saveCount);
        assertNull(savePort.lastSaved);
    }

    @Test
    void shouldPropagateCatalogUnavailableWhenCarCannotBeVerified() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();

        CarLookupPort carLookupPort = carId -> {
            throw new CatalogUnavailableException(
                    new RuntimeException("catalog connection failed")
            );
        };

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                Instant.parse("2026-08-20T18:00:00Z")
        );

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class,
                () -> service.openAuction(command)
        );

        assertEquals(
                "Catalog service is currently unavailable",
                exception.getMessage()
        );

        assertEquals(0, savePort.saveCount);
        assertNull(savePort.lastSaved);
    }

    @Test
    void shouldPlaceBidOnExistingAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        Auction auction = Auction.open(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                Instant.parse("2026-08-20T18:00:00Z")
        );

        loadPort.add(auction);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        PlaceBidCommand command = new PlaceBidCommand(
                auction.id(),
                BIDDER_ID,
                Money.of("400000.00", "USD")
        );

        service.placeBid(command);

        Auction savedAuction = savePort.lastSaved;

        assertNotNull(savedAuction);
        assertNotNull(savedAuction.highestBid());

        assertEquals(
                BIDDER_ID,
                savedAuction.highestBid().bidderId()
        );

        assertEquals(
                Money.of("400000.00", "USD"),
                savedAuction.highestBid().amount()
        );

        assertEquals(
                NOW,
                savedAuction.highestBid().placedAt()
        );

        assertEquals(1, savePort.saveCount);
    }

    @Test
    void shouldThrowWhenPlacingBidOnUnknownAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        UUID unknownAuctionId =
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        PlaceBidCommand command = new PlaceBidCommand(
                unknownAuctionId,
                BIDDER_ID,
                Money.of("400000.00", "USD")
        );

        AuctionNotFoundException exception = assertThrows(
                AuctionNotFoundException.class,
                () -> service.placeBid(command)
        );

        assertEquals(
                "Auction not found: " + unknownAuctionId,
                exception.getMessage()
        );

        assertEquals(0, savePort.saveCount);
    }

    @Test
    void shouldCloseExistingAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        Auction auction = Auction.open(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                Instant.parse("2026-08-20T18:00:00Z")
        );

        loadPort.add(auction);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        service.closeAuction(auction.id());

        Auction savedAuction = savePort.lastSaved;

        assertNotNull(savedAuction);
        assertEquals(AuctionStatus.CLOSED, savedAuction.status());
        assertEquals(1, savePort.saveCount);
    }

    @Test
    void shouldThrowWhenClosingUnknownAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeSaveAuctionPort savePort = new FakeSaveAuctionPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = new AuctionService(
                loadPort,
                savePort,
                carLookupPort,
                CLOCK
        );

        UUID unknownAuctionId =
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        AuctionNotFoundException exception = assertThrows(
                AuctionNotFoundException.class,
                () -> service.closeAuction(unknownAuctionId)
        );

        assertEquals(
                "Auction not found: " + unknownAuctionId,
                exception.getMessage()
        );

        assertEquals(0, savePort.saveCount);
    }

    private static class FakeLoadAuctionPort implements LoadAuctionPort {

        private final Map<UUID, Auction> auctions = new HashMap<>();

        void add(Auction auction) {
            auctions.put(auction.id(), auction);
        }

        @Override
        public Optional<Auction> loadById(UUID auctionId) {
            return Optional.ofNullable(auctions.get(auctionId));
        }
    }

    private static class FakeSaveAuctionPort implements SaveAuctionPort {

        private Auction lastSaved;
        private int saveCount;

        @Override
        public Auction save(Auction auction) {
            this.lastSaved = auction;
            this.saveCount++;
            return auction;
        }
    }

    private static class FakeCarLookupPort implements CarLookupPort {

        private final boolean exists;

        private FakeCarLookupPort(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean existsById(UUID carId) {
            return exists;
        }
    }
}