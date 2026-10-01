package com.ziyadsamhaoui.messagingnotificationservice.push;

public record WebPushResult(int statusCode, boolean gone, String error) {

    public boolean success() {
        return statusCode >= 200 && statusCode < 300;
    }

    public static WebPushResult subscriptionGone() {
        return new WebPushResult(410, true, "subscription no longer valid");
    }

    public static WebPushResult failure(String error) {
        return new WebPushResult(0, false, error);
    }
}
