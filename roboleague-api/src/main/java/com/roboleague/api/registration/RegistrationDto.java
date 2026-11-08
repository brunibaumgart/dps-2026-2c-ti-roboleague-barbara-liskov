package com.roboleague.api.registration;

import com.roboleague.port.in.RegisteredTeam;

import java.time.LocalDate;
import java.time.LocalDateTime;

record RegistrationDto(String editionId, String teamId, String categoryId, LocalDateTime registeredAt,
                       LocalDate referenceDate, TeamDto team) {
    static RegistrationDto from(RegisteredTeam view) {
        var registration = view.registration();
        return new RegistrationDto(registration.editionId().value(), registration.teamId().value(),
                registration.categoryId().value(), registration.registeredAt(), registration.referenceDate(), TeamDto.from(view.team()));
    }
}
