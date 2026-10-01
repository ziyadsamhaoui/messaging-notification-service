package com.ziyadsamhaoui.messagingnotificationservice.kafka;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

final class JsonPayloads {

    private JsonPayloads() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : UUID.fromString(value);
    }

    static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : Instant.parse(value);
    }

    static boolean flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean();
    }
}
