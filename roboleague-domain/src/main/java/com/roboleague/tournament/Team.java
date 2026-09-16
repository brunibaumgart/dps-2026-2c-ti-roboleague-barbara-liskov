package com.roboleague.tournament;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Team aggregate in the tournament context.
 */
public class Team {
    private final TeamProfile profile;
    private final List<TeamMember> members;
    private Robot robot;
    private Category category;
    private final Documentation documentation;
    private final LocalDate registrationDate;

    public Team(TeamProfile profile, Category category, Robot robot) {
        this.profile = Objects.requireNonNull(profile, "profile cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.robot = Objects.requireNonNull(robot, "robot cannot be null");
        this.members = new ArrayList<>();
        this.documentation = new Documentation();
        this.registrationDate = LocalDate.now();
    }

    public TeamProfile getProfile() {
        return profile;
    }

    public String getId() {
        return profile.id();
    }

    public String getName() {
        return profile.name();
    }

    public String getInstitution() {
        return profile.institution();
    }

    public Category getCategory() {
        return category;
    }

    public void changeCategory(Category category) {
        this.category = Objects.requireNonNull(category, "category cannot be null");
    }

    public Robot getRobot() {
        return robot;
    }

    public void assignRobot(Robot robot) {
        this.robot = Objects.requireNonNull(robot, "robot cannot be null");
    }

    public List<TeamMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public void addMember(TeamMember member) {
        Objects.requireNonNull(member, "member cannot be null");
        if (members.contains(member)) {
            throw new IllegalArgumentException("Member already registered in team: " + member.id());
        }
        members.add(member);
    }

    public void removeMember(String memberId) {
        members.removeIf(m -> m.id().equals(memberId));
    }

    public Documentation getDocumentation() {
        return documentation;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public static Team of(TeamProfile profile, Category category, Robot robot) {
        return new Team(profile, category, robot);
    }

    public static Team of(String id, String name, Category category, Robot robot) {
        return new Team(new TeamProfile(id, name, ""), category, robot);
    }

    public static Team of(String id, String name, String institution, Category category, Robot robot) {
        return new Team(new TeamProfile(id, name, institution != null ? institution : ""), category, robot);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team team)) return false;
        return Objects.equals(getId(), team.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
