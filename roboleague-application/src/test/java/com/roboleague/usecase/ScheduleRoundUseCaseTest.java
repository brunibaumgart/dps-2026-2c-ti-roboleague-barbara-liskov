package com.roboleague.usecase;

import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRoundUseCaseTest {

    @Test
    @DisplayName("Schedules round creating slots for all registered teams and assigning judges and tracks")
    void schedulesRoundSuccessfully() {
        InMemoryTeamRepository teams = new InMemoryTeamRepository();
        InMemoryEditionRepository editionRepo = new InMemoryEditionRepository();
        RoundSchedulerService schedulerService = new RoundSchedulerService(ids());
        InMemoryRoundRepository rounds = new InMemoryRoundRepository();
        ScheduleRoundUseCase useCase = new ScheduleRoundUseCase(editionRepo, teams, rounds, schedulerService, ids());

        Category category = Category.of(CategoryId.of("cat-sumo"), "Sumo", 2, 4, 15, 20, 2500);
        Season season = new Season("s-1", 2026, "2026");
        Tournament tournament = Tournament.of("t-1", "Torneo", "Desc", season);
        Edition edition = Edition.of(EditionId.of("ed-1"), tournament, 1, "Edicion 1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), List.of(category));

        Robot robot = new Robot(RobotId.of("r-1"), "Bot", RobotSpecification.of(2000, 100, 100, 100, 2, Set.of()));
        Team t1 = RegistrationFixtures.team("t-1");
        Team t2 = RegistrationFixtures.team("t-2");
        teams.save(t1);
        teams.save(t2);
        edition = edition.registerTeam(t1, category.id(), TIME).registerTeam(t2, category.id(), TIME);
        editionRepo.save(edition);

        List<Track> tracks = List.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Piedra"));
        List<Judge> judges = List.of(Judge.of(JudgeId.of("j-1"), "Juez Uno", "General"), Judge.of(JudgeId.of("j-2"), "Juez Dos", "General"));

        Round round = useCase.execute(ScheduleRoundCommand.of(
                EditionId.of("ed-1"), CategoryId.of("cat-sumo"), 1, "Ronda 1", tracks, judges,
                TIME, Duration.ofMinutes(10), Duration.ofMinutes(2)
        ));

        assertThat(round).isNotNull();
        assertThat(round.getSlots()).hasSize(2);
        assertThat(round.getSlots().get(0).getTeamId()).isEqualTo(TeamId.of("t-1"));
        assertThat(round.getSlots().get(1).getTeamId()).isEqualTo(TeamId.of("t-2"));
        assertThat(round.getSlots().get(0).getAssignedJudges()).isNotEmpty();
        assertThat(rounds.findBySlotId(round.getSlots().get(1).getSlotId())).containsSame(round);
    }

    @Test
    void revalidatesTheCanonicalTeamBeforeGeneratingOrSavingAnySlot() {
        InMemoryEditionRepository editions = new InMemoryEditionRepository();
        InMemoryTeamRepository teams = new InMemoryTeamRepository();
        InMemoryRoundRepository rounds = new InMemoryRoundRepository();
        Category category = RegistrationFixtures.category("junior", 1, 2, 10, 20, 2000);
        Edition edition = RegistrationFixtures.edition("ed-stale", DATE, category);
        Team original = RegistrationFixtures.team("team-stale");
        editions.save(edition.registerTeam(original, category.id(), TIME));
        // Simulate stale/invalid state introduced by a trusted persistence adapter, bypassing the use case.
        teams.save(original.withMembers(List.of()));
        java.util.concurrent.atomic.AtomicInteger generated = new java.util.concurrent.atomic.AtomicInteger();
        com.roboleague.support.IdGenerator generator = () -> "generated-" + generated.incrementAndGet();
        ScheduleRoundUseCase scheduler = new ScheduleRoundUseCase(editions, teams, rounds,
                new RoundSchedulerService(generator), generator);
        ScheduleRoundCommand command = ScheduleRoundCommand.of(edition.getId(), category.id(), 1, "Round",
                List.of(Track.active(TrackId.of("track-stale"), "Track", "Wood")),
                List.of(Judge.of(JudgeId.of("judge-stale"), "Judge", "General")),
                TIME, Duration.ofMinutes(10), Duration.ZERO);
        assertThatThrownBy(() -> scheduler.execute(command))
                .isInstanceOf(com.roboleague.tournament.eligibility.TeamIneligibleException.class);
        assertThat(generated).hasValue(0);
        assertThat(rounds.findBySlotId(com.roboleague.scheduling.SlotId.of("generated-2"))).isEmpty();
        assertThat(editions.findById(edition.getId()).orElseThrow().getRegistrations()).hasSize(1);
    }

    @Test
    @DisplayName("Fails to schedule when no teams are registered in the category")
    void failsWhenNoTeamsRegistered() {
        InMemoryTeamRepository teams = new InMemoryTeamRepository();
        InMemoryEditionRepository editionRepo = new InMemoryEditionRepository();
        ScheduleRoundUseCase useCase = new ScheduleRoundUseCase(editionRepo, teams, new InMemoryRoundRepository(),
                new RoundSchedulerService(ids()), ids());

        Category category = Category.of(CategoryId.of("cat-sumo"), "Sumo", 2, 4, 15, 20, 2500);
        Season season = new Season("s-1", 2026, "2026");
        Tournament tournament = Tournament.of("t-1", "Torneo", "Desc", season);
        Edition edition = Edition.of(EditionId.of("ed-1"), tournament, 1, "Edicion 1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), List.of(category));
        editionRepo.save(edition);

        List<Track> tracks = List.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Piedra"));
        List<Judge> judges = List.of(Judge.of(JudgeId.of("j-1"), "Juez Uno", "General"));

        assertThatThrownBy(() -> useCase.execute(ScheduleRoundCommand.of(
                EditionId.of("ed-1"), CategoryId.of("cat-sumo"), 1, "Ronda 1", tracks, judges,
                TIME, Duration.ofMinutes(10), Duration.ofMinutes(2)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No teams registered");
    }
}
