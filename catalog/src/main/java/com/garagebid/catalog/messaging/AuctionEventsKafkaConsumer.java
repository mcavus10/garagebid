package com.garagebid.catalog.messaging;

import com.garagebid.catalog.service.AuctionOpenedEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
public class AuctionEventsKafkaConsumer {

    private final JsonMapper jsonMapper;
    private final AuctionOpenedEventHandler auctionOpenedHandler;

    public AuctionEventsKafkaConsumer(
            JsonMapper jsonMapper,
            AuctionOpenedEventHandler auctionOpenedHandler
    ) {
        this.jsonMapper = jsonMapper;
        this.auctionOpenedHandler = auctionOpenedHandler;
    }

    @KafkaListener(topics = "${garagebid.kafka.auction-events-topic}")
    public void consume(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");

        if (!"auction.opened".equals(eventType)) {
            return;
        }

        int version = Integer.parseInt(header(record, "eventVersion"));

        if (version != 1) {
            throw new IllegalArgumentException("Unsupported auction.opened version: " + version);
        }

        try {
            AuctionOpenedMessageV1 event = jsonMapper.readValue(record.value(), AuctionOpenedMessageV1.class);
            auctionOpenedHandler.handle(event);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid auction.opened payload", e);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);

        if (header == null) {
            throw new IllegalArgumentException("Missing Kafka header: " + name);
        }

        return new String(header.value(), StandardCharsets.UTF_8);
    }
}