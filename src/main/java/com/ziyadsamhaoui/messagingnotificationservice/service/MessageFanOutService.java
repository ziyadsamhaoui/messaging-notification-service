package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.RoomMembership;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.repository.RoomMembershipRepository;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.DisplayNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageFanOutService {

    private final MessageSenderCacheService messageSenderCacheService;
    private final RoomMembershipRepository roomMembershipRepository;
    private final NotificationDeliveryService deliveryService;

    @Transactional
    public int handleMessageSent(String messageId, String roomId, UUID senderId, String senderUsername, String content,
            Instant createdAt) {

        messageSenderCacheService.record(messageId, roomId, senderId, createdAt);

        String body = DisplayNames.orFallback(senderUsername) + ": " + (content == null ? "" : content);
        Instant now = Instant.now();
        List<RoomMembership> members = roomMembershipRepository.findByRoomId(roomId);
        int created = 0;

        for (RoomMembership member : members) {
            if (member.getUserId().equals(senderId)) {
                continue;
            }
            if (member.isMuteActive(now)) {
                continue;
            }
            if (deliveryService.notifyUser(member.getUserId(), NotificationType.MESSAGE,
                    NotificationSource.MESSAGE, messageId, body)) {
                created++;
            }
        }

        return created;
    }
}
