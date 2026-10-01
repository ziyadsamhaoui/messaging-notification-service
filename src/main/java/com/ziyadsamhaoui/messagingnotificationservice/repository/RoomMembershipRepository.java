package com.ziyadsamhaoui.messagingnotificationservice.repository;

import com.ziyadsamhaoui.messagingnotificationservice.model.RoomMembership;
import com.ziyadsamhaoui.messagingnotificationservice.model.RoomMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMembershipRepository extends JpaRepository<RoomMembership, RoomMembershipId> {

    List<RoomMembership> findByRoomId(String roomId);

    Optional<RoomMembership> findByRoomIdAndUserId(String roomId, UUID userId);

    @Modifying
    @Query("delete from RoomMembership m where m.roomId = :roomId and m.userId = :userId")
    int deleteMembership(@Param("roomId") String roomId, @Param("userId") UUID userId);
}
