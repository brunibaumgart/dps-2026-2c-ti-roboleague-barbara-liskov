package com.roboleague.api.registration;

import com.roboleague.support.ActorId;
import com.roboleague.support.Clock;
import com.roboleague.tournament.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

/** Complete team candidate. Verification timestamps always come from the server. */
record TeamBody(String id, String name, String institution, List<MemberBody> members,
                RobotBody robot, DocumentationBody documentation) {
    Team toTeam(Clock clock) {
        var now = clock.now();
        return Team.of(TeamId.of(text(id, "team.id")), text(name, "team.name"), required(institution, "team.institution"),
                required(robot, "team.robot").toRobot(), required(members, "team.members").stream()
                        .map(member -> required(member, "team.members[]").toMember(now.toLocalDate())).toList(),
                required(documentation, "team.documentation").toDocumentation(now));
    }

    record MemberBody(String id, String fullName, LocalDate birthDate, String role) {
        TeamMember toMember(LocalDate referenceDate) {
            return TeamMember.of(ParticipantId.of(text(id, "member.id")), text(fullName, "member.fullName"),
                    required(birthDate, "member.birthDate"), text(role, "member.role"), referenceDate);
        }
    }

    record RobotBody(String id, String name, Double weightGrams, Double lengthMm, Double widthMm,
                     Double heightMm, Integer actuatorCount, Set<String> sensors) {
        Robot toRobot() {
            required(sensors, "robot.sensors").forEach(sensor -> text(sensor, "robot.sensors[]"));
            return new Robot(RobotId.of(text(id, "robot.id")), text(name, "robot.name"), RobotSpecification.of(
                    required(weightGrams, "robot.weightGrams"), required(lengthMm, "robot.lengthMm"),
                    required(widthMm, "robot.widthMm"), required(heightMm, "robot.heightMm"),
                    required(actuatorCount, "robot.actuatorCount"), sensors));
        }
    }

    record DocumentationBody(Map<String, String> documents, String verifiedBy) {
        Documentation toDocumentation(java.time.LocalDateTime now) {
            Documentation result = new Documentation();
            for (var entry : required(documents, "documentation.documents").entrySet()) {
                result = result.withDocument(text(entry.getKey(), "document.type"), text(entry.getValue(), "document.reference"));
            }
            return verifiedBy == null ? result : result.verify(ActorId.of(text(verifiedBy, "documentation.verifiedBy")), now);
        }
    }
}
