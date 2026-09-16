package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoringPolicy;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveAppealUseCaseTest {

    @Test
    @DisplayName("Rejecting an appeal keeps the original score and releases the attempt from appeal")
    void rejectedAppealKeepsScoreAndRestoresAttempt() {
        InMemoryAttemptRepository attemptRepository = new InMemoryAttemptRepository();
        InMemoryAppealRepository appealRepository = new InMemoryAppealRepository();
        InMemoryEditionRepository editionRepository = new InMemoryEditionRepository();
        RecalculateRankingUseCase recalculate = new RecalculateRankingUseCase(
                editionRepository, attemptRepository, new InMemoryRankingRepository(), new RankingCalculatorService()
        );
        ResolveAppealUseCase useCase = new ResolveAppealUseCase(appealRepository, attemptRepository, editionRepository, recalculate);

        ScoringPolicy policy = ScoringPolicy.of("pol", "v1", "Reglamento", List.of(
                TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0),
                new PenaltyRule("Faltas", 10.0)
        ));
        Attempt attempt = Attempt.of("att-1", "t-1", "slot-1", "r-1", 1);
        RawMetrics metrics = RawMetrics.of(40.0, 2, 3);
        attempt.registerInitialResult(metrics, policy.evaluate(metrics), "judge-1");
        attemptRepository.save(attempt);

        Appeal appeal = new FileAppealUseCase(attemptRepository, appealRepository)
                .execute("att-1", "t-1", "Faltas mal contadas", "Video");
        new ReviewAppealUseCase(appealRepository).execute(appeal.getAppealId(), "arb-1");

        Appeal resolved = useCase.rejectAppeal(appeal.getAppealId(), "El video confirma las faltas", "arb-1");

        assertThat(resolved.isRejected()).isTrue();
        Attempt stored = attemptRepository.findById("att-1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(stored.getRevisionHistory()).hasSize(1);
        // 40s => 100 + 20 bonus; 3 fouls => -30
        assertThat(stored.getFinalScore()).isEqualTo(90.0);
    }
}
