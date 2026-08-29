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

    private static final String AUCTION_OPENED = "auction.opened";
    private static final int AUCTION_OPENED_VERSION = 1;

    private final JsonMapper jsonMapper;
    private final AuctionOpenedEventHandler auctionOpenedEventHandler;

    public AuctionEventsKafkaConsumer(
            JsonMapper jsonMapper,
            AuctionOpenedEventHandler auctionOpenedEventHandler
    ) {
        this.jsonMapper = jsonMapper;
        this.auctionOpenedEventHandler = auctionOpenedEventHandler;
    }

    @KafkaListener(topics = "${garagebid.kafka.auction-events-topic}")
    public void consume(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");

        if (!AUCTION_OPENED.equals(eventType)) {
            return;
        }

        int version = parseVersion(header(record, "eventVersion"));

        if (version != AUCTION_OPENED_VERSION) {
            throw new NonRetryableKafkaMessageException("Unsupported auction.opened event version: " + version);
        }

        AuctionOpenedMessageV1 event = deserialize(record.value());
        auctionOpenedEventHandler.handle(event);
    }

    private AuctionOpenedMessageV1 deserialize(String payload) {
        try {
            return jsonMapper.readValue(payload, AuctionOpenedMessageV1.class);
        } catch (JacksonException e) {
            throw new NonRetryableKafkaMessageException("Invalid auction.opened payload", e);
        }
    }

    private int parseVersion(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new NonRetryableKafkaMessageException("Invalid eventVersion header: " + value, e);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);

        if (header == null) {
            throw new NonRetryableKafkaMessageException("Missing Kafka header: " + name);
        }

        return new String(header.value(), StandardCharsets.UTF_8);
    }
}