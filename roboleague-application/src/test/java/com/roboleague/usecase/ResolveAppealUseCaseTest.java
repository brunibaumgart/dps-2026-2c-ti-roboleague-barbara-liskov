package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
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
import java.util.Optional;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;

class ResolveAppealUseCaseTest {

    @Test
    @DisplayName("Rejecting an appeal keeps the original score and releases the attempt from appeal")
    void rejectedAppealKeepsScoreAndRestoresAttempt() {
        InMemoryAttemptRepository attemptRepository = new InMemoryAttemptRepository();
        InMemoryAppealRepository appealRepository = new InMemoryAppealRepository();
        InMemoryEditionRepository editionRepository = new InMemoryEditionRepository();
        RecalculateRankingUseCase recalculate = new RecalculateRankingUseCase(
                editionRepository, attemptRepository, new InMemoryRankingRepository(), new RankingCalculatorService(),
        CLOCK, ids());
        ResolveAppealUseCase useCase = new ResolveAppealUseCase(appealRepository, attemptRepository, new InMemoryChallengeRepository(), recalculate, CLOCK, ids());

        Rulebook rulebook = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(
                List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                List.of(new PenaltyRule("Faltas", 10.0))
        ), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        AttemptId attemptId = AttemptId.of(SlotId.of("slot-1"), 1);
        Attempt attempt = Attempt.of(new AttemptIdentity(attemptId, RoundId.of("r-1"), TeamId.of("t-1")), RulebookReference.of(ChallengeId.of("ch-1"), rulebook));
        attempt.receive(sensors(40.0, 2, 3, "judge-1"), rulebook, audit());
        attemptRepository.save(attempt);

        Appeal appeal = new FileAppealUseCase(attemptRepository, appealRepository, CLOCK, ids())
                .execute(attemptId, TeamId.of("t-1"), "Faltas mal contadas", "Video");
        new ReviewAppealUseCase(appealRepository).execute(appeal.getAppealId(), ActorId.of("arb-1"));

        Appeal resolved = useCase.rejectAppeal(appeal.getAppealId(), "El video confirma las faltas", ActorId.of("arb-1"));

        assertThat(resolved.isRejected()).isTrue();
        Attempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(stored.getRevisionHistory()).hasSize(1);
        // 40s => 100 + 20 bonus; 3 fouls => -30
        assertThat(stored.getFinalScore()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("Hallazgo 2: aceptar una apelación puntúa con la versión del intento aunque el desafío haya publicado otra")
    void acceptedAppealScoresWithTheAttemptsOwnRulebookVersion() {
        InMemoryAttemptRepository attemptRepository = new InMemoryAttemptRepository();
        InMemoryAppealRepository appealRepository = new InMemoryAppealRepository();
        InMemoryEditionRepository editionRepository = new InMemoryEditionRepository();
        InMemoryChallengeRepository challengeRepository = new InMemoryChallengeRepository();
        Category maze = Category.of(CategoryId.of("cat-maze"), "Laberinto", 2, 4, 15, 25, 2500);
        editionRepository.save(Edition.of(EditionId.of("ed-1"), Tournament.of("tor-1", "Torneo", "Desc", new Season("s-1", 2026, "2026")),
                1, "Edicion 1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), List.of(maze)));
        Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), EditionId.of("ed-1"), "Laberinto")
                .publish(scoringWithFaultsWorth(10.0), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        challengeRepository.save(challenge);
        ResolveAppealUseCase useCase = new ResolveAppealUseCase(appealRepository, attemptRepository, challengeRepository,
                new RecalculateRankingUseCase(editionRepository, attemptRepository, new InMemoryRankingRepository(),
                        new RankingCalculatorService(), CLOCK, ids()), CLOCK, ids());

        AttemptId attemptId = AttemptId.of(SlotId.of("slot-1"), 1);
        Rulebook first = challenge.currentRulebook();
        Attempt attempt = Attempt.of(new AttemptIdentity(attemptId, RoundId.of("r-1"), TeamId.of("t-1")), RulebookReference.of(ChallengeId.of("ch-1"), first));
        attempt.receive(sensors(40.0, 2, 3, "judge-1"), first, audit());
        attemptRepository.save(attempt);
        challenge.publish(scoringWithFaultsWorth(100.0), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        Appeal appeal = new FileAppealUseCase(attemptRepository, appealRepository, CLOCK, ids())
                .execute(attemptId, TeamId.of("t-1"), "Faltas mal contadas", "Video");
        new ReviewAppealUseCase(appealRepository).execute(appeal.getAppealId(), ActorId.of("arb-1"));

        useCase.acceptAppeal(appeal.getAppealId(), CategoryId.of("cat-maze"), Optional.of(RoundId.of("r-1")), "Era una sola falta",
                RawMetrics.of(40.0, 2, 1), ActorId.of("arb-1"));

        Attempt stored = attemptRepository.findById(attemptId).orElseThrow();
        // 40s => 100 + 20; one fault at 10 => 110 with v1 (v2 would charge 100 for it)
        assertThat(stored.getFinalScore()).isEqualTo(110.0);
        assertThat(stored.getLatestSnapshot().rulebookVersion()).isEqualTo(RulebookVersion.first());
    }

    @Test
    @DisplayName("Hallazgo 3: con dos apelaciones abiertas, rechazar una deja al intento en apelación")
    void rejectingOneOfTwoOpenAppealsKeepsTheAttemptUnderAppeal() {
        InMemoryAttemptRepository attemptRepository = new InMemoryAttemptRepository();
        InMemoryAppealRepository appealRepository = new InMemoryAppealRepository();
        ResolveAppealUseCase useCase = new ResolveAppealUseCase(appealRepository, attemptRepository,
                new InMemoryChallengeRepository(), new RecalculateRankingUseCase(new InMemoryEditionRepository(),
                attemptRepository, new InMemoryRankingRepository(), new RankingCalculatorService(), CLOCK, ids()), CLOCK, ids());
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), scoringWithFaultsWorth(10.0),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        AttemptId attemptId = AttemptId.of(SlotId.of("slot-1"), 1);
        Attempt attempt = Attempt.of(new AttemptIdentity(attemptId, RoundId.of("r-1"), TeamId.of("t-1")), RulebookReference.of(ChallengeId.of("ch-1"), rulebook));
        attempt.receive(sensors(55.0, 4, 0, "j-1"), rulebook, audit());
        attemptRepository.save(attempt);
        FileAppealUseCase fileAppeal = new FileAppealUseCase(attemptRepository, appealRepository, CLOCK, ids());
        Appeal first = fileAppeal.execute(attemptId, TeamId.of("t-1"), "tiempo", "video");
        Appeal second = fileAppeal.execute(attemptId, TeamId.of("t-1"), "objetivos", "video");
        new ReviewAppealUseCase(appealRepository).execute(first.getAppealId(), ActorId.of("arbitro"));

        useCase.rejectAppeal(first.getAppealId(), "sin evidencia", ActorId.of("arbitro"));

        assertThat(second.isPending()).isTrue();
        assertThat(attemptRepository.findById(attemptId).orElseThrow().getStatus())
                .isEqualTo(Attempt.AttemptStatus.UNDER_APPEAL);
    }

    private static SourceDelivery sensors(double seconds, int objectives, int faults, String judgeId) {
        return new SourceDelivery(new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of()),
                JudgeId.of(judgeId));
    }

    private static ScoringScheme scoringWithFaultsWorth(double deductionPerFault) {
        return ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                List.of(new PenaltyRule("Faltas", deductionPerFault)));
    }
}
