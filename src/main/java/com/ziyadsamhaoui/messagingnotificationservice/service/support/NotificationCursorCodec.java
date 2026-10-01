package com.ziyadsamhaoui.messagingnotificationservice.service.support;

import com.ziyadsamhaoui.messagingnotificationservice.exception.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationCursorCodec {

    private static final String SEPARATOR = "|";

    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public String encode(Instant createdAt, UUID id) {
        return encoder.encodeToString((createdAt.toEpochMilli() + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Cursor> decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }

        String raw = decodeValue(cursor);
        int separatorIndex = raw.indexOf(SEPARATOR);

        if (separatorIndex < 1 || separatorIndex == raw.length() - 1) {
            throw malformedCursor();
        }

        try {
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separatorIndex)));
            UUID id = UUID.fromString(raw.substring(separatorIndex + 1));
            return Optional.of(new Cursor(createdAt, id));
        } catch (IllegalArgumentException exception) {
            throw malformedCursor();
        }
    }

    private String decodeValue(String cursor) {
        try {
            return new String(decoder.decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw malformedCursor();
        }
    }

    private InvalidRequestException malformedCursor() {
        return new InvalidRequestException("The supplied cursor is malformed");
    }

    public record Cursor(Instant createdAt, UUID id) {
    }
}
