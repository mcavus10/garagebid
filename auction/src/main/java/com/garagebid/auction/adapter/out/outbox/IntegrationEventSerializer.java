package com.garagebid.auction.adapter.out.outbox;

import com.garagebid.auction.application.event.IntegrationEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class IntegrationEventSerializer {

    private final JsonMapper jsonMapper;

    IntegrationEventSerializer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    String serialize(IntegrationEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (JacksonException e) {

            throw new IllegalStateException(
                    "Failed to serialize integration event: " + event.eventId(), e
            );
        }
    }
}