package com.roboleague.evaluation.scheme;

/**
 * Outcome of comparing two teams with the rulebook's tie-break chain.
 */
public sealed interface TieBreakDecision permits TieBreakDecision.DecidedBy, TieBreakDecision.Tied {

    record DecidedBy(String criterion, int order) implements TieBreakDecision {
    }

    record Tied() implements TieBreakDecision {
    }
}
