package com.ziyadsamhaoui.messagingnotificationservice.push;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.PushDeliveryStatus;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PushDeliveryRelay {

    static final int MAX_ERROR_LENGTH = 1000;

    private final PendingPushDeliveryRepository pendingPushDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushSender webPushSender;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${badrlink.notification.push.relay-interval:5s}")
    public void dispatchPending() {
        NotificationProperties.Push push = properties.push();
        if (!push.enabled()) {
            return;
        }

        List<PendingPushDelivery> batch = transactionTemplate.execute(status ->
                pendingPushDeliveryRepository.lockPendingBatch(push.batchSize()));
        if (batch == null) {
            return;
        }

        for (PendingPushDelivery delivery : batch) {
            processOne(delivery, push);
        }
    }

    private void processOne(PendingPushDelivery delivery, NotificationProperties.Push push) {
        try {
            if (attempt(delivery)) {
                transactionTemplate.executeWithoutResult(status ->
                        pendingPushDeliveryRepository.markProcessed(delivery.getId()));
            } else {
                recordFailure(delivery, push, "push delivery attempt failed");
            }
        } catch (RuntimeException exception) {
            recordFailure(delivery, push, exception.getMessage());
        }
    }

    boolean attempt(PendingPushDelivery delivery) {
        Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
        if (notification == null) {
            return true;
        }

        NotificationPreference preference = preferenceRepository.findById(notification.getUserId()).orElse(null);
        if (preference != null && (!preference.isPushEnabled()
                || preference.mutedTypeSet().contains(notification.getType()))) {
            return true;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(notification.getUserId());
        if (subscriptions.isEmpty()) {
            return true;
        }

        String payload = buildPayload(notification);
        boolean delivered = false;

        for (PushSubscription subscription : subscriptions) {
            WebPushResult result = webPushSender.send(subscription, payload);

            if (result.gone()) {
                log.info("removing dead push subscription {} (HTTP 410)", subscription.getId());
                subscriptionRepository.delete(subscription);
                continue;
            }

            if (result.success()) {
                delivered = true;
                subscriptionRepository.stampLastUsed(subscription.getId(), Instant.now());
            }
        }

        return delivered;
    }

    private void recordFailure(PendingPushDelivery delivery, NotificationProperties.Push push, String message) {
        int attempts = delivery.getAttempts() + 1;
        PushDeliveryStatus status = attempts >= push.maxAttempts()
                ? PushDeliveryStatus.FAILED_DEAD_LETTER
                : PushDeliveryStatus.PENDING;
        Instant nextRetryAt = Instant.now().plusSeconds(1L << attempts);
        String error = truncate(message);
        transactionTemplate.executeWithoutResult(ignored ->
                pendingPushDeliveryRepository.recordFailure(
                        delivery.getId(), attempts, error, status.name(), nextRetryAt));
        log.warn("push delivery {} failed on attempt {} with status {}: {}",
                delivery.getId(), attempts, status, error);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String buildPayload(Notification notification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", notification.getId().toString());
        payload.put("type", notification.getType().name());
        payload.put("title", title(notification));
        payload.put("body", notification.getContent());
        payload.put("sourceType", notification.getSourceType().name());
        payload.put("sourceId", notification.getSourceId());
        payload.put("createdAt", notification.getCreatedAt().toString());
        return objectMapper.writeValueAsString(payload);
    }

    private String title(Notification notification) {
        return switch (notification.getType()) {
            case MESSAGE -> "New message";
            case REACTION -> "New reaction";
            case INVITATION -> "Invitation";
            case SYSTEM -> "BadrLink";
        };
    }
}
