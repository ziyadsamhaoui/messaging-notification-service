package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import com.ziyadsamhaoui.messagingnotificationservice.service.MessageFanOutService;
import com.ziyadsamhaoui.messagingnotificationservice.service.ReactionNotificationService;
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
public class ChatMessageEventsConsumer {

    public static final String TOPIC = "badrlink.chat.message.v1";
    public static final String GROUP_ID = "notification-service";

    private final MessageFanOutService messageFanOutService;
    private final ReactionNotificationService reactionNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload = envelope.get("payload");

            switch (envelope.get("eventType").asString()) {
                case "MESSAGE_SENT" -> messageFanOutService.handleMessageSent(
                        JsonPayloads.text(payload, "messageId"),
                        JsonPayloads.text(payload, "roomId"),
                        JsonPayloads.uuid(payload, "senderId"),
                        JsonPayloads.text(payload, "senderUsername"),
                        JsonPayloads.text(payload, "content"),
                        JsonPayloads.instant(payload, "createdAt"));
                case "REACTION_ADDED" -> reactionNotificationService.handleReactionAdded(
                        JsonPayloads.text(payload, "messageId"),
                        JsonPayloads.uuid(payload, "userId"),
                        JsonPayloads.text(payload, "reactorUsername"),
                        JsonPayloads.text(payload, "emoji"));
                case "MESSAGE_DELETED" -> log.debug("MESSAGE_DELETED is deliberately ignored by notification");
                default -> log.debug("ignoring event type {} on {}", envelope.get("eventType").asString(), TOPIC);
            }
        } catch (RuntimeException exception) {
            log.error("failed to process event from {}: {}", TOPIC, record.value(), exception);
        }
    }
}
