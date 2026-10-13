package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;

/**
 * A use case that saves and then fails, like accepting an appeal whose second save breaks. It lives in the use case
 * package, not next to {@code TransactionalUseCasesTest}, because {@code TransactionalUseCases} wraps only the beans of
 * exactly {@code com.roboleague.usecase}: widening that check for a test package would change production wiring just
 * to test it.
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
