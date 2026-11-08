package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.evaluation.scheme.TieBreakCriterion;

import java.util.List;

/**
 * GraphQL view of a published rulebook version: the sources it needs and, in words, how a turn and the rounds count
 * and how ties break. The rules themselves stay in REST ({@code GET .../rulebooks}); the schema exposes what a
 * reader of the results needs.
 */
public record RulebookView(int version, List<ResultSource> requiredSources, String attemptRule, String selectionRule,
                           List<String> tieBreakCriteria) {

    public static RulebookView from(Rulebook rulebook) {
        RankingScheme ranking = rulebook.rankingScheme();
        return new RulebookView(rulebook.version().number(), rulebook.requiredSources().stream().sorted().toList(),
                ranking.attemptSelection().describe(), ranking.roundSelection().describe(),
                ranking.criteria().stream().map(TieBreakCriterion::name).toList());
    }
}
