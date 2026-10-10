package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.SourceReport;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryStandingsRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.Tournament;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolveAppealUseCaseTest {
    private static final CategoryId MAZE = CategoryId.of("cat-maze");
    private static final AttemptId ATTEMPT = AttemptId.of(SlotId.of("slot-1"), 1);
    private static final AuditNote ONE_FAULT = AuditNote.of(ActorId.of("arb-1"), "Era una sola falta");

    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final InMemoryAppealRepository appeals = new InMemoryAppealRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryStandingsRepository standings = new InMemoryStandingsRepository();
    private final ResolveAppealUseCase useCase = new ResolveAppealUseCase(appeals, attempts, challenges, rounds,
            new RecalculateStandingsUseCase(new CategoryResultsReader(challenges, editions, new InMemoryTeamRepository(),
                    rounds, attempts), standings, CLOCK), CLOCK, ids());
    private final Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), EditionId.of("ed-1"), "Laberinto")
            .publish(scoringWithFaultsWorth(10.0), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

    ResolveAppealUseCaseTest() {
        editions.save(Edition.of(EditionId.of("ed-1"), Tournament.of("tor-1", "Torneo", "Desc", new Season("s-1", 2026, "2026")),
                1, "Edicion 1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                List.of(Category.of(MAZE, "Laberinto", 2, 4, 15, 25, 2500))));
        challenges.save(challenge);
        rounds.save(Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1",
                RoundScope.of(challenge.getId(), EditionId.of("ed-1"), MAZE, 1))));
    }

    @Test
    @DisplayName("Rejecting an appeal keeps the original score and releases the attempt from appeal")
    void rejectedAppealKeepsScoreAndRestoresAttempt() {
        scoredAttempt(40.0, 2, 3);
        Appeal appeal = appealUnderReview("Faltas mal contadas", "arb-1");

        Appeal resolved = useCase.rejectAppeal(appeal.getAppealId(),
                AuditNote.of(ActorId.of("arb-1"), "El video confirma las faltas"));

        assertThat(resolved.isRejected()).isTrue();
        Attempt stored = attempts.findById(ATTEMPT).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(stored.getRevisionHistory()).hasSize(1);
        // 40s => 100 + 20 bonus; 3 fouls => -30
        assertThat(stored.getFinalScore()).isEqualTo(90.0);
        assertThat(standings.findById(new StandingsId(challenge.getId(), MAZE))).isEmpty();
    }

    @Test
    @DisplayName("Hallazgo 2: aceptar una apelación puntúa con la versión del intento aunque el desafío haya publicado otra")
    void acceptedAppealScoresWithTheAttemptsOwnRulebookVersion() {
        scoredAttempt(40.0, 2, 3);
        challenge.publish(scoringWithFaultsWorth(100.0), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        Appeal appeal = appealUnderReview("Faltas mal contadas", "arb-1");

        AppealAcceptance result = useCase.acceptAppeal(appeal.getAppealId(), ONE_FAULT, List.of(sensors(40.0, 2, 1)));

        Attempt stored = attempts.findById(ATTEMPT).orElseThrow();
        // 40s => 100 + 20; one fault at 10 => 110 with v1 (v2 would charge 100 for it)
        assertThat(stored.getFinalScore()).isEqualTo(110.0);
        assertThat(stored.getLatestSnapshot().rulebookVersion()).isEqualTo(RulebookVersion.first());
        assertThat(result).isInstanceOfSatisfying(AppealAcceptance.Accepted.class, accepted -> {
            assertThat(accepted.appeal().isAccepted()).isTrue();
            assertThat(accepted.appeal().getRevisedMetrics().penaltiesCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Accepting an appeal recalculates the standings of the attempt's category as a new version")
    void acceptingAnAppealRecalculatesTheStandingsOfItsCategory() {
        scoredAttempt(40.0, 2, 3);
        StandingsId id = new StandingsId(challenge.getId(), MAZE);
        new RecalculateStandingsUseCase(new CategoryResultsReader(challenges, editions, new InMemoryTeamRepository(),
                rounds, attempts), standings, CLOCK).execute(id);
        Appeal appeal = appealUnderReview("Faltas mal contadas", "arb-1");

        AppealAcceptance result = useCase.acceptAppeal(appeal.getAppealId(), ONE_FAULT, List.of(sensors(40.0, 2, 1)));

        assertThat(result).isInstanceOfSatisfying(AppealAcceptance.Accepted.class,
                accepted -> assertThat(accepted.standings().number()).isEqualTo(2));
        assertThat(standings.findById(id).orElseThrow().versions()).hasSize(2);
    }

    @Test
    @DisplayName("Corrections that do not fit the attempt's rulebook change nothing and come back as problems")
    void invalidCorrectionsChangeNothing() {
        scoredAttempt(40.0, 2, 3);
        Appeal appeal = appealUnderReview("Faltas mal contadas", "arb-1");
        JudgeScores panel = new JudgeScores(Map.of(JudgeId.of("j-1"), 8.0), Map.of());

        AppealAcceptance noCorrection = useCase.acceptAppeal(appeal.getAppealId(), ONE_FAULT, List.of());
        AppealAcceptance wrongSource = useCase.acceptAppeal(appeal.getAppealId(), ONE_FAULT,
                List.of(panel, sensors(40.0, 2, 1), sensors(40.0, 2, 0)));

        assertThat(noCorrection).isEqualTo(new AppealAcceptance.Invalid(
                List.of("an accepted appeal needs the corrected report of at least one source")));
        assertThat(wrongSource).isEqualTo(new AppealAcceptance.Invalid(List.of(
                "ch-1 v1 takes no results from JUDGE_PANEL", "AUTOMATIC_MEASUREMENTS is corrected twice")));
        assertThat(appeal.isUnderReview()).isTrue();
        assertThat(attempts.findById(ATTEMPT).orElseThrow().getRevisionHistory()).hasSize(1);
        assertThat(standings.findById(new StandingsId(challenge.getId(), MAZE))).isEmpty();
    }

    @Test
    @DisplayName("Only the reviewer who took the appeal accepts it")
    void onlyTheReviewerAcceptsTheAppeal() {
        scoredAttempt(40.0, 2, 3);
        Appeal appeal = appealUnderReview("Faltas mal contadas", "arb-1");

        assertThatThrownBy(() -> useCase.acceptAppeal(appeal.getAppealId(),
                AuditNote.of(ActorId.of("arb-2"), "Corresponde"), List.of(sensors(40.0, 2, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("under review by arb-1");
        assertThat(attempts.findById(ATTEMPT).orElseThrow().getRevisionHistory()).hasSize(1);
    }

    @Test
    @DisplayName("Hallazgo 3: con dos apelaciones abiertas, rechazar una deja al intento en apelación")
    void rejectingOneOfTwoOpenAppealsKeepsTheAttemptUnderAppeal() {
        scoredAttempt(55.0, 4, 0);
        FileAppealUseCase fileAppeal = new FileAppealUseCase(attempts, appeals, CLOCK, ids());
        Appeal first = fileAppeal.execute(ATTEMPT, TeamId.of("t-1"), "tiempo", "video");
        Appeal second = fileAppeal.execute(ATTEMPT, TeamId.of("t-1"), "objetivos", "video");
        new ReviewAppealUseCase(appeals).execute(first.getAppealId(), ActorId.of("arbitro"));

        useCase.rejectAppeal(first.getAppealId(), AuditNote.of(ActorId.of("arbitro"), "sin evidencia"));

        assertThat(second.isPending()).isTrue();
        assertThat(attempts.findById(ATTEMPT).orElseThrow().getStatus())
                .isEqualTo(Attempt.AttemptStatus.UNDER_APPEAL);
    }

    @Test
    @DisplayName("An appeal that does not exist cannot be resolved")
    void aMissingAppealCannotBeResolved() {
        assertThatThrownBy(() -> useCase.rejectAppeal(AppealId.of("missing"), ONE_FAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Appeal not found: missing");
    }

    private void scoredAttempt(double seconds, int objectives, int faults) {
        Rulebook rulebook = challenge.currentRulebook();
        Attempt attempt = Attempt.of(new AttemptIdentity(ATTEMPT, RoundId.of("r-1"), TeamId.of("t-1")),
                RulebookReference.of(challenge.getId(), rulebook));
        attempt.receive(new SourceDelivery(sensors(seconds, objectives, faults), JudgeId.of("judge-1")), rulebook, audit());
        attempts.save(attempt);
    }

    private Appeal appealUnderReview(String reason, String reviewer) {
        Appeal appeal = new FileAppealUseCase(attempts, appeals, CLOCK, ids()).execute(ATTEMPT, TeamId.of("t-1"), reason, "Video");
        new ReviewAppealUseCase(appeals).execute(appeal.getAppealId(), ActorId.of(reviewer));
        return appeal;
    }

    private static SourceReport sensors(double seconds, int objectives, int faults) {
        return new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of());
    }

    private static ScoringScheme scoringWithFaultsWorth(double deductionPerFault) {
        return ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                List.of(new PenaltyRule("Faltas", deductionPerFault)));
    }
}
