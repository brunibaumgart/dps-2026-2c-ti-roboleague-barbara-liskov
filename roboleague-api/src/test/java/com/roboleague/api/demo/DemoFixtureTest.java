package com.roboleague.api.demo;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.ranking.Ranking;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.tournament.ChallengeId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
@Import(PostgresContainer.class)
class DemoFixtureTest {

    @Autowired
    private RankingRepository rankings;

    @Autowired
    private AppealRepository appeals;

    @Autowired
    private ChallengeRepository challenges;

    @Autowired
    private AttemptRepository attempts;

    @Test
    void theDemoEndsWithAnAcceptedAppealAndAnOfficialRanking() {
        assertThat(appeals.findAll()).singleElement().satisfies(appeal -> assertThat(appeal.isAccepted()).isTrue());

        Ranking official = rankings.findLatestByEditionAndCategory("ed-1", "cat-junior").orElseThrow();
        assertThat(official.isOfficial()).isTrue();
        assertThat(official.getEntries().getFirst().teamScore().teamId()).isEqualTo("t-b");
        assertThat(attempts.findById("att-b1").orElseThrow().getOriginalSnapshot().breakdown().totalScore())
                .isEqualTo(197.5);
        assertThat(attempts.findById("att-b1").orElseThrow().getFinalScore()).isEqualTo(257.5);
        assertThat(attempts.findById("att-a1").orElseThrow().getFinalScore()).isEqualTo(235.0);
    }

    @Test
    void theAppealLowersWhatTheDeductionsTookFromTitanTeam() {
        assertThat(attempts.findById("att-b1").orElseThrow().getOriginalSnapshot().breakdown().deducted())
                .isEqualTo(65.0);
        assertThat(attempts.findById("att-b1").orElseThrow().getScoreBreakdown().deducted()).isEqualTo(5.0);
    }

    @Test
    void theDemoConfiguresThreeChallengesThroughTheUseCases() {
        assertThat(challenges.findById(ChallengeId.of("ch-maze"))).isPresent();
        assertThat(challenges.findById(ChallengeId.of("ch-line")).orElseThrow().currentRulebook().version())
                .isEqualTo(new RulebookVersion(2));
        assertThat(challenges.findById(ChallengeId.of("ch-rescue")).orElseThrow().currentRulebook().requiredSources())
                .containsExactlyInAnyOrder(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
    }

    @Test
    void theMixedChallengeDeclaresWhatTheJudgePanelMeasures() {
        assertThat(challenges.findById(ChallengeId.of("ch-rescue")).orElseThrow().currentRulebook()
                .check(ResultSource.JUDGE_PANEL, Map.of("victimas_rescatadas", 3.0, "rescate_completo", 0.0)))
                .isEqualTo(new MeasurementCheck.Accepted());
    }

    @Test
    void theMazeAttemptsShowTheBonusCapInTheirBreakdown() {
        assertThat(attempts.findById("att-a1").orElseThrow().getScoreBreakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal() == -10.0);
        assertThat(attempts.findById("att-b1").orElseThrow().getOriginalSnapshot().breakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal() == -35.0);
    }
}
