package com.roboleague.api.graphql;

import com.roboleague.api.ApiTest;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.RegisterTeamUseCase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidence of the evaluation-area batches (D5, acceptance criterion 3): asking for a relation costs the same extra
 * statements whether its level has one row or three. Resolved per row it would grow with the rows.
 * <p>
 * Two independent scenarios, built once and shared by every test: with N = 1 and N = 3, an edition with one
 * category, N teams and one challenge with N rounds; every round has one slot per team, every slot is scored once,
 * the standings are calculated (N entries, each considering N round results) and every team appeals its attempt of
 * round 1. The measured value is (statements with the relation) - (statements without it).
 */
class GraphQlEvaluationBatchingTest extends ApiTest {

    /** {@code Round.challenge}: the {@code findByIds} of the challenges. */
    private static final long ONE_READ_FOR_THE_CHALLENGES = 1;
    /** {@code Round.category}: the {@code findByIds} of the editions; the category is filtered from them. */
    private static final long ONE_READ_FOR_THE_EDITIONS = 1;
    /** {@code Slot.turn}: rounds, challenges, the category's rounds and the category's attempts. */
    private static final long FOUR_READS_FOR_THE_TURNS_OF_ONE_CATEGORY = 4;
    /** {@code Attempt.round} and {@code RoundResult.round}: the {@code findByIds} of the rounds. */
    private static final long ONE_READ_FOR_THE_ROUNDS = 1;
    /** {@code Attempt.breakdown}, {@code RoundResult.attempt}, {@code Appeal.attempt}: attempts and their challenges. */
    private static final long TWO_READS_FOR_THE_EXPLAINED_ATTEMPTS = 2;
    /** {@code attempt { breakdown }} under a round result: the attempt view already carries it. */
    private static final long NO_READ_FOR_A_LOADED_BREAKDOWN = 0;

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Autowired
    private EditionRepository editions;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private RoundRepository rounds;
    @Autowired
    private RegisterTeamUseCase registerTeam;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void scenarios() throws Exception {
        scenario(1);
        scenario(3);
    }

    @Test
    void resolvingTheChallengeOfEveryRoundCostsTheSameStatementsForOneOrThreeRounds() {
        long one = extraForRounds(1, "challenge { name }");
        long three = extraForRounds(3, "challenge { name }");

        assertThat(one).isEqualTo(three).isEqualTo(ONE_READ_FOR_THE_CHALLENGES);
    }

    @Test
    void resolvingTheCategoryOfEveryRoundCostsTheSameStatementsForOneOrThreeRounds() {
        long one = extraForRounds(1, "category { name }");
        long three = extraForRounds(3, "category { name }");

        assertThat(one).isEqualTo(three).isEqualTo(ONE_READ_FOR_THE_EDITIONS);
    }

    @Test
    void resolvingTheTurnOfEverySlotOfARoundCostsTheSameStatementsForOneOrThreeSlots() {
        long one = extraForSlots(1);
        long three = extraForSlots(3);

        assertThat(one).isEqualTo(three).isEqualTo(FOUR_READS_FOR_THE_TURNS_OF_ONE_CATEGORY);
    }

    @Test
    void resolvingTheRoundOfEveryAttemptOfATurnCostsTheSameStatementsForOneOrThreeAttempts() {
        long one = extraForTurnAttempts(1, "round { number }");
        long three = extraForTurnAttempts(3, "round { number }");

        assertThat(one).isEqualTo(three).isEqualTo(ONE_READ_FOR_THE_ROUNDS);
    }

    @Test
    void resolvingTheBreakdownOfEveryAttemptOfATurnCostsTheSameStatementsForOneOrThreeAttempts() {
        long one = extraForTurnAttempts(1, "breakdown { total }");
        long three = extraForTurnAttempts(3, "breakdown { total }");

        assertThat(one).isEqualTo(three).isEqualTo(TWO_READS_FOR_THE_EXPLAINED_ATTEMPTS);
    }

    @Test
    void resolvingTheRoundOfEveryRoundResultCostsTheSameStatementsForOneOrThreeRankedTeams() {
        long one = extraForRoundResults(1, "roundId", "roundId round { number }");
        long three = extraForRoundResults(3, "roundId", "roundId round { number }");

        assertThat(one).isEqualTo(three).isEqualTo(ONE_READ_FOR_THE_ROUNDS);
    }

