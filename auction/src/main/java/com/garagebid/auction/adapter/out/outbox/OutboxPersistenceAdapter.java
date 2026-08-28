package com.garagebid.auction.adapter.out.outbox;

import com.garagebid.auction.application.event.IntegrationEvent;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class OutboxPersistenceAdapter implements SaveIntegrationEventPort {

    private static final String AGGREGATE_TYPE = "auction";

    private final OutboxJpaRepository repository;
    private final IntegrationEventSerializer serializer;
    private final Clock clock;

    public OutboxPersistenceAdapter(
            OutboxJpaRepository repository,
            IntegrationEventSerializer serializer,
            Clock clock
    ) {
        this.repository = repository;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public void save(IntegrationEvent event) {
        String payload = serializer.serialize(event);

        OutboxJpaEntity entity = new OutboxJpaEntity(
                event.eventId(),
                AGGREGATE_TYPE,
                event.aggregateId(),
                event.eventType(),
                event.eventVersion(),
                event.occurredAt(),
                payload,
                clock.instant()
        );

        repository.save(entity);
    }
}