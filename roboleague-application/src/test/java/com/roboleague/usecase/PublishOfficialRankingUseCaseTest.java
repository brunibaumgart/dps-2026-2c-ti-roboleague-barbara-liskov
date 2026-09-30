package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.ranking.PerformanceSummary;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingEntry;
import com.roboleague.ranking.TeamScore;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishOfficialRankingUseCaseTest {

    private InMemoryRankingRepository rankingRepo;
    private InMemoryAppealRepository appealRepo;
    private InMemoryAttemptRepository attemptRepo;
    private PublishOfficialRankingUseCase useCase;

    @BeforeEach
    void setUp() {
        rankingRepo = new InMemoryRankingRepository();
        appealRepo = new InMemoryAppealRepository();
        attemptRepo = new InMemoryAttemptRepository();
        useCase = new PublishOfficialRankingUseCase(rankingRepo, appealRepo, attemptRepo);

        PerformanceSummary perf = PerformanceSummary.of(100, 30, 0, 9.0);
        TeamScore score = TeamScore.of("t-1", "Team", "cat-1", "ed-1", perf, List.of());
        rankingRepo.save(Ranking.of("rank-1", "ed-1", "cat-1", "r-1", List.of(RankingEntry.of(1, score, false, ""))));
    }

    private void saveAppealedAttempt(String attemptId, String teamId, String roundId) {
        Attempt attempt = Attempt.of(attemptId, teamId, "slot-" + attemptId, roundId, 1);
        attempt.registerInitialResult(RawMetrics.of(30.0, 1, 0), ScoreBreakdown.empty(), "judge-1");
        attempt.markUnderAppeal();
        attemptRepo.save(attempt);
        appealRepo.save(Appeal.of("app-" + attemptId, attemptId, teamId, "Revision", ""));
    }

    @Test
    @DisplayName("Successfully publishes official ranking when no pending appeals exist")
    void publishesOfficialWhenNoAppeals() {
        Ranking published = useCase.execute("rank-1", "Cierre oficial validado");

        assertThat(published.isOfficial()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublicationNotes()).isEqualTo("Cierre oficial validado");
    }

    @Test
    @DisplayName("Blocks publishing when a pending appeal targets an attempt of this ranking")
    void blocksPublishingWhenPendingAppealExists() {
        saveAppealedAttempt("att-1", "t-1", "r-1");

        assertThatThrownBy(() -> useCase.execute("rank-1", "Cierre"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appeal(s) remain unresolved");
    }

    @Test
    @DisplayName("Appeals from other rounds do not block this ranking's publication")
    void ignoresAppealsFromOtherRounds() {
        saveAppealedAttempt("att-other", "t-1", "r-2");

        Ranking published = useCase.execute("rank-1", "Cierre de ronda 1");

        assertThat(published.isOfficial()).isTrue();
    }

    @Test
    @DisplayName("An official ranking cannot be published twice")
    void rejectsRepublishing() {
        useCase.execute("rank-1", "Cierre");

        assertThatThrownBy(() -> useCase.execute("rank-1", "Otra vez"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already official");
    }
}
