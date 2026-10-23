package com.roboleague.api.attempt;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.api.ApiTest;
import com.roboleague.evaluation.CappedAt;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.ChallengeRepository;
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
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttemptControllerTest extends ApiTest {

    private static final String MAZE = "api-att-maze";
    private static final String RESCUE = "api-att-rescue";
    private static final String CAPPED = "api-att-capped";
    private static final Metric ZONE = Metric.sensor("zona_alcanzada");
    private static final Metric DISTANCE = Metric.sensor("distancia_metros");
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final RankingScheme RANKING = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Autowired
    private ChallengeRepository challenges;

    @Autowired
    private RoundRepository rounds;

    @BeforeEach
    void publishTheChallenges() {
        publishOnce(Challenge.draft(ChallengeId.of(MAZE), EditionId.of("ed-api-att"), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", Points.of(100), 60.0, 1.0, 2.0, Points.ZERO),
                        new ObjectivesRule("Objetivos", Points.of(20))), List.of(new PenaltyRule("Faltas", Points.of(15)))), RANKING));
        publishOnce(Challenge.draft(ChallengeId.of(RESCUE), EditionId.of("ed-api-att"), "Rescate").publish(new ScoringScheme(
                new MetricSheet(List.of(new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)))),
                new ScoreRules(List.of(new ObjectivesRule("Zonas despejadas", Points.of(15)),
                        new VictimsRule("Víctimas rescatadas", RESCUED, Points.of(25)),
                        new JudgeSubjectiveRule("Panel técnico", 5.0)), List.of(), List.of()),
                new Unlimited()), RANKING));
        publishOnce(Challenge.draft(ChallengeId.of(CAPPED), EditionId.of("ed-api-att"), "Hitos con tope").publish(
                new ScoringScheme(new MetricSheet(List.of(
                        new MetricDefinition(ZONE, MeasurementUnit.COUNT, ValueRange.between(0.0, 1.0)),
                        new MetricDefinition(DISTANCE, MeasurementUnit.METERS, ValueRange.atLeast(0.0)))),
                        new ScoreRules(List.of(TimeBasedRule.standard(Points.of(100), 60.0)),
                                List.of(new MilestoneBonusRule("Bonus por zona", new Milestone(ZONE, 1.0), Points.of(30)),
                                        new MilestoneBonusRule("Bonus por distancia", new Milestone(DISTANCE, 10.0), Points.of(25))),
                                List.of()),
                        new CappedAt(Points.of(40))), RANKING));
    }

    /** The challenges are shared by every test of the class: a second save of a new copy would be a conflict. */
    private void publishOnce(Challenge challenge) {
        if (challenges.findById(challenge.getId()).isEmpty()) {
            challenges.save(challenge);
        }
    }

    @Test
    void measurementsScoreAnAttemptOfTheSlotsTeam() throws Exception {
        scheduleSlot("api-att-slot-1", MAZE);

        mvc.perform(measurements("api-att-slot-1", MAZE, "api-j-1", 50.0, 4, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("api-att-slot-1-1"))
                .andExpect(jsonPath("$.slotId").value("api-att-slot-1"))
                .andExpect(jsonPath("$.teamId").value("api-t-1"))
                .andExpect(jsonPath("$.challengeId").value(MAZE))
                .andExpect(jsonPath("$.rulebookVersion").value(1))
                .andExpect(jsonPath("$.status").value("EVALUATED"))
                .andExpect(jsonPath("$.received", contains("AUTOMATIC_MEASUREMENTS")))
                // 50s => 100 + 10; 4 objectives => 80; 1 fault => -15
                .andExpect(jsonPath("$.score").value(175.0));
    }

    @Test
    void theMixedChallengeAwaitsTheJudgePanelBeforeScoring() throws Exception {
        scheduleSlot("api-att-slot-2", RESCUE);

        mvc.perform(measurements("api-att-slot-2", RESCUE, "api-j-1", 90.0, 3, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SOURCES"))
                .andExpect(jsonPath("$.score").value(nullValue()));

        mvc.perform(judgeScores("api-att-slot-2", RESCUE, "api-j-2", """
                        {"api-j-1": 8, "api-j-2": 6}""", """
                        {"victimas_rescatadas": 2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EVALUATED"))
                .andExpect(jsonPath("$.received", contains("AUTOMATIC_MEASUREMENTS", "JUDGE_PANEL")))
                .andExpect(jsonPath("$.score").value(130.0));
    }

    @Test
    void theBreakdownShowsWhatIsPendingUntilEverySourceArrives() throws Exception {
        scheduleSlot("api-att-slot-8", RESCUE);
        mvc.perform(measurements("api-att-slot-8", RESCUE, "api-j-1", 90.0, 3, 0)).andExpect(status().isOk());

        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot-8-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SOURCES"))
                .andExpect(jsonPath("$.awaiting", contains("JUDGE_PANEL")))
                .andExpect(jsonPath("$.score").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.revisions", hasSize(0)));
    }

    @Test
    void theBreakdownExplainsEachSourceOnceTheAttemptIsScored() throws Exception {
        scheduleSlot("api-att-slot-9", RESCUE);
        mvc.perform(measurements("api-att-slot-9", RESCUE, "api-j-1", 90.0, 3, 0)).andExpect(status().isOk());
        mvc.perform(judgeScores("api-att-slot-9", RESCUE, "api-j-2", """
                {"api-j-1": 8, "api-j-2": 6}""", """
                {"victimas_rescatadas": 2}""")).andExpect(status().isOk());

        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot-9-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awaiting", hasSize(0)))
                .andExpect(jsonPath("$.score").value(130.0))
                .andExpect(jsonPath("$.items[*].concept",
                        contains("Zonas despejadas", "Víctimas rescatadas", "Panel técnico")))
                .andExpect(jsonPath("$.bySource[0].label").value("Mediciones automáticas"))
                .andExpect(jsonPath("$.bySource[0].subtotal").value(45.0))
                .andExpect(jsonPath("$.bySource[1].source").value("JUDGE_PANEL"))
                .andExpect(jsonPath("$.bySource[1].subtotal").value(85.0))
                .andExpect(jsonPath("$.revisions[0].rulebookVersion").value(1))
                .andExpect(jsonPath("$.revisions[0].authorId").value("api-j-2"))
                .andExpect(jsonPath("$.revisions[0].timestamp").value("2026-10-08T12:00:00"));
    }

    @Test
    void theBreakdownOfACappedRulebookShowsTheCutAsAnItemWithItsFormula() throws Exception {
        scheduleSlot("api-att-slot-capped", CAPPED);
        mvc.perform(post("/slots/{slotId}/measurements", "api-att-slot-capped")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "api-j-1", "timeSeconds": 60, "objectives": 0,
                                 "penalties": 0, "consumption": 0,
                                 "measurements": {"zona_alcanzada": 1, "distancia_metros": 12}}
                                """.formatted(CAPPED)))
                .andExpect(status().isOk());

        // 100 for the time; the bonuses add 55, the cap keeps 40
        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot-capped-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(140.0))
                .andExpect(jsonPath("$.items[*].concept", contains("Regla de Tiempo", "Bonus por zona",
                        "Bonus por distancia", "Tope de bonificaciones")))
                .andExpect(jsonPath("$.items[3].rawMetric").value("bonificaciones obtenidas 55.0 pts"))
                .andExpect(jsonPath("$.items[3].formula").value("tope 40.0 pts, recorte -15.0 pts"))
                .andExpect(jsonPath("$.items[3].subtotal").value(-15.0))
                .andExpect(jsonPath("$.bySource[0].subtotal").value(155.0));
    }

    @Test
    void theBreakdownOfAnUnknownAttemptIsNotFound() throws Exception {
        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-nothing-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Attempt not found: api-att-nothing-1"));
    }

    @Test
    void aSecondResultForATurnOfOneAttemptIsUnprocessableAndKeepsTheFirst() throws Exception {
        scheduleSlot("api-att-slot-3", MAZE);
        mvc.perform(measurements("api-att-slot-3", MAZE, "api-j-1", 50.0, 4, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("api-att-slot-3-1"))
                .andExpect(jsonPath("$.number").value(1));

        mvc.perform(measurements("api-att-slot-3", MAZE, "api-j-2", 40.0, 5, 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value(
                        "Turn api-att-slot-3 already ran 1 attempt(s); rulebook v1 admits 1 intento por turno"));
        mvc.perform(get("/slots/{slotId}/results", "api-att-slot-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value("api-att-slot-3"))
                .andExpect(jsonPath("$.rulebookVersion").value(1))
                .andExpect(jsonPath("$.attemptRule").value("1 intento por turno"))
                .andExpect(jsonPath("$.acceptsResult").value(false))
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].id").value("api-att-slot-3-1"))
                .andExpect(jsonPath("$.attempts[0].number").value(1));
    }

    @Test
    void theSameSourceTwiceWhileTheOtherIsAwaitedIsAConflict() throws Exception {
        scheduleSlot("api-att-slot-twice", RESCUE);
        mvc.perform(measurements("api-att-slot-twice", RESCUE, "api-j-1", 90.0, 3, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SOURCES"));

        mvc.perform(measurements("api-att-slot-twice", RESCUE, "api-j-1", 80.0, 3, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AUTOMATIC_MEASUREMENTS already arrived for attempt "
                        + "api-att-slot-twice-1; corrections go through a fault adjustment or an appeal"));
    }

    @Test
    void theAttemptsOfATurnWithoutResultsAreEmptyAndOfAnUnknownTurnNotFound() throws Exception {
        scheduleSlot("api-att-slot-empty", MAZE);
        mvc.perform(get("/slots/{slotId}/results", "api-att-slot-empty"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempts.length()").value(0))
                .andExpect(jsonPath("$.acceptsResult").value(true));
        mvc.perform(get("/slots/{slotId}/results", "api-att-slot-none"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Slot not found: api-att-slot-none"));
    }

    @Test
    void measurementsThatDoNotFitTheRulebookAreUnprocessable() throws Exception {
        scheduleSlot("api-att-slot-4", RESCUE);

        mvc.perform(judgeScores("api-att-slot-4", RESCUE, "api-j-2", """
                        {"api-j-1": 8}""", """
                        {"victimas_rescatadas": 7, "rescate_completo": 1}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("Result rejected"))
                .andExpect(jsonPath("$.details", contains(
                        "measurement 'victimas_rescatadas' must be between 0.0 and 4.0: 7.0",
                        "measurement 'rescate_completo' is not declared for JUDGE_PANEL")));
    }

    @Test
    void aJudgeNotAssignedToTheSlotIsUnprocessable() throws Exception {
        scheduleSlot("api-att-slot-5", MAZE);

        mvc.perform(measurements("api-att-slot-5", MAZE, "api-j-9", 50.0, 4, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details", contains("judge api-j-9 is not assigned to slot api-att-slot-5")));
    }

    @Test
    void aSlotThatWasNotScheduledIsNotFound() throws Exception {
        mvc.perform(measurements("api-att-slot-unknown", MAZE, "api-j-1", 50.0, 4, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Slot not found: api-att-slot-unknown"));
    }

    @Test
    void anAttemptIdWithoutANumberIsABadRequest() throws Exception {
        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("attempt id must be <slot>-<number>: api-att-slot"));
    }

    @Test
    void measurementsWithoutTheirFixedFieldsAreABadRequest() throws Exception {
        mvc.perform(post("/slots/{slotId}/measurements", "api-att-slot-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "api-j-1", "timeSeconds": 50}
                                """.formatted(MAZE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "measurements need challengeId, judgeId, timeSeconds, objectives, penalties and consumption"));
    }

    @Test
    void aNegativeTimeIsABadRequest() throws Exception {
        scheduleSlot("api-att-slot-7", MAZE);

        mvc.perform(measurements("api-att-slot-7", MAZE, "api-j-1", -1.0, 4, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("timeTakenSeconds cannot be negative"));
    }

    @Test
    void aNullJudgeScoreRemainsABadRequestAfterTypingTheMapKeys() throws Exception {
        scheduleSlot("api-att-slot-null-score", RESCUE);

        mvc.perform(judgeScores("api-att-slot-null-score", RESCUE, "api-j-1",
                        "{\"api-j-1\": null}", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "score of judge 'api-j-1' must be a finite, non-negative number: null"));
    }

    @Test
    void aJudgePanelWithoutScoresIsABadRequestAndCreatesNoAttempt() throws Exception {
        scheduleSlot("api-att-slot-empty-panel", RESCUE);

        mvc.perform(judgeScores("api-att-slot-empty-panel", RESCUE, "api-j-1", "{}",
                        "{\"victimas_rescatadas\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("a judge panel report needs at least one judge score"));
        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot-empty-panel-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFirstCaptureForAnotherChallengeIsAConflictAndCreatesNoAttempt() throws Exception {
        scheduleSlot("api-att-slot-wrong-challenge", MAZE);
        mvc.perform(measurements("api-att-slot-wrong-challenge", RESCUE, "api-j-1", 90, 3, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Round round-api-att-slot-wrong-challenge belongs to challenge " + MAZE + ", not " + RESCUE));
        mvc.perform(get("/attempts/{attemptId}/breakdown", "api-att-slot-wrong-challenge-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Attempt not found: api-att-slot-wrong-challenge-1"));
    }

    private static final java.util.concurrent.atomic.AtomicInteger ROUND_NUMBER = new java.util.concurrent.atomic.AtomicInteger();

    private void scheduleSlot(String slotId, String challengeId) {
        String roundId = "round-" + slotId;
        Round round = Round.of(RoundInfo.of(RoundId.of(roundId), "Ronda", RoundScope.of(ChallengeId.of(challengeId), EditionId.of("ed-api-att"), CategoryId.of("cat-junior"), ROUND_NUMBER.incrementAndGet())));
        round = round.addSlot(Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of(roundId), TeamId.of("api-t-1")),
                SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Madera"),
                        List.of(Judge.of(JudgeId.of("api-j-1"), "Juez Uno", "General"), Judge.of(JudgeId.of("api-j-2"), "Juez Dos", "General"))),
                new TimeWindow(START, START.plusMinutes(10))));
        rounds.save(round);
    }

    private static RequestBuilder measurements(String slotId, String challengeId, String judgeId, double seconds,
                                               int objectives, int penalties) {
        return post("/slots/{slotId}/measurements", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"challengeId": "%s", "judgeId": "%s", "timeSeconds": %s, "objectives": %d,
                         "penalties": %d, "consumption": 0, "measurements": {}}
                        """.formatted(challengeId, judgeId, seconds, objectives, penalties));
    }

    private static RequestBuilder judgeScores(String slotId, String challengeId, String judgeId, String scores,
                                              String measurements) {
        return post("/slots/{slotId}/judge-scores", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"challengeId": "%s", "judgeId": "%s", "scores": %s, "measurements": %s}
                        """.formatted(challengeId, judgeId, scores, measurements));
    }
}
