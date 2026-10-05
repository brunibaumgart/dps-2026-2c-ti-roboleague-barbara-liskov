package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.definition.RulebookDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * How a rulebook ranks teams in its challenge: which rounds count (F1) and the ordered tie-break chain.
 * Each criterion names itself when it decides an order.
 */
public record RankingScheme(RoundSelection roundSelection, List<TieBreakCriterion> criteria)
        implements Comparator<ChallengeScore> {

    public RankingScheme {
        Objects.requireNonNull(roundSelection, "roundSelection cannot be null");
        criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria cannot be null"));
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("a ranking scheme needs at least one criterion");
        }
    }

    public RulebookDefinition.Ranking definition() {
        List<String> codes = new ArrayList<>();
        for (TieBreakCriterion criterion : criteria) {
            codes.add(criterion.code());
        }
        return new RulebookDefinition.Ranking(roundSelection.definition(), codes);
    }

    public TieBreakDecision decide(ChallengeScore a, ChallengeScore b) {
        for (TieBreakCriterion criterion : criteria) {
            int order = criterion.compare(a, b);
            if (order != 0) {
                return new TieBreakDecision.DecidedBy(criterion.name(), order);
            }
        }
        return new TieBreakDecision.Tied();
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return switch (decide(a, b)) {
            case TieBreakDecision.DecidedBy decided -> decided.order();
            case TieBreakDecision.Tied() -> 0;
        };
    }
}
