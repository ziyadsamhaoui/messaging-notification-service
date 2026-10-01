package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ziyadsamhaoui.messagingnotificationservice.service.ConnectionNotificationService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProfileEventsConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConnectionNotificationService connectionNotificationService;
    private UserProfileEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        connectionNotificationService = mock(ConnectionNotificationService.class);
        consumer = new UserProfileEventsConsumer(connectionNotificationService, MAPPER);
    }

    @Test
    void blockedAndUnblockedProduceNoNotifications() {
        consumer.onMessage(record(envelope("USER_BLOCKED",
                "{\"blockerId\":\"a\",\"blockedId\":\"b\",\"blockedAt\":\"2026-01-01T10:00:00Z\"}")));
        consumer.onMessage(record(envelope("USER_UNBLOCKED", "{\"blockerId\":\"a\",\"blockedId\":\"b\"}")));

        verify(connectionNotificationService, never()).handleConnectionAccepted(anyString(), any(), any(), any(), any());
    }

    @Test
    void connectionAcceptedNotifiesBothUsers() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String connectionId = "42";

        String payload = "{\"userIdA\":\"%s\",\"userIdB\":\"%s\",\"userAUsername\":\"ana\","
                + "\"userBUsername\":\"ben\",\"acceptedAt\":\"2026-01-01T10:00:00Z\"}";

        consumer.onMessage(record(envelopeWithAggregate("USER_CONNECTION_ACCEPTED", connectionId,
                payload.formatted(userA, userB))));

        verify(connectionNotificationService).handleConnectionAccepted(connectionId, userA, userB, "ana", "ben");
    }

    @Test
    void malformedPayloadDoesNotThrowOutOfTheListener() {
        assertThatCode(() -> consumer.onMessage(record("not-json"))).doesNotThrowAnyException();
        assertThatCode(() -> consumer.onMessage(record(envelope("USER_CONNECTION_ACCEPTED", "{}"))))
                .doesNotThrowAnyException();
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(UserProfileEventsConsumer.TOPIC, 0, 0L, "key", value);
    }

    private String envelope(String eventType, String payloadJson) {
        return envelopeWithAggregate(eventType, UUID.randomUUID().toString(), payloadJson);
    }

    private String envelopeWithAggregate(String eventType, String aggregateId, String payloadJson) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-01-01T10:00:00Z",
                  "producer": "messaging-user-service",
                  "correlationId": "corr-1",
                  "aggregateId": "%s",
                  "payload": %s
                }
                """.formatted(UUID.randomUUID(), eventType, aggregateId, payloadJson.trim());
    }
}
