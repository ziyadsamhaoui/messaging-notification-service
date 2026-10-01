package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationSource;
import com.ziyadsamhaoui.messagingnotificationservice.model.enums.NotificationType;
import com.ziyadsamhaoui.messagingnotificationservice.service.support.DisplayNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationNotificationService {

    private final NotificationDeliveryService deliveryService;

    @Transactional
    public boolean handleInvitationSent(String invitationId, UUID invitedId, String roomName, String inviterUsername) {
        String body = DisplayNames.orFallback(inviterUsername) + " invited you to "
                + DisplayNames.orFallback(roomName);
        return deliveryService.notifyUser(invitedId, NotificationType.INVITATION, NotificationSource.INVITATION,
                invitationId, body);
    }

    @Transactional
    public boolean handleInvitationAccepted(String invitationId, UUID inviterId, String invitedUsername) {
        String body = DisplayNames.orFallback(invitedUsername) + " accepted your invitation";
        return deliveryService.notifyUser(inviterId, NotificationType.INVITATION, NotificationSource.INVITATION,
                invitationId + ":accepted", body);
    }
}
