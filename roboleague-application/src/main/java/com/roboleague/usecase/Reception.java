package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;

import java.util.List;

/**
 * Outcome of receiving what one source sent for an attempt: the attempt that took it, or every problem that kept
 * it out (measurements that do not fit the rulebook, a judge not assigned to the slot).
 */
public sealed interface Reception permits Reception.Received, Reception.Rejected {

    record Received(Attempt attempt) implements Reception {
    }

    record Rejected(List<String> problems) implements Reception {
        public Rejected {
            problems = List.copyOf(problems);
        }
    }
}
