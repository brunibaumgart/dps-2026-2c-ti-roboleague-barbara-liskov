package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Strategy that says how many attempts a turn admits and which of them counts for the round (F1). The domain
 * numbers a turn's attempts in the order they arrive, so the attempts it receives are in chronological order.
 */
public interface AttemptSelection {

    /**
     * How many attempts one turn admits; at least one.
     */
    int perTurn();

    /**
     * Whether a turn may open its attempt with this (positive) number. Checked before opening one, so a turn never
     * runs more attempts than its rulebook admits.
     */
    default boolean allowsAttempt(int number) {
        return number <= perTurn();
    }

    /**
     * The attempt whose score counts for the round, from the attempts of one turn in the order they were opened.
     * Empty when none of them counts (still awaiting a source, or disqualified).
     */
    Optional<Attempt> counted(List<Attempt> attempts);

    String describe();

    StrategyDefinition definition();

    static int requirePositive(int perTurn) {
        if (perTurn < 1) {
            throw new IllegalArgumentException("a turn admits at least one attempt: " + perTurn);
        }
        return perTurn;
    }

    static String attempts(int perTurn) {
        return perTurn == 1 ? "1 intento por turno" : perTurn + " intentos por turno";
    }
}
