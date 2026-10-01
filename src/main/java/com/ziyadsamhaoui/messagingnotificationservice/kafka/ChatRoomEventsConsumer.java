package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import com.ziyadsamhaoui.messagingnotificationservice.service.RoomMembershipCacheService;
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
public class ChatRoomEventsConsumer {

    public static final String TOPIC = "badrlink.chat.room.v1";
    public static final String GROUP_ID = "notification-service";

    private final RoomMembershipCacheService roomMembershipCacheService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload = envelope.get("payload");

            switch (envelope.get("eventType").asString()) {
                case "PARTICIPANT_ADDED" -> roomMembershipCacheService.memberAdded(
                        JsonPayloads.text(payload, "roomId"), JsonPayloads.uuid(payload, "userId"));
                case "PARTICIPANT_REMOVED" -> roomMembershipCacheService.memberRemoved(
                        JsonPayloads.text(payload, "roomId"), JsonPayloads.uuid(payload, "userId"));
                case "PARTICIPANT_MUTED" -> roomMembershipCacheService.applyMute(
                        JsonPayloads.text(payload, "roomId"), JsonPayloads.uuid(payload, "userId"), true,
                        JsonPayloads.instant(payload, "mutedUntil"));
                case "PARTICIPANT_UNMUTED" -> roomMembershipCacheService.applyMute(
                        JsonPayloads.text(payload, "roomId"), JsonPayloads.uuid(payload, "userId"), false, null);
                default -> log.debug("ignoring event type {} on {}", envelope.get("eventType").asString(), TOPIC);
            }
        } catch (RuntimeException exception) {
            log.error("failed to process event from {}: {}", TOPIC, record.value(), exception);
        }
    }
}
