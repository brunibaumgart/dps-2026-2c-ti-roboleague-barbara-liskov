package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.EligibilityResult;
import com.roboleague.tournament.eligibility.EligibilitySpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterTeamUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private EditionRepository editionRepository;

    @Mock
    private EligibilitySpecification<Team> eligibilitySpecification;

    @InjectMocks
    private RegisterTeamUseCase registerTeamUseCase;

    @Test
    @DisplayName("Registers team and saves when team meets all eligibility specifications")
    void registersTeamSuccessfullyWhenEligible() {
        Category category = Category.of("cat-1", "Sumo", 2, 4, 15, 20, 2500);
        Edition edition = mock(Edition.class);
        when(editionRepository.findById("ed-1")).thenReturn(Optional.of(edition));

        Robot robot = new Robot("r-1", "Bot", new RobotSpecification(2000, 100, 100, 100, 2, Set.of()));
        Team team = new Team("t-1", "RoboDevs", "ITBA", category, robot);

        when(eligibilitySpecification.isSatisfiedBy(team)).thenReturn(EligibilityResult.eligible());

        Team result = registerTeamUseCase.execute("ed-1", team);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("t-1");
        verify(edition).registerTeam(team);
        verify(teamRepository).save(team);
        verify(editionRepository).save(edition);
    }

    @Test
    @DisplayName("Throws TeamIneligibleException and does not save when eligibility fails")
    void throwsExceptionWhenIneligible() {
        Category category = Category.of("cat-1", "Sumo", 2, 4, 15, 20, 2500);
        Edition edition = mock(Edition.class);
        when(editionRepository.findById("ed-1")).thenReturn(Optional.of(edition));

        Robot robot = new Robot("r-1", "Bot", new RobotSpecification(2000, 100, 100, 100, 2, Set.of()));
        Team team = new Team("t-1", "RoboDevs", "ITBA", category, robot);

        when(eligibilitySpecification.isSatisfiedBy(team))
                .thenReturn(EligibilityResult.ineligible(List.of("Robot exceeds maximum weight limit")));

        assertThatThrownBy(() -> registerTeamUseCase.execute("ed-1", team))
                .isInstanceOf(TeamIneligibleException.class)
                .hasMessageContaining("Robot exceeds maximum weight limit");

        verify(edition, never()).registerTeam(any());
        verify(teamRepository, never()).save(any());
    }
}
