package com.roboleague.api.standings;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.OfficialPublication;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsVersion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON view of one version of the standings with its rows: the rulebook version that ranked it, each team's place,
 * why it is there, which rounds counted and which were discarded, and which attempt of each turn counted.
 */
record StandingsVersionDto(int number, LocalDateTime calculatedAt, String status, PublicationDto publication,
                           int rulebookVersion, List<EntryDto> entries) {

    static StandingsVersionDto from(Standings standings, StandingsVersion version) {
        Summary summary = Summary.from(standings, version);
        return new StandingsVersionDto(summary.number(), summary.calculatedAt(), summary.status(),
                summary.publication(), version.table().rulebookVersion().number(),
                version.table().entries().stream().map(EntryDto::from).toList());
    }

    /**
     * A version without its rows, for the list of versions.
     */
    record Summary(int number, LocalDateTime calculatedAt, String status, PublicationDto publication) {

        static Summary from(Standings standings, StandingsVersion version) {
            return new Summary(version.number(), version.calculatedAt(), standings.statusOf(version).name(),
                    standings.publicationOf(version).map(PublicationDto::from).orElse(null));
        }
    }

    record PublicationDto(LocalDateTime publishedAt, String publishedBy, String notes) {

        static PublicationDto from(OfficialPublication publication) {
            return new PublicationDto(publication.publishedAt(), publication.note().authorId().value(),
                    publication.note().reason());
        }
    }

    record EntryDto(int position, String explanation, String teamId, String teamName, double total,
                    String selectionRule, String attemptRule, List<RoundDto> considered, List<RoundDto> discarded) {

        static EntryDto from(StandingsEntry entry) {
            return new EntryDto(entry.position(), entry.placement().explanation(), entry.team().teamId().value(),
                    entry.team().teamName(), entry.total().doubleValue(), entry.rounds().selectionRule(),
                    entry.rounds().attemptRule(),
                    entry.rounds().considered().stream().map(RoundDto::from).toList(),
                    entry.rounds().discarded().stream().map(RoundDto::from).toList());
        }
    }

    /**
     * A round of a team: the attempt that counted, its score, and the other attempts of the turn, which did not.
     */
    record RoundDto(String roundId, String attemptId, double total, List<String> otherAttempts) {

        static RoundDto from(RoundResult round) {
            return new RoundDto(round.roundId().value(), round.attemptId().value(), round.total().doubleValue(),
                    round.otherAttempts().stream().map(AttemptId::value).toList());
        }
    }
}
