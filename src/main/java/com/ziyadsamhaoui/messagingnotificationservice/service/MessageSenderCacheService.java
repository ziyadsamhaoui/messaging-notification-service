package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.MessageSender;
import com.ziyadsamhaoui.messagingnotificationservice.repository.MessageSenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageSenderCacheService {

    private final MessageSenderRepository messageSenderRepository;

    @Transactional
    public void record(String messageId, String roomId, UUID senderId, Instant createdAt) {
        if (messageSenderRepository.existsById(messageId)) {
            return;
        }
        messageSenderRepository.save(MessageSender.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .createdAt(createdAt == null ? Instant.now() : createdAt)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveSender(String messageId) {
        return messageSenderRepository.findById(messageId).map(MessageSender::getSenderId);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return messageSenderRepository.deleteOlderThan(cutoff);
    }
}
