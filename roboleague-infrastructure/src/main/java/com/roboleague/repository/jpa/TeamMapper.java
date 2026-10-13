package com.roboleague.repository.jpa;

import com.roboleague.repository.jpa.TeamJpaEntity.DocumentationJson;
import com.roboleague.repository.jpa.TeamJpaEntity.MemberJson;
import com.roboleague.repository.jpa.TeamJpaEntity.RobotJson;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.MemberProfile;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.TeamProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;

/**
 * Anti-corruption layer between the teams table and the {@link Team} aggregate. Reading restores members and
 * documentation as stored: it neither checks birth dates against today nor repeats the verification.
 */
final class TeamMapper {

    private TeamMapper() {
    }

    static TeamJpaEntity toEntity(Team team) {
        TeamJpaEntity entity = new TeamJpaEntity();
        entity.id = team.getId().value();
        entity.name = team.getName();
        entity.institution = team.getInstitution();
        Robot robot = team.getRobot();
        RobotSpecification spec = robot.getSpecification();
        entity.robot = new RobotJson(robot.getId().value(), robot.getName(), spec.weightGrams(), spec.lengthMm(),
                spec.widthMm(), spec.heightMm(), spec.actuatorCount(), spec.sensors().stream().sorted().toList());
        entity.members = team.getMembers().stream()
                .map(member -> new MemberJson(member.id().value(), member.fullName(), member.birthDate().toString(),
                        member.role()))
                .toList();
        Documentation documentation = team.getDocumentation();
        entity.documentation = new DocumentationJson(documentation.getDocuments(), documentation.isVerified(),
                documentation.getVerifiedAt() == null ? null : documentation.getVerifiedAt().toString(),
                documentation.getVerifiedBy() == null ? null : documentation.getVerifiedBy().value(),
                documentation.getRevocationReason());
        return entity;
    }

    static Team toDomain(TeamJpaEntity entity) {
        RobotJson robot = entity.robot;
        DocumentationJson documentation = entity.documentation;
        return new Team(TeamProfile.of(TeamId.of(entity.id), entity.name, entity.institution),
                new Robot(RobotId.of(robot.id()), robot.name(), RobotSpecification.of(robot.weightGrams(),
                        robot.lengthMm(), robot.widthMm(), robot.heightMm(), robot.actuatorCount(),
                        new HashSet<>(robot.sensors()))),
                entity.members.stream()
                        .map(member -> new TeamMember(MemberProfile.of(ParticipantId.of(member.id()), member.fullName()),
                                LocalDate.parse(member.birthDate()), member.role()))
                        .toList(),
                Documentation.restore(documentation.documents(), documentation.verified(),
                        documentation.verifiedAt() == null ? null : LocalDateTime.parse(documentation.verifiedAt()),
                        documentation.verifiedBy() == null ? null : ActorId.of(documentation.verifiedBy()),
                        documentation.revocationReason()));
    }
}
