package com.roboleague.api.graphql;

import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import com.roboleague.api.graphql.view.AppealView;
import com.roboleague.api.graphql.view.AttemptView;
import com.roboleague.api.graphql.view.RegistrationView;
import com.roboleague.api.graphql.view.SlotView;
import com.roboleague.api.graphql.view.StandingsEntryView;
import com.roboleague.api.graphql.view.TeamView;
import com.roboleague.port.in.InconsistentStateException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GraphQlTeamBatchingTest extends ApiTest {

    // One select ... where id in (...) for every team requested at the same field/level.
    private static final long ONE_STATEMENT_FOR_ALL_TEAMS = 1;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TeamQueries teams;

    @Test
    void resolvingTheTeamOfEveryEntryCostsOneStatementNotOnePerEntry() throws Exception {
        var scenario = scoredScenario("batch-entries", 1);
        // Keep all three registrations present in BOTH measurements: QueryStandings also reads them.
        // The first calculated snapshot has one row; a recalculation adds the two newly registered teams.
        scenario.register(2);
        scenario.register(3);
        long oneBase = statements(standings(scenario, "teamName"), "standings.latest.entries", 1);
        long oneTeams = statements(standings(scenario, "teamName team { id institution }"), "standings.latest.entries", 1);
        scenario.calculateStandings();
        long threeBase = statements(standings(scenario, "teamName"), "standings.latest.entries", 3);
        long threeTeams = statements(standings(scenario, "teamName team { id institution }"), "standings.latest.entries", 3);
        System.out.printf("TEAM_SQL N=1 base=%d withTeams=%d extra=%d; N=3 base=%d withTeams=%d extra=%d%n",
                oneBase, oneTeams, oneTeams - oneBase, threeBase, threeTeams, threeTeams - threeBase);
        assertThat(oneTeams - oneBase).isEqualTo(ONE_STATEMENT_FOR_ALL_TEAMS);
        assertThat(threeTeams - threeBase).isEqualTo(ONE_STATEMENT_FOR_ALL_TEAMS);
        assertThat(threeTeams).isEqualTo(oneTeams);
    }

    @Test
    void registrationsLoadTheirTeamsInOneAdditionalStatementForOneOrThreeRows() throws Exception {
        for (int count : List.of(1, 3)) {
            var scenario = new TournamentScenario(mvc, "batch-registrations-" + count, count, false);
            Function<String, String> document = fields -> "{ edition(id: \"" + scenario.edition
                    + "\") { registrations { " + fields + " } } }";
            long base = statements(document.apply("categoryId"), "edition.registrations", count);
            long withTeams = statements(document.apply("categoryId team { id institution }"), "edition.registrations", count);
            assertThat(withTeams - base).isEqualTo(ONE_STATEMENT_FOR_ALL_TEAMS);
            long withCategory = statements(document.apply("categoryId category { id name }"), "edition.registrations", count);
            assertThat(withCategory).as("Registration.category comes from the loaded edition").isEqualTo(base);
        }
    }

    @Test
    void slotsLoadTheirTeamsInOneAdditionalStatementForOneOrThreeRows() throws Exception {
        for (int count : List.of(1, 3)) {
            var scenario = new TournamentScenario(mvc, "batch-slots-" + count, count, false);
            scenario.round(1);
            Function<String, String> document = fields -> "{ challenge(id: \"" + scenario.challenge
                    + "\") { rounds { slots { " + fields + " } } } }";
            assertBatchCost(document, "id", "challenge.rounds[0].slots", count);
        }
    }

    @Test
    void attemptsLoadTheirTeamsInOneAdditionalStatementForOneOrThreeRows() throws Exception {
        for (int count : List.of(1, 3)) {
            var scenario = new TournamentScenario(mvc, "batch-attempts-" + count, 1, false);
            for (int round = 1; round <= count; round++) scenario.measure(scenario.round(round).getFirst(), round);
            Function<String, String> document = fields -> "{ team(id: \"" + scenario.team(1)
                    + "\") { results(challengeId: \"" + scenario.challenge + "\") { attempts { " + fields + " } } } }";
            assertBatchCost(document, "id", "team.results[*].attempts[*]", count);
        }
    }

    @Test
    void appealsLoadTheirTeamsInOneAdditionalStatementForOneOrThreeRows() throws Exception {
        for (int count : List.of(1, 3)) {
            var scenario = new TournamentScenario(mvc, "batch-appeals-" + count, count, false);
            for (var turn : scenario.round(1)) {
                scenario.measure(turn, 2);
                scenario.appeal(turn);
            }
            Function<String, String> document = fields -> "{ challenge(id: \"" + scenario.challenge
                    + "\") { appeals(categoryId: \"" + scenario.category + "\") { " + fields + " } } }";
            assertBatchCost(document, "id", "challenge.appeals", count);
        }
    }

    @Test
    void aStandingsEntryKeepsItsHistoricalNameButResolvesTheCurrentCanonicalTeam() throws Exception {
        var scenario = scoredScenario("batch-canonical", 1);
        mvc.perform(put("/editions/{id}/registrations/{team}", scenario.edition, scenario.team(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EditionRequests.enrollment(scenario.category,
                                EditionRequests.team(scenario.team(1), "Renamed team", 1500))))
                .andExpect(status().isOk());
        graphQl.document(standings(scenario, "teamName team { id name institution }"))
                .execute()
                .path("standings.latest.entries[0].teamName").entity(String.class).isEqualTo("Team 1")
                .path("standings.latest.entries[0].team.name").entity(String.class).isEqualTo("Renamed team")
                .path("standings.latest.entries[0].team.id").entity(String.class).isEqualTo(scenario.team(1));
    }

    @Test
    void everyBatchAcceptsDuplicateViewsAndMapsDifferentParentsToTheCanonicalTeam() throws Exception {
        var scenario = new TournamentScenario(mvc, "batch-duplicates", 2, false);
        var first = Parents.of(scenario.team(1), "a");
        var second = Parents.of(scenario.team(2), "b");
        assertTeams(teams.registrationTeams(List.of(first.registration(), first.registration(), second.registration())),
                first.registration(), second.registration(), scenario);
        assertTeams(teams.slotTeams(List.of(first.slot(), first.slot(), second.slot())), first.slot(), second.slot(), scenario);
        assertTeams(teams.attemptTeams(List.of(first.attempt(), first.attempt(), second.attempt())),
                first.attempt(), second.attempt(), scenario);
        assertTeams(teams.appealTeams(List.of(first.appeal(), first.appeal(), second.appeal())),
                first.appeal(), second.appeal(), scenario);
        assertTeams(teams.standingsTeams(List.of(first.entry(), first.entry(), second.entry())),
                first.entry(), second.entry(), scenario);
    }

    @Test
    void everyBatchRejectsMissingStoredTeamsAsInconsistentStateInsteadOfNotFound() {
        var missing = Parents.of("team-gql-tour-batch-missing", "missing");
        List<ThrowingCallable> batches = List.of(
                () -> teams.registrationTeams(List.of(missing.registration())),
                () -> teams.slotTeams(List.of(missing.slot())),
                () -> teams.attemptTeams(List.of(missing.attempt())),
                () -> teams.appealTeams(List.of(missing.appeal())),
                () -> teams.standingsTeams(List.of(missing.entry())));
        for (var batch : batches) {
            assertThatThrownBy(batch).isInstanceOf(InconsistentStateException.class)
                    .hasMessageContaining("team-gql-tour-batch-missing");
        }
    }

    @Test
    void emptyBatchesDoNotExecuteAnyStatement() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            assertThat(teams.registrationTeams(List.of())).isEmpty();
            assertThat(teams.slotTeams(List.of())).isEmpty();
            assertThat(teams.attemptTeams(List.of())).isEmpty();
            assertThat(teams.appealTeams(List.of())).isEmpty();
            assertThat(teams.standingsTeams(List.of())).isEmpty();
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private void assertBatchCost(Function<String, String> document, String fields, String path, int count) {
        long base = statements(document.apply(fields), path, count);
        long withTeams = statements(document.apply(fields + " team { id institution }"), path, count);
        assertThat(withTeams - base).as("team batch for %s rows at %s", count, path)
                .isEqualTo(ONE_STATEMENT_FOR_ALL_TEAMS);
    }

    private static <V> void assertTeams(Map<V, TeamView> result, V first, V second, TournamentScenario scenario) {
        assertThat(result).hasSize(2);
        assertThat(result.get(first).id()).isEqualTo(scenario.team(1));
        assertThat(result.get(first).name()).isEqualTo("Team 1");
        assertThat(result.get(second).id()).isEqualTo(scenario.team(2));
        assertThat(result.get(second).name()).isEqualTo("Team 2");
    }

    /** Only ids matter to team batches; the missing-team case deliberately represents broken stored references. */
    private record Parents(RegistrationView registration, SlotView slot, AttemptView attempt, AppealView appeal,
                           StandingsEntryView entry) {
        static Parents of(String team, String suffix) {
            String id = "parent-gql-tour-" + suffix;
            return new Parents(new RegistrationView(id, id, null, "2026-11-01", "2026-11-10", team),
                    new SlotView(id, id, team, null, List.of(), "2026-11-10T10:00:00", "2026-11-10T10:05:00", 0,
                            "SCHEDULED", null),
                    new AttemptView(id + "-1", 1, id, id, team, id, 1, "EVALUATED", List.of(), 20.0, Optional.empty()),
                    new AppealView(id, id + "-1", team, "Reason", "Video", "PENDING", null, "2026-11-10T11:00:00", null, null),
                    new StandingsEntryView(1, "Score", team, "Historical name", 20, "All rounds", "Best attempt", List.of(), List.of()));
        }
    }

    private TournamentScenario scoredScenario(String suffix, int count) throws Exception {
        var scenario = new TournamentScenario(mvc, suffix, count, false);
        var turns = scenario.round(1);
        for (int i = 0; i < turns.size(); i++) scenario.measure(turns.get(i), i + 1);
        scenario.calculateStandings();
        return scenario;
    }

    private String standings(TournamentScenario scenario, String fields) {
        return "{ standings(challengeId: \"" + scenario.challenge + "\", categoryId: \"" + scenario.category
                + "\") { latest { entries { " + fields + " } } } }";
    }

    private long statements(String document, String path, int rows) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            graphQl.document(document).execute().path(path).entityList(Object.class).hasSize(rows);
            statistics.clear();
            var response = graphQl.document(document).execute();
            long count = statistics.getPrepareStatementCount();
            response.path(path).entityList(Object.class).hasSize(rows);
            return count;
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}
