package com.roboleague.ranking;

import java.util.List;

/**
 * Outcome of asking to publish a standings version: published as the official one, or blocked with every reason.
 */
public sealed interface StandingsPublication permits StandingsPublication.Published, StandingsPublication.Blocked {

    record Published(StandingsVersion version) implements StandingsPublication {
    }

    record Blocked(List<String> reasons) implements StandingsPublication {
        public Blocked {
            reasons = List.copyOf(reasons);
            if (reasons.isEmpty()) {
                throw new IllegalArgumentException("a blocked publication says why");
            }
        }
    }
}
