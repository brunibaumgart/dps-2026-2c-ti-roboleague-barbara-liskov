package com.roboleague.api.standings;

import com.roboleague.ranking.PublicationCheck;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.usecase.StandingsOverview;

import java.util.List;

/**
 * JSON view of the standings of a challenge in a category: every version without its rows, the latest and the
 * official one with them, and what would block publishing right now. Before the first calculation there are no
 * versions and both are null.
 */
record StandingsDto(String challengeId, String categoryId, List<StandingsVersionDto.Summary> versions,
                    StandingsVersionDto latest, StandingsVersionDto official, PendingDto pending) {

    static StandingsDto from(StandingsId id, StandingsOverview overview) {
        PendingDto pending = PendingDto.from(overview.now(), overview.outdated());
        return overview.standings()
                .map(standings -> new StandingsDto(id.challengeId().value(), id.categoryId().value(),
                        standings.versions().stream()
                                .map(version -> StandingsVersionDto.Summary.from(standings, version)).toList(),
                        StandingsVersionDto.from(standings, standings.latest()),
                        official(standings), pending))
                .orElseGet(() -> new StandingsDto(id.challengeId().value(), id.categoryId().value(), List.of(),
                        null, null, pending));
    }

    private static StandingsVersionDto official(Standings standings) {
        return standings.official().map(version -> StandingsVersionDto.from(standings, version)).orElse(null);
    }

    /**
     * What the competition has open now and whether the latest version no longer matches the results.
     */
    record PendingDto(int openAppeals, int unfinishedTurns, boolean outdated) {

        static PendingDto from(PublicationCheck now, boolean outdated) {
            return new PendingDto(now.openAppeals(), now.unfinishedTurns(), outdated);
        }
    }
}
