package com.roboleague.tournament;

import com.roboleague.tournament.eligibility.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilitySpecificationTest {

    private Category sumoCategory;
    private LocalDate tournamentDate;

    @BeforeEach
    void setUp() {
        tournamentDate = LocalDate.of(2026, 10, 15);
        sumoCategory = new Category(
                "cat-sumo",
                "RoboSumo Junior",
                "Competencia de empuje para jóvenes",
                2, 4,
                14, 18,
                3000.0, // max 3kg
                300.0, 300.0, 300.0 // 30x30x30 cm
        );
    }

    private Team createValidTeam() {
        RobotSpecification spec = new RobotSpecification(2500.0, 200.0, 200.0, 250.0, 2, Set.of("ULTRASONIC", "INFRARED"));
        Robot robot = new Robot("rob-1", "Titan", spec);
        Team team = new Team("team-1", "Los Titanes", "Escuela Técnica N1", sumoCategory, robot);

        // Members: 15 and 17 years old at tournamentDate
        team.addMember(new TeamMember("m-1", "Lucas Vega", LocalDate.of(2011, 5, 10), "CAPTAIN"));
        team.addMember(new TeamMember("m-2", "Sofia Gomez", LocalDate.of(2009, 3, 20), "PROGRAMMER"));

        team.getDocumentation().addDocument("CONSENT", "consent_lucas.pdf");
        team.getDocumentation().addDocument("TECHNICAL_SHEET", "sheet_titan.pdf");
        team.getDocumentation().verify("Official Inspector 01");

        return team;
    }

    @Test
    @DisplayName("Eligible team satisfies all composite specifications")
    void eligibleTeamSatisfiesAllSpecifications() {
        Team team = createValidTeam();

        EligibilitySpecification<Team> compositeSpec = new AgeLimitSpecification(tournamentDate)
                .and(new TeamSizeSpecification())
                .and(new RobotSpecificationLimit())
                .and(new DocumentationVerifiedSpecification());

        EligibilityResult result = compositeSpec.isSatisfiedBy(team);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Ineligible when team member is below minimum age")
    void rejectsWhenMemberTooYoung() {
        Team team = createValidTeam();
        // Add a 10-year-old member (min is 14)
        team.addMember(new TeamMember("m-3", "Nene Pro", LocalDate.of(2016, 1, 1), "TESTER"));

        AgeLimitSpecification spec = new AgeLimitSpecification(tournamentDate);
        EligibilityResult result = spec.isSatisfiedBy(team);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("under the minimum age limit"));
    }

    @Test
    @DisplayName("Ineligible when team size is below minimum or above maximum")
    void rejectsInvalidTeamSize() {
        RobotSpecification robotSpec = new RobotSpecification(2000.0, 150.0, 150.0, 150.0, 2, Set.of());
        Robot robot = new Robot("rob-2", "Mini", robotSpec);
        Team team = new Team("team-2", "SoloBot", "ITBA", sumoCategory, robot);

        // Only 1 member (min is 2)
        team.addMember(new TeamMember("m-1", "Solo Dev", LocalDate.of(2010, 1, 1), "SOLO"));

        TeamSizeSpecification spec = new TeamSizeSpecification();
        EligibilityResult result = spec.isSatisfiedBy(team);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("fewer members than required"));

        // Now add 4 more members (total 5, max is 4)
        team.addMember(new TeamMember("m-2", "Dev 2", LocalDate.of(2010, 1, 1), "M"));
        team.addMember(new TeamMember("m-3", "Dev 3", LocalDate.of(2010, 1, 1), "M"));
        team.addMember(new TeamMember("m-4", "Dev 4", LocalDate.of(2010, 1, 1), "M"));
        team.addMember(new TeamMember("m-5", "Dev 5", LocalDate.of(2010, 1, 1), "M"));

        EligibilityResult resultExceeded = spec.isSatisfiedBy(team);
        assertThat(resultExceeded.isEligible()).isFalse();
        assertThat(resultExceeded.reasons()).anyMatch(r -> r.contains("exceeds maximum member count"));
    }

    @Test
    @DisplayName("Ineligible when robot exceeds maximum weight or dimensions")
    void rejectsRobotExceedingLimits() {
        // Overweight robot (3500g > 3000g) and too tall (350mm > 300mm)
        RobotSpecification overweight = new RobotSpecification(3500.0, 200.0, 200.0, 350.0, 4, Set.of());
        Robot robot = new Robot("rob-heavy", "Behemoth", overweight);
        Team team = new Team("team-heavy", "HeavyWeights", "Lab", sumoCategory, robot);

        RobotSpecificationLimit spec = new RobotSpecificationLimit();
        EligibilityResult result = spec.isSatisfiedBy(team);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("Robot weight (3500.0g) exceeds category limit"));
        assertThat(result.reasons()).anyMatch(r -> r.contains("Robot height (350.0mm) exceeds category limit"));
    }

    @Test
    @DisplayName("Ineligible when documentation is unverified")
    void rejectsUnverifiedDocumentation() {
        Team team = createValidTeam();
        team.getDocumentation().revokeVerification("Missing medical certificate");

        DocumentationVerifiedSpecification spec = new DocumentationVerifiedSpecification();
        EligibilityResult result = spec.isSatisfiedBy(team);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("documentation has not been verified"));
    }
}
