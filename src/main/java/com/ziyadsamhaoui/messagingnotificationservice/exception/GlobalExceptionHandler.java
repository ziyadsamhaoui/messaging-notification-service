package com.ziyadsamhaoui.messagingnotificationservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getAllErrors().get(0).getDefaultMessage()
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(UnauthenticatedRequestException.class)
    ResponseEntity<Map<String, Object>> handleUnauthenticated(UnauthenticatedRequestException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message,
                        "timestamp", Instant.now().toString()
                ));
    }
}
