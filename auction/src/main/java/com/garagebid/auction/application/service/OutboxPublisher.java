package com.garagebid.auction.application.service;

import com.garagebid.auction.adapter.out.kafka.KafkaOutboxSender;
import com.garagebid.auction.adapter.out.outbox.ClaimedOutboxEvent;
import com.garagebid.auction.adapter.out.outbox.OutboxClaimRepository;
import com.garagebid.auction.application.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimRepository outboxRepository;
    private final KafkaOutboxSender kafkaSender;
    private final OutboxProperties properties;
    private final Clock clock;


    private final String workerId = "auction-outbox-" + UUID.randomUUID();

    public OutboxPublisher(
            OutboxClaimRepository outboxRepository,
            KafkaOutboxSender kafkaSender,
            OutboxProperties properties,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaSender = kafkaSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${garagebid.outbox.poll-delay-ms:1000}")
    public void publishPendingEvents() {

        Instant now = clock.instant();

        List<ClaimedOutboxEvent> events =
                outboxRepository.claimBatch(
                        properties.batchSize(),
                        workerId,
                        now,
                        properties.leaseDuration()
                );

        for (ClaimedOutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(ClaimedOutboxEvent event) {
        try {
            kafkaSender.send(event);
            boolean marked = outboxRepository.markPublished(event.id(), event.claimToken(), clock.instant());

            if (!marked) {
                log.warn("Outbox event {} was published to Kafka but its claim is no longer valid", event.id());
            }

        } catch (RuntimeException e) {
            log.warn("Failed to publish outbox event {}. It will become eligible after lease expiration.", event.id(), e);
        }
    }
}