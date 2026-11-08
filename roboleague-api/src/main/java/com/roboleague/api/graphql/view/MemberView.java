package com.roboleague.api.graphql.view;

import com.roboleague.tournament.TeamMember;

/**
 * GraphQL view of a team member.
 */
public record MemberView(String id, String fullName, String birthDate, String role) {

    public static MemberView from(TeamMember member) {
        return new MemberView(member.id().value(), member.fullName(), Dates.iso(member.birthDate()), member.role());
    }
}
