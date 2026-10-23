package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;

import java.util.List;
import java.util.Objects;

/**
 * What a turn ran and what it takes next: its attempts in the order the domain opened them, the rulebook its
 * category plays with, and whether the turn still takes a result (it was not cancelled, and the last attempt awaits a
 * source or that rulebook admits another attempt).
 */
public record TurnResults(List<Attempt> attempts, Rulebook rulebook, boolean acceptsResult) {
    public TurnResults {
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts cannot be null"));
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
    }
}
