package com.ziyadsamhaoui.messagingnotificationservice.repository;

import com.ziyadsamhaoui.messagingnotificationservice.model.PendingPushDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PendingPushDeliveryRepository extends JpaRepository<PendingPushDelivery, UUID> {

    List<PendingPushDelivery> findByDeliveredAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
