package com.roboleague.scheduling;

import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Who cancelled a turn, why and when: for example, a team that did not show up. A cancelled turn needs no result.
 */
public record SlotCancellation(ActorId cancelledBy, String reason, LocalDateTime cancelledAt) {
    public SlotCancellation {
        Objects.requireNonNull(cancelledBy, "cancelledBy cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("a cancellation needs a reason");
        }
    }
}
