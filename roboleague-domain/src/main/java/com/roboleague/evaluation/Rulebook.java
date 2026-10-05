package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Published, immutable rules of a challenge: how an attempt scores and how teams are ranked.
 * Changing either means publishing a new version.
 */
public final class Rulebook {
    private final RulebookVersion version;
    private final ScoringScheme scoring;
    private final RankingScheme rankingScheme;

    public Rulebook(RulebookVersion version, ScoringScheme scoring, RankingScheme rankingScheme) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.scoring = Objects.requireNonNull(scoring, "scoring cannot be null");
        this.rankingScheme = Objects.requireNonNull(rankingScheme, "rankingScheme cannot be null");
    }

    public RulebookVersion version() {
        return version;
    }

    public ScoringScheme scoring() {
        return scoring;
    }

    public RankingScheme rankingScheme() {
        return rankingScheme;
    }

    /**
     * Sources an attempt needs before it can be scored with this rulebook (F3).
     */
    public Set<ResultSource> requiredSources() {
        return scoring.requiredSources();
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        RuleEvaluation evaluation = scoring.evaluate(metrics);
        return ScoreBreakdown.of(evaluation.items(), evaluation.notes());
    }

    /**
     * The score split by source, so a mixed challenge explains each contribution separately (F3).
     * The bonus cap and the zero floor apply to the whole score, so they belong to no source.
     */
    public List<SourceContribution> contributions(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        return scoring.contributions(metrics);
    }
}
