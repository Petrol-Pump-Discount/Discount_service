package com.petrolpump.discount.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        String msg = ex.getReason() == null ? "Something went wrong" : sanitize(ex.getReason());
        if (ex.getStatusCode().is5xxServerError()) {
            log.warn("API {}: {}", ex.getStatusCode().value(), ex.getReason());
        }
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", msg));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDup(DataIntegrityViolationException ex) {
        log.warn("Data integrity: {}", rootMessage(ex));
        String lower = rootMessage(ex).toLowerCase();
        String msg = "Could not save — this record may already exist.";
        if (lower.contains("vehicle") || lower.contains("regno") || lower.contains("vehicle_links")) {
            msg = "This vehicle is already linked to your account.";
        } else if (lower.contains("receipt") || lower.contains("bill")) {
            msg = "This bill was already submitted.";
        } else if (lower.contains("phone") || lower.contains("app_users")) {
            msg = "This phone number is already registered.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", msg));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooBig(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Photo is too large. Take a smaller photo and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Something went wrong. Please try again."));
    }

    /** Never leak SQL / stack / driver text to clients. */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Something went wrong";
        }
        String s = raw.trim();
        String lower = s.toLowerCase();
        if (lower.contains("sql") || lower.contains("constraint") || lower.contains("hibernate")
                || lower.contains("jdbc") || lower.contains("psql") || lower.contains("duplicate key")
                || lower.contains("org.springframework") || lower.contains("exception")
                || lower.contains("stack") || s.length() > 180) {
            return "Something went wrong. Please try again.";
        }
        return s;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? "" : t.getMessage();
    }
}
