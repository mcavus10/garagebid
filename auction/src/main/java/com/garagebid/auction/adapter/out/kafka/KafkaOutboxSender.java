package com.garagebid.auction.adapter.out.kafka;

import com.garagebid.auction.adapter.out.outbox.ClaimedOutboxEvent;
import com.garagebid.auction.application.config.OutboxProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaOutboxSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public KafkaOutboxSender(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void send(ClaimedOutboxEvent event) {

        String key = event.aggregateId().toString();

        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        properties.topic(),
                        key,
                        event.payload()
                );


        record.headers().add("eventId", bytes(event.id().toString()));
        record.headers().add("eventType", bytes(event.eventType()));
        record.headers().add("eventVersion", bytes(Integer.toString(event.eventVersion())));

        try {
            kafkaTemplate
                    .send(record)
                    .get(
                            properties.sendTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException("Interrupted while publishing outbox event " + event.id(), e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish outbox event " + event.id(), e);
        }
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}