package com.roboleague.usecase;

import java.util.List;

/**
 * Outcome of publishing something built from a rulebook definition that came from outside:
 * either what was published or every problem found in the definition.
 */
public sealed interface Publication<T> permits Publication.Published, Publication.Rejected {

    record Published<T>(T value) implements Publication<T> {
    }

    record Rejected<T>(List<String> problems) implements Publication<T> {
        public Rejected {
            problems = List.copyOf(problems);
        }
    }
}
