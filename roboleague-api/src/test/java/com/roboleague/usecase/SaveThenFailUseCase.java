package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;

/**
 * A use case that saves and then fails, like accepting an appeal whose second save breaks. It lives in the use case
 * package so the application wraps it in a transaction like any other.
 */
public class SaveThenFailUseCase {
    private final ChallengeRepository challenges;

    public SaveThenFailUseCase(ChallengeRepository challenges) {
        this.challenges = challenges;
    }

    public void execute(Challenge challenge) {
        challenges.save(challenge);
        throw new IllegalStateException("the second save failed");
    }
}
