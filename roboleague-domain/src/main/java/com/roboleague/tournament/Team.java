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
    private final String id;
    private final String name;
    private final String institution;
    private final List<TeamMember> members;
    private Robot robot;
    private Category category;
    private final Documentation documentation;
    private final LocalDate registrationDate;

    public Team(String id, String name, String institution, Category category, Robot robot) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.institution = institution != null ? institution : "";
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.robot = Objects.requireNonNull(robot, "robot cannot be null");
        this.members = new ArrayList<>();
        this.documentation = new Documentation();
        this.registrationDate = LocalDate.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInstitution() {
        return institution;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team team)) return false;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
