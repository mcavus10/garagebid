package com.garagebid.auction.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "garagebid.outbox")
public record OutboxProperties(
        String topic,
        int batchSize,
        long pollDelayMs,
        Duration leaseDuration,
        Duration sendTimeout
) {
}