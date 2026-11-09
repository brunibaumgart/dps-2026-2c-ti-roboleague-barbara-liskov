package com.roboleague.port.in;

import com.roboleague.scheduling.Round;

/**
 * Use case to schedule a competition round and assign slots, tracks, and judges. Every turn has to fall within the
 * dates of the edition, and the round has to fit the rulebook its category plays with ({@link CategoryRulebook}).
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ScheduleRound {
    Round execute(ScheduleRoundCommand command);
}
