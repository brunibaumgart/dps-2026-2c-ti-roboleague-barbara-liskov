package com.roboleague.tournament;

import com.roboleague.support.ActorId;
import com.roboleague.tournament.eligibility.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;

class EligibilitySpecificationTest {

    private Category sumoCategory;
    private LocalDate tournamentDate;

    @BeforeEach
    void setUp() {
        tournamentDate = LocalDate.of(2026, 10, 15);
        CategoryRestrictions restrictions = CategoryRestrictions.of(
                TeamSizeRange.of(2, 4),
                AgeRange.of(14, 18),
                RobotLimits.of(3000.0, 300.0, 300.0, 300.0)
        );
        sumoCategory = new Category(CategoryId.of("cat-sumo"), "RoboSumo Junior", restrictions);
    }

    private Team createValidTeam() {
        RobotSpecification spec = new RobotSpecification(
                new Weight(2500.0),
                new Dimensions(200.0, 200.0, 250.0),
                new RobotHardware(2, Set.of("ULTRASONIC", "INFRARED"))
        );
        Robot robot = new Robot(RobotId.of("rob-1"), "Titan", spec);
        Team team = new Team(new TeamProfile(TeamId.of("team-1"), "Los Titanes", "Escuela Técnica N1"), robot, List.of(), new Documentation());

        // Members: 15 and 17 years old at tournamentDate
        team = addMember(team, TeamMember.of(ParticipantId.of("m-1"), "Lucas Vega", LocalDate.of(2011, 5, 10), "CAPTAIN", DATE));
        team = addMember(team, TeamMember.of(ParticipantId.of("m-2"), "Sofia Gomez", LocalDate.of(2009, 3, 20), "PROGRAMMER", DATE));

        team = team.withDocumentation(team.getDocumentation().withDocument("CONSENT", "consent_lucas.pdf"));
        team = team.withDocumentation(team.getDocumentation().withDocument("TECHNICAL_SHEET", "sheet_titan.pdf"));
        team = team.withDocumentation(team.getDocumentation().verify(ActorId.of("Official Inspector 01"), TIME));

        return team;
    }

    private static Team addMember(Team original, TeamMember member) {
        var members = new ArrayList<>(original.getMembers());
        members.add(member);
        return original.withMembers(members);
    }

    @Test
    @DisplayName("Eligible team satisfies all composite specifications")
    void eligibleTeamSatisfiesAllSpecifications() {
        Team team = createValidTeam();

        EligibilitySpecification<EligibilityCandidate> compositeSpec = new AgeLimitSpecification()
                .and(new TeamSizeSpecification())
                .and(new RobotSpecificationLimit())
                .and(new DocumentationVerifiedSpecification());

        EligibilityResult result = compositeSpec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Ineligible when team member is below minimum age")
    void rejectsWhenMemberTooYoung() {
        Team team = createValidTeam();
        // Add a 10-year-old member (min is 14)
        team = addMember(team, TeamMember.of(ParticipantId.of("m-3"), "Nene Pro", LocalDate.of(2016, 1, 1), "TESTER", DATE));

        AgeLimitSpecification spec = new AgeLimitSpecification();
        EligibilityResult result = spec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("under the minimum age limit"));
    }

    @Test
    @DisplayName("Ineligible when team size is below minimum or above maximum")
    void rejectsInvalidTeamSize() {
        RobotSpecification robotSpec = new RobotSpecification(
                new Weight(2000.0),
                new Dimensions(150.0, 150.0, 150.0),
                new RobotHardware(2, Set.of())
        );
        Robot robot = new Robot(RobotId.of("rob-2"), "Mini", robotSpec);
        Team team = new Team(new TeamProfile(TeamId.of("team-2"), "SoloBot", "ITBA"), robot, List.of(), new Documentation());

        // Only 1 member (min is 2)
        team = addMember(team, TeamMember.of(ParticipantId.of("m-1"), "Solo Dev", LocalDate.of(2010, 1, 1), "SOLO", DATE));

        TeamSizeSpecification spec = new TeamSizeSpecification();
        EligibilityResult result = spec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("fewer members than required"));

        // Now add 4 more members (total 5, max is 4)
        team = addMember(team, TeamMember.of(ParticipantId.of("m-2"), "Dev 2", LocalDate.of(2010, 1, 1), "M", DATE));
        team = addMember(team, TeamMember.of(ParticipantId.of("m-3"), "Dev 3", LocalDate.of(2010, 1, 1), "M", DATE));
        team = addMember(team, TeamMember.of(ParticipantId.of("m-4"), "Dev 4", LocalDate.of(2010, 1, 1), "M", DATE));
        team = addMember(team, TeamMember.of(ParticipantId.of("m-5"), "Dev 5", LocalDate.of(2010, 1, 1), "M", DATE));

        EligibilityResult resultExceeded = spec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));
        assertThat(resultExceeded.isEligible()).isFalse();
        assertThat(resultExceeded.reasons()).anyMatch(r -> r.contains("exceeds maximum member count"));
    }

    @Test
    @DisplayName("Ineligible when robot exceeds maximum weight or dimensions")
    void rejectsRobotExceedingLimits() {
        // Overweight robot (3500g > 3000g) and too tall (350mm > 300mm)
        RobotSpecification overweight = new RobotSpecification(
                new Weight(3500.0),
                new Dimensions(200.0, 200.0, 350.0),
                new RobotHardware(4, Set.of())
        );
        Robot robot = new Robot(RobotId.of("rob-heavy"), "Behemoth", overweight);
        Team team = new Team(new TeamProfile(TeamId.of("team-heavy"), "HeavyWeights", "Lab"), robot, List.of(), new Documentation());

        RobotSpecificationLimit spec = new RobotSpecificationLimit();
        EligibilityResult result = spec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("Robot weight (3500.0g) exceeds category limit"));
        assertThat(result.reasons()).anyMatch(r -> r.contains("Robot height (350.0mm) exceeds category limit"));
    }

    @Test
    @DisplayName("Ineligible when documentation is unverified")
    void rejectsUnverifiedDocumentation() {
        Team team = createValidTeam();
        team = team.withDocumentation(team.getDocumentation().revokeVerification("Missing medical certificate"));

        DocumentationVerifiedSpecification spec = new DocumentationVerifiedSpecification();
        EligibilityResult result = spec.isSatisfiedBy(new EligibilityCandidate(team, sumoCategory, tournamentDate));

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("documentation has not been verified"));
    }
}
