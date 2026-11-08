package com.roboleague.api.graphql.view;

import com.roboleague.scheduling.SlotCancellation;

/**
 * GraphQL view of why and when a slot was cancelled.
 */
public record CancellationView(String actorId, String reason, String cancelledAt) {

    public static CancellationView from(SlotCancellation cancellation) {
        return new CancellationView(cancellation.cancelledBy().value(), cancellation.reason(),
                Dates.iso(cancellation.cancelledAt()));
    }
}
