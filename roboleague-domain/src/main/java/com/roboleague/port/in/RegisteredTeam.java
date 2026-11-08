package com.roboleague.port.in;

import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Team;

/** Enrollment with the current canonical team, without transport concerns. */
public record RegisteredTeam(Registration registration, Team team) { }
