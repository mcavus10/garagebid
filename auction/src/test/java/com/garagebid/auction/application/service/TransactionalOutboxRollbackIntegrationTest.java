package com.garagebid.auction.application.service;

import com.garagebid.auction.application.event.IntegrationEvent;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import com.garagebid.auction.domain.model.Auction;
import com.garagebid.auction.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.consul.enabled=false",
                "spring.cloud.consul.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        }
)
class TransactionalOutboxRollbackIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-08-29T09:00:00Z");

    private static final Instant ENDS_AT =
            Instant.parse("2026-09-01T18:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("auction")
                    .withUsername("garagebid")
                    .withPassword("garagebid");

    @Autowired
    private AuctionTransactionalWriter transactionalWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @MockitoBean
    private SaveIntegrationEventPort saveIntegrationEventPort;

    @Test
    void shouldRollbackAuctionWhenIntegrationEventPersistenceFails() {
        UUID carId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        Auction auction = Auction.open(
                carId,
                sellerId,
                Money.of("395000.00", "USD"),
                ENDS_AT,
                NOW
        );

        doThrow(
                new IllegalStateException(
                        "simulated integration event persistence failure"
                )
        )
                .when(saveIntegrationEventPort)
                .save(any(IntegrationEvent.class));

        assertThrows(
                IllegalStateException.class,
                () -> transactionalWriter.create(auction)
        );

        Integer auctionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auctions
                WHERE id = ?
                """,
                Integer.class,
                auction.id()
        );

        assertEquals(0, auctionCount);
    }
}