package com.ziyadsamhaoui.messagingnotificationservice.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.NotificationPreference;
import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PushDeliveryRelayTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    private PendingPushDeliveryRepository pendingPushDeliveryRepository;
    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private PushSubscriptionRepository subscriptionRepository;
    private WebPushSender webPushSender;
    private PushDeliveryRelay relay;
    private NotificationProperties.Push push;

    @BeforeEach
    void setUp() {
        pendingPushDeliveryRepository = mock(PendingPushDeliveryRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        subscriptionRepository = mock(PushSubscriptionRepository.class);
        webPushSender = mock(WebPushSender.class);
        push = new NotificationProperties.Push(true, Duration.ofSeconds(5), 100, 5, 2419200L, "pub", "priv",
                "mailto:admin@badrlink.local");
        NotificationProperties properties = new NotificationProperties(30, 100, Duration.ofDays(90),
                Duration.ofHours(24), push);
        relay = new PushDeliveryRelay(pendingPushDeliveryRepository, notificationRepository, preferenceRepository,
                subscriptionRepository, webPushSender, properties, new ObjectMapper());
    }

    @Test
    void goneResponseDeletesTheDeadSubscription() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(WebPushResult.subscriptionGone());

        relay.dispatch(delivery(), push);

        verify(subscriptionRepository).delete(subscription);
        verify(subscriptionRepository, never()).stampLastUsed(any(), any());
    }

    @Test
    void successfulSendStampsSubscriptionAndMarksDelivered() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(new WebPushResult(201, false, null));

        PendingPushDelivery delivery = delivery();
        relay.dispatch(delivery, push);

        verify(subscriptionRepository).stampLastUsed(eq(subscription.getId()), any(Instant.class));
        ArgumentCaptor<PendingPushDelivery> captor = ArgumentCaptor.forClass(PendingPushDelivery.class);
        verify(pendingPushDeliveryRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveredAt()).isNotNull();
    }

    @Test
    void noSubscriptionsMarksDeliveredWithoutSending() {
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of());

        relay.dispatch(delivery(), push);

        verify(webPushSender, never()).send(any(), anyString());
        verify(pendingPushDeliveryRepository).save(any(PendingPushDelivery.class));
    }

    @Test
    void pushDisabledPreferenceSkipsSending() {
        givenNotification();
        when(preferenceRepository.findById(USER)).thenReturn(Optional.of(NotificationPreference.builder()
                .userId(USER).mutedTypes("").pushEnabled(false).build()));

        relay.dispatch(delivery(), push);

        verify(webPushSender, never()).send(any(), anyString());
        verify(pendingPushDeliveryRepository).save(any(PendingPushDelivery.class));
    }

    private void givenNotification() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(Notification.builder()
                .id(NOTIFICATION_ID).userId(USER).type(NotificationType.MESSAGE)
                .sourceType(NotificationSource.MESSAGE).sourceId("msg-1").content("zara: hi")
                .createdAt(Instant.now()).build()));
    }

    private PushSubscription subscription() {
        return PushSubscription.builder().id(UUID.randomUUID()).userId(USER).endpoint("https://push.example/x")
                .p256dhKey("p").authKey("a").build();
    }

    private PendingPushDelivery delivery() {
        return PendingPushDelivery.builder().id(UUID.randomUUID()).notificationId(NOTIFICATION_ID).attempts(0).build();
    }
}
