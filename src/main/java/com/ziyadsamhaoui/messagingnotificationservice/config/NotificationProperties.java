package com.ziyadsamhaoui.messagingnotificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "badrlink.notification")
public record NotificationProperties(
        int defaultPageSize,
        int maxPageSize,
        Duration senderCacheRetention,
        Duration cleanupInterval,
        Push push) {

    public record Push(
            boolean enabled,
            Duration relayInterval,
            int batchSize,
            int maxAttempts,
            long ttlSeconds,
            String vapidPublicKey,
            String vapidPrivateKey,
            String vapidSubject) {
    }
}
