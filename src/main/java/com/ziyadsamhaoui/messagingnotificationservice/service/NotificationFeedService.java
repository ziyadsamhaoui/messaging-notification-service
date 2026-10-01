package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.dto.CursorPage;
import com.ziyadsamhaoui.messagingnotificationservice.dto.NotificationResponse;
import com.ziyadsamhaoui.messagingnotificationservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import com.ziyadsamhaoui.messagingnotificationservice.repository.NotificationRepository;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.NotificationCursorCodec;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.NotificationCursorCodec.Cursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationFeedService {

    private final NotificationRepository notificationRepository;
    private final NotificationCursorCodec cursorCodec;
    private final NotificationProperties properties;

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> list(UUID userId, String cursor, Integer limit, boolean unreadOnly) {
        int pageSize = resolvePageSize(limit);
        Optional<Cursor> decoded = cursorCodec.decode(cursor);

        List<Notification> rows = decoded
                .map(value -> notificationRepository.findNextPage(userId, unreadOnly, value.createdAt(), value.id(),
                        pageSize + 1))
                .orElseGet(() -> notificationRepository.findFirstPage(userId, unreadOnly, pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<Notification> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<NotificationResponse> items = page.stream().map(NotificationResponse::from).toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Notification last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getCreatedAt(), last.getId());
        }

        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        if (notificationRepository.markRead(notificationId, userId) == 0) {
            throw new ResourceNotFoundException("Notification not found");
        }
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    private int resolvePageSize(Integer limit) {
        if (limit == null) {
            return properties.defaultPageSize();
        }
        return Math.max(1, Math.min(limit, properties.maxPageSize()));
    }
}
