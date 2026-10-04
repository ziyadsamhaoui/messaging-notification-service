package com.ziyadsamhaoui.messagingnotificationservice.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
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
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        push = new NotificationProperties.Push(true, Duration.ofSeconds(5), 100, 5, 2419200L, "pub", "priv",
                "mailto:admin@badrlink.local");
        NotificationProperties properties = new NotificationProperties(30, 100, Duration.ofDays(90),
                Duration.ofHours(24), push);
        relay = new PushDeliveryRelay(pendingPushDeliveryRepository, notificationRepository, preferenceRepository,
                subscriptionRepository, webPushSender, properties, new ObjectMapper(),
                new TransactionTemplate(transactionManager));
    }

    @Test
    void goneResponseDeletesTheDeadSubscription() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(WebPushResult.subscriptionGone());

        assertThat(relay.attempt(delivery())).isFalse();

        verify(subscriptionRepository).delete(subscription);
        verify(subscriptionRepository, never()).stampLastUsed(any(), any());
    }

    @Test
    void successfulSendStampsSubscriptionAndMarksProcessed() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(new WebPushResult(201, false, null));
        PendingPushDelivery delivery = delivery();
        when(pendingPushDeliveryRepository.lockPendingBatch(push.batchSize())).thenReturn(List.of(delivery));

        relay.dispatchPending();

        verify(subscriptionRepository).stampLastUsed(eq(subscription.getId()), any(Instant.class));
        verify(pendingPushDeliveryRepository).markProcessed(delivery.getId());
        verify(pendingPushDeliveryRepository, never()).recordFailure(any(), anyInt(), any(), any(), any());
    }

    @Test
    void noSubscriptionsMarksProcessedWithoutSending() {
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of());
        PendingPushDelivery delivery = delivery();
        when(pendingPushDeliveryRepository.lockPendingBatch(push.batchSize())).thenReturn(List.of(delivery));

        relay.dispatchPending();

        verify(webPushSender, never()).send(any(), anyString());
        verify(pendingPushDeliveryRepository).markProcessed(delivery.getId());
    }

    @Test
    void pushDisabledPreferenceSkipsSending() {
        givenNotification();
        when(preferenceRepository.findById(USER)).thenReturn(Optional.of(NotificationPreference.builder()
                .userId(USER).mutedTypes("").pushEnabled(false).build()));
        PendingPushDelivery delivery = delivery();
        when(pendingPushDeliveryRepository.lockPendingBatch(push.batchSize())).thenReturn(List.of(delivery));

        relay.dispatchPending();

        verify(webPushSender, never()).send(any(), anyString());
        verify(pendingPushDeliveryRepository).markProcessed(delivery.getId());
    }

    @Test
    void failedSendRecordsRetryWithBackoff() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(new WebPushResult(500, false, "boom"));
        PendingPushDelivery delivery = delivery();
        when(pendingPushDeliveryRepository.lockPendingBatch(push.batchSize())).thenReturn(List.of(delivery));

        Instant lowerBound = Instant.now().plusSeconds(1);
        relay.dispatchPending();
        Instant upperBound = Instant.now().plusSeconds(3);

        ArgumentCaptor<Instant> nextRetryAt = ArgumentCaptor.forClass(Instant.class);
        verify(pendingPushDeliveryRepository).recordFailure(eq(delivery.getId()), eq(1), anyString(),
                eq("PENDING"), nextRetryAt.capture());
        assertThat(nextRetryAt.getValue()).isBetween(lowerBound, upperBound);
        verify(pendingPushDeliveryRepository, never()).markProcessed(any());
    }

    @Test
    void fifthFailureMovesToDeadLetter() {
        PushSubscription subscription = subscription();
        givenNotification();
        when(subscriptionRepository.findByUserId(USER)).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(new WebPushResult(500, false, "boom"));
        PendingPushDelivery delivery = PendingPushDelivery.builder().id(UUID.randomUUID())
                .notificationId(NOTIFICATION_ID).attempts(4).build();
        when(pendingPushDeliveryRepository.lockPendingBatch(push.batchSize())).thenReturn(List.of(delivery));

        relay.dispatchPending();

        verify(pendingPushDeliveryRepository).recordFailure(eq(delivery.getId()), eq(5), anyString(),
                eq("FAILED_DEAD_LETTER"), any(Instant.class));
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
