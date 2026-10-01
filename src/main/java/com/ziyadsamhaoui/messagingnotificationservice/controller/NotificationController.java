package com.ziyadsamhaoui.messagingnotificationservice.controller;

import com.ziyadsamhaoui.messagingnotificationservice.dto.CursorPage;
import com.ziyadsamhaoui.messagingnotificationservice.dto.NotificationResponse;
import com.ziyadsamhaoui.messagingnotificationservice.dto.PreferencesResponse;
import com.ziyadsamhaoui.messagingnotificationservice.dto.RegisterSubscriptionRequest;
import com.ziyadsamhaoui.messagingnotificationservice.dto.SubscriptionResponse;
import com.ziyadsamhaoui.messagingnotificationservice.dto.UnreadCountResponse;
import com.ziyadsamhaoui.messagingnotificationservice.dto.UpdatePreferencesRequest;
import com.ziyadsamhaoui.messagingnotificationservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingnotificationservice.service.NotificationFeedService;
import com.ziyadsamhaoui.messagingnotificationservice.service.NotificationPreferenceService;
import com.ziyadsamhaoui.messagingnotificationservice.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationFeedService feedService;
    private final PushSubscriptionService pushSubscriptionService;
    private final NotificationPreferenceService preferenceService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public CursorPage<NotificationResponse> list(@RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        return feedService.list(currentUserProvider.requireUserId(), cursor, limit, unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(feedService.unreadCount(currentUserProvider.requireUserId()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        feedService.markRead(currentUserProvider.requireUserId(), id);
    }

    @PostMapping("/read-all")
    public UnreadCountResponse markAllRead() {
        return new UnreadCountResponse(feedService.markAllRead(currentUserProvider.requireUserId()));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@Valid @RequestBody RegisterSubscriptionRequest request) {
        return pushSubscriptionService.subscribe(currentUserProvider.requireUserId(), request);
    }

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable UUID id) {
        pushSubscriptionService.unsubscribe(currentUserProvider.requireUserId(), id);
    }

    @GetMapping("/preferences")
    public PreferencesResponse preferences() {
        return preferenceService.get(currentUserProvider.requireUserId());
    }

    @PatchMapping("/preferences")
    public PreferencesResponse updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        return preferenceService.update(currentUserProvider.requireUserId(), request);
    }
}
