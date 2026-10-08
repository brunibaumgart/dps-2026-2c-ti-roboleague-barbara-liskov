package com.roboleague.support;

import com.roboleague.evaluation.audit.OperationAudit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit values for existing fixtures; scenario tests create their own clocks and sequences. */
public final class TestValues {
    private TestValues() { }
    public static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 8, 12, 0);
    public static final LocalDate DATE = TIME.toLocalDate();
    public static final Clock CLOCK = () -> TIME;
    private static final AtomicLong EVENTS = new AtomicLong();
    public static IdGenerator ids() {
        AtomicLong sequence = new AtomicLong();
        return () -> "generated-" + sequence.incrementAndGet();
    }
    public static OperationAudit audit() {
        return new OperationAudit(TIME, "event-" + EVENTS.incrementAndGet(), "event-" + EVENTS.incrementAndGet());
    }
}
