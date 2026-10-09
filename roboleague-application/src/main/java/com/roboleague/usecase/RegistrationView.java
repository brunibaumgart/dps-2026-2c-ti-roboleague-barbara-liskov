package com.roboleague.usecase;

import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Team;

/** Enrollment with the current canonical team, without transport concerns. */
public record RegistrationView(Registration registration, Team team) { }
