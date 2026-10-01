package com.ziyadsamhaoui.messagingnotificationservice.push;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PushDeliveryRelay {

    private final PendingPushDeliveryRepository pendingPushDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushSender webPushSender;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${badrlink.notification.push.relay-interval:5s}")
    public void dispatchPending() {
        NotificationProperties.Push push = properties.push();
        if (!push.enabled()) {
            return;
        }

        List<PendingPushDelivery> batch = pendingPushDeliveryRepository
                .findByDeliveredAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, push.batchSize()));

        for (PendingPushDelivery delivery : batch) {
            try {
                dispatch(delivery, push);
            } catch (RuntimeException exception) {
                log.error("push delivery {} failed", delivery.getId(), exception);
            }
        }
    }

    void dispatch(PendingPushDelivery delivery, NotificationProperties.Push push) {
        Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
        if (notification == null) {
            markDelivered(delivery);
            return;
        }

        NotificationPreference preference = preferenceRepository.findById(notification.getUserId()).orElse(null);
        if (preference != null && (!preference.isPushEnabled()
                || preference.mutedTypeSet().contains(notification.getType()))) {
            markDelivered(delivery);
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(notification.getUserId());
        if (subscriptions.isEmpty()) {
            markDelivered(delivery);
            return;
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

        delivery.setAttempts(delivery.getAttempts() + 1);
        if (delivered || delivery.getAttempts() >= push.maxAttempts()) {
            markDelivered(delivery);
        } else {
            pendingPushDeliveryRepository.save(delivery);
        }
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

    private void markDelivered(PendingPushDelivery delivery) {
        delivery.setDeliveredAt(Instant.now());
        pendingPushDeliveryRepository.save(delivery);
    }
}
