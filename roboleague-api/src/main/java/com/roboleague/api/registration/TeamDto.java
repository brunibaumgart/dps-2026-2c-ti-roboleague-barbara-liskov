package com.roboleague.api.registration;

import com.roboleague.tournament.Team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

record TeamDto(String id, String name, String institution, List<MemberDto> members,
               RobotDto robot, DocumentationDto documentation) {
    static TeamDto from(Team team) {
        var robot = team.getRobot();
        var spec = robot.getSpecification();
        var documentation = team.getDocumentation();
        return new TeamDto(team.getId().value(), team.getName(), team.getInstitution(),
                team.getMembers().stream().map(member -> new MemberDto(member.id().value(), member.fullName(),
                        member.birthDate(), member.role())).toList(),
                new RobotDto(robot.getId().value(), robot.getName(), spec.weightGrams(), spec.lengthMm(), spec.widthMm(),
                        spec.heightMm(), spec.actuatorCount(), spec.sensors().stream().sorted().toList()),
                new DocumentationDto(documentation.getDocuments(), documentation.isVerified(), documentation.getVerifiedAt(),
                        documentation.getVerifiedBy() == null ? null : documentation.getVerifiedBy().value(), documentation.getRevocationReason()));
    }

    record MemberDto(String id, String fullName, LocalDate birthDate, String role) { }
    record RobotDto(String id, String name, double weightGrams, double lengthMm, double widthMm, double heightMm,
                    int actuatorCount, List<String> sensors) { }
    record DocumentationDto(Map<String, String> documents, boolean verified, LocalDateTime verifiedAt,
                            String verifiedBy, String revocationReason) { }
}
