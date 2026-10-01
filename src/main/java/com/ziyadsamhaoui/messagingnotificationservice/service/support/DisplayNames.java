package com.ziyadsamhaoui.messagingnotificationservice.service.support;

public final class DisplayNames {

    private static final String FALLBACK = "Someone";

    private DisplayNames() {
    }

    public static String orFallback(String value) {
        return value == null || value.isBlank() ? FALLBACK : value;
    }
}
