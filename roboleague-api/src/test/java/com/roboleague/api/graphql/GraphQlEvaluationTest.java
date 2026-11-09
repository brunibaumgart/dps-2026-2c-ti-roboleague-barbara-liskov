package com.roboleague.api.graphql;

import com.jayway.jsonpath.JsonPath;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evaluation reads of the GraphQL API: a round with its challenge, category and the capture state of its turns; a slot
 * whose attempts get their breakdown from the {@code Attempt.breakdown} batch; an attempt with its round and full
 * breakdown; the round and attempt behind each standings row; the attempt of an appeal; and the errors of the three
 * lookups by id.
 * <p>
 * Scenario (built once, shared by every test): one challenge scoring 20 points per objective, two teams in one
 * category. Round 1 has Alpha's turn (2 objectives, 40) and Beta's turn still without a result; round 2 has Beta's
 * turn (3 objectives, 60). The standings are calculated once and Alpha appeals its attempt.
 */
class GraphQlEvaluationTest extends ApiTest {

    private static final String EDITION = "ed-gql-eval";
    private static final String CATEGORY = "cat-gql-eval";
    private static final String CHALLENGE = "ch-gql-eval";
    private static final String TEAM_A = "team-gql-eval-a";
    private static final String TEAM_B = "team-gql-eval-b";
    private static final String ROUND_1 = "round-gql-eval-1";
    private static final String ROUND_2 = "round-gql-eval-2";
    private static final String SLOT_A1 = "slot-gql-eval-a1";
    private static final String SLOT_B1 = "slot-gql-eval-b1";
    private static final String SLOT_B2 = "slot-gql-eval-b2";
    private static final String ATTEMPT_A1 = SLOT_A1 + "-1";
    private static final String ATTEMPT_B2 = SLOT_B2 + "-1";
    private static final String TRACK = "trk-gql-eval";
    private static final String JUDGE = "j-gql-eval";
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

