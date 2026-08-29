package com.garagebid.catalog.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryMarkProcessed(String consumerName, UUID eventId, String eventType) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO processed_events (consumer_name, event_id, event_type)
                VALUES (?, ?, ?)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """,
                consumerName,
                eventId,
                eventType
        );

        return inserted == 1;
    }
}