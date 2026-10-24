package com.roboleague.api.attempt;

import com.roboleague.scheduling.SlotId;
import com.roboleague.usecase.TurnResults;

import java.util.List;

/**
 * JSON view of a turn for whoever captures: its attempts in the order the domain opened them, the rulebook version
 * its category plays with, how many attempts that version admits and which counts, and whether the turn still takes
 * a result. The capture form shows these instead of working them out.
 */
record TurnResultsDto(String slotId, int rulebookVersion, String attemptRule, boolean acceptsResult,
                      List<AttemptDto> attempts) {

    static TurnResultsDto from(SlotId slotId, TurnResults turn) {
        return new TurnResultsDto(slotId.value(), turn.rulebook().version().number(),
                turn.rulebook().rankingScheme().attemptSelection().describe(), turn.acceptsResult(),
                turn.attempts().stream().map(AttemptDto::from).toList());
    }
}
