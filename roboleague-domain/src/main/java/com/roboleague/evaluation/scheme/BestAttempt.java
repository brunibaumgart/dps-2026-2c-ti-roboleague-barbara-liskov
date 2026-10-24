package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.List;
import java.util.Optional;

/**
 * The round counts with the best scored attempt of the turn; between two with the same score, the earlier one.
 * An attempt that awaits a source or was disqualified does not count. With one attempt per turn, that attempt.
 */
public record BestAttempt(int perTurn) implements AttemptSelection {
    public static final String TYPE = "best-attempt";
    public static final String PER_TURN = "perTurn";

    public BestAttempt {
        AttemptSelection.requirePositive(perTurn);
    }

    /**
     * One attempt per turn: the default of a rulebook that does not say otherwise.
     */
    public static BestAttempt single() {
        return new BestAttempt(1);
    }

    public static BestAttempt from(StrategyDefinition definition) {
        return new BestAttempt(definition.numbers().whole(PER_TURN));
    }

    @Override
    public StrategyDefinition definition() {
        return new StrategyDefinition(TYPE, Parameters.none().with(PER_TURN, perTurn));
    }

    @Override
    public Optional<Attempt> counted(List<Attempt> attempts) {
        Attempt best = null;
        for (Attempt attempt : attempts) {
            if (attempt.countableScore().isPresent() && (best == null || totalOf(attempt).isGreaterThan(totalOf(best)))) {
                best = attempt;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Points totalOf(Attempt attempt) {
        return attempt.countableScore().orElseThrow().totalScore();
    }

    @Override
    public String describe() {
        return perTurn == 1 ? AttemptSelection.attempts(1)
                : "el mejor de " + AttemptSelection.attempts(perTurn);
    }
}