    @Test
    void resolvingTheAttemptOfEveryRoundResultCostsTheSameStatementsForOneOrThreeRankedTeams() {
        long one = extraForRoundResults(1, "roundId", "roundId attempt { id }");
        long three = extraForRoundResults(3, "roundId", "roundId attempt { id }");

        assertThat(one).isEqualTo(three).isEqualTo(TWO_READS_FOR_THE_EXPLAINED_ATTEMPTS);
    }

    @Test
    void theBreakdownOfTheAttemptOfARoundResultCostsNothingOverTheAttempt() {
        long one = extraForRoundResults(1, "roundId attempt { id }", "roundId attempt { id breakdown { total } }");
        long three = extraForRoundResults(3, "roundId attempt { id }", "roundId attempt { id breakdown { total } }");

        assertThat(one).isEqualTo(three).isEqualTo(NO_READ_FOR_A_LOADED_BREAKDOWN);
    }

    @Test
    void resolvingTheAttemptOfEveryAppealCostsTheSameStatementsForOneOrThreeAppeals() {
        long one = extraForAppeals(1);
        long three = extraForAppeals(3);

        assertThat(one).isEqualTo(three).isEqualTo(TWO_READS_FOR_THE_EXPLAINED_ATTEMPTS);
    }

    /** Rows: the N rounds of the challenge. */
    private long extraForRounds(int n, String relation) {
        String query = "{ challenge(id: \"" + challenge(n) + "\") { rounds { id %s } } }";
        return extraStatements(query.formatted(""), query.formatted(relation), "challenge.rounds[*].id", n);
    }

    /** Rows: the N slots of round 1. */
    private long extraForSlots(int n) {
        String query = "{ round(id: \"" + round(n, 1) + "\") { slots { id %s } } }";
        return extraStatements(query.formatted(""), query.formatted("turn { acceptsResult attempts { id } }"),
                "round.slots[*].id", n);
    }

    /** Rows: the N attempts of the slots of round 1, one per turn; they arrive without their breakdown. */
    private long extraForTurnAttempts(int n, String relation) {
        String query = "{ round(id: \"" + round(n, 1) + "\") { slots { turn { attempts { id %s } } } } }";
        return extraStatements(query.formatted(""), query.formatted(relation), "round.slots[*].turn.attempts[*].id", n);
    }

    /** Rows: the N x N round results the N entries consider (one per team and round). */
    private long extraForRoundResults(int n, String without, String with) {
        String query = "{ standings(challengeId: \"" + challenge(n) + "\", categoryId: \"" + category(n)
                + "\") { latest { entries { considered { %s } } } } }";
        return extraStatements(query.formatted(without), query.formatted(with),
                "standings.latest.entries[*].considered[*].roundId", n * n);
    }

    /** Rows: the N appeals of the category, one per team. */
    private long extraForAppeals(int n) {
        String query = "{ challenge(id: \"" + challenge(n) + "\") { appeals(categoryId: \"" + category(n)
                + "\") { id %s } } }";
        return extraStatements(query.formatted(""), query.formatted("attempt { id }"), "challenge.appeals[*].id", n);
    }

    /**
     * Statements of {@code with} minus statements of {@code without}, after a warm-up of both; each run checks the
     * level really has {@code rows} rows.
     */
    private long extraStatements(String without, String with, String rowsPath, int rows) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statementsOf(statistics, without, rowsPath, rows); // warm-up: first use of the statements and mappings
            statementsOf(statistics, with, rowsPath, rows);
            return statementsOf(statistics, with, rowsPath, rows) - statementsOf(statistics, without, rowsPath, rows);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    /** Clears right before the query and reads right after it: the suite shares the context and runs on one thread. */
    private long statementsOf(Statistics statistics, String document, String rowsPath, int rows) {
        statistics.clear();
        var response = graphQl.document(document).execute();
        long statements = statistics.getPrepareStatementCount();
        response.path(rowsPath).entityList(Object.class).hasSize(rows);
        return statements;
    }

