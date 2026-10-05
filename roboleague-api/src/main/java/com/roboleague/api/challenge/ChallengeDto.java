package com.roboleague.api.challenge;

import com.roboleague.tournament.Challenge;

/**
 * JSON view of a challenge with its current rulebook. The aggregate never leaves the API as is.
 */
record ChallengeDto(String id, String editionId, String name, RulebookDto currentRulebook) {

    static ChallengeDto from(Challenge challenge) {
        return new ChallengeDto(challenge.getId().value(), challenge.getEditionId(), challenge.getName(),
                RulebookDto.from(challenge.currentRulebook()));
    }
}
