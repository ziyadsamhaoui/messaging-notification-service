package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import com.ziyadsamhaoui.messagingnotificationservice.service.ConnectionNotificationService;
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
public class UserProfileEventsConsumer {

    public static final String TOPIC = "badrlink.user.profile.v1";
    public static final String GROUP_ID = "notification-service";

    private final ConnectionNotificationService connectionNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            String eventType = envelope.get("eventType").asString();

            if (!"USER_CONNECTION_ACCEPTED".equals(eventType)) {
                log.debug("deliberately ignoring event type {} on {}", eventType, TOPIC);
                return;
            }

            JsonNode payload = envelope.get("payload");
            connectionNotificationService.handleConnectionAccepted(
                    JsonPayloads.text(envelope, "aggregateId"),
                    JsonPayloads.uuid(payload, "userIdA"),
                    JsonPayloads.uuid(payload, "userIdB"),
                    JsonPayloads.text(payload, "userAUsername"),
                    JsonPayloads.text(payload, "userBUsername"));
        } catch (RuntimeException exception) {
            log.error("failed to process event from {}: {}", TOPIC, record.value(), exception);
        }
    }
}
