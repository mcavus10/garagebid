package com.garagebid.catalog.messaging;

public class NonRetryableKafkaMessageException extends RuntimeException {

    public NonRetryableKafkaMessageException(String message) {
        super(message);
    }

    public NonRetryableKafkaMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}