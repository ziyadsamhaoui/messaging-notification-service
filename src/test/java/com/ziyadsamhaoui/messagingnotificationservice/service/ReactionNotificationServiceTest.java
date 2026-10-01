package com.ziyadsamhaoui.messagingnotificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReactionNotificationServiceTest {

    private MessageSenderCacheService senderCacheService;
    private NotificationDeliveryService deliveryService;
    private ReactionNotificationService service;

    @BeforeEach
    void setUp() {
        senderCacheService = mock(MessageSenderCacheService.class);
        deliveryService = mock(NotificationDeliveryService.class);
        service = new ReactionNotificationService(senderCacheService, deliveryService);
    }

    @Test
    void notifiesTheMessageSenderWithACompositeSourceId() {
        UUID sender = UUID.randomUUID();
        UUID reactor = UUID.randomUUID();
        when(senderCacheService.resolveSender("msg-1")).thenReturn(Optional.of(sender));
        when(deliveryService.notifyUser(any(), any(), any(), anyString(), anyString())).thenReturn(true);

        boolean created = service.handleReactionAdded("msg-1", reactor, "bob", "thumbsup");

        assertThat(created).isTrue();
        verify(deliveryService).notifyUser(sender, NotificationType.REACTION, NotificationSource.REACTION,
                "msg-1:" + reactor, "bob reacted thumbsup to your message");
    }

    @Test
    void selfReactionNotifiesNobody() {
        UUID reactor = UUID.randomUUID();
        when(senderCacheService.resolveSender("msg-1")).thenReturn(Optional.of(reactor));

        boolean created = service.handleReactionAdded("msg-1", reactor, "bob", "thumbsup");

        assertThat(created).isFalse();
        verify(deliveryService, never()).notifyUser(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void unknownMessageNotifiesNobody() {
        when(senderCacheService.resolveSender("missing")).thenReturn(Optional.empty());

        boolean created = service.handleReactionAdded("missing", UUID.randomUUID(), "bob", "thumbsup");

        assertThat(created).isFalse();
        verify(deliveryService, never()).notifyUser(any(), any(), any(), anyString(), anyString());
    }
}
