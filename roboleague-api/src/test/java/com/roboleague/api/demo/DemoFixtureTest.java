package com.roboleague.api.demo;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.StandingsRepository;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
@Import(PostgresContainer.class)
class DemoFixtureTest {

    @Autowired
    private StandingsRepository standings;

    @Autowired
    private AppealRepository appeals;

    @Autowired
    private ChallengeRepository challenges;

    @Autowired
    private AttemptRepository attempts;

    @Autowired
    private EditionRepository editions;

    @Test
    void theDemoUsesFixedDatesSoEveryRunEndsTheSame() {
        assertThat(editions.findById(EditionId.of("ed-1")).orElseThrow().getDates())
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));
    }

    @Test
    void theDemoEndsWithAnAcceptedAppealAndAnOfficialRanking() {
        assertThat(appeals.findAll()).singleElement().satisfies(appeal -> assertThat(appeal.isAccepted()).isTrue());

        Standings official = standings.findById(new StandingsId(ChallengeId.of("ch-maze"), CategoryId.of("cat-junior")))
                .orElseThrow();
        assertThat(official.official()).isPresent();
        assertThat(official.official().orElseThrow().table().entries().getFirst().team().teamId())
                .isEqualTo(TeamId.of("t-b"));
        assertThat(mazeAttemptOf("t-b").getRevisionHistory().getFirst().breakdown().totalScore())
                .isEqualTo(Points.of(197.5));
        assertThat(mazeAttemptOf("t-b").getScoreBreakdown().totalScore()).isEqualTo(Points.of(257.5));
        assertThat(mazeAttemptOf("t-a").getScoreBreakdown().totalScore()).isEqualTo(Points.of(235));
    }

    @Test
    void theAppealLowersWhatTheDeductionsTookFromTitanTeam() {
        assertThat(mazeAttemptOf("t-b").getRevisionHistory().getFirst().breakdown().deducted())
                .isEqualTo(Points.of(65));
        assertThat(mazeAttemptOf("t-b").getScoreBreakdown().deducted()).isEqualTo(Points.of(5));
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
        assertThat(mazeAttemptOf("t-a").getScoreBreakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal().equals(Points.of(-10)));
        assertThat(mazeAttemptOf("t-b").getRevisionHistory().getFirst().breakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal().equals(Points.of(-35)));
    }

    @Test
    void theMixedRescueHasOneAttemptScoredWithBothSourcesAndOneAwaitingTheJudgePanel() {
        Attempt scored = attemptOf("t-a", "ch-rescue");
        assertThat(scored.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(scored.getDeliveries()).extracting(SourceDelivery::source)
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
        // 3 zones => 45; 3 victims => 75; judges 8 and 7 => 7.5 * 5 = 37.5; one victim abandoned => -10
        assertThat(scored.countableScore()).hasValueSatisfying(score -> assertThat(score.totalScore()).isEqualTo(Points.of(147.5)));

        Attempt awaiting = attemptOf("t-b", "ch-rescue");
        assertThat(awaiting.getStatus()).isEqualTo(Attempt.AttemptStatus.AWAITING_SOURCES);
        assertThat(awaiting.countableScore()).isEmpty();
    }

    private Attempt mazeAttemptOf(String teamId) {
        return attemptOf(teamId, "ch-maze");
    }

    private Attempt attemptOf(String teamId, String challengeId) {
        return attempts.findByTeamId(TeamId.of(teamId)).stream()
                .filter(attempt -> attempt.getRulebookReference().challengeId().equals(ChallengeId.of(challengeId)))
                .findFirst()
                .orElseThrow();
    }
}
