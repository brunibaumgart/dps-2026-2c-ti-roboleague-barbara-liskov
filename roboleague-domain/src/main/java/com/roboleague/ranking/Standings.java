package com.roboleague.ranking;

import com.roboleague.evaluation.audit.AuditNote;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Standings aggregate of a challenge in one category. Every calculation adds a version and none is edited, so a
 * correction never rewrites a table someone already saw. A version stays provisional until it is published; only
 * the latest one can be, with no appeal open, no turn without an outcome and no result changed since it was
 * calculated. Publishing replaces the previous official version, which keeps its publication record.
 */
public class Standings {
    private final StandingsId id;
    private final List<StandingsVersion> versions;
    private final List<OfficialPublication> publications;

    private Standings(StandingsId id, List<StandingsVersion> versions, List<OfficialPublication> publications) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.versions = new ArrayList<>(versions);
        this.publications = new ArrayList<>(publications);
    }

    /**
     * The first calculation of a challenge's standings in a category.
     */
    public static Standings first(StandingsId id, StandingsTable table, LocalDateTime calculatedAt) {
        return new Standings(id, List.of(new StandingsVersion(1, calculatedAt, table)), List.of());
    }

    /**
     * Rebuilds stored standings as they were. Only persistence adapters use it: versions start at 1 and are
     * consecutive, and each publication names an existing version, in the order they were published.
     */
    public static Standings restore(StandingsId id, List<StandingsVersion> versions,
                                    List<OfficialPublication> publications) {
        if (versions.isEmpty()) {
            throw new IllegalStateException("stored standings have at least one version: " + id);
        }
        int expected = 1;
        for (StandingsVersion version : versions) {
            if (version.number() != expected) {
                throw new IllegalStateException("stored standings " + id + " skip version " + expected);
            }
            expected++;
        }
        int lastPublished = 0;
        for (OfficialPublication publication : publications) {
            if (publication.version() <= lastPublished || publication.version() > versions.size()) {
                throw new IllegalStateException("stored standings " + id + " publish v" + publication.version()
                        + " out of order");
            }
            lastPublished = publication.version();
        }
        return new Standings(id, versions, publications);
    }

    public StandingsId getId() {
        return id;
    }

    /**
     * Calculates again with the current results: the new version is provisional and becomes the latest one.
     */
    public StandingsVersion recalculate(StandingsTable table, LocalDateTime calculatedAt) {
        StandingsVersion next = new StandingsVersion(latest().number() + 1, calculatedAt, table);
        versions.add(next);
        return next;
    }

    /**
     * Makes a version official when nothing blocks it; otherwise nothing changes and every reason comes back.
     */
    public StandingsPublication publish(int number, PublicationCheck check, AuditNote note, LocalDateTime publishedAt) {
        Objects.requireNonNull(check, "check cannot be null");
        Objects.requireNonNull(note, "note cannot be null");
        Objects.requireNonNull(publishedAt, "publishedAt cannot be null");
        StandingsVersion target = version(number)
                .orElseThrow(() -> new IllegalArgumentException("Standings " + id + " have no version " + number));
        StandingsVersion latest = latest();
        List<String> reasons = new ArrayList<>();
        if (statusOf(target) == StandingsVersion.Status.OFFICIAL) {
            reasons.add("version " + number + " is already the official one");
        } else if (target.number() != latest.number()) {
            reasons.add("only the latest version (" + latest.number() + ") can be published; version " + number
                    + " is outdated");
        }
        if (!check.current().equals(latest.table())) {
            reasons.add("results changed since version " + latest.number() + " was calculated; recalculate first");
        }
        if (check.openAppeals() > 0) {
            reasons.add(check.openAppeals() + " appeal(s) still open");
        }
        if (check.unfinishedTurns() > 0) {
            reasons.add(check.unfinishedTurns() + " turn(s) without an outcome yet");
        }
        if (!reasons.isEmpty()) {
            return new StandingsPublication.Blocked(reasons);
        }
        publications.add(new OfficialPublication(number, publishedAt, note));
        return new StandingsPublication.Published(target);
    }

    public StandingsVersion latest() {
        return versions.getLast();
    }

    public Optional<StandingsVersion> version(int number) {
        return versions.stream().filter(version -> version.number() == number).findFirst();
    }

    /**
     * Every version, oldest first.
     */
    public List<StandingsVersion> versions() {
        return List.copyOf(versions);
    }

    /**
     * Every publication, oldest first; the last one is the official version.
     */
    public List<OfficialPublication> publications() {
        return List.copyOf(publications);
    }

    public Optional<StandingsVersion> official() {
        if (publications.isEmpty()) {
            return Optional.empty();
        }
        return version(publications.getLast().version());
    }

    public Optional<OfficialPublication> publicationOf(StandingsVersion version) {
        return publications.stream().filter(publication -> publication.version() == version.number()).findFirst();
    }

    public StandingsVersion.Status statusOf(StandingsVersion version) {
        if (official().filter(official -> official.number() == version.number()).isPresent()) {
            return StandingsVersion.Status.OFFICIAL;
        }
        if (publicationOf(version).isPresent()) {
            return StandingsVersion.Status.REPLACED;
        }
        return StandingsVersion.Status.PROVISIONAL;
    }
}
