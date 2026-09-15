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

    private final String rankingId;
    private final String editionId;
    private final String categoryId;
    private final String roundId;
    private RankingStatus status;
    private final LocalDateTime generatedAt;
    private LocalDateTime publishedAt;
    private String publicationNotes;
    private final List<RankingEntry> entries;

    public Ranking(String rankingId, String editionId, String categoryId, String roundId, List<RankingEntry> entries) {
        this.rankingId = Objects.requireNonNull(rankingId, "rankingId cannot be null");
        this.editionId = Objects.requireNonNull(editionId, "editionId cannot be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId cannot be null");
        this.roundId = roundId != null ? roundId : "";
        this.entries = entries != null ? List.copyOf(entries) : List.of();
        this.status = RankingStatus.PROVISIONAL;
        this.generatedAt = LocalDateTime.now();
    }

    public String getRankingId() {
        return rankingId;
    }

    public String getEditionId() {
        return editionId;
    }

    public String getCategoryId() {
        return categoryId;
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
}
