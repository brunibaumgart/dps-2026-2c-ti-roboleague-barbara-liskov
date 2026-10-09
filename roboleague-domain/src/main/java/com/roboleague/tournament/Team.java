package com.roboleague.tournament;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Canonical team state. Competitive categories and registration times belong to Edition. */
public final class Team {
    private final TeamProfile profile;
    private final List<TeamMember> members;
    private final Robot robot;
    private final Documentation documentation;

    public Team(TeamProfile profile, Robot robot, List<TeamMember> members, Documentation documentation) {
        this.profile = Objects.requireNonNull(profile, "profile cannot be null");
        this.robot = Objects.requireNonNull(robot, "robot cannot be null");
        this.members = List.copyOf(Objects.requireNonNull(members, "members cannot be null"));
        this.documentation = Objects.requireNonNull(documentation, "documentation cannot be null");
        var identities = new HashSet<ParticipantId>();
        for (TeamMember member : this.members) {
            if (!identities.add(member.id())) {
                throw new IllegalArgumentException("Member already registered in team: " + member.id());
            }
        }
    }

    public TeamProfile getProfile() { return profile; }
    public TeamId getId() { return profile.id(); }
    public String getName() { return profile.name(); }
    public String getInstitution() { return profile.institution(); }
    public Robot getRobot() { return robot; }
    public List<TeamMember> getMembers() { return members; }
    public Documentation getDocumentation() { return documentation; }

    /** Builds a candidate; saving changes to a registered team requires UpdateTeamUseCase. */
    public Team withMembers(List<TeamMember> members) {
        return new Team(profile, robot, members, documentation);
    }

    public Team withRobot(Robot robot) {
        return new Team(profile, robot, members, documentation);
    }

    public Team withDocumentation(Documentation documentation) {
        return new Team(profile, robot, members, documentation);
    }

    public Team withProfile(TeamProfile profile) {
        if (!getId().equals(profile.id())) {
            throw new IllegalArgumentException("A team update cannot change its identity");
        }
        return new Team(profile, robot, members, documentation);
    }

    public static Team of(TeamId id, String name, String institution, Robot robot,
                          List<TeamMember> members, Documentation documentation) {
        return new Team(TeamProfile.of(id, name, institution), robot, members, documentation);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Team team && getId().equals(team.getId());
    }

    @Override
    public int hashCode() { return getId().hashCode(); }
}
