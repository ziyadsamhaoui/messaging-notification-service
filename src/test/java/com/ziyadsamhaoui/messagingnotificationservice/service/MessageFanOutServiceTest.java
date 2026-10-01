package com.ziyadsamhaoui.messagingnotificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingnotificationservice.model.RoomMembership;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.RoomMembershipRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageFanOutServiceTest {

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID MUTED_MEMBER = UUID.randomUUID();
    private static final UUID ACTIVE_MEMBER = UUID.randomUUID();

    private MessageSenderCacheService senderCacheService;
    private RoomMembershipRepository roomMembershipRepository;
    private NotificationDeliveryService deliveryService;
    private MessageFanOutService service;

    @BeforeEach
    void setUp() {
        senderCacheService = mock(MessageSenderCacheService.class);
        roomMembershipRepository = mock(RoomMembershipRepository.class);
        deliveryService = mock(NotificationDeliveryService.class);
        service = new MessageFanOutService(senderCacheService, roomMembershipRepository, deliveryService);
    }

    @Test
    void fanOutSkipsSenderAndMutedMembers() {
        when(roomMembershipRepository.findByRoomId("room-1")).thenReturn(List.of(
                RoomMembership.builder().roomId("room-1").userId(SENDER).muted(false).build(),
                RoomMembership.builder().roomId("room-1").userId(MUTED_MEMBER).muted(true).build(),
                RoomMembership.builder().roomId("room-1").userId(ACTIVE_MEMBER).muted(false).build()));
        when(deliveryService.notifyUser(ACTIVE_MEMBER, NotificationType.MESSAGE, NotificationSource.MESSAGE, "msg-1",
                "zara: hello")).thenReturn(true);

        int created = service.handleMessageSent("msg-1", "room-1", SENDER, "zara", "hello", Instant.now());

        assertThat(created).isEqualTo(1);
        verify(senderCacheService).record(eq("msg-1"), eq("room-1"), eq(SENDER), any(Instant.class));
        verify(deliveryService).notifyUser(ACTIVE_MEMBER, NotificationType.MESSAGE, NotificationSource.MESSAGE,
                "msg-1", "zara: hello");
        verify(deliveryService, never()).notifyUser(org.mockito.ArgumentMatchers.eq(SENDER), any(), any(), any(),
                any());
        verify(deliveryService, never()).notifyUser(org.mockito.ArgumentMatchers.eq(MUTED_MEMBER), any(), any(), any(),
                any());
    }
}
