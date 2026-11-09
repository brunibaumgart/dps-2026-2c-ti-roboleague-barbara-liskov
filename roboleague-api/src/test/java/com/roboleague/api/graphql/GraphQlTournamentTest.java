package com.roboleague.api.graphql;

import com.jayway.jsonpath.JsonPath;
import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GraphQlTournamentTest extends ApiTest {

    @Test
    void readsTheTournamentCategoriesChallengesAndCanonicalRegisteredTeams() throws Exception {
        var scenario = new TournamentScenario(mvc, "read", 2, false);
        graphQl.document("""
                        query($id: ID!) {
                          editions { id }
                          edition(id: $id) {
                            id name editionNumber startDate endDate
                            tournament { id name description season { id year name } }
                            categories { id name minAge maxAge }
                            challenges { id name currentRulebook { version requiredSources } }
                            registrations { editionId categoryId category { id name } referenceDate registeredAt
                              team { id name institution members { id fullName birthDate role }
                                robot { id name weightGrams sensors }
                                documentation { verified documents { kind reference } } } }
                          }
                        }
                        """)
                .variable("id", scenario.edition)
                .execute()
                .path("editions[*].id").entityList(String.class).contains(scenario.edition)
                .path("edition.id").entity(String.class).isEqualTo(scenario.edition)
                .path("edition.editionNumber").entity(Integer.class).isEqualTo(1)
                .path("edition.tournament.name").entity(String.class).isEqualTo("RoboLeague")
                .path("edition.tournament.season.year").entity(Integer.class).isEqualTo(2026)
                .path("edition.startDate").entity(String.class).isEqualTo("2026-11-10")
                .path("edition.categories[*].id").entityList(String.class)
                .containsExactly(scenario.category, scenario.edition + "-large")
                .path("edition.challenges[*].id").entityList(String.class).containsExactly(scenario.challenge)
                .path("edition.challenges[0].currentRulebook.version").entity(Integer.class).isEqualTo(1)
                .path("edition.registrations[*].team.name").entityList(String.class).containsExactly("Team 1", "Team 2")
                .path("edition.registrations[0].team.institution").entity(String.class).isEqualTo("University")
                .path("edition.registrations[0].team.documentation.verified").entity(Boolean.class).isEqualTo(true)
                .path("edition.registrations[0].category.id").entity(String.class).isEqualTo(scenario.category)
                .path("edition.registrations[0].category.name").entity(String.class).isEqualTo("Small")
                .path("edition.registrations[0].referenceDate").entity(String.class).isEqualTo("2026-11-10");
    }

    @Test
    void filtersRegistrationsByCategoryAndKeepsValidEmptyLists() throws Exception {
        var scenario = new TournamentScenario(mvc, "filter", 1, false);
        String large = scenario.edition + "-large";
        mvc.perform(post("/editions/{id}/registrations", scenario.edition).contentType(MediaType.APPLICATION_JSON)
                        .content(EditionRequests.enrollment(large,
                                EditionRequests.team("team-gql-tour-filter-large", "Large team", 3000))))
                .andExpect(status().isCreated());
        graphQl.document("""
                        { edition(id: "%s") {
                            small: registrations(categoryId: "%s") { category { id } team { name } }
                            large: registrations(categoryId: "%s") { category { id } team { name } }
                            all: registrations { team { id } }
                        } }
                        """.formatted(scenario.edition, scenario.category, large))
                .execute()
                .path("edition.small[*].team.name").entityList(String.class).containsExactly("Team 1")
                .path("edition.large[*].team.name").entityList(String.class).containsExactly("Large team")
                .path("edition.large[0].category.id").entity(String.class).isEqualTo(large)
                .path("edition.all").entityList(Object.class).hasSize(2);

        String empty = "ed-gql-tour-empty";
        mvc.perform(EditionRequests.edition(empty)).andExpect(status().isCreated());
        graphQl.document("{ edition(id: \"" + empty + "\") { challenges { id } registrations { team { id } } } }")
                .execute()
                .path("edition.challenges").entityList(Object.class).hasSize(0)
                .path("edition.registrations").entityList(Object.class).hasSize(0);
    }

    @Test
    void rejectsAForeignRegistrationCategoryAtTheFieldWithoutInventingData() throws Exception {
        var scenario = new TournamentScenario(mvc, "foreign", 0, false);
        graphQl.document("""
                        { edition(id: "%s") { registrations(categoryId: "cat-gql-tour-foreign") { team { id } } } }
                        """.formatted(scenario.edition))
                .execute().errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getMessage().equals("Category cat-gql-tour-foreign is not offered in edition " + scenario.edition)
                        && error.getParsedPath().equals(List.of("edition", "registrations")))
                .verify().path("edition").valueIsNull();
    }

    @Test
    void missingEditionAndTeamAreNullableWithNotFoundErrorsWhileOtherFieldsRemain() {
        graphQl.document("""
                        { editions { id }
                          edition(id: "ed-gql-tour-missing") { id }
                          team(id: "team-gql-tour-missing") { id } }
                        """)
                .execute().errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Edition not found: ed-gql-tour-missing")
                        && error.getParsedPath().equals(List.of("edition")))
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Team not found: team-gql-tour-missing")
                        && error.getParsedPath().equals(List.of("team")))
                .verify()
                .path("edition").valueIsNull().path("team").valueIsNull().path("editions").hasValue();
    }

    @Test
    void teamResultsAreOrderedAndDistinguishScoredPendingAndUnplayedTurns() throws Exception {
        var scenario = new TournamentScenario(mvc, "results", 1, true);
        scenario.round(3);
        var scored = scenario.round(1).getFirst();
        var pending = scenario.round(2).getFirst();
        scenario.measure(scored, 2);
        scenario.judge(scored);
        scenario.measure(pending, 3);

        // TeamTurn embeds round/slot/attempt summaries. Attempt.breakdown belongs to the evaluation area.
        graphQl.document("""
                        { team(id: "%s") {
                            name results(challengeId: "%s") {
                              round { number } slot { team { name } }
                              attempts { id number score status received team { id } }
                            }
                            filtered: results(challengeId: "%s", categoryId: "%s") { round { number } }
                            other: results(challengeId: "%s", categoryId: "%s-large") { round { number } }
                        } }
                        """.formatted(scenario.team(1), scenario.challenge, scenario.challenge, scenario.category,
                        scenario.challenge, scenario.edition))
                .execute()
                .path("team.name").entity(String.class).isEqualTo("Team 1")
                .path("team.results[*].round.number").entityList(Integer.class).containsExactly(1, 2, 3)
                .path("team.results[0].attempts[0].id").entity(String.class).isEqualTo(scored.id() + "-1")
                .path("team.results[0].attempts[0].score").entity(Double.class).isEqualTo(40.0)
                .path("team.results[0].attempts[0].team.id").entity(String.class).isEqualTo(scenario.team(1))
                .path("team.results[1].attempts[0].score").valueIsNull()
                .path("team.results[1].attempts[0].status").entity(String.class).isEqualTo("AWAITING_SOURCES")
                .path("team.results[1].attempts[0].received").entityList(String.class).containsExactly("AUTOMATIC_MEASUREMENTS")
                .path("team.results[2].attempts").entityList(Object.class).hasSize(0)
                .path("team.filtered[*].round.number").entityList(Integer.class).containsExactly(1, 2, 3)
                .path("team.other").entityList(Object.class).hasSize(0);
    }

    @Test
    void resultsValidateTheirChallengeAndCategoryAndReturnEmptyForATeamWithoutTurns() throws Exception {
        var scenario = new TournamentScenario(mvc, "results-errors", 1, false);
        graphQl.document("{ team(id: \"" + scenario.team(1) + "\") { results(challengeId: \"" + scenario.challenge
                        + "\") { round { id } } } }")
                .execute().path("team.results").entityList(Object.class).hasSize(0);
        graphQl.document("{ team(id: \"" + scenario.team(1)
                        + "\") { results(challengeId: \"ch-gql-tour-missing\") { round { id } } } }")
                .execute().errors().expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getParsedPath().equals(List.of("team", "results")))
                .verify().path("team").valueIsNull();
        graphQl.document("{ team(id: \"" + scenario.team(1) + "\") { results(challengeId: \"" + scenario.challenge
                        + "\", categoryId: \"cat-gql-tour-unrelated\") { round { id } } } }")
                .execute().errors().expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getParsedPath().equals(List.of("team", "results")))
                .verify().path("team").valueIsNull();
    }
}

