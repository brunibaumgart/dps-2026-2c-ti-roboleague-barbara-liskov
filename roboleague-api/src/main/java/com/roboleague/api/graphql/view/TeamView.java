package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Team;

import java.util.List;

/**
 * GraphQL view of a team. {@code results} is resolved on demand with its arguments, so it is not a component.
 */
public record TeamView(String id, String name, String institution, List<MemberView> members, RobotView robot,
                       DocumentationView documentation) {

    public static TeamView from(Team team) {
        return new TeamView(team.getId().value(), team.getName(), team.getInstitution(),
                team.getMembers().stream().map(MemberView::from).toList(), RobotView.from(team.getRobot()),
                DocumentationView.from(team.getDocumentation()));
    }
}
