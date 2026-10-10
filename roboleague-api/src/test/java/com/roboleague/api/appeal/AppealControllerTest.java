package com.roboleague.api.appeal;

import com.roboleague.api.ApiTest;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppealControllerTest extends ApiTest {

    private static final String EDITION = "ed-api-app";
    private static final String CATEGORY = "cat-api-app";
    private static final String TEAM = "team-api-app";
    private static final AtomicInteger NEXT = new AtomicInteger();

    @Autowired
    private AppealRepository appeals;
    @Autowired
    private EditionRepository editions;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private RoundRepository rounds;
    @Autowired
    private RegisterTeamUseCase registerTeam;

    @Test
    void reviewMovesTheAppealUnderReview() throws Exception {
        appeals.save(Appeal.of(AppealId.of("api-app-1"), AttemptId.parse("att-1"), TeamId.of("team-1"), "Penalizacion inexistente", "Video", TIME));

        mvc.perform(review("api-app-1", "arb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("api-app-1"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerId").value("arb-1"));
    }

    @Test
    void reviewingTwiceIsAConflict() throws Exception {
        appeals.save(Appeal.of(AppealId.of("api-app-2"), AttemptId.parse("att-2"), TeamId.of("team-2"), "Penalizacion inexistente", "Video", TIME));
        mvc.perform(review("api-app-2", "arb-1")).andExpect(status().isOk());

        mvc.perform(review("api-app-2", "arb-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Appeal is already under review by arb-1"));
    }

    @Test
    void reviewingAnUnknownAppealIsABadRequest() throws Exception {
        mvc.perform(review("does-not-exist", "arb-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Appeal not found: does-not-exist"));
    }

    @Test
    void filingAnAppealAndResolvingItGoesThroughTheRealEndpoints() throws Exception {
        ScoredAttempt scored = scoredAttempt();
        String attemptId = scored.attemptId();

        mvc.perform(post("/attempts/{attemptId}/appeals", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId": "%s", "reason": "Faltas mal contadas", "evidence": "Video"}
                                """.formatted(TEAM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.teamId").value(TEAM))
                .andExpect(jsonPath("$.status").value("PENDING"));

        String appealId = appeals.findByAttemptId(AttemptId.parse(attemptId)).getFirst().getAppealId().value();
        mvc.perform(review(appealId, "arb-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mvc.perform(post("/appeals/{appealId}/acceptance", appealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": "arb-api", "notes": "Era una sola falta",
                                 "measurements": {"timeSeconds": 40, "objectives": 2, "penalties": 1, "consumption": 0}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.standingsVersion").value(1));

        mvc.perform(get("/attempts/{attemptId}/appeals", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        mvc.perform(get("/challenges/{id}/appeals", scored.challengeId()).param("categoryId", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(appealId)));
    }

    @Test
    void rejectingAnAppealKeepsTheScoreAndInvalidCorrectionsAreUnprocessable() throws Exception {
        ScoredAttempt scored = scoredAttempt();
        mvc.perform(post("/attempts/{attemptId}/appeals", scored.attemptId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId": "%s", "reason": "Faltas", "evidence": "Video"}
                                """.formatted(TEAM)))
                .andExpect(status().isCreated());
        String appealId = appeals.findByAttemptId(AttemptId.parse(scored.attemptId())).getFirst().getAppealId().value();
        mvc.perform(review(appealId, "arb-api")).andExpect(status().isOk());

        mvc.perform(post("/appeals/{appealId}/acceptance", appealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": "arb-api", "notes": "Sin correccion"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Corrections rejected"))
                .andExpect(jsonPath("$.details", contains("an accepted appeal needs the corrected report of at least one source")));

        mvc.perform(post("/appeals/{appealId}/rejection", appealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": "arb-api", "notes": "El video confirma las faltas"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void aTeamCannotAppealSomeoneElsesAttempt() throws Exception {
        ScoredAttempt scored = scoredAttempt();
        mvc.perform(post("/attempts/{attemptId}/appeals", scored.attemptId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId": "other-team", "reason": "Ajeno", "evidence": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Team other-team does not own attempt " + scored.attemptId()));
    }

    private ScoredAttempt scoredAttempt() throws Exception {
        ensureEdition();
        String suffix = String.valueOf(NEXT.incrementAndGet());
        String challenge = "ch-api-app-" + suffix;
        String slot = "slot-api-app-" + suffix;
        challenges.save(Challenge.draft(ChallengeId.of(challenge), EditionId.of(EDITION), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                        List.of(new PenaltyRule("Faltas", 10.0))),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        Round round = Round.of(RoundInfo.of(RoundId.of("round-" + slot), "Ronda",
                RoundScope.of(ChallengeId.of(challenge), EditionId.of(EDITION), CategoryId.of(CATEGORY), NEXT.incrementAndGet())));
        rounds.save(round.addSlot(Slot.of(SlotIdentity.of(SlotId.of(slot), RoundId.of("round-" + slot), TeamId.of(TEAM)),
                SlotAssignment.of(Track.active(TrackId.of("trk-api-app"), "Pista", "Madera"),
                        List.of(Judge.of(JudgeId.of("j-api-app"), "Juez", "General"))),
                new TimeWindow(LocalDateTime.of(2026, 11, 10, 9, 0), LocalDateTime.of(2026, 11, 10, 9, 10)))));
        String attemptId = slot + "-1";
        mvc.perform(put("/attempts/{attemptId}/measurements", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "j-api-app", "timeSeconds": 40, "objectives": 2,
                                 "penalties": 3, "consumption": 0, "measurements": {}}
                                """.formatted(challenge)))
                .andExpect(status().isOk());
        return new ScoredAttempt(challenge, attemptId);
    }

    private void ensureEdition() {
        if (editions.findById(EditionId.of(EDITION)).isPresent()) {
            return;
        }
        editions.save(Edition.of(EditionId.of(EDITION), Tournament.of("tor-api-app", "Torneo", new Season("s-api-app", 2026, "2026")),
                1, "Edicion API", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                List.of(Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500))));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY),
                Team.of(TeamId.of(TEAM), "Apelante", "ITBA",
                        new Robot(RobotId.of("r-api-app"), "Bot", RobotSpecification.of(2000, 200, 200, 200, 2, Set.of("LIDAR"))),
                        List.of(TeamMember.of(ParticipantId.of("m1-api-app"), "Ana", LocalDate.of(2004, 1, 1), "LEADER", DATE),
                                TeamMember.of(ParticipantId.of("m2-api-app"), "Ben", LocalDate.of(2004, 2, 2), "DEV", DATE)),
                        new Documentation().withDocument("DOC", "doc.pdf").verify(ActorId.of("inspector"), TIME)));
    }

    private record ScoredAttempt(String challengeId, String attemptId) {
    }

    private static RequestBuilder review(String appealId, String reviewerId) {
        return post("/appeals/{appealId}/review", appealId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reviewerId": "%s"}
                        """.formatted(reviewerId));
    }
}
