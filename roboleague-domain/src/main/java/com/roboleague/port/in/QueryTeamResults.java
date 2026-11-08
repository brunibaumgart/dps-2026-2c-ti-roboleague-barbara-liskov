package com.roboleague.port.in;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import java.util.List;
import java.util.Optional;

/**
 * What a team did in a challenge: each of its turns, round by round, with the attempts it ran. Reads only.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryTeamResults {
    /**
     * Every turn the team has in the challenge (optionally in one category), in round order, with its attempts in
     * the order they were opened. Reads: the team (1), the challenge (1), its edition when a category is given (1),
     * the challenge's rounds (1) and the team's attempts (1), so 4 or 5 however many rounds or attempts there are.
     */
    List<TeamTurn> execute(TeamId teamId, ChallengeId challengeId, Optional<CategoryId> category);
}
