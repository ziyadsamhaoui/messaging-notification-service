package com.ziyadsamhaoui.messagingnotificationservice.push;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationPreferenceRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PendingPushDeliveryRepository;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that one Web Push attempt is counted exactly once, under the outcome that the push
 * service reported: 2xx is success, HTTP 410 is the gone series that reveals stale
 * subscriptions, and everything else is a failure.
 */
class PushDeliveryRelayMetricsTest {

    private static final String METRIC = PushDeliveryRelay.DELIVERY_METRIC;

    private final PendingPushDeliveryRepository pendingPushDeliveryRepository =
            mock(PendingPushDeliveryRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository preferenceRepository =
            mock(NotificationPreferenceRepository.class);
    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final WebPushSender webPushSender = mock(WebPushSender.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private SimpleMeterRegistry registry;
    private PushDeliveryRelay relay;
    private PendingPushDelivery delivery;
    private PushSubscription subscription;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        relay = new PushDeliveryRelay(pendingPushDeliveryRepository, notificationRepository,
                preferenceRepository, subscriptionRepository, webPushSender, properties(),
                new ObjectMapper(), transactionTemplate, registry);

        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        subscription = PushSubscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .endpoint("https://push.example.test/subscription")
                .p256dhKey("p256dh")
                .authKey("auth")
                .createdAt(Instant.now())
                .build();
        delivery = PendingPushDelivery.builder()
                .id(UUID.randomUUID())
                .notificationId(notificationId)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(
                Notification.builder()
                        .id(notificationId)
                        .userId(userId)
                        .type(NotificationType.MESSAGE)
                        .content("hello")
                        .sourceType(NotificationSource.MESSAGE)
                        .sourceId("room-1")
                        .createdAt(Instant.now())
                        .build()));
        when(preferenceRepository.findById(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByUserId(userId)).thenReturn(List.of(subscription));
    }

    @Test
    void acceptedPushIsCountedAsSuccess() {
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(new WebPushResult(201, false, null));

        assertThat(relay.attempt(delivery)).isTrue();

        assertThat(count("success")).isEqualTo(1.0);
        assertThat(count("gone")).isZero();
        assertThat(count("failure")).isZero();
        verify(subscriptionRepository).stampLastUsed(eq(subscription.getId()), any(Instant.class));
    }

    @Test
    void goneSubscriptionIsCountedSeparatelyAndDeleted() {
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(WebPushResult.subscriptionGone());

        assertThat(relay.attempt(delivery)).isFalse();

        assertThat(count("gone")).isEqualTo(1.0);
        assertThat(count("success")).isZero();
        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void transportFailureIsCountedAsFailure() {
        when(webPushSender.send(eq(subscription), anyString())).thenReturn(WebPushResult.failure("connection reset"));

        assertThat(relay.attempt(delivery)).isFalse();

        assertThat(count("failure")).isEqualTo(1.0);
        assertThat(count("success")).isZero();
        assertThat(count("gone")).isZero();
    }

    @Test
    void eachAttemptedSubscriptionIsCountedOnce() {
        when(webPushSender.send(any(PushSubscription.class), anyString()))
                .thenReturn(WebPushResult.failure("gateway timeout"));

        relay.attempt(delivery);
        relay.attempt(delivery);

        assertThat(count("failure")).isEqualTo(2.0);
    }

    /**
     * A Micrometer counter series only exists once it has been incremented, so an outcome that
     * has not happened yet is legitimately absent rather than zero-valued.
     */
    private double count(String result) {
        var counter = registry.find(METRIC).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static NotificationProperties properties() {
        return new NotificationProperties(30, 100, Duration.ofDays(90), Duration.ofHours(24),
                new NotificationProperties.Push(true, Duration.ofSeconds(5), 100, 5, 1_000,
                        "public-key", "private-key", "mailto:admin@badrlink.local"));
    }
}
