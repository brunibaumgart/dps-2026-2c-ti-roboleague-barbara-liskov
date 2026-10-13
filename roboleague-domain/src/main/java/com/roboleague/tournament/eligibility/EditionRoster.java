package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamMember;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The teams registered in one edition. A participant or a robot competes for a single team in an edition, so a
 * candidate that shares one of them with another registered team is rejected with every conflict. Participants
 * and robots are compared by identity; the candidate's own registration is not a conflict.
 */
public record EditionRoster(List<Team> registered) {
    public EditionRoster {
        registered = List.copyOf(Objects.requireNonNull(registered, "registered cannot be null"));
    }

    public List<String> conflictsWith(Team candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        List<String> conflicts = new ArrayList<>();
        for (Team other : registered) {
            if (other.getId().equals(candidate.getId())) {
                continue;
            }
            for (TeamMember member : candidate.getMembers()) {
                if (other.getMembers().stream().anyMatch(theirs -> theirs.id().equals(member.id()))) {
                    conflicts.add("Participant " + member.id() + " already competes for team " + other.getId());
                }
            }
            if (other.getRobot().getId().equals(candidate.getRobot().getId())) {
                conflicts.add("Robot " + candidate.getRobot().getId() + " already competes for team " + other.getId());
            }
        }
        return List.copyOf(conflicts);
    }

    public void requireNoConflicts(Team candidate) {
        List<String> conflicts = conflictsWith(candidate);
        if (!conflicts.isEmpty()) {
            throw new TeamIneligibleException("Team " + candidate.getName()
                    + " shares participants or robot with another team of the edition", conflicts);
        }
    }
}
