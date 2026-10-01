package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.model.RoomMembership;
import com.ziyadsamhaoui.messagingnotificationservice.repository.RoomMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMembershipCacheService {

    private final RoomMembershipRepository roomMembershipRepository;

    @Transactional
    public void memberAdded(String roomId, UUID userId) {
        RoomMembership membership = roomMembershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> RoomMembership.builder().roomId(roomId).userId(userId).build());
        membership.setMuted(false);
        membership.setMutedUntil(null);
        roomMembershipRepository.save(membership);
    }

    @Transactional
    public void memberRemoved(String roomId, UUID userId) {
        roomMembershipRepository.deleteMembership(roomId, userId);
    }

    @Transactional
    public void applyMute(String roomId, UUID userId, boolean muted, Instant mutedUntil) {
        RoomMembership membership = roomMembershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> RoomMembership.builder().roomId(roomId).userId(userId).build());
        membership.setMuted(muted);
        membership.setMutedUntil(muted ? mutedUntil : null);
        roomMembershipRepository.save(membership);
    }
}
