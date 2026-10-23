package com.roboleague.tournament;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Challenge aggregate: one event of an edition (maze, line follower, rescue) with the history of its rulebooks.
 * It always has a rulebook: it is created by publishing the first one. Publishing never edits a previous
 * version, so any result can be recalculated with the version it was scored with. A new version applies to what is
 * played after it: a category that already has results keeps playing and ranking with the version that scored them.
 */
public class Challenge {
    private final ChallengeId id;
    private final EditionId editionId;
    private final String name;
    private final List<Rulebook> rulebooks;

    private Challenge(Draft draft, Rulebook first) {
        this.id = draft.id();
        this.editionId = draft.editionId();
        this.name = draft.name();
        this.rulebooks = new ArrayList<>(List.of(first));
    }

    public static Draft draft(ChallengeId id, EditionId editionId, String name) {
        return new Draft(id, editionId, name);
    }

    /**
     * Rebuilds a stored challenge. Only persistence adapters use it: versions must start at 1 and be consecutive.
     */
    public static Challenge restore(Draft draft, List<Rulebook> rulebooks) {
        if (rulebooks.isEmpty()) {
            throw new IllegalStateException("a stored challenge has at least one rulebook: " + draft.id());
        }
        RulebookVersion expected = RulebookVersion.first();
        for (Rulebook rulebook : rulebooks) {
            if (!rulebook.version().equals(expected)) {
                throw new IllegalStateException("stored rulebooks of " + draft.id() + " skip version " + expected);
            }
            expected = expected.next();
        }
        Challenge challenge = new Challenge(draft, rulebooks.getFirst());
        challenge.rulebooks.addAll(rulebooks.subList(1, rulebooks.size()));
        return challenge;
    }

    /**
     * A challenge that has not published its first rulebook yet, so it cannot score anything.
     */
    public record Draft(ChallengeId id, EditionId editionId, String name) {
        public static final int MAX_NAME_LENGTH = 120;

        public Draft {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(editionId, "editionId cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("challenge name cannot be blank");
            }
            if (name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("challenge name cannot be longer than " + MAX_NAME_LENGTH
                        + " characters");
            }
        }

        public Challenge publish(ScoringScheme scoring, RankingScheme rankingScheme) {
            return new Challenge(this, new Rulebook(RulebookVersion.first(), scoring, rankingScheme));
        }
    }

    public ChallengeId getId() {
        return id;
    }

    public EditionId getEditionId() {
        return editionId;
    }

    public String getName() {
        return name;
    }

    public Rulebook publish(ScoringScheme scoring, RankingScheme rankingScheme) {
        Rulebook rulebook = new Rulebook(currentRulebook().version().next(), scoring, rankingScheme);
        rulebooks.add(rulebook);
        return rulebook;
    }

    public Rulebook currentRulebook() {
        return rulebooks.getLast();
    }

    /**
     * The rulebook a category of this challenge plays and ranks with, given the versions its attempts were scored
     * with: that version once the category has results, the current one before. Results scored with two versions
     * would make a single table impossible, so they are refused.
     */
    public Rulebook rulebookInForce(Collection<RulebookVersion> versionsOfResults) {
        Set<RulebookVersion> versions = Set.copyOf(Objects.requireNonNull(versionsOfResults,
                "versionsOfResults cannot be null"));
        if (versions.isEmpty()) {
            return currentRulebook();
        }
        if (versions.size() > 1) {
            throw new IllegalStateException("results of one category of " + id
                    + " were scored with several rulebook versions: " + versions);
        }
        RulebookVersion version = versions.iterator().next();
        return rulebook(version).orElseThrow(() -> new IllegalStateException(
                "results of " + id + " were scored with " + version + ", which it never published"));
    }

    /**
     * Every published version, oldest first.
     */
    public List<Rulebook> rulebooks() {
        return List.copyOf(rulebooks);
    }

    public Optional<Rulebook> rulebook(RulebookVersion version) {
        return rulebooks.stream()
                .filter(rulebook -> rulebook.version().equals(version))
                .findFirst();
    }
}
