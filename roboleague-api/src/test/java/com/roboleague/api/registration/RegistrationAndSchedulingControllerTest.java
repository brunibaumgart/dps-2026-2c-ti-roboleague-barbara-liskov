package com.roboleague.api.registration;

import com.jayway.jsonpath.JsonPath;
import com.roboleague.api.ApiTest;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationAndSchedulingControllerTest extends ApiTest {
    @Autowired
    private TeamRepository teams;

    private static final String RULEBOOK = """
            {"metrics": [], "scoring": {
               "rules": [{"type": "objectives", "name": "Objectives", "numbers": {"pointsPerObjective": 10}},
                         {"type": "judges", "name": "Panel", "numbers": {"weightMultiplier": 5}}],
               "bonuses": [], "deductions": [], "bonusLimit": {"type": "unlimited"}},
             "ranking": {"roundSelection": {"type": "all-rounds"}, "criteria": ["higher-total"]}}
            """;

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
    void emptyListsAreOkAndUnknownReferencesOrCategoryFiltersAreBadRequests() throws Exception {
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
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details", hasSize(0)));
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
                .andExpect(status().isBadRequest());
        mvc.perform(get("/editions/{id}/registrations/{team}", id, id + "-team"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team.name").value("Original"));
    }

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
        mvc.perform(post("/editions").contentType(MediaType.APPLICATION_JSON).content("""
                {"id": "%1$s", "name": "Edition", "editionNumber": 1,
                 "tournament": {"id": "http05-tournament", "name": "RoboLeague",
                   "season": {"id": "http05-season", "year": 2026, "name": "2026"}},
                 "startDate": "2026-11-10", "endDate": "2026-11-12",
                 "categories": [{"id": "%1$s-small", "name": "Small", "minMembers": 1, "maxMembers": 4,
                    "minAge": 10, "maxAge": 20, "maxWeightGrams": 2000, "maxLengthMm": 300, "maxWidthMm": 300, "maxHeightMm": 300},
                   {"id": "%1$s-large", "name": "Large", "minMembers": 1, "maxMembers": 4,
                    "minAge": 10, "maxAge": 20, "maxWeightGrams": 4000}]}
                """.formatted(id))).andExpect(status().isCreated());
    }
    private void challenge(String edition, String id) throws Exception {
        mvc.perform(post("/editions/{id}/challenges", edition).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + id + "\", \"name\": \"Challenge\", \"rulebook\": " + RULEBOOK + "}"))
                .andExpect(status().isCreated());
    }
    private ResultActions register(String edition, String id, String name, int weight) throws Exception {
        return mvc.perform(post("/editions/{id}/registrations", edition).contentType(MediaType.APPLICATION_JSON)
                .content(enrollment(edition + "-small", team(id, name, weight))));
    }
    private static String enrollment(String category, String team) {
        return "{\"categoryId\": \"" + category + "\", \"team\": " + team + "}";
    }
    private static String team(String id, String name, int weight) {
        return """
                {"id": "%s", "name": "%s", "institution": "University",
                 "members": [{"id": "member", "fullName": "Student", "birthDate": "2010-01-01", "role": "LEADER"}],
                 "robot": {"id": "robot", "name": "Bot", "weightGrams": %d,
                   "lengthMm": 100, "widthMm": 100, "heightMm": 100, "actuatorCount": 2, "sensors": ["LIDAR"]},
                 "documentation": {"documents": {"consent": "consent.pdf"}, "verifiedBy": "inspector"}}
                """.formatted(id, name, weight);
    }
    private static String roundBody(String category, int number) {
        return """
                {"categoryId": "%s", "roundNumber": %d, "roundName": "Round", "startTime": "2026-11-10T10:00:00",
                 "slotDurationSeconds": 300, "intervalSeconds": 60,
                 "tracks": [{"id": "http05-p1", "name": "Track", "surfaceType": "Wood", "isActive": true}],
                 "judges": [{"id": "http05-j1", "fullName": "Judge", "specialty": "General"}]}
                """.formatted(category, number).replace("http05-p1", category + "-p1").replace("http05-j1", category + "-j1");
    }
}
