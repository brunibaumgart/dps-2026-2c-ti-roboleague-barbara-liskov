package com.roboleague.ranking;

import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TieBreakerRankingTest {

    private TeamScore createScore(String teamId, String teamName, double totalScore, double bestAttemptTime, int totalPenalties, double judgeScore) {
        return TeamScore.of(
                teamId, teamName, "cat-1", "ed-1",
                PerformanceSummary.of(totalScore, bestAttemptTime, totalPenalties, judgeScore),
                List.of()
        );
    }

    @Test
    @DisplayName("TieBreakerChain prioritizes total score first")
    void tieBreakerTotalScoreFirst() {
        TieBreakerChain chain = TieBreakerChain.defaultRules();

        TeamScore scoreA = createScore("t-1", "Team A", 100.0, 50.0, 1, 8.0);
        TeamScore scoreB = createScore("t-2", "Team B", 120.0, 60.0, 3, 7.0);

        List<TeamScore> list = new ArrayList<>(List.of(scoreA, scoreB));
        list.sort(chain);

        // Team B has higher score (120 > 100), should be first
        assertThat(list.get(0).teamId()).isEqualTo("t-2");
        assertThat(list.get(1).teamId()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("TieBreakerChain uses lowest time taken as second criterion when scores are equal")
    void tieBreakerLowestTimeSecond() {
        TieBreakerChain chain = TieBreakerChain.defaultRules();

        // Equal score (100.0), Team A was faster (40s vs 55s)
        TeamScore scoreA = createScore("t-1", "Team A", 100.0, 40.0, 2, 8.0);
        TeamScore scoreB = createScore("t-2", "Team B", 100.0, 55.0, 0, 9.0);

        List<TeamScore> list = new ArrayList<>(List.of(scoreB, scoreA));
        list.sort(chain);

        assertThat(list.get(0).teamId()).isEqualTo("t-1");
        assertThat(list.get(1).teamId()).isEqualTo("t-2");
    }

    @Test
    @DisplayName("TieBreakerChain uses fewest penalties as third criterion when scores and times are equal")
    void tieBreakerFewestPenaltiesThird() {
        TieBreakerChain chain = TieBreakerChain.defaultRules();

        // Equal score (100.0), equal time (45.0s), Team A has 1 penalty, Team B has 3
        TeamScore scoreA = createScore("t-1", "Team A", 100.0, 45.0, 1, 7.0);
        TeamScore scoreB = createScore("t-2", "Team B", 100.0, 45.0, 3, 9.0);

        List<TeamScore> list = new ArrayList<>(List.of(scoreB, scoreA));
        list.sort(chain);

        assertThat(list.get(0).teamId()).isEqualTo("t-1");
        assertThat(list.get(1).teamId()).isEqualTo("t-2");
    }

    @Test
    @DisplayName("TieBreakerChain uses highest judge score as fourth criterion")
    void tieBreakerJudgeScoreFourth() {
        TieBreakerChain chain = TieBreakerChain.defaultRules();

        // Equal score, time and penalties. Team B has higher judge score (9.5 vs 8.0)
        TeamScore scoreA = createScore("t-1", "Team A", 100.0, 45.0, 1, 8.0);
        TeamScore scoreB = createScore("t-2", "Team B", 100.0, 45.0, 1, 9.5);

        List<TeamScore> list = new ArrayList<>(List.of(scoreA, scoreB));
        list.sort(chain);

        assertThat(list.get(0).teamId()).isEqualTo("t-2");
        assertThat(list.get(1).teamId()).isEqualTo("t-1");
    }
}
