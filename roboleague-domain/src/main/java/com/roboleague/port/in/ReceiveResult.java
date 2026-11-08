package com.roboleague.port.in;

/**
 * Receives what one source sent for a turn: the automatic measurements or the judge panel's scores (F3).
 * The turn has to exist and the judge has to be assigned to it; the team is the slot's. The result goes to the
 * turn's attempt that still awaits a source, scored with its own version; otherwise it opens the next attempt,
 * numbered by the domain in the order it arrives, with the rulebook its category plays with, while that rulebook
 * admits another attempt per turn ({@link TurnAttempts}).
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ReceiveResult {
    Reception execute(ReceiveResultCommand command);
}