/** Shared only by Nico's two test classes; all setup goes through the real REST adapter and use cases. */
final class TournamentScenario {
    final String edition;
    final String category;
    final String challenge;
    private final String suffix;
    private final MockMvc mvc;

    TournamentScenario(MockMvc mvc, String suffix, int teams, boolean panel) throws Exception {
        this.mvc = mvc;
        this.suffix = suffix;
        edition = "ed-gql-tour-" + suffix;
        category = edition + "-small";
        challenge = "ch-gql-tour-" + suffix;
        mvc.perform(EditionRequests.edition(edition)).andExpect(status().isCreated());
        String rules = """
                {"type": "objectives", "name": "Objetivos", "numbers": {"pointsPerObjective": 10}}
                """;
        if (panel) rules += ", {\"type\": \"judges\", \"name\": \"Panel\", \"numbers\": {\"weightMultiplier\": 5}}";
        mvc.perform(post("/editions/{id}/challenges", edition).contentType(MediaType.APPLICATION_JSON).content("""
                {"id": "%s", "name": "Challenge", "rulebook": {
                  "metrics": [], "scoring": {"rules": [%s], "bonuses": [], "deductions": [],
                    "bonusLimit": {"type": "unlimited"}},
                  "ranking": {"roundSelection": {"type": "all-rounds"}, "criteria": ["higher-total"]}}}
                """.formatted(challenge, rules))).andExpect(status().isCreated());
        for (int i = 1; i <= teams; i++) {
            register(i);
        }
    }

