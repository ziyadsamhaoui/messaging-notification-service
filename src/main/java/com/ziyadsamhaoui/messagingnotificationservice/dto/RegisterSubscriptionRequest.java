package com.ziyadsamhaoui.messagingnotificationservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterSubscriptionRequest(
        @NotBlank(message = "endpoint is required") String endpoint,
        @NotNull(message = "keys are required") @Valid Keys keys) {

    public record Keys(
            @NotBlank(message = "keys.p256dh is required") String p256dh,
            @NotBlank(message = "keys.auth is required") String auth) {
    }
}
