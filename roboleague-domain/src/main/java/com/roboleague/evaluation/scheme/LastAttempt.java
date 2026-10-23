package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.List;
import java.util.Optional;

/**
 * The round counts with the last attempt of the turn that has an outcome, even if an earlier one scored more.
 * If that attempt was disqualified, the round gives nothing: an earlier attempt does not take its place.
 */
public record LastAttempt(int perTurn) implements AttemptSelection {
    public static final String TYPE = "last-attempt";

    public LastAttempt {
        AttemptSelection.requirePositive(perTurn);
    }

    public static LastAttempt from(StrategyDefinition definition) {
        return new LastAttempt(definition.numbers().whole(BestAttempt.PER_TURN));
    }

    @Override
    public StrategyDefinition definition() {
        return new StrategyDefinition(TYPE, Parameters.none().with(BestAttempt.PER_TURN, perTurn));
    }

    @Override
    public Optional<Attempt> counted(List<Attempt> attempts) {
        return attempts.reversed().stream()
                .filter(Attempt::hasOutcome)
                .findFirst()
                .filter(attempt -> attempt.countableScore().isPresent());
    }

    @Override
    public String describe() {
        return "el último de " + AttemptSelection.attempts(perTurn);
    }
}
