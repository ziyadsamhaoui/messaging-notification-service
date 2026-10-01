package com.ziyadsamhaoui.messagingnotificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_membership_cache")
@IdClass(RoomMembershipId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RoomMembership {

    @Id
    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_muted", nullable = false)
    private boolean muted;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    public boolean isMuteActive(Instant reference) {
        return muted && (mutedUntil == null || mutedUntil.isAfter(reference));
    }
}
