package com.roboleague.tournament;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplicitCalendarTest {
    @Test
    void newParticipantChecksBirthAgainstTheProvidedCalendarAndRestoreDoesNotUseToday() {
        LocalDate reference = LocalDate.of(2020, 1, 1);
        MemberProfile profile = MemberProfile.of("m-1", "Participante");
        assertThatThrownBy(() -> TeamMember.of(profile, reference.plusDays(1), "MEMBER", reference))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThat(TeamMember.of(profile, reference, "MEMBER", reference).birthDate()).isEqualTo(reference);

        // The value constructor restores structural data without consulting a current calendar.
        TeamMember restored = new TeamMember(profile, LocalDate.of(2030, 1, 1), "MEMBER");
        assertThat(restored.birthDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    }

    @Test
    void documentationKeepsTheVerificationTimeSuppliedByTheCaller() {
        Documentation documentation = new Documentation();
        LocalDateTime verifiedAt = LocalDateTime.of(2020, 1, 1, 9, 0);
        documentation.verify("inspector", verifiedAt);
        assertThat(documentation.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(documentation.getVerifiedBy()).isEqualTo("inspector");
    }
}