    private void scenario(int n) throws Exception {
        if (editions.findById(EditionId.of(edition(n))).isPresent()) {
            return;
        }
        editions.save(Edition.of(EditionId.of(edition(n)),
                Tournament.of("tor-gql-eval-batch-" + n, "Torneo", new Season("s-gql-eval-batch-" + n, 2026, "2026")),
                1, "Edicion lotes " + n, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22),
                List.of(Category.of(CategoryId.of(category(n)), "Junior", 2, 4, 13, null, 2500))));
        for (int team = 1; team <= n; team++) {
            registerTeam.execute(EditionId.of(edition(n)), CategoryId.of(category(n)), team(team(n, team)));
        }
        challenges.save(Challenge.draft(ChallengeId.of(challenge(n)), EditionId.of(edition(n)), "Laberinto " + n)
                .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))),
                                List.of()),
                        new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        for (int number = 1; number <= n; number++) {
            schedule(n, number);
        }
        for (int number = 1; number <= n; number++) {
            for (int team = 1; team <= n; team++) {
                score(n, slot(n, number, team), team);
            }
        }
        mvc.perform(post("/challenges/{id}/standings/versions", challenge(n)).param("categoryId", category(n)))
                .andExpect(status().isCreated());
        for (int team = 1; team <= n; team++) {
            mvc.perform(post("/attempts/{attemptId}/appeals", slot(n, 1, team) + "-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"teamId": "%s", "reason": "Objetivo no contado", "evidence": "Video"}
                                    """.formatted(team(n, team))))
                    .andExpect(status().isCreated());
        }
    }

    /** Round {@code number} of scenario {@code n}: one slot per team, one after the other on the same track. */
    private void schedule(int n, int number) {
        Round round = Round.of(RoundInfo.of(RoundId.of(round(n, number)), "Ronda " + number,
                RoundScope.of(ChallengeId.of(challenge(n)), EditionId.of(edition(n)), CategoryId.of(category(n)),
                        number)));
        for (int team = 1; team <= n; team++) {
            LocalDateTime start = START.plusHours(number).plusMinutes(team * 10L);
            round = round.addSlot(Slot.of(SlotIdentity.of(SlotId.of(slot(n, number, team)),
                            RoundId.of(round(n, number)), TeamId.of(team(n, team))),
                    SlotAssignment.of(Track.active(TrackId.of("trk-gql-eval-batch"), "Pista", "Madera"),
                            List.of(Judge.of(JudgeId.of("j-gql-eval-batch"), "Juez", "General"))),
                    new TimeWindow(start, start.plusMinutes(5))));
        }
        rounds.save(round);
    }

    private void score(int n, String slotId, int objectives) throws Exception {
        mvc.perform(post("/slots/{slotId}/measurements", slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "j-gql-eval-batch", "timeSeconds": 60,
                                 "objectives": %d, "penalties": 0, "consumption": 0, "measurements": {}}
                                """.formatted(challenge(n), objectives)))
                .andExpect(status().isOk());
    }

    private static String edition(int n) {
        return "ed-gql-eval-batch-" + n;
    }

    private static String category(int n) {
        return "cat-gql-eval-batch-" + n;
    }

    private static String challenge(int n) {
        return "ch-gql-eval-batch-" + n;
    }

    private static String round(int n, int number) {
        return "round-gql-eval-batch-" + n + "-r" + number;
    }

    private static String slot(int n, int number, int team) {
        return "slot-gql-eval-batch-" + n + "-r" + number + "-t" + team;
    }

    private static String team(int n, int team) {
        return "team-gql-eval-batch-" + n + "-t" + team;
    }

    private static Team team(String id) {
        return Team.of(TeamId.of(id), "Equipo " + id, "ITBA",
                new Robot(RobotId.of("r-" + id), "Bot " + id,
                        RobotSpecification.of(2000, 200, 200, 200, 2, Set.of("LIDAR"))),
                List.of(TeamMember.of(ParticipantId.of("m1-" + id), "Ana " + id, LocalDate.of(2004, 1, 1), "LEADER",
                                DATE),
                        TeamMember.of(ParticipantId.of("m2-" + id), "Beto " + id, LocalDate.of(2004, 2, 2), "DEV",
                                DATE)),
                new Documentation().withDocument("DOC", "doc.pdf").verify(ActorId.of("inspector"), TIME));
    }
}
