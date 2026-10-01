package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSenderRetentionJob {

    private final MessageSenderCacheService messageSenderCacheService;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${badrlink.notification.cleanup-interval:PT24H}")
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.senderCacheRetention());
        int deleted = messageSenderCacheService.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("message_sender_cache retention cleanup removed {} row(s) older than {}", deleted, cutoff);
        }
    }
}
