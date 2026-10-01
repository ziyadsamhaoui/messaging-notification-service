package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PendingPushDeliveryRepository pendingPushDeliveryRepository;

    @Transactional
    public boolean notifyUser(UUID userId, NotificationType type, NotificationSource sourceType, String sourceId,
            String content) {

        if (userId == null) {
            return false;
        }

        NotificationPreference preference = preferenceRepository.findById(userId).orElse(null);
        if (preference != null && preference.mutedTypeSet().contains(type)) {
            return false;
        }

        UUID notificationId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = notificationRepository.insertIfAbsent(notificationId, userId, type.name(), content,
                sourceType.name(), sourceId, now);

        if (inserted == 0) {
            return false;
        }

        boolean pushEnabled = preference == null || preference.isPushEnabled();
        if (pushEnabled && subscriptionRepository.existsByUserId(userId)) {
            pendingPushDeliveryRepository.save(PendingPushDelivery.builder()
                    .id(UUID.randomUUID())
                    .notificationId(notificationId)
                    .attempts(0)
                    .build());
        }

        return true;
    }
}
