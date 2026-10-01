package com.ziyadsamhaoui.messagingnotificationservice.dto;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;

import java.util.Set;

public record UpdatePreferencesRequest(Set<NotificationType> mutedTypes, Boolean pushEnabled) {
}
