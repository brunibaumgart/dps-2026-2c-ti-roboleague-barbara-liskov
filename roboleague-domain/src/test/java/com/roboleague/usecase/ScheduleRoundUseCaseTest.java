package com.roboleague.usecase;

import com.roboleague.evaluation.ScoringPolicy;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRoundUseCaseTest {

    @Test
    @DisplayName("Schedules round creating slots for all registered teams and assigning judges and tracks")
    void schedulesRoundSuccessfully() {
        InMemoryEditionRepository editionRepo = new InMemoryEditionRepository();
        RoundSchedulerService schedulerService = new RoundSchedulerService();
        ScheduleRoundUseCase useCase = new ScheduleRoundUseCase(editionRepo, schedulerService);

        Category category = Category.of("cat-sumo", "Sumo", 2, 4, 15, 20, 2500);
        Season season = new Season("s-1", 2026, "2026");
        Tournament tournament = Tournament.of("t-1", "Torneo", "Desc", season);
        ScoringPolicy policy = ScoringPolicy.of("pol-1", "v1", "Reglamento", List.of());

        Edition edition = Edition.of("ed-1", tournament, 1, "Edicion 1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), policy, List.of(category));

        Robot robot = new Robot("r-1", "Bot", RobotSpecification.of(2000, 100, 100, 100, 2, Set.of()));
        Team t1 = Team.of("t-1", "Alpha", "ITBA", category, robot);
        Team t2 = Team.of("t-2", "Beta", "ITBA", category, robot);
        edition.registerTeam(t1);
        edition.registerTeam(t2);
        editionRepo.save(edition);

        List<Track> tracks = List.of(Track.active("trk-1", "Pista 1", "Piedra"));
        List<Judge> judges = List.of(Judge.of("j-1", "Juez Uno", "General"), Judge.of("j-2", "Juez Dos", "General"));

        Round round = useCase.execute(ScheduleRoundCommand.of(
                "ed-1", "cat-sumo", 1, "Ronda 1", tracks, judges,
                LocalDateTime.now(), Duration.ofMinutes(10), Duration.ofMinutes(2)
        ));

        assertThat(round).isNotNull();
        assertThat(round.getSlots()).hasSize(2);
        assertThat(round.getSlots().get(0).getTeamId()).isEqualTo("t-1");
        assertThat(round.getSlots().get(1).getTeamId()).isEqualTo("t-2");
        assertThat(round.getSlots().get(0).getAssignedJudges()).isNotEmpty();
    }

    @Test
    @DisplayName("Fails to schedule when no teams are registered in the category")
    void failsWhenNoTeamsRegistered() {
        InMemoryEditionRepository editionRepo = new InMemoryEditionRepository();
        ScheduleRoundUseCase useCase = new ScheduleRoundUseCase(editionRepo, new RoundSchedulerService());

        Category category = Category.of("cat-sumo", "Sumo", 2, 4, 15, 20, 2500);
        Season season = new Season("s-1", 2026, "2026");
        Tournament tournament = Tournament.of("t-1", "Torneo", "Desc", season);
        ScoringPolicy policy = ScoringPolicy.of("pol-1", "v1", "Reglamento", List.of());

        Edition edition = Edition.of("ed-1", tournament, 1, "Edicion 1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), policy, List.of(category));
        editionRepo.save(edition);

        List<Track> tracks = List.of(Track.active("trk-1", "Pista 1", "Piedra"));
        List<Judge> judges = List.of(Judge.of("j-1", "Juez Uno", "General"));

        assertThatThrownBy(() -> useCase.execute(ScheduleRoundCommand.of(
                "ed-1", "cat-sumo", 1, "Ronda 1", tracks, judges,
                LocalDateTime.now(), Duration.ofMinutes(10), Duration.ofMinutes(2)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No teams registered");
    }
}
