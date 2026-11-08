package com.roboleague.usecase;

import com.roboleague.port.in.QueryTeams;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the canonical teams: one by id, or many at once for whoever got their ids from registrations, slots or
 * standings. Reads only.
 */
public class QueryTeamsUseCase implements QueryTeams {
    private final TeamRepository teams;

    public QueryTeamsUseCase(TeamRepository teams) {
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
    }

    /** The canonical team, or NotFoundException("Team", id). One read. */
    public Team get(TeamId id) {
        return teams.findById(id).orElseThrow(() -> new NotFoundException("Team", id));
    }

    /**
     * The teams with those ids, keyed by id, in one read however many ids there are (none for an empty collection).
     * A stored reference that does not exist is an InconsistentStateException: the caller got the ids from
     * registrations, slots or standings.
     */
    public Map<TeamId, Team> byIds(Collection<TeamId> ids) {
        return StoredReferences.byIds(ids, teams::findByIds, Team::getId, "Registered team not found");
    }
}
