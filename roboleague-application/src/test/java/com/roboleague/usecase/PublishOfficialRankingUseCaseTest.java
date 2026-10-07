package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishOfficialRankingUseCaseTest {

    private static final Rulebook RULEBOOK = new Rulebook(RulebookVersion.first(),
            ScoringScheme.withoutBonuses(List.of(TimeBasedRule.standard(100.0, 60.0)), List.of()),
            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

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

    private void saveAppealedAttempt(String slotId, String teamId, String roundId) {
        AttemptId attemptId = AttemptId.of(slotId, 1);
        Attempt attempt = Attempt.of(new AttemptIdentity(attemptId, roundId, teamId), RulebookReference.of("ch-1", RULEBOOK));
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(30.0, 1, 0), 0.0, Map.of()), "judge-1"),
                RULEBOOK);
        attempt.markUnderAppeal();
        attemptRepo.save(attempt);
        appealRepo.save(Appeal.of("app-" + slotId, attemptId.value(), teamId, "Revision", ""));
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
        saveAppealedAttempt("slot-1", "t-1", "r-1");

        assertThatThrownBy(() -> useCase.execute("rank-1", "Cierre"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appeal(s) remain unresolved");
    }

    @Test
    @DisplayName("Appeals from other rounds do not block this ranking's publication")
    void ignoresAppealsFromOtherRounds() {
        saveAppealedAttempt("slot-other", "t-1", "r-2");

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
