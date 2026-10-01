package com.ziyadsamhaoui.messagingnotificationservice.repository;

import com.ziyadsamhaoui.messagingnotificationservice.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Modifying
    @Query(value = """
            insert into notifications (id, user_id, type, content, source_type, source_id, created_at, delivered_at, is_read)
            values (:id, :userId, :type, :content, :sourceType, :sourceId, :createdAt, :createdAt, false)
            on conflict (user_id, source_type, source_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("userId") UUID userId,
                       @Param("type") String type,
                       @Param("content") String content,
                       @Param("sourceType") String sourceType,
                       @Param("sourceId") String sourceId,
                       @Param("createdAt") Instant createdAt);

    @Query(value = """
            select * from notifications n
            where n.user_id = :userId
              and (:unreadOnly = false or n.is_read = false)
            order by n.created_at desc, n.id desc
            limit :limit
            """, nativeQuery = true)
    List<Notification> findFirstPage(@Param("userId") UUID userId,
                                     @Param("unreadOnly") boolean unreadOnly,
                                     @Param("limit") int limit);

    @Query(value = """
            select * from notifications n
            where n.user_id = :userId
              and (:unreadOnly = false or n.is_read = false)
              and (n.created_at, n.id) < (cast(:cursorCreatedAt as timestamptz), cast(:cursorId as uuid))
            order by n.created_at desc, n.id desc
            limit :limit
            """, nativeQuery = true)
    List<Notification> findNextPage(@Param("userId") UUID userId,
                                    @Param("unreadOnly") boolean unreadOnly,
                                    @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                    @Param("cursorId") UUID cursorId,
                                    @Param("limit") int limit);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.id = :id and n.userId = :userId")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);
}
