package com.roboleague.api.attempt;

import com.roboleague.api.ApiTest;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttemptCorrectionControllerTest extends ApiTest {

    private static final String EDITION = "ed-api-fix";
    private static final String CATEGORY = "cat-api-fix";
    private static final String TEAM = "team-api-fix";
    private static final String JUDGE = "j-api-fix";
    private static final AtomicInteger NEXT = new AtomicInteger();

    @Autowired
    private EditionRepository editions;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private RoundRepository rounds;
    @Autowired
    private RegisterTeamUseCase registerTeam;

    @Test
    void aPenaltyAdjustmentAddsARevisionAndAnswersTheNewStandingsVersion() throws Exception {
        String attemptId = scoredAttempt();

        mvc.perform(penalties(attemptId, """
                        {"actorId": "arb-fix", "reason": "Toque de borde visto en video", "additionalPenalties": 2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.id").value(attemptId))
                .andExpect(jsonPath("$.attempt.status").value("ADJUSTED"))
                // 3 objectives => 60; 1 + 2 faults at 10 => -30
                .andExpect(jsonPath("$.attempt.score").value(30.0))
                .andExpect(jsonPath("$.standingsVersion").value(1));

        mvc.perform(get("/attempts/{attemptId}/breakdown", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions[1].authorId").value("arb-fix"))
                .andExpect(jsonPath("$.revisions[1].reason").value("Penalty applied: Toque de borde visto en video"));
    }

    @Test
    void aPenaltyAdjustmentNeedsEveryFieldAndAPositiveCount() throws Exception {
        String attemptId = scoredAttempt();

        mvc.perform(penalties(attemptId, """
                        {"actorId": "arb-fix", "reason": "Video"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing field 'additionalPenalties'"));
        mvc.perform(penalties(attemptId, """
                        {"reason": "Video", "additionalPenalties": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing field 'actorId'"));
        mvc.perform(penalties(attemptId, """
                        {"actorId": "arb-fix", "reason": "Video", "additionalPenalties": 0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("additionalPenalties must be positive"));
        mvc.perform(penalties("api-fix-missing-1", """
                        {"actorId": "arb-fix", "reason": "Video", "additionalPenalties": 1}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Attempt not found: api-fix-missing-1"));

        mvc.perform(get("/attempts/{attemptId}/breakdown", attemptId))
                .andExpect(jsonPath("$.status").value("EVALUATED"))
                .andExpect(jsonPath("$.revisions.length()").value(1));
    }

    @Test
    void aDisqualifiedAttemptStopsCountingAndTakesNoFurtherCorrection() throws Exception {
        String attemptId = scoredAttempt();

        mvc.perform(disqualification(attemptId, """
                        {"judgeId": "%s", "reason": "Robot fuera de reglamento"}""".formatted(JUDGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.status").value("DISQUALIFIED"))
                .andExpect(jsonPath("$.attempt.score").value(nullValue()))
                .andExpect(jsonPath("$.standingsVersion").value(1));

        mvc.perform(disqualification(attemptId, """
                        {"judgeId": "%s", "reason": "Otra vez"}""".formatted(JUDGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("attempt is disqualified: it cannot be disqualified"));
        mvc.perform(penalties(attemptId, """
                        {"actorId": "arb-fix", "reason": "Video", "additionalPenalties": 1}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAJudgeOfTheTurnDisqualifiesItsAttempt() throws Exception {
        String attemptId = scoredAttempt();

        mvc.perform(disqualification(attemptId, """
                        {"judgeId": "j-other", "reason": "Ajeno"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("judge j-other is not assigned to slot "
                        + attemptId.substring(0, attemptId.length() - 2)));
        mvc.perform(disqualification(attemptId, """
                        {"judgeId": "%s"}""".formatted(JUDGE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing field 'reason'"));
        mvc.perform(disqualification("api-fix-missing-1", """
                        {"judgeId": "%s", "reason": "x"}""".formatted(JUDGE)))
                .andExpect(status().isNotFound());
    }

    private String scoredAttempt() throws Exception {
        ensureEdition();
        String suffix = String.valueOf(NEXT.incrementAndGet());
        String challenge = "ch-api-fix-" + suffix;
        String slot = "slot-api-fix-" + suffix;
        challenges.save(Challenge.draft(ChallengeId.of(challenge), EditionId.of(EDITION), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))),
                        List.of(new PenaltyRule("Faltas", Points.of(10)))),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        RoundId roundId = RoundId.of("round-" + slot);
        rounds.save(Round.of(RoundInfo.of(roundId, "Ronda",
                RoundScope.of(ChallengeId.of(challenge), EditionId.of(EDITION), CategoryId.of(CATEGORY), 1)))
                .addSlot(new Slot(new SlotIdentity(SlotId.of(slot), roundId, TeamId.of(TEAM)),
                        new SlotAssignment(Track.active(TrackId.of("trk-api-fix"), "Pista", "Madera"),
                                List.of(Judge.of(JudgeId.of(JUDGE), "Juez", "General"))),
                        new TimeWindow(LocalDateTime.of(2026, 11, 10, 9, 0), LocalDateTime.of(2026, 11, 10, 9, 10)))));
        String attemptId = slot + "-1";
        mvc.perform(post("/slots/{slotId}/measurements", slot)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "%s", "timeSeconds": 40, "objectives": 3,
                                 "penalties": 1, "consumption": 0, "measurements": {}}
                                """.formatted(challenge, JUDGE)))
                .andExpect(status().isOk());
        return attemptId;
    }

    private void ensureEdition() {
        if (editions.findById(EditionId.of(EDITION)).isPresent()) {
            return;
        }
        editions.save(Edition.of(EditionId.of(EDITION), Tournament.of("tor-api-fix", "Torneo", new Season("s-api-fix", 2026, "2026")),
                1, "Edicion API", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                List.of(Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500))));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY),
                Team.of(TeamId.of(TEAM), "Corregido", "ITBA",
                        new Robot(RobotId.of("r-api-fix"), "Bot", RobotSpecification.of(2000, 200, 200, 200, 2, Set.of("LIDAR"))),
                        List.of(TeamMember.of(ParticipantId.of("m1-api-fix"), "Ana", LocalDate.of(2004, 1, 1), "LEADER", DATE),
                                TeamMember.of(ParticipantId.of("m2-api-fix"), "Ben", LocalDate.of(2004, 2, 2), "DEV", DATE)),
                        new Documentation().withDocument("DOC", "doc.pdf").verify(ActorId.of("inspector"), TIME)));
    }

    private static RequestBuilder penalties(String attemptId, String body) {
        return post("/attempts/{attemptId}/penalty-adjustments", attemptId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestBuilder disqualification(String attemptId, String body) {
        return post("/attempts/{attemptId}/disqualification", attemptId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
