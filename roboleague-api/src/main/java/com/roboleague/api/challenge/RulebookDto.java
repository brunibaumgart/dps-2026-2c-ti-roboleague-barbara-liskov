package com.roboleague.api.challenge;

import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.Rulebook;

import java.util.List;

/**
 * JSON view of a published rulebook version: its number, the sources it needs, and its metrics and rules as they
 * were sent.
 */
record RulebookDto(int version, List<String> requiredSources, List<RulebookBody.MetricDeclarationBody> metrics,
                   ScoringView scoring, RankingView ranking) {

    record ScoringView(List<RulebookBody.RuleBody> rules, List<RulebookBody.RuleBody> bonuses,
                       List<RulebookBody.RuleBody> deductions, RulebookBody.StrategyBody bonusLimit) {
    }

    record RankingView(RulebookBody.StrategyBody roundSelection, List<String> criteria) {
    }

    static RulebookDto from(Rulebook rulebook) {
        RulebookBody body = RulebookBody.from(rulebook.definition());
        return new RulebookDto(rulebook.version().number(),
                rulebook.requiredSources().stream().map(ResultSource::name).sorted().toList(),
                body.metrics(),
                new ScoringView(body.scoring().rules(), body.scoring().bonuses(), body.scoring().deductions(),
                        body.scoring().bonusLimit()),
                new RankingView(body.ranking().roundSelection(), body.ranking().criteria()));
    }
}
