package com.roboleague.support;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.scheduling.JudgeId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
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

    /** A capture with only the track performance measured. */
    public static RawMetrics metrics(double seconds, int objectives, int penalties) {
        return RawMetrics.of(new TrackPerformance(seconds, objectives, penalties), EvaluationFeedback.empty());
    }

    /** A capture with the track performance and the judges' scores. */
    public static RawMetrics metrics(double seconds, int objectives, int penalties, Map<JudgeId, Double> judgeScores) {
        return RawMetrics.of(new TrackPerformance(seconds, objectives, penalties),
                EvaluationFeedback.of(0.0, judgeScores, Map.of()));
    }

    /** Feedback carrying only named measurements. */
    public static EvaluationFeedback measured(Map<String, Double> measurements) {
        return EvaluationFeedback.of(0.0, Map.of(), measurements);
    }
}
