package com.roboleague.api;

import com.roboleague.usecase.TeamIneligibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates what the use cases throw into HTTP. Use cases that return sealed results
 * are mapped in their controller with a switch instead (see README, "Convenciones de la API").
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(TeamIneligibleException.class)
    ResponseEntity<ErrorDto> ineligible(TeamIneligibleException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e.getViolations());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorDto> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorDto> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ErrorDto(message, details));
    }
}
