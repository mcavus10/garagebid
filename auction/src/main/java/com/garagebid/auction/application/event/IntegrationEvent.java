package com.garagebid.auction.application.event;

import java.time.Instant;
import java.util.UUID;

public interface IntegrationEvent {

    UUID eventId();

    UUID aggregateId();

    Instant occurredAt();

    String eventType();

    int eventVersion();
}