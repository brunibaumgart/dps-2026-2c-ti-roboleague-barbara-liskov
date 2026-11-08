package com.roboleague.api.registration;

import com.roboleague.support.ActorId;
import com.roboleague.port.in.TeamCandidate;
import com.roboleague.tournament.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

/** Complete team candidate as JSON. Ages and verification timestamps always come from the server clock. */
record TeamBody(String id, String name, String institution, List<MemberBody> members,
                RobotBody robot, DocumentationBody documentation) {
    /** The candidate the use case builds the team from; the server clock gives it its dates, not this body. */
    TeamCandidate toCandidate() {
        return new TeamCandidate(TeamId.of(text(id, "team.id")), text(name, "team.name"),
                required(institution, "team.institution"), required(robot, "team.robot").toRobot(),
                required(members, "team.members").stream()
                        .map(member -> required(member, "team.members[]").toMember()).toList(),
                required(documentation, "team.documentation").checkedDocuments(), documentation.verifier());
    }

    record MemberBody(String id, String fullName, LocalDate birthDate, String role) {
        TeamCandidate.Member toMember() {
            return new TeamCandidate.Member(ParticipantId.of(text(id, "member.id")), text(fullName, "member.fullName"),
                    required(birthDate, "member.birthDate"), text(role, "member.role"));
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
        Map<String, String> checkedDocuments() {
            Map<String, String> checked = new LinkedHashMap<>();
            for (var entry : required(documents, "documentation.documents").entrySet()) {
                checked.put(text(entry.getKey(), "document.type"), text(entry.getValue(), "document.reference"));
            }
            return checked;
        }

        Optional<ActorId> verifier() {
            return verifiedBy == null ? Optional.empty()
                    : Optional.of(ActorId.of(text(verifiedBy, "documentation.verifiedBy")));
        }
    }
}