    String team(int number) { return "team-gql-tour-" + suffix + "-" + number; }

    void register(int number) throws Exception {
        mvc.perform(EditionRequests.register(edition, team(number), "Team " + number, 1500)).andExpect(status().isCreated());
    }

    List<Turn> round(int number) throws Exception {
        String body = EditionRequests.roundBody(category, number)
                .replace("T10:00:00", "T%02d:00:00".formatted(9 + number));
        String json = mvc.perform(post("/challenges/{id}/rounds", challenge)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> slots = JsonPath.read(json, "$.slots");
        return slots.stream().map(slot -> new Turn((String) slot.get("slotId"), (String) slot.get("teamId"))).toList();
    }

    void measure(Turn turn, int objectives) throws Exception {
        mvc.perform(post("/slots/{id}/measurements", turn.id()).contentType(MediaType.APPLICATION_JSON).content("""
                {"challengeId": "%s", "judgeId": "%s-j1", "timeSeconds": 60, "objectives": %d,
                 "penalties": 0, "consumption": 0, "measurements": {}}
                """.formatted(challenge, category, objectives))).andExpect(status().isOk());
    }

    void judge(Turn turn) throws Exception {
        mvc.perform(post("/slots/{id}/judge-scores", turn.id()).contentType(MediaType.APPLICATION_JSON).content("""
                {"challengeId": "%s", "judgeId": "%s-j1", "scores": {"%s-j1": 4}}
                """.formatted(challenge, category, category))).andExpect(status().isOk());
    }

    void calculateStandings() throws Exception {
        mvc.perform(post("/challenges/{id}/standings/versions", challenge).param("categoryId", category))
                .andExpect(status().isCreated());
    }

    String appeal(Turn turn) throws Exception {
        String json = mvc.perform(post("/attempts/{id}/appeals", turn.id() + "-1")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"teamId": "%s", "reason": "Objetivo no contado", "evidence": "Video"}
                                """.formatted(turn.teamId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    record Turn(String id, String teamId) { }
}
