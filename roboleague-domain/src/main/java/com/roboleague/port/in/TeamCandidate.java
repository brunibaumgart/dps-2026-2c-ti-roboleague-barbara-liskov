package com.roboleague.port.in;

import com.roboleague.support.ActorId;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A team as a client sends it, before the server gives it a time: members without the date their age is measured
 * against, documents without the moment they were verified. Only the use case knows the time, from its clock, so
 * the adapter never builds a {@link Team} itself.
 *
 * @param verifiedBy who verified the documentation, empty when it is not verified yet
 */
public record TeamCandidate(TeamId id, String name, String institution, Robot robot, List<Member> members,
                            Map<String, String> documents, Optional<ActorId> verifiedBy) {

    public TeamCandidate {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(institution, "institution cannot be null");
        Objects.requireNonNull(robot, "robot cannot be null");
        members = List.copyOf(Objects.requireNonNull(members, "members cannot be null"));
        documents = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(documents, "documents cannot be null")));
        Objects.requireNonNull(verifiedBy, "verifiedBy cannot be null");
    }

    /**
     * The team as of {@code now}: ages are measured on that date and a verified documentation is verified at that
     * moment.
     */
    public Team toTeam(LocalDateTime now) {
        Objects.requireNonNull(now, "now cannot be null");
        Documentation documentation = new Documentation();
        for (var document : documents.entrySet()) {
            documentation = documentation.withDocument(document.getKey(), document.getValue());
        }
        Documentation built = documentation;
        documentation = verifiedBy.map(actor -> built.verify(actor, now)).orElse(built);
        return Team.of(id, name, institution, robot,
                members.stream().map(member -> member.toMember(now.toLocalDate())).toList(), documentation);
    }

    /** A member as sent: who it is, when it was born and its role. */
    public record Member(ParticipantId id, String fullName, LocalDate birthDate, String role) {

        public Member {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(fullName, "fullName cannot be null");
            Objects.requireNonNull(birthDate, "birthDate cannot be null");
            Objects.requireNonNull(role, "role cannot be null");
        }

        TeamMember toMember(LocalDate referenceDate) {
            return TeamMember.of(id, fullName, birthDate, role, referenceDate);
        }
    }
}
