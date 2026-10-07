package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AuditTrail;

import java.util.List;
import java.util.Objects;

/**
 * Everything an attempt went through, to rebuild a stored one: its stage, what each source sent and its audit trail.
 */
public record AttemptProgress(AttemptStage stage, List<SourceDelivery> deliveries, AuditTrail trail) {
    public AttemptProgress {
        Objects.requireNonNull(stage, "stage cannot be null");
        deliveries = List.copyOf(Objects.requireNonNull(deliveries, "deliveries cannot be null"));
        Objects.requireNonNull(trail, "trail cannot be null");
    }
}
