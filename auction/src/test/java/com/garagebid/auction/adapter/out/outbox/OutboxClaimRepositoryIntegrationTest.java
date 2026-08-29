package com.garagebid.auction.adapter.out.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class OutboxClaimRepositoryIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-08-29T10:00:00Z");

    private static final Duration LEASE =
            Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("auction")
                    .withUsername("garagebid")
                    .withPassword("garagebid");

    @Autowired
    private OutboxClaimRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void concurrentWorkersShouldClaimDifferentEvents() throws Exception {

        /*
         * Prepare ten independent pieces of work.
         */
        for (int i = 0; i < 10; i++) {
            insertOutboxEvent(
                    UUID.randomUUID(),
                    NOW.plusMillis(i)
            );
        }

        /*
         * We want both threads to start the database operation
         * at approximately the same time.
         */
        CountDownLatch startSignal =
                new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            Future<List<ClaimedOutboxEvent>> workerA =
                    executor.submit(() -> {
                        startSignal.await();

                        return repository.claimBatch(
                                5,
                                "worker-a",
                                NOW,
                                LEASE
                        );
                    });

            Future<List<ClaimedOutboxEvent>> workerB =
                    executor.submit(() -> {
                        startSignal.await();

                        return repository.claimBatch(
                                5,
                                "worker-b",
                                NOW,
                                LEASE
                        );
                    });

            /*
             * Release both workers.
             */
            startSignal.countDown();

            List<ClaimedOutboxEvent> claimedByA =
                    workerA.get();

            List<ClaimedOutboxEvent> claimedByB =
                    workerB.get();

            assertEquals(5, claimedByA.size());
            assertEquals(5, claimedByB.size());

            Set<UUID> idsA = claimedByA.stream()
                    .map(ClaimedOutboxEvent::id)
                    .collect(HashSet::new, Set::add, Set::addAll);

            Set<UUID> idsB = claimedByB.stream()
                    .map(ClaimedOutboxEvent::id)
                    .collect(HashSet::new, Set::add, Set::addAll);

            /*
             * Core concurrency invariant:
             *
             * no event may be owned by both workers.
             */
            Set<UUID> intersection =
                    new HashSet<>(idsA);

            intersection.retainAll(idsB);

            assertTrue(
                    intersection.isEmpty(),
                    "Workers claimed overlapping outbox events: "
                            + intersection
            );

            /*
             * Together they should have claimed all ten rows.
             */
            Set<UUID> allClaimed =
                    new HashSet<>(idsA);

            allClaimed.addAll(idsB);

            assertEquals(10, allClaimed.size());
        }
    }

    @Test
    void expiredLeaseShouldAllowAnotherWorkerToReclaimEvent() {

        UUID eventId = UUID.randomUUID();

        insertOutboxEvent(
                eventId,
                NOW
        );

        List<ClaimedOutboxEvent> firstClaim =
                repository.claimBatch(
                        1,
                        "worker-a",
                        NOW,
                        Duration.ofSeconds(10)
                );

        assertEquals(1, firstClaim.size());
        assertEquals(eventId, firstClaim.getFirst().id());

        /*
         * Before lease expiration, another worker must NOT get it.
         */
        List<ClaimedOutboxEvent> tooEarly =
                repository.claimBatch(
                        1,
                        "worker-b",
                        NOW.plusSeconds(5),
                        Duration.ofSeconds(10)
                );

        assertTrue(tooEarly.isEmpty());

        /*
         * Original lease expired at NOW + 10 seconds.
         *
         * A crashed worker should not permanently poison the row.
         */
        List<ClaimedOutboxEvent> reclaimed =
                repository.claimBatch(
                        1,
                        "worker-b",
                        NOW.plusSeconds(11),
                        Duration.ofSeconds(10)
                );

        assertEquals(1, reclaimed.size());
        assertEquals(eventId, reclaimed.getFirst().id());

        /*
         * A new processing attempt must get a new ownership token.
         */
        assertNotEquals(
                firstClaim.getFirst().claimToken(),
                reclaimed.getFirst().claimToken()
        );

        assertEquals(
                2,
                reclaimed.getFirst().attemptCount()
        );
    }

    @Test
    void staleClaimTokenShouldNotBeAllowedToMarkEventAsPublished() {

        UUID eventId = UUID.randomUUID();

        insertOutboxEvent(
                eventId,
                NOW
        );

        /*
         * Worker A gets the first lease.
         */
        ClaimedOutboxEvent claimA =
                repository.claimBatch(
                        1,
                        "worker-a",
                        NOW,
                        Duration.ofSeconds(10)
                ).getFirst();

        /*
         * Imagine worker A stalls or crashes.
         *
         * Its lease expires, then worker B reclaims the same event.
         */
        ClaimedOutboxEvent claimB =
                repository.claimBatch(
                        1,
                        "worker-b",
                        NOW.plusSeconds(11),
                        Duration.ofSeconds(10)
                ).getFirst();

        assertNotEquals(
                claimA.claimToken(),
                claimB.claimToken()
        );

        /*
         * Worker A wakes up late.
         *
         * It no longer owns the row, so its old token
         * must not be accepted.
         */
        boolean staleWorkerSucceeded =
                repository.markPublished(
                        eventId,
                        claimA.claimToken(),
                        NOW.plusSeconds(12)
                );

        assertFalse(staleWorkerSucceeded);

        /*
         * Current owner can acknowledge publication.
         */
        boolean currentWorkerSucceeded =
                repository.markPublished(
                        eventId,
                        claimB.claimToken(),
                        NOW.plusSeconds(13)
                );

        assertTrue(currentWorkerSucceeded);

        Timestamp publishedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT published_at
                        FROM outbox_events
                        WHERE id = ?
                        """,
                        Timestamp.class,
                        eventId
                );

        assertNotNull(publishedAt);
        assertEquals(
                NOW.plusSeconds(13),
                publishedAt.toInstant()
        );
    }

    private void insertOutboxEvent(
            UUID eventId,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    event_version,
                    occurred_at,
                    payload,
                    created_at,
                    published_at,
                    claimed_at,
                    claim_expires_at,
                    claimed_by,
                    claim_token,
                    attempt_count
                )
                VALUES (
                    ?,
                    'auction',
                    ?,
                    'auction.opened',
                    1,
                    ?,
                    CAST(? AS jsonb),
                    ?,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    0
                )
                """,
                eventId,
                UUID.randomUUID(),
                Timestamp.from(createdAt),
                """
                {
                  "eventId": "%s",
                  "aggregateId": "%s"
                }
                """.formatted(
                        eventId,
                        UUID.randomUUID()
                ),
                Timestamp.from(createdAt)
        );
    }
}