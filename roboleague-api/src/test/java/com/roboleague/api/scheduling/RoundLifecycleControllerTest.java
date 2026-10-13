package com.roboleague.api.scheduling;

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
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.Tournament;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundLifecycleControllerTest extends ApiTest {

    private static final String EDITION = "ed-api-life";
    private static final String CATEGORY = "cat-api-life";
    private static final String JUDGE = "j-api-life";
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final AtomicInteger NEXT = new AtomicInteger();

    @Autowired
    private EditionRepository editions;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private RoundRepository rounds;

    @Test
    void cancellingASlotRecordsItsAuthorAndReasonAndItTakesNoResults() throws Exception {
        Scheduled round = round();

        mvc.perform(cancellation(round.id(), round.slot("b"), """
                        {"actorId": "org-life", "reason": "El equipo no se presentó"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").value(round.id()))
                .andExpect(jsonPath("$.slots[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.slots[0].cancellation").value(nullValue()))
                .andExpect(jsonPath("$.slots[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.slots[1].cancellation.actorId").value("org-life"))
                .andExpect(jsonPath("$.slots[1].cancellation.reason").value("El equipo no se presentó"))
                .andExpect(jsonPath("$.slots[1].cancellation.cancelledAt").value("2026-10-08T12:00:00"));

        mvc.perform(measurements(round.slot("b") + "-1", round.challengeId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Slot " + round.slot("b") + " was cancelled; it takes no results"));
        mvc.perform(cancellation(round.id(), round.slot("b"), """
                        {"actorId": "org-life", "reason": "Otra vez"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void aSlotWithResultsIsNotCancelledAndUnknownRoundsOrSlotsAreNotFound() throws Exception {
        Scheduled round = round();
        mvc.perform(measurements(round.slot("a") + "-1", round.challengeId())).andExpect(status().isOk());

        mvc.perform(cancellation(round.id(), round.slot("a"), """
                        {"actorId": "org-life", "reason": "Tarde"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Slot " + round.slot("a")
                        + " already has results; disqualify its attempt instead"));
        mvc.perform(cancellation(round.id(), "slot-missing", """
                        {"actorId": "org-life", "reason": "x"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Slot slot-missing not found in round " + round.id()));
        mvc.perform(cancellation("round-missing", round.slot("a"), """
                        {"actorId": "org-life", "reason": "x"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Round not found: round-missing"));
        mvc.perform(cancellation(round.id(), round.slot("b"), """
                        {"actorId": "org-life"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing field 'reason'"));
        mvc.perform(post("/rounds/{roundId}/start", "round-missing")).andExpect(status().isNotFound());
    }

    @Test
    void theRoundAndItsSlotsGoThroughTheirLifecycle() throws Exception {
        Scheduled round = round();

        mvc.perform(post("/rounds/{roundId}/slots/{slotId}/start", round.id(), round.slot("a")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Expected round IN_PROGRESS, was SCHEDULED"));
        mvc.perform(post("/rounds/{roundId}/start", round.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/rounds/{roundId}/slots/{slotId}/start", round.id(), round.slot("a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].status").value("IN_PROGRESS"));
        mvc.perform(post("/rounds/{roundId}/slots/{slotId}/completion", round.id(), round.slot("a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].status").value("COMPLETED"));
        mvc.perform(post("/rounds/{roundId}/completion", round.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("All slots must be completed or cancelled"));
        mvc.perform(cancellation(round.id(), round.slot("b"), """
                        {"actorId": "org-life", "reason": "No se presentó"}"""))
                .andExpect(status().isOk());
        mvc.perform(post("/rounds/{roundId}/completion", round.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private Scheduled round() {
        ensureEdition();
        String suffix = String.valueOf(NEXT.incrementAndGet());
        String challenge = "ch-api-life-" + suffix;
        challenges.save(Challenge.draft(ChallengeId.of(challenge), EditionId.of(EDITION), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        Scheduled scheduled = new Scheduled("round-api-life-" + suffix, challenge);
        RoundId roundId = RoundId.of(scheduled.id());
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-api-life"), "Pista", "Madera"),
                List.of(Judge.of(JudgeId.of(JUDGE), "Juez", "General")));
        rounds.save(Round.of(RoundInfo.of(roundId, "Ronda",
                        RoundScope.of(ChallengeId.of(challenge), EditionId.of(EDITION), CategoryId.of(CATEGORY), 1)))
                .addSlot(new Slot(new SlotIdentity(SlotId.of(scheduled.slot("a")), roundId, TeamId.of("team-life-a")),
                        assignment, new TimeWindow(START, START.plusMinutes(10))))
                .addSlot(new Slot(new SlotIdentity(SlotId.of(scheduled.slot("b")), roundId, TeamId.of("team-life-b")),
                        assignment, new TimeWindow(START.plusMinutes(10), START.plusMinutes(20)))));
        return scheduled;
    }

    private void ensureEdition() {
        if (editions.findById(EditionId.of(EDITION)).isEmpty()) {
            editions.save(Edition.of(EditionId.of(EDITION), Tournament.of("tor-api-life", "Torneo",
                            new Season("s-api-life", 2026, "2026")), 1, "Edicion API",
                    LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                    List.of(Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500))));
        }
    }

    private static RequestBuilder cancellation(String roundId, String slotId, String body) {
        return post("/rounds/{roundId}/slots/{slotId}/cancellation", roundId, slotId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestBuilder measurements(String attemptId, String challengeId) {
        return put("/attempts/{attemptId}/measurements", attemptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"challengeId": "%s", "judgeId": "%s", "timeSeconds": 40, "objectives": 2,
                         "penalties": 0, "consumption": 0, "measurements": {}}
                        """.formatted(challengeId, JUDGE));
    }

    private record Scheduled(String id, String challengeId) {
        String slot(String team) {
            return id + "-slot-" + team;
        }
    }
}
