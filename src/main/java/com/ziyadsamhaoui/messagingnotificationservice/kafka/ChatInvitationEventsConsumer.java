package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import com.ziyadsamhaoui.messagingnotificationservice.service.InvitationNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "badrlink.kafka", name = "enabled", havingValue = "true")
public class ChatInvitationEventsConsumer {

    public static final String TOPIC = "badrlink.chat.invitation.v1";
    public static final String GROUP_ID = "notification-service";

    private final InvitationNotificationService invitationNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload = envelope.get("payload");

            switch (envelope.get("eventType").asString()) {
                case "INVITATION_SENT" -> invitationNotificationService.handleInvitationSent(
                        JsonPayloads.text(payload, "invitationId"),
                        JsonPayloads.uuid(payload, "invitedId"),
                        JsonPayloads.text(payload, "roomName"),
                        JsonPayloads.text(payload, "inviterUsername"));
                case "INVITATION_ACCEPTED" -> invitationNotificationService.handleInvitationAccepted(
                        JsonPayloads.text(payload, "invitationId"),
                        JsonPayloads.uuid(payload, "inviterId"),
                        JsonPayloads.text(payload, "invitedUsername"));
                case "INVITATION_REJECTED" -> log.debug("INVITATION_REJECTED is deliberately ignored by notification");
                default -> log.debug("ignoring event type {} on {}", envelope.get("eventType").asString(), TOPIC);
            }
        } catch (RuntimeException exception) {
            log.error("failed to process event from {}: {}", TOPIC, record.value(), exception);
        }
    }
}
