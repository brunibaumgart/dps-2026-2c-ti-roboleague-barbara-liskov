package com.roboleague.api;

import com.roboleague.repository.ConcurrentModificationConflict;
import com.roboleague.repository.jpa.CorruptDataException;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.usecase.InconsistentStateException;
import com.roboleague.usecase.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each failure cause maps to its own status with an ErrorDto body, without a Spring context or a database: a
 * test-only controller throws what the use cases and adapters would.
 */
class ApiExceptionHandlerTest {
    private static final String SECRET = "org.postgresql.util.PSQLException: relation attempts";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "not-found, 404, Attempt not found: a-1",
            "business-rule, 422, Team t-2 does not own attempt a-1",
            "conflict, 409, Attempt a-1 was changed by someone else",
            "stale, 409, Someone else changed the same data at the same time; load it again and retry",
            "illegal-state, 409, attempt is evaluated",
            "illegal-argument, 400, teamId cannot be blank",
            "corrupt, 500, " + ApiExceptionHandler.INTERNAL_ERROR,
            "inconsistent, 500, " + ApiExceptionHandler.INTERNAL_ERROR,
            "integrity, 500, " + ApiExceptionHandler.INTERNAL_ERROR,
            "unexpected, 500, " + ApiExceptionHandler.INTERNAL_ERROR,
            "database-down, 503, " + ApiExceptionHandler.UNAVAILABLE})
    void eachCauseHasItsStatusAndTechnicalOnesHideTheirDetails(String cause, int status, String error) throws Exception {
        mvc.perform(get("/failures/{cause}", cause))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    void aValueOfTheWrongTypeNamesItsFieldPath() throws Exception {
        mvc.perform(post("/bodies").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "ok", "items": [{"count": 1}, {"count": "many"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for field 'items[1].count'"));
        mvc.perform(post("/bodies").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void anUnknownRouteKeepsItsStatusWithAnErrorBody() throws Exception {
        mvc.perform(post("/failures/{cause}", "unexpected"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").isString());
    }

    @RestController
    static class FailingController {

        @GetMapping("/failures/{cause}")
        String fail(@PathVariable String cause) {
            throw switch (cause) {
                case "not-found" -> new NotFoundException("Attempt", "a-1");
                case "business-rule" -> new BusinessRuleViolation("Team t-2 does not own attempt a-1");
                case "conflict" -> new ConcurrentModificationConflict("Attempt a-1 was changed by someone else",
                        new OptimisticLockingFailureException(SECRET));
                case "stale" -> new OptimisticLockingFailureException(SECRET);
                case "illegal-state" -> new IllegalStateException("attempt is evaluated");
                case "illegal-argument" -> new IllegalArgumentException("teamId cannot be blank");
                case "corrupt" -> new CorruptDataException(SECRET, new IllegalArgumentException(SECRET));
                case "inconsistent" -> new InconsistentStateException(SECRET);
                case "integrity" -> new DataIntegrityViolationException(SECRET);
                case "database-down" -> new CannotCreateTransactionException(SECRET);
                default -> new RuntimeException(SECRET);
            };
        }

        @PostMapping("/bodies")
        String body(@RequestBody Body body) {
            return body.name();
        }
    }

    record Body(String name, List<Item> items) {
    }

    record Item(int count) {
    }
}
