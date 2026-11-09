package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.AppealView;
import com.roboleague.api.graphql.view.AttemptView;
import com.roboleague.api.graphql.view.RegistrationView;
import com.roboleague.api.graphql.view.SlotView;
import com.roboleague.api.graphql.view.StandingsEntryView;
import com.roboleague.api.graphql.view.TeamTurnView;
import com.roboleague.api.graphql.view.TeamView;
import com.roboleague.port.in.QueryTeamResults;
import com.roboleague.port.in.QueryTeams;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Canonical teams, their results and all team relations; input ports own the reads and business rules. */
@Controller
class TeamQueries {
    private final QueryTeams queryTeams;
    private final QueryTeamResults queryResults;

    TeamQueries(QueryTeams queryTeams, QueryTeamResults queryResults) {
        this.queryTeams = Objects.requireNonNull(queryTeams);
        this.queryResults = Objects.requireNonNull(queryResults);
    }

    @QueryMapping
    TeamView team(@Argument String id) {
        return TeamView.from(queryTeams.get(TeamId.of(id)));
    }

    @SchemaMapping(typeName = "Team", field = "results")
    List<TeamTurnView> results(TeamView team, @Argument String challengeId, @Argument String categoryId) {
        return queryResults.execute(TeamId.of(team.id()), ChallengeId.of(challengeId),
                        Optional.ofNullable(categoryId).map(CategoryId::of))
                .stream().map(TeamTurnView::from).toList();
    }

    @BatchMapping(typeName = "Registration", field = "team")
    Map<RegistrationView, TeamView> registrationTeams(List<RegistrationView> registrations) {
        return teams(registrations, RegistrationView::teamId);
    }

    @BatchMapping(typeName = "Slot", field = "team")
    Map<SlotView, TeamView> slotTeams(List<SlotView> slots) {
        return teams(slots, SlotView::teamId);
    }

    @BatchMapping(typeName = "Attempt", field = "team")
    Map<AttemptView, TeamView> attemptTeams(List<AttemptView> attempts) {
        return teams(attempts, AttemptView::teamId);
    }

    @BatchMapping(typeName = "Appeal", field = "team")
    Map<AppealView, TeamView> appealTeams(List<AppealView> appeals) {
        return teams(appeals, AppealView::teamId);
    }

    @BatchMapping(typeName = "StandingsEntry", field = "team")
    Map<StandingsEntryView, TeamView> standingsTeams(List<StandingsEntryView> entries) {
        return teams(entries, StandingsEntryView::teamId);
    }

    private <V> Map<V, TeamView> teams(List<V> parents, Function<V, String> teamId) {
        Map<TeamId, Team> found = queryTeams.byIds(parents.stream().map(teamId).map(TeamId::of).distinct().toList());
        Map<V, TeamView> result = new LinkedHashMap<>();
        // Reaching the same view twice is valid; equal records share one key.
        parents.forEach(parent -> result.putIfAbsent(parent, TeamView.from(found.get(TeamId.of(teamId.apply(parent))))));
        return result;
    }
}