    @BeforeEach
    void scenario() throws Exception {
        if (editions.findById(EditionId.of(EDITION)).isPresent()) {
            return;
        }
        editions.save(Edition.of(EditionId.of(EDITION),
                Tournament.of("tor-gql-eval", "Torneo", new Season("s-gql-eval", 2026, "2026")), 1, "Edicion Eval",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                List.of(Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500))));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_A, "Alpha"));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_B, "Beta"));
        challenges.save(Challenge.draft(ChallengeId.of(CHALLENGE), EditionId.of(EDITION), "Laberinto Eval").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        rounds.save(round(ROUND_1, 1)
                .addSlot(slot(SLOT_A1, ROUND_1, TEAM_A, 0))
                .addSlot(slot(SLOT_B1, ROUND_1, TEAM_B, 10)));
        rounds.save(round(ROUND_2, 2).addSlot(slot(SLOT_B2, ROUND_2, TEAM_B, 60)));
        score(SLOT_A1, 2);
        score(SLOT_B2, 3);
        mvc.perform(post("/challenges/{id}/standings/versions", CHALLENGE).param("categoryId", CATEGORY))
                .andExpect(status().isCreated());
        String filed = mvc.perform(post("/attempts/{attemptId}/appeals", ATTEMPT_A1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId": "%s", "reason": "Objetivo no contado", "evidence": "Video"}
                                """.formatted(TEAM_A)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        appealId = JsonPath.read(filed, "$.id");
    }

    @Test
    void aRoundAnswersItsChallengeCategoryAndTheCaptureStateOfEveryTurn() {
        graphQl.document("""
                        query($id: ID!) {
                          round(id: $id) {
                            number name status challengeId categoryId
                            challenge { id name }
                            category { id name }
                            slots { id teamId track { id }
                                    turn { rulebookVersion attemptRule acceptsResult attempts { id score } } }
                          }
                        }
                        """)
                .variable("id", ROUND_1)
                .execute()
                .path("round.number").entity(Integer.class).isEqualTo(1)
                .path("round.name").entity(String.class).isEqualTo("Ronda 1")
                .path("round.status").entity(String.class).isEqualTo("SCHEDULED")
                .path("round.challengeId").entity(String.class).isEqualTo(CHALLENGE)
                .path("round.categoryId").entity(String.class).isEqualTo(CATEGORY)
                .path("round.challenge.id").entity(String.class).isEqualTo(CHALLENGE)
                .path("round.challenge.name").entity(String.class).isEqualTo("Laberinto Eval")
                .path("round.category.id").entity(String.class).isEqualTo(CATEGORY)
                .path("round.category.name").entity(String.class).isEqualTo("Junior")
                // Slots in start-time order: Alpha's scored turn, then Beta's turn without a result.
                .path("round.slots[*].id").entityList(String.class).containsExactly(SLOT_A1, SLOT_B1)
                .path("round.slots[*].teamId").entityList(String.class).containsExactly(TEAM_A, TEAM_B)
                .path("round.slots[*].track.id").entityList(String.class).containsExactly(TRACK, TRACK)
                .path("round.slots[*].turn.rulebookVersion").entityList(Integer.class).containsExactly(1, 1)
                .path("round.slots[*].turn.attemptRule").entityList(String.class)
                .containsExactly("1 intento por turno", "1 intento por turno")
                .path("round.slots[0].turn.acceptsResult").entity(Boolean.class).isEqualTo(false)
                .path("round.slots[0].turn.attempts[*].id").entityList(String.class).containsExactly(ATTEMPT_A1)
                .path("round.slots[0].turn.attempts[0].score").entity(Double.class).isEqualTo(40.0)
                .path("round.slots[1].turn.acceptsResult").entity(Boolean.class).isEqualTo(true)
                .path("round.slots[1].turn.attempts").entityList(Object.class).hasSize(0);
    }

    @Test
    void aSlotAnswersItsTurnWithTheBreakdownOfEachAttemptLoadedInBatch() {
        graphQl.document("""
                        { slot(id: "%s") {
                            id roundId teamId status
                            turn { rulebookVersion acceptsResult
                                   attempts { id number status score breakdown { total } } }
                        } }
                        """.formatted(SLOT_A1))
                .execute()
                .path("slot.id").entity(String.class).isEqualTo(SLOT_A1)
                .path("slot.roundId").entity(String.class).isEqualTo(ROUND_1)
                .path("slot.teamId").entity(String.class).isEqualTo(TEAM_A)
                .path("slot.status").entity(String.class).isEqualTo("SCHEDULED")
                .path("slot.turn.rulebookVersion").entity(Integer.class).isEqualTo(1)
                .path("slot.turn.acceptsResult").entity(Boolean.class).isEqualTo(false)
                .path("slot.turn.attempts[*].id").entityList(String.class).containsExactly(ATTEMPT_A1)
                .path("slot.turn.attempts[0].number").entity(Integer.class).isEqualTo(1)
                // Alpha appealed it after the standings were calculated.
                .path("slot.turn.attempts[0].status").entity(String.class).isEqualTo("UNDER_APPEAL")
                .path("slot.turn.attempts[0].score").entity(Double.class).isEqualTo(40.0)
                .path("slot.turn.attempts[0].breakdown.total").entity(Double.class).isEqualTo(40.0);
    }

    @Test
    void anAttemptAnswersItsRoundAndExplainsItsScoreItemBySourceAndRevision() {
        graphQl.document("""
                        { attempt(id: "%s") {
                            id number slotId roundId status received score
                            round { id number }
                            breakdown { total awaiting
                                        items { concept rawMetric formula subtotal }
                                        bySource { source label subtotal }
                                        revisions { number reason total } }
                        } }
                        """.formatted(ATTEMPT_B2))
                .execute()
                .path("attempt.id").entity(String.class).isEqualTo(ATTEMPT_B2)
                .path("attempt.number").entity(Integer.class).isEqualTo(1)
                .path("attempt.slotId").entity(String.class).isEqualTo(SLOT_B2)
                .path("attempt.roundId").entity(String.class).isEqualTo(ROUND_2)
                .path("attempt.status").entity(String.class).isEqualTo("EVALUATED")
                .path("attempt.received").entityList(String.class).containsExactly("AUTOMATIC_MEASUREMENTS")
                // ObjectivesRule: 3 objectives x 20 points.
                .path("attempt.score").entity(Double.class).isEqualTo(60.0)
                .path("attempt.round.id").entity(String.class).isEqualTo(ROUND_2)
                .path("attempt.round.number").entity(Integer.class).isEqualTo(2)
                .path("attempt.breakdown.total").entity(Double.class).isEqualTo(60.0)
                .path("attempt.breakdown.awaiting").entityList(String.class).hasSize(0)
                .path("attempt.breakdown.items[*].concept").entityList(String.class).containsExactly("Objetivos")
                .path("attempt.breakdown.items[0].rawMetric").entity(String.class).isEqualTo("3 objetivos")
                .path("attempt.breakdown.items[0].formula").entity(String.class).isEqualTo("3 obj * 20.0 pts")
                .path("attempt.breakdown.items[0].subtotal").entity(Double.class).isEqualTo(60.0)
                .path("attempt.breakdown.bySource[*].source").entityList(String.class)
                .containsExactly("AUTOMATIC_MEASUREMENTS")
                .path("attempt.breakdown.bySource[0].label").entity(String.class).isEqualTo("Mediciones automáticas")
                .path("attempt.breakdown.bySource[0].subtotal").entity(Double.class).isEqualTo(60.0)
                .path("attempt.breakdown.revisions[*].number").entityList(Integer.class).containsExactly(1)
                .path("attempt.breakdown.revisions[0].reason").entity(String.class).isEqualTo("Every source arrived")
                .path("attempt.breakdown.revisions[0].total").entity(Double.class).isEqualTo(60.0);
    }

    @Test
    void eachStandingsRowAnswersTheRoundAndAttemptThatCounted() {
        graphQl.document("""
                        { standings(challengeId: "%s", categoryId: "%s") {
                            latest { entries { teamId considered {
                              roundId round { number } attempt { id score breakdown { total } } } } }
                        } }
                        """.formatted(CHALLENGE, CATEGORY))
                .execute()
                .path("standings.latest.entries[*].teamId").entityList(String.class).containsExactly(TEAM_B, TEAM_A)
                .path("standings.latest.entries[0].considered[*].roundId").entityList(String.class)
                .containsExactly(ROUND_2)
                .path("standings.latest.entries[0].considered[0].round.number").entity(Integer.class).isEqualTo(2)
                .path("standings.latest.entries[0].considered[0].attempt.id").entity(String.class).isEqualTo(ATTEMPT_B2)
                .path("standings.latest.entries[0].considered[0].attempt.score").entity(Double.class).isEqualTo(60.0)
                .path("standings.latest.entries[0].considered[0].attempt.breakdown.total").entity(Double.class)
                .isEqualTo(60.0)
                .path("standings.latest.entries[1].considered[*].roundId").entityList(String.class)
                .containsExactly(ROUND_1)
                .path("standings.latest.entries[1].considered[0].round.number").entity(Integer.class).isEqualTo(1)
                .path("standings.latest.entries[1].considered[0].attempt.id").entity(String.class).isEqualTo(ATTEMPT_A1)
                .path("standings.latest.entries[1].considered[0].attempt.score").entity(Double.class).isEqualTo(40.0)
                .path("standings.latest.entries[1].considered[0].attempt.breakdown.total").entity(Double.class)
                .isEqualTo(40.0);
    }

    @Test
    void anAppealAnswersTheAttemptItQuestionsWithItsBreakdown() {
        graphQl.document("""
                        { appeal(id: "%s") { attempt { id score breakdown { total revisions { number } } } } }
                        """.formatted(appealId))
                .execute()
                .path("appeal.attempt.id").entity(String.class).isEqualTo(ATTEMPT_A1)
                .path("appeal.attempt.score").entity(Double.class).isEqualTo(40.0)
                .path("appeal.attempt.breakdown.total").entity(Double.class).isEqualTo(40.0)
                .path("appeal.attempt.breakdown.revisions[*].number").entityList(Integer.class).containsExactly(1);
    }

    @Test
    void aMissingRoundIsNullWithANotFoundError() {
        graphQl.document("{ round(id: \"round-gql-eval-missing\") { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Round not found: round-gql-eval-missing")
                        && error.getParsedPath().equals(List.of("round")))
                .verify()
                .path("round").valueIsNull();
    }

    @Test
    void aMissingSlotIsNullWithANotFoundError() {
        graphQl.document("{ slot(id: \"slot-gql-eval-missing\") { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Slot not found: slot-gql-eval-missing")
                        && error.getParsedPath().equals(List.of("slot")))
                .verify()
                .path("slot").valueIsNull();
    }

    @Test
    void aMissingAttemptIsNullWithANotFoundError() {
        graphQl.document("{ attempt(id: \"slot-gql-eval-missing-1\") { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Attempt not found: slot-gql-eval-missing-1")
                        && error.getParsedPath().equals(List.of("attempt")))
                .verify()
                .path("attempt").valueIsNull();
    }

    @Test
    void anAttemptIdWithoutANumberIsABadRequest() {
        graphQl.document("{ attempt(id: \"sin-numero\") { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getMessage().equals("attempt id must be <slot>-<number>: sin-numero")
                        && error.getParsedPath().equals(List.of("attempt")))
                .verify()
                .path("attempt").valueIsNull();
    }

    private static Round round(String id, int number) {
        return Round.of(RoundInfo.of(RoundId.of(id), "Ronda " + number,
                RoundScope.of(ChallengeId.of(CHALLENGE), EditionId.of(EDITION), CategoryId.of(CATEGORY), number)));
    }

    private static Slot slot(String slotId, String roundId, String teamId, int minutesAfterStart) {
        LocalDateTime start = START.plusMinutes(minutesAfterStart);
        return Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of(roundId), TeamId.of(teamId)),
                SlotAssignment.of(Track.active(TrackId.of(TRACK), "Pista", "Madera"),
                        List.of(Judge.of(JudgeId.of(JUDGE), "Juez", "General"))),
                new TimeWindow(start, start.plusMinutes(5)));
    }

    private void score(String slotId, int objectives) throws Exception {
        mvc.perform(post("/slots/{slotId}/measurements", slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "%s", "timeSeconds": 60, "objectives": %d,
                                 "penalties": 0, "consumption": 0, "measurements": {}}
                                """.formatted(CHALLENGE, JUDGE, objectives)))
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
