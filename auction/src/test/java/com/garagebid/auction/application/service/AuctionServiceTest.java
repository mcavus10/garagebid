package com.garagebid.auction.application.service;

import com.garagebid.auction.application.event.*;
import com.garagebid.auction.application.port.in.OpenAuctionUseCase.OpenAuctionCommand;
import com.garagebid.auction.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.garagebid.auction.application.port.out.CarLookupPort;
import com.garagebid.auction.application.port.out.CreateAuctionPort;
import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import com.garagebid.auction.domain.model.Auction;
import com.garagebid.auction.domain.model.AuctionStatus;
import com.garagebid.auction.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-20T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID CAR_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID SELLER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID BIDDER_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID EXISTING_AUCTION_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void shouldOpenAuctionWhenCarExists() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                ENDS_AT
        );

        UUID auctionId = service.openAuction(command);

        assertNotNull(auctionId);

        Auction createdAuction = persistencePort.lastCreated;

        assertNotNull(createdAuction);
        assertEquals(auctionId, createdAuction.id());
        assertEquals(CAR_ID, createdAuction.carId());
        assertEquals(SELLER_ID, createdAuction.sellerId());
        assertEquals(
                Money.of("395000.00", "USD"),
                createdAuction.startingPrice()
        );
        assertEquals(AuctionStatus.OPEN, createdAuction.status());
        assertNull(createdAuction.highestBid());

        assertEquals(1, persistencePort.createCount);
        assertEquals(0, persistencePort.saveCount);

        assertEquals(1, eventPort.events.size());

        IntegrationEvent integrationEvent = eventPort.events.getFirst();

        assertInstanceOf(
                AuctionOpenedIntegrationEvent.class,
                integrationEvent
        );

        AuctionOpenedIntegrationEvent event =
                (AuctionOpenedIntegrationEvent) integrationEvent;

        assertNotNull(event.eventId());
        assertEquals(auctionId, event.aggregateId());
        assertEquals(NOW, event.occurredAt());
        assertEquals(CAR_ID, event.carId());
        assertEquals(SELLER_ID, event.sellerId());

        assertEquals(
                Money.of("395000.00", "USD").amount(),
                event.startingAmount()
        );

        assertEquals("USD", event.currency());
        assertEquals(ENDS_AT, event.endsAt());

        assertEquals(
                AuctionOpenedIntegrationEvent.TYPE,
                event.eventType()
        );

        assertEquals(
                AuctionOpenedIntegrationEvent.VERSION,
                event.eventVersion()
        );
    }

    @Test
    void shouldRejectOpeningAuctionWhenCarDoesNotExist() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(false);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                ENDS_AT
        );

        CarNotFoundException exception = assertThrows(
                CarNotFoundException.class,
                () -> service.openAuction(command)
        );

        assertEquals(
                "Car not found: " + CAR_ID,
                exception.getMessage()
        );

        assertEquals(0, persistencePort.createCount);
        assertEquals(0, persistencePort.saveCount);
        assertNull(persistencePort.lastCreated);
        assertNull(persistencePort.lastSaved);

        assertTrue(eventPort.events.isEmpty());
    }

    @Test
    void shouldPropagateCatalogUnavailableWhenCarCannotBeVerified() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();

        CarLookupPort carLookupPort = carId -> {
            throw new CatalogUnavailableException(
                    new RuntimeException("catalog connection failed")
            );
        };

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
        );

        OpenAuctionCommand command = new OpenAuctionCommand(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                ENDS_AT
        );

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class,
                () -> service.openAuction(command)
        );

        assertEquals(
                "Catalog service is currently unavailable",
                exception.getMessage()
        );

        assertEquals(0, persistencePort.createCount);
        assertEquals(0, persistencePort.saveCount);
        assertNull(persistencePort.lastCreated);
        assertNull(persistencePort.lastSaved);

        assertTrue(eventPort.events.isEmpty());
    }

    @Test
    void shouldPlaceBidOnExistingAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        Auction auction = existingOpenAuction();
        loadPort.add(auction);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
        );

        PlaceBidCommand command = new PlaceBidCommand(
                auction.id(),
                BIDDER_ID,
                Money.of("400000.00", "USD")
        );

        service.placeBid(command);

        Auction savedAuction = persistencePort.lastSaved;

        assertNotNull(savedAuction);
        assertNotNull(savedAuction.highestBid());
        assertEquals(BIDDER_ID, savedAuction.highestBid().bidderId());
        assertEquals(
                Money.of("400000.00", "USD"),
                savedAuction.highestBid().amount()
        );
        assertEquals(NOW, savedAuction.highestBid().placedAt());

        assertEquals(0, persistencePort.createCount);
        assertEquals(1, persistencePort.saveCount);

        assertEquals(1, eventPort.events.size());

        IntegrationEvent integrationEvent = eventPort.events.getFirst();

        assertInstanceOf(
                AuctionBidPlacedIntegrationEvent.class,
                integrationEvent
        );

        AuctionBidPlacedIntegrationEvent event =
                (AuctionBidPlacedIntegrationEvent) integrationEvent;

        assertNotNull(event.eventId());
        assertEquals(auction.id(), event.aggregateId());
        assertEquals(NOW, event.occurredAt());
        assertEquals(BIDDER_ID, event.bidderId());
        assertEquals(Money.of("400000.00", "USD").amount(), event.amount());
        assertEquals("USD", event.currency());

        assertEquals(
                AuctionBidPlacedIntegrationEvent.TYPE,
                event.eventType()
        );

        assertEquals(
                AuctionBidPlacedIntegrationEvent.VERSION,
                event.eventVersion()
        );
    }

    @Test
    void shouldThrowWhenPlacingBidOnUnknownAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
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

        assertEquals(0, persistencePort.createCount);
        assertEquals(0, persistencePort.saveCount);

        assertTrue(eventPort.events.isEmpty());
    }

    @Test
    void shouldCloseExistingAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        Auction auction = existingOpenAuction();
        loadPort.add(auction);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
        );

        service.closeAuction(auction.id());

        Auction savedAuction = persistencePort.lastSaved;

        assertNotNull(savedAuction);
        assertEquals(AuctionStatus.CLOSED, savedAuction.status());

        assertEquals(0, persistencePort.createCount);
        assertEquals(1, persistencePort.saveCount);

        assertEquals(1, eventPort.events.size());

        IntegrationEvent integrationEvent = eventPort.events.getFirst();

        assertInstanceOf(
                AuctionClosedIntegrationEvent.class,
                integrationEvent
        );

        AuctionClosedIntegrationEvent event =
                (AuctionClosedIntegrationEvent) integrationEvent;

        assertNotNull(event.eventId());
        assertEquals(auction.id(), event.aggregateId());
        assertEquals(NOW, event.occurredAt());

        assertNull(event.winnerId());
        assertNull(event.winningAmount());
        assertNull(event.currency());

        assertEquals(
                AuctionClosedIntegrationEvent.TYPE,
                event.eventType()
        );

        assertEquals(
                AuctionClosedIntegrationEvent.VERSION,
                event.eventVersion()
        );
    }

    @Test
    void shouldThrowWhenClosingUnknownAuction() {
        FakeLoadAuctionPort loadPort = new FakeLoadAuctionPort();
        FakeAuctionPersistencePort persistencePort = new FakeAuctionPersistencePort();
        FakeSaveIntegrationEventPort eventPort = new FakeSaveIntegrationEventPort();
        FakeCarLookupPort carLookupPort = new FakeCarLookupPort(true);

        AuctionService service = createService(
                loadPort,
                persistencePort,
                eventPort,
                carLookupPort
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

        assertEquals(0, persistencePort.createCount);
        assertEquals(0, persistencePort.saveCount);

        assertTrue(eventPort.events.isEmpty());
    }

    private static Auction existingOpenAuction() {
        return Auction.rehydrate(
                EXISTING_AUCTION_ID,
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                ENDS_AT,
                AuctionStatus.OPEN,
                null,
                0L
        );
    }

    private static AuctionService createService(
            FakeLoadAuctionPort loadPort,
            FakeAuctionPersistencePort persistencePort,
            FakeSaveIntegrationEventPort eventPort,
            CarLookupPort carLookupPort
    ) {
        AuctionEventMapper eventMapper = new AuctionEventMapper();

        AuctionTransactionalWriter transactionalWriter =
                new AuctionTransactionalWriter(
                        persistencePort,
                        persistencePort,
                        eventPort,
                        eventMapper
                );

        return new AuctionService(
                loadPort,
                carLookupPort,
                transactionalWriter,
                CLOCK
        );
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

    private static class FakeAuctionPersistencePort
            implements CreateAuctionPort, SaveAuctionPort {

        private Auction lastCreated;
        private Auction lastSaved;
        private int createCount;
        private int saveCount;

        @Override
        public void create(Auction auction) {
            this.lastCreated = auction;
            this.createCount++;
        }

        @Override
        public Auction save(Auction auction) {
            this.lastSaved = auction;
            this.saveCount++;
            return auction;
        }
    }

    private static class FakeSaveIntegrationEventPort
            implements SaveIntegrationEventPort {

        private final List<IntegrationEvent> events = new ArrayList<>();

        @Override
        public void save(IntegrationEvent event) {
            events.add(event);
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