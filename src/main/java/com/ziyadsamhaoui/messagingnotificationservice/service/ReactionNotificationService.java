package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.DisplayNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionNotificationService {

    private final MessageSenderCacheService messageSenderCacheService;
    private final NotificationDeliveryService deliveryService;

    @Transactional
    public boolean handleReactionAdded(String messageId, UUID reactorId, String reactorUsername, String emoji) {
        Optional<UUID> senderId = messageSenderCacheService.resolveSender(messageId);

        if (senderId.isEmpty()) {
            return false;
        }

        UUID recipient = senderId.get();
        if (recipient.equals(reactorId)) {
            return false;
        }

        String body = DisplayNames.orFallback(reactorUsername) + " reacted " + emoji + " to your message";
        String sourceId = messageId + ":" + reactorId;
        return deliveryService.notifyUser(recipient, NotificationType.REACTION, NotificationSource.REACTION,
                sourceId, body);
    }
}
