package com.ziyadsamhaoui.messagingnotificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.dto.CursorPage;
import com.ziyadsamhaoui.messagingnotificationservice.dto.NotificationResponse;
import com.ziyadsamhaoui.messagingnotificationservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.NotificationCursorCodec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationFeedServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private NotificationRepository notificationRepository;
    private NotificationCursorCodec cursorCodec;
    private NotificationFeedService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        cursorCodec = new NotificationCursorCodec();
        NotificationProperties properties = new NotificationProperties(2, 100, Duration.ofDays(90),
                Duration.ofHours(24), null);
        service = new NotificationFeedService(notificationRepository, cursorCodec, properties);
    }

    @Test
    void firstPageReturnsNextCursorWhenMoreRowsExist() {
        when(notificationRepository.findFirstPage(eq(USER), eq(false), eq(3)))
                .thenReturn(List.of(notification(1), notification(2), notification(3)));

        CursorPage<NotificationResponse> page = service.list(USER, null, null, false);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void cursorIsDecodedIntoAKeysetQuery() {
        Notification oldest = notification(1);
        String cursor = cursorCodec.encode(oldest.getCreatedAt(), oldest.getId());
        when(notificationRepository.findNextPage(eq(USER), eq(false), any(Instant.class), any(UUID.class), eq(11)))
                .thenReturn(List.of(notification(2)));

        CursorPage<NotificationResponse> page = service.list(USER, cursor, 10, false);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        verify(notificationRepository).findNextPage(eq(USER), eq(false), eq(oldest.getCreatedAt()),
                eq(oldest.getId()), eq(11));
    }

    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> service.list(USER, "%%%not-base64%%%", null, false))
                .isInstanceOf(InvalidRequestException.class);
    }

    private Notification notification(int minutesAgo) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(USER)
                .type(NotificationType.MESSAGE)
                .sourceType(NotificationSource.MESSAGE)
                .sourceId("msg-" + minutesAgo)
                .content("zara: hi")
                .createdAt(Instant.now().minus(Duration.ofMinutes(minutesAgo)).truncatedTo(ChronoUnit.MILLIS))
                .build();
    }
}
