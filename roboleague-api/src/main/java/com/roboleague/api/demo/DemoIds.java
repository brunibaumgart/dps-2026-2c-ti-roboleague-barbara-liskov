package com.roboleague.api.demo;

import com.roboleague.support.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sequential ids for the demo ({@code demo-1}, {@code demo-2}...), so every run gives rounds, turns, appeals and
 * events the same ids. They are unique because the demo starts over an empty database.
 */
final class DemoIds implements IdGenerator {
    private static final String PREFIX = "demo-";

    private final AtomicLong last = new AtomicLong();

    @Override
    public String nextId() {
        return PREFIX + last.incrementAndGet();
    }

    /** Back to {@code demo-1}, for a new load over an empty database. */
    void restart() {
        last.set(0);
    }
}
