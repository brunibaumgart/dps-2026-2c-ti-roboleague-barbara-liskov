package com.roboleague.evaluation.scheme;

/**
 * One step of the tie-break chain declared by a rulebook. Negative means {@code a} ranks before {@code b}.
 */
public interface TieBreakCriterion {

    String name();

    int compare(ChallengeScore a, ChallengeScore b);
}
