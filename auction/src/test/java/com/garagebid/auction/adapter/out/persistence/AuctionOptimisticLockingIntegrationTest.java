package com.garagebid.auction.adapter.out.persistence;

import com.garagebid.auction.domain.model.Auction;
import com.garagebid.auction.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.consul.enabled=false",
                "spring.cloud.consul.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        }
)
class AuctionOptimisticLockingIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-09-01T18:00:00Z");

    private static final UUID CAR_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID SELLER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID BIDDER_A =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID BIDDER_B =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("auction")
                    .withUsername("garagebid")
                    .withPassword("garagebid");

    @Autowired
    private AuctionPersistenceAdapter persistenceAdapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldRejectStaleConcurrentAuctionUpdate() throws Exception {
        Auction auction = Auction.open(
                CAR_ID,
                SELLER_ID,
                Money.of("395000.00", "USD"),
                ENDS_AT,
                NOW
        );

        inTransaction(() -> persistenceAdapter.create(auction));

        Auction persisted = inTransaction(
                () -> persistenceAdapter.loadById(auction.id()).orElseThrow()
        );

        assertEquals(0L, persisted.version());

        /*
         * Both transactions must first read version 0.
         *
         * TX-A then commits first.
         * TX-B keeps its stale version 0 snapshot and attempts to save
         * after TX-A has committed version 1.
         */
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch firstCommitted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> firstUpdate = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                transaction.executeWithoutResult(status -> {
                    Auction loaded = persistenceAdapter
                            .loadById(auction.id())
                            .orElseThrow();

                    assertEquals(0L, loaded.version());

                    loaded.placeBid(
                            BIDDER_A,
                            Money.of("400000.00", "USD"),
                            NOW
                    );

                    bothLoaded.countDown();
                    await(bothLoaded, "both transactions to load the auction");

                    persistenceAdapter.save(loaded);
                });

                // TransactionTemplate returned, so TX-A has committed.
                firstCommitted.countDown();

                return null;
            });

            Future<Void> staleUpdate = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                transaction.executeWithoutResult(status -> {
                    Auction loaded = persistenceAdapter
                            .loadById(auction.id())
                            .orElseThrow();

                    assertEquals(0L, loaded.version());

                    loaded.placeBid(
                            BIDDER_B,
                            Money.of("410000.00", "USD"),
                            NOW
                    );

                    bothLoaded.countDown();
                    await(bothLoaded, "both transactions to load the auction");

                    // Keep this transaction stale until TX-A commits.
                    await(firstCommitted, "first transaction to commit");

                    persistenceAdapter.save(loaded);
                });

                return null;
            });

            // TX-A must succeed.
            firstUpdate.get(10, TimeUnit.SECONDS);

            // TX-B must fail because it still carries version 0.
            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> staleUpdate.get(10, TimeUnit.SECONDS)
            );

            assertInstanceOf(
                    ObjectOptimisticLockingFailureException.class,
                    exception.getCause()
            );
        } finally {
            executor.shutdownNow();
        }

        Auction finalAuction = inTransaction(
                () -> persistenceAdapter.loadById(auction.id()).orElseThrow()
        );

        assertEquals(1L, finalAuction.version());
        assertNotNull(finalAuction.highestBid());
        assertEquals(BIDDER_A, finalAuction.highestBid().bidderId());
        assertEquals(
                Money.of("400000.00", "USD"),
                finalAuction.highestBid().amount()
        );
    }

    private void inTransaction(Runnable action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> action.run());
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> action.get());
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);

            if (!completed) {
                throw new IllegalStateException(
                        "Timed out waiting for " + description
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for " + description,
                    e
            );
        }
    }
}