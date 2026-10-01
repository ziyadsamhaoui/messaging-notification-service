package com.ziyadsamhaoui.messagingnotificationservice.exception;

public class UnauthenticatedRequestException extends RuntimeException {

    public UnauthenticatedRequestException(String message) {
        super(message);
    }
}
