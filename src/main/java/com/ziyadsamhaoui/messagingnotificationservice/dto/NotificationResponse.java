package com.ziyadsamhaoui.messagingnotificationservice.dto;

import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationSource sourceType,
        String sourceId,
        String content,
        Instant createdAt,
        boolean read) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getSourceType(),
                notification.getSourceId(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.isRead());
    }
}
