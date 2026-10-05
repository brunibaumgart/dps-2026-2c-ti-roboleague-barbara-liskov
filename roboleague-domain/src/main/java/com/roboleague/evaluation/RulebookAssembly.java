package com.roboleague.evaluation;

import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.List;

/**
 * Outcome of building a rulebook's schemes from its definition: either both schemes or every problem found.
 */
public sealed interface RulebookAssembly permits RulebookAssembly.Assembled, RulebookAssembly.Rejected {

    record Assembled(ScoringScheme scoring, RankingScheme ranking) implements RulebookAssembly {
    }

    record Rejected(List<String> problems) implements RulebookAssembly {
        public Rejected {
            problems = List.copyOf(problems);
        }
    }
}
