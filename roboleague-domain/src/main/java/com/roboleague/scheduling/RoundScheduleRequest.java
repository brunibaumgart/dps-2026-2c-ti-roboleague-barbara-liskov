package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object encapsulating all parameters required to schedule a round.
 */
public record RoundScheduleRequest(
        RoundInfo roundInfo,
        RoundResources resources,
        RoundScheduleTiming timing
) {
    public RoundScheduleRequest {
        Objects.requireNonNull(roundInfo, "roundInfo cannot be null");
        Objects.requireNonNull(resources, "resources cannot be null");
        Objects.requireNonNull(timing, "timing cannot be null");
    }

    public static RoundScheduleRequest of(RoundInfo roundInfo, RoundResources resources, RoundScheduleTiming timing) {
        return new RoundScheduleRequest(roundInfo, resources, timing);
    }
}
