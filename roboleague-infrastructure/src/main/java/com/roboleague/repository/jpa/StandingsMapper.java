package com.roboleague.repository.jpa;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.OfficialPublication;
import com.roboleague.ranking.Placement;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsTable;
import com.roboleague.ranking.StandingsVersion;
import com.roboleague.ranking.TeamIdentity;
import com.roboleague.ranking.TeamRounds;
import com.roboleague.repository.jpa.StandingsJpaEntity.EntryJson;
import com.roboleague.repository.jpa.StandingsJpaEntity.PublicationJson;
import com.roboleague.repository.jpa.StandingsJpaEntity.RoundJson;
import com.roboleague.repository.jpa.StandingsJpaEntity.VersionJson;
import com.roboleague.scheduling.RoundId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Anti-corruption layer between the standings table and the {@link Standings} aggregate.
 */
final class StandingsMapper {

    private StandingsMapper() {
    }

    static StandingsJpaEntity toEntity(Standings standings) {
        StandingsJpaEntity entity = new StandingsJpaEntity();
        entity.id = standings.getId().value();
        entity.challengeId = standings.getId().challengeId().value();
        entity.categoryId = standings.getId().categoryId().value();
        entity.versions = standings.versions().stream().map(StandingsMapper::toJson).toList();
        entity.publications = standings.publications().stream().map(StandingsMapper::toJson).toList();
        return entity;
    }

    static Standings toDomain(StandingsJpaEntity entity) {
        return Standings.restore(
                new StandingsId(ChallengeId.of(entity.challengeId), CategoryId.of(entity.categoryId)),
                entity.versions.stream().map(StandingsMapper::toVersion).toList(),
                entity.publications.stream().map(StandingsMapper::toPublication).toList());
    }

    private static VersionJson toJson(StandingsVersion version) {
        return new VersionJson(version.number(), version.calculatedAt().toString(),
                version.table().entries().stream().map(StandingsMapper::toJson).toList());
    }

    private static EntryJson toJson(StandingsEntry entry) {
        TeamRounds rounds = entry.rounds();
        return new EntryJson(entry.position(), entry.placement().explanation(), entry.team().teamId().value(),
                entry.team().teamName(), rounds.considered().stream().map(StandingsMapper::toJson).toList(),
                rounds.discarded().stream().map(StandingsMapper::toJson).toList(), rounds.selectionRule());
    }

    private static RoundJson toJson(RoundResult round) {
        return new RoundJson(round.roundId().value(), round.attemptId().value(), round.total());
    }

    private static PublicationJson toJson(OfficialPublication publication) {
        return new PublicationJson(publication.version(), publication.publishedAt().toString(),
                publication.note().authorId().value(), publication.note().reason());
    }

    private static StandingsVersion toVersion(VersionJson json) {
        return new StandingsVersion(json.number(), LocalDateTime.parse(json.calculatedAt()),
                new StandingsTable(json.entries().stream().map(StandingsMapper::toEntry).toList()));
    }

    private static StandingsEntry toEntry(EntryJson json) {
        return new StandingsEntry(new Placement(json.position(), json.explanation()),
                TeamIdentity.of(TeamId.of(json.teamId()), json.teamName()),
                new TeamRounds(toRounds(json.considered()), toRounds(json.discarded()), json.selectionRule()));
    }

    private static List<RoundResult> toRounds(List<RoundJson> rounds) {
        return rounds.stream()
                .map(round -> new RoundResult(RoundId.of(round.roundId()), AttemptId.parse(round.attemptId()),
                        round.total()))
                .toList();
    }

    private static OfficialPublication toPublication(PublicationJson json) {
        return new OfficialPublication(json.version(), LocalDateTime.parse(json.publishedAt()),
                AuditNote.of(ActorId.of(json.authorId()), json.reason()));
    }
}
