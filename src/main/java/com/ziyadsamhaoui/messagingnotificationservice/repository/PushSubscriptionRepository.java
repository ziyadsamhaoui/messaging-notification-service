package com.ziyadsamhaoui.messagingnotificationservice.repository;

import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    long deleteByEndpoint(String endpoint);

    @Modifying
    @Query("update PushSubscription s set s.lastUsedAt = :lastUsedAt where s.id = :id")
    int stampLastUsed(@Param("id") UUID id, @Param("lastUsedAt") Instant lastUsedAt);
}
