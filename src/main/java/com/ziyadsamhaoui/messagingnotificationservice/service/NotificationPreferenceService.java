package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.dto.PreferencesResponse;
import com.ziyadsamhaoui.messagingnotificationservice.dto.UpdatePreferencesRequest;
import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public PreferencesResponse get(UUID userId) {
        return toView(preferenceRepository.findById(userId).orElseGet(() -> NotificationPreference.defaults(userId)));
    }

    @Transactional
    public PreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        NotificationPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> NotificationPreference.defaults(userId));

        if (request.mutedTypes() != null) {
            preference.setMutedTypeSet(request.mutedTypes());
        }
        if (request.pushEnabled() != null) {
            preference.setPushEnabled(request.pushEnabled());
        }

        return toView(preferenceRepository.save(preference));
    }

    private PreferencesResponse toView(NotificationPreference preference) {
        return new PreferencesResponse(preference.mutedTypeSet(), preference.isPushEnabled());
    }
}
