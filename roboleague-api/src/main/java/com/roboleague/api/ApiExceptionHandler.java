package com.roboleague.api;

import com.roboleague.tournament.eligibility.TeamIneligibleException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorDto> unreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
            return error(HttpStatus.BAD_REQUEST, "Unknown field '" + unknown.getPropertyName() + "'",
                    List.of("expected one of " + unknown.getKnownPropertyIds().stream().map(String::valueOf).sorted().toList()));
        }
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorDto> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    /**
     * Inside a use case's transaction, Postgres refuses a stale or duplicate write when it commits: someone else
     * saved the same aggregate in between. Like any conflicting transition, it is a 409 to retry.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorDto> concurrentChange(RuntimeException e) {
        return error(HttpStatus.CONFLICT, "Someone else changed the same data at the same time; load it again and retry",
                List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorDto> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ErrorDto(message, details));
    }
}
