package com.ziyadsamhaoui.messagingnotificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private PushSubscriptionRepository subscriptionRepository;
    private PendingPushDeliveryRepository pendingPushDeliveryRepository;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        subscriptionRepository = mock(PushSubscriptionRepository.class);
        pendingPushDeliveryRepository = mock(PendingPushDeliveryRepository.class);
        service = new NotificationDeliveryService(notificationRepository, preferenceRepository,
                subscriptionRepository, pendingPushDeliveryRepository);
    }

    @Test
    void enqueuesPushWhenEnabledAndSubscribed() {
        when(notificationRepository.insertIfAbsent(any(), eq(USER), anyString(), anyString(), anyString(), anyString(),
                any(Instant.class))).thenReturn(1);
        when(subscriptionRepository.existsByUserId(USER)).thenReturn(true);

        boolean created = service.notifyUser(USER, NotificationType.MESSAGE, NotificationSource.MESSAGE, "msg-1",
                "zara: hi");

        assertThat(created).isTrue();
        verify(pendingPushDeliveryRepository).save(any());
    }

    @Test
    void duplicateInsertIsDroppedWithoutEnqueuingPush() {
        when(notificationRepository.insertIfAbsent(any(), eq(USER), anyString(), anyString(), anyString(), anyString(),
                any(Instant.class))).thenReturn(0);

        boolean created = service.notifyUser(USER, NotificationType.MESSAGE, NotificationSource.MESSAGE, "msg-1",
                "zara: hi");

        assertThat(created).isFalse();
        verify(pendingPushDeliveryRepository, never()).save(any());
    }

    @Test
    void mutedTypeIsSkippedBeforeInsert() {
        when(preferenceRepository.findById(USER)).thenReturn(Optional.of(NotificationPreference.builder()
                .userId(USER).mutedTypes("MESSAGE").pushEnabled(true).build()));

        boolean created = service.notifyUser(USER, NotificationType.MESSAGE, NotificationSource.MESSAGE, "msg-1",
                "zara: hi");

        assertThat(created).isFalse();
        verify(notificationRepository, never()).insertIfAbsent(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(Instant.class));
    }

    @Test
    void pushDisabledStillCreatesInAppRow() {
        when(preferenceRepository.findById(USER)).thenReturn(Optional.of(NotificationPreference.builder()
                .userId(USER).mutedTypes("").pushEnabled(false).build()));
        when(notificationRepository.insertIfAbsent(any(), eq(USER), anyString(), anyString(), anyString(), anyString(),
                any(Instant.class))).thenReturn(1);

        boolean created = service.notifyUser(USER, NotificationType.MESSAGE, NotificationSource.MESSAGE, "msg-1",
                "zara: hi");

        assertThat(created).isTrue();
        verify(pendingPushDeliveryRepository, never()).save(any());
    }
}
