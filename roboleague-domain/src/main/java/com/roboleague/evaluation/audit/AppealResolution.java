package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Value object specifying appeal identity and resolution notes.
 */
public record AppealResolution(String appealId, String resolutionNotes) {
    public AppealResolution {
        Objects.requireNonNull(appealId, "appealId cannot be null");
        Objects.requireNonNull(resolutionNotes, "resolutionNotes cannot be null");
    }

    public static AppealResolution of(String appealId, String resolutionNotes) {
        return new AppealResolution(appealId, resolutionNotes);
    }
}
