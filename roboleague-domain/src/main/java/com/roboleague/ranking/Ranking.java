package com.roboleague.ranking;

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
    private final String roundId;
    private RankingStatus status;
    private final LocalDateTime generatedAt;
    private LocalDateTime publishedAt;
    private String publicationNotes;
    private final List<RankingEntry> entries;

    public Ranking(RankingScope scope, String roundId, List<RankingEntry> entries) {
        this.scope = Objects.requireNonNull(scope, "scope cannot be null");
        this.roundId = roundId != null ? roundId : "";
        this.entries = entries != null ? List.copyOf(entries) : List.of();
        this.status = RankingStatus.PROVISIONAL;
        this.generatedAt = LocalDateTime.now();
    }

    public RankingScope getScope() {
        return scope;
    }

    public String getRankingId() {
        return scope.rankingId();
    }

    public String getEditionId() {
        return scope.editionId();
    }

    public String getCategoryId() {
        return scope.categoryId();
    }

    public String getRoundId() {
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

    public Optional<RankingEntry> getEntryForTeam(String teamId) {
        return entries.stream().filter(e -> e.teamScore().teamId().equals(teamId)).findFirst();
    }

    public void publishOfficial(String notes) {
        this.status = RankingStatus.OFFICIAL;
        this.publishedAt = LocalDateTime.now();
        this.publicationNotes = notes != null ? notes : "Official ranking published.";
    }

    public static Ranking of(RankingScope scope, String roundId, List<RankingEntry> entries) {
        return new Ranking(scope, roundId, entries);
    }

    public static Ranking of(String rankingId, String editionId, String categoryId, String roundId, List<RankingEntry> entries) {
        return new Ranking(new RankingScope(rankingId, editionId, categoryId), roundId, entries);
    }
}
