package com.roboleague.api.registration;

import com.jayway.jsonpath.JsonPath;
import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static com.roboleague.api.EditionRequests.enrollment;
import static com.roboleague.api.EditionRequests.roundBody;
import static com.roboleague.api.EditionRequests.team;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Registration through HTTP: enrolling, updating and querying teams of an edition, and the full flow from
 * registration to a scored attempt.
 */
class RegistrationControllerTest extends ApiTest {
    @Test
    void registersUpdatesSchedulesAndCapturesUsingOnlyTheHttpContracts() throws Exception {
        String id = "http05-flow";
        edition(id);
        challenge(id, id + "-challenge");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.editionId").value(id))
                .andExpect(jsonPath("$.categoryId").value(id + "-small"))
                .andExpect(jsonPath("$.registeredAt").value("2026-10-08T12:00:00"))
                .andExpect(jsonPath("$.referenceDate").value("2026-11-10"))
                .andExpect(jsonPath("$.team.documentation.verifiedAt").value("2026-10-08T12:00:00"))
                .andExpect(jsonPath("$.team.documentation.verifiedBy").value("inspector"));
        mvc.perform(put("/editions/{id}/registrations/{team}", id, id + "-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollment(id + "-large", team(id + "-team", "Updated", 2500))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(id + "-large"))
                .andExpect(jsonPath("$.team.name").value("Updated"))
                .andExpect(jsonPath("$.registeredAt").value("2026-10-08T12:00:00"));
        mvc.perform(get("/editions/{id}/registrations", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team.name").value("Updated"));
        mvc.perform(get("/editions/{id}/registrations/{team}", id, id + "-team"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team.robot.weightGrams").value(2500));

        String response = mvc.perform(post("/challenges/{id}/rounds", id + "-challenge")
                        .contentType(MediaType.APPLICATION_JSON).content(roundBody(id + "-large", 1)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.editionId").value(id))
                .andExpect(jsonPath("$.challengeId").value(id + "-challenge"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.slots", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].judges", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].startTime").value("2026-11-10T10:00:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("2026-11-10T10:05:00"))
                .andExpect(jsonPath("$.slots[0].intervalSeconds").value(60))
                .andReturn().getResponse().getContentAsString();
        String roundId = JsonPath.read(response, "$.roundId");
        String slot = JsonPath.read(response, "$.slots[0].slotId");
        mvc.perform(get("/rounds/{id}", roundId)).andExpect(status().isOk())
                .andExpect(content().json(response));
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge").param("categoryId", id + "-large"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].roundId").value(roundId));

        mvc.perform(put("/attempts/{id}/measurements", slot + "-1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s-challenge", "judgeId": "%s-large-j1", "timeSeconds": 50,
                                 "objectives": 3, "penalties": 0, "consumption": 0, "measurements": {}}
                                """.formatted(id, id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_SOURCES"));
        mvc.perform(put("/attempts/{id}/judge-scores", slot + "-1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s-challenge", "judgeId": "%s-large-j1",
                                 "scores": {"%s-large-j1": 8}, "measurements": {}}
                                """.formatted(id, id, id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(70.0));
        mvc.perform(get("/attempts/{id}/breakdown", slot + "-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.score").value(70.0));
    }

    @Test
    void editionDetailsExposeRestrictionsAndChallengesStayWithinTheirEdition() throws Exception {
        String id = "http05-query";
        edition(id);
        edition(id + "-other");
        challenge(id, id + "-b");
        challenge(id, id + "-a");
        challenge(id + "-other", id + "-foreign");
        mvc.perform(get("/editions")).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/editions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", contains(id + "-small", id + "-large")))
                .andExpect(jsonPath("$.categoryDetails[0].minMembers").value(1))
                .andExpect(jsonPath("$.categoryDetails[0].maxAge").value(20))
                .andExpect(jsonPath("$.categoryDetails[0].maxLengthMm").value(300))
                .andExpect(jsonPath("$.categoryDetails[1].maxLengthMm").value(1000));
        mvc.perform(get("/editions/{id}/challenges", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(id + "-a", id + "-b")))
                .andExpect(jsonPath("$[0].currentRulebook.version").value(1));
    }

    @Test
    void emptyListsAreOkUnknownReferencesAreNotFoundAndForeignCategoryFiltersAreBadRequests() throws Exception {
        String id = "http05-empty";
        edition(id);
        mvc.perform(get("/editions/{id}/challenges", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/editions/{id}/registrations", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        challenge(id, id + "-challenge");
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge").param("categoryId", id + "-large"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/challenges/{id}/rounds", id + "-challenge").param("categoryId", "foreign-category"))
                .andExpect(status().isBadRequest());
        for (String path : new String[]{"/editions/http05-unknown", "/editions/http05-unknown/challenges",
                "/editions/http05-unknown/registrations", "/challenges/http05-unknown/rounds", "/rounds/http05-unknown",
                "/editions/" + id + "/registrations/unknown-team"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.details", hasSize(0)));
        }
    }

    @Test
    void existingTeamReferenceDoesNotAllowDuplicateEnrollmentOrCanonicalOverwrite() throws Exception {
        String id = "http05-existing";
        edition(id);
        edition(id + "-other");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        mvc.perform(post("/editions/{id}/registrations", id + "-other").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\": \"" + id + "-other-small\", \"teamId\": \"" + id + "-team\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.team.name").value("Original"));
        register(id, id + "-team", "Replacement", 1000).andExpect(status().isConflict());
        mvc.perform(post("/editions/{id}/registrations", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\": \"" + id + "-small\", \"teamId\": \"" + id + "-team\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/editions/{id}/registrations/{team}", id, id + "-team"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team.name").value("Original"));
    }

    @Test
    void rejectedCombinedUpdatePreservesTheCanonicalTeamAndEveryEnrollment() throws Exception {
        String id = "http05-rejected-update";
        edition(id);
        edition(id + "-other");
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        mvc.perform(post("/editions/{id}/registrations", id + "-other").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\": \"" + id + "-other-small\", \"teamId\": \"" + id + "-team\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/editions/{id}/registrations/{team}", id, id + "-team").contentType(MediaType.APPLICATION_JSON)
                        .content(enrollment(id + "-large", team(id + "-team", "Rejected", 2500))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.details", not(empty())))
                .andExpect(jsonPath("$.details[0]", containsString(id + "-other")));
        for (String edition : new String[]{id, id + "-other"}) {
            mvc.perform(get("/editions/{id}/registrations/{team}", edition, id + "-team"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(edition + "-small"))
                    .andExpect(jsonPath("$.team.name").value("Original"))
                    .andExpect(jsonPath("$.team.robot.weightGrams").value(1000));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"neither", "both", "missing-member", "null-member", "missing-hardware", "null-sensor",
            "missing-documents", "future-birth", "blank-id", "foreign-category", "null-document", "timestamp", "malformed"})
    void invalidEnrollmentBodiesNeverCreateAPartialTeam(String invalid) throws Exception {
        String id = "http05-invalid-" + invalid;
        edition(id);
        String body = enrollment(id + "-small", team(id + "-team", "Original", 1000));
        body = switch (invalid) {
            case "neither" -> "{\"categoryId\": \"" + id + "-small\"}";
            case "both" -> body.replace("\"categoryId\":", "\"teamId\": \"existing\", \"categoryId\":");
            case "missing-member" -> body.replace("\"birthDate\": \"2010-01-01\",", "");
            case "null-member" -> body.replace("\"members\": [{", "\"members\": [null, {");
            case "missing-hardware" -> body.replace("\"actuatorCount\": 2,", "");
            case "null-sensor" -> body.replace("[\"LIDAR\"]", "[null]");
            case "missing-documents" -> body.replace("\"documents\": {\"consent\": \"consent.pdf\"},", "");
            case "future-birth" -> body.replace("2010-01-01", "2030-01-01");
            case "blank-id" -> body.replace("\"id\": \"" + id + "-team\"", "\"id\": \" \"");
            case "foreign-category" -> body.replace(id + "-small", "foreign");
            case "null-document" -> body.replace("\"consent.pdf\"", "null");
            case "timestamp" -> body.replace("\"verifiedBy\":", "\"verifiedAt\": \"2000-01-01T00:00:00\", \"verifiedBy\":");
            case "malformed" -> "{";
            default -> throw new AssertionError(invalid);
        };
        mvc.perform(post("/editions/{id}/registrations", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/editions/{id}/registrations", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        // A complete retry with the same team identity proves no canonical team was saved.
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
    }

    @Test
    void ineligibleEnrollmentCanBeCorrectedWithoutAStoredPartialCandidate() throws Exception {
        String id = "http05-ineligible";
        edition(id);
        register(id, id + "-team", "Rejected", 2500).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details", not(empty())));
        mvc.perform(post("/editions/{id}/registrations", id).contentType(MediaType.APPLICATION_JSON)
                        .content(enrollment(id + "-small", team(id + "-team", "Unverified", 1000).replace("\"inspector\"", "null"))))
                .andExpect(status().isUnprocessableContent());
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
    }

    @Test
    void invalidOrUnknownUpdateLeavesExistingTeamUntouched() throws Exception {
        String id = "http05-bad-update";
        edition(id);
        register(id, id + "-team", "Original", 1000).andExpect(status().isCreated());
        for (String target : new String[]{id + "-team", "unknown"}) {
            mvc.perform(put("/editions/{id}/registrations/{team}", id, target).contentType(MediaType.APPLICATION_JSON)
                            .content(enrollment(id + "-small", team("wrong-id", "Rejected", 1000))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/editions/{id}/registrations/{team}", id, "unknown").contentType(MediaType.APPLICATION_JSON)
                        .content(enrollment(id + "-small", team("unknown", "Rejected", 1000))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/editions/{id}/registrations/{team}", id, id + "-team"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team.name").value("Original"));
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
