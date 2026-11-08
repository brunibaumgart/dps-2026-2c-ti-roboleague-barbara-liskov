package com.roboleague.api.graphql.view;

import com.roboleague.ranking.StandingsEntry;

import java.util.List;

/**
 * GraphQL view of a row of the standings. {@code teamName} comes with the row (no query); {@code team} is resolved
 * in batch only if asked for: the example of a free field next to a field that costs a read.
 */
public record StandingsEntryView(int position, String explanation, String teamId, String teamName, double total,
                                 String selectionRule, String attemptRule, List<RoundResultView> considered,
                                 List<RoundResultView> discarded) {

    public static StandingsEntryView from(StandingsEntry entry) {
        return new StandingsEntryView(entry.position(), entry.placement().explanation(), entry.team().teamId().value(),
                entry.team().teamName(), entry.total().doubleValue(), entry.rounds().selectionRule(),
                entry.rounds().attemptRule(),
                entry.rounds().considered().stream().map(RoundResultView::from).toList(),
                entry.rounds().discarded().stream().map(RoundResultView::from).toList());
    }
}
