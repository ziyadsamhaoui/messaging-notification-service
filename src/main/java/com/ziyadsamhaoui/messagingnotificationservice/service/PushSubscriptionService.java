package com.ziyadsamhaoui.messagingnotificationservice.service;

import com.ziyadsamhaoui.messagingnotificationservice.dto.RegisterSubscriptionRequest;
import com.ziyadsamhaoui.messagingnotificationservice.dto.SubscriptionResponse;
import com.ziyadsamhaoui.messagingnotificationservice.exception.ResourceNotFoundException;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import com.ziyadsamhaoui.messagingnotificationservice.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;

    @Transactional
    public SubscriptionResponse subscribe(UUID userId, RegisterSubscriptionRequest request) {
        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(() -> PushSubscription.builder()
                        .id(UUID.randomUUID())
                        .endpoint(request.endpoint())
                        .build());

        subscription.setUserId(userId);
        subscription.setP256dhKey(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());

        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    @Transactional
    public void unsubscribe(UUID userId, UUID subscriptionId) {
        PushSubscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        subscriptionRepository.delete(subscription);
    }
}
