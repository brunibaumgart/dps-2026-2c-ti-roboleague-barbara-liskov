package com.roboleague.api.scheduling;

import com.jayway.jsonpath.JsonPath;
import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.roboleague.api.EditionRequests.roundBody;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Scheduling rounds through HTTP: a rejected schedule saves nothing and leaves the earlier rounds as they were.
 */
class RoundControllerTest extends ApiTest {
    @Autowired
    private TeamRepository teams;

    @ParameterizedTest
    @ValueSource(strings = {"missing-time", "null-track", "missing-active", "no-judge", "duplicate-track", "zero-duration", "negative-interval", "edition-id", "foreign-category"})
    void invalidSchedulingNeverCreatesAPartialRound(String invalid) throws Exception {
        String id = "http05-bad-round-" + invalid;
        edition(id);
        challenge(id, id + "-challenge");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        String body = roundBody(id + "-small", 1).replace(id + "-small-", "http05-");
        body = switch (invalid) {
            case "missing-time" -> body.replace("\"startTime\": \"2026-11-10T10:00:00\",", "");
            case "null-track" -> body.replace("\"tracks\": [{", "\"tracks\": [null, {");
            case "missing-active" -> body.replace(", \"isActive\": true", "");
            case "no-judge" -> body.replace("[{\"id\": \"http05-j1\", \"fullName\": \"Judge\", \"specialty\": \"General\"}]", "[]");
            case "duplicate-track" -> body.replace("\"tracks\": [", "\"tracks\": [{\"id\": \"http05-p1\", \"name\": \"Other\", \"surfaceType\": \"Wood\", \"isActive\": true},");
            case "zero-duration" -> body.replace("\"slotDurationSeconds\": 300", "\"slotDurationSeconds\": 0");
            case "negative-interval" -> body.replace("\"intervalSeconds\": 60", "\"intervalSeconds\": -1");
            case "edition-id" -> body.replace("\"categoryId\":", "\"editionId\": \"contradictory\", \"categoryId\":");
            case "foreign-category" -> body.replace(id + "-small", "foreign");
            default -> throw new AssertionError(invalid);
        };
        mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"http05-p1\"", "\"" + id + "-small-p1\"")
                                .replace("\"http05-j1\"", "\"" + id + "-small-j1\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void aRoundOutsideTheEditionDatesIsUnprocessableAndNotSaved() throws Exception {
        String id = "http05-out-of-dates";
        edition(id);
        challenge(id, id + "-challenge");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());

        mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 1).replace("2026-11-10T10:00:00", "2026-11-13T10:00:00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("Round Round does not fit within the dates of edition " + id
                        + " (2026-11-10 to 2026-11-12)"));
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void duplicateRoundReturnsConflictAndKeepsThePreviousSchedule() throws Exception {
        String id = "http05-duplicate-round";
        edition(id);
        challenge(id, id + "-challenge");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        String response = mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 1))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 1))).andExpect(status().isConflict());
        mvc.perform(get("/rounds/{id}", (String) JsonPath.read(response, "$.roundId")))
                .andExpect(status().isOk()).andExpect(content().json(response));
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void schedulingRevalidatesCanonicalStateAndLeavesTheEarlierRoundUntouched() throws Exception {
        String id = "http05-stale-schedule";
        edition(id);
        challenge(id, id + "-challenge");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        String response = mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 1))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // Simulate stale data introduced by a persistence adapter, bypassing validated application entry points.
        var stored = teams.findById(TeamId.of(id + "-team")).orElseThrow();
        teams.save(stored.withMembers(List.of()));
        mvc.perform(post("/challenges/{id}/rounds", id + "-challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(roundBody(id + "-small", 2)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.details", not(empty())));
        mvc.perform(get("/rounds/{id}", (String) JsonPath.read(response, "$.roundId")))
                .andExpect(status().isOk()).andExpect(content().json(response));
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    private void edition(String id) throws Exception {
        mvc.perform(EditionRequests.edition(id)).andExpect(status().isCreated());
    }

    private void challenge(String edition, String id) throws Exception {
        mvc.perform(EditionRequests.challenge(edition, id)).andExpect(status().isCreated());
    }

    private ResultActions register(String edition, String id, String name, int weight) throws Exception {
        return mvc.perform(EditionRequests.register(edition, id, name, weight));
    }
}
