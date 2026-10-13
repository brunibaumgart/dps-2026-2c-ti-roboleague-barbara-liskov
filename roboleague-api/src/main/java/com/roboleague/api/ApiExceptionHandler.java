package com.roboleague.api;

import com.roboleague.repository.jpa.CorruptDataException;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.support.ConflictException;
import com.roboleague.tournament.eligibility.TeamIneligibleException;
import com.roboleague.usecase.InconsistentStateException;
import com.roboleague.usecase.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;

/**
 * Translates what the use cases throw into HTTP, always with an {@link ErrorDto} body. Use cases that return sealed
 * results are mapped in their controller with a switch instead (see README, "Convenciones de la API").
 * Technical failures answer 500/503 with a generic message; their cause goes to the log, never to the client.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    static final String INTERNAL_ERROR = "Internal error; the problem was logged";
    static final String UNAVAILABLE = "The service is temporarily unavailable; try again later";

    @ExceptionHandler(TeamIneligibleException.class)
    ResponseEntity<ErrorDto> ineligible(TeamIneligibleException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e.getViolations());
    }

    @ExceptionHandler(BusinessRuleViolation.class)
    ResponseEntity<ErrorDto> businessRule(BusinessRuleViolation e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorDto> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Includes {@code ConcurrentModificationConflict}: someone else saved the same aggregate first. */
    @ExceptionHandler({ConflictException.class, IllegalStateException.class})
    ResponseEntity<ErrorDto> conflict(RuntimeException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    /** A stale write that reached Postgres outside the adapters' own translation. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorDto> staleWrite(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "Someone else changed the same data at the same time; load it again and retry");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorDto> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorDto> unavailable(Exception e) {
        log.warn("Database unavailable", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    /**
     * Stored data that cannot be rebuilt, references between aggregates that do not hold, a constraint the domain
     * should have checked first, or anything unexpected: the request may be fine, the server is not.
     */
    @ExceptionHandler({CorruptDataException.class, InconsistentStateException.class,
            DataIntegrityViolationException.class, Exception.class})
    ResponseEntity<ErrorDto> internal(Exception e) {
        log.error("Unexpected failure while handling a request", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ErrorDto body = switch (e.getCause()) {
            case UnrecognizedPropertyException unknown -> new ErrorDto("Unknown field '" + path(unknown) + "'",
                    List.of("expected one of " + unknown.getKnownPropertyIds().stream()
                            .map(String::valueOf).sorted().toList()));
            case MismatchedInputException mismatch when !mismatch.getPath().isEmpty() ->
                    new ErrorDto("Invalid value for field '" + path(mismatch) + "'");
            case null, default -> new ErrorDto("Malformed request body");
        };
        return handleExceptionInternal(e, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException e,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        return handleExceptionInternal(e, new ErrorDto("Missing parameter '" + e.getParameterName() + "'"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = e instanceof MethodArgumentTypeMismatchException argument ? argument.getName()
                : e.getPropertyName();
        List<String> details = e.getRequiredType() == null ? List.of()
                : List.of("'" + e.getValue() + "' is not a valid " + e.getRequiredType().getSimpleName());
        return handleExceptionInternal(e, new ErrorDto("Invalid value for parameter '" + name + "'", details),
                headers, status, request);
    }

    /**
     * Every other Spring MVC failure (unsupported method, unknown route...) keeps its status with an ErrorDto body.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        if (!(body instanceof ErrorDto)) {
            body = new ErrorDto(status.is5xxServerError() ? INTERNAL_ERROR : detailOf(e, body, status));
        }
        if (status.is5xxServerError()) {
            log.error("Unexpected failure while handling a request", e);
        }
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private static String detailOf(Exception e, Object body, HttpStatusCode status) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail
                : e instanceof ErrorResponse response ? response.getBody() : null;
        if (problem != null && problem.getDetail() != null) {
            return problem.getDetail();
        }
        return status instanceof HttpStatus known ? known.getReasonPhrase() : "Request failed";
    }

    /** Where the value sits in the body, such as {@code team.members[0].birthDate}. */
    private static String path(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String message) {
        return error(status, message, List.of());
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ErrorDto(message, details));
    }
}
