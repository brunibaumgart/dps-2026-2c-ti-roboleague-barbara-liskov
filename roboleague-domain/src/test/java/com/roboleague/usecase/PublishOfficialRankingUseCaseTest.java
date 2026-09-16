package com.roboleague.usecase;

import com.roboleague.ranking.PerformanceSummary;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingEntry;
import com.roboleague.ranking.TeamScore;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishOfficialRankingUseCaseTest {

    @Test
    @DisplayName("Successfully publishes official ranking when no pending appeals exist")
    void publishesOfficialWhenNoAppeals() {
        InMemoryRankingRepository rankingRepo = new InMemoryRankingRepository();
        InMemoryAppealRepository appealRepo = new InMemoryAppealRepository();
        PublishOfficialRankingUseCase useCase = new PublishOfficialRankingUseCase(rankingRepo, appealRepo);

        PerformanceSummary perf = PerformanceSummary.of(100, 30, 0, 9.0);
        TeamScore score = TeamScore.of("t-1", "Champion", "cat-1", "ed-1", perf, List.of());
        Ranking ranking = Ranking.of("rank-1", "ed-1", "cat-1", "r-1", List.of(RankingEntry.of(1, score, false, "Ganador")));
        rankingRepo.save(ranking);

        Ranking published = useCase.execute("rank-1", "Cierre oficial validado");

        assertThat(published.isOfficial()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublicationNotes()).isEqualTo("Cierre oficial validado");
    }

    @Test
    @DisplayName("Blocks publishing official ranking when pending appeal exists")
    void blocksPublishingWhenPendingAppealExists() {
        InMemoryRankingRepository rankingRepo = new InMemoryRankingRepository();
        InMemoryAppealRepository appealRepo = new InMemoryAppealRepository();
        PublishOfficialRankingUseCase useCase = new PublishOfficialRankingUseCase(rankingRepo, appealRepo);

        PerformanceSummary perf = PerformanceSummary.of(100, 30, 0, 9.0);
        TeamScore score = TeamScore.of("t-1", "Team", "cat-1", "ed-1", perf, List.of());
        Ranking ranking = Ranking.of("rank-2", "ed-1", "cat-1", "r-1", List.of(RankingEntry.of(1, score, false, "")));
        rankingRepo.save(ranking);

        Appeal appeal = Appeal.of("app-1", "att-1", "t-1", "Revision", "");
        appealRepo.save(appeal);

        assertThatThrownBy(() -> useCase.execute("rank-2", "Cierre"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appeal(s) remain unresolved");
    }
}
