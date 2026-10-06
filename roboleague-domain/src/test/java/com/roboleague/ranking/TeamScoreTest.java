package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamScoreTest {

    private static final Rulebook MAZE = new Rulebook(RulebookVersion.first(),
            ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0),
                    new ObjectivesRule("Objetivos", 20.0)), List.of()),
            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

    @Test
    @DisplayName("Hallazgo 3: un equipo cuyo único intento está descalificado no suma puntos")
    void givenOnlyADisqualifiedAttemptThenTheTeamScoresNothing() {
        Attempt disqualified = scored("slot-1", 55.0, 4);
        disqualified.disqualify("robot fuera de pista", "j-1");

        TeamScore score = TeamScore.fromBestAttempt("t-alpha", "Alpha", "cat-1", "ed-1", List.of(disqualified));

        assertThat(score.totalScore()).isEqualTo(0.0);
    }

    @Test
    void givenADisqualifiedAttemptWithAHigherScoreThenTheTeamScoresWithTheOneThatCounts() {
        Attempt disqualified = scored("slot-1", 55.0, 4);
        disqualified.disqualify("robot fuera de pista", "j-1");
        Attempt counted = scored("slot-2", 60.0, 1);

        TeamScore score = TeamScore.fromBestAttempt("t-alpha", "Alpha", "cat-1", "ed-1", List.of(disqualified, counted));

        assertThat(score.totalScore()).isEqualTo(120.0);
        assertThat(score.bestAttemptTime()).isEqualTo(60.0);
    }

    private static Attempt scored(String slotId, double seconds, int objectives) {
        Attempt attempt = Attempt.of(new AttemptIdentity(AttemptId.of(slotId, 1), "r-1", "t-alpha"),
                RulebookReference.of("ch-maze", MAZE));
        attempt.registerInitialResult(RawMetrics.of(seconds, objectives, 0), "j-1", MAZE);
        return attempt;
    }
}
