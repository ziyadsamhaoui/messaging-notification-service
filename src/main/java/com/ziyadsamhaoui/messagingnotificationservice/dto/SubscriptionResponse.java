package com.ziyadsamhaoui.messagingnotificationservice.dto;

import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;

import java.util.UUID;

public record SubscriptionResponse(UUID id, String endpoint) {

    public static SubscriptionResponse from(PushSubscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getEndpoint());
    }
}
