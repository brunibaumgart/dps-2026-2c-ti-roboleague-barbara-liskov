package com.roboleague.usecase;

import com.roboleague.ranking.StandingsVersion;
import com.roboleague.ranking.appeal.Appeal;

import java.util.List;
import java.util.Objects;

/**
 * What accepting an appeal gives: the accepted appeal with the standings version its correction produced, or the
 * problems of corrections that do not fit the attempt's rulebook (nothing changes then).
 */
public sealed interface AppealAcceptance permits AppealAcceptance.Accepted, AppealAcceptance.Invalid {

    record Accepted(Appeal appeal, StandingsVersion standings) implements AppealAcceptance {
        public Accepted {
            Objects.requireNonNull(appeal, "appeal cannot be null");
            Objects.requireNonNull(standings, "standings cannot be null");
        }
    }

    record Invalid(List<String> problems) implements AppealAcceptance {
        public Invalid {
            problems = List.copyOf(Objects.requireNonNull(problems, "problems cannot be null"));
            if (problems.isEmpty()) {
                throw new IllegalArgumentException("invalid corrections need at least one problem");
            }
        }
    }
}
