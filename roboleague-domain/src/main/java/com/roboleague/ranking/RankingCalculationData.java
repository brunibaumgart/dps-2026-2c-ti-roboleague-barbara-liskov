package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.tournament.Team;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value object grouping candidate teams and their attempts for ranking computation.
 */
public record RankingCalculationData(
        List<Team> teams,
        Map<String, List<Attempt>> attemptsByTeamId
) {
    public RankingCalculationData {
        Objects.requireNonNull(teams, "teams cannot be null");
        Objects.requireNonNull(attemptsByTeamId, "attemptsByTeamId cannot be null");
        teams = Collections.unmodifiableList(teams);
        attemptsByTeamId = Collections.unmodifiableMap(attemptsByTeamId);
    }

    public static RankingCalculationData of(List<Team> teams, Map<String, List<Attempt>> attemptsByTeamId) {
        return new RankingCalculationData(teams, attemptsByTeamId);
    }
}
