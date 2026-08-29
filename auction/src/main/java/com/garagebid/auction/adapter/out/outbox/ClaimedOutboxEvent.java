package com.garagebid.auction.adapter.out.outbox;

import java.time.Instant;
import java.util.UUID;

public record ClaimedOutboxEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String payload,
        Instant createdAt,
        UUID claimToken,
        int attemptCount
) {
}