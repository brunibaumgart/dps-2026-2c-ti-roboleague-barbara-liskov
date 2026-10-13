package com.roboleague.api.demo;

import com.roboleague.support.Clock;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The demo's simulated time. It starts at a fixed instant and each reading advances one second, so every run of the
 * demo records the same times and no two operations share one. The fixture moves it forward to the moment of each
 * step of the story (registration the day before, each round, the appeal); after loading, it keeps ticking from there.
 */
final class DemoClock implements Clock {
    /** The day before the edition: teams register and their documentation is verified. */
    static final LocalDateTime START = LocalDateTime.of(2026, 11, 9, 10, 0);

    private LocalDateTime current = START;

    @Override
    public synchronized LocalDateTime now() {
        LocalDateTime now = current;
        current = current.plusSeconds(1);
        return now;
    }

    /** Jumps to a later moment of the story; time never goes back. */
    synchronized void advanceTo(LocalDateTime moment) {
        Objects.requireNonNull(moment, "moment cannot be null");
        if (moment.isBefore(current)) {
            throw new IllegalStateException("the demo clock is already at " + current + ", after " + moment);
        }
        current = moment;
    }

    /** Back to {@link #START}, for a new load over an empty database. */
    synchronized void restart() {
        current = START;
    }
}
