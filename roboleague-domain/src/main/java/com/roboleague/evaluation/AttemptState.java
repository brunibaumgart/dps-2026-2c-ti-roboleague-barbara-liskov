package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * State pattern for the attempt lifecycle. Each state decides which changes it accepts and which state follows;
 * what it does not accept is refused with the state it is in.
 */
interface AttemptState {

    AttemptStatus status();

    /**
     * Whether the attempt's latest score counts for the standings.
     */
    default boolean counts() {
        return false;
    }

    /**
     * A source of results arrived; {@code lastOne} says whether it was the last one the rulebook needs.
     */
    default AttemptState sourceReceived(boolean lastOne) {
        throw refused("receive results; corrections go through a fault adjustment or an appeal");
    }

    default AttemptState appealFiled() {
        throw refused("be appealed");
    }

    default AttemptState appealRejected() {
        throw refused("close an appeal: it has none open");
    }

    default AttemptState appealAccepted() {
        throw refused("close an appeal: it has none open");
    }

    default AttemptState faultsAdjusted() {
        throw refused("have its faults adjusted");
    }

    default AttemptState disqualified() {
        throw refused("be disqualified");
    }

    private IllegalStateException refused(String change) {
        return new IllegalStateException("attempt is " + status().label() + ": it cannot " + change);
    }
}
