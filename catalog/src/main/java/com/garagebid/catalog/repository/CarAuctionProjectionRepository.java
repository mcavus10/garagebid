package com.garagebid.catalog.repository;

import com.garagebid.catalog.messaging.AuctionOpenedMessageV1;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class CarAuctionProjectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public CarAuctionProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(AuctionOpenedMessageV1 event) {
        jdbcTemplate.update("""
                INSERT INTO car_auction_projection (
                    car_id,
                    auction_id,
                    auction_ends_at,
                    event_occurred_at
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (car_id) DO UPDATE
                SET auction_id = EXCLUDED.auction_id,
                    auction_ends_at = EXCLUDED.auction_ends_at,
                    event_occurred_at = EXCLUDED.event_occurred_at,
                    updated_at = CURRENT_TIMESTAMP
                WHERE EXCLUDED.event_occurred_at >= car_auction_projection.event_occurred_at
                """,
                event.carId(),
                event.aggregateId(),
                Timestamp.from(event.endsAt()),
                Timestamp.from(event.occurredAt())
        );
    }
}