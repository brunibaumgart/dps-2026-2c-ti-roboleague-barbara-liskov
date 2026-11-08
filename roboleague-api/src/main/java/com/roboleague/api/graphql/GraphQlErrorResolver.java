package com.roboleague.api.graphql;

import com.roboleague.port.in.NotFoundException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Translates what a use case throws while resolving a field into an entry of {@code errors} (D6). The field becomes
 * null and the rest of the response stays. Only reads reach GraphQL, so the table is shorter than the REST one: no
 * 409 or 422. Technical failures answer a generic message; their cause goes to the log, never to the client.
 */
@Component
class GraphQlErrorResolver extends DataFetcherExceptionResolverAdapter {
    private static final Logger log = LoggerFactory.getLogger(GraphQlErrorResolver.class);

    // Same texts as ApiExceptionHandler.INTERNAL_ERROR and UNAVAILABLE, which are package-private in
    // com.roboleague.api. Copied on purpose so this package does not change a file of the REST adapter; a client
    // reads the same message from either API. Keep them in sync until both share one place.
    static final String INTERNAL_ERROR = "Internal error; the problem was logged";
    static final String UNAVAILABLE = "The service is temporarily unavailable; try again later";

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment env) {
        return switch (exception) {
            case NotFoundException e -> error(env, ErrorType.NOT_FOUND, e.getMessage());
            case IllegalArgumentException e -> error(env, ErrorType.BAD_REQUEST, e.getMessage());
            // REST answers 503 here; ErrorType has no SERVICE_UNAVAILABLE, so the message tells it apart.
            case DataAccessResourceFailureException e -> unavailable(env, e);
            case CannotCreateTransactionException e -> unavailable(env, e);
            // A read never makes a transition, so IllegalStateException is stored data that does not hold (REST
            // reads give 409 only for writes); like InconsistentStateException and CorruptDataException, it is ours.
            default -> {
                log.error("Unexpected failure while resolving {}", env.getField().getName(), exception);
                yield error(env, ErrorType.INTERNAL_ERROR, INTERNAL_ERROR);
            }
        };
    }

    private static GraphQLError unavailable(DataFetchingEnvironment env, Exception exception) {
        log.warn("Database unavailable", exception);
        return error(env, ErrorType.INTERNAL_ERROR, UNAVAILABLE);
    }

    /** Carries the path and location of the field that failed. */
    private static GraphQLError error(DataFetchingEnvironment env, ErrorType type, String message) {
        return GraphqlErrorBuilder.newError(env).errorType(type).message(message).build();
    }
}
