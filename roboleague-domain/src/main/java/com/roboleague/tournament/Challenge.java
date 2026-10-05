package com.roboleague.tournament;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Challenge aggregate: one event of an edition (maze, line follower, rescue) with the history of its rulebooks.
 * It always has a rulebook: it is created by publishing the first one. Publishing never edits a previous
 * version, so any result can be recalculated with the version it was scored with.
 */
public class Challenge {
    private final ChallengeId id;
    private final String editionId;
    private final String name;
    private final List<Rulebook> rulebooks;

    private Challenge(Draft draft, Rulebook first) {
        this.id = draft.id();
        this.editionId = draft.editionId();
        this.name = draft.name();
        this.rulebooks = new ArrayList<>(List.of(first));
    }

    public static Draft draft(ChallengeId id, String editionId, String name) {
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
    public record Draft(ChallengeId id, String editionId, String name) {
        public Draft {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(editionId, "editionId cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
        }

        public Challenge publish(ScoringScheme scoring, RankingScheme rankingScheme) {
            return new Challenge(this, new Rulebook(RulebookVersion.first(), scoring, rankingScheme));
        }
    }

    public ChallengeId getId() {
        return id;
    }

    public String getEditionId() {
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
