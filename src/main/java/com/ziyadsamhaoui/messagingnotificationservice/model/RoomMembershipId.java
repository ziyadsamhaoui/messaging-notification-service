package com.ziyadsamhaoui.messagingnotificationservice.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoomMembershipId implements Serializable {

    private String roomId;

    private UUID userId;
}
