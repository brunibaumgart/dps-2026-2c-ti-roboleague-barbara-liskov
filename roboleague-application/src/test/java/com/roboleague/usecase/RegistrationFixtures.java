package com.roboleague.usecase;

import com.roboleague.support.ActorId;
import com.roboleague.tournament.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.*;

final class RegistrationFixtures {
    private RegistrationFixtures() {}

    static Category category(String id, int minMembers, int maxMembers, int minAge, int maxAge, double maxWeight) {
        return Category.of(CategoryId.of(id), id, minMembers, maxMembers, minAge, maxAge, maxWeight, 300, 300, 300);
    }

    static Edition edition(String id, LocalDate start, Category... categories) {
        return Edition.of(EditionId.of(id), Tournament.of("t-1", "Tournament", new Season("s-1", 2026, "2026")),
                1, id, start, start.plusDays(1), List.of(categories));
    }

    static TeamMember member(String id, LocalDate birth) {
        return TeamMember.of(ParticipantId.of(id), id, birth, "MEMBER", DATE);
    }

    static Robot robot(double weight) {
        return new Robot(RobotId.of("robot-1"), "Robot", RobotSpecification.of(weight, 100, 100, 100, 2, Set.of("LIDAR")));
    }

    static Team team(String id) {
        return Team.of(TeamId.of(id), id, "School", robot(1000),
                List.of(member("m-1", LocalDate.of(2010, 1, 1)), member("m-2", LocalDate.of(2010, 1, 1))),
                new Documentation().withDocument("CONSENT", "consent.pdf").verify(ActorId.of("inspector"), TIME));
    }
}
