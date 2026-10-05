package com.roboleague.evaluation;

/**
 * Where a result of an attempt comes from. A mixed challenge (F3) needs both before it can score.
 */
public enum ResultSource {
    AUTOMATIC_MEASUREMENTS("Mediciones automáticas"),
    JUDGE_PANEL("Panel de jueces");

    private final String label;

    ResultSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
