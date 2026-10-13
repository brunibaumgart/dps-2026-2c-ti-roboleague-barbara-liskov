package com.roboleague.usecase;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.eligibility.EditionRoster;

/**
 * Builds the roster of an edition from the canonical team of each registration, so registering and updating a team
 * can check it against the other teams of the edition.
 */
final class EditionRosters {
    private EditionRosters() {
    }

    static EditionRoster of(Edition edition, TeamRepository teams) {
        return new EditionRoster(edition.getRegistrations().stream()
                .map(Registration::teamId)
                .map(teamId -> teams.findById(teamId).orElseThrow(() -> new InconsistentStateException(
                        "Registered team " + teamId + " of edition " + edition.getId() + " not found")))
                .toList());
    }
}
