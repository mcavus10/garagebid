package com.garagebid.auction.adapter.out.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimBatch(
            int batchSize,
            String workerId,
            Instant now,
            Duration leaseDuration
    ) {
        UUID claimToken = UUID.randomUUID();

        Instant claimExpiresAt =
                now.plus(leaseDuration);

        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND (
                          claim_expires_at IS NULL
                          OR claim_expires_at < ?
                      )
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_events o
                SET claimed_at = ?,
                    claim_expires_at = ?,
                    claimed_by = ?,
                    claim_token = ?,
                    attempt_count = attempt_count + 1
                FROM candidates c
                WHERE o.id = c.id
                RETURNING
                    o.id,
                    o.aggregate_type,
                    o.aggregate_id,
                    o.event_type,
                    o.event_version,
                    o.occurred_at,
                    o.payload::text AS payload,
                    o.created_at,
                    o.claim_token,
                    o.attempt_count
                """,
                (rs, rowNum) -> new ClaimedOutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("claim_token", UUID.class),
                        rs.getInt("attempt_count")
                ),
                Timestamp.from(now),
                batchSize,
                Timestamp.from(now),
                Timestamp.from(claimExpiresAt),
                workerId,
                claimToken
        );
    }

    @Transactional
    public boolean markPublished(
            UUID eventId,
            UUID claimToken,
            Instant publishedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at = ?,
                    claimed_at = NULL,
                    claim_expires_at = NULL,
                    claimed_by = NULL,
                    claim_token = NULL
                WHERE id = ?
                  AND claim_token = ?
                  AND published_at IS NULL
                """,
                Timestamp.from(publishedAt),
                eventId,
                claimToken
        );

        return updated == 1;
    }
}