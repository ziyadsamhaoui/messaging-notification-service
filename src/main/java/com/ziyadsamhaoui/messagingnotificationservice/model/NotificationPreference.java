package com.ziyadsamhaoui.messagingnotificationservice.model;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "muted_types", nullable = false, length = 255)
    private String mutedTypes;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    public static NotificationPreference defaults(UUID userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .mutedTypes("")
                .pushEnabled(true)
                .build();
    }

    public Set<NotificationType> mutedTypeSet() {
        if (mutedTypes == null || mutedTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(mutedTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(NotificationType::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setMutedTypeSet(Set<NotificationType> types) {
        this.mutedTypes = types == null ? ""
                : types.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
