package com.roboleague.ranking;

import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ranking leaderboard for a category/round in a competition edition.
 * Tracks publication state: PROVISIONAL vs OFFICIAL.
 */
public class Ranking {

    public enum RankingStatus {
        PROVISIONAL,
        OFFICIAL
    }

    private final RankingScope scope;
    private final Optional<RoundId> roundId;
    private RankingStatus status;
    private final LocalDateTime generatedAt;
    private LocalDateTime publishedAt;
    private String publicationNotes;
    private final List<RankingEntry> entries;

    public Ranking(RankingScope scope, Optional<RoundId> roundId, List<RankingEntry> entries, LocalDateTime generatedAt) {
        this.scope = Objects.requireNonNull(scope, "scope cannot be null");
        this.roundId = Objects.requireNonNull(roundId, "roundId cannot be null");
        this.entries = entries != null ? List.copyOf(entries) : List.of();
        this.status = RankingStatus.PROVISIONAL;
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt cannot be null");
    }

    public RankingScope getScope() {
        return scope;
    }

    public RankingId getRankingId() {
        return scope.rankingId();
    }

    public EditionId getEditionId() {
        return scope.editionId();
    }

    public CategoryId getCategoryId() {
        return scope.categoryId();
    }

    public Optional<RoundId> getRoundId() {
        return roundId;
    }

    public RankingStatus getStatus() {
        return status;
    }

    public boolean isOfficial() {
        return status == RankingStatus.OFFICIAL;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getPublicationNotes() {
        return publicationNotes;
    }

    public List<RankingEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public Optional<RankingEntry> getEntryForTeam(TeamId teamId) {
        return entries.stream().filter(e -> e.teamScore().teamId().equals(teamId)).findFirst();
    }

    public void publishOfficial(String notes, LocalDateTime publishedAt) {
        Objects.requireNonNull(publishedAt, "publishedAt cannot be null");
        this.status = RankingStatus.OFFICIAL;
        this.publishedAt = publishedAt;
        this.publicationNotes = notes != null ? notes : "Official ranking published.";
    }

    public static Ranking of(RankingScope scope, Optional<RoundId> roundId, List<RankingEntry> entries, LocalDateTime generatedAt) {
        return new Ranking(scope, roundId, entries, generatedAt);
    }

    public static Ranking of(RankingId rankingId, EditionId editionId, CategoryId categoryId, Optional<RoundId> roundId, List<RankingEntry> entries, LocalDateTime generatedAt) {
        return new Ranking(new RankingScope(rankingId, editionId, categoryId), roundId, entries, generatedAt);
    }
}
