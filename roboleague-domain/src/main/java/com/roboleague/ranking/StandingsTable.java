package com.roboleague.ranking;

import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.evaluation.scheme.TieBreakDecision;
import com.roboleague.ranking.TeamAttempts.ScoredTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The ordered rows of a challenge's standings in one category. Each team's total comes from the rounds its
 * rulebook counts (F1) and the order from the rulebook's chain of criteria; each row says which criterion placed
 * it below the previous one, or that they tie on every criterion and share the position.
 */
public record StandingsTable(List<StandingsEntry> entries) {
    public StandingsTable {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries cannot be null"));
    }

    public static StandingsTable rank(RankingScheme scheme, List<TeamAttempts> teams) {
        Objects.requireNonNull(scheme, "scheme cannot be null");
        List<ScoredTeam> scored = new ArrayList<>();
        for (TeamAttempts team : teams) {
            scored.add(team.scoredWith(scheme.roundSelection()));
        }
        scored.sort(Comparator.comparing(ScoredTeam::score, scheme)
                .thenComparing(team -> team.team().teamId()));

        List<StandingsEntry> entries = new ArrayList<>();
        ScoredTeam previous = null;
        Placement previousPlacement = null;
        int rank = 0;
        for (ScoredTeam current : scored) {
            rank++;
            Placement placement = previous == null
                    ? new Placement(1, "Primer puesto")
                    : placed(scheme, previous, previousPlacement, current, rank);
            entries.add(new StandingsEntry(placement, current.team(), current.rounds()));
            previous = current;
            previousPlacement = placement;
        }
        return new StandingsTable(entries);
    }

    private static Placement placed(RankingScheme scheme, ScoredTeam previous, Placement previousPlacement,
                                    ScoredTeam current, int rank) {
        int above = previousPlacement.position();
        return switch (scheme.decide(previous.score(), current.score())) {
            case TieBreakDecision.Tied() ->
                    new Placement(above, "Empata con el puesto " + above + " en todos los criterios");
            case TieBreakDecision.DecidedBy decided when Double.compare(previous.score().total(),
                    current.score().total()) == 0 ->
                    new Placement(rank, "Empata en puntaje con el puesto " + above + "; desempata por "
                            + decided.criterion());
            case TieBreakDecision.DecidedBy decided ->
                    new Placement(rank, "Debajo del puesto " + above + " por " + decided.criterion());
        };
    }
}
