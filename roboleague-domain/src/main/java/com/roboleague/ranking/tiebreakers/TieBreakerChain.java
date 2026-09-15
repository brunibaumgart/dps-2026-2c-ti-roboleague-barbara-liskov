package com.roboleague.ranking.tiebreakers;

import com.roboleague.ranking.TeamScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Functional composition of tie-breaker comparators.
 * Allows decoupling and configurable ordering rules per category or tournament edition.
 */
public class TieBreakerChain implements Comparator<TeamScore> {

    public enum StandardCriterion {
        HIGHEST_TOTAL_SCORE("Mayor Puntaje Total",
                Comparator.comparingDouble(TeamScore::totalScore).reversed()),

        LOWEST_TIME_TAKEN("Menor Tiempo Empleado",
                Comparator.comparingDouble(TeamScore::bestAttemptTime)),

        LOWEST_PENALTIES("Menor Cantidad de Penalizaciones",
                Comparator.comparingInt(TeamScore::totalPenalties)),

        HIGHEST_JUDGE_SCORE("Mayor Puntuación de Jueces",
                Comparator.comparingDouble(TeamScore::judgeSubjectiveScore).reversed());

        private final String description;
        private final Comparator<TeamScore> comparator;

        StandardCriterion(String description, Comparator<TeamScore> comparator) {
            this.description = description;
            this.comparator = comparator;
        }

        public String getDescription() {
            return description;
        }

        public Comparator<TeamScore> getComparator() {
            return comparator;
        }
    }

    private final List<Comparator<TeamScore>> criteria;

    public TieBreakerChain() {
        this.criteria = new ArrayList<>();
    }

    public static TieBreakerChain defaultRules() {
        TieBreakerChain chain = new TieBreakerChain();
        chain.addCriterion(StandardCriterion.HIGHEST_TOTAL_SCORE.getComparator());
        chain.addCriterion(StandardCriterion.LOWEST_TIME_TAKEN.getComparator());
        chain.addCriterion(StandardCriterion.LOWEST_PENALTIES.getComparator());
        chain.addCriterion(StandardCriterion.HIGHEST_JUDGE_SCORE.getComparator());
        return chain;
    }

    public TieBreakerChain addCriterion(Comparator<TeamScore> criterion) {
        this.criteria.add(criterion);
        return this;
    }

    @Override
    public int compare(TeamScore o1, TeamScore o2) {
        for (Comparator<TeamScore> criterion : criteria) {
            int result = criterion.compare(o1, o2);
            if (result != 0) {
                return result;
            }
        }
        // Fallback deterministic tie-break by team id
        return o1.teamId().compareTo(o2.teamId());
    }
}
