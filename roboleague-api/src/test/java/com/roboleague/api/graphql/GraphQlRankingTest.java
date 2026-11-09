package com.roboleague.api.graphql;

import com.jayway.jsonpath.JsonPath;
import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
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
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ranking reads of the GraphQL API: a challenge with its edition, rulebooks, rounds, standings and appeals in one
 * request; the standings and an appeal by id; the appeals of an attempt; and the evidence that {@code Challenge.edition}
 * costs one statement for the whole response, not one per challenge.
 * <p>
 * Scenario (built once, shared by every test): two teams in one category, one round each, Alpha scores 2 objectives
 * (40) and Beta 3 (60), the standings are calculated once and Alpha appeals its attempt.
 */
class GraphQlRankingTest extends ApiTest {

    private static final String EDITION = "ed-gql-rank";
    private static final String CATEGORY = "cat-gql-rank";
    private static final String CHALLENGE = "ch-gql-rank";
    private static final String TEAM_A = "team-gql-rank-a";
    private static final String TEAM_B = "team-gql-rank-b";
    private static final String ATTEMPT_A = "slot-gql-rank-a-1";
    private static final String ATTEMPT_B = "slot-gql-rank-b-1";
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);

    /** Alpha's appeal; filed in the first {@link #scenario()} of the run. */
    private static String appealId;

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
    void scenario() throws Exception {
        if (editions.findById(EditionId.of(EDITION)).isPresent()) {
            return;
        }
        editions.save(Edition.of(EditionId.of(EDITION),
                Tournament.of("tor-gql-rank", "Torneo", new Season("s-gql-rank", 2026, "2026")), 1, "Edicion GraphQL",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                List.of(Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500))));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_A, "Alpha"));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_B, "Beta"));
        challenges.save(Challenge.draft(ChallengeId.of(CHALLENGE), EditionId.of(EDITION), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        schedule("slot-gql-rank-a", TEAM_A, 1);
        schedule("slot-gql-rank-b", TEAM_B, 2);
        score(ATTEMPT_A, 2);
        score(ATTEMPT_B, 3);
        mvc.perform(post("/challenges/{id}/standings/versions", CHALLENGE).param("categoryId", CATEGORY))
                .andExpect(status().isCreated());
        String filed = mvc.perform(post("/attempts/{attemptId}/appeals", ATTEMPT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId": "%s", "reason": "Objetivo no contado", "evidence": "Video"}
                                """.formatted(TEAM_A)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        appealId = JsonPath.read(filed, "$.id");
    }

    @Test
    void aChallengeAnswersItsEditionRulebooksRoundsStandingsAndAppealsInOneRequest() {
        graphQl.document("""
                        query($id: ID!, $category: ID!) {
                          challenge(id: $id) {
                            name
                            edition { id name }
                            currentRulebook { version requiredSources attemptRule selectionRule tieBreakCriteria }
                            rulebooks { version }
                            first: rulebook(version: 1) { version }
                            never: rulebook(version: 9) { version }
                            rounds(categoryId: $category) { number categoryId }
                            allRounds: rounds { id }
                            standings(categoryId: $category) {
                              latest { number status rulebookVersion entries { position teamId teamName total } }
                              pending { openAppeals outdated }
                            }
                            appeals(categoryId: $category) { id attemptId teamId status }
                          }
                        }
                        """)
                .variable("id", CHALLENGE)
                .variable("category", CATEGORY)
                .execute()
                .path("challenge.name").entity(String.class).isEqualTo("Laberinto")
                .path("challenge.edition.id").entity(String.class).isEqualTo(EDITION)
                .path("challenge.edition.name").entity(String.class).isEqualTo("Edicion GraphQL")
                .path("challenge.currentRulebook.version").entity(Integer.class).isEqualTo(1)
                .path("challenge.currentRulebook.requiredSources").entityList(String.class)
                .containsExactly("AUTOMATIC_MEASUREMENTS")
                .path("challenge.currentRulebook.selectionRule").hasValue()
                .path("challenge.rulebooks[*].version").entityList(Integer.class).containsExactly(1)
                .path("challenge.first.version").entity(Integer.class).isEqualTo(1)
                // A version the challenge never published filters to null; execute() already failed on any error.
                .path("challenge.never").valueIsNull()
                .path("challenge.rounds[*].number").entityList(Integer.class).containsExactly(1, 2)
                .path("challenge.rounds[*].categoryId").entityList(String.class).containsExactly(CATEGORY, CATEGORY)
                .path("challenge.allRounds").entityList(Object.class).hasSize(2)
                .path("challenge.standings.latest.number").entity(Integer.class).isEqualTo(1)
                .path("challenge.standings.latest.status").entity(String.class).isEqualTo("PROVISIONAL")
                .path("challenge.standings.latest.rulebookVersion").entity(Integer.class).isEqualTo(1)
                .path("challenge.standings.latest.entries[*].teamId").entityList(String.class)
                .containsExactly(TEAM_B, TEAM_A)
                .path("challenge.standings.latest.entries[*].teamName").entityList(String.class)
                .containsExactly("Beta", "Alpha")
                .path("challenge.standings.latest.entries[*].total").entityList(Double.class)
                .containsExactly(60.0, 40.0)
                .path("challenge.standings.pending.openAppeals").entity(Integer.class).isEqualTo(1)
                .path("challenge.appeals[*].id").entityList(String.class).containsExactly(appealId)
                .path("challenge.appeals[0].attemptId").entity(String.class).isEqualTo(ATTEMPT_A)
                .path("challenge.appeals[0].teamId").entity(String.class).isEqualTo(TEAM_A)
                .path("challenge.appeals[0].status").entity(String.class).isEqualTo("PENDING");
    }

    @Test
    void standingsByChallengeAndCategoryMatchTheRestTable() {
        graphQl.document("""
                        { standings(challengeId: "%s", categoryId: "%s") {
                            challengeId categoryId
                            versions { number status }
                            latest { entries { position explanation teamName selectionRule attemptRule
                                               considered { roundId attemptId total } } }
                            official { number }
                            pending { unfinishedTurns outdated }
                        } }
                        """.formatted(CHALLENGE, CATEGORY))
                .execute()
                .path("standings.challengeId").entity(String.class).isEqualTo(CHALLENGE)
                .path("standings.categoryId").entity(String.class).isEqualTo(CATEGORY)
                .path("standings.versions[*].number").entityList(Integer.class).containsExactly(1)
                .path("standings.latest.entries[*].position").entityList(Integer.class).containsExactly(1, 2)
                .path("standings.latest.entries[0].explanation").hasValue()
                .path("standings.latest.entries[0].considered[*].attemptId").entityList(String.class)
                .containsExactly(ATTEMPT_B)
                .path("standings.latest.entries[1].considered[0].total").entity(Double.class).isEqualTo(40.0)
                .path("standings.official").valueIsNull()
                .path("standings.pending.unfinishedTurns").entity(Integer.class).isEqualTo(0);
    }

    @Test
    void anAppealIsReadById() {
        graphQl.document("{ appeal(id: \"" + appealId + "\") { id attemptId teamId reason evidence status "
                        + "reviewerId submittedAt resolvedAt } }")
                .execute()
                .path("appeal.id").entity(String.class).isEqualTo(appealId)
                .path("appeal.attemptId").entity(String.class).isEqualTo(ATTEMPT_A)
                .path("appeal.teamId").entity(String.class).isEqualTo(TEAM_A)
                .path("appeal.reason").entity(String.class).isEqualTo("Objetivo no contado")
                .path("appeal.evidence").entity(String.class).isEqualTo("Video")
                .path("appeal.status").entity(String.class).isEqualTo("PENDING")
                .path("appeal.reviewerId").valueIsNull()
                .path("appeal.submittedAt").hasValue()
                .path("appeal.resolvedAt").valueIsNull();
    }

    @Test
    void theAppealsOfAnAttemptAreItsOwn() {
        graphQl.document("""
                        { a: attempt(id: "%s") { id appeals { id attemptId teamId status } }
                          b: attempt(id: "%s") { id appeals { id } } }
                        """.formatted(ATTEMPT_A, ATTEMPT_B))
                .execute()
                .path("a.appeals[*].id").entityList(String.class).containsExactly(appealId)
                .path("a.appeals[0].attemptId").entity(String.class).isEqualTo(ATTEMPT_A)
                .path("a.appeals[0].teamId").entity(String.class).isEqualTo(TEAM_A)
                .path("a.appeals[0].status").entity(String.class).isEqualTo("PENDING")
                .path("b.appeals").entityList(Object.class).hasSize(0);
    }

    @Test
    void aCategoryTheEditionDoesNotOfferInRoundsIsABadRequestAtThatField() {
        graphQl.document("""
                        { challenge(id: "%s") { name rounds(categoryId: "cat-gql-rank-other") { id } } }
                        """.formatted(CHALLENGE))
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getMessage().equals("Category cat-gql-rank-other is not offered in edition " + EDITION)
                        && error.getParsedPath().equals(List.of("challenge", "rounds")))
                .verify()
                // rounds is non-null, so its parent goes null; nothing about the challenge is made up.
                .path("challenge").valueIsNull();
    }

    /**
     * Evidence of the batch (D5): asking for {@code edition} costs a fixed number of statements however many
     * challenges the response has, each in its own edition. Resolved per challenge it would cost one per challenge.
     * <p>
     * Here the rows are aliases of {@code Query.challenge}. Measured with graphql-java 25: the first root alias is
     * dispatched on its own and the rest in one batch, so N &gt;= 2 aliases cost
     * {@value #STATEMENTS_FOR_ALIASED_ROOTS} statements whether N is 3 or 5. Rows inside one list share a single
     * dispatch: see {@link #theChallengesOfAnEditionResolveTheirEditionInOneStatementForOneOrThreeChallenges()}.
     */
    @Test
    void resolvingTheEditionOfEveryChallengeCostsTheSameStatementsForThreeOrFiveChallenges() throws Exception {
        List<String> ids = IntStream.rangeClosed(1, 5).mapToObj(n -> "ch-gql-rank-batch-" + n).toList();
        for (int n = 1; n <= ids.size(); n++) {
            String edition = "ed-gql-rank-batch-" + n;
            if (editions.findById(EditionId.of(edition)).isEmpty()) {
                mvc.perform(EditionRequests.edition(edition)).andExpect(status().isCreated());
                mvc.perform(EditionRequests.challenge(edition, ids.get(n - 1))).andExpect(status().isCreated());
            }
        }

        assertThat(extraStatementsForEditions(ids.subList(0, 1))).isEqualTo(ONE_STATEMENT_FOR_ONE_CHALLENGE);
        assertThat(extraStatementsForEditions(ids.subList(0, 3))).isEqualTo(STATEMENTS_FOR_ALIASED_ROOTS);
        assertThat(extraStatementsForEditions(ids)).isEqualTo(STATEMENTS_FOR_ALIASED_ROOTS);
    }

    /**
     * The same batch under a list ({@code Edition.challenges}): every row is dispatched together, so asking for the
     * edition of each challenge costs one statement with one challenge and with three.
     */
    @Test
    void theChallengesOfAnEditionResolveTheirEditionInOneStatementForOneOrThreeChallenges() throws Exception {
        for (int count : List.of(1, 3)) {
            String edition = "ed-gql-rank-list-" + count;
            if (editions.findById(EditionId.of(edition)).isEmpty()) {
                mvc.perform(EditionRequests.edition(edition)).andExpect(status().isCreated());
                for (int n = 1; n <= count; n++) {
                    mvc.perform(EditionRequests.challenge(edition, "ch-gql-rank-list-" + count + "-" + n))
                            .andExpect(status().isCreated());
                }
            }
        }

        assertThat(extraStatementsUnderAList("ed-gql-rank-list-1")).isEqualTo(ONE_STATEMENT_FOR_ONE_CHALLENGE);
        assertThat(extraStatementsUnderAList("ed-gql-rank-list-3")).isEqualTo(ONE_STATEMENT_FOR_ONE_CHALLENGE);
    }

    /** The {@code findByIds} of the editions: one {@code select ... where id in (...)}. */
    private static final long ONE_STATEMENT_FOR_ONE_CHALLENGE = 1;
    /** One {@code findByIds} for the first root alias and one for all the others together. */
    private static final long STATEMENTS_FOR_ALIASED_ROOTS = 2;

    private long extraStatementsForEditions(List<String> challengeIds) {
        return extraStatements(aliased(challengeIds, "id name"), aliased(challengeIds, "id name edition { id name }"),
                "c1.id");
    }

    private long extraStatementsUnderAList(String edition) {
        String query = "{ edition(id: \"" + edition + "\") { challenges { id name %s } } }";
        return extraStatements(query.formatted(""), query.formatted("edition { id name }"), "edition.challenges");
    }

    private long extraStatements(String base, String withEditions, String answered) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            graphQl.document(withEditions).execute(); // warm-up: first use of the statements and mappings
            return statementsOf(statistics, withEditions, answered) - statementsOf(statistics, base, answered);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    /** Clears right before the query and reads right after it: the suite shares the context and runs on one thread. */
    private long statementsOf(Statistics statistics, String document, String answered) {
        statistics.clear();
        graphQl.document(document).execute().path(answered).hasValue();
        return statistics.getPrepareStatementCount();
    }

    /** {@code { c1: challenge(id: ...) { fields } c2: ... }}: every challenge at the same level of the response. */
    private static String aliased(List<String> challengeIds, String fields) {
        return IntStream.range(0, challengeIds.size())
                .mapToObj(i -> "c" + (i + 1) + ": challenge(id: \"" + challengeIds.get(i) + "\") { " + fields + " }")
                .collect(Collectors.joining(" ", "{ ", " }"));
    }

    private void schedule(String slotId, String teamId, int number) {
        String roundId = "round-" + slotId;
        Round round = Round.of(RoundInfo.of(RoundId.of(roundId), "Ronda " + number,
                RoundScope.of(ChallengeId.of(CHALLENGE), EditionId.of(EDITION), CategoryId.of(CATEGORY), number)));
        LocalDateTime start = START.plusMinutes(number * 10L);
        rounds.save(round.addSlot(Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of(roundId), TeamId.of(teamId)),
                SlotAssignment.of(Track.active(TrackId.of("trk-gql-rank"), "Pista", "Madera"),
                        List.of(Judge.of(JudgeId.of("j-gql-rank"), "Juez", "General"))),
                new TimeWindow(start, start.plusMinutes(5)))));
    }

    private void score(String attemptId, int objectives) throws Exception {
        mvc.perform(post("/slots/{slotId}/measurements", attemptId.substring(0, attemptId.lastIndexOf('-')))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "j-gql-rank", "timeSeconds": 60, "objectives": %d,
                                 "penalties": 0, "consumption": 0, "measurements": {}}
                                """.formatted(CHALLENGE, objectives)))
                .andExpect(status().isOk());
    }

    private static Team team(String id, String name) {
        return Team.of(TeamId.of(id), name, "ITBA",
                new Robot(RobotId.of("r-" + id), name + "-Bot",
                        RobotSpecification.of(2000, 200, 200, 200, 2, Set.of("LIDAR"))),
                List.of(TeamMember.of(ParticipantId.of("m1-" + id), name + " A", LocalDate.of(2004, 1, 1), "LEADER", DATE),
                        TeamMember.of(ParticipantId.of("m2-" + id), name + " B", LocalDate.of(2004, 2, 2), "DEV", DATE)),
                new Documentation().withDocument("DOC", "doc.pdf").verify(ActorId.of("inspector"), TIME));
    }
}
