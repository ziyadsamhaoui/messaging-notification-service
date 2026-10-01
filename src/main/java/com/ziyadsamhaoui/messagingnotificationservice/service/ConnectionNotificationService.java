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
public class ConnectionNotificationService {

    private final NotificationDeliveryService deliveryService;

    @Transactional
    public int handleConnectionAccepted(String connectionId, UUID userIdA, UUID userIdB, String usernameA,
            String usernameB) {

        int created = 0;

        if (deliveryService.notifyUser(userIdA, NotificationType.SYSTEM, NotificationSource.SYSTEM, connectionId,
                DisplayNames.orFallback(usernameB) + " accepted your connection request")) {
            created++;
        }

        if (deliveryService.notifyUser(userIdB, NotificationType.SYSTEM, NotificationSource.SYSTEM, connectionId,
                DisplayNames.orFallback(usernameA) + " accepted your connection request")) {
            created++;
        }

        return created;
    }
}
