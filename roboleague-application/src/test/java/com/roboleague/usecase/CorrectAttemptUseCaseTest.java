package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryStandingsRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
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
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class CorrectAttemptUseCaseTest {
    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final AuditNote VIDEO = AuditNote.of(ActorId.of("arb-1"), "Toque de borde visto en video");

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final InMemoryStandingsRepository standings = new InMemoryStandingsRepository();
    private final RecalculateStandingsUseCase recalculate = new RecalculateStandingsUseCase(
            new CategoryResultsReader(challenges, editions, teams, rounds, attempts), standings, CLOCK);
    private final CorrectAttemptUseCase correct = new CorrectAttemptUseCase(attempts, challenges, rounds, recalculate,
            CLOCK, ids());
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());

    private final Category maze = category("maze", 1, 4, 14, 25, 2000);
    private final Edition edition = edition("ed-1", DATE, maze);
    private final Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), edition.getId(), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))),
                            List.of(new PenaltyRule("Faltas", Points.of(5)))),
                    new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    private final StandingsId mazeStandings = new StandingsId(challenge.getId(), maze.id());

    CorrectAttemptUseCaseTest() {
        editions.save(edition);
        RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
        register.execute(edition.getId(), maze.id(), team("team-a"));
        register.execute(edition.getId(), maze.id(), team("team-b"));
        challenges.save(challenge);
        rounds.save(round("team-a", "team-b"));
        score("team-a", 3);
        score("team-b", 2);
        recalculate.execute(mazeStandings);
    }

    @Test
    @DisplayName("Faults seen after the capture add a revision and recalculate the standings as a new version")
    void aPenaltyAdjustmentAddsARevisionAndRecalculatesTheStandings() {
        AttemptCorrection correction = correct.adjustPenalties(attemptOf("team-a"), 5, VIDEO);

        Attempt stored = attempts.findById(attemptOf("team-a")).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Attempt.AttemptStatus.ADJUSTED);
        assertThat(stored.getRevisionHistory()).hasSize(2);
        assertThat(stored.getLatestSnapshot().authorOrJudgeId()).isEqualTo(ActorId.of("arb-1"));
        assertThat(stored.getLatestSnapshot().timestamp()).isEqualTo(TIME);
        assertThat(correction.attempt().getRevisionHistory()).isEqualTo(stored.getRevisionHistory());
        assertThat(correction.standings().number()).isEqualTo(2);
        // 3 objectives = 60, 5 faults = -25: team-b (40) moves ahead
        assertThat(correction.standings().table().entries())
                .extracting(entry -> entry.team().teamId(), StandingsEntry::total)
                .containsExactly(tuple(TeamId.of("team-b"), Points.of(40)), tuple(TeamId.of("team-a"), Points.of(35)));
    }

    @Test
    @DisplayName("A penalty adjustment that is not positive, or on an attempt that does not exist, changes nothing")
    void invalidPenaltyAdjustmentsChangeNothing() {
        assertThatThrownBy(() -> correct.adjustPenalties(attemptOf("team-a"), 0, VIDEO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("additionalPenalties must be positive");
        assertThatThrownBy(() -> correct.adjustPenalties(AttemptId.of(SlotId.of("missing"), 1), 1, VIDEO))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Attempt not found: missing-1");

        assertThat(attempts.findById(attemptOf("team-a")).orElseThrow().getRevisionHistory()).hasSize(1);
        assertThat(standings.findById(mazeStandings).orElseThrow().versions()).hasSize(1);
    }

    @Test
    @DisplayName("A disqualified attempt stops counting: the standings are recalculated without its score")
    void aDisqualifiedAttemptNoLongerCounts() {
        AttemptCorrection correction = correct.disqualify(attemptOf("team-a"), JUDGE, "Robot fuera de reglamento");

        Attempt stored = attempts.findById(attemptOf("team-a")).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Attempt.AttemptStatus.DISQUALIFIED);
        assertThat(stored.countableScore()).isEmpty();
        assertThat(stored.getEventHistory().getLast().description())
                .isEqualTo("Attempt disqualified by j-1: Robot fuera de reglamento");
        assertThat(correction.standings().number()).isEqualTo(2);
        assertThat(correction.standings().table().entries())
                .extracting(entry -> entry.team().teamId(), StandingsEntry::total)
                .containsExactly(tuple(TeamId.of("team-b"), Points.of(40)), tuple(TeamId.of("team-a"), Points.ZERO));
    }

    @Test
    @DisplayName("Only a judge assigned to the turn disqualifies its attempt, and only once")
    void onlyAnAssignedJudgeDisqualifiesAndOnlyOnce() {
        assertThatThrownBy(() -> correct.disqualify(attemptOf("team-a"), JudgeId.of("j-9"), "Ajeno"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessage("judge j-9 is not assigned to slot r-1-team-a");
        assertThat(attempts.findById(attemptOf("team-a")).orElseThrow().getStatus())
                .isEqualTo(Attempt.AttemptStatus.EVALUATED);

        correct.disqualify(attemptOf("team-a"), JUDGE, "Robot fuera de reglamento");
        assertThatThrownBy(() -> correct.disqualify(attemptOf("team-a"), JUDGE, "Otra vez"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt is disqualified: it cannot be disqualified");
        assertThatThrownBy(() -> correct.adjustPenalties(attemptOf("team-a"), 1, VIDEO))
                .isInstanceOf(IllegalStateException.class);
        assertThat(standings.findById(mazeStandings).orElseThrow().versions()).hasSize(2);
    }

    private void score(String teamId, int objectives) {
        Reception reception = receive.execute(new ReceiveResultCommand(challenge.getId(), attemptOf(teamId).slotId(),
                new SourceDelivery(new Measurements(new TrackPerformance(60, objectives, 0), 0.0, Map.of()), JUDGE)));
        assertThat(reception).isInstanceOf(Reception.Received.class);
    }

    private static AttemptId attemptOf(String teamId) {
        return AttemptId.of(SlotId.of("r-1-" + teamId), 1);
    }

    private Round round(String... teamIds) {
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1", RoundScope.of(challenge.getId(),
                edition.getId(), maze.id(), 1)));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-1"), "Pista", "Madera"),
                List.of(Judge.of(JUDGE, "Juez", "General")));
        for (int index = 0; index < teamIds.length; index++) {
            round = round.addSlot(new Slot(new SlotIdentity(SlotId.of("r-1-" + teamIds[index]), round.getId(),
                    TeamId.of(teamIds[index])), assignment,
                    new TimeWindow(TIME.plusMinutes(10L * index), TIME.plusMinutes(10L * index + 5))));
        }
        return round;
    }
}
