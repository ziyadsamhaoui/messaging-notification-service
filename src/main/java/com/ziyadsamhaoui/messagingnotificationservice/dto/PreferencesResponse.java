package com.ziyadsamhaoui.messagingnotificationservice.dto;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;

import java.util.Set;

public record PreferencesResponse(Set<NotificationType> mutedTypes, boolean pushEnabled) {
}
