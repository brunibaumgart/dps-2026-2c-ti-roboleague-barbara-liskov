package com.roboleague.evaluation.scheme;

/**
 * One step of the tie-break chain declared by a rulebook. Negative means {@code a} ranks before {@code b}.
 */
public interface TieBreakCriterion {

    String name();

    /**
     * Stable identifier used to declare the criterion in the API and to store it.
     */
    String code();

    int compare(ChallengeScore a, ChallengeScore b);
}
