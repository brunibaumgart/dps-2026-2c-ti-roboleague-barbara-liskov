package com.roboleague.usecase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many times each read of a port was called, so a test can assert that a batch costs a fixed number of reads.
 * The counting repositories in this package report to one of these.
 */
final class ReadCounts {
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    void count(String read) {
        calls.computeIfAbsent(read, ignored -> new AtomicInteger()).incrementAndGet();
    }

    int of(String read) {
        AtomicInteger counted = calls.get(read);
        return counted == null ? 0 : counted.get();
    }

    int total() {
        return calls.values().stream().mapToInt(AtomicInteger::get).sum();
    }
}
